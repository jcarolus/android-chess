use chess_core::{GameState, Position, Variant};

fn position(fen: &str) -> Position {
    Position::from_fen(Variant::Chess960, fen).unwrap()
}
fn mirror(fen: &str) -> String {
    let mut fields: Vec<String> = fen.split_whitespace().map(str::to_string).collect();
    fields[0] = fields[0]
        .split('/')
        .rev()
        .map(|rank| {
            rank.chars()
                .map(|c| {
                    if c.is_ascii_uppercase() {
                        c.to_ascii_lowercase()
                    } else {
                        c.to_ascii_uppercase()
                    }
                })
                .collect::<String>()
        })
        .collect::<Vec<_>>()
        .join("/");
    fields[1] = if fields[1] == "w" { "b" } else { "w" }.into();
    fields[2] = fields[2]
        .chars()
        .map(|c| {
            if c.is_ascii_uppercase() {
                c.to_ascii_lowercase()
            } else {
                c.to_ascii_uppercase()
            }
        })
        .collect();
    fields.join(" ")
}

#[test]
fn overlapping_castles_work_for_both_wings_and_colours() {
    for (rank, mv, expected, san) in [
        ("3RK2R", "e1d1", "2KR3R", "O-O-O"),
        ("R3KR2", "e1f1", "R4RK1", "O-O"),
        ("R1K4R", "c1a1", "2KR3R", "O-O-O"),
        ("R5KR", "g1h1", "R4RK1", "O-O"),
        ("5KR1", "f1g1", "5RK1", "O-O"),
        ("2RK4", "d1c1", "2KR4", "O-O-O"),
    ] {
        let fen = format!("8/4k3/8/8/8/8/8/{rank} w KQ - 0 1");
        for black in [false, true] {
            let fen = if black { mirror(&fen) } else { fen.clone() };
            let mv = if black {
                mv.replace('1', "8")
            } else {
                mv.into()
            };
            let mut p = position(&fen);
            assert_eq!(p.to_san(&mv).unwrap(), san);
            p.apply(&mv).unwrap();
            let output = p.fen();
            let rank = output
                .split_whitespace()
                .next()
                .unwrap()
                .split('/')
                .nth(if black { 0 } else { 7 })
                .unwrap();
            assert_eq!(
                rank,
                if black {
                    expected.to_ascii_lowercase()
                } else {
                    expected.into()
                }
            );
            assert_eq!(p.castling_rights(), [false; 4]);
        }
    }
}

#[test]
fn castling_rejects_rook_path_blockers_and_revealed_attacks() {
    for (fen, mv) in [
        ("8/4k3/8/8/8/8/8/rRK5 w Q - 0 1", "c1b1"),
        ("8/4k3/8/8/8/8/8/R2NK3 w Q - 0 1", "e1a1"),
        ("8/4k3/8/8/8/8/8/R3KN1R w K - 0 1", "e1h1"),
        ("2r5/4k3/8/8/8/8/8/R1K5 w Q - 0 1", "c1a1"),
    ] {
        for black in [false, true] {
            let mut p = position(&if black { mirror(fen) } else { fen.into() });
            let mv = if black {
                mv.replace('1', "8")
            } else {
                mv.into()
            };
            assert!(!p.legal_moves().iter().any(|m| m.as_str() == mv));
            let before = p.fen();
            assert!(p.apply(&mv).is_err());
            assert_eq!(p.fen(), before);
        }
    }
}

#[test]
fn king_and_tracked_rook_rights_never_return() {
    for variant in [Variant::Standard, Variant::Chess960, Variant::KingOfTheHill] {
        for (moves, expected) in [
            (
                ["a1a2", "e7e6", "a2a1", "e6e7"],
                [true, false, false, false],
            ),
            (
                ["h1h2", "e7e6", "h2h1", "e6e7"],
                [false, true, false, false],
            ),
            (
                ["e1f1", "e7e6", "f1e1", "e6e7"],
                [false, false, false, false],
            ),
        ] {
            let mut p = Position::from_fen(variant, "8/4k3/8/8/8/8/8/R3K2R w KQ - 0 1").unwrap();
            for mv in moves {
                p.apply(mv).unwrap();
            }
            assert_eq!(p.castling_rights(), expected, "{variant:?}: {moves:?}");
            assert_ne!(p.repetition_count(), 2);
        }
    }
}

#[test]
fn only_original_back_rank_rooks_control_rights() {
    for black in [false, true] {
        for (fen, mv, expected) in [
            (
                "8/4k3/8/8/R7/8/8/R3K2R w KQ - 0 1",
                "a4a5",
                [true, true, false, false],
            ),
            (
                "8/4k3/8/8/r7/8/8/R3K2R w KQ - 0 1",
                "a1a4",
                [true, false, false, false],
            ),
            (
                "8/4k3/8/8/8/8/r7/R3K2R b KQ - 0 1",
                "a2a1",
                [true, false, false, false],
            ),
        ] {
            let mut p = position(&if black { mirror(fen) } else { fen.into() });
            let mv = if black {
                mv.chars()
                    .map(|c| {
                        if ('1'..='8').contains(&c) {
                            char::from(b'9' - (c as u8 - b'0'))
                        } else {
                            c
                        }
                    })
                    .collect::<String>()
            } else {
                mv.into()
            };
            p.apply(&mv).unwrap();
            let expected = if black {
                [expected[2], expected[3], expected[0], expected[1]]
            } else {
                expected
            };
            assert_eq!(p.castling_rights(), expected);
        }
    }
}

