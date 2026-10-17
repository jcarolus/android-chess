use chess_core::*;
use std::process::Command;
const START: &str = "rnbqkbnr/pppppppp/8/8/8/8/PPPPPPPP/RNBQKBNR w KQkq - 0 1";
fn compare(variant: Variant, fen: &str, history: &[&str], depth: u8, quiescence: bool) {
    let reference = std::env::var("CHESS_REFERENCE")
        .expect("Set CHESS_REFERENCE to the built chess_parity_reference executable");
    let output = Command::new(reference)
        .args([
            fen,
            &depth.to_string(),
            if quiescence { "1" } else { "0" },
            if variant == Variant::Chess960 { "1" } else { "0" },
        ])
        .args(history)
        .output()
        .unwrap();
    assert!(
        output.status.success(),
        "C++ reference rejected {fen} {history:?}"
    );
    let text = String::from_utf8(output.stdout).unwrap();
    let request = SearchRequest::new(
        1,
        variant,
        fen,
        history
            .iter()
            .map(|m| CoordinateMove::parse(m).unwrap())
            .collect(),
        SearchLimit::Depth(depth),
        SearchOptions { quiescence },
    )
    .unwrap();
    let p = request.position();
    let line = text.lines().find(|l| l.starts_with("POSITION\t")).unwrap();
    let parts: Vec<_> = line.split('\t').collect();
    assert_eq!(p.fen(), parts[1], "{fen} {history:?}");
    assert_eq!(p.evaluate().to_string(), parts[2], "{fen} {history:?}");
    let state = match p.game_state() {
        GameState::Play => 1,
        GameState::Check => 2,
        GameState::DrawMaterial => 4,
        GameState::DrawFiftyMove => 5,
        GameState::Mate => 6,
        GameState::Stalemate => 7,
        GameState::DrawRepetition => 8,
    };
    assert_eq!(state.to_string(), parts[3], "state {fen} {history:?}");
    if state == 1 || state == 2 {
        let reference_moves: Vec<_> = text.lines().filter(|l| l.starts_with("MOVE\t")).collect();
        let legal = p.legal_moves();
        assert_eq!(legal.len(), reference_moves.len(), "{fen} {history:?}");
        for (m, line) in legal.iter().zip(reference_moves) {
            let fields: Vec<_> = line.split('\t').collect();
            assert_eq!(m.as_str(), fields[1], "move order {fen} {history:?}");
            let mut child = p.clone();
            child.apply(m.as_str()).unwrap();
            assert_eq!(child.fen(), fields[2]);
            assert_eq!(
                child.evaluate().to_string(),
                fields[3],
                "child {fen} {history:?} {m}"
            );
        }
    }
    let result = search(&request, &SearchControl::default()).unwrap();
    let fields: Vec<_> = text
        .lines()
        .find(|l| l.starts_with("RESULT\t"))
        .unwrap()
        .split('\t')
        .collect();
    assert_eq!(
        result
            .best_move
            .as_ref()
            .map(CoordinateMove::as_str)
            .unwrap_or("-"),
        fields[1],
        "best {fen} {history:?} depth={depth} q={quiescence}"
    );
    let native: i32 = fields[2].parse().unwrap();
    assert_eq!(
        result.score, native,
        "score {fen} {history:?} depth={depth} q={quiescence}"
    );
    assert_eq!(result.completed_depth.to_string(), fields[3]);
    assert_eq!(
        result.nodes.to_string(),
        fields[4],
        "evaluation count {fen} {history:?}"
    );
}
#[test]
#[ignore = "requires the C++ reference executable; run via scripts/test-rust-engine.sh"]
fn native_regression_fixtures() {
    let fixtures = [
        START,
        "5K2/8/8/8/4kr2/8/8/8 w - - 0 1",
        "3r1K2/8/5k2/8/8/8/8/8 w - - 0 1",
        "2k5/8/8/8/8/8/8/4KB2 w - - 0 1",
        "r3k2r/8/8/8/8/8/8/R3K2R w KQkq - 0 1",
        "4k3/P7/8/8/8/8/7p/4K3 w - - 0 1",
        "4k3/8/8/3pP3/8/8/8/4K3 w - d6 0 1",
        "4k3/8/8/r4pPK/8/8/8/8 w - f6 0 1",
        "4k1n1/1b2p3/8/8/2B2B2/8/8/4K3 w - - 0 1",
        "k7/8/1QK5/8/8/8/8/8 b - - 0 1",
        "4k3/8/8/8/8/8/8/1NB1K3 w - - 0 1",
        "4k3/8/8/8/8/8/4P3/4K3 b - - 99 1",
    ];
    for fen in fixtures {
        for depth in 1..=3 {
            for q in [false, true] {
                compare(Variant::Standard, fen, &[], depth, q);
            }
        }
    }
    compare(
        Variant::Standard,
        START,
        &[
            "g1f3", "g8f6", "f3g1", "f6g8", "g1f3", "g8f6", "f3g1", "f6g8",
        ],
        2,
        true,
    );
    compare(
        Variant::Standard,
        "r3k2r/8/8/8/8/8/8/R3K2R w KQkq - 0 1",
        &[
            "e1e2", "e8e7", "e2e1", "e7e8", "e1e2", "e8e7", "e2e1", "e7e8",
        ],
        2,
        true,
    );
}
#[test]
#[ignore = "requires the C++ reference executable; run via scripts/test-rust-engine.sh"]
fn chess960_castling_fixtures() {
    // King on e1, rooks flanking at b1/g1 (native/project/jni/tests/engine_tests.cpp).
    let king_between_rooks = "4k3/8/8/8/8/8/8/1R2K1R1 w KQ - 0 1";
    for depth in 1..=3 {
        for q in [false, true] {
            compare(Variant::Chess960, king_between_rooks, &[], depth, q);
        }
    }
    // Rook already on its destination square.
    compare(
        Variant::Chess960,
        "4k3/8/8/8/8/8/8/3RK2R w KQ - 0 1",
        &[],
        2,
        true,
    );
    // King already on its destination square.
    compare(
        Variant::Chess960,
        "4k3/8/8/8/8/8/8/R1K4R w KQ - 0 1",
        &[],
        2,
        true,
    );
    // King and rook swap places.
    compare(Variant::Chess960, "4k3/8/8/8/8/8/8/5KR1 w K - 0 1", &[], 2, true);
    // Independent rook columns per colour, both castling in one line.
    compare(
        Variant::Chess960,
        "r3k3/8/8/8/8/8/8/4K1R1 w Kq - 0 1",
        &["e1g1", "e8a8"],
        2,
        true,
    );
    // Rights lost when the rook moves away and are not restored when it returns.
    compare(
        Variant::Chess960,
        "4k3/8/8/8/8/8/8/R3K2R w KQ - 0 1",
        &["a1a5", "e8e7", "a5a1", "e7e8"],
        2,
        true,
    );
}
#[test]
#[ignore = "requires the C++ reference executable; run via scripts/test-rust-engine.sh"]
fn deterministic_playouts() {
    let mut p = Position::from_fen(Variant::Standard, START).unwrap();
    let mut history = Vec::new();
    let mut seed = 248u64;
    for _ in 0..32 {
        let moves = p.legal_moves();
        if moves.is_empty() {
            break;
        }
        seed = seed.wrapping_mul(6364136223846793005).wrapping_add(1);
        let m = moves[(seed as usize) % moves.len()].to_string();
        p.apply(&m).unwrap();
        history.push(m);
        compare(
            Variant::Standard,
            START,
            &history.iter().map(String::as_str).collect::<Vec<_>>(),
            2,
            true,
        );
    }
}
