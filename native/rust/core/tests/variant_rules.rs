use chess_core::{
    search, GameState, Position, SearchControl, SearchLimit, SearchOptions, SearchRequest, Variant,
};

const ORTHODOX: [Variant; 3] = [Variant::Standard, Variant::Chess960, Variant::KingOfTheHill];

fn position(variant: Variant, fen: &str) -> Position {
    Position::from_fen(variant, fen).unwrap_or_else(|e| panic!("{variant:?}: {fen}: {e}"))
}

fn rejects(p: &mut Position, mv: &str) {
    let before = (
        p.fen(),
        p.history().to_vec(),
        p.halfmove_clock(),
        p.repetition_count(),
    );
    assert!(p.apply(mv).is_err(), "accepted {mv} in {}", before.0);
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

fn perft(p: &Position, depth: u8) -> usize {
    if depth == 0 {
        return 1;
    }
    p.legal_moves()
        .into_iter()
        .map(|m| {
            let mut next = p.clone();
            next.apply(m.as_str()).unwrap();
            perft(&next, depth - 1)
        })
        .sum()
}

#[test]
fn reference_perft_exercises_castling_en_passant_and_promotions() {
    for variant in [Variant::Standard, Variant::Chess960] {
        for (fen, counts) in [
            (
                "r3k2r/p1ppqpb1/bn2pnp1/3PN3/1p2P3/2N2Q1p/PPPBBPPP/R3K2R w KQkq - 0 1",
                [48, 2039, 97862],
            ),
            ("8/2p5/3p4/KP5r/1R3p1k/8/4P1P1/8 w - - 0 1", [14, 191, 2812]),
            (
                "r3k2r/Pppp1ppp/1b3nbN/nP6/BBP1P3/q4N2/Pp1P2PP/R2Q1RK1 w kq - 0 1",
                [6, 264, 9467],
            ),
        ] {
            let p = position(variant, fen);
            for (i, expected) in counts.into_iter().enumerate() {
                assert_eq!(
                    perft(&p, (i + 1) as u8),
                    expected,
                    "{variant:?}: {fen}, depth {}",
                    i + 1
                );
            }
        }
    }
}

#[test]
fn checks_pins_and_king_safety_restrict_orthodox_moves() {
    for variant in ORTHODOX {
        let mut p = position(variant, "4r2k/8/8/8/8/8/3B4/4K3 w - - 0 1");
        assert_eq!(p.game_state(), GameState::Check);
        rejects(&mut p, "d2c3");
        p.apply("d2e3").unwrap();
        let p = position(variant, "4r2k/8/8/8/1b6/8/3R4/4K3 w - - 0 1");
        assert_eq!(p.game_state(), GameState::Check);
        assert!(p.legal_moves().iter().all(|m| m.as_str().starts_with("e1")));
        let mut p = position(variant, "4r2k/8/8/8/8/8/4R3/4K3 w - - 0 1");
        rejects(&mut p, "e2d2");
        p.apply("e2e3").unwrap();
        for (fen, mv) in [
            ("8/8/8/8/8/4k3/8/4K3 w - - 0 1", "e1e2"),
            ("4r2k/8/8/8/8/8/8/4K3 w - - 0 1", "e1e2"),
            ("4r2k/8/8/8/8/8/4n3/4K3 w - - 0 1", "e1e2"),
        ] {
            rejects(&mut position(variant, fen), mv);
        }
    }
}

#[test]
fn en_passant_removes_the_pawn_expires_and_obeys_king_safety() {
    for variant in ORTHODOX {
        for (fen, mv, captured, destination) in [
            ("7k/8/8/3pP3/8/8/8/K7 w - d6 17 1", "e5d6", "d5", "d6"),
            ("7k/8/8/8/3Pp3/8/8/K7 b - d3 17 1", "e4d3", "d4", "d3"),
        ] {
            let mut p = position(variant, fen);
            p.apply(mv).unwrap();
            assert_eq!(p.piece_at(captured).unwrap(), None);
            assert!(p.piece_at(destination).unwrap().is_some());
            assert_eq!(p.halfmove_clock(), 0);
        }
        let mut p = position(variant, "7k/8/8/3pP3/8/8/8/K7 w - d6 0 1");
        p.apply("a1a2").unwrap();
        p.apply("h8h7").unwrap();
        rejects(&mut p, "e5d6");
        for fen in [
            "7k/8/8/r4pPK/8/8/8/8 w - f6 0 1",
            "6bk/8/8/3pP3/8/8/K7/8 w - d6 0 1",
        ] {
            let mv = if fen.contains("r4pPK") {
                "g5f6"
            } else {
                "e5d6"
            };
            rejects(&mut position(variant, fen), mv);
        }
        let mut p = position(variant, "7k/8/8/3pP3/4K3/8/8/8 w - d6 0 1");
        assert_eq!(p.game_state(), GameState::Check);
        p.apply("e5d6").unwrap();
        assert_ne!(p.game_state(), GameState::Check);
    }
}

#[test]
fn all_promotions_are_generated_and_applied_for_both_colours() {
    for variant in [
        Variant::Standard,
        Variant::Chess960,
        Variant::KingOfTheHill,
        Variant::Duck,
    ] {
        for (fen, from, destinations, white) in [
            ("1r5k/P7/8/8/8/8/8/7K w - - 99 1", "a7", ["a8", "b8"], true),
            ("7k/8/8/8/8/8/p7/1R5K b - - 99 1", "a2", ["a1", "b1"], false),
        ] {
            let p = position(variant, fen);
            let suffix = if variant == Variant::Duck { "@c3" } else { "" };
            for to in destinations {
                for promotion in ['q', 'r', 'b', 'n'] {
                    let mv = format!("{from}{to}{promotion}{suffix}");
                    assert!(p.legal_moves().iter().any(|m| m.as_str() == mv));
                    let mut next = p.clone();
                    next.apply(&mv).unwrap();
                    assert_eq!(
                        next.piece_at(to).unwrap(),
                        Some(if white {
                            promotion.to_ascii_uppercase()
                        } else {
                            promotion
                        })
                    );
                    assert_eq!(next.halfmove_clock(), 0);
                }
                for promotion in ["", "k", "p"] {
                    rejects(&mut p.clone(), &format!("{from}{to}{promotion}{suffix}"));
                }
            }
        }
    }
}

#[test]
fn orthodox_castling_obeys_attacks_and_blockers() {
    for variant in ORTHODOX {
        for (fen, forbidden) in [
            ("4r3/1k6/8/8/8/8/8/R3K2R w KQ - 0 1", [true, true]),
            ("5r2/1k6/8/8/8/8/8/R3K2R w KQ - 0 1", [true, false]),
            ("6r1/1k6/8/8/8/8/8/R3K2R w KQ - 0 1", [true, false]),
            ("3r4/1k6/8/8/8/8/8/R3K2R w KQ - 0 1", [false, true]),
            ("2r5/1k6/8/8/8/8/8/R3K2R w KQ - 0 1", [false, true]),
            ("8/1k6/8/8/8/8/8/R3KB1R w KQ - 0 1", [true, false]),
            ("8/1k6/8/8/8/8/8/RN2K2R w KQ - 0 1", [false, true]),
        ] {
            let p = position(variant, fen);
            for (i, blocked) in forbidden.into_iter().enumerate() {
                let mv = match (variant == Variant::Chess960, i) {
                    (true, 0) => "e1h1",
                    (true, _) => "e1a1",
                    (false, 0) => "e1g1",
                    _ => "e1c1",
                };
                assert_eq!(
                    p.legal_moves().iter().any(|m| m.as_str() == mv),
                    !blocked,
                    "{variant:?}: {fen}: {mv}"
                );
            }
        }
        for (fen, mv, king, rook) in [
            (
                "4k2r/8/8/8/8/8/8/K7 b k - 0 1",
                if variant == Variant::Chess960 {
                    "e8h8"
                } else {
                    "e8g8"
                },
                "g8",
                "f8",
            ),
            (
                "r3k3/8/8/8/8/8/8/7K b q - 0 1",
                if variant == Variant::Chess960 {
                    "e8a8"
                } else {
                    "e8c8"
                },
                "c8",
                "d8",
            ),
            (
                "7k/8/8/8/8/8/8/R3K3 w Q - 0 1",
                if variant == Variant::Chess960 {
                    "e1a1"
                } else {
                    "e1c1"
                },
                "c1",
                "d1",
            ),
        ] {
            let mut p = position(variant, fen);
            p.apply(mv).unwrap();
            assert!(matches!(p.piece_at(king).unwrap(), Some('K' | 'k')));
            assert!(matches!(p.piece_at(rook).unwrap(), Some('R' | 'r')));
        }
    }
}

#[test]
fn draw_boundaries_and_irreversible_moves_are_variant_independent() {
    for variant in [
        Variant::Standard,
        Variant::Chess960,
        Variant::KingOfTheHill,
        Variant::Duck,
    ] {
        let mut p = position(variant, "7k/8/8/8/8/8/P7/KR6 w - - 99 1");
        assert_eq!(p.game_state(), GameState::Play);
        p.apply("b1b2").unwrap();
        assert_eq!(p.game_state(), GameState::DrawFiftyMove);
        for (fen, mv) in [
            ("7k/8/8/8/8/8/P7/KR6 w - - 99 1", "a2a3"),
            ("7k/8/8/8/8/1n6/8/KR6 w - - 99 1", "b1b3"),
        ] {
            let mut p = position(variant, fen);
            p.apply(&format!(
                "{mv}{}",
                if variant == Variant::Duck { "@c3" } else { "" }
            ))
            .unwrap();
            assert_eq!(p.halfmove_clock(), 0);
            assert_ne!(p.game_state(), GameState::DrawFiftyMove);
        }
    }
}

#[test]
fn orthodox_terminal_states_have_the_expected_meaning() {
    for variant in ORTHODOX {
        for (fen, state) in [
            ("7k/6Q1/5K2/8/8/8/8/8 b - - 0 1", GameState::Mate),
            ("7k/5K2/6Q1/8/8/8/8/8 b - - 0 1", GameState::Stalemate),
            ("8/8/8/8/8/5k2/6q1/7K w - - 0 1", GameState::Mate),
            ("8/8/8/8/8/6q1/5k2/7K w - - 0 1", GameState::Stalemate),
        ] {
            assert_eq!(position(variant, fen).game_state(), state);
        }
        let mut p = position(variant, "6k1/5ppp/8/8/8/8/8/R5K1 w - - 99 1");
        p.apply("a1a8").unwrap();
        assert_eq!(p.game_state(), GameState::Mate);
    }
}

#[test]
fn hill_capture_resets_draw_clock_and_only_kings_win() {
    for (fen, moves) in [
        ("7k/8/8/8/3nn3/3K4/8/8 w - - 99 1", ["d3d4", "d3e4"]),
        ("8/8/3K4/3nn3/8/8/8/7k w - - 99 1", ["d6d5", "d6e5"]),
        ("7K/8/8/8/3NN3/3k4/8/8 b - - 99 1", ["d3d4", "d3e4"]),
        ("8/8/3k4/3NN3/8/8/8/7K b - - 99 1", ["d6d5", "d6e5"]),
    ] {
        for mv in moves {
            let mut p = position(Variant::KingOfTheHill, fen);
            p.apply(mv).unwrap();
            assert_eq!(p.halfmove_clock(), 0);
            assert_eq!(p.game_state(), GameState::HillWin);
        }
    }
    for (fen, mv) in [
        ("7k/8/8/8/8/3R4/8/K7 w - - 0 1", "d3d4"),
        ("7k/8/8/8/8/2K5/8/8 w - - 0 1", "c3c4"),
    ] {
        let mut p = position(Variant::KingOfTheHill, fen);
        p.apply(mv).unwrap();
        assert_eq!(p.game_state(), GameState::Play);
    }
    for fen in [
        "7k/8/8/8/8/8/B7/K7 w - - 0 1",
        "7k/8/8/8/8/8/N7/K7 w - - 0 1",
        "7k/6b1/8/8/8/8/1B6/K7 w - - 0 1",
    ] {
        assert_eq!(
            position(Variant::KingOfTheHill, fen).game_state(),
            GameState::Play
        );
    }
}

#[test]
fn hill_terminal_roots_and_depth_one_wins_stop_search() {
    for quiescence in [false, true] {
        for (fen, mv) in [
            ("7k/8/8/8/8/3K4/8/8 w - - 0 1", "d3d4"),
            ("7K/8/8/8/8/3k4/8/8 b - - 0 1", "d3d4"),
        ] {
            let mut p = position(Variant::KingOfTheHill, fen);
            let request = SearchRequest::new(
                1,
                Variant::KingOfTheHill,
                fen,
                vec![],
                SearchLimit::Depth(1),
                SearchOptions { quiescence },
            )
            .unwrap();
            let result = search(&request, &SearchControl::default()).unwrap();
            p.apply(result.best_move.unwrap().as_str()).unwrap();
            assert_eq!(p.game_state(), GameState::HillWin);
            let mut p = position(Variant::KingOfTheHill, fen);
            p.apply(mv).unwrap();
            let request = SearchRequest::new(
                2,
                Variant::KingOfTheHill,
                &p.fen(),
                vec![],
                SearchLimit::Depth(2),
                SearchOptions { quiescence },
            )
            .unwrap();
            let result = search(&request, &SearchControl::default()).unwrap();
            assert_eq!(result.score, -100000);
            assert!(result.best_move.is_none());
            assert!(result.pv.is_empty());
        }
    }
}

#[test]
fn two_occurrences_are_playable_and_castling_rights_change_repetition_identity() {
    for variant in ORTHODOX {
        let mut p = position(variant, "6nk/8/8/8/8/8/8/KN6 w - - 0 1");
        for cycle in 1..=2 {
            for mv in ["b1c3", "g8f6", "c3b1", "f6g8"] {
                p.apply(mv).unwrap();
            }
            assert_eq!(p.repetition_count(), cycle);
            assert_eq!(
                p.game_state(),
                if cycle == 1 {
                    GameState::Play
                } else {
                    GameState::DrawRepetition
                }
            );
        }
        let mut p = position(variant, "8/4k3/8/8/8/8/8/R3K2R w KQ - 0 1");
        for _ in 0..2 {
            for mv in ["e1f1", "e7e6", "f1e1", "e6e7"] {
                p.apply(mv).unwrap();
            }
        }
        assert_eq!(p.game_state(), GameState::Play);
        assert_eq!(p.repetition_count(), 1);
        p.apply("e1f1").unwrap();
        assert_eq!(p.game_state(), GameState::DrawRepetition);
    }
}

#[test]
fn defended_hill_captures_and_enemy_king_adjacency_remain_illegal() {
    for (fen, mv) in [
        ("3r3k/8/8/8/3n4/3K4/8/8 w - - 0 1", "d3d4"),
        ("8/8/8/4k3/8/3K4/8/8 w - - 0 1", "d3d4"),
    ] {
        rejects(&mut position(Variant::KingOfTheHill, fen), mv);
    }
    let p = position(Variant::KingOfTheHill, "7k/8/8/8/8/3K4/8/8 w - - 0 1");
    assert_eq!(p.to_san("d3d4").unwrap(), "Kd4");
}
