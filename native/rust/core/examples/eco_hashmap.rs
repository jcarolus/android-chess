//! Builds the opening database `app/src/main/assets/hashmap.bin` from the
//! lichess-org/chess-openings list converted to JSON (`eco_moves.json`: an array of
//! objects with "moves" in SAN and "name"):
//!
//! `cargo run --release --manifest-path native/rust/Cargo.toml -p chess-core --example eco_hashmap -- eco_moves.json app/src/main/assets/hashmap.bin`
//!
//! Each opening's final position is keyed by `Position::hash_key`; when several
//! openings reach the same position, the first one keeps it. The output is the
//! `HMap` format read by `jwtc.android.chess.services.HMap`.
use chess_core::{Position, Variant};
use std::process::exit;

const START: &str = "rnbqkbnr/pppppppp/8/8/8/8/PPPPPPPP/RNBQKBNR w KQkq - 0 1";

fn main() {
    let args: Vec<String> = std::env::args().skip(1).collect();
    if args.len() != 2 {
        eprintln!("usage: eco_hashmap <eco_moves.json> <hashmap.bin>");
        exit(2);
    }
    let json = std::fs::read_to_string(&args[0]).unwrap_or_else(|e| fail(&e.to_string()));
    let openings = parse_openings(&json).unwrap_or_else(|e| fail(&e));
    let mut entries: Vec<(u64, String)> = Vec::new();
    let mut skipped = 0;
    for (moves, name) in &openings {
        match final_key(moves) {
            Ok(key) => {
                if !entries.iter().any(|(k, _)| *k == key) {
                    entries.push((key, name.clone()));
                }
            }
            Err(e) => {
                eprintln!("skipped {name:?} ({moves:?}): {e}");
                skipped += 1;
            }
        }
    }
    // HMap looks entries up by binary search over signed 64-bit hashes.
    entries.sort_by_key(|(key, _)| *key as i64);
    std::fs::write(&args[1], hmap(&entries)).unwrap_or_else(|e| fail(&e.to_string()));
    println!(
        "{} openings, {} positions written, {} skipped",
        openings.len(),
        entries.len(),
        skipped
    );
}

fn fail(message: &str) -> ! {
    eprintln!("{message}");
    exit(1)
}

/// The key after replaying SAN movetext such as `1. e4 c5 2. Nf3`.
fn final_key(movetext: &str) -> Result<u64, String> {
    let mut position = Position::from_fen(Variant::Standard, START).unwrap();
    let mut played = 0;
    for token in movetext.split_whitespace() {
        if token.ends_with('.') {
            continue;
        }
        let san = token.trim_end_matches(['+', '#', '!', '?']);
        let coordinate = position
            .legal_moves()
            .into_iter()
            .find(|m| {
                position
                    .to_san(m.as_str())
                    .is_ok_and(|s| s.trim_end_matches(['+', '#']) == san)
            })
            .ok_or_else(|| format!("no legal move {token}"))?;
        position
            .apply(coordinate.as_str())
            .map_err(|e| e.to_string())?;
        played += 1;
    }
    if played == 0 {
        return Err("no moves".into());
    }
    Ok(position.hash_key())
}

/// `(moves, name)` of each object in a JSON array of flat string-valued objects;
/// objects lacking either field are left out.
fn parse_openings(json: &str) -> Result<Vec<(String, String)>, String> {
    let mut chars = json.chars().peekable();
    let mut openings = Vec::new();
    let skip_ws = |chars: &mut std::iter::Peekable<std::str::Chars>| {
        while chars.peek().is_some_and(|c| c.is_whitespace()) {
            chars.next();
        }
    };
    let expect = |chars: &mut std::iter::Peekable<std::str::Chars>, c: char| {
        skip_ws(chars);
        if chars.next() == Some(c) {
            Ok(())
        } else {
            Err(format!("expected '{c}'"))
        }
    };
    expect(&mut chars, '[')?;
    skip_ws(&mut chars);
    if chars.peek() == Some(&']') {
        return Ok(openings);
    }
    loop {
        expect(&mut chars, '{')?;
        let (mut moves, mut name) = (None, None);
        skip_ws(&mut chars);
        if chars.peek() == Some(&'}') {
            chars.next();
        } else {
            loop {
                expect(&mut chars, '"')?;
                let key = string(&mut chars)?;
                expect(&mut chars, ':')?;
                expect(&mut chars, '"')?;
                let value = string(&mut chars)?;
                match key.as_str() {
                    "moves" => moves = Some(value),
                    "name" => name = Some(value),
                    _ => {}
                }
                skip_ws(&mut chars);
                match chars.next() {
                    Some(',') => {}
                    Some('}') => break,
                    _ => return Err("expected ',' or '}'".into()),
                }
            }
        }
        if let (Some(moves), Some(name)) = (moves, name) {
            openings.push((moves, name));
        }
        skip_ws(&mut chars);
        match chars.next() {
            Some(',') => {}
            Some(']') => return Ok(openings),
            _ => return Err("expected ',' or ']'".into()),
        }
    }
}

/// The rest of a JSON string whose opening quote has been read.
fn string(chars: &mut impl Iterator<Item = char>) -> Result<String, String> {
    let mut s = String::new();
    loop {
        match chars.next().ok_or("unterminated string")? {
            '"' => return Ok(s),
            '\\' => match chars.next().ok_or("unterminated escape")? {
                'n' => s.push('\n'),
                't' => s.push('\t'),
                'r' => s.push('\r'),
                'b' => s.push('\u{8}'),
                'f' => s.push('\u{c}'),
                'u' => {
                    let hex: String = chars.take(4).collect();
                    let unit = u32::from_str_radix(&hex, 16).map_err(|_| "invalid \\u escape")?;
                    s.push(char::from_u32(unit).ok_or("unsupported \\u escape")?);
                }
                c => s.push(c),
            },
            c => s.push(c),
        }
    }
}

/// Header `HMAP`, version 1, count and CRC-32 of entries plus blob; entries of
/// key, blob offset and length; then the UTF-8 names. All little-endian.
fn hmap(entries: &[(u64, String)]) -> Vec<u8> {
    let mut table = Vec::with_capacity(entries.len() * 16);
    let mut blob = Vec::new();
    for (key, name) in entries {
        table.extend_from_slice(&key.to_le_bytes());
        table.extend_from_slice(&(blob.len() as u32).to_le_bytes());
        table.extend_from_slice(&(name.len() as u16).to_le_bytes());
        table.extend_from_slice(&0u16.to_le_bytes());
        blob.extend_from_slice(name.as_bytes());
    }
    let mut out = Vec::with_capacity(16 + table.len() + blob.len());
    out.extend_from_slice(b"HMAP");
    out.extend_from_slice(&1u16.to_le_bytes());
    out.extend_from_slice(&0u16.to_le_bytes());
    out.extend_from_slice(&(entries.len() as u32).to_le_bytes());
    out.extend_from_slice(&crc32(&[&table, &blob]).to_le_bytes());
    out.extend_from_slice(&table);
    out.extend_from_slice(&blob);
    out
}

/// CRC-32 (IEEE), as computed by `java.util.zip.CRC32`.
fn crc32(parts: &[&[u8]]) -> u32 {
    let mut crc = !0u32;
    for byte in parts.iter().flat_map(|p| p.iter()) {
        crc ^= u32::from(*byte);
        for _ in 0..8 {
            crc = if crc & 1 != 0 {
                (crc >> 1) ^ 0xedb8_8320
            } else {
                crc >> 1
            };
        }
    }
    !crc
}
