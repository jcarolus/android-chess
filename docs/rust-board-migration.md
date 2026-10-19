# Rust board migration: readiness for C++ removal

## State ownership

`GameApi` uses `RustBoard`, a Java-owned sequence of board snapshots. Each accepted
move stores its resulting FEN, coordinate, packed move, Duck placement, query
result and incremental ECO hash. Undo removes a snapshot; variation navigation
and restoration replay the selected path. Rust calls rebuild from the Starting
Position and complete coordinate history, preserving repetition detection.

The existing sharing behavior between board screens and the hotspot service is
preserved by a shared Java board. Native code holds no live board between calls.
Each `GameApi` retains its own Game Tree and restores it when returning to that
screen. The Variant is retained with the game, including after Chess 960 castling
rights disappear.

`BoardPosition` handles editable FEN data and coordinate-to-packed-move conversion.
Incomplete setup positions can be displayed and edited without asking Rust to
accept an illegal position. Rust validates playable positions and computes legal
moves, SAN and game state. Chess 960 generation covers all 960 seeds.

Duck piece moves use a detached Rust preview. They enter the complete coordinate
history only after a valid Duck placement, unless the piece move ends the game.
An invalid placement leaves the preview intact; undo restores the preceding
complete turn. The search API continues to require complete turns.

`EcoHash` retains the existing opening database's initial and incremental hash
conventions. ECO probing and legal-move packing no longer need a C++ board.
Practice Position imports use blocking Rust searches with private search handles;
the obsolete C++ search wrappers and result decoder have been removed from
`GameApi`.

## Validation

Validated on 2026-10-19:

- `scripts/test-rust-engine.sh`: C++ unit tests, Rust tests and all seven ignored
  differential suites pass, including a new detached Duck-preview regression.
- `./gradlew testFossDebugUnitTest assembleFossDebug assembleFossDebugAndroidTest
  assemblePlayStoreDebug --offline`: both app flavors build; 145 JVM tests pass.
- `RustEngineInstrumentation` on a Motorola One Vision: board ownership,
  repetition after undo/redo, PGN variation import and failed-import rollback,
  editable setup, Chess 960 variant restoration, stationary-king and king-to-rook
  castling, Duck preview/completion/undo, engine sessions and ECO probing.
- Device adapter differential checks compare packed legal moves, FEN, game state,
  Duck state and ECO hashes against the independent C++ reference across seeded
  Standard, Chess 960, Duck and promotion/en-passant histories.
- Mutating the C++ reference board leaves the Java-owned production position and
  repetition state unchanged.

## Remaining work in #271

There are no active production consumers of `jwtc.chess.JNI`. `JNI.java`, the C++
engine, its packaging and the differential reference tests remain deliberately
available for validation during the production soak period.

Before deleting them, complete the manual Play/Analysis/Puzzles/Practice checks
and the release soak requested by #271. The automated device checks above do not
claim to replace a release soak or manual screen verification. When removing the
legacy native build, retain the Rust JNI glue and its build targets, which are
currently also declared in `native/project/jni/Android.mk`. Retire the C++-dependent
test portions and update ADR 0001 as part of that deletion.
