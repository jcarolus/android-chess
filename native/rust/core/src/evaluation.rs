use crate::position::*;
const KING_ENDINGS: [i32; 64] = [
    0, 6, 12, 18, 18, 12, 6, 0, 6, 12, 18, 24, 24, 18, 12, 6, 12, 18, 24, 32, 32, 24, 18, 12, 18,
    24, 32, 48, 48, 32, 24, 18, 18, 24, 32, 48, 48, 32, 24, 18, 12, 18, 24, 32, 32, 24, 18, 12, 6,
    12, 18, 24, 24, 18, 12, 6, 0, 6, 12, 18, 18, 12, 6, 0,
];
const KBNK: [[i32; 64]; 2] = [
    [
        0, 10, 20, 30, 40, 50, 60, 70, 10, 20, 30, 40, 50, 60, 70, 60, 20, 30, 40, 50, 60, 70, 60,
        50, 30, 40, 50, 60, 70, 60, 50, 40, 40, 50, 60, 70, 60, 50, 40, 30, 50, 60, 70, 60, 50, 40,
        30, 20, 60, 70, 60, 50, 40, 30, 20, 10, 70, 60, 50, 40, 30, 20, 10, 0,
    ],
    [
        70, 60, 50, 40, 30, 20, 10, 0, 60, 70, 60, 50, 40, 30, 20, 10, 50, 60, 70, 60, 50, 40, 30,
        20, 40, 50, 60, 70, 60, 50, 40, 30, 30, 40, 50, 60, 70, 60, 50, 40, 20, 30, 40, 50, 60, 70,
        60, 50, 10, 20, 30, 40, 50, 60, 70, 60, 0, 10, 20, 30, 40, 50, 60, 70,
    ],
];
/// Squares in front of a pawn, on its own and the adjacent files, indexed by
/// colour and square; white (1) advances towards lower squares.
const PASSED_PAWN_AREA: [[u64; 64]; 2] = {
    let mut table = [[0u64; 64]; 2];
    let mut t = 0;
    while t < 2 {
        let mut s = 0;
        while s < 64 {
            let mut row = 0;
            while row < 8 {
                if (t == 1 && row < s / 8) || (t == 0 && row > s / 8) {
                    let mut col = 0;
                    while col < 8 {
                        if col + 1 >= s % 8 && col <= s % 8 + 1 {
                            table[t][s] |= 1u64 << (row * 8 + col);
                        }
                        col += 1;
                    }
                }
                row += 1;
            }
            s += 1;
        }
        t += 1;
    }
    table
};
const CENTER: u64 = 0x00003C3C3C3C0000;
impl Position {
    pub(crate) fn plies_until_queen_mobility(&self) -> usize {
        11usize.saturating_sub(self.ply)
    }

