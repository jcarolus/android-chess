use chess_core::{search, SearchControl, SearchLimit, SearchOptions, SearchRequest, Variant};
#[test]
fn fixed_depth_matches_native_starting_position() {
    let request = SearchRequest::new(
        17,
        Variant::Standard,
        "rnbqkbnr/pppppppp/8/8/8/8/PPPPPPPP/RNBQKBNR w KQkq - 0 1",
        vec![],
        SearchLimit::Depth(2),
        SearchOptions { quiescence: false },
    )
    .unwrap();
    let result = search(&request, &SearchControl::default()).unwrap();
    assert_eq!(result.search_id, 17);
    assert_eq!(result.best_move.unwrap().as_str(), "h2h4");
    assert_eq!(result.score, 0);
    assert_eq!(result.completed_depth, 2);
}
