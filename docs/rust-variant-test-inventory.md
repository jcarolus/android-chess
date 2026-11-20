# Rust chess variant rule-test inventory

Inspected 2026-11-08. Scope: `native/rust/core` and `native/rust/bridge`; no Java/Kotlin tests. This is a source-level assessment of assertions, not measured branch coverage. A missing test does not establish a defect.

Baseline validation before additions: `cargo test --manifest-path native/rust/Cargo.toml --workspace`: **77 passed, 0 failed, 1 ignored**. The ignored test is the bridge `import_cost` benchmark. The baseline had 78 test functions: 72 in core and 6 in bridge. Some are infrastructure or evaluation tests, rather than independent rule guards.

## Implemented coverage

The follow-up adds **36 test functions**, with table-driven fixtures across variants, colours, promotion choices and all 960 starting arrangements. Current release validation: `cargo test --release --locked --manifest-path native/rust/Cargo.toml --workspace`: **113 passed, 0 failed, 1 ignored benchmark**.

| Tests | Added rule guards |
|---|---|
| `core/tests/variant_rules.rs` | Three reference perft positions at depths 1–3 for Standard/Chess 960; single/double check, pins, king safety; en-passant removal/expiry/discovered attacks/check escape; every promotion with/without capture for both colours in all variants; castling attacks/blockers; draw thresholds/resets/repetition identity; mirrored mate/stalemate; Hill captures, material exemptions, defended entry, notation and terminal/horizon search |
| `core/tests/chess960_rules.rs` | Both colours/wings for stationary king/rook and swaps; blocked rook paths and discovered attacks; rights loss and replacement/promoted rooks; independent outermost-rook tracking and FEN/SAN round trips; all 960 starting layouts; search history with castling and legal PV replay |
| `core/tests/duck_rules.rs` | No check restrictions and adjacent kings; duck blockers for every piece; knight jumps; en passant and placement on vacated squares; failed-turn atomicity; king capture including promotions; placement-dependent stalemate for either colour; terminal draw placement rules; replayable search moves ending in 50-move/repetition draws; search blocking king capture and avoiding gifting a stalemate win |
| `core/tests/notation_and_validation.rs` | Rank/full-square/pinned-piece SAN disambiguation; Duck capture/promotion/en-passant SAN without orthodox check/mate suffixes; variant-aware invalid kings, pawns, counters and castling claims |
| `core/src/search.rs` | A real Chess 960 castling branch isolated from alternative root moves, ensuring both colours produce a replayable PV in king-to-rook coordinates |
| `bridge/src/lib.rs` | Variant IDs and invalid IDs; Chess 960 move coordinates/SAN; Hill/Duck terminal codes, replayed histories, empty terminal turns and owned replay parity |

The stalemate-search regression exposed two Duck search gaps: terminal no-move wins were evaluated statically at depth zero, and the placement heuristic could consider only squares that gifted a stalemate win. Search now scores no-move wins before the depth cutoff and falls back to all empty placement squares when every heuristic candidate leaves the opponent without moves. The depth-1/depth-2 regression verifies a safe alternative is chosen. Further regressions exposed draw-ending search moves incorrectly carrying duck placements; search now stops those turns after the piece move for both the 50-move rule and repetition.

Scope notes: File-letter castling FEN support remains unspecified. A repeated terminal hill position is unreachable through normal play. A normal capture leaves the capturing piece on the captured piece's square, so Duck placement there must be rejected as occupied; the en-passant victim square is separately tested as a valid placement. These are not missing acceptance tests.

The detailed gap assessment below is retained as the original audit, rather than a list of currently absent tests. Coverage is representative and is not an exhaustive cross-product of all boards or mutation/branch coverage.

## Baseline coverage by variant

| Variant | Existing direct guards | Main weakness |
|---|---|---|
| Standard | Initial-position perft(3)=8902; castling, en passant and knight promotion application; material draws; repetition and en-passant identity; mate overriding the 50-move draw; SAN; terminal search and check evasions | No reference perft positions exercising special moves; few explicit illegal-move/check tests |
| Chess 960 | 12 dedicated castling tests in positions.rs; material draws; mixed SAN-query consistency; incremental hash consistency | Mostly White examples, limited rights-loss cases, no reference perft or dedicated search test |
| Duck | 11 dedicated tests; material-draw exemption; duck-insensitive position key; SAN/query and bridge preview guards | En passant, general blocker behavior and absence of check restrictions lack explicit fixtures |
| King of the Hill | 8 dedicated tests; both colours/all four hill squares; illegal attacked entry; draw precedence; bare kings; mate/stalemate; hill-aware search | Shared orthodox special rules mostly untested under this variant; capture-on-hill and terminal-root search missing |

