use chess_core::*;
use std::path::{Path, PathBuf};
use std::process::Command;
const START: &str = "rnbqkbnr/pppppppp/8/8/8/8/PPPPPPPP/RNBQKBNR w KQkq - 0 1";

fn fnv1a(s: &str) -> u64 {
    let mut hash: u64 = 0xcbf29ce484222325;
    for b in s.bytes() {
        hash ^= b as u64;
        hash = hash.wrapping_mul(0x100000001b3);
    }
    hash
}

fn fixture_path(subdir: &str, variant: Variant, fen: &str, history: &[&str], depth: u8, quiescence: bool) -> PathBuf {
    let key = format!("{variant:?}|{fen}|{}|{depth}|{quiescence}", history.join(","));
    Path::new(env!("CARGO_MANIFEST_DIR"))
        .join("tests/fixtures")
        .join(subdir)
        .join(format!("{:016x}.fixture", fnv1a(&key)))
}

/// Captures a mismatching comparison as a reproducible fixture before failing, so the seed,
/// request and result that found it can be inspected or promoted into `tests/fixtures/regressions/`
/// without re-deriving them from a test run.
fn report_mismatch(
    variant: Variant,
    fen: &str,
    history: &[&str],
    depth: u8,
    quiescence: bool,
    field: &str,
    actual: &str,
    expected: &str,
) -> ! {
    let path = fixture_path("failures", variant, fen, history, depth, quiescence);
    std::fs::create_dir_all(path.parent().unwrap()).unwrap();
    std::fs::write(
        &path,
        format!(
            "variant={variant:?}\nfen={fen}\nhistory={}\ndepth={depth}\nquiescence={quiescence}\nfield={field}\nexpected={expected}\nactual={actual}\n",
            history.join(",")
        ),
    )
    .unwrap();
    panic!(
        "{field} mismatch for {fen} {history:?} depth={depth} q={quiescence}: expected {expected:?}, got {actual:?} (fixture saved to {})",
        path.display()
    );
}

fn check(
    variant: Variant,
    fen: &str,
    history: &[&str],
    depth: u8,
    quiescence: bool,
    field: &str,
    actual: impl ToString,
    expected: impl ToString,
) {
    let (actual, expected) = (actual.to_string(), expected.to_string());
    if actual != expected {
        report_mismatch(variant, fen, history, depth, quiescence, field, &actual, &expected);
    }
}

