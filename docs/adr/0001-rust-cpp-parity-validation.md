# 0001: Rust/C++ differential validation scope

## Status

Accepted, partially deferred (see "Deferred" below).

## Context

The Local Engine is being ported from C++ to Rust as a behavioural-parity port: same algorithms,
evaluation, move ordering, depth and quiescence semantics, not a new search design. `scripts/test-rust-engine.sh`
is the repeatable comparison command: it builds the C++ `chess_parity_reference` binary, runs the
C++ unit suite, then runs the Rust test suite including the `--ignored` differential tests in
`native/rust/core/tests/parity.rs`, which replay identical Starting Positions, histories, depth
limits and quiescence options through both engines and assert the FEN, evaluation, game state, full
legal-move list (order included) and fixed-depth search result (best move, score, completed depth,
node count) match exactly.

Issue #254 asked for this validation to be completed, but is blocked by #253 ("Select C++ or Rust
as the Local Engine across Modes"), which is still open: no persisted preference exists, and the
C++ `LocalEngine` is unreachable from any Mode (`EngineSession` only ever constructs
`RustLocalEngine`). This ADR records what was validated now and what is deferred until #253 lands.

## Decision

**Validated now**, all passing as of this writing (`scripts/test-rust-engine.sh`):

- The fixed-position regressions already in `parity.rs` for Standard and Chess 960, including the
  existing C++ castling/repetition regressions.
- The stateless board-operations API (#264, `chess_core::Position`'s legal-move generation, move
  application, SAN generation and board/game-state queries, exposed to Android as the C ABI
  functions in `native/rust/bridge/src/lib.rs` prefixed `rust_board_*`): `parity.rs`'s `compare`
  now also asserts SAN-string equality for every legal move against `sanFor` in
  `native/project/jni/tests/parity_runner.cpp` (which calls the C++ reference's
  `ChessBoard::myMoveToString`), on top of the legal-move-list and game-state/repetition/draw
  comparisons it already made for the search port. This runs across the same fixed-position,
  Chess 960 castling, Duck and generated-history coverage listed below, so no new test functions
  were needed — only the per-move comparison itself was extended.
- `generated_histories`: deterministic seeded random walks from starting positions chosen to make
  repetition, promotion, castling-under-pressure and draw/terminal detection likely, across
  Standard, Chess 960 and Duck, comparing full engine state after every ply (not only the final
  position).
- Complete Duck turns (`piece@square`), including terminal king-capture turns with no placement.
- A fixture-capture mechanism: a mismatch is written to `native/rust/core/tests/fixtures/failures/`
  (gitignored) with the variant, FEN, history, depth, quiescence and the mismatching field, before
  the test fails. Promoting a file into `tests/fixtures/regressions/` and committing it keeps it as
  a permanent, replayable regression (`fixture_regressions` test); see
  `native/rust/core/tests/fixtures/README.md`.
- Android lifecycle/session checks in `RustEngineInstrumentation.java` (a device `Instrumentation`,
  not Activity/Espresso tests, per this repo's testing rules): Play, Analysis (including Duck and
  Chess 960), Mate-in-Two Puzzles and Practice Positions (the latter two share one `EngineSession.forPuzzles`
  entry point via `MateCollection`, so one covers both), stop, abort, restart, suppressed/stale
  queued results after close, pending-Duck-move suppressing search, navigation (undo/redo
  preserving Duck placement and position identity) and error reporting.

**One C++ bug was found and fixed** by the SAN comparison above: `ChessBoard::ambigiousMove()`
picked its disambiguating candidate from `genMoves()`'s pseudo-legal list without checking that the
candidate's own move was actually legal, so a piece pinned to its king (unable to legally reach the
shared destination) still triggered a needless file/rank prefix (e.g. `Nbc5` where only one knight
could legally reach `c5`, the other being pinned on the e-file). Fixed in `ChessBoard::ambigiousMove`
(`native/project/jni/ChessBoard.cpp`) by rejecting a candidate that leaves its own king in check,
matching `chess_core::Position::to_san`'s use of the fully-legal move list for disambiguation.
`generated_histories`' fixed seeds reach this position deterministically, so the fixture that
caught it did not need promoting into `tests/fixtures/regressions/` for future replay — the fixed
behaviour is already exercised on every run.

**Otherwise no C++ bugs or behavioural exceptions were found**: the full comparison suite passes
with no known unexplained mismatch. If a future run captures a genuine mismatch, promote its
fixture into `tests/fixtures/regressions/` and add a decision to this ADR (fix both engines, or
document why one side's behaviour is accepted) before treating the suite as green again.

**Deferred until #253 is implemented**:

- "Android checks cover both backend selections" and "switching without restart": there is no
  selectable C++ path today to exercise, so these criteria cannot be meaningfully tested yet. Once
  #253 adds the persisted preference and reconnects `LocalEngine` (C++), extend
  `RustEngineInstrumentation` to drive both backends and a mid-session switch.
- Release-build N/s comparison on a physical device: this needs a release APK run on real
  hardware, which isn't something this harness automates. Run it manually with the same C++
  evaluation-count definition (`chess_engine`'s node counter vs. `SearchResult::nodes`) once a
  release build is available, and record the comparison here or in a follow-up ADR; a performance
  difference does not fail acceptance.

## Consequences

The differential harness is broader and keeps reproducible evidence of any future failure, but the
full #254 acceptance criteria can't be closed out until #253 exists. Re-run
`scripts/test-rust-engine.sh` whenever either engine's move generation, evaluation or search
changes; it is the single repeatable comparison command referenced by this ADR and by #254.
