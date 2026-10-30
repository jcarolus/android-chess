use chess_core::{Position, Variant};
const START: &str = "rnbqkbnr/pppppppp/8/8/8/8/PPPPPPPP/RNBQKBNR w KQkq - 0 1";
#[test]
fn owned_position_replays_coordinate_history() {
    let mut position = Position::from_fen(Variant::Standard, START).unwrap();
    assert_eq!(position.legal_moves().len(), 20);
    position.apply("e2e4").unwrap();
    assert_eq!(
        position.fen(),
        "rnbqkbnr/pppppppp/8/8/4P3/8/PPPP1PPP/RNBQKBNR b KQkq e3 0 1"
    );
    assert_eq!(position.history(), &["e2e4"]);
    assert!(position.apply("e2e5").is_err());
}
#[test]
fn symmetric_initial_evaluation() {
    let position = Position::from_fen(Variant::Standard, START).unwrap();
    assert_eq!(position.evaluate(), 0);
}
fn perft(position: &Position, depth: u8) -> usize {
    if depth == 0 {
        return 1;
    }
    position
        .legal_moves()
        .iter()
        .map(|m| {
            let mut next = position.clone();
            next.apply(m.as_str()).unwrap();
            perft(&next, depth - 1)
        })
        .sum()
}
#[test]
fn standard_move_generation_matches_perft() {
    let p = Position::from_fen(Variant::Standard, START).unwrap();
    assert_eq!(perft(&p, 3), 8902);
}
#[test]
fn special_moves_and_repetition_are_available_without_search() {
    let mut p =
        Position::from_fen(Variant::Standard, "r3k2r/8/8/8/8/8/8/R3K2R w KQkq - 0 1").unwrap();
    p.apply("e1g1").unwrap();
    assert!(p.fen().starts_with("r3k2r/8/8/8/8/8/8/R4RK1 b kq"));
    let mut p = Position::from_fen(Variant::Standard, "4k3/8/8/3pP3/8/8/8/4K3 w - d6 0 1").unwrap();
    p.apply("e5d6").unwrap();
    assert!(p.fen().starts_with("4k3/8/3P4/8/8/8/8/4K3 b"));
    let mut p = Position::from_fen(Variant::Standard, "4k3/P7/8/8/8/8/7p/4K3 w - - 0 1").unwrap();
    p.apply("a7a8n").unwrap();
    assert!(p.fen().starts_with("N3k3/8/"));
    let mut p = Position::from_fen(Variant::Standard, START).unwrap();
    for m in [
        "g1f3", "g8f6", "f3g1", "f6g8", "g1f3", "g8f6", "f3g1", "f6g8",
    ] {
        p.apply(m).unwrap();
    }
    assert_eq!(p.game_state(), chess_core::GameState::DrawRepetition);
}
#[test]
fn invalid_requests_do_not_change_position() {
    let mut p = Position::from_fen(Variant::Standard, START).unwrap();
    for m in ["e2e5", "éé", "a1a9", "a1a2k", ""] {
        assert!(p.apply(m).is_err());
        assert_eq!(p.fen(), START);
    }
    for fen in [
        "",
        "8/8/8/8/8/8/8/8 w - - 0 1",
        "4k3/8/8/8/8/8/8/4K3 w - a9 0 1",
        "4k3/8/8/8/8/8/8/4K3 b - a8 0 1",
    ] {
        assert!(Position::from_fen(Variant::Standard, fen).is_err());
    }
}

#[test]
fn repetition_distinguishes_a_legal_en_passant_option() {
    let mut p =
        Position::from_fen(Variant::Standard, "4k1n1/8/8/3pP3/8/8/8/4K1N1 w - d6 0 1").unwrap();
    for _ in 0..2 {
        for m in ["g1f3", "g8f6", "f3g1", "f6g8"] {
            p.apply(m).unwrap();
        }
    }
    assert_ne!(p.game_state(), chess_core::GameState::DrawRepetition);
    for m in ["g1f3", "g8f6", "f3g1", "f6g8"] {
        p.apply(m).unwrap();
    }
    assert_eq!(p.game_state(), chess_core::GameState::DrawRepetition);
    let mut p =
        Position::from_fen(Variant::Standard, "4k1n1/8/8/r4pPK/8/8/8/6N1 w - f6 0 1").unwrap();
    for _ in 0..2 {
        for m in ["g1f3", "g8h6", "f3g1", "h6g8"] {
            p.apply(m).unwrap();
        }
    }
    assert_eq!(p.game_state(), chess_core::GameState::DrawRepetition);
}

