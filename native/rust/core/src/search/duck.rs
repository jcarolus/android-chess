use super::{ordered_scores, pick, pv_tail, Entry, Worker, MATE};
use crate::position::*;

/// History for all placements and for quiet moves with the same piece type and destination.
pub(super) struct History {
    placement: [[i16; 64]; 2],
    quiet: [[[i16; 64]; 64]; 12],
}

impl History {
    pub(super) fn new() -> Self {
        Self {
            placement: [[0; 64]; 2],
            quiet: [[[0; 64]; 64]; 12],
        }
    }

    fn score(&self, side: usize, piece: usize, m: Move, square: usize) -> i32 {
        let quiet = if m.capture || m.promotion.is_some() {
            0
        } else {
            self.quiet[side * 6 + piece][m.to][square]
        };
        i32::from(self.placement[side][square]) + i32::from(quiet)
    }

    pub(super) fn cutoff(&mut self, p: &Position, m: Move, tried: u64, depth: u8) {
        let Some(winner) = m.duck else { return };
        let bonus = i16::from(depth).pow(2).min(128);
        let piece = p.piece(p.turn, m.from).unwrap();
        let quiet = !m.capture && m.promotion.is_none();
        for square in squares(tried) {
            let change = if square == winner { bonus } else { -bonus };
            let placement = &mut self.placement[p.turn][square];
            *placement = (*placement + change).clamp(-1024, 1024);
            if quiet {
                let score = &mut self.quiet[p.turn * 6 + piece][m.to][square];
                *score = (*score + change).clamp(-1024, 1024);
            }
        }
    }
}

/// Squares that would make a generated piece move unavailable.
fn blockers(p: &Position, m: Move) -> u64 {
    blockers_for(p, m, p.piece(p.turn, m.from).unwrap())
}

fn blockers_for(p: &Position, m: Move, piece: usize) -> u64 {
    if m.castle {
        let base = m.from / 8 * 8;
        let short = m.to % 8 == 6;
        let rook = base + p.rook_file[p.turn][usize::from(short)];
        let rook_to = base + if short { 5 } else { 3 };
        return between(m.from, m.to) | bit(m.to) | between(rook, rook_to) | bit(rook_to);
    }
    if m.double || matches!(piece, BISHOP | ROOK | QUEEN) {
        between(m.from, m.to) | bit(m.to)
    } else {
        bit(m.to)
    }
}

/// Look out from the king using piece occupancy. Intersect the blocker squares
/// for each attacker because the duck must prevent all immediate captures.
fn king_shields(p: &Position, side: usize) -> u64 {
    let mut safe = u64::MAX;
    for blockers in p.king_capture_paths(side) {
        safe &= blockers;
        if safe == 0 {
            break;
        }
    }
    safe
}

/// A reply reused for the same piece move must still be playable and lead to
/// the same position with the same repetition history.
#[derive(Default)]
pub(super) struct Refutations(Vec<(u64, Repetition)>);

impl Refutations {
    pub(super) fn record(&mut self, p: &Position, reply: Move) {
        if let Some(square) = reply.duck {
            self.0
                .push((blockers(p, reply) | bit(square), p.repetition()));
        }
    }

    pub(super) fn rules_out(&self, p: &Position, square: u64) -> bool {
        self.0.iter().any(|(required_empty, repetition)| {
            required_empty & square == 0 && p.repetition() == *repetition
        })
    }
}

#[derive(Default)]
pub(super) struct MovePlacements {
    searched: u16,
    neutral: bool,
}

pub(super) struct PlacementCounts {
    by_square: [u16; 64],
    unique: u16,
}

impl PlacementCounts {
    pub(super) fn new() -> Self {
        Self {
            by_square: [0; 64],
            unique: 0,
        }
    }

    pub(super) fn record(&mut self, state: &mut MovePlacements, square: usize, neutral: bool) {
        state.searched += 1;
        state.neutral |= neutral;
        self.unique += u16::from(self.by_square[square] == 0);
        self.by_square[square] += 1;
    }

