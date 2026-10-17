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

#[test]
fn timed_progress_can_stop_and_retains_the_completed_iteration() {
    use chess_core::{search_with_progress, SearchEvent};
    let request = SearchRequest::new(
        249,
        Variant::Standard,
        "rnbqkbnr/pppppppp/8/8/8/8/PPPPPPPP/RNBQKBNR w KQkq - 0 1",
        vec![],
        SearchLimit::TimeMillis(60_000),
        SearchOptions { quiescence: false },
    )
    .unwrap();
    let control = SearchControl::default();
    let mut events = Vec::new();
    let result = search_with_progress(&request, &control, |event| {
        if let SearchEvent::Progress(_) = &event {
            control.stop();
        }
        events.push(event);
    })
    .unwrap();
    assert_eq!(result.completed_depth, 1);
    assert!(result.interrupted);
    assert_eq!(events.len(), 2);
    let SearchEvent::Progress(progress) = &events[0] else {
        panic!("missing progress")
    };
    let SearchEvent::Finished(final_result) = &events[1] else {
        panic!("missing final")
    };
    assert_eq!(progress.search_id, 249);
    assert_eq!(progress.best_move, final_result.best_move);
    assert_eq!(progress.score, final_result.score);
    assert!(progress.nodes > 0);
    assert!(final_result.elapsed_micros >= progress.elapsed_micros);
}

#[test]
fn completed_principal_variation_replays_legally() {
    let request = SearchRequest::new(
        7,
        Variant::Standard,
        "rnbqkbnr/pppppppp/8/8/8/8/PPPPPPPP/RNBQKBNR w KQkq - 0 1",
        vec![],
        SearchLimit::Depth(3),
        SearchOptions { quiescence: false },
    )
    .unwrap();
    let result = search(&request, &SearchControl::default()).unwrap();
    assert_eq!(result.pv.len(), 3);
    assert_eq!(result.pv.first(), result.best_move.as_ref());
    let mut position = request.position().clone();
    for coordinate in result.pv {
        position.apply(coordinate.as_str()).unwrap();
    }
}

#[test]
fn interruption_before_depth_completion_has_no_fabricated_move_or_score() {
    use chess_core::{search_with_progress, SearchEvent};
    let request = SearchRequest::new(
        8,
        Variant::Standard,
        "rnbqkbnr/pppppppp/8/8/8/8/PPPPPPPP/RNBQKBNR w KQkq - 0 1",
        vec![],
        SearchLimit::Depth(10),
        SearchOptions { quiescence: true },
    )
    .unwrap();
    let control = SearchControl::default();
    control.stop();
    let mut events = Vec::new();
    let result = search_with_progress(&request, &control, |event| events.push(event)).unwrap();
    assert!(result.interrupted);
    assert!(!result.has_evaluation);
    assert!(result.best_move.is_none());
    assert_eq!(result.completed_depth, 0);
    assert_eq!(events.len(), 1);
    assert!(matches!(events[0], SearchEvent::Finished(_)));
}

#[test]
fn terminal_search_finishes_without_progress_or_mate_distance() {
    use chess_core::{search_with_progress, SearchEvent};
    for (fen, score) in [
        ("7k/6Q1/5K2/8/8/8/8/8 b - - 0 1", -100000),
        ("k7/8/1QK5/8/8/8/8/8 b - - 0 1", 0),
    ] {
        let request = SearchRequest::new(
            9,
            Variant::Standard,
            fen,
            vec![],
            SearchLimit::TimeMillis(100),
            SearchOptions { quiescence: true },
        )
        .unwrap();
        let mut events = Vec::new();
        let result = search_with_progress(&request, &SearchControl::default(), |event| {
            events.push(event)
        })
        .unwrap();
        assert_eq!(result.score, score);
        assert!(result.has_evaluation);
        assert!(result.best_move.is_none());
        assert_eq!(result.nodes, 0);
        assert_eq!(events.len(), 1);
        assert!(matches!(events[0], SearchEvent::Finished(_)));
    }
}

#[test]
fn timed_search_and_independent_control_finish_without_shared_cancellation() {
    let request = SearchRequest::new(
        10,
        Variant::Standard,
        "rnbqkbnr/pppppppp/8/8/8/8/PPPPPPPP/RNBQKBNR w KQkq - 0 1",
        vec![],
        SearchLimit::TimeMillis(30),
        SearchOptions { quiescence: false },
    )
    .unwrap();
    let stopped = SearchControl::default();
    stopped.stop();
    std::thread::scope(|scope| {
        let cancelled = scope.spawn(|| search(&request, &stopped).unwrap());
        let running = scope.spawn(|| search(&request, &SearchControl::default()).unwrap());
        assert!(!cancelled.join().unwrap().has_evaluation);
        let result = running.join().unwrap();
        assert!(result.has_evaluation);
        assert!(result.completed_depth > 0);
        assert!(result.elapsed_micros < 5_000_000);
    });
}
