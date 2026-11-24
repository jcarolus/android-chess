package jwtc.android.chess.engine;

import java.util.List;
import jwtc.chess.PgnDocument;
import jwtc.chess.Variant;
import jwtc.chess.board.BoardPosition;

/** Validates a parsed PGN in Rust when the caller only needs its final Position. */
public final class PgnReplay {
    private PgnReplay() {}

    public static final class StartingPosition {
        public final String fen;
        public final Variant variant;

        private StartingPosition(String fen, Variant variant) {
            this.fen = fen;
            this.variant = variant;
        }
    }

    /** Resolves PGN setup tags the same way when validating and when opening a Game. */
    public static StartingPosition startingPosition(PgnDocument document) {
        String tag = document.tags.get("Variant");
        String fen = document.tags.get("FEN");
        if (fen == null) {
            Variant named = Variant.fromPgnTag(tag);
            if (named == Variant.DUCK) {
                return new StartingPosition(BoardPosition.DUCK_START_FEN, Variant.DUCK);
            }
            return new StartingPosition(
                    BoardPosition.START_FEN,
                    named == Variant.KING_OF_THE_HILL
                            ? Variant.KING_OF_THE_HILL
                            : Variant.STANDARD);
        }

        BoardPosition position = new BoardPosition(fen);
        return new StartingPosition(position.fen(), Variant.of(tag, position));
    }

    /** Returns the main-line end after checking every variation for legal moves. */
    public static RustBoardResult validate(PgnDocument document) {
        StartingPosition start = startingPosition(document);
        StringBuilder tokens = new StringBuilder();
        appendLine(tokens, document.mainLine);
        return RustBoardNative.replaySan(start.variant.ordinal(), start.fen, tokens.toString());
    }

    private static void appendLine(StringBuilder tokens, List<PgnDocument.Move> line) {
        for (PgnDocument.Move move : line) {
            tokens.append(move.san).append(' ');
            for (List<PgnDocument.Move> variation : move.variations) {
                tokens.append("( ");
                appendLine(tokens, variation);
                tokens.append(") ");
            }
        }
    }
}
