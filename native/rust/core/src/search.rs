use crate::position::*;
mod table;
use std::sync::atomic::{AtomicBool, Ordering};
use std::time::{Duration, Instant};
use table::{Entry, Table};
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
    visits: u64,
    interrupted: bool,
    /// Positions seen along the current line, from the game history onwards.
    seen: Vec<Repetition>,
    /// Entries of `seen` before this index precede an irreversible move.
    seen_from: usize,
    repetition_key: u64,
    table: Table,
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
            table: Table::new(),
            best_at_ply: [None; 20],
            quiet_history: Box::new([[[0; 64]; 64]; 2]),
            pv: std::array::from_fn(|_| Vec::new()),
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
                    let value = -self.alpha_beta_duck(
                        &duck_board,
                        None,
                        depth - 1,
                        ply + 1,
                        (-beta, -alpha),
                        pv_tail(previous_pv, turn),
                    );
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
            let value = if depth == 1 {
                -self.quiesce(&next, 5, -beta, -alpha)
            } else {
                -self.alpha_beta(
                    &next,
                    None,
                    depth - 1,
                    ply + 1,
                    (-beta, -alpha),
                    pv_tail(previous_pv, m),
                )
            };
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
    let mut worker = Worker::new(control, start, budget, request.options.quiescence, p);
    let mut moves = p.legal_internal();
    let depths = match request.limit {
        SearchLimit::Depth(depth) => depth..=depth,
        _ => 1..=14,
    };
    let mut previous_pv = Vec::new();
    for depth in depths {
        // The full root window includes mate scores so even a forced win or loss builds a line.
        let score = if p.variant == Variant::Duck {
            worker.alpha_beta_duck(
                p,
                Some(&mut moves),
                depth,
                0,
                (-MATE - 1, MATE + 1),
                &previous_pv,
            )
        } else {
            worker.alpha_beta(
                p,
                Some(&mut moves),
                depth,
                0,
                (-MATE - 1, MATE + 1),
                &previous_pv,
            )
        };
        if worker.interrupted {
            break;
        }
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

#[cfg(test)]
mod tests {
    use super::*;

    fn quiescence(fen: &str, depth: u8, enabled: bool) -> (i32, u64) {
        let p = Position::from_fen(Variant::Standard, fen).unwrap();
        let control = SearchControl::default();
        let mut worker = Worker::new(&control, Instant::now(), None, enabled, &p);
        worker.nodes = 0;
        let score = worker.quiesce(&p, depth, -MATE, MATE);
        (score, worker.nodes)
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
        let mut worker = Worker::new(&control, Instant::now(), None, false, &p);
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
        let mut worker = Worker::new(&control, Instant::now(), None, false, &p);
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

        let mut worker = Worker::new(&control, Instant::now(), None, false, &fresh);
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
                for table in [Table::with_slots(0), Table::new()] {
                    let mut worker = Worker::new(&control, Instant::now(), None, quiescence, &p);
                    worker.table = table;
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
            let mut worker = Worker::new(&control, Instant::now(), None, true, &p);
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