Shared implementation gives indirect protection, but Standard-only assertions do not guard variant-specific branching. The incremental-hash test checks key consistency, not whether the generated move set is correct. The SAN batch test compares APIs using the same implementation, so it cannot independently establish correct notation or legality.

## Original gap assessment (before implementation)

### 1. Orthodox legality and independent move-generation oracles — high priority

Apply these to Standard and Chess 960, and selected cases to King of the Hill:

- Add reference perft positions covering castling, checks, pins, en passant and promotions. Initial perft at depth 3 never reaches castling or promotion, or an en-passant capture. The complex position in the hash test has no expected move counts.
- Single check: only capture, block or king escape permitted. Double check: only king escapes. Pinned pieces cannot expose the king; a pinned piece may still move along the pin.
- Kings cannot become adjacent, move into attacked squares or capture a defended piece; capturing an enemy king is forbidden.
- En passant for both colours: correct captured pawn removed; opportunity expires after one reply; capture exposing rook/bishop attack is rejected; capture resolving check is allowed. Existing repetition coverage exercises a pinned en-passant identity, but does not directly assert move rejection.
- Every promotion (Q/R/B/N), for both colours, with and without capture; all four generated; missing/invalid promotion rejected.
- Standard castling: both wings/both colours; reject while in check, through check or into check; reject occupied king/rook paths; king/rook moved-and-returned rights remain lost.
- Draw boundaries: halfmove 99 versus 100; reset on pawn move, normal capture, en passant and promotion capture; two occurrences versus three; changed castling rights prevent repetition. Run shared draw fixtures under Chess 960 too.
- Direct Standard game-state assertions for check, mate and stalemate, including the side-to-move mirror. Some of these currently have only search/SAN guards.

### 2. Duck rule interactions — high priority

- Explicitly permit leaving one's king attacked, moving a king into attack, adjacent kings and ignoring an attack on one's king. Assert `game_state` remains Play rather than Check/Mate until capture or another terminal rule.
- Duck blocks rook/bishop/queen rays, knight/king destinations, pawn single/double pushes and captures. Knights can jump over the duck. Neither side can capture the duck. Existing examples cover a pawn blocked by the duck and a castling path, not this full matrix.
- En passant works with duck relocation; a duck on the en-passant target prevents capture; relocating the duck does not prolong the en-passant window.
- Duck may be placed on the piece's vacated square, captured-piece square (where now empty), vacated en-passant pawn square or castling origins (where empty). It cannot stay put or occupy any remaining piece square. Assert failed turns preserve history and counters as well as FEN.
- King capture by Black and by other piece types, including promotion capture; terminal capture requires no placement and rejects a supplied placement. Capture on halfmove 100 takes precedence over the draw.
- Stalemate win for White as well as Black; importantly, create/remove a stalemate through the final duck placement. The current test only loads an already blocked position.
- Fifty-move and repetition terminal turns reject extra placement, expose no moves and stop subsequent play. Existing tests establish omission, but not all rejection paths.
- Search must account for duck placement preventing immediate king capture or creating a stalemate win; replayability alone is not a tactical rules oracle.

Duck position identity deliberately ignores the duck square: two existing tests lock this behavior. Do not propose changing those expectations without a product decision.

### 3. Chess 960 castling matrix — high priority

- Mirror overlap/stationary-piece/king-rook-swap fixtures for Black and the opposite wing.
- Castling rook moves away and returns; king moves away and returns; original rook captured; replacement/promoted rook does not restore the right. Verify only the appropriate wing/colour loses rights.
- Moving/capturing a different rook on the same file away from the back rank must preserve the tracked rook's rights.
- Attacks revealed when the rook vacates its origin, especially with the king already on its final square; attacked destination; castling out of check. Assert final board legality after simultaneous relocation.
- Blocking only the rook's path/destination, rather than only the king's path. Include overlap squares occupied by a third piece.
- Multiple same-side rooks: select the intended outermost rook for K/Q rights, preserve this through FEN round trips and coordinate/SAN queries.
- Independent `O-O`/`O-O-O` assertions and a search PV containing castling in king-to-rook coordinates.
- Exercise all 960 starting layouts as valid Rust FEN inputs, then representative cleared castling arrangements with independent expected moves. Starting-layout generation outside Rust is out of scope.

