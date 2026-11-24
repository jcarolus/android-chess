package jwtc.chess;

import java.util.Locale;
import jwtc.chess.board.BoardPosition;

/**
 * The rule set a Game is played under.
 *
 * <p>Declaration order is the variant code the Rust board receives through JNI, and Hotspot peers
 * exchange {@link #name()}, so neither may change.
 */
public enum Variant {
    STANDARD(null),
    CHESS_960("Chess960"),
    DUCK("Duck"),
    KING_OF_THE_HILL("King of the Hill");

    private final String pgnTag;

    Variant(String pgnTag) {
        this.pgnTag = pgnTag;
    }

    /** The PGN {@code Variant} tag value to write, or null when Standard needs no tag. */
    public String pgnTag() {
        return pgnTag;
    }

    /** OEX Engines only play Standard and Chess 960; other Variants need the Local Engine. */
    public boolean supportsOexEngine() {
        return this == STANDARD || this == CHESS_960;
    }

    /** The opening book only holds lines from the standard setup. */
    public boolean hasOpeningBook() {
        return this == STANDARD;
    }

    /** Text and dictated move entry has no notation for placing the Duck. */
    public boolean supportsMoveEntry() {
        return this != DUCK;
    }

    /**
     * Returns the Variant a PGN {@code Variant} tag names, or null when the tag is absent, names
     * Standard or "From Position", or names a rule set the app does not play. A null result means
     * the Starting Position decides; see {@link #infer(BoardPosition)}.
     */
    public static Variant fromPgnTag(String tag) {
        if (tag == null) {
            return null;
        }
        switch (tag.trim().toLowerCase(Locale.ROOT)) {
            case "chess960":
            case "chess 960":
            case "fischerandom":
            case "fischer random":
                return CHESS_960;
            case "duck":
                return DUCK;
            case "king of the hill":
            case "kingofthehill":
                return KING_OF_THE_HILL;
            default:
                return null;
        }
    }

    /** Resolves the tag, falling back to what the Starting Position implies. */
    public static Variant of(String tag, BoardPosition startingPosition) {
        Variant named = fromPgnTag(tag);
        return named != null ? named : infer(startingPosition);
    }

    /**
     * The Variant a Starting Position implies on its own: a Duck square means Duck, Chess 960
     * castling rights or piece placement mean Chess 960. King of the Hill cannot be told from a
     * Position. A null Position is the standard setup.
     */
    public static Variant infer(BoardPosition startingPosition) {
        if (startingPosition == null) {
            return STANDARD;
        }
        if (startingPosition.duck() >= 0) {
            return DUCK;
        }
        return startingPosition.chess960() ? CHESS_960 : STANDARD;
    }
}
