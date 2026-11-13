use crate::position::*;
mod table;
use std::sync::atomic::{AtomicBool, Ordering};
use std::time::{Duration, Instant};
use table::{Entry, Table};
const MATE: i32 = 100000;
// Include mate scores inside the full window so a winning or losing line is built.
const FULL_WINDOW: (i32, i32) = (-MATE - 1, MATE + 1);
const ASPIRATION_MARGIN: i32 = 50;
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

#[derive(Clone, Copy, PartialEq, Eq)]
struct TableContext {
    variant: Variant,
    rook_file: [[usize; 2]; 2],
    quiescence: bool,
}

/// Retains search entries across requests. Each request has its own search control.
#[derive(Default)]
pub struct SearchSession {
    cache: Option<(TableContext, Table)>,
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
    visits: u64,
    interrupted: bool,
    /// Positions seen along the current line, from the game history onwards.
    seen: Vec<Repetition>,
    /// Entries of `seen` before this index precede an irreversible move.
    seen_from: usize,
    repetition_key: u64,
    table: &'a mut Table,
    best_at_ply: [Option<Move>; 20],
    quiet_history: Box<[[[u16; 64]; 64]; 2]>,
    pv: [Vec<Move>; 20],
}
impl<'a> Worker<'a> {
    fn new(
        control: &'a SearchControl,
        start: Instant,
        budget: Option<Duration>,
        quiescence: bool,
        p: &Position,
        table: &'a mut Table,
    ) -> Self {
        Worker {
            control,
            start,
            budget,
            quiescence,
            nodes: 1,
            visits: 0,
            interrupted: false,
            seen: p.repetitions().to_vec(),
            seen_from: 0,
            repetition_key: p
                .repetitions()
                .iter()
                .map(|r| r.key)
                .fold(0, u64::wrapping_add),
            table,
            best_at_ply: [None; 20],
            quiet_history: Box::new([[[0; 64]; 64]; 2]),
            pv: std::array::from_fn(|_| Vec::new()),
        }
    }

    fn search_iteration(
        &mut self,
        p: &Position,
        moves: &mut Vec<Move>,
        depth: u8,
        previous_pv: &[Move],
        previous_score: Option<i32>,
    ) -> Option<i32> {
        let mut margin = ASPIRATION_MARGIN;
        let (mut alpha, mut beta) = match previous_score {
            Some(score) if score.abs() < MATE => (
                (score - margin).max(FULL_WINDOW.0),
                (score + margin).min(FULL_WINDOW.1),
            ),
            _ => FULL_WINDOW,
        };
        loop {
            let window = (alpha, beta);
            let score = if p.variant == Variant::Duck {
                self.alpha_beta_duck(p, Some(moves), depth, 0, window, previous_pv)
            } else {
                self.alpha_beta(p, Some(moves), depth, 0, window, previous_pv)
            };
            if self.interrupted {
                return None;
            }
            // Widen only the edge that the score reached.
            if score <= alpha && alpha > FULL_WINDOW.0 {
                alpha = (score - margin).max(FULL_WINDOW.0);
            } else if score >= beta && beta < FULL_WINDOW.1 {
                beta = (score + margin).min(FULL_WINDOW.1);
            } else {
                return Some(score);
            }
            margin *= 2;
        }
    }

