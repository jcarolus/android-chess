use chess_core::{
    search, GameState, Position, SearchControl, SearchLimit, SearchOptions, SearchRequest, Variant,
};

fn position(fen: &str) -> Position {
    Position::from_fen(Variant::Duck, fen).unwrap()
}
fn rejects(p: &mut Position, mv: &str) {
    let before = (
        p.fen(),
        p.history().to_vec(),
        p.halfmove_clock(),
        p.repetition_count(),
    );
    assert!(p.apply(mv).is_err(), "{mv}: {}", p.fen());
    assert_eq!(
        (
            p.fen(),
            p.history().to_vec(),
            p.halfmove_clock(),
            p.repetition_count()
        ),
        before
    );
}

#[test]
fn attacks_and_adjacent_kings_do_not_restrict_duck_moves() {
    for (fen, mv) in [
        ("4r2k/8/8/8/8/8/8/R3K3 w - - 0 1", "a1a2@b3"),
        ("4r2k/8/8/8/8/8/8/4K3 w - - 0 1", "e1e2@b3"),
        ("8/8/8/8/8/4k3/8/4K3 w - - 0 1", "e1e2@b3"),
        ("7k/8/8/8/8/8/4R3/4K3 w - - 0 1", "e2d2@b3"),
    ] {
        let mut p = position(fen);
        assert_eq!(p.game_state(), GameState::Play);
        p.apply(mv).unwrap();
        assert_eq!(p.game_state(), GameState::Play);
    }
}

#[test]
fn duck_is_an_uncapturable_blocker_for_every_piece() {
    for (fen, illegal, legal) in [
        (
            "7k/8/8/8/8/$7/8/R6K w - - 0 1",
            vec!["a1a3", "a1a4"],
            "a1a2",
        ),
        (
            "7k/8/8/8/8/2$5/8/B6K w - - 0 1",
            vec!["a1c3", "a1d4"],
            "a1b2",
        ),
        (
            "7k/8/8/8/8/2$5/8/Q6K w - - 0 1",
            vec!["a1c3", "a1d4"],
            "a1b2",
        ),
        ("7k/8/8/8/8/2$5/8/1N5K w - - 0 1", vec!["b1c3"], "b1a3"),
        ("7k/8/8/8/8/8/4$3/4K3 w - - 0 1", vec!["e1e2"], "e1d2"),
        (
            "7k/8/8/8/8/4$3/4P3/K7 w - - 0 1",
            vec!["e2e3", "e2e4"],
            "a1a2",
        ),
        ("7k/8/8/8/4$3/8/4P3/K7 w - - 0 1", vec!["e2e4"], "e2e3"),
        ("7k/8/8/8/8/3$4/4P3/K7 w - - 0 1", vec!["e2d3"], "e2e3"),
    ] {
        let p = position(fen);
        for mv in illegal {
            rejects(&mut p.clone(), &format!("{mv}@b5"));
        }
        p.clone().apply(&format!("{legal}@b5")).unwrap();
    }
    let mut p = position("7k/8/8/8/8/8/1$6/1N5K w - - 0 1");
    p.apply("b1c3@b1").unwrap();
    let mut p = position("7k/8/8/8/8/2$5/8/1n5K b - - 0 1");
    rejects(&mut p, "b1c3@b5");
}

#[test]
fn en_passant_and_vacated_squares_accept_duck_placement() {
    for (fen, mv, empty) in [
        ("7k/8/8/3pP3/8/8/8/K7 w - d6 12 1", "e5d6@d5", "e5"),
        ("7k/8/8/8/3Pp3/8/8/K7 b - d3 12 1", "e4d3@d4", "e4"),
        ("7k/8/8/8/8/8/4P3/K7 w - - 12 1", "e2e4@e2", "e3"),
        ("7k/8/8/8/8/8/8/4K2R w K - 12 1", "e1g1@h1", "e1"),
        ("7k/8/8/8/8/8/8/R3K3 w Q - 12 1", "e1c1@e1", "a1"),
    ] {
        let mut p = position(fen);
        p.apply(mv).unwrap();
        assert_eq!(p.piece_at(empty).unwrap(), None);
        assert_eq!(
            p.piece_at(mv.split('@').nth(1).unwrap()).unwrap(),
            Some('$')
        );
    }
    let mut p = position("7k/8/3$4/3pP3/8/8/8/K7 w - d6 0 1");
    rejects(&mut p, "e5d6@c3");
    p.apply("a1a2@b3").unwrap();
    p.apply("h8h7@c3").unwrap();
    rejects(&mut p, "e5d6@b3");
    let mut p = position("7k/8/8/3$4/8/8/4P3/K7 w - - 23 1");
    for mv in ["e2e4@d5", "e2e4@e4", "e2e4@a1"] {
        rejects(&mut p, mv);
    }
}

#[test]
fn king_captures_end_immediately_for_both_colours_and_promotions() {
    for (fen, mv) in [
        ("4k3/4R3/8/8/8/8/8/K7 w - - 99 1", "e7e8"),
        ("7k/8/8/8/8/8/4r3/4K3 b - - 99 1", "e2e1"),
        ("1k6/P7/8/8/8/8/8/7K w - - 99 1", "a7b8q"),
        ("7k/8/8/8/8/8/p7/1K6 b - - 99 1", "a2b1n"),
        ("4k3/8/3N4/8/8/8/8/K7 w - - 99 1", "d6e8"),
    ] {
        let mut p = position(fen);
        rejects(&mut p, &format!("{mv}@c3"));
        p.apply(mv).unwrap();
        assert_eq!(p.game_state(), GameState::Mate);
        assert!(p.legal_moves().is_empty());
        rejects(&mut p, "h8h7@c3");
    }
}