    /// Static evaluation from the side to move; mate distances are not encoded.
    pub fn evaluate(&self) -> i32 {
        let t = self.turn;
        let other = t ^ 1;
        if self.pieces[t][KING] == 0 {
            return -100000;
        }
        if self.pieces[other][KING] == 0 {
            return 100000;
        }
        if matches!(self.variant, Variant::Standard | Variant::Chess960) {
            if self.dead_position_by_material() {
                return 0;
            }
            if self.own(t) == self.pieces[t][KING] {
                return -self.against_lone_king(other);
            }
            if self.own(other) == self.pieces[other][KING] {
                return self.against_lone_king(t);
            }
        }
        self.material(t) - self.material(other) + self.extensions(t) - self.extensions(other)
    }
    fn against_lone_king(&self, t: usize) -> i32 {
        let winner = self.king(t);
        let loser = self.king(t ^ 1);
        let p = self.pieces[t];
        let conversion = if p[PAWN] != 0 && p[ROOK] == 0 && p[QUEEN] == 0 {
            15 - distance(winner, loser)
                + advance(t, winner)
                + squares(p[PAWN]).map(|s| 10 * advance(t, s)).sum::<i32>()
        } else if p[PAWN] == 0
            && p[ROOK] == 0
            && p[QUEEN] == 0
            && p[BISHOP].count_ones() == 1
            && p[KNIGHT].count_ones() == 1
        {
            let center_bonus = if p[KING] & CENTER != 0 { 20 } else { 0 };
            center_bonus + 300
                - 6 * distance(winner, loser)
                - KBNK[usize::from(p[BISHOP] & WHITE_SQUARES != 0)][loser]
                - KING_ENDINGS[loser]
                - distance(p[BISHOP].trailing_zeros() as usize, loser)
                - distance(p[KNIGHT].trailing_zeros() as usize, loser)
        } else {
            150 - 6 * distance(winner, loser) - KING_ENDINGS[loser]
        };
        self.material(t) + conversion
    }
    fn extensions(&self, t: usize) -> i32 {
        let p = self.pieces[t];
        let occ = self.occupied();
        let pawns = p[PAWN];
        let opp_pawns = self.pieces[t ^ 1][PAWN];
        let mut value = if self.variant == Variant::KingOfTheHill {
            // A fixed bonus per king step towards the nearest hill square.
            let king = self.king(t);
            let steps = [27usize, 28, 35, 36]
                .iter()
                .map(|&s| (king / 8).abs_diff(s / 8).max((king % 8).abs_diff(s % 8)) as i32)
                .min()
                .unwrap();
            let danger = squares(self.range_with(KING, t, king, occ))
                .filter(|&s| self.attacked(t, s))
                .count() as i32;
            -40 * steps - 10 * danger
        } else if self.variant == Variant::Duck {
            let king = self.king(t);
            let mobility = self.mobility_with(KING, t, king, occ).count_ones() as i32;
            5 * mobility - VALUES[PAWN] * i32::from(self.attacked(t, king))
        } else if self.castling[t] & 8 != 0 {
            60
        } else if self.castling[t] == 0 {
            -60
        } else {
            -100
        };
        if matches!(self.variant, Variant::Standard | Variant::Chess960) {
            value += (self.range_with(KING, t, self.king(t), occ) & pawns).count_ones() as i32 * 5;
        }
        if pawns
            & if t == 1 {
                bit(51) | bit(52)
            } else {
                bit(11) | bit(12)
            }
            != 0
        {
            value -= 30;
        }
        for s in squares(pawns) {
            let passed = PASSED_PAWN_AREA[t][s];
            value += advance(t, s) * 5;
            if passed & pawns != 0 {
                value += 5;
            }
            if self.range_with(PAWN, t, s, occ) & pawns != 0 {
                value += 5;
            }
            if (0x0101010101010101u64 << (s % 8)) & (pawns & !bit(s)) != 0 {
                value -= 50;
            }
            if passed & opp_pawns == 0 {
                value += 30;
            }
        }
        value += (p[KNIGHT] & CENTER).count_ones() as i32 * 5;
        for s in squares(p[KNIGHT]) {
            value += self.mobility_with(KNIGHT, t, s, occ).count_ones() as i32 * 5;
        }
        if p[BISHOP].count_ones() == 1 {
            let s = p[BISHOP].trailing_zeros() as usize;
            value += self.mobility_with(BISHOP, t, s, occ).count_ones() as i32 * 5;
            value -= (pawns
                & if bit(s) & WHITE_SQUARES != 0 {
                    WHITE_SQUARES
                } else {
                    !WHITE_SQUARES
                })
            .count_ones() as i32;
        } else if p[BISHOP] != 0 {
            value += 20;
            for s in squares(p[BISHOP]) {
                value += self.mobility_with(BISHOP, t, s, occ).count_ones() as i32 * 7;
            }
        }
        let mut first = None;
        for s in squares(p[ROOK]) {
            value += self.mobility_with(ROOK, t, s, occ).count_ones() as i32 * 8;
            if let Some(previous) = first {
                if s / 8 == previous / 8 {
                    value += 5;
                }
                if s % 8 == previous % 8 {
                    value += 5;
                }
            } else {
                first = Some(s);
            }
            // Count each connected pair once, from its higher-indexed rook.
            let connected = self.range_with(ROOK, t, s, occ) & p[ROOK] & (bit(s) - 1);
            value += 30 * connected.count_ones() as i32;
        }
        if p[QUEEN] != 0 {
            if self.plies_until_queen_mobility() > 0 {
                let back = if t == 1 { 0xff00000000000000 } else { 0xff };
                if p[QUEEN] & back == 0 {
                    value -= 60;
                }
            } else {
                for s in squares(p[QUEEN]) {
                    value += self.mobility_with(ROOK, t, s, occ).count_ones() as i32 * 8
                        + self.mobility_with(BISHOP, t, s, occ).count_ones() as i32 * 7;
                }
            }
        }
        value
    }
}