    fn alpha_beta_duck(
        &mut self,
        p: &Position,
        moves: Option<&mut [Move]>,
        depth: u8,
        ply: usize,
        (mut alpha, beta): (i32, i32),
        previous_pv: &[Move],
    ) -> i32 {
        self.pv[ply].clear();
        if self.stopped() {
            return alpha;
        }
        if p.drawn_in_search(&self.seen[self.seen_from..]) {
            return 0;
        }
        let alpha_start = alpha;
        let key = if depth > 0 { p.search_key() } else { 0 };
        let entry = if depth > 0 {
            self.table.probe(key)
        } else {
            None
        };
        if let Some(score) = entry.and_then(|e| e.cutoff(self.repetition_key, depth, alpha, beta)) {
            return score;
        }
        let mut generated;
        let moves = match moves {
            Some(moves) => moves,
            None => {
                generated = p.pseudo_moves();
                &mut generated
            }
        };
        if moves.is_empty() {
            self.nodes += 1;
            return MATE;
        }
        if depth == 0 {
            self.nodes += 1;
            return p.evaluate();
        }
        let pv_move = previous_pv.first().copied();
        let table_move = entry.map(|e| e.best_move);
        let mut scores = ordered_scores(p, moves, pv_move, table_move, self.best_at_ply[ply], None);
        let mut best = -MATE - 1;
        let mut first_turn = true;
        for index in 0..moves.len() {
            pick(moves, &mut scores, index);
            let mut m = moves[index];
            let next = p.make_unrecorded(m);
            let mut line = Vec::new();
            let context = self.enter(p, m);
            let value = if next.pieces.iter().any(|p| p[KING] == 0) {
                -next.evaluate()
            } else if next.drawn_in_search(&self.seen[self.seen_from..]) {
                0
            } else {
                let mut best_duck = -MATE - 1;
                // Prefer destinations of the opponent's non-capturing moves.
                let mut candidates = duck_placement_candidates(&next);
                // A restricted candidate set must not force a stalemate win for the opponent.
                if candidates.iter().all(|&square| {
                    let mut placed = next.clone();
                    placed.duck = Some(square);
                    placed.pseudo_moves().is_empty()
                }) {
                    candidates = (0..64)
                        .filter(|&square| next.occupied() & bit(square) == 0)
                        .collect();
                }
                let placement_hint = pv_move
                    .filter(|hint| hint.same_piece_move(m))
                    .or_else(|| table_move.filter(|hint| hint.same_piece_move(m)))
                    .and_then(|hint| hint.duck);
                if let Some(index) = candidates.iter().position(|&s| Some(s) == placement_hint) {
                    candidates[..=index].rotate_right(1);
                }
                for square in candidates {
                    let turn = Move {
                        duck: Some(square),
                        ..m
                    };
                    let mut duck_board = next.clone();
                    duck_board.duck = turn.duck;
                    let tail = pv_tail(previous_pv, turn);
                    let value = self.search_child(first_turn, (alpha, beta), |worker, window| {
                        worker.alpha_beta_duck(&duck_board, None, depth - 1, ply + 1, window, tail)
                    });
                    first_turn = false;
                    if value > best_duck {
                        best_duck = value;
                        m = turn;
                        line = self.pv[ply + 1].clone();
                    }
                    alpha = alpha.max(best_duck);
                    if best_duck >= beta || self.interrupted {
                        break;
                    }
                }
                best_duck
            };
            first_turn = false;
            self.leave(p, m, context);
            if value > best && !self.interrupted {
                best = value;
                self.best_at_ply[ply] = Some(m);
                self.pv[ply] = vec![m];
                self.pv[ply].extend(line);
            }
            alpha = alpha.max(best);
            if best >= beta || self.interrupted {
                break;
            }
        }
        self.store(key, depth, ply, best, alpha_start, beta);
        best
    }

    fn search_child(
        &mut self,
        first: bool,
        (alpha, beta): (i32, i32),
        mut search: impl FnMut(&mut Self, (i32, i32)) -> i32,
    ) -> i32 {
        if !first && alpha + 1 < beta {
            let score = -search(self, (-alpha - 1, -alpha));
            // A probe inside the parent window needs a full search to establish the PV.
            if self.interrupted || score <= alpha || score >= beta {
                return score;
            }
        }
        -search(self, (-beta, -alpha))
    }

