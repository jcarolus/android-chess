use chess_core::{GameState, Position, Variant};
const START: &str = "rnbqkbnr/pppppppp/8/8/8/8/PPPPPPPP/RNBQKBNR w KQkq - 0 1";

#[test]
fn piece_at_reports_fen_characters_and_empty_squares() {
    let p = Position::from_fen(Variant::Standard, START).unwrap();
    assert_eq!(p.piece_at("e1").unwrap(), Some('K'));
    assert_eq!(p.piece_at("e8").unwrap(), Some('k'));
    assert_eq!(p.piece_at("e4").unwrap(), None);
    assert!(p.piece_at("e9").is_err());
}

#[test]
fn piece_at_reports_the_duck() {
    let p = Position::from_fen(Variant::Duck, "4k3/8/8/3$4/8/8/8/4K3 w - - 0 1").unwrap();
    assert_eq!(p.piece_at("d5").unwrap(), Some('$'));
}

#[test]
fn castling_rights_reflect_the_fen_and_are_lost_after_a_king_move() {
    let mut p = Position::from_fen(Variant::Standard, START).unwrap();
    assert_eq!(p.castling_rights(), [true, true, true, true]);
    for m in ["e2e4", "e7e5", "e1e2", "e8e7"] {
        p.apply(m).unwrap();
    }
    assert_eq!(p.castling_rights(), [false, false, false, false]);
}

#[test]
fn attackers_lists_every_square_of_the_given_colour_attacking_the_target() {
    let p = Position::from_fen(
        Variant::Standard,
        "4k3/8/8/8/8/8/PPPPPPPP/RNBQKBNR w - - 0 1",
    )
    .unwrap();
    let mut attackers = p.attackers(true, "d3").unwrap();
    attackers.sort();
    assert_eq!(attackers, vec!["c2", "e2"]);
    assert_eq!(p.attackers(false, "d3").unwrap(), Vec::<String>::new());
}

#[test]
fn captured_counts_pieces_missing_from_the_opening_set() {
    let p = Position::from_fen(Variant::Standard, "4k3/8/8/8/8/8/8/R3K3 w - - 0 1").unwrap();
    assert_eq!(p.captured(true), [8, 2, 2, 1, 1]);
    assert_eq!(p.captured(false), [8, 2, 2, 2, 1]);
}

#[test]
fn halfmove_clock_and_repetition_count_track_the_draw_rules() {
    let mut p = Position::from_fen(Variant::Standard, START).unwrap();
    assert_eq!(p.halfmove_clock(), 0);
    assert_eq!(p.repetition_count(), 0);
    for _ in 0..2 {
        for m in ["g1f3", "g8f6", "f3g1", "f6g8"] {
            p.apply(m).unwrap();
        }
    }
    assert_eq!(p.halfmove_clock(), 8);
    assert_eq!(p.repetition_count(), 2);
    assert_eq!(p.game_state(), GameState::DrawRepetition);
}

#[test]
fn san_covers_captures_promotion_castling_and_mate() {
    let mut p = Position::from_fen(Variant::Standard, START).unwrap();
    assert_eq!(p.to_san("e2e4").unwrap(), "e4");
    p.apply("e2e4").unwrap();
    assert_eq!(p.to_san("d7d5").unwrap(), "d5");
    p.apply("d7d5").unwrap();
    assert_eq!(p.to_san("e4d5").unwrap(), "exd5");

    let p = Position::from_fen(Variant::Standard, "4k3/P7/8/8/8/8/7p/4K3 w - - 0 1").unwrap();
    assert_eq!(p.to_san("a7a8q").unwrap(), "a8=Q+");

    let p = Position::from_fen(Variant::Standard, "4k3/8/8/8/8/8/8/R3K2R w KQ - 0 1").unwrap();
    assert_eq!(p.to_san("e1g1").unwrap(), "O-O");
    assert_eq!(p.to_san("e1c1").unwrap(), "O-O-O");

    // Fool's mate.
    let mut p = Position::from_fen(Variant::Standard, START).unwrap();
    for m in ["f2f3", "e7e5", "g2g4"] {
        p.apply(m).unwrap();
    }
    assert_eq!(p.to_san("d8h4").unwrap(), "Qh4#");

    assert!(p.to_san("a1a1").is_err());
}

#[test]
fn san_for_a_duck_turn_includes_the_duck_placement() {
    let p = Position::from_fen(Variant::Duck, START).unwrap();
    assert_eq!(p.to_san("e2e4@d5").unwrap(), "e4@d5");
    assert_eq!(p.to_san("e2e4@a5").unwrap(), "e4@a5");
    assert!(p.to_san("e2e4@a1").is_err());
}

#[test]
fn san_disambiguates_identical_pieces_that_share_a_destination() {
    let p = Position::from_fen(Variant::Standard, "4k3/8/8/8/8/8/8/1N1NK3 w - - 0 1").unwrap();
    assert_eq!(p.to_san("b1c3").unwrap(), "Nbc3");
    assert_eq!(p.to_san("d1c3").unwrap(), "Ndc3");
}

#[test]
fn legal_piece_moves_with_san_match_individual_queries() {
    for (variant, fen) in [
        (Variant::Standard, START),
        (Variant::Standard, "4k3/8/8/3pP3/8/8/8/4K3 w - d6 0 1"),
        (Variant::Standard, "4k3/P7/8/8/8/8/7p/4K3 w - - 0 1"),
        (Variant::Standard, "4k3/8/8/8/8/8/8/R3K2R w KQ - 0 1"),
        (Variant::Standard, "4k3/8/8/8/8/8/8/1N1NK3 w - - 0 1"),
        (
            Variant::Standard,
            "rnb1kbnr/pppp1ppp/8/4p3/6Pq/5P2/PPPPP2P/RNBQKBNR w KQkq - 1 3",
        ),
        (Variant::Chess960, "4k3/8/8/8/8/8/8/1R2K1R1 w KQ - 0 1"),
        (Variant::Duck, START),
        (Variant::Duck, "4k3/8/8/8/8/8/4R3/4K3 w - - 0 1"),
        (Variant::Duck, "8/8/8/8/8/8/4R3/4K3 b - - 0 1"),
        (Variant::KingOfTheHill, "7k/8/8/8/8/3K4/8/8 w - - 0 1"),
        (Variant::KingOfTheHill, "7k/8/8/8/3K4/8/8/8 b - - 1 1"),
    ] {
        let p = Position::from_fen(variant, fen).unwrap();
        let mut seen = std::collections::HashSet::new();
        let expected: Vec<_> = p
            .legal_moves()
            .iter()
            .filter_map(|m| {
                let piece = m.as_str().split('@').next().unwrap();
                seen.insert(piece.to_string())
                    .then(|| (piece.to_string(), p.to_san(piece).unwrap()))
            })
            .collect();
        let actual: Vec<_> = p
            .legal_piece_moves_with_san()
            .into_iter()
            .map(|(m, san)| (m.to_string(), san))
            .collect();
        assert_eq!(actual, expected, "{variant:?}: {fen}");
    }
}
