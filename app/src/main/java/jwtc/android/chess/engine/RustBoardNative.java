package jwtc.android.chess.engine;

import jwtc.chess.Variant;

/**
 * Board-operations JNI adapter. Snapshot calls rebuild from a starting FEN and coordinate history;
 * scoped replay handles retain a position while loading PGN.
 */
public final class RustBoardNative {
    static {
        System.loadLibrary("rust-chess-jni");
    }

    static native long createReplay(int variant, String fen, String[] history);

    static native void destroyReplay(long handle);

    static native RustBoardResult replayTurns(long handle);

    static native RustBoardResult replayMove(long handle, String move);

    private static native RustBoardResult query(int variant, String fen, String[] history);

    static native RustBoardResult replaySan(int variant, String fen, String tokens);

    private static native RustBoardResult legalMoves(int variant, String fen, String[] history);

    private static native RustBoardResult legalTurns(int variant, String fen, String[] history);

    private static native RustBoardResult applyMove(
            int variant, String fen, String[] history, String move);

    private static native RustBoardResult san(
            int variant, String fen, String[] history, String move);

    private static native RustBoardResult pieceAt(
            int variant, String fen, String[] history, String square);

    private static native RustBoardResult attackers(
            int variant, String fen, String[] history, boolean byWhite, String square);

    public static RustBoardResult query(Variant variant, String fen, String[] history) {
        return query(variant.ordinal(), fen, history);
    }

    public static RustBoardResult legalMoves(Variant variant, String fen, String[] history) {
        return legalMoves(variant.ordinal(), fen, history);
    }

    /**
     * Legal moves with their SAN in {@link RustBoardResult#turns}, from a single replay of the
     * history.
     */
    public static RustBoardResult legalTurns(Variant variant, String fen, String[] history) {
        return legalTurns(variant.ordinal(), fen, history);
    }

    /**
     * A Duck piece-only move returns a detached preview; record history only after placement.
     * Otherwise {@link RustBoardResult#turns} holds the resulting position's legal moves.
     */
    public static RustBoardResult applyMove(
            Variant variant, String fen, String[] history, String move) {
        return applyMove(variant.ordinal(), fen, history, move);
    }

    public static RustBoardResult san(Variant variant, String fen, String[] history, String move) {
        return san(variant.ordinal(), fen, history, move);
    }

    public static RustBoardResult pieceAt(
            Variant variant, String fen, String[] history, String square) {
        return pieceAt(variant.ordinal(), fen, history, square);
    }

    public static RustBoardResult attackers(
            Variant variant, String fen, String[] history, boolean byWhite, String square) {
        return attackers(variant.ordinal(), fen, history, byWhite, square);
    }

    private RustBoardNative() {}
}
