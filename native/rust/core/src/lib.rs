//! Owned chess positions and deterministic local search, independent of Android.
mod position;
pub use position::{CoordinateMove, Error, GameState, Position, Variant};
mod evaluation;
mod search;
pub use search::{
    search, search_with_progress, SearchControl, SearchEvent, SearchLimit, SearchOptions,
    SearchRequest, SearchResult,
};