#[test]
fn captured_rook_loses_castling_rights() {
    let mut p =
        Position::from_fen(Variant::Standard, "r3k2r/8/8/8/8/8/8/R3K2R w KQkq - 0 1").unwrap();
    p.apply("a1a8").unwrap();
    assert_eq!(p.fen(), "R3k2r/8/8/8/8/8/8/4K2R b Kk - 0 1");
}

#[test]
fn chess960_castling_coordinates_are_king_to_rook() {
    let p = Position::from_fen(Variant::Chess960, "4k3/8/8/8/8/8/8/R3K2R w KQ - 0 1").unwrap();
    let legal = p.legal_moves();
    let coordinates: Vec<_> = legal.iter().map(|m| m.as_str()).collect();
    assert!(coordinates.contains(&"e1h1"), "{coordinates:?}");
    assert!(coordinates.contains(&"e1a1"), "{coordinates:?}");
    assert!(!coordinates.contains(&"e1g1"), "{coordinates:?}");
    assert!(!coordinates.contains(&"e1c1"), "{coordinates:?}");
}

#[test]
fn chess960_kingside_castle_relocates_the_tracked_rook() {
    let mut p =
        Position::from_fen(Variant::Chess960, "4k3/8/8/8/8/8/8/1R2K1R1 w KQ - 0 1").unwrap();
    p.apply("e1g1").unwrap();
    assert!(
        p.fen().starts_with("4k3/8/8/8/8/8/8/1R3RK1 b - "),
        "{}",
        p.fen()
    );
}

#[test]
fn chess960_castle_where_the_rook_already_sits_on_its_destination() {
    let mut p = Position::from_fen(Variant::Chess960, "4k3/8/8/8/8/8/8/3RK2R w KQ - 0 1").unwrap();
    let legal: Vec<_> = p.legal_moves().iter().map(|m| m.to_string()).collect();
    assert!(legal.contains(&"e1d1".to_string()), "{legal:?}");
    p.apply("e1d1").unwrap();
    assert!(
        p.fen().starts_with("4k3/8/8/8/8/8/8/2KR3R b - "),
        "{}",
        p.fen()
    );
}

#[test]
fn chess960_castle_where_the_king_already_sits_on_its_destination() {
    let mut p = Position::from_fen(Variant::Chess960, "4k3/8/8/8/8/8/8/R1K4R w KQ - 0 1").unwrap();
    let legal: Vec<_> = p.legal_moves().iter().map(|m| m.to_string()).collect();
    assert!(legal.contains(&"c1a1".to_string()), "{legal:?}");
    p.apply("c1a1").unwrap();
    assert!(
        p.fen().starts_with("4k3/8/8/8/8/8/8/2KR3R b - "),
        "{}",
        p.fen()
    );
}

#[test]
fn chess960_castle_swaps_an_adjacent_king_and_rook() {
    let mut p = Position::from_fen(Variant::Chess960, "4k3/8/8/8/8/8/8/5KR1 w K - 0 1").unwrap();
    p.apply("f1g1").unwrap();
    assert!(
        p.fen().starts_with("4k3/8/8/8/8/8/8/5RK1 b - "),
        "{}",
        p.fen()
    );
}

#[test]
fn chess960_tracks_independent_rook_columns_per_colour() {
    // White castles kingside with its rook on g1; Black castles queenside with
    // its rook on a1's mirror, a8. Neither rook's file crosses the other
    // colour's king-to-rook path, so the two castles don't interfere.
    let mut p = Position::from_fen(Variant::Chess960, "r3k3/8/8/8/8/8/8/4K1R1 w Kq - 0 1").unwrap();
    let legal: Vec<_> = p.legal_moves().iter().map(|m| m.to_string()).collect();
    assert!(legal.contains(&"e1g1".to_string()), "{legal:?}");
    p.apply("e1g1").unwrap();
    let legal: Vec<_> = p.legal_moves().iter().map(|m| m.to_string()).collect();
    assert!(legal.contains(&"e8a8".to_string()), "{legal:?}");
    p.apply("e8a8").unwrap();
    assert!(
        p.fen().starts_with("2kr4/8/8/8/8/8/8/5RK1 w - "),
        "{}",
        p.fen()
    );
}