    /// Records `p` as seen before `m` and returns the context restored by `leave`.
    fn enter(&mut self, p: &Position, m: Move) -> (usize, u64) {
        let context = (self.seen_from, self.repetition_key);
        if p.is_irreversible(m) {
            self.seen_from = self.seen.len();
            self.repetition_key = 0;
        } else {
            let repetition = p.repetition();
            // Addition preserves multiplicity and permits any order of the same positions.
            self.repetition_key = self.repetition_key.wrapping_add(repetition.key);
            self.seen.push(repetition);
        }
        context
    }
    fn leave(&mut self, p: &Position, m: Move, context: (usize, u64)) {
        if !p.is_irreversible(m) {
            self.seen.pop();
        }
        (self.seen_from, self.repetition_key) = context;
    }
    fn stopped(&mut self) -> bool {
        self.visits += 1;
        if self.control.stopped.load(Ordering::Relaxed)
            || (self.visits == 1 || self.visits.is_multiple_of(1000))
                && self.budget.is_some_and(|b| self.start.elapsed() > b)
        {
            self.interrupted = true;
        }
        self.interrupted
    }
    fn store(&mut self, key: u64, depth: u8, ply: usize, score: i32, alpha: i32, beta: i32) {
        if !self.interrupted {
            if let Some(&best_move) = self.pv[ply].first() {
                self.table.store(Entry::new(
                    key,
                    self.repetition_key,
                    depth,
                    score,
                    (alpha, beta),
                    best_move,
                ));
            }
        }
    }
    fn alpha_beta(
        &mut self,
        p: &Position,
        moves: Option<&mut Vec<Move>>,
        depth: u8,
        ply: usize,
        (mut alpha, beta): (i32, i32),
        previous_pv: &[Move],
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
        let alpha_start = alpha;
        let key = p.search_key();
        let entry = self.table.probe(key);
        if let Some(score) = entry.and_then(|e| e.cutoff(self.repetition_key, depth, alpha, beta)) {
            return score;
        }
        let mut generated;
        let moves = match moves {
            Some(moves) => moves,
            None => {
                generated = p.pseudo_moves();
                &mut generated
            }
        };
        let mut scores = ordered_scores(
            p,
            moves,
            previous_pv.first().copied(),
            entry.map(|e| e.best_move),
            self.best_at_ply[ply],
            Some(&self.quiet_history[p.turn]),
        );
        let mut best = -MATE - 1;
        let mut index = 0;
        while index < moves.len() {
            pick(moves, &mut scores, index);
            let mut m = moves[index];
            let mut next = p.make_unrecorded(m);
            if next.attacked(p.turn, next.king(p.turn)) {
                moves.swap_remove(index);
                scores.swap_remove(index);
                continue;
            }
            m.check = next.in_check();
            next.last_check = m.check;
            let context = self.enter(p, m);
            let tail = pv_tail(previous_pv, m);
            let value = self.search_child(index == 0, (alpha, beta), |worker, window| {
                if depth == 1 {
                    worker.quiesce(&next, 5, window.0, window.1)
                } else {
                    worker.alpha_beta(&next, None, depth - 1, ply + 1, window, tail)
                }
            });
            self.leave(p, m, context);
            if value > best && !self.interrupted {
                best = value;
                self.best_at_ply[ply] = Some(m);
                self.pv[ply].clear();
                self.pv[ply].push(m);
                if depth > 1 {
                    let (line, deeper) = self.pv.split_at_mut(ply + 1);
                    line[ply].extend_from_slice(&deeper[0]);
                }
            }
            alpha = alpha.max(best);
            if self.interrupted {
                break;
            }
            if best >= beta {
                if !m.capture && m.promotion.is_none() {
                    let history = &mut self.quiet_history[p.turn][m.from][m.to];
                    // Bound the bonus so learned quiet moves stay below tactical moves.
                    *history = (*history + u16::from(depth).pow(2)).min(8192);
                }
                break;
            }
            index += 1;
        }
        if moves.is_empty() {
            self.nodes += 1;
            return if p.last_check { -MATE } else { 0 };
        }
        self.store(key, depth, ply, best, alpha_start, beta);
        best
    }
    fn quiesce(&mut self, p: &Position, depth: u8, mut alpha: i32, beta: i32) -> i32 {
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
        if !self.quiescence || depth == 0 {
            // Checked leaves must distinguish mate from static evaluation even
            // when tactical search is disabled. Stop at the first legal reply.
            if p.last_check
                && !p.pseudo_moves().into_iter().any(|m| {
                    let next = p.make_unrecorded(m);
                    !next.attacked(p.turn, next.king(p.turn))
                })
            {
                return -MATE;
            }
            return value;
        }
        if !p.last_check {
            if value >= beta {
                return beta;
            }
            alpha = alpha.max(value);
        }
        let mut moves = p.pseudo_moves();
        let mut scores: Vec<_> = moves.iter().map(|m| score_move(p, *m)).collect();
        let mut index = 0;
        while index < moves.len() {
            pick(&mut moves, &mut scores, index);
            let m = moves[index];
            let mut next = p.make_unrecorded(m);
            if next.attacked(p.turn, next.king(p.turn)) {
                moves.swap_remove(index);
                scores.swap_remove(index);
                continue;
            }
            next.last_check = next.in_check();
            if p.last_check
                || m.capture
                || m.promotion.is_some()
                || next.last_check
                || next.hill_won()
            {
                let context = self.enter(p, m);
                let value = -self.quiesce(&next, depth - 1, -beta, -alpha);
                self.leave(p, m, context);
                if value > alpha {
                    alpha = value;
                    if value >= beta {
                        return beta;
                    }
                }
            }
            index += 1;
        }
        if moves.is_empty() && p.last_check {
            return -MATE;
        }
        alpha
    }
}
fn duck_placement_candidates(p: &Position) -> Vec<usize> {
    let mut unavailable = p.occupied();
    p.pseudo_moves()
        .into_iter()
        .filter_map(|m| {
            let destination = bit(m.to);
            if m.capture || unavailable & destination != 0 {
                return None;
            }
            unavailable |= destination;
            Some(m.to)
        })
        .collect()
}

fn pv_tail(previous: &[Move], m: Move) -> &[Move] {
    match previous.split_first() {
        Some((hint, tail)) if hint.same_piece_move(m) && hint.duck == m.duck => tail,
        _ => &[],
    }
}

