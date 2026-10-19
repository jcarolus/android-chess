package jwtc.android.chess.engine;

/**
 * Stateless board-operations JNI adapter: every call rebuilds the position from a starting FEN
 * plus coordinate history and returns a result, with nothing persisted between calls.
 */
public final class RustBoardNative {
    static { System.loadLibrary("rust-chess-jni"); }

    private static native RustBoardResult query(int variant, String fen, String[] history);
    private static native RustBoardResult legalMoves(int variant, String fen, String[] history);
    private static native RustBoardResult legalTurns(int variant, String fen, String[] history);
    private static native RustBoardResult applyMove(int variant, String fen, String[] history, String move);
    private static native RustBoardResult san(int variant, String fen, String[] history, String move);
    private static native RustBoardResult pieceAt(int variant, String fen, String[] history, String square);
    private static native RustBoardResult attackers(int variant, String fen, String[] history, boolean byWhite, String square);

    public static RustBoardResult query(RustSearchRequest.Variant variant, String fen, String[] history) {
        return query(variant.ordinal(), fen, history);
    }

    public static RustBoardResult legalMoves(RustSearchRequest.Variant variant, String fen, String[] history) {
        return legalMoves(variant.ordinal(), fen, history);
    }

    /** Legal moves with their SAN in {@link RustBoardResult#turns}, from a single replay of the history. */
    public static RustBoardResult legalTurns(RustSearchRequest.Variant variant, String fen, String[] history) {
        return legalTurns(variant.ordinal(), fen, history);
    }

    /** A Duck piece-only move returns a detached preview; record history only after placement. Otherwise {@link RustBoardResult#turns} holds the resulting position's legal moves. */
    public static RustBoardResult applyMove(RustSearchRequest.Variant variant, String fen, String[] history, String move) {
        return applyMove(variant.ordinal(), fen, history, move);
    }

    public static RustBoardResult san(RustSearchRequest.Variant variant, String fen, String[] history, String move) {
        return san(variant.ordinal(), fen, history, move);
    }

    public static RustBoardResult pieceAt(RustSearchRequest.Variant variant, String fen, String[] history, String square) {
        return pieceAt(variant.ordinal(), fen, history, square);
    }

    public static RustBoardResult attackers(RustSearchRequest.Variant variant, String fen, String[] history, boolean byWhite, String square) {
        return attackers(variant.ordinal(), fen, history, byWhite, square);
    }

    private RustBoardNative() {}
}
