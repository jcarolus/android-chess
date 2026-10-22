use chess_core::*;
const START: &str = "rnbqkbnr/pppppppp/8/8/8/8/PPPPPPPP/RNBQKBNR w KQkq - 0 1";

#[test]
fn complete_turns_round_trip_and_incomplete_input_is_atomic() {
    let mut p = Position::from_fen(Variant::Duck, START).unwrap();
    assert_eq!(p.legal_moves().len(), 640);
    for invalid in ["e2e4", "@d5", "e2e4@e4", "e2e4@a9"] {
        assert!(p.apply(invalid).is_err());
        assert_eq!(p.fen(), START);
    }
    p.apply("e2e4@d5").unwrap();
    assert_eq!(p.history(), &["e2e4@d5"]);
    assert_eq!(
        p.fen(),
        "rnbqkbnr/pppppppp/8/3$4/4P3/8/PPPP1PPP/RNBQKBNR b KQkq e3 0 1"
    );
    assert!(p.apply("d7d5@e2").is_err());
    assert!(p.apply("e7e5@d5").is_err());
    p.apply("e7e5@e2").unwrap();
    assert_eq!(
        Position::from_fen(Variant::Duck, &p.fen()).unwrap().fen(),
        p.fen().trim_end_matches("2").to_owned() + "1"
    );
    assert!(Position::from_fen(Variant::Standard, &p.fen()).is_err());
}

#[test]
fn promotion_and_king_capture_are_complete_turns() {
    let mut p = Position::from_fen(Variant::Duck, "4k3/P7/8/8/8/8/7p/4K3 w - - 0 1").unwrap();
    p.apply("a7a8n@d5").unwrap();
    assert!(p.fen().starts_with("N3k3/8/8/3$4/"));
    let mut p = Position::from_fen(Variant::Duck, "4k3/4R3/8/3$4/8/8/8/4K3 w - - 0 1").unwrap();
    p.apply("e7e8").unwrap();
    assert_eq!(p.history(), &["e7e8"]);
    assert_eq!(p.game_state(), GameState::Mate);
    assert_eq!(p.evaluate(), -100000);
    assert!(p.legal_moves().is_empty());
    assert!(p.apply("e1e2@a1").is_err());
}

#[test]
fn search_returns_replayable_full_turns_and_terminal_results() {
    for (fen, history) in [
        (START, vec!["e2e4@d5"]),
        ("4k3/4R3/8/3$4/8/8/8/4K3 w - - 0 1", vec![]),
        ("4k3/4R3/8/3$4/8/8/8/4K3 w - - 0 1", vec!["e7e8"]),
    ] {
        let request = SearchRequest::new(
            7,
            Variant::Duck,
            fen,
            history
                .iter()
                .map(|m| CoordinateMove::parse(m).unwrap())
                .collect(),
            SearchLimit::Depth(2),
            SearchOptions { quiescence: true },
        )
        .unwrap();
        let result = search(&request, &SearchControl::default()).unwrap();
        let mut replay = request.position().clone();
        for m in &result.pv {
            replay.apply(m.as_str()).unwrap();
        }
        if request.position().game_state() == GameState::Mate {
            assert!(result.best_move.is_none());
            assert_eq!(result.score, -100000);
        } else {
            assert_eq!(result.completed_depth, 2);
            assert_eq!(result.best_move, result.pv.first().cloned());
            if history.is_empty() {
                assert_eq!(result.best_move.unwrap().as_str(), "e7e8");
            } else {
                assert!(result.best_move.unwrap().as_str().contains('@'));
            }
        }
    }
}

#[test]
fn request_omits_only_a_legal_unfinished_trailing_piece_move() {
    let request = |history: &[&str]| {
        SearchRequest::new(
            9,
            Variant::Duck,
            START,
            history
                .iter()
                .map(|m| CoordinateMove::parse(m).unwrap())
                .collect(),
            SearchLimit::Depth(1),
            SearchOptions { quiescence: false },
        )
    };
    let prepared = request(&["e2e4@d5", "e7e5"]).unwrap();
    let complete = request(&["e2e4@d5"]).unwrap();
    assert_eq!(prepared.position().fen(), complete.position().fen());
    assert_eq!(prepared.position().history(), &["e2e4@d5"]);
    assert!(request(&["e2e4", "e7e5@d5"]).is_err());
    assert!(request(&["e2e5"]).is_err());
}

#[test]
fn duck_repetition_preserves_native_position_identity() {
    let mut p = Position::from_fen(Variant::Duck, START).unwrap();
    for m in [
        "g1f3@a3", "g8f6@a4", "f3g1@a5", "f6g8@a6", "g1f3@b3", "g8f6@b4", "f3g1@b5", "f6g8",
    ] {
        p.apply(m).unwrap();
    }
    assert_eq!(p.game_state(), GameState::DrawRepetition);
}

#[test]
fn a_draw_ending_piece_move_is_not_discarded_as_pending() {
    let request = SearchRequest::new(
        10,
        Variant::Duck,
        "4k3/8/8/8/8/8/4P3/4K3 b - - 99 1",
        vec![CoordinateMove::parse("e8d8").unwrap()],
        SearchLimit::Depth(1),
        SearchOptions { quiescence: false },
    )
    .unwrap();
    assert_eq!(request.position().history(), &["e8d8"]);
    assert_eq!(request.position().game_state(), GameState::DrawFiftyMove);
    assert!(search(&request, &SearchControl::default())
        .unwrap()
        .best_move
        .is_none());
}

#[test]
fn malformed_duck_positions_are_rejected() {
    for fen in [
        "8/8/8/8/8/8/8/8 w - - 0 1",
        "4k3/8/8/3$$3/8/8/8/4K3 w - - 0 1",
    ] {
        assert!(Position::from_fen(Variant::Duck, fen).is_err());
    }
    for token in ["@d5", "e2e4@d5@a3", "e2e4@", "e2e4@é", "a7a8k@d5"] {
        assert!(CoordinateMove::parse(token).is_err());
    }
}

#[test]
fn piece_preview_is_detached_and_complete_turns_still_require_placement() {
    let mut p = Position::from_fen(Variant::Duck, START).unwrap();
    let preview = p.preview_piece("e2e4").unwrap();
    assert_eq!(p.fen(), START);
    assert_eq!(preview.piece_at("e4").unwrap(), Some('P'));
    assert_eq!(preview.piece_at("e2").unwrap(), None);
    assert!(!preview.white_to_move());
    assert!(p.apply("e2e4").is_err());
    assert!(p.preview_piece("e2e5").is_err());
    assert!(p.preview_piece("e2e4@d5").is_err());
    p.apply("e2e4@d5").unwrap();
    assert_eq!(p.history(), &["e2e4@d5"]);
    let capture = Position::from_fen(Variant::Duck, "4k3/4R3/8/3$4/8/8/8/4K3 w - - 0 1").unwrap();
    assert_eq!(
        capture.preview_piece("e7e8").unwrap().game_state(),
        GameState::Mate
    );
}