fn ordered_scores(
    p: &Position,
    moves: &[Move],
    pv_move: Option<Move>,
    table_move: Option<Move>,
    recent_move: Option<Move>,
    quiet_history: Option<&[[u16; 64]; 64]>,
) -> Vec<i32> {
    moves
        .iter()
        .map(|&m| {
            if pv_move.is_some_and(|hint| hint.same_piece_move(m)) {
                i32::MAX
            } else if table_move.is_some_and(|hint| hint.same_piece_move(m)) {
                i32::MAX - 1
            } else if recent_move.is_some_and(|hint| hint.same_piece_move(m)) {
                score_move(p, m).max(100000)
            } else {
                let base = score_move(p, m);
                if !m.capture && m.promotion.is_none() {
                    base + quiet_history.map_or(0, |history| i32::from(history[m.from][m.to]))
                } else {
                    base
                }
            }
        })
        .collect()
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
    publish: impl FnMut(SearchEvent),
) -> Result<SearchResult, Error> {
    SearchSession::default().search_with_progress(request, control, publish)
}

impl SearchSession {
    pub fn search_with_progress(
        &mut self,
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
        let context = TableContext {
            variant: p.variant,
            rook_file: p.rook_file,
            quiescence: request.options.quiescence,
        };
        let (cached_context, table) = self.cache.get_or_insert_with(|| (context, Table::new()));
        table.new_search(*cached_context != context);
        *cached_context = context;
        let mut worker = Worker::new(control, start, budget, request.options.quiescence, p, table);
        let mut moves = p.legal_internal();
        let depths = match request.limit {
            SearchLimit::Depth(depth) => depth..=depth,
            _ => 1..=14,
        };
        let mut previous_pv = Vec::new();
        for depth in depths {
            let previous_score = result.has_evaluation.then_some(result.score);
            let Some(score) =
                worker.search_iteration(p, &mut moves, depth, &previous_pv, previous_score)
            else {
                break;
            };
            previous_pv.clone_from(&worker.pv[0]);
            result.completed_depth = depth;
            result.has_evaluation = true;
            result.score = score;
            // Movers alternate along the line, starting with the root's side to move.
            result.pv = worker.pv[0]
                .iter()
                .enumerate()
                .map(|(i, m)| p.external(p.turn ^ (i % 2), *m))
                .collect();
            result.best_move = result.pv.first().cloned();
            result.nodes = worker.nodes;
            result.elapsed_micros = start.elapsed().as_micros() as u64;
            publish(SearchEvent::Progress(result.clone()));
            if score == MATE || budget.is_some_and(|b| worker.start.elapsed() > b / 3) {
                break;
            }
        }
        result.interrupted = worker.interrupted;
        result.nodes = worker.nodes;
        result.elapsed_micros = start.elapsed().as_micros() as u64;
        publish(SearchEvent::Finished(result.clone()));
        Ok(result)
    }
}

#[cfg(test)]
mod tests {
    use super::*;

    #[test]
    fn session_reuses_a_child_position_and_a_stop_is_local_to_one_search() {
        let fen = "r1bq1rk1/ppp2ppp/2np1n2/2b1p3/2B1P3/2NP1N2/PPP2PPP/R1BQ1RK1 w - - 0 10";
        let options = SearchOptions { quiescence: true };
        let parent = SearchRequest::new(
            1,
            Variant::Standard,
            fen,
            vec![],
            SearchLimit::Depth(4),
            options,
        )
        .unwrap();
        let mut session = SearchSession::default();
        let control = SearchControl::default();
        let parent_result = session
            .search_with_progress(&parent, &control, |_| {})
            .unwrap();
        let child = SearchRequest::new(
            2,
            Variant::Standard,
            fen,
            vec![parent_result.best_move.unwrap()],
            SearchLimit::Depth(3),
            options,
        )
        .unwrap();
        let child_key = child.position().search_key();
        assert!(session.cache.as_ref().unwrap().1.probe(child_key).is_some());

        control.stop();
        let stopped = session
            .search_with_progress(&child, &control, |_| {})
            .unwrap();
        assert!(stopped.interrupted);
        assert!(stopped.best_move.is_none());
        assert!(session.cache.as_ref().unwrap().1.probe(child_key).is_some());

        let control = SearchControl::default();
        let warm = session
            .search_with_progress(&child, &control, |_| {})
            .unwrap();
        let cold = search(&child, &control).unwrap();
        assert!(!warm.interrupted);
        assert_eq!(warm.search_id, 2);
        assert_eq!(warm.completed_depth, cold.completed_depth);
        assert_eq!(warm.score, cold.score);
        assert_eq!(warm.best_move.as_ref(), warm.pv.first());
        assert_eq!(warm.pv.len(), 3);
        let mut replay = child.position().clone();
        for m in warm.pv {
            replay.apply(m.as_str()).unwrap();
        }
    }

