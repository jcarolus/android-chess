use chess_core::{
    search, GameState, Position, SearchControl, SearchLimit, SearchOptions, SearchRequest, Variant,
};

const WIN: i32 = 100_000;

fn solve(fen: &str, depth: u8, published_move: &str, king: &str, plies: usize) {
    // A search depth counts full turns and detects immediate king captures at the horizon.
    let request = SearchRequest::new(
        1,
        Variant::Duck,
        fen,
        vec![],
        SearchLimit::Depth(depth),
        SearchOptions { quiescence: false },
    )
    .unwrap();
    let start = request.position();
    assert_forced_capture(start, published_move, king, plies);

    let result = search(&request, &SearchControl::default()).unwrap();
    assert_eq!(result.completed_depth, depth);
    assert_eq!(result.score, WIN, "{fen}: {:?}", result.pv);
    let best = result.best_move.unwrap();
    assert_eq!(result.pv.first(), Some(&best));
    // A different move is equally valid if every defense still loses its king.
    assert_forced_capture(start, best.as_str(), king, plies);
    let mut end = start.clone();
    assert_eq!(result.pv.len(), plies, "{fen}: {:?}", result.pv);
    for turn in result.pv {
        end.apply(turn.as_str()).unwrap();
    }
    assert_eq!(end.game_state(), GameState::Mate);
}

fn assert_forced_capture(start: &Position, attack: &str, king: &str, plies: usize) {
    let mut defense = start.clone();
    defense.apply(attack).unwrap();
    if plies == 1 {
        assert_eq!(defense.game_state(), GameState::Mate, "{attack}");
        return;
    }

    assert_eq!(defense.game_state(), GameState::Play, "{attack}");
    for reply in defense.legal_moves() {
        let mut next = defense.clone();
        next.apply(reply.as_str()).unwrap();
        assert_eq!(next.game_state(), GameState::Play, "{attack} {reply}");
        let king_square = match reply.as_str().split_once('@').unwrap().0 {
            piece_move if &piece_move[..2] == king => &piece_move[2..4],
            _ => king,
        };
        let attackers = next.attackers(start.white_to_move(), king_square).unwrap();
        assert!(!attackers.is_empty(), "{attack} fails against {reply}");
        let attacker = &attackers[0];
        let promotion = if matches!(next.piece_at(attacker).unwrap(), Some('p' | 'P'))
            && matches!(king_square.as_bytes()[1], b'1' | b'8')
        {
            "q"
        } else {
            ""
        };
        next.apply(&format!("{attacker}{king_square}{promotion}"))
            .unwrap();
        assert_eq!(next.game_state(), GameState::Mate, "{attack} {reply}");
    }
}

// https://duckchess.com/ (A warm-up puzzle)
const PAULDEN: &str = "8/5Q1p/7k/5$1p/4Bn2/6q1/6P1/7K";

#[test]
fn paulden_queen_move_forces_the_duck_off_the_only_blocker() {
    solve(&format!("{PAULDEN} w - - 0 1"), 2, "f7f6@g6", "h6", 3);
}

#[test]
fn paulden_black_queen_capture_wins_with_the_duck_as_a_shield() {
    solve(&format!("{PAULDEN} b - - 0 1"), 2, "g3g2@f3", "h1", 3);
}

#[test]
fn paulden_king_can_take_the_queen_and_block_the_recapture() {
    let fen = format!("{PAULDEN} w - - 0 1");
    let mut p = Position::from_fen(Variant::Duck, &fen).unwrap();
    p.apply("f7h7@g5").unwrap();
    p.apply("h6h7@f5").unwrap();
    assert_eq!(p.piece_at("h7").unwrap(), Some('k'));
    assert!(p.attackers(true, "h7").unwrap().is_empty());
}

// https://www.chess.com/news/view/2026-duck-chess-championship-khachatryan-wins#khachatryan
#[test]
fn khachatryan_blocks_the_white_king_escape_in_grand_final_game_one() {
    solve(
        "1r1r4/1qp2kpp/p2bp3/n2$1p2/2p2P2/P1N1BBPP/1PR2P2/2KR4 b - - 0 28",
        2,
        "a5b3@b1",
        "c1",
        3,
    );
}

#[test]
fn khachatryan_queen_attack_in_grand_final_game_two() {
    solve(
        "3rr1k1/1pp4p/p2bN1p1/5p2/1PPQ4/q3P1PP/5P2/3RK$1R w K - 0 22",
        2,
        "d4g7@h8",
        "g8",
        3,
    );
}

#[test]
fn minh_to_finds_the_unblockable_knight_capture_against_rosen() {
    solve(
        "4N3/8/2$1nk1p/2P5/1rp1B3/6P1/5P1P/4R1K1 w - - 1 40",
        1,
        "e8f6",
        "f6",
        1,
    );
}

// https://www.chess.com/news/view/2024-chesscom-duck-chess-championship-knockout-minh-to-wins
#[test]
fn rosen_knight_capture_and_duck_smothers_the_king() {
    solve(
        "8/4rr1k/pR5b/P4p2/3p2np/q4$P1/2QP1PB1/B3N1RK b - - 1 48",
        2,
        "g4f2@h2",
        "h1",
        3,
    );
}

#[test]
fn nalbandyan_can_force_the_duck_off_the_bishop_diagonal() {
    solve(
        "1b3kr1/1qrP4/p1n1Q$2/1pP2pBp/8/P4RP1/6KP/3R4 w - - 1 34",
        2,
        "g5h6@g7",
        "f8",
        3,
    );
}

#[test]
fn minh_to_takes_with_the_rook_to_preserve_two_capture_lines() {
    solve(
        "5rk1/R2R2p1/2p2p1p/7P/2P2P2/3P$K2/3qPB2/6Q1 w - - 2 44",
        2,
        "d7g7@h8",
        "g8",
        3,
    );
}