    pub(super) fn skip(
        &self,
        state: &MovePlacements,
        square: usize,
        neutral: bool,
        quiet: bool,
        depth: u8,
    ) -> bool {
        let depth = u16::from(depth);
        let count = self.by_square[square];
        // Search one neutral square per piece move. Limit late quiet
        // placements by attempts for this piece move and across the node.
        (neutral && state.neutral)
            || (quiet
                && (state.searched >= 8 + 2 * depth
                    || count >= 4 + depth
                    || (count == 0 && self.unique >= 24 + 2 * depth)))
    }
}

pub(super) struct Placements {
    pub(super) safe: u64,
    pub(super) ordered: Vec<usize>,
    pub(super) tactical: u64,
    pub(super) neutral: u64,
}

impl Placements {
    pub(super) fn new(
        next: &Position,
        m: Move,
        piece: usize,
        hint: Option<usize>,
        history: &History,
    ) -> Self {
        let legal = !next.occupied();
        let mover = next.turn ^ 1;
        let safe = king_shields(next, mover) & legal;
        let mut lifted = next.without_records();
        lifted.duck = None;
        let mut capture_value = [0; 64];
        let mut blocked_replies = [0u16; 64];
        let mut knight_targets = 0;
        let mut avoids_recapture = legal;
        for reply in lifted.pseudo_moves() {
            let piece = lifted.piece(lifted.turn, reply.from).unwrap();
            let blocked = blockers_for(&lifted, reply, piece) & legal;
            if m.capture && reply.capture && reply.to == m.to {
                avoids_recapture &= blocked;
            }
            if piece == KNIGHT {
                knight_targets |= blocked;
            }
            let victim = if !reply.capture {
                0
            } else if reply.ep {
                VALUES[PAWN]
            } else {
                lifted.piece(mover, reply.to).map_or(0, |p| VALUES[p])
            };
            for square in squares(blocked) {
                capture_value[square] = capture_value[square].max(victim);
                blocked_replies[square] += 1;
            }
        }

        let occupied = lifted.occupied();
        let enemy_king = lifted.king(lifted.turn);
        let enemy_escapes = lifted.range_with(KING, lifted.turn, enemy_king, occupied);
        let own_king_ring = lifted.range_with(KING, mover, lifted.king(mover), occupied);
        // A duck on a line to the enemy king can force the opponent to lift
        // that shield. Keep these placements available even without a blocked reply.
        let mut attacking_lines = 0;
        for piece in [BISHOP, ROOK, QUEEN] {
            let attackers = lifted.pieces[mover][piece]
                & lifted.range_with(piece, lifted.turn, enemy_king, occupied);
            for attacker in squares(attackers) {
                attacking_lines |= between(enemy_king, attacker);
            }
        }
        let mut ordered: Vec<_> = squares(legal).collect();
        let mut tactical = enemy_escapes | attacking_lines;
        let mut neutral = 0;
        for square in squares(legal) {
            if capture_value[square] > 0 {
                tactical |= bit(square);
            }
            if blocked_replies[square] == 0 && (tactical | own_king_ring) & bit(square) == 0 {
                neutral |= bit(square);
            }
        }
        ordered.sort_by_cached_key(|&square| {
            let mask = bit(square);
            let priority = if capture_value[square] > 0 {
                (5, capture_value[square])
            } else if (enemy_escapes | attacking_lines) & mask != 0 {
                (4, i32::from(attacking_lines & mask != 0))
            } else if knight_targets & mask != 0 {
                (3, 0)
            } else if own_king_ring & mask != 0 {
                (2, 0)
            } else {
                (i32::from(blocked_replies[square] > 0), 0)
            };
            std::cmp::Reverse((
                safe & mask != 0,
                hint == Some(square),
                avoids_recapture & mask != 0,
                priority,
                history.score(mover, piece, m, square),
                blocked_replies[square],
            ))
        });
        Self {
            safe,
            ordered,
            tactical: tactical & legal,
            neutral,
        }
    }
}

