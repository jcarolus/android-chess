# Rust Local Engine

The `core` crate owns Standard, Chess 960, Duck and King of the Hill positions, coordinate move history, legal move
validation/application, game state, static evaluation and search. It has no JNI,
Android, C++ or external crate dependency. `SearchRequest::new` validates and
replays a Starting Position and complete history before search. Requests are
immutable and never borrow a live board. Duck history, best moves and PVs use
complete turns such as `e2e4@d5` and `a7a8q@d5`; terminal piece moves need no
placement. The core exposes no placement-only search. A legal unfinished trailing
piece move is omitted during request preparation, retaining the preceding complete
position in `SearchRequest::position`; unfinished moves earlier in history fail.

`bridge` exposes an owned search handle and a small C ABI. The separate
`jni/rust-chess-jni.cpp` adapter packages that static Rust library as
`librust-chess-jni.so`. `EngineSession.forRustTesting` is a separate internal Java integration entry
used only by device tests. Rust is the built-in Local Engine for every Mode and
Variant: Play (`EnginePolicy.forPlay`), Analysis (`EnginePolicy.forAnalysis`) and
Mate-in-Two Puzzles and Practice Positions (`EngineSession.forPuzzles`, quiescence
off). OEX Engines are used only when selected and available, and never for Duck.
The C++ `LocalEngine` class is kept but no Mode selects it.

King of the Hill is exposed only in Play, with the built-in Engine or two humans on
one device. PGN uses `[Variant "King of the Hill"]`; Analysis and Setup are unavailable.
A legal king move to d4, e4, d5 or e5 wins after the fifty-move and repetition draw
checks. Insufficient material is not a draw. Search bypasses the opening book and
uses a phase-independent 40-centipawn bonus per king step towards the nearest hill
square, with a 10-centipawn penalty per attacked adjacent square instead of the
standard castling/pawn-shelter terms and lone-king mating heuristic.
Interactive pending placement gates all EngineSession search entry points and
RustLocalEngine itself. Snapshots include piece and duck history. Results are
checked against the live position captured at dispatch, even when analyzing a
different recorded position.

Core scores are searched-side centipawns, with +/-100000 indicating mate without a
mate distance. The C ABI converts scores to White's perspective. Java adapts
analysis callbacks back to the existing side-to-move listener contract; the
Analysis view performs its usual White-perspective conversion. Fixed-depth limits are 1–15; quiescence has the native five-ply
limit. Timed searches retain only a completed iteration. Stop interrupts the
owned worker; abort also invalidates Java result delivery. A native failure or
invalid request reports an error and never falls back to C++.

`search_with_progress` emits a typed progress event after each completed depth
and exactly one final event, including terminal and interrupted searches. Events
carry search identity, evaluation availability, completed depth, evaluation count,
principal variation and monotonic elapsed microseconds. The C ABI calls JNI
synchronously on the search worker for progress and returns the final result;
Java posts both to the main thread in order, without polling JNI. N/s and display
text are calculated in Java, never in the core. Evaluation counts follow C++:
one initial root count, each quiescence evaluation and terminal alpha-beta leaf.
Duck preserves the separate native alpha-beta algorithm: depth counts full turns,
quiescence is unused, and placement candidates are the opponent’s non-capturing
move destinations in their original order, including duplicates. Legal turn
generation still exposes every valid empty placement square. The JNI result
buffers accommodate promotion-plus-placement coordinates and fifteen full PV turns.

The principal variation tracks the selected line separately from the C++ per-ply
best-move ordering heuristic, without changing move ordering or tie-breaking.
It includes searched main-line plies, excluding quiescence continuations.

Stop retains the last completed depth (no move if interrupted before completion).
Abort invalidates the search identity before stopping. A worker destroys its
native handle under the same lock used by cancellation, only after native search
returns. The engine remains busy through main-thread final delivery; completion
callbacks run afterward, when a replacement can safely start. Queued progress and
results check identity and destruction, and Play checks the captured Game position
again before each move listener. Closing EngineSession removes its listener and
invalidates delivery immediately; native teardown finishes on the worker.
Like the reference engine, FEN fullmove counters are rebased to 1 at the Starting
Position, and the early-queen heuristic counts plies from that position.

## Build

Install Rust (tested with 1.99.1), Cargo, and the four Android targets:

```sh
rustup target add aarch64-linux-android armv7-linux-androideabi i686-linux-android x86_64-linux-android
./gradlew :app:assembleFossDebug
```

Gradle invokes `scripts/build-rust-android.sh` using the configured Android NDK.
Cargo must be on PATH, or set `CARGO` to its executable. The build script supports
macOS and Linux and packages all four Android ABIs. Dependencies are entirely
local; `Cargo.lock` is checked in and builds use `--locked`.

## Validation

```sh
scripts/test-rust-engine.sh
./gradlew :app:testFossDebugUnitTest :app:assembleFossDebugAndroidTest
adb install -r app/build/outputs/apk/foss/debug/app-foss-debug.apk
adb install -r app/build/outputs/apk/androidTest/foss/debug/app-foss-debug-androidTest.apk
adb shell am instrument -w jwtc.android.chess.test/jwtc.android.chess.engine.RustEngineInstrumentation
```

The host runner requires CMake and GoogleTest. It compares ordered legal moves,
resulting FENs, static evaluations, terminal states, completed depth and exact
best moves and evaluation counts against the compiled C++ implementation. Fixtures cover native
regressions and a seeded 32-ply game path, with quiescence on and off. Perft and
invalid-input tests exercise the core independently of search. Device tests use
the real JNI, EngineSession and GameApi without an Activity or test doubles,
covering progress, stop ordering, abort/restart, pre-depth interruption, stale
navigation, queued results on teardown, terminal positions and errors.

## Parity decisions

The following C++ bugs were found during the port and approved for correction in
both engines before establishing the parity baseline:

- Single-bishop mobility passed a square index to a bitboard-taking function.
- Promotion added the promoted piece without subtracting the pawn's value.
- Passed-pawn masks were absent for White and malformed for Black.
- Capturing an original rook did not clear its castling right.
- Repetition used an incremental opening-book hash that did not represent
  castling rights or legal en-passant availability. Repetition now compares
  position fields directly; the historical opening-book hash remains unchanged.

Other native evaluation and search behavior is preserved, including square
iteration order (upper 32 bits first), swap-based move ordering, quiescence
stand-pat behavior while in check, and the material-draw heuristic. Performance
is informational; exact deterministic agreement is the acceptance gate.

### Known Duck repetition limitation

C++ `sameRepetitionPosition` does not compare the duck square. Different Duck
placements can therefore count as the same position. Rust reproduces this so both
engines agree, and the Rust repetition regression records that behavior. Castling attack
checks, asymmetric duck occupancy in mobility evaluation, candidate ordering and
draw/depth behavior likewise follow the native implementation.
