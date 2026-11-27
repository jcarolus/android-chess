use super::*;
use std::iter::Peekable;

const MAX_VARIATION_DEPTH: usize = 128;

impl Position {
    /// Applies a PGN move, accepting explicit source files or ranks even when redundant.
    /// Check and mate suffixes are annotations; the resulting board determines the state.
    pub fn apply_san(&mut self, san: &str) -> Result<(), Error> {
        let san = san.strip_suffix(['+', '#']).unwrap_or(san);
        let (piece, duck) = match san.split_once('@') {
            Some((piece, square_name)) if self.variant == Variant::Duck => {
                (piece, Some(square(square_name)?))
            }
            Some(_) => return Err(invalid("Duck placement in another variant")),
            None => (san, None),
        };
        self.check_turn_available()?;
        let san_move = SanMove::parse(piece)?;
        let mut matches = self
            .legal_internal()
            .into_iter()
            .filter(|&m| san_move.matches(self, m));
        let selected = matches.next().ok_or_else(|| invalid("Illegal SAN move"))?;
        if matches.next().is_some() {
            return Err(invalid("Ambiguous SAN move"));
        }
        self.apply_resolved(selected, duck, false)?;
        Ok(())
    }

    /// Replays pre-parsed SAN. Parentheses delimit alternatives to the preceding move.
    /// Every branch is checked; the returned position is the end of the main line.
    pub fn replay_san<'a>(
        mut self,
        tokens: impl IntoIterator<Item = &'a str>,
    ) -> Result<Self, Error> {
        self.replay_san_line(&mut tokens.into_iter().enumerate().peekable(), 0)?;
        Ok(self)
    }

    fn replay_san_line<'a>(
        &mut self,
        tokens: &mut Peekable<impl Iterator<Item = (usize, &'a str)>>,
        depth: usize,
    ) -> Result<(), Error> {
        let mut has_move = false;
        while let Some((index, token)) = tokens.next() {
            match token {
                ")" if depth > 0 && has_move => return Ok(()),
                ")" => return Err(invalid("Unexpected end of SAN variation")),
                "(" => return Err(invalid("SAN variation must follow a move")),
                _ => {}
            }
            let before = matches!(tokens.peek(), Some((_, "("))).then(|| {
                let mut branch = self.without_records();
                branch.previous.clone_from(&self.previous);
                branch
            });
            self.apply_san(token)
                .map_err(|error| Error(format!("{error} at SAN token {}", index + 1)))?;
            has_move = true;
            if let Some(before) = before {
                if depth >= MAX_VARIATION_DEPTH {
                    return Err(invalid("Too many nested SAN variations"));
                }
                while matches!(tokens.peek(), Some((_, "("))) {
                    tokens.next();
                    before.clone().replay_san_line(tokens, depth + 1)?;
                }
            }
        }
        if depth == 0 {
            Ok(())
        } else {
            Err(invalid("Unclosed SAN variation"))
        }
    }
}

enum SanMove {
    Castle {
        kingside: bool,
    },
    Piece {
        kind: usize,
        destination: usize,
        capture: bool,
        promotion: Option<usize>,
        file: Option<usize>,
        rank: Option<usize>,
    },
}

impl SanMove {
    fn parse(san: &str) -> Result<Self, Error> {
        let bad_san = || invalid("Invalid SAN move");
        if !san.is_ascii() {
            return Err(bad_san());
        }
        if san.contains('-') {
            return match san.replace('0', "O").as_str() {
                "O-O" => Ok(Self::Castle { kingside: true }),
                "O-O-O" => Ok(Self::Castle { kingside: false }),
                _ => Err(bad_san()),
            };
        }
        let (san, promotion) = match san.split_once('=') {
            Some((stem, "N")) => (stem, Some(KNIGHT)),
            Some((stem, "B")) => (stem, Some(BISHOP)),
            Some((stem, "R")) => (stem, Some(ROOK)),
            Some((stem, "Q")) => (stem, Some(QUEEN)),
            Some(_) => return Err(bad_san()),
            None => (san, None),
        };
        let destination_at = san.len().checked_sub(2).ok_or_else(bad_san)?;
        let (prefix, destination) = san.split_at(destination_at);
        let destination = square(destination)?;
        let mut prefix = prefix.as_bytes();
        let kind = match prefix.first() {
            Some(b'N') => KNIGHT,
            Some(b'B') => BISHOP,
            Some(b'R') => ROOK,
            Some(b'Q') => QUEEN,
            Some(b'K') => KING,
            _ => PAWN,
        };
        if kind != PAWN {
            prefix = &prefix[1..];
        }
        let capture = prefix.last() == Some(&b'x');
        if capture {
            prefix = &prefix[..prefix.len() - 1];
        }
        let file = match prefix.first() {
            Some(&file @ b'a'..=b'h') => {
                prefix = &prefix[1..];
                Some(usize::from(file - b'a'))
            }
            _ => None,
        };
        let rank = match prefix {
            [rank @ b'1'..=b'8'] => Some(usize::from(b'8' - rank)),
            [] => None,
            _ => return Err(bad_san()),
        };
        Ok(Self::Piece {
            kind,
            destination,
            capture,
            promotion,
            file,
            rank,
        })
    }

    fn matches(&self, position: &Position, m: Move) -> bool {
        match *self {
            Self::Castle { kingside } => m.castle && (m.to % 8 == 6) == kingside,
            Self::Piece {
                kind,
                destination,
                capture,
                promotion,
                file,
                rank,
            } => {
                !m.castle
                    && m.to == destination
                    && m.capture == capture
                    && m.promotion == promotion
                    && position.piece(position.turn, m.from) == Some(kind)
                    && file.is_none_or(|file| m.from % 8 == file)
                    && rank.is_none_or(|rank| m.from / 8 == rank)
            }
        }
    }
}