impl Worker<'_> {
    pub(super) fn alpha_beta_duck(
        &mut self,
        p: &Position,
        moves: Option<&mut [Move]>,
        depth: u8,
        ply: usize,
        (mut alpha, beta): (i32, i32),
        previous_pv: &[Move],
    ) -> i32 {
        debug_assert_eq!(p.variant, Variant::Duck);
        self.pv[ply].clear();
        if self.stopped() {
            return alpha;
        }
        if p.drawn_in_search(&self.seen[self.seen_from..]) {
            return 0;
        }
        let alpha_start = alpha;
        let key = p.search_key();
        let entry = (depth > 0).then(|| self.table.probe(key)).flatten();
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
        let enemy_king = p.king(p.turn ^ 1);
        if let Some(&capture) = moves.iter().find(|m| m.to == enemy_king && m.capture) {
            self.nodes += 1;
            self.pv[ply].push(capture);
            return MATE;
        }
        if depth == 0 {
            self.nodes += 1;
            return p.evaluate();
        }
        let pv_move = previous_pv.first().copied();
        let table_move = entry.map(|e| e.best_move);
        let duckless_key = p.duckless_search_key();
        let duckless_entry = self.table.probe(duckless_key).and_then(|entry| {
            let hint = entry.best_move;
            let m = moves.iter().find(|m| m.same_piece_move(hint))?;
            let square = hint.duck?;
            let next = p.make_unrecorded(*m);
            (next.occupied() & bit(square) == 0).then_some((entry, *m))
        });
        // The shared entry is a lower bound for this legal complete move.
        // An irreversible move resets repetition, so its complete child is
        // identical even if the duck started on a different square.
        if beta == alpha + 1 && ply > 0 {
            if let Some((entry, _)) = duckless_entry.filter(|(_, m)| p.is_irreversible(*m)) {
                if let Some(score) = entry.cutoff(self.repetition_key, depth, alpha, beta) {
                    return score;
                }
            }
        }
        let duckless_hint = duckless_entry.map(|(entry, _)| entry.best_move);
        let table_hint = table_move.or(duckless_hint);
        let mut scores = ordered_scores(p, moves, pv_move, table_hint, self.best_at_ply[ply], None);
        let selective = ply > 0 && depth <= 8 && beta == alpha + 1 && alpha > -MATE && beta < MATE;
        let mut best = -MATE - 1;
        let mut first_turn = true;
        let mut counts = PlacementCounts::new();
        let in_danger = p.attacked(p.turn, p.king(p.turn));
        for index in 0..moves.len() {
            pick(moves, &mut scores, index);
            let piece_move = moves[index];
            let mut m = piece_move;
            let next = p.make_unrecorded(m);
            let mut line = Vec::new();
            let context = self.enter(p, m);
            let mut tried = 0;
            let value = if next.drawn_in_search(&self.seen[self.seen_from..]) {
                0
            } else {
                let mut best_duck = -MATE - 1;
                let hint = [pv_move, table_move, duckless_hint]
                    .into_iter()
                    .flatten()
                    .find(|hint| hint.same_piece_move(m))
                    .and_then(|hint| hint.duck);
                let piece = p.piece(p.turn, m.from).unwrap();
                let placements =
                    Placements::new(&next, m, piece, hint, self.duck_history.as_deref().unwrap());
                let mut refutations = Refutations::default();
                let mut tried_placements = MovePlacements::default();
                let mut duck_board = next;
                let quiet = !piece_move.capture && piece_move.promotion.is_none();
                let late_quiet = selective && depth >= 4 && index >= 8 && quiet && !in_danger;
                for square in placements.ordered {
                    let mask = bit(square);
                    let turn = Move {
                        duck: Some(square),
                        ..piece_move
                    };
                    duck_board.duck = turn.duck;
                    let drawn = self.seen.len() - self.seen_from >= 2
                        && duck_board.drawn_in_search(&self.seen[self.seen_from..]);
                    let safe = placements.safe & mask != 0;
                    let neutral = placements.neutral & mask != 0;
                    // Keep all tactical and hinted placements. Heuristic limits
                    // are only useful after searching a non-losing placement.
                    if selective
                        && best_duck > -MATE
                        && !drawn
                        && hint != Some(square)
                        && placements.tactical & mask == 0
                        && (counts.skip(&tried_placements, square, neutral, quiet, depth)
                            || refutations.rules_out(&duck_board, mask))
                    {
                        continue;
                    }
                    let tail = pv_tail(previous_pv, turn);
                    let value = if drawn || !safe {
                        if self.stopped() {
                            break;
                        }
                        self.pv[ply + 1].clear();
                        if drawn {
                            0
                        } else {
                            self.nodes += 1;
                            -MATE
                        }
                    } else {
                        // Count distinct piece moves so placements of one move
                        // do not make the next piece move look late.
                        let reduced =
                            late_quiet && hint != Some(square) && placements.tactical & mask == 0;
                        let mut value = alpha + 1;
                        if reduced {
                            value = -self.alpha_beta_duck(
                                &duck_board,
                                None,
                                depth - 2,
                                ply + 1,
                                (-beta, -alpha),
                                tail,
                            );
                            if value <= -MATE {
                                // A reduced search cannot establish a forced loss.
                                value = alpha + 1;
                            }
                        }
                        if !self.interrupted && value > alpha {
                            value =
                                self.search_child(first_turn, (alpha, beta), |worker, window| {
                                    worker.alpha_beta_duck(
                                        &duck_board,
                                        None,
                                        depth - 1,
                                        ply + 1,
                                        window,
                                        tail,
                                    )
                                });
                        }
                        if selective && !reduced && !self.interrupted && value <= alpha {
                            if let Some(reply) = self.pv[ply + 1].first().copied() {
                                refutations.record(&duck_board, reply);
                            }
                        }
                        value
                    };
                    first_turn = false;
                    counts.record(&mut tried_placements, square, neutral);
                    tried |= mask;
                    if value > best_duck {
                        best_duck = value;
                        m = turn;
                        line.clone_from(&self.pv[ply + 1]);
                        if value == -MATE && !drawn && !safe {
                            let king = duck_board.king(p.turn);
                            line.extend(
                                duck_board
                                    .pseudo_moves()
                                    .into_iter()
                                    .find(|m| m.to == king && m.capture),
                            );
                        }
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
                if !self.interrupted {
                    self.duck_history
                        .as_deref_mut()
                        .unwrap()
                        .cutoff(p, m, tried, depth);
                }
                break;
            }
        }
        // A move from the same piece position can be reused for ordering once
        // both parts of the turn have been checked against the current duck.
        if !self.interrupted && best > alpha_start && best.abs() < MATE {
            if let Some(&best_move) = self.pv[ply].first().filter(|m| m.duck.is_some()) {
                self.table.store(Entry::new(
                    duckless_key,
                    self.repetition_key,
                    depth,
                    best,
                    (best - 1, best),
                    best_move,
                ));
            }
        }
        self.store(key, depth, ply, best, alpha_start, beta);
        best
    }
}

#[cfg(test)]
mod tests {
    use super::*;

    fn position(fen: &str) -> Position {
        Position::from_fen(Variant::Duck, fen).unwrap()
    }

    #[test]
    fn blocker_masks_match_duck_move_generation() {
        for fen in [
            "r3k2r/1p6/8/8/3pP3/8/4K3/8 b kq e3 0 1",
            "k7/2p5/8/8/8/4N3/6P1/4K3 w - - 0 1",
            "4r2k/8/8/8/8/8/8/4K3 b - - 0 1",
        ] {
            let mut p = position(fen);
            let moves = p.pseudo_moves();
            let mut castle = false;
            let mut en_passant = false;
            let mut double_push = false;
            for m in moves {
                castle |= m.castle;
                en_passant |= m.ep;
                double_push |= m.double;
                let blocked = blockers(&p, m);
                p.duck = None;
                for square in squares(!p.occupied()) {
                    p.duck = Some(square);
                    let generated = p.pseudo_moves().into_iter().any(|reply| reply == m);
                    assert_eq!(
                        generated,
                        blocked & bit(square) == 0,
                        "{fen}, {m:?}, {square}"
                    );
                }
                p.duck = None;
            }
            if fen.starts_with("r3k2r") {
                assert!(castle && en_passant && double_push);
            }
        }
    }

    #[test]
    fn shields_intersect_capture_paths_and_exclude_the_old_duck_square() {
        let e4 = bit(square("e4").unwrap());
        let p = position("4r2k/8/8/8/4$3/8/8/4K3 b - - 0 1");
        let safe = king_shields(&p, WHITE);
        assert_ne!(safe & e4, 0);
        let hints = History::new();
        let placements = Placements::new(&p, p.pseudo_moves()[0], ROOK, None, &hints);
        assert_eq!(placements.safe, safe & !p.occupied());
        assert!(placements.ordered.iter().all(|&s| bit(s) != e4));
        assert_eq!(
            placements.ordered.len(),
            (!p.occupied()).count_ones() as usize
        );

        let crossing = position("4r2k/8/8/8/4$3/8/8/r3K3 b - - 0 1");
        assert_eq!(king_shields(&crossing, WHITE), 0);
        let knight = position("4r2k/8/8/8/4$3/8/2n5/4K3 b - - 0 1");
        assert_eq!(king_shields(&knight, WHITE), 0);
    }

    #[test]
    fn order_blocks_higher_value_captures_first() {
        let p = position("q5rk/8/8/8/8/8/8/Q3K1R1 b - - 0 1");
        let m = p.pseudo_moves()[0];
        let ordered = Placements::new(&p, m, QUEEN, None, &History::new()).ordered;
        let a4 = square("a4").unwrap();
        let g4 = square("g4").unwrap();
        assert!(
            ordered.iter().position(|&s| s == a4).unwrap()
                < ordered.iter().position(|&s| s == g4).unwrap()
        );
    }

    #[test]
    fn refutation_requires_an_available_duck_square_and_unblocked_path() {
        let mut p = position("4r2k/8/8/8/8/8/4$3/4K3 b - - 0 1");
        let reply = p
            .pseudo_moves()
            .into_iter()
            .find(|m| m.coordinate().as_str() == "e8e4")
            .unwrap();
        let mut refutations = Refutations::default();
        let duck = square("d3").unwrap();
        refutations.record(
            &p,
            Move {
                duck: Some(duck),
                ..reply
            },
        );
        for (target, ruled_out) in [("d3", false), ("e6", false), ("d4", true)] {
            let square = square(target).unwrap();
            p.duck = Some(square);
            assert_eq!(
                refutations.rules_out(&p, bit(square)),
                ruled_out,
                "{target}"
            );
        }
    }

    #[test]
    fn king_capture_shortcut_finds_a_replayable_win_at_the_horizon() {
        use super::super::{SearchControl, Table, FULL_WINDOW};
        use std::time::Instant;

        let mut p = position("7k/8/8/8/3$4/8/4r3/4K3 b - - 0 1");
        let control = SearchControl::default();
        let mut table = Table::with_slots(0);
        let mut worker = Worker::new(&control, Instant::now(), None, false, &p, &mut table);
        assert_eq!(
            worker.alpha_beta_duck(&p, None, 0, 0, FULL_WINDOW, &[]),
            MATE
        );
        let capture = worker.pv[0][0].coordinate();
        assert_eq!(capture.as_str(), "e2e1");
        p.apply(capture.as_str()).unwrap();
        assert_eq!(p.game_state(), GameState::Mate);
    }
}