fn compare(variant: Variant, fen: &str, history: &[&str], depth: u8, quiescence: bool) {
    let reference = std::env::var("CHESS_REFERENCE")
        .expect("Set CHESS_REFERENCE to the built chess_parity_reference executable");
    let output = Command::new(reference)
        .args([
            fen,
            &depth.to_string(),
            if quiescence { "1" } else { "0" },
            match variant {
                Variant::Standard => "0",
                Variant::Chess960 => "1",
                Variant::Duck => "2",
            },
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
    check(variant, fen, history, depth, quiescence, "fen", p.fen(), parts[1]);
    check(variant, fen, history, depth, quiescence, "eval", p.evaluate(), parts[2]);
    let state = match p.game_state() {
        GameState::Play => 1,
        GameState::Check => 2,
        GameState::DrawMaterial => 4,
        GameState::DrawFiftyMove => 5,
        GameState::Mate => 6,
        GameState::Stalemate => 7,
        GameState::DrawRepetition => 8,
    };
    check(variant, fen, history, depth, quiescence, "state", state, parts[3]);
    if state == 1 || state == 2 {
        let reference_moves: Vec<_> = text.lines().filter(|l| l.starts_with("MOVE\t")).collect();
        let legal = p.legal_moves();
        check(variant, fen, history, depth, quiescence, "move_count", legal.len(), reference_moves.len());
        for (i, (m, line)) in legal.iter().zip(reference_moves).enumerate() {
            let fields: Vec<_> = line.split('\t').collect();
            check(variant, fen, history, depth, quiescence, &format!("move_order[{i}]"), m.as_str(), fields[1]);
            let mut child = p.clone();
            child.apply(m.as_str()).unwrap();
            check(variant, fen, history, depth, quiescence, &format!("child_fen[{i}]"), child.fen(), fields[2]);
            check(variant, fen, history, depth, quiescence, &format!("child_eval[{i}]"), child.evaluate(), fields[3]);
            let san = p.to_san(m.as_str()).unwrap();
            check(variant, fen, history, depth, quiescence, &format!("san[{i}]"), san, fields[4]);
        }
    }
    let result = search(&request, &SearchControl::default()).unwrap();
    let fields: Vec<_> = text
        .lines()
        .find(|l| l.starts_with("RESULT\t"))
        .unwrap()
        .split('\t')
        .collect();
    check(
        variant, fen, history, depth, quiescence, "best_move",
        result.best_move.as_ref().map(CoordinateMove::as_str).unwrap_or("-"),
        fields[1],
    );
    check(variant, fen, history, depth, quiescence, "score", result.score, fields[2]);
    check(variant, fen, history, depth, quiescence, "completed_depth", result.completed_depth, fields[3]);
    check(variant, fen, history, depth, quiescence, "nodes", result.nodes, fields[4]);
}

/// Deterministically replays `plies` random-but-legal moves from `fen` for each seed, comparing
/// after every move so any repetition, promotion, castling or terminal state the walk happens to
/// reach is checked, not only the final position. Stops once the position is no longer `Play` or
/// `Check` (mate, stalemate or any drawn state): that mirrors `ChessBoard::isEnded()`, which the
/// production engine uses to refuse further moves once a game has ended, so a walk that kept
/// going past it would ask the C++ reference for a move the real app could never request.
fn walk(variant: Variant, fen: &str, seeds: &[u64], plies: u32, depth: u8, quiescence: bool) {
    for &initial_seed in seeds {
        let mut p = Position::from_fen(variant, fen).unwrap();
        let mut history = Vec::new();
        let mut seed = initial_seed;
        for _ in 0..plies {
            let moves = p.legal_moves();
            if moves.is_empty() {
                break;
            }
            seed = seed.wrapping_mul(6364136223846793005).wrapping_add(1);
            let m = moves[(seed as usize) % moves.len()].to_string();
            p.apply(&m).unwrap();
            history.push(m);
            compare(
                variant,
                fen,
                &history.iter().map(String::as_str).collect::<Vec<_>>(),
                depth,
                quiescence,
            );
            if !matches!(p.game_state(), GameState::Play | GameState::Check) {
                break;
            }
        }
    }
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
    // b1 attacked but not occupied: queenside castling only requires c1/d1
    // (the king's own transit) to be safe, not the whole span to the rook.
    compare(
        Variant::Standard,
        "1r2k3/8/8/8/8/8/8/R3K3 w Q - 0 1",
        &[],
        1,
        false,
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
    compare(
        Variant::Chess960,
        "4k3/8/8/8/8/8/8/5KR1 w K - 0 1",
        &[],
        2,
        true,
    );
    // Classical king/rook squares declared Chess 960: still only the king's
    // own transit (c1/d1) needs to be unattacked, not the full span to a1.
    compare(
        Variant::Chess960,
        "1r2k3/8/8/8/8/8/8/R3K3 w Q - 0 1",
        &[],
        1,
        false,
    );
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
    walk(Variant::Standard, START, &[248], 32, 2, true);
}

#[test]
#[ignore = "requires the C++ reference executable"]
fn duck_complete_turn_parity() {
    for fen in [
        START,
        "4k3/P7/8/8/8/8/7p/4K3 w - - 0 1",
        "4k3/4R3/8/3$4/8/8/8/4K3 w - - 0 1",
        "r3k2r/8/8/3$4/8/8/8/R3K2R w KQkq - 0 1",
        "4k3/8/8/3pP3/8/8/8/4K3 w - d6 0 1",
    ] {
        for depth in 1..=2 {
            for q in [false, true] {
                compare(Variant::Duck, fen, &[], depth, q);
            }
        }
    }
    compare(Variant::Duck, START, &["e2e4@d5", "e7e5@e2"], 2, true);
    compare(
        Variant::Duck,
        "4k3/4R3/8/3$4/8/8/8/4K3 w - - 0 1",
        &["e7e8"],
        2,
        true,
    );
}

#[test]
#[ignore = "requires the C++ reference executable"]
fn duck_deterministic_playout() {
    walk(Variant::Duck, START, &[252], 32, 2, true);
    compare(Variant::Duck, START, &[], 3, false);
}

/// Generated histories that supplement the fixed-position regressions above: each starting
/// position is chosen so a short random walk is likely to reach the specific situation named,
/// across several seeds for variety. Unlike the fixed fixtures, these exercise repetition,
/// promotion, castling-under-pressure and terminal detection as they arise mid-walk rather than
/// only at a hand-picked position.
#[test]
#[ignore = "requires the C++ reference executable; run via scripts/test-rust-engine.sh"]
fn generated_histories() {
    // A pawnless rook ending: random shuffling has no progress to reset the clock, so the walk
    // is expected to reach the fifty-move rule or a repeated position well inside the budget.
    walk(Variant::Standard, "4k2r/8/8/8/8/8/8/4KR2 w - - 0 1", &[401, 402, 403], 60, 2, true);
    // Lone passed pawns a step from promotion on both wings with nothing to block them.
    walk(Variant::Standard, "4k3/P6P/8/8/8/8/p6p/4K3 w - - 0 1", &[404, 405, 406], 24, 2, true);
    // A Chess 960 arrangement that keeps castling rights available deep into the walk, so
    // castling has to compete with other legal moves rather than being played immediately.
    walk(Variant::Chess960, "rk2r3/pppppppp/8/8/8/8/PPPPPPPP/RK2R3 w KQkq - 0 1", &[407, 408], 32, 2, true);
    // Duck turns over the same promotion-prone material as the Standard case above.
    walk(Variant::Duck, "4k3/P6P/8/8/8/8/p6p/4K3 w - - 0 1", &[409, 410], 32, 2, true);
    // A lone bishop ending prone to stalemate and insufficient-material terminal states.
    walk(Variant::Standard, "7k/8/8/8/8/8/6K1/7B w - - 0 1", &[411, 412], 40, 2, true);
}

/// Replays every fixture committed under `tests/fixtures/regressions/` (see
/// `tests/fixtures/README.md`). The directory may not exist yet, which is not a failure: nothing
/// has been promoted there until a discovered mismatch is deliberately kept as a regression.
#[test]
#[ignore = "requires the C++ reference executable; run via scripts/test-rust-engine.sh"]
fn fixture_regressions() {
    let dir = Path::new(env!("CARGO_MANIFEST_DIR")).join("tests/fixtures/regressions");
    let Ok(entries) = std::fs::read_dir(&dir) else {
        return;
    };
    for entry in entries {
        let path = entry.unwrap().path();
        if path.extension().and_then(|e| e.to_str()) != Some("fixture") {
            continue;
        }
        let text = std::fs::read_to_string(&path).unwrap();
        let mut variant = None;
        let mut fen = None;
        let mut history: Vec<String> = Vec::new();
        let mut depth = None;
        let mut quiescence = None;
        for line in text.lines() {
            let Some((key, value)) = line.split_once('=') else {
                continue;
            };
            match key {
                "variant" => {
                    variant = Some(match value {
                        "Standard" => Variant::Standard,
                        "Chess960" => Variant::Chess960,
                        "Duck" => Variant::Duck,
                        other => panic!("Unknown variant {other} in {}", path.display()),
                    })
                }
                "fen" => fen = Some(value.to_string()),
                "history" => {
                    history = if value.is_empty() {
                        Vec::new()
                    } else {
                        value.split(',').map(String::from).collect()
                    }
                }
                "depth" => depth = Some(value.parse().unwrap()),
                "quiescence" => quiescence = Some(value == "true"),
                _ => {}
            }
        }
        let history_refs: Vec<&str> = history.iter().map(String::as_str).collect();
        compare(
            variant.unwrap_or_else(|| panic!("Missing variant in {}", path.display())),
            &fen.unwrap_or_else(|| panic!("Missing fen in {}", path.display())),
            &history_refs,
            depth.unwrap_or_else(|| panic!("Missing depth in {}", path.display())),
            quiescence.unwrap_or_else(|| panic!("Missing quiescence in {}", path.display())),
        );
    }
}