#[test]
fn outermost_rook_is_tracked_across_fen_round_trips() {
    let mut p = position("8/4k3/8/8/8/8/8/RR2K1RR w KQ - 0 1");
    p.apply("b1b2").unwrap();
    p.apply("e7f7").unwrap();
    p.apply("g1g2").unwrap();
    p.apply("f7e7").unwrap();
    let restored = position(&p.fen());
    for mv in ["e1a1", "e1h1"] {
        assert!(restored.legal_moves().iter().any(|m| m.as_str() == mv));
        assert_eq!(
            restored.to_san(mv).unwrap(),
            if mv == "e1a1" { "O-O-O" } else { "O-O" }
        );
    }
}

#[test]
fn all_960_starting_arrangements_parse_with_four_castling_rights() {
    let mut count = 0;
    for dark in [0, 2, 4, 6] {
        for light in [1, 3, 5, 7] {
            for queen in 0..8 {
                if [dark, light].contains(&queen) {
                    continue;
                }
                for knight1 in 0..8 {
                    if [dark, light, queen].contains(&knight1) {
                        continue;
                    }
                    for knight2 in knight1 + 1..8 {
                        if [dark, light, queen].contains(&knight2) {
                            continue;
                        }
                        let mut rank = [' '; 8];
                        rank[dark] = 'B';
                        rank[light] = 'B';
                        rank[queen] = 'Q';
                        rank[knight1] = 'N';
                        rank[knight2] = 'N';
                        let empty: Vec<_> = (0..8).filter(|&i| rank[i] == ' ').collect();
                        for (i, piece) in empty.into_iter().zip(['R', 'K', 'R']) {
                            rank[i] = piece;
                        }
                        let rank: String = rank.into_iter().collect();
                        let fen = format!(
                            "{}/pppppppp/8/8/8/8/PPPPPPPP/{rank} w KQkq - 0 1",
                            rank.to_ascii_lowercase()
                        );
                        let p = position(&fen);
                        assert_eq!(p.fen(), fen);
                        assert_eq!(p.castling_rights(), [true; 4]);
                        assert_eq!(p.game_state(), GameState::Play);
                        count += 1;
                    }
                }
            }
        }
    }
    assert_eq!(count, 960);
}

#[test]
fn replacement_rook_does_not_restore_a_lost_right() {
    let mut p = position("8/4k3/8/8/8/8/7R/4K2R w K - 0 1");
    for mv in ["h1g1", "e7e6", "g1g2", "e6e7", "h2h1", "e7e6"] {
        p.apply(mv).unwrap();
    }
    assert_eq!(p.piece_at("h1").unwrap(), Some('R'));
    assert_eq!(p.castling_rights(), [false; 4]);
    assert!(!p.legal_moves().iter().any(|m| m.as_str() == "e1h1"));
    assert_eq!(position(&p.fen()).castling_rights(), [false; 4]);
}

#[test]
fn search_replays_chess960_castling_history_and_returns_a_legal_pv() {
    use chess_core::{
        search, CoordinateMove, SearchControl, SearchLimit, SearchOptions, SearchRequest,
    };
    for black in [false, true] {
        let fen = "8/4k3/8/8/8/8/8/1R2K1R1 w KQ - 0 1";
        let fen = if black { mirror(fen) } else { fen.into() };
        let mv = if black { "e8b8" } else { "e1b1" };
        let mut expected = position(&fen);
        expected.apply(mv).unwrap();
        let request = SearchRequest::new(
            1,
            Variant::Chess960,
            &fen,
            vec![CoordinateMove::parse(mv).unwrap()],
            SearchLimit::Depth(2),
            SearchOptions { quiescence: true },
        )
        .unwrap();
        assert_eq!(request.position().fen(), expected.fen());
        let result = search(&request, &SearchControl::default()).unwrap();
        assert!(result.best_move.is_some());
        for mv in result.pv {
            expected.apply(mv.as_str()).unwrap();
        }
    }
}

#[test]
fn promoted_rook_cannot_replace_a_captured_castling_rook() {
    let mut p = position("7n/k5P1/8/8/8/8/6b1/4K2R b K - 0 1");
    for mv in ["g2h1", "g7h8r", "a7a6", "h8h1", "a6a7"] {
        p.apply(mv).unwrap();
    }
    assert_eq!(p.piece_at("h1").unwrap(), Some('R'));
    assert_eq!(p.castling_rights(), [false; 4]);
    assert!(!p.legal_moves().iter().any(|m| m.as_str() == "e1h1"));
}
