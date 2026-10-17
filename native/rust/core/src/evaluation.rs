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
const CENTER: u64 = 0x00003C3C3C3C0000;
const WHITE_SQUARES: u64 = 0x55AA55AA55AA55AA;
impl Position {
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
        if self.own(t) == self.pieces[t][KING] {
            return -self.against_lone_king(other);
        }
        if self.own(other) == self.pieces[other][KING] {
            return self.against_lone_king(t);
        }
        self.material(t) - self.material(other) + self.extensions(t) - self.extensions(other)
    }
    fn against_lone_king(&self, t: usize) -> i32 {
        let winner = self.king(t);
        let loser = self.king(t ^ 1);
        let p = self.pieces[t];
        if p[PAWN] != 0 {
            return 15 - distance(winner, loser)
                + advance(t, winner)
                + squares(p[PAWN]).map(|s| 10 * advance(t, s)).sum::<i32>();
        }
        if p[ROOK] == 0
            && p[QUEEN] == 0
            && p[BISHOP].count_ones() == 1
            && p[KNIGHT].count_ones() == 1
        {
            return if p[KING] & CENTER != 0 { 20 } else { 0 } + 300
                - 6 * distance(winner, loser)
                - KBNK[usize::from(p[BISHOP] & WHITE_SQUARES != 0)][loser]
                - KING_ENDINGS[loser]
                - distance(p[BISHOP].trailing_zeros() as usize, loser)
                - distance(p[KNIGHT].trailing_zeros() as usize, loser);
        }
        150 - 6 * distance(winner, loser) - KING_ENDINGS[loser] + self.material(t)
    }
    fn extensions(&self, t: usize) -> i32 {
        let p = self.pieces[t];
        let pawns = p[PAWN];
        let opp_pawns = self.pieces[t ^ 1][PAWN];
        let mut value = if self.castling[t] & 8 != 0 {
            60
        } else if self.castling[t] == 0 {
            -60
        } else {
            -100
        };
        value += (self.range(KING, t, self.king(t)) & pawns).count_ones() as i32 * 5;
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
            let mut passed = 0;
            for row in 0..8 {
                if (t == 1 && row < s / 8) || (t == 0 && row > s / 8) {
                    for col in 0usize..8 {
                        if col.abs_diff(s % 8) <= 1 {
                            passed |= bit(row * 8 + col);
                        }
                    }
                }
            }
            value += advance(t, s) * 5;
            if passed & pawns != 0 {
                value += 5;
            }
            if self.range(PAWN, t, s) & pawns != 0 {
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
            value += self.mobility(KNIGHT, t, s).count_ones() as i32 * 5;
        }
        if p[BISHOP].count_ones() == 1 {
            let s = p[BISHOP].trailing_zeros() as usize;
            value += self.mobility(BISHOP, t, s).count_ones() as i32 * 5;
            value -= (pawns
                & if s.is_multiple_of(2) {
                    WHITE_SQUARES
                } else {
                    !WHITE_SQUARES
                })
            .count_ones() as i32;
        } else if p[BISHOP] != 0 {
            value += 20;
            for s in squares(p[BISHOP]) {
                value += self.mobility(BISHOP, t, s).count_ones() as i32 * 7;
            }
        }
        let mut first = None;
        for s in squares(p[ROOK]) {
            value += self.mobility(ROOK, t, s).count_ones() as i32 * 8;
            if let Some(previous) = first {
                if s / 8 == previous / 8 {
                    value += 5;
                }
                if s % 8 == previous % 8 {
                    value += 5;
                }
                if ((!(self.own(t)
                    | if t == self.turn {
                        self.duck.map(bit).unwrap_or(0)
                    } else {
                        0
                    }))
                    | (p[ROOK] & !bit(s)))
                    & self.range(ROOK, t, s)
                    != 0
                {
                    value += 30;
                }
            } else {
                first = Some(s);
            }
        }
        if p[QUEEN] != 0 {
            if self.ply < 11 {
                let back = if t == 1 { 0xff00000000000000 } else { 0xff };
                if p[QUEEN] & back == 0 {
                    value -= 60;
                }
            } else {
                for s in squares(p[QUEEN]) {
                    value += self.mobility(ROOK, t, s).count_ones() as i32 * 8
                        + self.mobility(BISHOP, t, s).count_ones() as i32 * 7;
                }
            }
        }
        value
    }
}
