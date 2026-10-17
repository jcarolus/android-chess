# Parity fixtures

`failures/` holds fixtures `tests/parity.rs` writes automatically when a Rust/C++ comparison
mismatches (variant, FEN, history, depth, quiescence and the mismatching field). It is gitignored
scratch space, keyed by a hash of the request so a repeated failure overwrites the same file.

To keep a reproduction permanently, copy its `.fixture` file into `regressions/` (create the
directory if it doesn't exist yet) and commit it. `fixture_regressions()` replays every file found
there on each run of `scripts/test-rust-engine.sh`.
