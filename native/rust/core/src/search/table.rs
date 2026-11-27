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
    generation: u32,
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
            generation: 0,
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
    generation: u32,
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
            generation: 0,
        }
    }

    pub(super) fn new_search(&mut self, clear: bool) {
        if clear {
            self.slots.fill(None);
        }
        self.generation = self.generation.wrapping_add(1);
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

    pub(super) fn store(&mut self, mut entry: Entry) {
        let index = self.index(entry.key);
        entry.generation = self.generation;
        if let Some(slot) = self.slots.get_mut(index) {
            if slot.is_none_or(|old| {
                // Discount old entries by two plies per search for replacement only.
                let age = self.generation.wrapping_sub(old.generation).min(8) as i32;
                let priority = i32::from(old.depth) - 2 * age;
                let depth = i32::from(entry.depth);
                depth > priority
                    || (depth == priority
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

    #[test]
    fn entries_survive_new_searches_and_lose_replacement_priority_with_age() {
        let mut table = Table::with_slots(1);
        table.store(entry(0, 6, 10, (0, 20)));
        table.new_search(false);
        table.store(entry(1, 3, 10, (0, 20)));
        assert!(table.probe(0).is_some());

        table.new_search(false);
        table.store(entry(1, 3, 10, (0, 20)));
        assert!(table.probe(0).is_none());
        assert!(table.probe(1).is_some());

        table.new_search(true);
        assert!(table.probe(1).is_none());
    }
}