    #[test]
    fn session_clears_entries_when_search_context_changes() {
        let standard = "8/4k3/8/8/8/8/8/1R2K1R1 w - - 0 10";
        let chess960 = "8/4k3/8/8/8/8/8/1R2K1R1 w KQ - 0 10";
        let other_rook_files = "8/4k3/8/8/8/8/8/R3K2R w KQ - 0 10";
        for ((variant, fen, quiescence), (next_variant, next_fen, next_quiescence)) in [
            (
                (Variant::Standard, standard, true),
                (Variant::Standard, standard, false),
            ),
            (
                (Variant::Standard, standard, true),
                (Variant::Chess960, standard, true),
            ),
            (
                (Variant::Chess960, chess960, true),
                (Variant::Chess960, other_rook_files, true),
            ),
        ] {
            let request = |variant, fen, quiescence| {
                SearchRequest::new(
                    1,
                    variant,
                    fen,
                    vec![],
                    SearchLimit::Depth(1),
                    SearchOptions { quiescence },
                )
                .unwrap()
            };
            let mut session = SearchSession::default();
            let first = request(variant, fen, quiescence);
            let key = first.position().search_key();
            let control = SearchControl::default();
            session
                .search_with_progress(&first, &control, |_| {})
                .unwrap();
            assert!(session.cache.as_ref().unwrap().1.probe(key).is_some());

            // Stop before any entry can be written under the new settings.
            control.stop();
            let next = request(next_variant, next_fen, next_quiescence);
            session
                .search_with_progress(&next, &control, |_| {})
                .unwrap();
            assert!(session.cache.as_ref().unwrap().1.probe(key).is_none());
        }
    }

    #[test]
    fn warm_mate_search_still_returns_the_line_used_to_count_mate_distance() {
        let request = SearchRequest::new(
            1,
            Variant::Standard,
            "7k/8/5KQ1/8/8/8/8/8 w - - 0 1",
            vec![],
            SearchLimit::TimeMillis(60_000),
            SearchOptions { quiescence: false },
        )
        .unwrap();
        let mut session = SearchSession::default();
        let control = SearchControl::default();
        for _ in 0..2 {
            let result = session
                .search_with_progress(&request, &control, |_| {})
                .unwrap();
            assert_eq!(result.score, MATE);
            assert_eq!(result.pv.len(), 1);
            let mut replay = request.position().clone();
            replay.apply(result.pv[0].as_str()).unwrap();
            assert_eq!(replay.game_state(), GameState::Mate);
        }
    }

    fn quiescence(fen: &str, depth: u8, enabled: bool) -> (i32, u64) {
        let p = Position::from_fen(Variant::Standard, fen).unwrap();
        let control = SearchControl::default();
        let mut table = Table::new();
        let mut worker = Worker::new(&control, Instant::now(), None, enabled, &p, &mut table);
        worker.nodes = 0;
        let score = worker.quiesce(&p, depth, -MATE, MATE);
        (score, worker.nodes)
    }

    #[test]
    fn aspiration_retries_both_failed_edges_and_builds_a_principal_variation() {
        for (variant, fen) in [
            (Variant::Standard, "7k/8/8/8/8/8/8/KR6 w - - 0 1"),
            (Variant::Duck, "7k/8/8/3$4/8/8/8/K7 w - - 0 10"),
        ] {
            let p = Position::from_fen(variant, fen).unwrap();
            let control = SearchControl::default();
            let mut table = Table::new();
            let mut worker = Worker::new(&control, Instant::now(), None, true, &p, &mut table);
            let expected = worker
                .search_iteration(&p, &mut p.legal_internal(), 3, &[], None)
                .unwrap();
            for offset in [-ASPIRATION_MARGIN, ASPIRATION_MARGIN] {
                let mut table = Table::new();
                let mut worker = Worker::new(&control, Instant::now(), None, true, &p, &mut table);
                assert_eq!(
                    worker.search_iteration(
                        &p,
                        &mut p.legal_internal(),
                        3,
                        &[],
                        Some(expected + offset)
                    ),
                    Some(expected),
                );
                assert_eq!(worker.pv[0].len(), 3, "{variant:?}, offset {offset}");
                let mut replay = p.clone();
                for &m in &worker.pv[0] {
                    let coordinate = replay.external(replay.turn, m);
                    replay.apply(coordinate.as_str()).unwrap();
                }
            }
        }
    }

