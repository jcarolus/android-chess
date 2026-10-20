use chess_core::{search, SearchControl, SearchLimit, SearchOptions, SearchRequest, Variant};
use std::time::Instant;

// Runs a search from a FEN and prints the node count and N/s.
fn main() {
    let mut args = std::env::args().skip(1);
    let fen = args
        .next()
        .unwrap_or_else(|| "r1bq1rk1/ppp2ppp/2np1n2/2b1p3/2B1P3/2NP1N2/PPP2PPP/R1BQ1RK1 w - - 0 1".into());
    // The limit is milliseconds ("10000") or a fixed depth ("d5").
    let limit = match args.next() {
        None => SearchLimit::TimeMillis(10_000),
        Some(s) => match s.strip_prefix('d') {
            Some(depth) => SearchLimit::Depth(depth.parse().unwrap()),
            None => SearchLimit::TimeMillis(s.parse().unwrap()),
        },
    };
    let quiescence = args.next().map_or(true, |s| s != "0");

    let request = SearchRequest::new(
        1,
        Variant::Standard,
        &fen,
        vec![],
        limit,
        SearchOptions { quiescence },
    )
    .unwrap();

    let start = Instant::now();
    let result = search(&request, &SearchControl::default()).unwrap();
    let seconds = start.elapsed().as_secs_f64();

    println!(
        "engine=rust depth={} value={} nodes={} seconds={:.3} N/s={:.0}",
        result.completed_depth,
        result.score,
        result.nodes,
        seconds,
        result.nodes as f64 / seconds
    );
}
