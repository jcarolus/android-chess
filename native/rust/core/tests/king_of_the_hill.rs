use chess_core::{
    search, GameState, Position, SearchControl, SearchLimit, SearchOptions, SearchRequest, Variant,
};

fn position(fen: &str) -> Position {
    Position::from_fen(Variant::KingOfTheHill, fen).unwrap()
}

#[test]
fn either_king_wins_on_each_hill_square_and_play_stops() {
    for (fen, moves) in [
        ("7k/8/8/8/8/3K4/8/8 w - - 0 1", ["d3d4", "d3e4"]),
        ("8/8/3K4/8/8/8/8/7k w - - 0 1", ["d6d5", "d6e5"]),
        ("7K/8/8/8/8/3k4/8/8 b - - 0 1", ["d3d4", "d3e4"]),
        ("8/8/3k4/8/8/8/8/7K b - - 0 1", ["d6d5", "d6e5"]),
    ] {
        for mv in moves {
            let mut p = position(fen);
            assert_eq!(p.game_state(), GameState::Play);
            p.apply(mv).unwrap();
            assert_eq!(p.game_state(), GameState::HillWin);
            assert!(p.legal_moves().is_empty());
            assert!(p.apply("h8h7").is_err());
        }
    }
}

#[test]
fn hill_does_not_allow_moving_into_check() {
    let mut p = position("3r3k/8/8/8/8/4K3/8/8 w - - 0 1");
    assert!(p.apply("e3d4").is_err());
    p.apply("e3e4").unwrap();
    assert_eq!(p.game_state(), GameState::HillWin);
}

#[test]
fn fifty_move_draw_takes_precedence_over_hill() {
    let mut p = position("7k/8/8/8/8/3K4/8/8 w - - 99 1");
    p.apply("d3d4").unwrap();
    assert_eq!(p.game_state(), GameState::DrawFiftyMove);
    assert!(p.legal_moves().is_empty());
}

#[test]
fn bare_kings_can_play_but_repetition_still_draws() {
    let mut p = position("7k/8/8/8/8/8/8/K7 w - - 0 1");
    for mv in [
        "a1a2", "h8h7", "a2a1", "h7h8", "a1a2", "h8h7", "a2a1", "h7h8",
    ] {
        p.apply(mv).unwrap();
    }
    assert_eq!(p.game_state(), GameState::DrawRepetition);
}

#[test]
fn checkmate_and_stalemate_still_end_games() {
    assert_eq!(
        position("7k/6Q1/5K2/8/8/8/8/8 b - - 0 1").game_state(),
        GameState::Mate
    );
    assert_eq!(
        position("7k/5K2/6Q1/8/8/8/8/8 b - - 0 1").game_state(),
        GameState::Stalemate
    );
}

#[test]
fn king_progress_is_rewarded_without_lone_king_mating_heuristics() {
    let far = position("7k/8/8/8/8/8/8/K7 w - - 0 1");
    let near = position("7k/8/8/8/8/3K4/8/8 w - - 0 1");
    assert_eq!(far.evaluate(), 0);
    assert!(near.evaluate() > far.evaluate());
}

#[test]
fn search_chooses_hill_win_and_observes_draw_precedence() {
    for quiescence in [false, true] {
        for halfmove in [0, 99] {
            let fen = format!("7k/8/8/8/8/3K4/8/8 w - - {halfmove} 1");
            let request = SearchRequest::new(
                1,
                Variant::KingOfTheHill,
                &fen,
                vec![],
                SearchLimit::Depth(2),
                SearchOptions { quiescence },
            )
            .unwrap();
            let result = search(&request, &SearchControl::default()).unwrap();
            let mut p = position(&fen);
            p.apply(result.best_move.unwrap().as_str()).unwrap();
            if halfmove == 0 {
                assert_eq!(p.game_state(), GameState::HillWin);
                assert_eq!(result.score, 100000);
            } else {
                assert_eq!(p.game_state(), GameState::DrawFiftyMove);
                assert_eq!(result.score, 0);
            }
        }
    }
}

#[test]
fn search_prevents_an_opponents_immediate_hill_win() {
    let fen = "R7/8/3k4/8/8/8/8/K7 w - - 0 1";
    for quiescence in [false, true] {
        let request = SearchRequest::new(
            2,
            Variant::KingOfTheHill,
            fen,
            vec![],
            SearchLimit::Depth(2),
            SearchOptions { quiescence },
        )
        .unwrap();
        let result = search(&request, &SearchControl::default()).unwrap();
        let mut p = position(fen);
        p.apply(result.best_move.unwrap().as_str()).unwrap();
        for reply in p.legal_moves() {
            let mut next = p.clone();
            next.apply(reply.as_str()).unwrap();
            assert_ne!(
                next.game_state(),
                GameState::HillWin,
                "Allowed hill win with {reply}"
            );
        }
    }
}