    #[test]
    fn aspiration_finds_mate_outside_the_initial_window() {
        let p = Position::from_fen(Variant::Standard, "7k/8/5KQ1/8/8/8/8/8 w - - 0 1").unwrap();
        let control = SearchControl::default();
        let mut table = Table::new();
        let mut worker = Worker::new(&control, Instant::now(), None, false, &p, &mut table);
        assert_eq!(
            worker.search_iteration(&p, &mut p.legal_internal(), 1, &[], Some(0)),
            Some(MATE)
        );
        let m = worker.pv[0][0];
        assert_eq!(p.make_unrecorded(m).game_state(), GameState::Mate);
    }

    #[test]
    fn a_later_move_inside_the_window_is_searched_again() {
        let p = Position::from_fen(Variant::Standard, "7k/8/8/8/8/8/8/KR6 w - - 0 1").unwrap();
        let control = SearchControl::default();
        let mut table = Table::new();
        let mut worker = Worker::new(&control, Instant::now(), None, false, &p, &mut table);
        let mut windows = Vec::new();
        let score = worker.search_child(false, (10, 30), |_, window| {
            windows.push(window);
            if windows.len() == 1 {
                -11
            } else {
                -20
            }
        });
        assert_eq!(score, 20);
        assert_eq!(windows, [(-11, -10), (-30, -10)]);
    }

    #[test]
    fn the_first_legal_move_after_an_illegal_candidate_builds_a_full_line() {
        let p = Position::from_fen(Variant::Standard, "k3r3/8/8/8/8/8/4R3/4K3 w - - 0 1").unwrap();
        let illegal = p
            .pseudo_moves()
            .into_iter()
            .find(|m| m.coordinate().as_str() == "e2d2")
            .unwrap();
        let control = SearchControl::default();
        let mut table = Table::new();
        let mut worker = Worker::new(&control, Instant::now(), None, false, &p, &mut table);
        let window = (-MATE - 1, MATE + 1);
        let expected = worker.alpha_beta(&p, None, 2, 0, window, &[]);
        let mut table = Table::new();
        let mut worker = Worker::new(&control, Instant::now(), None, false, &p, &mut table);
        assert_eq!(
            worker.alpha_beta(&p, None, 2, 0, window, &[illegal]),
            expected
        );
        assert_eq!(worker.pv[0].len(), 2);
        let mut replay = p.clone();
        for &m in &worker.pv[0] {
            replay.apply(m.coordinate().as_str()).unwrap();
        }
    }

    #[test]
    fn cancellation_at_the_start_of_a_research_discards_its_pv() {
        let p = Position::from_fen(Variant::Standard, "7k/8/8/8/8/8/8/KR6 w - - 0 1").unwrap();
        let control = SearchControl::default();
        let mut table = Table::new();
        let mut worker = Worker::new(&control, Instant::now(), None, false, &p, &mut table);
        let mut windows = Vec::new();
        worker.search_child(false, (10, 30), |worker, window| {
            windows.push(window);
            if windows.len() == 1 {
                control.stop();
                -11
            } else {
                worker.alpha_beta(&p, None, 2, 0, window, &[])
            }
        });
        assert_eq!(windows, [(-11, -10), (-30, -10)]);
        assert!(worker.interrupted);
        assert!(worker.pv[0].is_empty());
        assert!(worker.table.probe(p.search_key()).is_none());
    }

    #[test]
    fn duck_placement_candidates_keep_each_destination_in_first_seen_order() {
        let p = Position::from_fen(Variant::Duck, "7k/8/8/8/3$4/8/8/KN3N2 w - - 0 1").unwrap();
        let mut expected = Vec::new();
        let mut duplicates = 0;
        for m in p.pseudo_moves() {
            if !m.capture && p.occupied() & bit(m.to) == 0 {
                if expected.contains(&m.to) {
                    duplicates += 1;
                } else {
                    expected.push(m.to);
                }
            }
        }
        assert!(duplicates > 0);
        assert_eq!(duck_placement_candidates(&p), expected);
    }

    #[test]
    fn a_quiet_beta_cutoff_improves_move_ordering_for_the_moving_side() {
        let p = Position::from_fen(Variant::Standard, "7k/8/8/8/8/8/8/KR6 w - - 0 1").unwrap();
        let control = SearchControl::default();
        let mut table = Table::new();
        let mut worker = Worker::new(&control, Instant::now(), None, false, &p, &mut table);
        worker.alpha_beta(&p, None, 1, 0, (-MATE, -MATE + 1), &[]);
        let m = worker.pv[0][0];
        assert_eq!(worker.quiet_history[p.turn][m.from][m.to], 1);
        assert_eq!(worker.quiet_history[p.turn ^ 1][m.from][m.to], 0);
        assert_eq!(
            ordered_scores(
                &p,
                &[m],
                None,
                None,
                None,
                Some(&worker.quiet_history[p.turn])
            ),
            [score_move(&p, m) + 1]
        );
    }

