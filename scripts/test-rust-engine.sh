#!/usr/bin/env bash
set -euo pipefail
root="$(cd "$(dirname "$0")/.." && pwd)"
"${CARGO:-cargo}" test --locked --release --manifest-path "$root/native/rust/Cargo.toml"
