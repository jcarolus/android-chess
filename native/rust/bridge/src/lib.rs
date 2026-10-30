//! C ABI owned by the Android adapter. The reusable core contains no FFI or JNI.
use chess_core::*;
use std::ffi::{c_char, c_void, CStr};
use std::panic::{catch_unwind, AssertUnwindSafe};
#[repr(C)]
pub struct NativeResult {
    id: u64,
    score: i32,
    depth: i32,
    nodes: u64,
    elapsed_micros: u64,
    has_evaluation: i32,
    interrupted: i32,
    best: [u8; 9],
    pv: [u8; 160],
    error: [u8; 256],
}
#[no_mangle]
pub extern "C" fn rust_chess_create() -> *mut SearchControl {
    Box::into_raw(Box::new(SearchControl::default()))
}
/// # Safety
/// `control` must be a live handle from rust_chess_create, retained during this call.
#[no_mangle]
pub unsafe extern "C" fn rust_chess_stop(control: *const SearchControl) {
    if let Some(control) = control.as_ref() {
        control.stop();
    }
}
/// # Safety
/// Consume exactly once, after search and stop calls have finished.
#[no_mangle]
pub unsafe extern "C" fn rust_chess_destroy(control: *mut SearchControl) {
    if !control.is_null() {
        drop(Box::from_raw(control));
    }
}
unsafe fn text<'a>(p: *const c_char) -> Result<&'a str, Error> {
    if p.is_null() {
        return Err(Error("Missing text".into()));
    }
    CStr::from_ptr(p)
        .to_str()
        .map_err(|_| Error("Invalid UTF-8".into()))
}
/// # Safety
/// All strings must be NUL-terminated and live for the call. The history array
/// has `count` elements, output is writable, and control remains owned by JNI.
#[no_mangle]
pub unsafe extern "C" fn rust_chess_search(
    control: *const SearchControl,
    id: u64,
    variant: i32,
    fen: *const c_char,
    history: *const *const c_char,
    count: usize,
    depth: i32,
    millis: i32,
    quiescence: i32,
    out: *mut NativeResult,
    progress: Option<unsafe extern "C" fn(*mut c_void, *const NativeResult)>,
    context: *mut c_void,
) {
    if out.is_null() {
        return;
    }
    let output = &mut *out;
    *output = NativeResult {
        id,
        score: 0,
        depth: 0,
        nodes: 0,
        elapsed_micros: 0,
        has_evaluation: 0,
        interrupted: 0,
        best: [0; 9],
        pv: [0; 160],
        error: [0; 256],
    };
    let result = catch_unwind(AssertUnwindSafe(|| -> Result<(), Error> {
        let control = control
            .as_ref()
            .ok_or_else(|| Error("Missing search handle".into()))?;
        let variant = parse_variant(variant)?;
        if count > 100000 || count != 0 && history.is_null() {
            return Err(Error("Invalid history".into()));
        }
        let mut moves = Vec::with_capacity(count);
        for i in 0..count {
            moves.push(CoordinateMove::parse(text(*history.add(i))?)?);
        }
        let limit = if (1..=15).contains(&depth) && millis == 0 {
            SearchLimit::Depth(depth as u8)
        } else if depth == 0 && millis > 0 {
            SearchLimit::TimeMillis(millis as u32)
        } else {
            return Err(Error("Invalid search limit".into()));
        };
        let request = SearchRequest::new(
            id,
            variant,
            text(fen)?,
            moves,
            limit,
            SearchOptions {
                quiescence: quiescence != 0,
            },
        )?;
        // The C ABI is the application boundary: expose White-perspective scores here.
        let sign = if request.position().white_to_move() {
            1
        } else {
            -1
        };
        search_with_progress(&request, control, |event| {
            let (result, finished) = match event {
                SearchEvent::Progress(result) => (result, false),
                SearchEvent::Finished(result) => (result, true),
            };
            output.score = result.score * sign;
            output.depth = result.completed_depth as i32;
            output.nodes = result.nodes;
            output.elapsed_micros = result.elapsed_micros;
            output.has_evaluation = i32::from(result.has_evaluation);
            output.interrupted = i32::from(result.interrupted);
            output.best.fill(0);
            if let Some(m) = result.best_move {
                let bytes = m.as_str().as_bytes();
                output.best[..bytes.len()].copy_from_slice(bytes);
            }
            output.pv.fill(0);
            let pv = result
                .pv
                .iter()
                .map(CoordinateMove::as_str)
                .collect::<Vec<_>>()
                .join(" ");
            let bytes = pv.as_bytes();
            output.pv[..bytes.len()].copy_from_slice(bytes);
            if !finished {
                if let Some(callback) = progress {
                    callback(context, output);
                }
            }
        })?;
        Ok(())
    }));
    match result {
        Ok(Ok(())) => {}
        error => {
            let message = match error {
                Ok(Err(e)) => e.to_string(),
                _ => "Rust search panicked".into(),
            };
            let bytes = message.as_bytes();
            let count = bytes.len().min(255);
            output.error[..count].copy_from_slice(&bytes[..count]);
        }
    }
}
/// Stateless board-operations result: a snapshot query, a move's SAN, the legal moves from a
/// snapshot (optionally with SAN in `turns`), or the attackers of a square. Every call fills only the fields its name promises;
/// the rest are left at their zero/empty default.
#[repr(C)]
pub struct BoardResult {
    turn_white: i32,
    game_state: i32,
    in_check: i32,
    halfmove_clock: u32,
    repetition_count: u32,
    castling: i32,
    hash_key: u64,
    captured: [u32; 10],
    piece: u8,
    fen: [u8; 96],
    san: [u8; 24],
    moves: [u8; 32768],
    attackers: [u8; 256],
    turns: [u8; 8192],
    error: [u8; 256],
}
fn default_board_result() -> BoardResult {
    BoardResult {
        turn_white: 0,
        game_state: 0,
        in_check: 0,
        halfmove_clock: 0,
        repetition_count: 0,
        castling: 0,
        hash_key: 0,
        captured: [0; 10],
        piece: 0,
        fen: [0; 96],
        san: [0; 24],
        moves: [0; 32768],
        attackers: [0; 256],
        turns: [0; 8192],
        error: [0; 256],
    }
}
fn parse_variant(variant: i32) -> Result<Variant, Error> {
    match variant {
        0 => Ok(Variant::Standard),
        1 => Ok(Variant::Chess960),
        2 => Ok(Variant::Duck),
        3 => Ok(Variant::KingOfTheHill),
        _ => Err(Error("Unknown variant".into())),
    }
}
/// Writes `s` into `dst` (already zeroed by the caller), leaving a trailing zero byte to
/// terminate the C string. Returns `false`, writing nothing, if `s` does not fit: a truncated
/// result would silently misrepresent the position rather than signal the problem.
fn write_capped(dst: &mut [u8], s: &str) -> bool {
    if s.len() >= dst.len() {
        return false;
    }
    dst[..s.len()].copy_from_slice(s.as_bytes());
    true
}
/// Truncates `s` to fit `dst` (already zeroed by the caller), always leaving a trailing zero
/// byte. Used only for the error message, where a truncated explanation beats none at all.
fn write_truncated(dst: &mut [u8], s: &str) {
    let max = dst.len().saturating_sub(1);
    let n = s.len().min(max);
    dst[..n].copy_from_slice(&s.as_bytes()[..n]);
}
fn game_state_code(state: GameState) -> i32 {
    match state {
        GameState::Play => 0,
        GameState::Check => 1,
        GameState::Mate => 2,
        GameState::Stalemate => 3,
        GameState::DrawMaterial => 4,
        GameState::DrawFiftyMove => 5,
        GameState::DrawRepetition => 6,
        GameState::HillWin => 7,
        GameState::DuckStalemateWin => 8,
    }
}
/// # Safety
/// `fen` must be NUL-terminated; `history` has `count` NUL-terminated elements.
unsafe fn rebuild(
    variant: i32,
    fen: *const c_char,
    history: *const *const c_char,
    count: usize,
) -> Result<Position, Error> {
    if count > 100000 || (count != 0 && history.is_null()) {
        return Err(Error("Invalid history".into()));
    }
    let variant = parse_variant(variant)?;
    let mut position = Position::from_fen(variant, text(fen)?)?;
    for i in 0..count {
        position.apply(text(*history.add(i))?)?;
    }
    Ok(position)
}
/// Fills the whole-position fields shared by a query and a post-move result.
fn fill_query(position: &Position, out: &mut BoardResult) -> Result<(), Error> {
    out.turn_white = i32::from(position.white_to_move());
    out.game_state = game_state_code(position.game_state());
    out.in_check = i32::from(position.in_check());
    out.halfmove_clock = position.halfmove_clock();
    out.repetition_count = position.repetition_count() as u32;
    let rights = position.castling_rights();
    out.castling = rights
        .iter()
        .enumerate()
        .fold(0, |acc, (i, &r)| acc | (i32::from(r) << i));
    out.hash_key = position.hash_key();
    for (t, white) in [(0, false), (1, true)] {
        let captured = position.captured(white);
        out.captured[t * 5..t * 5 + 5].copy_from_slice(&captured);
    }
    if !write_capped(&mut out.fen, &position.fen()) {
        return Err(Error("FEN exceeds buffer capacity".into()));
    }
    Ok(())
}
/// Writes the legal moves of `position` to `out.turns` as space-separated `coordinate=san` pairs.
/// Duck turns are reduced to their piece move, since SAN and move selection ignore the placement.
fn fill_turns(position: &Position, out: &mut BoardResult) -> Result<(), Error> {
    let mut seen = std::collections::HashSet::new();
    let mut turns = String::new();
    for turn in position.legal_moves() {
        let piece = turn.as_str().split('@').next().unwrap_or("");
        if !seen.insert(piece.to_string()) {
            continue;
        }
        if !turns.is_empty() {
            turns.push(' ');
        }
        turns.push_str(piece);
        turns.push('=');
        turns.push_str(&position.to_san(piece)?);
    }
    if !write_capped(&mut out.turns, &turns) {
        return Err(Error("Legal turns exceed buffer capacity".into()));
    }
    Ok(())
}
fn report(output: &mut BoardResult, result: std::thread::Result<Result<(), Error>>) {
    if let Err(e) = result.unwrap_or_else(|_| Err(Error("Rust board call panicked".into()))) {
        write_truncated(&mut output.error, &e.to_string());
    }
}
/// A snapshot query: game state, turn, check, castling rights, hash key, captured pieces, FEN
/// and the draw/repetition counters.
///
/// # Safety
/// All strings must be NUL-terminated and live for the call; `out` must be writable.
#[no_mangle]
pub unsafe extern "C" fn rust_board_query(
    variant: i32,
    fen: *const c_char,
    history: *const *const c_char,
    count: usize,
    out: *mut BoardResult,
) {
    if out.is_null() {
        return;
    }
    let output = &mut *out;
    *output = default_board_result();
    let result = catch_unwind(AssertUnwindSafe(|| -> Result<(), Error> {
        let position = rebuild(variant, fen, history, count)?;
        fill_query(&position, output)
    }));
    report(output, result);
}
/// The legal moves from a snapshot, space-separated in `moves`.
///
/// # Safety
/// All strings must be NUL-terminated and live for the call; `out` must be writable.
#[no_mangle]
pub unsafe extern "C" fn rust_board_legal_moves(
    variant: i32,
    fen: *const c_char,
    history: *const *const c_char,
    count: usize,
    out: *mut BoardResult,
) {
    if out.is_null() {
        return;
    }
    let output = &mut *out;
    *output = default_board_result();
    let result = catch_unwind(AssertUnwindSafe(|| -> Result<(), Error> {
        let position = rebuild(variant, fen, history, count)?;
        let moves = position
            .legal_moves()
            .iter()
            .map(CoordinateMove::as_str)
            .collect::<Vec<_>>()
            .join(" ");
        if !write_capped(&mut output.moves, &moves) {
            return Err(Error("Legal moves exceed buffer capacity".into()));
        }
        Ok(())
    }));
    report(output, result);
}
/// The legal moves from a snapshot with their SAN, as `coordinate=san` pairs in `turns`.
///
/// # Safety
/// All strings must be NUL-terminated and live for the call; `out` must be writable.
#[no_mangle]
pub unsafe extern "C" fn rust_board_legal_turns(
    variant: i32,
    fen: *const c_char,
    history: *const *const c_char,
    count: usize,
    out: *mut BoardResult,
) {
    if out.is_null() {
        return;
    }
    let output = &mut *out;
    *output = default_board_result();
    let result = catch_unwind(AssertUnwindSafe(|| -> Result<(), Error> {
        let position = rebuild(variant, fen, history, count)?;
        fill_turns(&position, output)
    }));
    report(output, result);
}
/// Applies one coordinate move to a snapshot and reports the resulting position, matching
/// `rust_board_query`'s fields, plus the new position's legal moves in `turns` (empty for a
/// Duck preview). A Duck piece-only coordinate produces a detached preview;
/// complete recorded turns require a placement unless the piece move ends the game.
/// The snapshot itself is never mutated.
///
/// # Safety
/// All strings must be NUL-terminated and live for the call; `out` must be writable.
#[no_mangle]
pub unsafe extern "C" fn rust_board_apply_move(
    variant: i32,
    fen: *const c_char,
    history: *const *const c_char,
    count: usize,
    coordinate: *const c_char,
    out: *mut BoardResult,
) {
    if out.is_null() {
        return;
    }
    let output = &mut *out;
    *output = default_board_result();
    let result = catch_unwind(AssertUnwindSafe(|| -> Result<(), Error> {
        let mut position = rebuild(variant, fen, history, count)?;
        let coordinate = text(coordinate)?;
        let preview = parse_variant(variant)? == Variant::Duck && !coordinate.contains('@');
        if preview {
            position = position.preview_piece(coordinate)?;
        } else {
            position.apply(coordinate)?;
        }
        fill_query(&position, output)?;
        if !preview {
            fill_turns(&position, output)?;
        }
        Ok(())
    }));
    report(output, result);
}
/// Standard Algebraic Notation for one legal move from a snapshot, written to `san`.
///
/// # Safety
/// All strings must be NUL-terminated and live for the call; `out` must be writable.
#[no_mangle]
pub unsafe extern "C" fn rust_board_san(
    variant: i32,
    fen: *const c_char,
    history: *const *const c_char,
    count: usize,
    coordinate: *const c_char,
    out: *mut BoardResult,
) {
    if out.is_null() {
        return;
    }
    let output = &mut *out;
    *output = default_board_result();
    let result = catch_unwind(AssertUnwindSafe(|| -> Result<(), Error> {
        let position = rebuild(variant, fen, history, count)?;
        let san = position.to_san(text(coordinate)?)?;
        if !write_capped(&mut output.san, &san) {
            return Err(Error("SAN exceeds buffer capacity".into()));
        }
        Ok(())
    }));
    report(output, result);
}
/// The FEN piece character at one square of a snapshot, written to `piece` (0 means empty).
///
/// # Safety
/// All strings must be NUL-terminated and live for the call; `out` must be writable.
#[no_mangle]
pub unsafe extern "C" fn rust_board_piece_at(
    variant: i32,
    fen: *const c_char,
    history: *const *const c_char,
    count: usize,
    square: *const c_char,
    out: *mut BoardResult,
) {
    if out.is_null() {
        return;
    }
    let output = &mut *out;
    *output = default_board_result();
    let result = catch_unwind(AssertUnwindSafe(|| -> Result<(), Error> {
        let position = rebuild(variant, fen, history, count)?;
        output.piece = position
            .piece_at(text(square)?)?
            .map(|c| c as u8)
            .unwrap_or(0);
        Ok(())
    }));
    report(output, result);
}
/// Squares of the given colour that attack a square of a snapshot, space-separated in
/// `attackers`.
///
/// # Safety
/// All strings must be NUL-terminated and live for the call; `out` must be writable.
#[no_mangle]
pub unsafe extern "C" fn rust_board_attackers(
    variant: i32,
    fen: *const c_char,
    history: *const *const c_char,
    count: usize,
    by_white: i32,
    square: *const c_char,
    out: *mut BoardResult,
) {
    if out.is_null() {
        return;
    }
    let output = &mut *out;
    *output = default_board_result();
    let result = catch_unwind(AssertUnwindSafe(|| -> Result<(), Error> {
        let position = rebuild(variant, fen, history, count)?;
        let attackers = position.attackers(by_white != 0, text(square)?)?.join(" ");
        if !write_capped(&mut output.attackers, &attackers) {
            return Err(Error("Attackers exceed buffer capacity".into()));
        }
        Ok(())
    }));
    report(output, result);
}
#[cfg(test)]
mod tests {
    use super::*;
    use std::ffi::CString;
    const START: &str = "rnbqkbnr/pppppppp/8/8/8/8/PPPPPPPP/RNBQKBNR w KQkq - 0 1";
    fn c(s: &str) -> CString {
        CString::new(s).unwrap()
    }
    fn text_of(bytes: &[u8]) -> String {
        let end = bytes.iter().position(|&b| b == 0).unwrap_or(bytes.len());
        String::from_utf8(bytes[..end].to_vec()).unwrap()
    }
    /// Runs `call` against the history's C representation and returns the filled result.
    fn with_history(
        history: &[String],
        call: impl FnOnce(*const *const c_char, usize, &mut BoardResult),
    ) -> Box<BoardResult> {
        let owned: Vec<CString> = history.iter().map(|m| c(m)).collect();
        let pointers: Vec<*const c_char> = owned.iter().map(|m| m.as_ptr()).collect();
        let mut out = Box::new(default_board_result());
        call(pointers.as_ptr(), pointers.len(), &mut out);
        assert_eq!(text_of(&out.error), "");
        out
    }
    fn legal_turns(history: &[String]) -> String {
        let fen = c(START);
        let out = with_history(history, |h, n, out| unsafe {
            rust_board_legal_turns(0, fen.as_ptr(), h, n, out)
        });
        text_of(&out.turns)
    }
    fn apply(history: &[String], coordinate: &str) -> Box<BoardResult> {
        let fen = c(START);
        let coordinate = c(coordinate);
        with_history(history, |h, n, out| unsafe {
            rust_board_apply_move(0, fen.as_ptr(), h, n, coordinate.as_ptr(), out)
        })
    }
    #[test]
    fn legal_turns_pair_each_move_with_its_san() {
        let turns = legal_turns(&[]);
        assert_eq!(turns.split(' ').count(), 20);
        assert!(turns.split(' ').any(|t| t == "e2e4=e4"));
        assert!(turns.split(' ').any(|t| t == "g1f3=Nf3"));
    }
    #[test]
    fn apply_move_reports_the_next_positions_turns() {
        let history = vec!["e2e4".to_string()];
        let applied = apply(&[], "e2e4");
        assert_eq!(text_of(&applied.turns), legal_turns(&history));
    }
    /// Replaying from the last pawn move gives the same repetition result as the full history.
    #[test]
    fn windowed_history_keeps_repetition_detection() {
        let shuffle = ["g1f3", "g8f6", "f3g1", "f6g8"];
        let mut full = vec!["e2e4".to_string(), "e7e5".to_string()];
        full.extend(shuffle.iter().map(|m| m.to_string()));
        full.extend(shuffle.iter().map(|m| m.to_string()));
        let fen = c(START);
        let whole = with_history(&full, |h, n, out| unsafe {
            rust_board_query(0, fen.as_ptr(), h, n, out)
        });
        let base = apply(&["e2e4".to_string()], "e7e5");
        let base_fen = c(&text_of(&base.fen));
        let tail: Vec<String> = full[2..].to_vec();
        let windowed = with_history(&tail, |h, n, out| unsafe {
            rust_board_query(0, base_fen.as_ptr(), h, n, out)
        });
        assert_eq!(whole.game_state, 6);
        assert_eq!(windowed.game_state, whole.game_state);
        assert_eq!(windowed.repetition_count, whole.repetition_count);
    }
    /// A game of `plies` moves, always taking the move at a rotating index of the legal list.
    fn game(plies: usize) -> Vec<String> {
        let mut history: Vec<String> = Vec::new();
        for i in 0..plies {
            let turns = legal_turns(&history);
            if turns.is_empty() {
                break;
            }
            let all: Vec<&str> = turns.split(' ').collect();
            history.push(
                all[(i * 7) % all.len()]
                    .split('=')
                    .next()
                    .unwrap()
                    .to_string(),
            );
        }
        history
    }
    /// `cargo test --release --manifest-path native/rust/Cargo.toml -p rust-chess-bridge import_cost -- --ignored --nocapture`
    #[test]
    #[ignore]
    fn import_cost() {
        let moves = game(80);
        let fen = c(START);
        let started = std::time::Instant::now();
        let mut calls = 0;
        for i in 0..moves.len() {
            let history = &moves[..i];
            let out = with_history(history, |h, n, out| unsafe {
                rust_board_legal_moves(0, fen.as_ptr(), h, n, out)
            });
            calls += 1;
            for coordinate in text_of(&out.moves).split(' ') {
                let coordinate = c(coordinate);
                with_history(history, |h, n, out| unsafe {
                    rust_board_san(0, fen.as_ptr(), h, n, coordinate.as_ptr(), out)
                });
                calls += 1;
            }
            apply(history, &moves[i]);
            calls += 1;
        }
        let before = started.elapsed();
        let started = std::time::Instant::now();
        for i in 0..moves.len() {
            apply(&moves[..i], &moves[i]);
        }
        let after = started.elapsed();
        // Replay from the latest pawn move or capture (halfmove clock 0), as `RustBoard` does.
        let started = std::time::Instant::now();
        let mut base_fen = START.to_string();
        let mut tail: Vec<String> = Vec::new();
        for m in &moves {
            let base = c(&base_fen);
            let out = with_history(&tail, |h, n, out| unsafe {
                rust_board_apply_move(0, base.as_ptr(), h, n, c(m).as_ptr(), out)
            });
            tail.push(m.clone());
            if out.halfmove_clock == 0 {
                base_fen = text_of(&out.fen);
                tail.clear();
            }
        }
        let windowed = started.elapsed();
        println!(
            "{} plies: per-move SAN calls {} native calls in {:?}; batched {} calls in {:?}; windowed in {:?}",
            moves.len(),
            calls,
            before,
            moves.len(),
            after,
            windowed
        );
    }
}
