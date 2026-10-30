use crate::position::*;
use std::sync::atomic::{AtomicBool, Ordering};
use std::time::{Duration, Instant};
const MATE: i32 = 100000;
#[derive(Clone, Copy, Debug)]
pub enum SearchLimit {
    Depth(u8),
    TimeMillis(u32),
}
#[derive(Clone, Copy, Debug)]
pub struct SearchOptions {
    pub quiescence: bool,
}
/// Validated immutable search input; no references to a live game or native board.
#[derive(Clone)]
pub struct SearchRequest {
    id: u64,
    position: Position,
    limit: SearchLimit,
    options: SearchOptions,
}
impl SearchRequest {
    pub fn new(
        id: u64,
        variant: Variant,
        starting_position: &str,
        history: Vec<CoordinateMove>,
        limit: SearchLimit,
        options: SearchOptions,
    ) -> Result<Self, Error> {
        match limit {
            SearchLimit::Depth(1..=15) | SearchLimit::TimeMillis(1..) => {}
            _ => return Err(invalid("Depth must be 1..15 or time must be positive")),
        }
        let mut position = Position::from_fen(variant, starting_position)?;
        for (index, m) in history.iter().enumerate() {
            position.apply_input(m.as_str(), index + 1 == history.len())?;
        }
        Ok(Self {
            id,
            position,
            limit,
            options,
        })
    }
    pub fn position(&self) -> &Position {
        &self.position
    }
}
#[derive(Default)]
pub struct SearchControl {
    stopped: AtomicBool,
}
impl SearchControl {
    pub fn stop(&self) {
        self.stopped.store(true, Ordering::Relaxed);
    }
}
#[derive(Clone, Debug)]
pub struct SearchResult {
    pub search_id: u64,
    pub best_move: Option<CoordinateMove>,
    /// Centipawns from the searched side's perspective; +/-100000 is mate without a distance.
    pub score: i32,
    pub completed_depth: u8,
    pub has_evaluation: bool,
    pub nodes: u64,
    pub pv: Vec<CoordinateMove>,
    pub interrupted: bool,
    pub elapsed_micros: u64,
}
/// One event per completed iteration, followed by exactly one final event.
#[derive(Clone, Debug)]
pub enum SearchEvent {
    Progress(SearchResult),
    Finished(SearchResult),
}
struct Worker<'a> {
    control: &'a SearchControl,
    start: Instant,
    budget: Option<Duration>,
    quiescence: bool,
    nodes: u64,
    interrupted: bool,
    /// Positions seen along the current line, from the game history onwards.
    seen: Vec<Repetition>,
    /// Entries of `seen` before this index precede an irreversible move.
    seen_from: usize,
    best: [Option<Move>; 20],
    values: [i32; 20],
    pv: [Vec<Move>; 20],
}
impl Worker<'_> {
    fn alpha_beta_duck(
        &mut self,
        p: &Position,
        moves: &mut Vec<Move>,
        depth: u8,
        ply: usize,
        mut alpha: i32,
        beta: i32,
    ) -> i32 {
        self.pv[ply].clear();
        if self.stopped() {
            return alpha;
        }
        if p.drawn_in_search(&self.seen[self.seen_from..]) {
            return 0;
        }
        if depth == 0 {
            self.nodes += 1;
            return p.evaluate();
        }
        // Order piece moves using the previous best piece move at this ply.
        let mut scores: Vec<_> = moves
            .iter()
            .map(|m| {
                let mut previous = self.best[ply];
                if let Some(ref mut previous) = previous {
                    previous.duck = None;
                }
                if previous == Some(*m) {
                    100000
                } else {
                    score_move(p, *m)
                }
            })
            .collect();
        let mut best = -MATE - 1;
        for index in 0..moves.len() {
            pick(moves, &mut scores, index);
            let mut m = moves[index];
            let next = p.make_unrecorded(m);
            let children = next.pseudo_moves();
            let mut line = Vec::new();
            let seen_from = self.enter(p, m);
            let value = if next.pieces.iter().any(|p| p[KING] == 0) {
                -next.evaluate()
            } else {
                let mut best_duck = -MATE - 1;
                // Preserve native candidates, their order and duplicates: destinations
                // of the opponent's non-capturing piece moves, not every empty square.
                for candidate in &children {
                    if candidate.capture || next.occupied() & bit(candidate.to) != 0 {
                        continue;
                    }
                    let mut duck_board = next.clone();
                    duck_board.duck = Some(candidate.to);
                    let mut replies = duck_board.pseudo_moves();
                    let value = -self.alpha_beta_duck(
                        &duck_board,
                        &mut replies,
                        depth - 1,
                        ply + 1,
                        -beta,
                        -alpha,
                    );
                    if value > best_duck {
                        best_duck = value;
                        m.duck = Some(candidate.to);
                        line = self.pv[ply + 1].clone();
                    }
                    alpha = alpha.max(best_duck);
                    if best_duck >= beta || self.interrupted {
                        break;
                    }
                }
                best_duck
            };
            self.leave(p, m, seen_from);
            if value > best && !self.interrupted {
                best = value;
                self.best[ply] = Some(m);
                self.values[ply] = value;
                self.pv[ply] = vec![m];
                self.pv[ply].extend(line);
            }
            alpha = alpha.max(best);
            if best >= beta || self.interrupted {
                break;
            }
        }
        if moves.is_empty() {
            // Duck chess: having no moves wins.
            self.nodes += 1;
            return MATE;
        }
        best
    }

    /// Records `p` as seen before `m` and returns the previous `seen_from`,
    /// to be handed back to `leave`.
    fn enter(&mut self, p: &Position, m: Move) -> usize {
        let from = self.seen_from;
        if p.is_irreversible(m) {
            self.seen_from = self.seen.len();
        } else {
            self.seen.push(p.repetition());
        }
        from
    }
    fn leave(&mut self, p: &Position, m: Move, seen_from: usize) {
        if !p.is_irreversible(m) {
            self.seen.pop();
        }
        self.seen_from = seen_from;
    }
    fn stopped(&mut self) -> bool {
        if self.control.stopped.load(Ordering::Relaxed)
            || self.nodes.is_multiple_of(1000)
                && self.budget.is_some_and(|b| self.start.elapsed() > b)
        {
            self.interrupted = true;
        }
        self.interrupted
    }
    fn alpha_beta(
        &mut self,
        p: &Position,
        moves: &mut Vec<Move>,
        depth: u8,
        ply: usize,
        mut alpha: i32,
        beta: i32,
    ) -> i32 {
        self.pv[ply].clear();
        if self.stopped() {
            return alpha;
        }
        if p.drawn_in_search(&self.seen[self.seen_from..]) {
            return 0;
        }
        if p.hill_won() {
            return -MATE;
        }
        let mut scores: Vec<_> = moves
            .iter()
            .map(|m| {
                let score = score_move(p, *m);
                if Some(*m) == self.best[ply] {
                    score.max(100000)
                } else {
                    score
                }
            })
            .collect();
        let mut best = -MATE - 1;
        let mut index = 0;
        while index < moves.len() {
            pick(moves, &mut scores, index);
            let mut m = moves[index];
            let mut next = p.make_unrecorded(m);
            if next.attacked(p.turn, next.king(p.turn)) {
                moves.swap_remove(index);
                continue;
            }
            let mut children = next.pseudo_moves();
            m.check = next.in_check();
            next.last_check = m.check;
            let seen_from = self.enter(p, m);
            let value = if depth == 1 {
                -self.quiesce(&next, &mut children, 5, -beta, -alpha)
            } else {
                -self.alpha_beta(&next, &mut children, depth - 1, ply + 1, -beta, -alpha)
            };
            self.leave(p, m, seen_from);
            if value > best && !self.interrupted {
                best = value;
                self.best[ply] = Some(m);
                self.values[ply] = value;
                self.pv[ply].clear();
                self.pv[ply].push(m);
                if depth > 1 {
                    let (line, deeper) = self.pv.split_at_mut(ply + 1);
                    line[ply].extend_from_slice(&deeper[0]);
                }
            }
            alpha = alpha.max(best);
            if best >= beta || self.interrupted {
                break;
            }
            index += 1;
        }
        if moves.is_empty() {
            self.nodes += 1;
            return if p.last_check { -MATE } else { 0 };
        }
        best
    }
    fn quiesce(
        &mut self,
        p: &Position,
        moves: &mut Vec<Move>,
        depth: u8,
        mut alpha: i32,
        beta: i32,
    ) -> i32 {
        if self.stopped() {
            return alpha;
        }
        self.nodes += 1;
        if p.variant == Variant::KingOfTheHill {
            if p.drawn_in_search(&self.seen[self.seen_from..]) {
                return 0;
            }
            if p.hill_won() {
                return -MATE;
            }
        }
        let value = p.evaluate();
        if !p.last_check && value >= beta {
            return beta;
        }
        if depth == 0 || !self.quiescence {
            return value;
        }
        if value >= beta {
            return beta;
        }
        alpha = alpha.max(value);
        let mut scores: Vec<_> = moves.iter().map(|m| score_move(p, *m)).collect();
        let mut index = 0;
        while index < moves.len() {
            pick(moves, &mut scores, index);
            let m = moves[index];
            let mut next = p.make_unrecorded(m);
            if next.attacked(p.turn, next.king(p.turn)) {
                moves.swap_remove(index);
                continue;
            }
            next.last_check = next.in_check();
            if m.capture || m.promotion.is_some() || next.last_check || next.hill_won() {
                let mut children = next.pseudo_moves();
                let seen_from = self.enter(p, m);
                let value = -self.quiesce(&next, &mut children, depth - 1, -beta, -alpha);
                self.leave(p, m, seen_from);
                if value > alpha {
                    alpha = value;
                    if value >= beta {
                        return beta;
                    }
                }
            }
            index += 1;
        }
        if moves.is_empty() {
            return if p.last_check { -MATE } else { 0 };
        }
        alpha
    }
}
fn pick(moves: &mut [Move], scores: &mut [i32], index: usize) {
    let mut best = -1;
    let mut at = index;
    for (i, score) in scores[..moves.len()].iter().enumerate().skip(index) {
        if *score > best {
            best = *score;
            at = i;
        }
    }
    moves.swap(index, at);
    scores.swap(index, at);
}
fn score_move(p: &Position, m: Move) -> i32 {
    let piece = p.piece(p.turn, m.from).unwrap();
    if p.variant == Variant::KingOfTheHill && piece == KING && [27, 28, 35, 36].contains(&m.to) {
        return 200000;
    }
    if m.capture {
        return if m.ep {
            30000
        } else {
            30000 + VALUES[p.piece(p.turn ^ 1, m.to).unwrap()] - VALUES[piece]
        };
    }
    if m.promotion.is_some() {
        return 25000;
    }
    match piece {
        KING => 0,
        KNIGHT => 2000 + distance(m.from, 27) - distance(m.to, 36),
        PAWN => 1900 + advance(p.turn, m.to) + distance(m.from, 27) - distance(m.to, 36),
        _ => 1000 + advance(p.turn, m.to),
    }
}
pub fn search(request: &SearchRequest, control: &SearchControl) -> Result<SearchResult, Error> {
    search_with_progress(request, control, |_| {})
}

