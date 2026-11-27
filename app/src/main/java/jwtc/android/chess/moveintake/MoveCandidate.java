package jwtc.android.chess.moveintake;

import jwtc.chess.Move;

/**
 * A move the user offers to a Mode's {@link MoveIntake}, together with the answers the user has
 * given so far. Answering a question offers a copy with that one answer filled in.
 */
public final class MoveCandidate {
    /** No promotion piece has been chosen. */
    public static final int NO_PROMOTION = -1;

    /** The answer to "castle?" when the king's move could also be an ordinary king move. */
    public enum Castle {
        UNASKED,
        CASTLE,
        ORDINARY_MOVE
    }

    public final int from;
    public final int to;
    public final int promotion;
    public final Castle castle;
    public final boolean overwriteConfirmed;
    public final boolean moveConfirmed;

    private MoveCandidate(
            int from,
            int to,
            int promotion,
            Castle castle,
            boolean overwriteConfirmed,
            boolean moveConfirmed) {
        this.from = from;
        this.to = to;
        this.promotion = promotion;
        this.castle = castle;
        this.overwriteConfirmed = overwriteConfirmed;
        this.moveConfirmed = moveConfirmed;
    }

    public static MoveCandidate of(int from, int to) {
        return new MoveCandidate(from, to, NO_PROMOTION, Castle.UNASKED, false, false);
    }

    /**
     * A move already resolved against the board, such as one picked from a list: it answers the
     * promotion and castle questions itself.
     */
    public static MoveCandidate resolved(int move) {
        boolean castles = Move.isOO(move) || Move.isOOO(move);
        return new MoveCandidate(
                Move.getFrom(move),
                Move.getTo(move),
                Move.isPromotionMove(move) ? Move.getPromotionPiece(move) : NO_PROMOTION,
                castles ? Castle.CASTLE : Castle.ORDINARY_MOVE,
                false,
                false);
    }

    public MoveCandidate withPromotion(int piece) {
        return new MoveCandidate(from, to, piece, castle, overwriteConfirmed, moveConfirmed);
    }

    public MoveCandidate withCastle(boolean castles) {
        return new MoveCandidate(
                from,
                to,
                promotion,
                castles ? Castle.CASTLE : Castle.ORDINARY_MOVE,
                overwriteConfirmed,
                moveConfirmed);
    }

    public MoveCandidate withOverwriteConfirmed() {
        return new MoveCandidate(from, to, promotion, castle, true, moveConfirmed);
    }

    public MoveCandidate withMoveConfirmed() {
        return new MoveCandidate(from, to, promotion, castle, overwriteConfirmed, true);
    }

    @Override
    public String toString() {
        return "MoveCandidate{"
                + from
                + "->"
                + to
                + ", promotion="
                + promotion
                + ", castle="
                + castle
                + ", overwriteConfirmed="
                + overwriteConfirmed
                + ", moveConfirmed="
                + moveConfirmed
                + "}";
    }
}
