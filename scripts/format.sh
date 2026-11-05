#!/usr/bin/env bash
set -euo pipefail
root="$(cd "$(dirname "$0")/.." && pwd)"
cd "$root"

status=0
./gradlew spotlessJavaApply || status=1
"${CARGO:-cargo}" fmt --all --manifest-path "$root/native/rust/Cargo.toml" || status=1
exit "$status"
