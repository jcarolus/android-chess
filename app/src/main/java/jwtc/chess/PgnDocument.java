package jwtc.chess;

import java.util.List;
import java.util.Map;

/**
 * The structure of a PGN text, independent of any board: tags, comments, and a tree of SAN moves. Moves are only
 * checked for shape, never for legality. A move's variations are alternatives to that move, each a line starting
 * at the same position the move was played from.
 */
public final class PgnDocument {
    public static final class Move {
        /** Move text without annotation glyphs, as written in the PGN (castling may use zeros). */
        public final String san;
        /** Character position of the move token, for error reporting. */
        public final int offset;
        /** Annotation glyph first (!, ?, ...), then explicit $n codes in order. */
        public final List<Integer> nags;
        public final String comment;
        /** Comment between the opening parenthesis and the first move of a variation. */
        public final String leadingComment;
        public final List<List<Move>> variations;

        Move(String san, int offset, List<Integer> nags, String comment, String leadingComment,
             List<List<Move>> variations) {
            this.san = san;
            this.offset = offset;
            this.nags = nags;
            this.comment = comment;
            this.leadingComment = leadingComment;
            this.variations = variations;
        }
    }

    /** Insertion-ordered, unescaped tag values. */
    public final Map<String, String> tags;
    /** Comment before the first move. */
    public final String rootComment;
    public final List<Move> mainLine;
    /** The termination marker from the movetext, or else the Result tag; null when the PGN states neither. */
    public final String result;

    PgnDocument(Map<String, String> tags, String rootComment, List<Move> mainLine, String result) {
        this.tags = tags;
        this.rootComment = rootComment;
        this.mainLine = mainLine;
        this.result = result;
    }
}
