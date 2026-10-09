# Chess for Android

A free, open-source chess app (no ads). It plays chess locally against an engine, online against humans on Lichess and FICS, and offers puzzles, practice, analysis and a local game database. This file is the project's domain glossary: use these terms in code, issues and docs.

## Language

### Games

**Game**:
A move sequence (with optional variations) from a Starting Position, plus PGN tags and a result, whatever its source: local play, Online Game, import or Game Database. Puzzles, practice and analysis are Modes that operate on a Game, not kinds of Game.
_Avoid_: match (FICS term only), session

**Position**:
Any board state during a Game, identified by a FEN.

**Starting Position**:
The Position a Game begins from: the standard setup, a Chess 960 arrangement or a custom Setup.

**Variant**:
The rule set a Game is played under: Standard, Chess 960 or Duck chess. Duck chess is experimental and supported only by the Local Engine (not OEX Engines, not Lichess).
_Avoid_: Chess960 (spell it "Chess 960")

**PGN**:
The only interchange format. A saved Game *is* its PGN, including tags and variations.

**ECO Opening**:
A named opening line classified by ECO code, shown by the opening explorer. User-facing label: "Opening".

### Modes

**Mode**:
What the user is doing with a Game: Play, Analyze, Mate-in-Two Puzzle, Practice, Setup, Online play.
_Avoid_: using "Mode" for engine behaviour (see Search Purpose)

**Setup**:
The Mode where the user builds a Starting Position by hand.

**Analysis**:
The Mode where an Engine evaluates each move of a Game and shows Move Classifications.

**Mate-in-Two Puzzle**:
A position from the bundled local "Mate in two" collection with a fixed solution, shown in `PuzzleActivity`. Local database only.

**Practice Position**:
A position from the local database where any move sequence that leads to mate counts as solved, shown in `PracticeActivity`.

**Lichess Puzzle**:
A puzzle fetched from the Lichess API, separate from Mate-in-Two Puzzles and Practice Positions. Can be **Rated** or unrated.
_Avoid_: bare "puzzle" without saying which kind

### Engines

**Engine**:
A move-search implementation behind `EngineApi`: the **Local Engine** (bundled, native) or an **OEX Engine** (third-party, via the Open Exchange Protocol).

**Engine session**:
What a Mode holds to use Engines: it picks the Engine for that Mode (Play follows the user's choice and Variant, Analysis needs an Engine that supports it, Mate-in-Two Puzzles and Practice Positions always use the Local Engine), owns its lifecycle and listener registration, and takes the search limit per request. Not a Lichess Session.
_Avoid_: bare "session"

**Search Purpose**:
Why an Engine is searching: PLAY (choose a move to play) or ANALYSIS (evaluate a position). `EngineApi.SearchPurpose` in code.
_Avoid_: Mode

**Evaluation**:
An Engine's score for a Position, either centipawns or mate-in-N, always from White's perspective.

**Move Classification**:
The label (Best, Excellent, Okay, Inaccuracy, Mistake, Blunder, Forced) given to a played move by comparing the Evaluation before and after it. `MoveClassifier.Feedback` is the enum name, not the domain term.
_Avoid_: feedback

### Playing against others

**Online Game**:
A Game played against a remote human through a Server.
_Avoid_: live game

**Server**:
Lichess or FICS.

**Hotspot Game**:
A Game played between two devices directly over a local network, with no Server. Not an Online Game.

**Time Control**:
The base time plus increment agreed for a Game, e.g. 5+3. Same term on Lichess and FICS.

**Correspondence Game**:
A Lichess Online Game with a days-per-move Time Control, played over a long period rather than in one sitting. Lichess only.

**Clock**:
The running countdown for each side. On Online Games the Server is authoritative and the local Clock only mirrors it between updates; on local Games `LocalClockApi` is authoritative.

### Lichess

**Lichess Account**:
The user's logged-in identity, proven by an OAuth2 (PKCE) token. The token may be absent.

**Rated**:
A flag saying whether an outcome counts toward the Lichess Account's rating. For Lichess Puzzles, rated requests send the token and unrated requests send none. Also applies to Challenges and Seeks.

**Rating**:
Always a Lichess rating. The app has no local ratings.

**Challenge**, **Seek**, **Swiss**:
Lichess terms for a request to a specific player, a lobby request, and a Swiss tournament. FICS uses different words.

**Lichess Session**:
The connection of one screen to `LichessService`, which owns the single `LichessApi`. Not a user-visible concept.
_Avoid_: session alone

### Storage

**Game Database**:
The on-device store of saved Games, searchable and exportable as PGN. It shares the SQLite database with the puzzle and practice collections but uses a separate namespace and never shows them.

**Import**:
Loading Games from a PGN file into the Game Database.

### Presentation

**Board Theme**:
The combination of color scheme and piece set.

## Principles

- **Accessibility is first-class.** Any new Mode must work with TalkBack and speak its moves.
- **E-ink and Chromecast** are alternative presentations of the same board, not separate features.
- **Child-Safety Filter.** FICS chat lines are filtered out of the response input, so the end user never sees them. No feature may surface FICS chat.

## Flagged ambiguities

- "puzzle" is used for three different things: Mate-in-Two Puzzle, Practice Position and Lichess Puzzle. Always say which.
- "Mode" meant both user activity and engine search purpose in code. Mode is now the user-facing term only.
- "game" also names a Lichess `Game` model and `LichessGameActivity`; these are Online Game representations, not a separate concept.