The parser currently reads KQkq castling rights. File-letter castling FEN support should be specified before writing acceptance tests for it; do not silently assume this is supported.

### 4. King of the Hill interactions — medium priority

- Capture onto each hill square wins if legal and resets the halfmove clock; at halfmove 99 a capture onto the hill should therefore win instead of drawing.
- A non-king piece entering a hill square does not win; squares adjacent to the four hill squares do not win.
- King entry cannot ignore a pin, defended captured piece or enemy king adjacency. Existing attacked-entry coverage is one rook fixture.
- Exempt K+B versus K, K+N versus K and same-colour-bishop endings from material draws, beyond the existing bare-kings example.
- Root search on an already-won position returns a terminal result with no best move/PV; both winning colours and both quiescence options. Also cover a hill win at the search horizon.
- Apply shared castling, promotion and en-passant fixtures under this variant, plus mate at halfmove 100.
- Repetition-before-hill ordering is present in the implementation but not directly tested. A third occurrence of a hill position is unreachable through ordinary play because the first entry ends play; do not manufacture an impossible public-API game merely to cover that branch.

### 5. Rust boundary and notation guards — medium priority

- Bridge variant IDs 0/1/2/3 and unknown IDs; ensure Chess 960 and Hill do not silently become Standard.
- Bridge game-state mappings for HillWin and DuckStalemateWin, terminal empty move lists, and terminal history replay.
- Independent expected SAN for Chess 960 castling, Duck capture/promotion/king capture (no orthodox check suffix), and Hill winning moves according to the intended notation contract.
- SAN disambiguation by rank and by both rank/file, and ignore pinned pseudo-legal alternatives. Existing disambiguation only asserts file disambiguation.
- Variant-aware invalid FEN cases: extra/missing kings, illegal pawn ranks, non-moving king in check, invalid castling claims and counters; distinguish Duck's allowed missing captured king from malformed input.

## Complete current test inventory

Every Rust `#[test]` function is listed, including supporting tests and the ignored benchmark.

### `native/rust/bridge/src/lib.rs` (8 tests)

