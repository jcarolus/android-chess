package jwtc.android.chess.matecollection;

import jwtc.chess.board.BoardConstants;

/** The solving and navigation rules of a Mate Collection, free of Android and the native board. */
final class MateRules {
    private static final int SOLUTION_PLY = 4;

    private MateRules() {
    }

    /**
     * Search depth in ply for the Engine's next reply. Each move already played brings the
     * mate one ply closer.
     */
    static int plyBudget(int numMoved) {
        return SOLUTION_PLY - numMoved;
    }

    /**
     * Whether the Engine's score proves the position is still won for the user. The Engine
     * scores from the side to move: mate when it is the user's turn, mated when it is the
     * opponent's.
     */
    static boolean isMateReply(int value, boolean usersTurn) {
        return value == BoardConstants.VALUATION_MATE * (usersTurn ? 1 : -1);
    }

    /** The saved position, or the first one when it is gone or past the end of the collection. */
    static int restore(int saved, int total) {
        return saved < 0 || saved >= total ? 0 : saved;
    }

    static boolean hasNext(int position, int total) {
        return position + 1 < total;
    }

    static int previous(int position) {
        return position > 0 ? position - 1 : 0;
    }
}
