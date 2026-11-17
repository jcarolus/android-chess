use chess_core::{GameState, Position, Variant};

const START: &str = "rnbqkbnr/pppppppp/8/8/8/8/PPPPPPPP/RNBQKBNR w KQkq - 0 1";

fn standard() -> Position {
    Position::from_fen(Variant::Standard, START).unwrap()
}

#[test]
fn replays_mate_and_keeps_the_main_line_end_after_validating_variations() {
    let tokens = [
        "f3", "(", "e4", "e5", "(", "c5", ")", ")", "e5", "g4", "Qh4#", "(", "Nf6", ")",
    ];
    let actual = standard().replay_san(tokens).unwrap();
    let mut expected = standard();
    for coordinate in ["f2f3", "e7e5", "g2g4", "d8h4"] {
        expected.apply(coordinate).unwrap();
    }
    assert_eq!(actual.fen(), expected.fen());
    assert_eq!(actual.hash_key(), expected.hash_key());
    assert_eq!(actual.history(), expected.history());
    assert_eq!(actual.game_state(), GameState::Mate);
}

#[test]
fn rejects_illegal_or_malformed_variations() {
    let error = standard()
        .replay_san(["e4", "e5", "(", "c5", "Ke4", ")"])
        .err()
        .unwrap();
    assert!(error.to_string().contains("SAN token 5"));
    for tokens in [
        &["(", "e4", ")"][..],
        &["e4", "(", ")"][..],
        &["e4", "(", "d4"][..],
        &["e4", ")"][..],
    ] {
        assert!(standard().replay_san(tokens.iter().copied()).is_err());
    }
}

#[test]
fn resolves_explicit_source_squares_and_checks_ambiguity() {
    let mut start =
        Position::from_fen(Variant::Standard, "4k3/8/8/8/8/8/8/1N1NK3 w - - 0 1").unwrap();
    assert!(start.apply_san("Nc3").is_err());
    assert!(start.apply_san("N1c3").is_err());
    assert!(start.apply_san("Nb1xc3").is_err());
    start.apply_san("Nb1c3").unwrap();
    assert_eq!(start.piece_at("c3").unwrap(), Some('N'));
    assert_eq!(start.piece_at("d1").unwrap(), Some('N'));
}

#[test]
fn preserves_promotion_en_passant_and_chess960_castling() {
    for (variant, fen, san, coordinate) in [
        (
            Variant::Standard,
            "4k3/P7/8/8/8/8/7p/4K3 w - - 0 1",
            "a8=N",
            "a7a8n",
        ),
        (
            Variant::Standard,
            "4k3/8/8/3pP3/8/8/8/4K3 w - d6 0 1",
            "exd6",
            "e5d6",
        ),
        (
            Variant::Chess960,
            "4k3/8/8/8/8/8/8/1R2K1R1 w KQ - 0 1",
            "0-0",
            "e1g1",
        ),
    ] {
        let mut expected = Position::from_fen(variant, fen).unwrap();
        let actual = expected.clone().replay_san([san]).unwrap();
        expected.apply(coordinate).unwrap();
        assert_eq!(actual.fen(), expected.fen());
    }
}

#[test]
fn keeps_repetition_history_and_variant_end_conditions() {
    let actual = standard()
        .replay_san(["Ng1f3", "Nf6", "Ng1", "Ng8", "Nf3", "Nf6", "Ng1", "Ng8"])
        .unwrap();
    assert_eq!(actual.game_state(), GameState::DrawRepetition);
    let hill = Position::from_fen(Variant::KingOfTheHill, "7k/8/8/8/8/3K4/8/8 w - - 0 1").unwrap();
    assert_eq!(
        hill.clone().replay_san(["Kd4"]).unwrap().game_state(),
        GameState::HillWin
    );
    assert!(hill.replay_san(["Kd4", "Kh7"]).is_err());
}

#[test]
fn validates_a_complete_duck_turn_atomically() {
    let mut duck = Position::from_fen(Variant::Duck, START).unwrap();
    let start = duck.fen();
    assert!(duck.apply_san("e4").is_err());
    assert!(duck.apply_san("e4@e4").is_err());
    assert_eq!(duck.fen(), start);
    duck.apply_san("e4@d5").unwrap();
    assert_eq!(duck.piece_at("d5").unwrap(), Some('$'));
    assert!(standard().apply_san("e4@d5").is_err());

    let king_capture =
        Position::from_fen(Variant::Duck, "4k3/8/8/8/8/8/4R3/4K3 w - - 0 1").unwrap();
    assert!(king_capture.clone().replay_san(["Rxe8@a1"]).is_err());
    assert_eq!(
        king_capture.replay_san(["Rxe8"]).unwrap().game_state(),
        GameState::Mate
    );
}
