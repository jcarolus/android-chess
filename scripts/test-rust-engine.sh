#!/usr/bin/env bash
set -euo pipefail
root="$(cd "$(dirname "$0")/.." && pwd)"
build="${CHESS_NATIVE_BUILD:-$root/native/project/jni/build-tests}"
cmake -S "$root/native/project/jni" -B "$build"
cmake --build "$build" -j 4
ctest --test-dir "$build" --output-on-failure
export CHESS_REFERENCE="$build/tests/chess_parity_reference"
"${CARGO:-cargo}" test --locked --release --manifest-path "$root/native/rust/Cargo.toml"
"${CARGO:-cargo}" test --locked --release --manifest-path "$root/native/rust/Cargo.toml" --test parity -- --ignored
