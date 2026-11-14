use chess_core::{GameState, Position, Variant};

#[test]
fn san_disambiguates_by_rank_and_both_coordinates_and_ignores_pins() {
    for (fen, mv, san) in [
        ("7k/8/8/8/8/R7/8/R3K3 w - - 0 1", "a1a2", "R1a2"),
        ("8/7k/8/8/8/Q7/8/Q1Q1K3 w - - 0 1", "a1c3", "Qa1c3"),
        ("3r3k/8/8/8/8/8/3N4/1N1K4 w - - 0 1", "b1c3", "Nc3"),
    ] {
        let p = Position::from_fen(Variant::Standard, fen).unwrap();
        assert_eq!(p.to_san(mv).unwrap(), san);
    }
}

#[test]
fn duck_san_omits_check_and_mate_suffixes() {
    for (fen, mv, san) in [
        ("4k3/8/8/8/8/8/4R3/K7 w - - 0 1", "e2e7@a3", "Re7@a3"),
        ("4k3/4R3/8/8/8/8/8/K7 w - - 0 1", "e7e8", "Rxe8"),
        ("7k/P7/8/8/8/8/8/7K w - - 0 1", "a7a8q@c3", "a8=Q@c3"),
        ("7k/8/8/3pP3/8/8/8/K7 w - d6 0 1", "e5d6@d5", "exd6@d5"),
    ] {
        let p = Position::from_fen(Variant::Duck, fen).unwrap();
        assert_eq!(p.to_san(mv).unwrap(), san);
    }
}

#[test]
fn invalid_fen_is_rejected_according_to_variant_king_rules() {
    for variant in [
        Variant::Standard,
        Variant::Chess960,
        Variant::KingOfTheHill,
        Variant::Duck,
    ] {
        for fen in [
            "7k/8/8/8/8/8/8/K3K3 w - - 0 1",
            "7k/8/8/8/8/8/8/K6P w - - 0 1",
            "p6k/8/8/8/8/8/8/K7 w - - 0 1",
            "7k/8/8/8/8/8/8/K7 w - - -1 1",
            "7k/8/8/8/8/8/8/K7 w - - 0 0",
            "7k/8/8/8/8/8/8/K7 w - - 1000001 1",
            "7k/8/8/8/8/8/8/K7 w - - 0 1000001",
        ] {
            assert!(
                Position::from_fen(variant, fen).is_err(),
                "{variant:?}: {fen}"
            );
        }
        let missing = "8/8/8/8/8/8/8/K7 b - - 0 1";
        if variant == Variant::Duck {
            assert_eq!(
                Position::from_fen(variant, missing).unwrap().game_state(),
                GameState::Mate
            );
        } else {
            assert!(Position::from_fen(variant, missing).is_err());
        }
        let checked = "7k/8/8/8/8/8/8/K6R w - - 0 1";
        assert_eq!(
            Position::from_fen(variant, checked).is_ok(),
            variant == Variant::Duck
        );
    }
    for variant in [Variant::Standard, Variant::KingOfTheHill, Variant::Duck] {
        for fen in [
            "7k/8/8/8/8/8/8/4K3 w K - 0 1",
            "7k/8/8/8/8/8/8/3K3R w K - 0 1",
        ] {
            assert!(Position::from_fen(variant, fen).is_err());
        }
    }
}
