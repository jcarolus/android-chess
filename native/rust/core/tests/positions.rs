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
    assert!(Position::from_fen(Variant::Duck, START).is_err());
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