- [`variant_ids_preserve_the_requested_rules_and_reject_unknown_ids`](../native/rust/bridge/src/lib.rs#L650)
- [`variant_terminal_states_survive_history_replay_and_have_no_turns`](../native/rust/bridge/src/lib.rs#L673)
- [`legal_turns_pair_each_move_with_its_san`](../native/rust/bridge/src/lib.rs#L716)
- [`apply_move_reports_the_next_positions_turns`](../native/rust/bridge/src/lib.rs#L723)
- [`owned_replay_matches_stateless_moves_and_preserves_failed_moves`](../native/rust/bridge/src/lib.rs#L730)
- [`owned_duck_replay_keeps_previews_detached`](../native/rust/bridge/src/lib.rs#L759)
- [`windowed_history_keeps_repetition_detection`](../native/rust/bridge/src/lib.rs#L781)
- [`import_cost`](../native/rust/bridge/src/lib.rs#L822) — ignored benchmark

### `native/rust/core/src/position.rs` (1 tests)

- [`incremental_key_matches_a_key_built_from_scratch`](../native/rust/core/src/position.rs#L1268)

### `native/rust/core/src/search.rs` (4 tests)

- [`chess960_castling_search_branch_produces_a_replayable_principal_variation`](../native/rust/core/src/search.rs#L533)
- [`quiescence_detects_mate_at_depth_limit`](../native/rust/core/src/search.rs#L571)
- [`quiescence_detects_mate_after_quiet_check_evasions_run_out`](../native/rust/core/src/search.rs#L579)
- [`quiescence_searches_quiet_check_evasions`](../native/rust/core/src/search.rs#L587)

### `native/rust/core/tests/board_queries.rs` (10 tests)

- [`piece_at_reports_fen_characters_and_empty_squares`](../native/rust/core/tests/board_queries.rs#L5)
- [`piece_at_reports_the_duck`](../native/rust/core/tests/board_queries.rs#L14)
- [`castling_rights_reflect_the_fen_and_are_lost_after_a_king_move`](../native/rust/core/tests/board_queries.rs#L20)
- [`attackers_lists_every_square_of_the_given_colour_attacking_the_target`](../native/rust/core/tests/board_queries.rs#L30)
- [`captured_counts_pieces_missing_from_the_opening_set`](../native/rust/core/tests/board_queries.rs#L43)
- [`halfmove_clock_and_repetition_count_track_the_draw_rules`](../native/rust/core/tests/board_queries.rs#L50)
- [`san_covers_captures_promotion_castling_and_mate`](../native/rust/core/tests/board_queries.rs#L65)
- [`san_for_a_duck_turn_includes_the_duck_placement`](../native/rust/core/tests/board_queries.rs#L91)
- [`san_disambiguates_identical_pieces_that_share_a_destination`](../native/rust/core/tests/board_queries.rs#L99)
- [`legal_piece_moves_with_san_match_individual_queries`](../native/rust/core/tests/board_queries.rs#L106)

### `native/rust/core/tests/chess960_rules.rs` (9 tests)

- [`overlapping_castles_work_for_both_wings_and_colours`](../native/rust/core/tests/chess960_rules.rs#L39)
- [`castling_rejects_rook_path_blockers_and_revealed_attacks`](../native/rust/core/tests/chess960_rules.rs#L81)
- [`king_and_tracked_rook_rights_never_return`](../native/rust/core/tests/chess960_rules.rs#L104)
- [`only_original_back_rank_rooks_control_rights`](../native/rust/core/tests/chess960_rules.rs#L131)
- [`outermost_rook_is_tracked_across_fen_round_trips`](../native/rust/core/tests/chess960_rules.rs#L176)
- [`all_960_starting_arrangements_parse_with_four_castling_rights`](../native/rust/core/tests/chess960_rules.rs#L193)
- [`replacement_rook_does_not_restore_a_lost_right`](../native/rust/core/tests/chess960_rules.rs#L238)
- [`search_replays_chess960_castling_history_and_returns_a_legal_pv`](../native/rust/core/tests/chess960_rules.rs#L250)
- [`promoted_rook_cannot_replace_a_captured_castling_rook`](../native/rust/core/tests/chess960_rules.rs#L279)

### `native/rust/core/tests/duck.rs` (11 tests)

- [`castling_can_start_on_or_cross_an_attacked_square`](../native/rust/core/tests/duck.rs#L5)
- [`castling_still_requires_an_empty_path`](../native/rust/core/tests/duck.rs#L21)
- [`complete_turns_round_trip_and_incomplete_input_is_atomic`](../native/rust/core/tests/duck.rs#L30)
- [`promotion_and_king_capture_are_complete_turns`](../native/rust/core/tests/duck.rs#L54)
- [`search_returns_replayable_full_turns_and_terminal_results`](../native/rust/core/tests/duck.rs#L68)
- [`request_omits_only_a_legal_unfinished_trailing_piece_move`](../native/rust/core/tests/duck.rs#L107)
- [`duck_repetition_preserves_native_position_identity`](../native/rust/core/tests/duck.rs#L130)
- [`a_draw_ending_piece_move_is_not_discarded_as_pending`](../native/rust/core/tests/duck.rs#L141)
- [`malformed_duck_positions_are_rejected`](../native/rust/core/tests/duck.rs#L160)
- [`piece_preview_is_detached_and_complete_turns_still_require_placement`](../native/rust/core/tests/duck.rs#L173)
- [`a_side_without_moves_wins`](../native/rust/core/tests/duck.rs#L193)

### `native/rust/core/tests/duck_rules.rs` (10 tests)

- [`attacks_and_adjacent_kings_do_not_restrict_duck_moves`](../native/rust/core/tests/duck_rules.rs#L28)
- [`duck_is_an_uncapturable_blocker_for_every_piece`](../native/rust/core/tests/duck_rules.rs#L43)
- [`en_passant_and_vacated_squares_accept_duck_placement`](../native/rust/core/tests/duck_rules.rs#L83)
- [`king_captures_end_immediately_for_both_colours_and_promotions`](../native/rust/core/tests/duck_rules.rs#L111)
- [`final_duck_placement_decides_stalemate_for_either_colour`](../native/rust/core/tests/duck_rules.rs#L129)
- [`draw_ending_turns_omit_and_reject_placement_and_stop_play`](../native/rust/core/tests/duck_rules.rs#L147)
- [`search_uses_placement_to_block_an_immediate_king_capture`](../native/rust/core/tests/duck_rules.rs#L168)
- [`search_does_not_give_the_opponent_a_stalemate_win`](../native/rust/core/tests/duck_rules.rs#L192)
- [`search_returns_a_replayable_draw_ending_piece_move_without_placement`](../native/rust/core/tests/duck_rules.rs#L215)
- [`search_returns_a_replayable_repetition_ending_piece_move_without_placement`](../native/rust/core/tests/duck_rules.rs#L236)

### `native/rust/core/tests/king_of_the_hill.rs` (8 tests)

- [`either_king_wins_on_each_hill_square_and_play_stops`](../native/rust/core/tests/king_of_the_hill.rs#L10)
- [`hill_does_not_allow_moving_into_check`](../native/rust/core/tests/king_of_the_hill.rs#L29)
- [`fifty_move_draw_takes_precedence_over_hill`](../native/rust/core/tests/king_of_the_hill.rs#L37)
- [`bare_kings_can_play_but_repetition_still_draws`](../native/rust/core/tests/king_of_the_hill.rs#L45)
- [`checkmate_and_stalemate_still_end_games`](../native/rust/core/tests/king_of_the_hill.rs#L56)
- [`king_progress_is_rewarded_without_lone_king_mating_heuristics`](../native/rust/core/tests/king_of_the_hill.rs#L68)
- [`search_chooses_hill_win_and_observes_draw_precedence`](../native/rust/core/tests/king_of_the_hill.rs#L76)
- [`search_prevents_an_opponents_immediate_hill_win`](../native/rust/core/tests/king_of_the_hill.rs#L104)

### `native/rust/core/tests/notation_and_validation.rs` (3 tests)

- [`san_disambiguates_by_rank_and_both_coordinates_and_ignores_pins`](../native/rust/core/tests/notation_and_validation.rs#L4)
- [`duck_san_omits_check_and_mate_suffixes`](../native/rust/core/tests/notation_and_validation.rs#L16)
- [`invalid_fen_is_rejected_according_to_variant_king_rules`](../native/rust/core/tests/notation_and_validation.rs#L29)

### `native/rust/core/tests/positions.rs` (31 tests)

- [`dead_positions_draw_in_standard_and_chess960`](../native/rust/core/tests/positions.rs#L5)
- [`minor_piece_endings_with_possible_mates_remain_playable`](../native/rust/core/tests/positions.rs#L26)
- [`duck_kings_are_not_a_material_draw`](../native/rust/core/tests/positions.rs#L44)
- [`owned_position_replays_coordinate_history`](../native/rust/core/tests/positions.rs#L49)
- [`symmetric_initial_evaluation`](../native/rust/core/tests/positions.rs#L61)
- [`standard_move_generation_matches_perft`](../native/rust/core/tests/positions.rs#L80)
- [`special_moves_and_repetition_are_available_without_search`](../native/rust/core/tests/positions.rs#L85)
- [`invalid_requests_do_not_change_position`](../native/rust/core/tests/positions.rs#L105)
- [`repetition_distinguishes_a_legal_en_passant_option`](../native/rust/core/tests/positions.rs#L122)
- [`captured_rook_loses_castling_rights`](../native/rust/core/tests/positions.rs#L146)
- [`chess960_castling_coordinates_are_king_to_rook`](../native/rust/core/tests/positions.rs#L154)
- [`chess960_kingside_castle_relocates_the_tracked_rook`](../native/rust/core/tests/positions.rs#L165)
- [`chess960_castle_where_the_rook_already_sits_on_its_destination`](../native/rust/core/tests/positions.rs#L177)
- [`chess960_castle_where_the_king_already_sits_on_its_destination`](../native/rust/core/tests/positions.rs#L190)
- [`chess960_castle_swaps_an_adjacent_king_and_rook`](../native/rust/core/tests/positions.rs#L203)
- [`chess960_tracks_independent_rook_columns_per_colour`](../native/rust/core/tests/positions.rs#L214)
- [`chess960_rights_do_not_restore_once_the_rook_returns`](../native/rust/core/tests/positions.rs#L233)
- [`chess960_fen_drops_a_claimed_right_with_no_matching_rook`](../native/rust/core/tests/positions.rs#L245)
- [`standard_queenside_castle_only_requires_the_kings_own_transit_to_be_safe`](../native/rust/core/tests/positions.rs#L254)
- [`chess960_queenside_castle_only_requires_the_kings_own_transit_to_be_safe`](../native/rust/core/tests/positions.rs#L264)
- [`rook_leaving_a_non_back_rank_square_keeps_castling_rights`](../native/rust/core/tests/positions.rs#L271)
- [`chess960_castle_may_not_jump_over_a_piece_beyond_the_rook`](../native/rust/core/tests/positions.rs#L278)
- [`chess960_castle_may_not_pass_through_an_attacked_rook_square`](../native/rust/core/tests/positions.rs#L286)
- [`chess960_castle_ignores_attacks_on_squares_the_king_never_crosses`](../native/rust/core/tests/positions.rs#L294)
- [`fen_keeps_its_fullmove_number`](../native/rust/core/tests/positions.rs#L302)
- [`checkmate_on_the_hundredth_half_move_is_not_a_fifty_move_draw`](../native/rust/core/tests/positions.rs#L311)
- [`fifty_move_rule_applies_past_the_hundredth_half_move`](../native/rust/core/tests/positions.rs#L319)
- [`hash_key_is_shared_by_transpositions`](../native/rust/core/tests/positions.rs#L331)
- [`hash_key_distinguishes_side_to_move_and_castling_rights`](../native/rust/core/tests/positions.rs#L338)
- [`hash_key_counts_en_passant_only_when_a_pawn_can_capture`](../native/rust/core/tests/positions.rs#L356)
- [`hash_key_ignores_the_duck_square`](../native/rust/core/tests/positions.rs#L369)

### `native/rust/core/tests/search.rs` (8 tests)

- [`fixed_depth_matches_native_starting_position`](../native/rust/core/tests/search.rs#L12)
- [`timed_progress_can_stop_and_retains_the_completed_iteration`](../native/rust/core/tests/search.rs#L30)
- [`completed_principal_variation_replays_legally`](../native/rust/core/tests/search.rs#L67)
- [`interruption_before_depth_completion_has_no_fabricated_move_or_score`](../native/rust/core/tests/search.rs#L87)
- [`a_history_reaching_threefold_repetition_finishes_as_an_immediate_draw`](../native/rust/core/tests/search.rs#L111)
- [`the_same_position_reached_without_a_third_repetition_is_not_a_draw`](../native/rust/core/tests/search.rs#L138)
- [`terminal_search_finishes_without_progress_or_mate_distance`](../native/rust/core/tests/search.rs#L155)
- [`timed_search_and_independent_control_finish_without_shared_cancellation`](../native/rust/core/tests/search.rs#L185)

### `native/rust/core/tests/variant_rules.rs` (11 tests)

- [`reference_perft_exercises_castling_en_passant_and_promotions`](../native/rust/core/tests/variant_rules.rs#L45)
- [`checks_pins_and_king_safety_restrict_orthodox_moves`](../native/rust/core/tests/variant_rules.rs#L72)
- [`en_passant_removes_the_pawn_expires_and_obeys_king_safety`](../native/rust/core/tests/variant_rules.rs#L95)
- [`all_promotions_are_generated_and_applied_for_both_colours`](../native/rust/core/tests/variant_rules.rs#L130)
- [`orthodox_castling_obeys_attacks_and_blockers`](../native/rust/core/tests/variant_rules.rs#L168)
- [`draw_boundaries_and_irreversible_moves_are_variant_independent`](../native/rust/core/tests/variant_rules.rs#L235)
- [`orthodox_terminal_states_have_the_expected_meaning`](../native/rust/core/tests/variant_rules.rs#L263)
- [`hill_capture_resets_draw_clock_and_only_kings_win`](../native/rust/core/tests/variant_rules.rs#L280)
- [`hill_terminal_roots_and_depth_one_wins_stop_search`](../native/rust/core/tests/variant_rules.rs#L315)
- [`two_occurrences_are_playable_and_castling_rights_change_repetition_identity`](../native/rust/core/tests/variant_rules.rs#L354)
- [`defended_hill_captures_and_enemy_king_adjacency_remain_illegal`](../native/rust/core/tests/variant_rules.rs#L385)