#[test]
fn final_duck_placement_decides_stalemate_for_either_colour() {
    for (fen, blocked, open) in [
        ("7K/8/8/8/8/8/pp6/k7 w - - 0 1", "h8h7@b1", "h8h7@c3"),
        ("K7/PP6/8/8/8/8/8/7k b - - 0 1", "h1h2@b8", "h1h2@c3"),
    ] {
        let p = position(fen);
        let mut terminal = p.clone();
        terminal.apply(blocked).unwrap();
        assert_eq!(terminal.game_state(), GameState::DuckStalemateWin);
        assert!(terminal.legal_moves().is_empty());
        rejects(&mut terminal, "a1b1@c3");
        let mut playable = p.clone();
        playable.apply(open).unwrap();
        assert_eq!(playable.game_state(), GameState::Play);
    }
}

#[test]
fn draw_ending_turns_omit_and_reject_placement_and_stop_play() {
    let mut p = position("7k/8/8/8/8/8/8/KR6 w - - 99 1");
    rejects(&mut p, "b1b2@c3");
    p.apply("b1b2").unwrap();
    assert_eq!(p.game_state(), GameState::DrawFiftyMove);
    assert!(p.legal_moves().is_empty());
    rejects(&mut p, "h8h7@c3");
    let mut p = position("6nk/8/8/8/8/8/8/KN6 w - - 0 1");
    for mv in [
        "b1c3@a3", "g8f6@a4", "c3b1@a5", "f6g8@a6", "b1c3@b3", "g8f6@b4", "c3b1@b5",
    ] {
        p.apply(mv).unwrap();
    }
    rejects(&mut p, "f6g8@b6");
    p.apply("f6g8").unwrap();
    assert_eq!(p.game_state(), GameState::DrawRepetition);
    assert!(p.legal_moves().is_empty());
    rejects(&mut p, "b1c3@b6");
}

#[test]
fn search_uses_placement_to_block_an_immediate_king_capture() {
    let fen = "4r2k/8/8/8/8/8/3P1P2/3RKR2 w - - 0 1";
    let request = SearchRequest::new(
        1,
        Variant::Duck,
        fen,
        vec![],
        SearchLimit::Depth(2),
        SearchOptions { quiescence: false },
    )
    .unwrap();
    let result = search(&request, &SearchControl::default()).unwrap();
    let mut p = position(fen);
    let best = result.best_move.unwrap();
    assert!(best.as_str().split_once('@').unwrap().1.starts_with('e'));
    p.apply(best.as_str()).unwrap();
    for mv in p.legal_moves() {
        let mut next = p.clone();
        next.apply(mv.as_str()).unwrap();
        assert_ne!(next.game_state(), GameState::Mate, "allowed {mv}");
    }
}

#[test]
fn search_does_not_give_the_opponent_a_stalemate_win() {
    let fen = "7K/8/8/8/8/8/pp6/k7 w - - 0 1";
    let mut safe = position(fen);
    safe.apply("h8h7@c3").unwrap();
    assert_eq!(safe.game_state(), GameState::Play);
    for depth in [1, 2] {
        let request = SearchRequest::new(
            1,
            Variant::Duck,
            fen,
            vec![],
            SearchLimit::Depth(depth),
            SearchOptions { quiescence: false },
        )
        .unwrap();
        let result = search(&request, &SearchControl::default()).unwrap();
        let mut p = position(fen);
        p.apply(result.best_move.unwrap().as_str()).unwrap();
        assert_eq!(p.game_state(), GameState::Play);
    }
}

#[test]
fn search_returns_a_replayable_draw_ending_piece_move_without_placement() {
    let fen = "7k/8/8/8/8/8/8/KR6 w - - 99 1";
    let request = SearchRequest::new(
        1,
        Variant::Duck,
        fen,
        vec![],
        SearchLimit::Depth(1),
        SearchOptions { quiescence: false },
    )
    .unwrap();
    let result = search(&request, &SearchControl::default()).unwrap();
    let best = result.best_move.unwrap();
    assert!(!best.as_str().contains('@'));
    let mut p = position(fen);
    p.apply(best.as_str()).unwrap();
    assert_eq!(p.game_state(), GameState::DrawFiftyMove);
    assert_eq!(result.score, 0);
}

#[test]
fn search_returns_a_replayable_repetition_ending_piece_move_without_placement() {
    use chess_core::CoordinateMove;
    let fen = "6nk/8/8/8/8/8/8/KN5R w - - 0 1";
    let history = [
        "b1c3@a3", "g8f6@a4", "c3b1@a5", "f6g8@a6", "b1c3@b3", "g8f6@b4", "c3b1@b5",
    ];
    let request = SearchRequest::new(
        1,
        Variant::Duck,
        fen,
        history
            .iter()
            .map(|m| CoordinateMove::parse(m).unwrap())
            .collect(),
        SearchLimit::Depth(1),
        SearchOptions { quiescence: false },
    )
    .unwrap();
    let result = search(&request, &SearchControl::default()).unwrap();
    let best = result.best_move.unwrap();
    assert_eq!(best.as_str(), "f6g8");
    let mut p = request.position().clone();
    p.apply(best.as_str()).unwrap();
    assert_eq!(p.game_state(), GameState::DrawRepetition);
    assert_eq!(result.score, 0);
}
