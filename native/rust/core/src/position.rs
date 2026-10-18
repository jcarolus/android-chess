use std::fmt;

#[derive(Clone, Copy, Debug, PartialEq, Eq)]
pub enum Variant {
    Standard,
    Chess960,
    Duck,
}
#[derive(Clone, Debug, PartialEq, Eq)]
pub struct Error(pub String);
impl fmt::Display for Error {
    fn fmt(&self, f: &mut fmt::Formatter<'_>) -> fmt::Result {
        f.write_str(&self.0)
    }
}
impl std::error::Error for Error {}
pub(crate) fn invalid(s: &str) -> Error {
    Error(s.into())
}
#[derive(Clone, Debug, PartialEq, Eq)]
pub struct CoordinateMove(String);
impl CoordinateMove {
    pub fn parse(s: &str) -> Result<Self, Error> {
        let (piece, duck) = match s.split_once('@') {
            Some((piece, duck)) => (piece, Some(duck)),
            None => (s, None),
        };
        let b = piece.as_bytes();
        if !piece.is_ascii() || !(b.len() == 4 || b.len() == 5) {
            return Err(invalid("Invalid coordinate move"));
        }
        square(&piece[..2])?;
        square(&piece[2..4])?;
        if b.len() == 5 && !b"qrbn".contains(&b[4]) {
            return Err(invalid("Invalid promotion"));
        }
        if let Some(duck) = duck {
            square(duck)?;
        }
        Ok(Self(s.into()))
    }
    pub fn as_str(&self) -> &str {
        &self.0
    }
}
impl fmt::Display for CoordinateMove {
    fn fmt(&self, f: &mut fmt::Formatter<'_>) -> fmt::Result {
        f.write_str(&self.0)
    }
}
#[derive(Clone, Copy, Debug, PartialEq, Eq)]
pub enum GameState {
    Play,
    Check,
    Mate,
    Stalemate,
    DrawMaterial,
    DrawFiftyMove,
    DrawRepetition,
}
pub(crate) const VALUES: [i32; 6] = [100, 320, 340, 500, 900, 50000];
pub(crate) const PAWN: usize = 0;
pub(crate) const KNIGHT: usize = 1;
pub(crate) const BISHOP: usize = 2;
pub(crate) const ROOK: usize = 3;
pub(crate) const QUEEN: usize = 4;
pub(crate) const KING: usize = 5;
pub(crate) const WHITE: usize = 1;
pub(crate) fn bit(s: usize) -> u64 {
    1u64 << s
}
pub(crate) fn squares(mut b: u64) -> impl Iterator<Item = usize> {
    std::iter::from_fn(move || {
        if b == 0 {
            None
        } else {
            let s = if b >> 32 != 0 {
                32 + (b >> 32).trailing_zeros() as usize
            } else {
                b.trailing_zeros() as usize
            };
            b &= !bit(s);
            Some(s)
        }
    })
}
pub(crate) fn name(s: usize) -> String {
    format!(
        "{}{}",
        (b'a' + (s % 8) as u8) as char,
        (b'8' - (s / 8) as u8) as char
    )
}
pub(crate) fn square(s: &str) -> Result<usize, Error> {
    let b = s.as_bytes();
    if b.len() != 2 || !(b'a'..=b'h').contains(&b[0]) || !(b'1'..=b'8').contains(&b[1]) {
        return Err(invalid("Invalid square"));
    }
    Ok((b'8' - b[1]) as usize * 8 + (b[0] - b'a') as usize)
}
pub(crate) fn distance(a: usize, b: usize) -> i32 {
    (a / 8).abs_diff(b / 8) as i32 + (a % 8).abs_diff(b % 8) as i32
}
/// The file of the outermost rook along `squares` (walked from the king outward), matching the
/// native reference engine's setCastlingsEPAnd50: later matches overwrite earlier ones, so the
/// result is whichever rook sits closest to the board edge.
fn outermost_rook_file(rooks: u64, squares: impl Iterator<Item = usize>) -> Option<usize> {
    let mut found = None;
    for s in squares {
        if rooks & bit(s) != 0 {
            found = Some(s % 8);
        }
    }
    found
}
pub(crate) fn advance(t: usize, s: usize) -> i32 {
    if t == WHITE {
        7 - (s / 8) as i32
    } else {
        (s / 8) as i32
    }
}
#[derive(Clone, Copy, Debug, PartialEq, Eq)]
pub(crate) struct Move {
    pub from: usize,
    pub to: usize,
    pub promotion: Option<usize>,
    pub capture: bool,
    pub ep: bool,
    pub castle: bool,
    pub double: bool,
    pub check: bool,
    pub duck: Option<usize>,
}
impl Move {
    fn new(from: usize, to: usize) -> Self {
        Self {
            from,
            to,
            promotion: None,
            capture: false,
            ep: false,
            castle: false,
            double: false,
            check: false,
            duck: None,
        }
    }
    pub fn coordinate(self) -> CoordinateMove {
        CoordinateMove(format!(
            "{}{}{}{}",
            name(self.from),
            name(self.to),
            self.promotion
                .map(|p| (b"pnbrqk"[p] as char).to_string())
                .unwrap_or_default(),
            self.duck
                .map(|s| format!("@{}", name(s)))
                .unwrap_or_default()
        ))
    }
}
#[derive(Clone, PartialEq, Eq)]
struct Repetition {
    pieces: [[u64; 6]; 2],
    turn: usize,
    rights: [u8; 2],
    ep: Option<usize>,
}
#[derive(Clone)]
pub struct Position {
    pub(crate) pieces: [[u64; 6]; 2],
    pub(crate) turn: usize,
    pub(crate) castling: [u8; 2],
    /// Original rook file per colour, `[queenside, kingside]`; fixed for the game.
    pub(crate) rook_file: [[usize; 2]; 2],
    pub(crate) variant: Variant,
    pub(crate) duck: Option<usize>,
    ep: Option<usize>,
    halfmove: u32,
    pub(crate) ply: usize,
    history: Vec<String>,
    previous: Vec<Repetition>,
    pub(crate) last_check: bool,
}
impl Position {
    pub fn from_fen(variant: Variant, fen: &str) -> Result<Self, Error> {
        let fields: Vec<_> = fen.split_whitespace().collect();
        if fields.len() != 6 || !fen.is_ascii() {
            return Err(invalid("Expected six-field Standard FEN"));
        }
        let mut p = Self {
            pieces: [[0; 6]; 2],
            turn: 1,
            castling: [0; 2],
            rook_file: [[0, 7], [0, 7]],
            variant,
            duck: None,
            ep: None,
            halfmove: 0,
            ply: 0,
            history: vec![],
            previous: vec![],
            last_check: false,
        };
        let ranks: Vec<_> = fields[0].split('/').collect();
        if ranks.len() != 8 {
            return Err(invalid("Expected eight ranks"));
        }
        for (r, rank) in ranks.iter().enumerate() {
            let mut c = 0;
            for b in rank.bytes() {
                if (b'1'..=b'8').contains(&b) {
                    c += (b - b'0') as usize;
                } else if b == b'$' && variant == Variant::Duck && p.duck.is_none() && c < 8 {
                    p.duck = Some(r * 8 + c);
                    c += 1;
                } else {
                    let piece = b"pnbrqk"
                        .iter()
                        .position(|v| *v == b.to_ascii_lowercase())
                        .ok_or_else(|| invalid("Invalid piece"))?;
                    if c >= 8 {
                        return Err(invalid("Rank overflow"));
                    }
                    p.pieces[usize::from(b.is_ascii_uppercase())][piece] |= bit(r * 8 + c);
                    c += 1;
                }
            }
            if c != 8 {
                return Err(invalid("Invalid rank width"));
            }
        }
        p.turn = match fields[1] {
            "w" => 1,
            "b" => 0,
            _ => return Err(invalid("Invalid side to move")),
        };
        if fields[2] != "-"
            && (!fields[2].bytes().all(|b| b"KQkq".contains(&b))
                || fields[2]
                    .bytes()
                    .any(|b| fields[2].bytes().filter(|v| *v == b).count() != 1))
        {
            return Err(invalid("Invalid castling rights"));
        }
        for t in 0..2 {
            if (p.pieces[t][KING].count_ones() != 1
                && !(variant == Variant::Duck && p.pieces[t][KING] == 0))
                || p.pieces[t][PAWN] & 0xff000000000000ff != 0
                || p.pieces[t][PAWN].count_ones() > 8
            {
                return Err(invalid("Invalid kings or pawns"));
            }
        }
        if p.pieces.iter().all(|p| p[KING] == 0) {
            return Err(invalid("Both kings are missing"));
        }
        for t in 0..2 {
            let long = fields[2].contains(if t == 1 { 'Q' } else { 'q' });
            let short = fields[2].contains(if t == 1 { 'K' } else { 'k' });
            let base = if t == 1 { 56 } else { 0 };
            if variant != Variant::Chess960 {
                if (long || short) && p.pieces[t][KING] != bit(base + 4) {
                    return Err(invalid("Non-Standard castling king"));
                }
                if long && p.pieces[t][ROOK] & bit(base) == 0
                    || short && p.pieces[t][ROOK] & bit(base + 7) == 0
                {
                    return Err(invalid("Missing castling rook"));
                }
                p.castling[t] = u8::from(!long)
                    | (u8::from(!short) << 1)
                    | (if !long && !short { 4 } else { 0 });
            } else {
                // Chess 960: scan outward from the king along the back rank for the
                // outermost rook on each side; a claimed right that resolves to no
                // rook is dropped silently, matching the native reference engine's
                // setCastlingsEPAnd50.
                let king = p.king(t);
                let mut lost = 0u8;
                if long {
                    match outermost_rook_file(p.pieces[t][ROOK], (base..king).rev()) {
                        Some(file) => p.rook_file[t][0] = file,
                        None => lost |= 1,
                    }
                } else {
                    lost |= 1;
                }
                if short {
                    match outermost_rook_file(p.pieces[t][ROOK], king + 1..=base + 7) {
                        Some(file) => p.rook_file[t][1] = file,
                        None => lost |= 2,
                    }
                } else {
                    lost |= 2;
                }
                p.castling[t] = lost | (if lost & 3 == 3 { 4 } else { 0 });
            }
        }
        p.ep = if fields[3] == "-" {
            None
        } else {
            Some(square(fields[3])?)
        };
        if let Some(ep) = p.ep {
            let expected = if p.turn == 1 { 2 } else { 5 };
            if ep / 8 != expected {
                return Err(invalid("Invalid en-passant rank"));
            }
            let pawn = if p.turn == 1 { ep + 8 } else { ep - 8 };
            if (p.own(0) | p.own(1)) & bit(ep) != 0 || p.pieces[p.turn ^ 1][PAWN] & bit(pawn) == 0 {
                return Err(invalid("Invalid en-passant square"));
            }
        }
        p.halfmove = fields[4]
            .parse()
            .map_err(|_| invalid("Invalid halfmove clock"))?;
        let full: u32 = fields[5]
            .parse()
            .map_err(|_| invalid("Invalid fullmove number"))?;
        if full == 0 || p.halfmove > 1_000_000 {
            return Err(invalid("Invalid move counters"));
        }
        if variant != Variant::Duck && p.attacked(p.turn ^ 1, p.king(p.turn ^ 1)) {
            return Err(invalid("Side not to move is in check"));
        }
        p.last_check = p.in_check();
        Ok(p)
    }
    pub fn history(&self) -> &[String] {
        &self.history
    }
    pub fn white_to_move(&self) -> bool {
        self.turn == 1
    }
    pub(crate) fn own(&self, t: usize) -> u64 {
        self.pieces[t].iter().fold(0, |a, b| a | b)
    }
    pub(crate) fn occupied(&self) -> u64 {
        self.own(0) | self.own(1) | self.duck.map(bit).unwrap_or(0)
    }
    pub(crate) fn king(&self, t: usize) -> usize {
        self.pieces[t][KING].trailing_zeros() as usize
    }
    pub(crate) fn piece(&self, t: usize, s: usize) -> Option<usize> {
        (0..6).find(|&p| self.pieces[t][p] & bit(s) != 0)
    }
    pub(crate) fn material(&self, t: usize) -> i32 {
        (0..6)
            .map(|p| self.pieces[t][p].count_ones() as i32 * VALUES[p])
            .sum()
    }
    pub(crate) fn range(&self, p: usize, t: usize, s: usize) -> u64 {
        let dirs: &[(i32, i32)] = match p {
            PAWN => {
                if t == 1 {
                    &[(-1, -1), (-1, 1)]
                } else {
                    &[(1, -1), (1, 1)]
                }
            }
            KNIGHT => &[
                (-2, -1),
                (-2, 1),
                (-1, -2),
                (-1, 2),
                (1, -2),
                (1, 2),
                (2, -1),
                (2, 1),
            ],
            BISHOP => &[(-1, -1), (-1, 1), (1, -1), (1, 1)],
            ROOK => &[(-1, 0), (1, 0), (0, -1), (0, 1)],
            _ => &[
                (-1, -1),
                (-1, 1),
                (1, -1),
                (1, 1),
                (-1, 0),
                (1, 0),
                (0, -1),
                (0, 1),
            ],
        };
        let mut result = 0;
        for &(dr, dc) in dirs {
            let (mut r, mut c) = (s as i32 / 8 + dr, s as i32 % 8 + dc);
            while (0..8).contains(&r) && (0..8).contains(&c) {
                let b = bit((r * 8 + c) as usize);
                result |= b;
                if p == PAWN || p == KNIGHT || p == KING || self.occupied() & b != 0 {
                    break;
                }
                r += dr;
                c += dc;
            }
        }
        result
    }
    pub(crate) fn mobility(&self, p: usize, t: usize, s: usize) -> u64 {
        self.range(p, t, s)
            & !(self.own(t)
                | if t == self.turn {
                    self.duck.map(bit).unwrap_or(0)
                } else {
                    0
                })
    }
    pub(crate) fn attacked(&self, t: usize, s: usize) -> bool {
        if s >= 64 {
            return false;
        }
        (0..6).any(|p| {
            squares(self.pieces[t ^ 1][p]).any(|from| self.range(p, t ^ 1, from) & bit(s) != 0)
        })
    }
    pub fn in_check(&self) -> bool {
        self.variant != Variant::Duck && self.attacked(self.turn, self.king(self.turn))
    }
    pub(crate) fn pseudo_moves(&self) -> Vec<Move> {
        let mut moves = Vec::new();
        let t = self.turn;
        let occ = self.occupied();
        for piece in [KNIGHT, BISHOP, PAWN, ROOK, QUEEN, KING] {
            if piece == PAWN {
                let step = if t == 1 { -8i32 } else { 8 };
                // Pawn groups and destination order match the native move generator.
                for group in 0..4 {
                    let mut group_moves = Vec::new();
                    for from in squares(self.pieces[t][PAWN]) {
                        let delta = match group {
                            0 => step,
                            1 => step * 2,
                            2 => {
                                if t == 1 {
                                    -9
                                } else {
                                    9
                                }
                            }
                            _ => {
                                if t == 1 {
                                    -7
                                } else {
                                    7
                                }
                            }
                        };
                        let to = from as i32 + delta;
                        if !(0..64).contains(&to) {
                            continue;
                        }
                        let to = to as usize;
                        let mut m = Move::new(from, to);
                        if group < 2 {
                            if occ & bit(to) != 0 {
                                continue;
                            }
                            if group == 1
                                && (advance(t, from) != 1
                                    || occ & bit((from as i32 + step) as usize) != 0)
                            {
                                continue;
                            }
                            m.double = group == 1;
                        } else {
                            if (from % 8).abs_diff(to % 8) != 1
                                || self.duck == Some(to)
                                || (self.own(t ^ 1) & bit(to) == 0 && self.ep != Some(to))
                            {
                                continue;
                            }
                            m.capture = true;
                            m.ep = self.ep == Some(to);
                        }
                        if advance(t, to) == 7 {
                            for promo in [QUEEN, ROOK, BISHOP, KNIGHT] {
                                let mut promotion = m;
                                promotion.promotion = Some(promo);
                                group_moves.push(promotion);
                            }
                        } else {
                            group_moves.push(m);
                        }
                    }
                    group_moves.sort_by_key(|m| (m.to + 32) % 64);
                    moves.extend(group_moves);
                }
            } else {
                for from in squares(self.pieces[t][piece]) {
                    for to in squares(self.mobility(piece, t, from)) {
                        if self.variant != Variant::Duck && self.pieces[t ^ 1][KING] & bit(to) != 0
                        {
                            break;
                        }
                        let mut m = Move::new(from, to);
                        m.capture = self.own(t ^ 1) & bit(to) != 0;
                        moves.push(m);
                    }
                }
                if piece == KING && !self.attacked(t, self.king(t)) {
                    let base = if t == 1 { 56 } else { 0 };
                    let king = self.king(t);
                    for (mask, dir, bound, short) in
                        [(6u8, 1i32, base + 7, true), (5u8, -1i32, base, false)]
                    {
                        if self.castling[t] & mask != 0 {
                            continue;
                        }
                        let target_file = self.rook_file[t][usize::from(short)];
                        let (to, rook_to) = if short {
                            (base + 6, base + 5)
                        } else {
                            (base + 2, base + 3)
                        };
                        if king == base + 4 && target_file == if short { 7 } else { 0 } {
                            // The classical king/rook squares: only the king's own transit
                            // squares need be unattacked; squares further toward the rook
                            // only need to be vacant (matches the native reference engine's
                            // dedicated fast path, which the general scan below does not
                            // reproduce exactly).
                            let transit: &[usize] = if short {
                                &[base + 5, base + 6]
                            } else {
                                &[base + 2, base + 3]
                            };
                            let clear: &[usize] = if short {
                                &[base + 5, base + 6]
                            } else {
                                &[base + 1, base + 2, base + 3]
                            };
                            if self.pieces[t][ROOK] & bit(base + target_file) != 0
                                && clear.iter().all(|s| occ & bit(*s) == 0)
                                && transit.iter().all(|s| !self.attacked(t, *s))
                            {
                                let mut m = Move::new(king, to);
                                m.castle = true;
                                moves.push(m);
                            }
                            continue;
                        }
                        let mut s = king as i32;
                        let mut rook = None;
                        loop {
                            let next = s + dir;
                            if dir > 0 {
                                if next as usize > bound {
                                    break;
                                }
                            } else if next < bound as i32 {
                                break;
                            }
                            s = next;
                            let sq = s as usize;
                            if occ & bit(sq) != 0 {
                                if self.pieces[t][ROOK] & bit(sq) != 0 && sq % 8 == target_file {
                                    rook = Some(sq);
                                }
                                break;
                            }
                            if self.attacked(t, sq) {
                                break;
                            }
                        }
                        let Some(rook) = rook else { continue };
                        let free = |sq: usize| occ & bit(sq) == 0;
                        let legal = (free(rook_to) && free(to))
                            || (rook == rook_to && free(to))
                            || (rook == to && king == rook_to)
                            || (rook == to && free(rook_to))
                            || (king == to && free(rook_to))
                            || (king == rook_to && free(to));
                        if legal {
                            let mut m = Move::new(king, to);
                            m.castle = true;
                            moves.push(m);
                        }
                    }
                }
            }
        }
        moves
    }
    pub(crate) fn legal_internal(&self) -> Vec<Move> {
        let mut moves = self.pseudo_moves();
        if self.variant == Variant::Duck {
            return moves;
        }
        let mut i = 0;
        while i < moves.len() {
            let next = self.make(moves[i]);
            if next.attacked(self.turn, next.king(self.turn)) {
                moves.swap_remove(i);
            } else {
                moves[i].check = next.in_check();
                i += 1;
            }
        }
        moves
    }
    pub fn legal_moves(&self) -> Vec<CoordinateMove> {
        if self.variant == Variant::Duck {
            if self.game_state() != GameState::Play {
                return vec![];
            }
            let mut turns = Vec::new();
            for m in self.legal_internal() {
                let next = self.make(m);
                if next.game_state() != GameState::Play {
                    turns.push(self.external(self.turn, m));
                } else {
                    for duck in 0..64 {
                        if next.occupied() & bit(duck) == 0 {
                            let mut turn = m;
                            turn.duck = Some(duck);
                            turns.push(self.external(self.turn, turn));
                        }
                    }
                }
            }
            return turns;
        }
        self.legal_internal()
            .into_iter()
            .map(|m| self.external(self.turn, m))
            .collect()
    }
    pub fn apply(&mut self, coordinate: &str) -> Result<(), Error> {
        self.apply_input(coordinate, false).map(|_| ())
    }
    /// A pending trailing piece move is validated but leaves this owned position unchanged.
    pub(crate) fn apply_input(
        &mut self,
        coordinate: &str,
        omit_pending: bool,
    ) -> Result<bool, Error> {
        let coordinate = CoordinateMove::parse(coordinate)?;
        if self.variant == Variant::Duck && self.game_state() != GameState::Play {
            return Err(invalid("Game has ended"));
        }
        let (piece, duck) = coordinate
            .as_str()
            .split_once('@')
            .map(|(p, d)| (p, Some(d)))
            .unwrap_or((coordinate.as_str(), None));
        if duck.is_some() && self.variant != Variant::Duck {
            return Err(invalid("Duck placement in another variant"));
        }
        let m = self
            .legal_internal()
            .into_iter()
            .find(|m| self.external(self.turn, *m).as_str() == piece)
            .ok_or_else(|| invalid("Illegal move"))?;
        let mut next = self.make(m);
        if self.variant == Variant::Duck {
            match duck {
                Some(d) => {
                    let d = square(d)?;
                    if next.game_state() != GameState::Play || next.occupied() & bit(d) != 0 {
                        return Err(invalid("Illegal duck placement"));
                    }
                    next.duck = Some(d);
                }
                None if next.game_state() == GameState::Play => {
                    if omit_pending {
                        return Ok(false);
                    }
                    return Err(invalid("Missing duck placement"));
                }
                None => {}
            }
        }
        next.history.push(coordinate.0);
        *self = next;
        Ok(true)
    }
    /// The rook's current square for a castle move, found by its tracked file
    /// (scanning outward from the king's pre-move square, as the native
    /// reference engine does when it relocates the rook).
    fn castling_rook_square(&self, t: usize, king_from: usize, short: bool) -> usize {
        king_from - king_from % 8 + self.rook_file[t][usize::from(short)]
    }
    /// The coordinate move crossing the Rust boundary: king-to-destination for
    /// Standard, king-to-original-rook for Chess 960 castling (UCI_Chess960
    /// convention); every other move is unchanged. `t` is the move's mover,
    /// which is `self.turn` only when `m` is played directly from `self` (a
    /// later ply, e.g. in a principal variation, has the opposite mover).
    pub(crate) fn external(&self, t: usize, m: Move) -> CoordinateMove {
        if m.castle && self.variant == Variant::Chess960 {
            let short = m.to % 8 == 6;
            let rook = self.castling_rook_square(t, m.from, short);
            CoordinateMove(format!("{}{}", name(m.from), name(rook)))
        } else {
            m.coordinate()
        }
    }
    fn rights(&self) -> [u8; 2] {
        [
            u8::from(self.castling[0] & 5 == 0) | (u8::from(self.castling[0] & 6 == 0) << 1),
            u8::from(self.castling[1] & 5 == 0) | (u8::from(self.castling[1] & 6 == 0) << 1),
        ]
    }
    fn repetition(&self) -> Repetition {
        let ep = self.ep.filter(|&to| {
            squares(self.pieces[self.turn][PAWN]).any(|from| {
                if self.range(PAWN, self.turn, from) & bit(to) == 0 {
                    return false;
                }
                let mut m = Move::new(from, to);
                m.capture = true;
                m.ep = true;
                let next = self.make(m);
                !next.attacked(self.turn, next.king(self.turn))
            })
        });
        Repetition {
            pieces: self.pieces,
            turn: self.turn,
            rights: self.rights(),
            ep,
        }
    }
    pub(crate) fn make(&self, m: Move) -> Self {
        let mut n = self.clone();
        let t = self.turn;
        let p = self.piece(t, m.from).unwrap();
        if p == PAWN || m.capture {
            n.previous.clear();
        } else {
            n.previous.push(self.repetition());
        }
        n.pieces[t][p] &= !bit(m.from);
        if m.capture {
            let back_rank = if t == WHITE { 0 } else { 7 };
            if self.piece(t ^ 1, m.to) == Some(ROOK) && m.to / 8 == back_rank {
                let file = m.to % 8;
                if file == self.rook_file[t ^ 1][0] {
                    n.castling[t ^ 1] |= 1;
                }
                if file == self.rook_file[t ^ 1][1] {
                    n.castling[t ^ 1] |= 2;
                }
            }
            let target = if m.ep {
                if t == 1 {
                    m.to + 8
                } else {
                    m.to - 8
                }
            } else {
                m.to
            };
            for b in &mut n.pieces[t ^ 1] {
                *b &= !bit(target);
            }
        }
        n.pieces[t][m.promotion.unwrap_or(p)] |= bit(m.to);
        if p == ROOK {
            let file = m.from % 8;
            if file == self.rook_file[t][0] {
                n.castling[t] |= 1;
            } else if file == self.rook_file[t][1] {
                n.castling[t] |= 2;
            }
        }
        if p == KING {
            n.castling[t] |= 4;
        }
        if m.castle {
            let base = if t == 1 { 56 } else { 0 };
            let short = m.to % 8 == 6;
            let rook_from = self.castling_rook_square(t, m.from, short);
            let rook_to = if short { base + 5 } else { base + 3 };
            n.pieces[t][ROOK] = (n.pieces[t][ROOK] & !bit(rook_from)) | bit(rook_to);
            n.castling[t] |= 8;
        }
        n.ep = if m.double {
            Some((m.from + m.to) / 2)
        } else {
            None
        };
        n.halfmove = if p == PAWN || m.capture {
            0
        } else {
            self.halfmove + 1
        };
        n.ply += 1;
        n.turn ^= 1;
        n.last_check = m.check;
        if m.duck.is_some() {
            n.duck = m.duck;
        }
        n
    }
    pub(crate) fn drawn_in_search(&self) -> bool {
        self.halfmove == 100
            || self
                .previous
                .iter()
                .filter(|p| **p == self.repetition())
                .count()
                >= 2
    }
    pub fn game_state(&self) -> GameState {
        if self.variant == Variant::Duck && self.pieces.iter().any(|p| p[KING] == 0) {
            return GameState::Mate;
        }
        if self.halfmove == 100 {
            return GameState::DrawFiftyMove;
        }
        if self
            .previous
            .iter()
            .filter(|p| **p == self.repetition())
            .count()
            >= 2
        {
            return GameState::DrawRepetition;
        }
        if self
            .pieces
            .iter()
            .all(|p| p[PAWN] | p[ROOK] | p[QUEEN] == 0)
            && self.material(0) <= 50340
            && self.material(1) <= 50340
            && ![0, 7, 56, 63].contains(&self.king(0))
            && ![0, 7, 56, 63].contains(&self.king(1))
        {
            return GameState::DrawMaterial;
        }
        if self.variant == Variant::Duck {
            return GameState::Play;
        }
        let check = self.in_check();
        if self.legal_internal().is_empty() {
            if check {
                GameState::Mate
            } else {
                GameState::Stalemate
            }
        } else if check {
            GameState::Check
        } else {
            GameState::Play
        }
    }
    pub fn fen(&self) -> String {
        let mut s = String::new();
        for r in 0..8 {
            let mut empty = 0;
            for c in 0..8 {
                let sq = r * 8 + c;
                let piece = (0..2).find_map(|t| self.piece(t, sq).map(|p| (t, p)));
                if self.duck == Some(sq) {
                    if empty > 0 {
                        s.push(char::from(b'0' + empty));
                        empty = 0;
                    }
                    s.push('$');
                } else if let Some((t, p)) = piece {
                    if empty > 0 {
                        s.push(char::from(b'0' + empty));
                        empty = 0;
                    }
                    let b = b"pnbrqk"[p];
                    s.push(if t == 1 { b.to_ascii_uppercase() } else { b } as char);
                } else {
                    empty += 1;
                }
            }
            if empty > 0 {
                s.push(char::from(b'0' + empty));
            }
            if r < 7 {
                s.push('/');
            }
        }
        s.push_str(if self.turn == 1 { " w " } else { " b " });
        let mut rights = String::new();
        for (t, mask, ch) in [(1, 6, 'K'), (1, 5, 'Q'), (0, 6, 'k'), (0, 5, 'q')] {
            if self.castling[t] & mask == 0 {
                rights.push(ch);
            }
        }
        s.push_str(if rights.is_empty() { "-" } else { &rights });
        format!(
            "{} {} {} {}",
            s,
            self.ep.map(name).unwrap_or_else(|| "-".into()),
            self.halfmove,
            self.ply / 2 + 1
        )
    }
    pub fn halfmove_clock(&self) -> u32 {
        self.halfmove
    }
    /// Count of identical positions (same pieces, turn, rights and en-passant
    /// target) seen earlier in this game, per the threefold-repetition rule.
    pub fn repetition_count(&self) -> usize {
        let current = self.repetition();
        self.previous.iter().filter(|p| **p == current).count()
    }
    /// `[white kingside, white queenside, black kingside, black queenside]`.
    pub fn castling_rights(&self) -> [bool; 4] {
        let r = self.rights();
        [r[1] & 2 != 0, r[1] & 1 != 0, r[0] & 2 != 0, r[0] & 1 != 0]
    }
    /// FEN piece character at `square` (uppercase for White), `$` for the duck, or `None` if empty.
    pub fn piece_at(&self, square: &str) -> Result<Option<char>, Error> {
        let s = self::square(square)?;
        if self.duck == Some(s) {
            return Ok(Some('$'));
        }
        for t in 0..2 {
            if let Some(p) = self.piece(t, s) {
                let b = b"pnbrqk"[p];
                return Ok(Some(if t == WHITE {
                    b.to_ascii_uppercase() as char
                } else {
                    b as char
                }));
            }
        }
        Ok(None)
    }
    /// Squares holding a `by_white` piece that attacks `square`.
    pub fn attackers(&self, by_white: bool, square: &str) -> Result<Vec<String>, Error> {
        let target = self::square(square)?;
        let t = usize::from(by_white);
        Ok((0..6)
            .flat_map(|p| {
                squares(self.pieces[t][p])
                    .filter(move |&from| self.range(p, t, from) & bit(target) != 0)
                    .map(name)
            })
            .collect())
    }
    /// Pieces missing from each side relative to the opening set, `[pawn, knight, bishop, rook, queen]`.
    pub fn captured(&self, white: bool) -> [u32; 5] {
        const STARTING: [u32; 5] = [8, 2, 2, 2, 1];
        let t = usize::from(white);
        let mut result = [0u32; 5];
        for (p, count) in result.iter_mut().enumerate() {
            *count = STARTING[p].saturating_sub(self.pieces[t][p].count_ones());
        }
        result
    }
    /// Standard Algebraic Notation for the legal move matching `coordinate`.
    pub fn to_san(&self, coordinate: &str) -> Result<String, Error> {
        let coordinate = CoordinateMove::parse(coordinate)?;
        let (piece_str, duck) = coordinate
            .as_str()
            .split_once('@')
            .map_or((coordinate.as_str(), None), |(p, d)| (p, Some(d)));
        let legal = self.legal_internal();
        let t = self.turn;
        let m = legal
            .iter()
            .copied()
            .find(|m| self.external(t, *m).as_str() == piece_str)
            .ok_or_else(|| invalid("Illegal move"))?;
        let piece = self.piece(t, m.from).unwrap();
        let mut san = String::new();
        if m.castle {
            san.push_str(if m.to % 8 == 6 { "O-O" } else { "O-O-O" });
        } else if piece == PAWN {
            if m.capture {
                san.push((b'a' + (m.from % 8) as u8) as char);
                san.push('x');
            }
            san.push_str(&name(m.to));
            if let Some(promo) = m.promotion {
                san.push('=');
                san.push((b"pnbrqk"[promo]).to_ascii_uppercase() as char);
            }
        } else {
            san.push(b"pnbrqk"[piece].to_ascii_uppercase() as char);
            let others: Vec<_> = legal
                .iter()
                .filter(|other| {
                    other.to == m.to
                        && other.from != m.from
                        && self.piece(t, other.from) == Some(piece)
                })
                .collect();
            if !others.is_empty() {
                let same_file = others.iter().any(|o| o.from % 8 == m.from % 8);
                let same_rank = others.iter().any(|o| o.from / 8 == m.from / 8);
                if !same_file {
                    san.push((b'a' + (m.from % 8) as u8) as char);
                } else if !same_rank {
                    san.push((b'8' - (m.from / 8) as u8) as char);
                } else {
                    san.push_str(&name(m.from));
                }
            }
            if m.capture {
                san.push('x');
            }
            san.push_str(&name(m.to));
        }
        if let Some(d) = duck {
            let d = square(d)?;
            let next = self.make(m);
            if next.occupied() & bit(d) != 0 {
                return Err(invalid("Illegal duck placement"));
            }
            san.push('@');
            san.push_str(&name(d));
        } else if self.variant != Variant::Duck && m.check {
            san.push(if self.make(m).legal_internal().is_empty() {
                '#'
            } else {
                '+'
            });
        }
        Ok(san)
    }
}
