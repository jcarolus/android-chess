#!/usr/bin/env bash
# Measures Rust engine search speed (N/s) on one position.
# Usage: scripts/bench-search.sh [fen] [millis|dN] [quiescence 0|1]
set -euo pipefail
root="$(cd "$(dirname "$0")/.." && pwd)"
fen="${1:-r1bq1rk1/ppp2ppp/2np1n2/2b1p3/2B1P3/2NP1N2/PPP2PPP/R1BQ1RK1 w - - 0 1}"
limit="${2:-10000}"
quiescence="${3:-1}"

"${CARGO:-cargo}" build --locked --release --example bench_search --manifest-path "$root/native/rust/Cargo.toml" >/dev/null

"$root/native/rust/target/release/examples/bench_search" "$fen" "$limit" "$quiescence"
