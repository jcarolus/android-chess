package jwtc.android.chess.engine;

import jwtc.chess.board.BoardConstants;

/**
 * Result of a stateless board-operations call. Each {@link RustBoardNative} method fills only the
 * fields its name promises; the rest are left at their zero/empty default.
 *
 * <p>{@code captured} holds pieces missing from each side's opening set, five entries per colour
 * ordered pawn/knight/bishop/rook/queen: indices 0-4 for Black, 5-9 for White.
 */
public final class RustBoardResult {
    public final boolean turnWhite;
    public final int gameState;
    public final boolean inCheck;
    public final long halfmoveClock;
    public final long repetitionCount;
    public final boolean castleWhiteKingside;
    public final boolean castleWhiteQueenside;
    public final boolean castleBlackKingside;
    public final boolean castleBlackQueenside;
    public final int[] captured;
    public final char piece;
    public final String fen;
    public final String san;
    public final String moves;
    public final String attackers;

    /** Space-separated {@code coordinate=san} pairs: the legal moves of the resulting position. */
    public final String turns;

    public RustBoardResult(
            boolean turnWhite,
            int gameState,
            boolean inCheck,
            long halfmoveClock,
            long repetitionCount,
            boolean castleWhiteKingside,
            boolean castleWhiteQueenside,
            boolean castleBlackKingside,
            boolean castleBlackQueenside,
            int[] captured,
            char piece,
            String fen,
            String san,
            String moves,
            String attackers,
            String turns) {
        this.turnWhite = turnWhite;
        this.gameState = gameState;
        this.inCheck = inCheck;
        this.halfmoveClock = halfmoveClock;
        this.repetitionCount = repetitionCount;
        this.castleWhiteKingside = castleWhiteKingside;
        this.castleWhiteQueenside = castleWhiteQueenside;
        this.castleBlackKingside = castleBlackKingside;
        this.castleBlackQueenside = castleBlackQueenside;
        this.captured = captured;
        this.piece = piece;
        this.fen = fen;
        this.san = san;
        this.moves = moves;
        this.attackers = attackers;
        this.turns = turns;
    }

    /**
     * {@link RustBoardResult#gameState}
     * (Play/Check/Mate/Stalemate/DrawMaterial/DrawFiftyMove/DrawRepetition/HillWin), indexed by its
     * ordinal, mapped to {@link BoardConstants}'s legacy numbering.
     */
    private static final int[] LEGACY_STATE_FOR_RUST_CODE = {
        BoardConstants.PLAY, BoardConstants.CHECK, BoardConstants.MATE, BoardConstants.STALEMATE,
        BoardConstants.DRAW_MATERIAL, BoardConstants.DRAW_50, BoardConstants.DRAW_REPEAT,
                BoardConstants.HILL_WIN,
    };

    public static int boardState(int rustGameStateCode) {
        return LEGACY_STATE_FOR_RUST_CODE[rustGameStateCode];
    }

    /** Splits {@link #moves} (or {@link #attackers}) on its separating spaces. */
    public static String[] words(String spaceSeparated) {
        return spaceSeparated.isEmpty() ? new String[0] : spaceSeparated.split(" ");
    }
}
