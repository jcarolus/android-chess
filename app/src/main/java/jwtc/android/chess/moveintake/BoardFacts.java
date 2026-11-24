package jwtc.android.chess.moveintake;

import jwtc.android.chess.services.GameApi;

/**
 * What the board says about one {@link MoveCandidate}, read once before deciding so the decision
 * itself needs no board.
 */
public final class BoardFacts {
    /** An ongoing Game where the candidate needs no question answered. */
    public static final BoardFacts PLAYABLE = new BoardFacts(false, -1, false, false, false, false);

    public final boolean ended;

    /** The Duck's square, or -1 when the Game has no Duck. */
    public final int duckSquare;

    /** A piece has moved and the Duck still has to be placed to finish the turn. */
    public final boolean duckPending;

    public final boolean promotion;

    /** The king's move could be read as castling or as an ordinary king move. */
    public final boolean ambiguousCastle;

    /** Playing a move here would replace moves that follow the current one. */
    public final boolean continuationToOverwrite;

    private BoardFacts(
            boolean ended,
            int duckSquare,
            boolean duckPending,
            boolean promotion,
            boolean ambiguousCastle,
            boolean continuationToOverwrite) {
        this.ended = ended;
        this.duckSquare = duckSquare;
        this.duckPending = duckPending;
        this.promotion = promotion;
        this.ambiguousCastle = ambiguousCastle;
        this.continuationToOverwrite = continuationToOverwrite;
    }

    public static BoardFacts of(GameApi game, MoveCandidate candidate) {
        return new BoardFacts(
                game.isEnded(),
                game.getDuckPos(),
                game.hasPendingDuckMove(),
                game.isPromotionMove(candidate.from, candidate.to),
                game.isAmbiguousCastle(candidate.from, candidate.to) != 0,
                game.hasContinuationToOverwrite());
    }

    public boolean isDuckMove(MoveCandidate candidate) {
        return duckSquare >= 0 && candidate.from == duckSquare;
    }

    public BoardFacts withEnded() {
        return new BoardFacts(
                true, duckSquare, duckPending, promotion, ambiguousCastle, continuationToOverwrite);
    }

    public BoardFacts withDuck(int square, boolean pending) {
        return new BoardFacts(
                ended, square, pending, promotion, ambiguousCastle, continuationToOverwrite);
    }

    public BoardFacts withPromotion() {
        return new BoardFacts(
                ended, duckSquare, duckPending, true, ambiguousCastle, continuationToOverwrite);
    }

    public BoardFacts withAmbiguousCastle() {
        return new BoardFacts(
                ended, duckSquare, duckPending, promotion, true, continuationToOverwrite);
    }

    public BoardFacts withContinuationToOverwrite() {
        return new BoardFacts(ended, duckSquare, duckPending, promotion, ambiguousCastle, true);
    }
}