    #[test]
    fn search_key_tracks_rule_and_evaluation_state_beyond_the_opening_hash() {
        let mut castled =
            Position::from_fen(Variant::Standard, "4k3/8/8/8/8/8/8/4K2R w K - 0 10").unwrap();
        castled.apply("e1g1").unwrap();
        let fen = "4k3/8/8/8/8/8/8/5RK1 b - - 1 10";
        let parsed = Position::from_fen(Variant::Standard, fen).unwrap();
        assert_eq!(castled.hash_key(), parsed.hash_key());
        assert_ne!(castled.search_key(), parsed.search_key());

        for (a, b) in [
            (fen, "4k3/8/8/8/8/8/8/5RK1 b - - 99 10"),
            (
                "4k3/8/8/8/8/8/8/5RK1 b - - 1 1",
                "4k3/8/8/8/8/8/8/5RK1 b - - 1 2",
            ),
            (
                "4k3/8/8/8/8/8/4$3/5RK1 b - - 1 10",
                "4k3/8/8/8/8/4$3/8/5RK1 b - - 1 10",
            ),
        ] {
            let a = Position::from_fen(Variant::Duck, a).unwrap();
            let b = Position::from_fen(Variant::Duck, b).unwrap();
            assert_eq!(a.hash_key(), b.hash_key());
            assert_ne!(a.search_key(), b.search_key());
        }
    }

    #[test]
    fn different_repetition_histories_allow_move_hints_and_require_fresh_scores() {
        let fen = "6nk/8/8/8/8/8/P7/KNR5 w - - 0 10";
        let mut p = Position::from_fen(Variant::Standard, fen).unwrap();
        for m in ["b1c3", "g8f6", "c3b1", "f6g8", "b1c3", "g8f6", "c3b1"] {
            p.apply(m).unwrap();
        }
        let fresh = Position::from_fen(Variant::Standard, &p.fen()).unwrap();
        assert_eq!(p.search_key(), fresh.search_key());
        let control = SearchControl::default();
        let mut table = Table::new();
        let mut worker = Worker::new(&control, Instant::now(), None, false, &p, &mut table);
        let mut moves = p.legal_internal();
        let draw = moves
            .iter()
            .copied()
            .find(|m| m.coordinate().as_str() == "f6g8")
            .unwrap();
        let context = worker.enter(&p, draw);
        assert!(p
            .make_unrecorded(draw)
            .drawn_in_search(&worker.seen[worker.seen_from..]));
        worker.leave(&p, draw, context);

        let entry = Entry::new(
            p.search_key(),
            worker.repetition_key,
            2,
            1000,
            (0, 1000),
            draw,
        );
        worker.table.store(entry);
        assert_eq!(
            worker.alpha_beta(&p, Some(&mut moves), 2, 0, (0, 1000), &[]),
            1000
        );
        assert!(worker.pv[0].is_empty());

        let mut table = Table::new();
        let mut worker = Worker::new(&control, Instant::now(), None, false, &fresh, &mut table);
        let mut moves = fresh.legal_internal();
        let context = worker.enter(&fresh, draw);
        assert!(!fresh
            .make_unrecorded(draw)
            .drawn_in_search(&worker.seen[worker.seen_from..]));
        worker.leave(&fresh, draw, context);
        worker.table.store(entry);
        assert_eq!(
            worker.table.probe(fresh.search_key()).unwrap().best_move,
            draw
        );
        assert_ne!(
            worker.alpha_beta(&fresh, Some(&mut moves), 2, 0, (0, 1000), &[]),
            1000
        );
        assert!(!worker.pv[0].is_empty());
    }

