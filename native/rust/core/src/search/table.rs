use crate::position::Move;

const BYTE_BUDGET: usize = 4 * 1024 * 1024;

#[derive(Clone, Copy, Debug, PartialEq, Eq)]
enum Bound {
    Exact,
    Lower,
    Upper,
}

#[derive(Clone, Copy)]
pub(super) struct Entry {
    key: u64,
    repetition_key: u64,
    depth: u8,
    score: i32,
    bound: Bound,
    pub(super) best_move: Move,
}

impl Entry {
    pub(super) fn new(
        key: u64,
        repetition_key: u64,
        depth: u8,
        score: i32,
        (alpha, beta): (i32, i32),
        best_move: Move,
    ) -> Self {
        let bound = if score <= alpha {
            Bound::Upper
        } else if score >= beta {
            Bound::Lower
        } else {
            Bound::Exact
        };
        Self {
            key,
            repetition_key,
            depth,
            score,
            bound,
            best_move,
        }
    }

    /// Values inside the window are searched so the PV is built from explored moves.
    pub(super) fn cutoff(
        self,
        repetition_key: u64,
        depth: u8,
        alpha: i32,
        beta: i32,
    ) -> Option<i32> {
        (self.repetition_key == repetition_key
            && self.depth == depth
            && match self.bound {
                Bound::Exact => self.score <= alpha || self.score >= beta,
                Bound::Lower => self.score >= beta,
                Bound::Upper => self.score <= alpha,
            })
        .then_some(self.score)
    }
}

pub(super) struct Table {
    slots: Vec<Option<Entry>>,
}

impl Table {
    pub(super) fn new() -> Self {
        let capacity = BYTE_BUDGET / std::mem::size_of::<Option<Entry>>();
        Self::with_slots(capacity.next_power_of_two() >> usize::from(!capacity.is_power_of_two()))
    }

    pub(super) fn with_slots(slots: usize) -> Self {
        debug_assert!(slots == 0 || slots.is_power_of_two());
        Self {
            slots: vec![None; slots],
        }
    }

    fn index(&self, key: u64) -> usize {
        key as usize & self.slots.len().wrapping_sub(1)
    }

    pub(super) fn probe(&self, key: u64) -> Option<Entry> {
        self.slots
            .get(self.index(key))
            .copied()
            .flatten()
            .filter(|e| e.key == key)
    }

    pub(super) fn store(&mut self, entry: Entry) {
        let index = self.index(entry.key);
        if let Some(slot) = self.slots.get_mut(index) {
            if slot.is_none_or(|old| {
                entry.depth > old.depth
                    || (entry.depth == old.depth
                        && (entry.bound == Bound::Exact || old.bound != Bound::Exact))
            }) {
                *slot = Some(entry);
            }
        }
    }
}

#[cfg(test)]
mod tests {
    use super::*;
    use crate::position::{Position, Variant};

    fn entry(key: u64, depth: u8, score: i32, window: (i32, i32)) -> Entry {
        let p = Position::from_fen(Variant::Standard, "7k/8/8/8/8/8/8/KR6 w - - 0 1").unwrap();
        Entry::new(key, 5, depth, score, window, p.legal_internal()[0])
    }

    #[test]
    fn bounds_require_matching_depth_and_history_and_close_the_window() {
        let exact = entry(1, 3, 10, (0, 20));
        assert_eq!(exact.cutoff(5, 3, 10, 20), Some(10));
        assert_eq!(exact.cutoff(5, 3, 0, 10), Some(10));
        assert_eq!(exact.cutoff(5, 3, 0, 20), None);
        assert_eq!(exact.cutoff(6, 3, 10, 20), None);
        assert_eq!(exact.cutoff(5, 2, 10, 20), None);
        assert_eq!(exact.cutoff(5, 4, 10, 20), None);

        let lower = entry(1, 3, 10, (0, 10));
        assert_eq!(lower.cutoff(5, 3, 0, 10), Some(10));
        assert_eq!(lower.cutoff(5, 3, 10, 20), None);

        let upper = entry(1, 3, 10, (10, 20));
        assert_eq!(upper.cutoff(5, 3, 10, 20), Some(10));
        assert_eq!(upper.cutoff(5, 3, 0, 10), None);
    }

    #[test]
    fn slot_collisions_verify_the_full_key_and_prefer_depth_then_exactness() {
        let mut table = Table::with_slots(1);
        table.store(entry(0, 3, 10, (0, 20)));
        table.store(entry(1, 2, 10, (0, 20)));
        table.store(entry(1, 3, 10, (0, 10)));
        assert!(table.probe(0).is_some());
        assert!(table.probe(1).is_none());

        table.store(entry(1, 4, 10, (0, 10)));
        assert!(table.probe(0).is_none());
        assert_eq!(table.probe(1).unwrap().bound, Bound::Lower);
        table.store(entry(1, 4, 10, (0, 20)));
        assert_eq!(table.probe(1).unwrap().bound, Bound::Exact);
    }
}