#[test]
fn chess960_rights_do_not_restore_once_the_rook_returns() {
    let mut p = Position::from_fen(Variant::Chess960, "4k3/8/8/8/8/8/8/R3K2R w KQ - 0 1").unwrap();
    for m in ["a1a5", "e8e7", "a5a1", "e7e8"] {
        p.apply(m).unwrap();
    }
    let legal: Vec<_> = p.legal_moves().iter().map(|m| m.to_string()).collect();
    assert!(!legal.contains(&"e1a1".to_string()), "{legal:?}");
    assert!(legal.contains(&"e1h1".to_string()), "{legal:?}");
    assert!(p.fen().contains(" K "), "{}", p.fen());
}

#[test]
fn chess960_fen_drops_a_claimed_right_with_no_matching_rook() {
    let p = Position::from_fen(Variant::Chess960, "4k3/8/8/8/8/8/8/1R2K3 w KQ - 0 1").unwrap();
    let legal: Vec<_> = p.legal_moves().iter().map(|m| m.to_string()).collect();
    assert!(legal.contains(&"e1b1".to_string()), "{legal:?}");
    assert!(!legal.contains(&"e1h1".to_string()), "{legal:?}");
    assert!(p.fen().contains(" Q "), "{}", p.fen());
}

#[test]
fn standard_queenside_castle_only_requires_the_kings_own_transit_to_be_safe() {
    // b1 is attacked but not occupied; FIDE castling only requires c1/d1 (the
    // king's own path) to be unattacked, matching the native reference
    // engine's fast path for classical king/rook squares.
    let p = Position::from_fen(Variant::Standard, "1r2k3/8/8/8/8/8/8/R3K3 w Q - 0 1").unwrap();
    let legal: Vec<_> = p.legal_moves().iter().map(|m| m.to_string()).collect();
    assert!(legal.contains(&"e1c1".to_string()), "{legal:?}");
}

#[test]
fn chess960_queenside_castle_only_requires_the_kings_own_transit_to_be_safe() {
    let p = Position::from_fen(Variant::Chess960, "1r2k3/8/8/8/8/8/8/R3K3 w Q - 0 1").unwrap();
    let legal: Vec<_> = p.legal_moves().iter().map(|m| m.to_string()).collect();
    assert!(legal.contains(&"e1a1".to_string()), "{legal:?}");
}

#[test]
fn rook_leaving_a_non_back_rank_square_keeps_castling_rights() {
    let mut p = Position::from_fen(Variant::Standard, "4k3/8/8/8/R7/8/8/R3K3 w Q - 0 1").unwrap();
    p.apply("a4a5").unwrap();
    assert!(p.fen().contains(" Q "), "{}", p.fen());
}

#[test]
fn chess960_castle_may_not_jump_over_a_piece_beyond_the_rook() {
    // The king travels b1 → g1 past the bishop on e1.
    let p = Position::from_fen(Variant::Chess960, "4k3/8/8/8/8/8/8/1KR1B3 w K - 0 1").unwrap();
    let legal: Vec<_> = p.legal_moves().iter().map(|m| m.to_string()).collect();
    assert!(!legal.contains(&"b1c1".to_string()), "{legal:?}");
}

#[test]
fn chess960_castle_may_not_pass_through_an_attacked_rook_square() {
    // The king passes over f1, which the bishop on b5 attacks.
    let p = Position::from_fen(Variant::Chess960, "4k3/8/8/1b6/8/8/8/4KR2 w K - 0 1").unwrap();
    let legal: Vec<_> = p.legal_moves().iter().map(|m| m.to_string()).collect();
    assert!(!legal.contains(&"e1f1".to_string()), "{legal:?}");
}

#[test]
fn chess960_castle_ignores_attacks_on_squares_the_king_never_crosses() {
    // b1 is attacked, but the king only travels f1 → c1.
    let p = Position::from_fen(Variant::Chess960, "1r2k3/8/8/8/8/8/8/R4K2 w Q - 0 1").unwrap();
    let legal: Vec<_> = p.legal_moves().iter().map(|m| m.to_string()).collect();
    assert!(legal.contains(&"f1a1".to_string()), "{legal:?}");
}
