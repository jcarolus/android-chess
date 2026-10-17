//! C ABI owned by the Android adapter. The reusable core contains no FFI or JNI.
use chess_core::*;
use std::ffi::{c_char, CStr};
use std::panic::{catch_unwind, AssertUnwindSafe};
#[repr(C)]
pub struct NativeResult {
    id: u64,
    score: i32,
    depth: i32,
    nodes: u64,
    has_evaluation: i32,
    interrupted: i32,
    best: [u8; 6],
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
        has_evaluation: 0,
        interrupted: 0,
        best: [0; 6],
        error: [0; 256],
    };
    let result = catch_unwind(AssertUnwindSafe(|| -> Result<SearchResult, Error> {
        let control = control
            .as_ref()
            .ok_or_else(|| Error("Missing search handle".into()))?;
        let variant = match variant {
            0 => Variant::Standard,
            1 => Variant::Chess960,
            2 => Variant::Duck,
            _ => return Err(Error("Unknown variant".into())),
        };
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
        search(&request, control)
    }));
    match result {
        Ok(Ok(result)) => {
            output.score = result.score;
            output.depth = result.completed_depth as i32;
            output.nodes = result.nodes;
            output.has_evaluation = i32::from(result.has_evaluation);
            output.interrupted = i32::from(result.interrupted);
            if let Some(m) = result.best_move {
                let bytes = m.as_str().as_bytes();
                output.best[..bytes.len()].copy_from_slice(bytes);
            }
        }
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