#[cfg(test)]
mod tests {
    use super::*;

    #[test]
    fn lone_king_scores_keep_material_and_treat_dead_positions_as_draws() {
        for variant in [Variant::Standard, Variant::Chess960] {
            let evaluate = |fen| Position::from_fen(variant, fen).unwrap().evaluate();
            let queen = evaluate("7k/8/8/8/8/8/8/KQ6 w - - 0 10");
            let queen_and_pawn = evaluate("7k/8/8/8/8/8/P7/KQ6 w - - 0 10");
            assert!((900..1100).contains(&queen));
            assert_eq!(queen_and_pawn, queen + VALUES[PAWN]);
            assert_eq!(evaluate("7k/8/8/8/8/8/8/KQ6 b - - 0 10"), -queen);
            assert_eq!(evaluate("7k/8/8/8/8/8/8/KB6 w - - 0 10"), 0);
            assert!(evaluate("7k/8/8/8/8/8/P7/K7 w - - 0 10") >= VALUES[PAWN]);
        }
    }

    #[test]
    fn duck_scores_bare_kings_and_accounts_for_blocked_king_capture_lines() {
        let evaluate = |fen| Position::from_fen(Variant::Duck, fen).unwrap().evaluate();
        for fen in [
            "7k/8/8/8/8/8/8/K7 w - - 0 10",
            "7k/8/8/8/8/8/8/K7 b - - 0 10",
        ] {
            assert_eq!(evaluate(fen), 0);
        }
        let open = evaluate("4r2k/8/8/8/3$4/8/8/4K3 w - - 0 10");
        let blocked = evaluate("4r2k/8/8/8/4$3/8/8/4K3 w - - 0 10");
        assert!(blocked > open);
        assert!(open > -1000);
        assert_eq!(evaluate("4r2k/8/8/8/4$3/8/8/4K3 b - - 0 10"), -blocked);
    }

    #[test]
    fn bishop_penalty_counts_only_pawns_on_the_bishops_square_color() {
        // On rank seven, index parity alone gives the wrong square color.
        let light_bishop =
            Position::from_fen(Variant::Standard, "7k/1B6/8/8/8/8/2P5/K7 w - - 0 10").unwrap();
        let without_pawn =
            Position::from_fen(Variant::Standard, "7k/1B6/8/8/8/8/8/K7 w - - 0 10").unwrap();
        // c2 contributes 5 for advancement and 30 for being passed, minus 1 for its color.
        assert_eq!(
            light_bishop.extensions(WHITE) - without_pawn.extensions(WHITE),
            34
        );
    }

    #[test]
    fn rook_connection_requires_an_unblocked_ray_to_another_rook() {
        for (fen, bonus) in [
            ("7k/8/8/8/8/R7/8/R3K3 w - - 0 10", 35),
            ("7k/8/8/8/8/R7/P7/R3K3 w - - 0 10", 5),
            ("7k/8/8/8/8/1R6/8/R3K3 w - - 0 10", 0),
        ] {
            let p = Position::from_fen(Variant::Standard, fen).unwrap();
            let mut without_rooks = p.clone();
            let rooks = p.pieces[WHITE][ROOK];
            without_rooks.pieces[WHITE][ROOK] = 0;
            let mobility: i32 = squares(rooks)
                .map(|s| p.mobility_with(ROOK, WHITE, s, p.occupied()).count_ones() as i32 * 8)
                .sum();
            assert_eq!(
                p.extensions(WHITE) - without_rooks.extensions(WHITE) - mobility,
                bonus,
                "{fen}"
            );
        }
    }
}