    #[test]
    fn table_on_and_off_agree_on_scores_and_build_replayable_lines() {
        for (variant, fen, depth) in [
            (
                Variant::Standard,
                "rnbqkbnr/pppppppp/8/8/8/8/PPPPPPPP/RNBQKBNR w KQkq - 0 1",
                3,
            ),
            (Variant::Standard, "7k/8/8/8/8/8/8/KR6 w - - 98 10", 3),
            (Variant::Chess960, "8/4k3/8/8/8/8/8/1R2K1R1 w KQ - 0 10", 3),
            (Variant::KingOfTheHill, "7k/8/8/8/8/4K3/8/8 w - - 0 10", 3),
            (Variant::Duck, "7k/8/8/3$4/8/8/8/K7 w - - 0 10", 3),
        ] {
            let p = Position::from_fen(variant, fen).unwrap();
            let control = SearchControl::default();
            for quiescence in [false, true] {
                let mut reference = None;
                for mut table in [Table::with_slots(0), Table::new()] {
                    let mut worker =
                        Worker::new(&control, Instant::now(), None, quiescence, &p, &mut table);
                    let mut previous_pv = Vec::new();
                    let mut scores = Vec::new();
                    for depth in 1..=depth {
                        let mut moves = p.legal_internal();
                        let window = (-MATE - 1, MATE + 1);
                        let score = if variant == Variant::Duck {
                            worker.alpha_beta_duck(
                                &p,
                                Some(&mut moves),
                                depth,
                                0,
                                window,
                                &previous_pv,
                            )
                        } else {
                            worker.alpha_beta(&p, Some(&mut moves), depth, 0, window, &previous_pv)
                        };
                        scores.push(score);
                        previous_pv.clone_from(&worker.pv[0]);
                        let mut replay = p.clone();
                        assert!(!previous_pv.is_empty(), "{variant:?}: {fen}");
                        for &m in &previous_pv {
                            let coordinate = replay.external(replay.turn, m);
                            replay.apply(coordinate.as_str()).unwrap();
                        }
                        assert!(
                            previous_pv.len() == depth as usize
                                || !matches!(
                                    replay.game_state(),
                                    GameState::Play | GameState::Check
                                ),
                            "{variant:?}: {fen}"
                        );
                    }
                    if let Some(expected) = &reference {
                        assert_eq!(&scores, expected, "{variant:?}: {fen}");
                    }
                    reference = Some(scores);
                }
            }
        }
    }

    #[test]
    fn chess960_castling_search_branch_produces_a_replayable_principal_variation() {
        for (fen, castle) in [
            ("8/4k3/8/8/8/8/8/1R2K1R1 w KQ - 0 1", "e1b1"),
            ("1r2k1r1/8/8/8/8/8/4K3/8 b kq - 0 1", "e8b8"),
        ] {
            let p = Position::from_fen(Variant::Chess960, fen).unwrap();
            let control = SearchControl::default();
            let mut table = Table::new();
            let mut worker = Worker::new(&control, Instant::now(), None, true, &p, &mut table);
            // Isolate the castling branch so evaluation changes cannot hide it behind another move.
            let mut moves: Vec<_> = p
                .legal_internal()
                .into_iter()
                .filter(|&m| p.external(p.turn, m).as_str() == castle)
                .collect();
            assert_eq!(moves.len(), 1);
            worker.alpha_beta(&p, Some(&mut moves), 2, 0, (-MATE - 1, MATE + 1), &[]);
            assert_eq!(p.external(p.turn, worker.pv[0][0]).as_str(), castle);
            let mut replay = p.clone();
            for &m in &worker.pv[0] {
                let coordinate = replay.external(replay.turn, m);
                replay.apply(coordinate.as_str()).unwrap();
            }
        }
    }

    #[test]
    fn quiescence_detects_mate_at_depth_limit() {
        assert_eq!(
            quiescence("7k/6Q1/5K2/8/8/8/8/8 b - - 0 1", 0, true).0,
            -MATE
        );
    }

    #[test]
    fn disabled_quiescence_still_detects_mate_without_searching_replies() {
        let (score, nodes) = quiescence("7k/6Q1/5K2/8/8/8/8/8 b - - 0 1", 5, false);
        assert_eq!(score, -MATE);
        assert_eq!(nodes, 1);
    }

    #[test]
    fn checked_leaf_with_a_quiet_escape_is_not_mate() {
        let fen = "4k3/8/8/8/8/8/4R3/4K3 b - - 0 1";
        let evaluation = Position::from_fen(Variant::Standard, fen)
            .unwrap()
            .evaluate();
        for (depth, enabled) in [(5, false), (0, true)] {
            let (score, nodes) = quiescence(fen, depth, enabled);
            assert_eq!(score, evaluation);
            assert_ne!(score, -MATE);
            assert_eq!(nodes, 1);
        }
    }

    #[test]
    fn quiescence_detects_mate_after_quiet_check_evasions_run_out() {
        assert_eq!(
            quiescence("7k/6Q1/5K2/8/8/8/8/8 b - - 0 1", 1, true).0,
            -MATE
        );
    }

    #[test]
    fn quiescence_searches_quiet_check_evasions() {
        let fen = "4k3/8/8/8/8/8/4R3/4K3 b - - 0 1";
        assert!(quiescence(fen, 1, true).1 > 1);
        assert_eq!(quiescence(fen, 0, true).1, 1);
        assert_eq!(quiescence(fen, 1, false).1, 1);
    }
}
