# Rust Local Engine

The `core` crate owns Standard positions, coordinate move history, legal move
validation/application, game state, static evaluation and search. It has no JNI,
Android, C++ or external crate dependency. `SearchRequest::new` validates and
replays a Starting Position and complete history before search. Requests are
immutable and never borrow a live board. Chess 960 and Duck have explicit Variant
values but are rejected in this Standard-only slice.

`bridge` exposes an owned search handle and a small C ABI. The separate
`jni/rust-chess-jni.cpp` adapter packages that static Rust library as
`librust-chess-jni.so`. The existing `libchess-jni.so` continues to manage the app's
board. `EngineSession.forRustTesting` is the internal Java integration entry;
production engine preferences do not select Rust.

Scores are White-perspective centipawns, with +/-100000 indicating mate without a
mate distance. Fixed-depth limits are 1–15; quiescence has the native five-ply
limit. Timed searches retain only a completed iteration. Stop interrupts the
owned worker; abort also invalidates Java result delivery. A native failure or
invalid request reports an error and never falls back to C++.

The Rust result includes a verified one-move PV prefix. The C++ per-ply best-move
array is retained for ordering parity, but it is not a trustworthy full PV.
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
best moves against the compiled C++ implementation. Fixtures cover native
regressions and a seeded 32-ply game path, with quiescence on and off. Perft and
invalid-input tests exercise the core independently of search. Device tests use
the real JNI, EngineSession and GameApi without an Activity or test doubles.

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