pub fn search_with_progress(
    request: &SearchRequest,
    control: &SearchControl,
    mut publish: impl FnMut(SearchEvent),
) -> Result<SearchResult, Error> {
    let start = Instant::now();
    let p = &request.position;
    let mut result = SearchResult {
        search_id: request.id,
        best_move: None,
        score: 0,
        completed_depth: 0,
        has_evaluation: false,
        nodes: 0,
        pv: vec![],
        interrupted: false,
        elapsed_micros: 0,
    };
    match p.game_state() {
        GameState::Play | GameState::Check => {}
        state => {
            result.score = match state {
                GameState::Mate | GameState::HillWin => -MATE,
                GameState::DuckStalemateWin => MATE,
                _ => 0,
            };
            result.has_evaluation = true;
            result.elapsed_micros = start.elapsed().as_micros() as u64;
            publish(SearchEvent::Finished(result.clone()));
            return Ok(result);
        }
    }
    let budget = match request.limit {
        SearchLimit::TimeMillis(ms) => Some(Duration::from_millis(ms as u64)),
        _ => None,
    };
    let mut worker = Worker {
        control,
        start,
        budget,
        quiescence: request.options.quiescence,
        nodes: 1,
        interrupted: false,
        seen: p.repetitions().to_vec(),
        seen_from: 0,
        best: [None; 20],
        values: [0; 20],
        pv: std::array::from_fn(|_| Vec::new()),
    };
    let mut moves = p.legal_internal();
    let depths = match request.limit {
        SearchLimit::Depth(depth) => depth..=depth,
        _ => 1..=14,
    };
    for depth in depths {
        if p.variant == Variant::Duck {
            worker.alpha_beta_duck(p, &mut moves, depth, 0, -MATE, MATE);
        } else {
            worker.alpha_beta(p, &mut moves, depth, 0, -MATE, MATE);
        }
        if worker.interrupted {
            break;
        }
        result.best_move = worker.best[0].map(|m| p.external(p.turn, m));
        result.completed_depth = depth;
        result.has_evaluation = true;
        result.score = worker.values[0];
        // Track the selected line independently of the native ordering heuristic.
        // Movers alternate along the line, starting with the root's side to move.
        result.pv = worker.pv[0]
            .iter()
            .enumerate()
            .map(|(i, m)| p.external(p.turn ^ (i % 2), *m))
            .collect();
        result.nodes = worker.nodes;
        result.elapsed_micros = start.elapsed().as_micros() as u64;
        publish(SearchEvent::Progress(result.clone()));
        if worker.values[0] == MATE || budget.is_some_and(|b| worker.start.elapsed() > b / 3) {
            break;
        }
    }
    result.interrupted = worker.interrupted;
    result.nodes = worker.nodes;
    result.elapsed_micros = start.elapsed().as_micros() as u64;
    publish(SearchEvent::Finished(result.clone()));
    Ok(result)
}
