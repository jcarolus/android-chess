package jwtc.android.chess.moveintake;

import jwtc.android.chess.services.GameApi;

/**
 * Move intake for Modes without rules of their own, such as Analyze: any legal move is played on
 * the {@link GameApi}, and moves played from earlier in the Game become variations without asking.
 * Other adapters apply their own rules first and then reuse {@link #decide} and {@link #play}.
 */
public final class DefaultMoveIntake implements MoveIntake {
    private final GameApi game;

    public DefaultMoveIntake(GameApi game) {
        this.game = game;
    }

    @Override
    public MoveOutcome offer(MoveCandidate candidate) {
        MoveOutcome outcome = decide(candidate, BoardFacts.of(game, candidate), false);
        return outcome == MoveOutcome.PLAYED ? play(game, candidate) : outcome;
    }

    /**
     * The questions the board itself raises, in the order the user meets them: overwriting the
     * moves that follow (only when {@code confirmOverwrite}), then the castle choice, then the
     * promotion piece. The Duck moves only after a piece has moved, and then nothing else may move
     * until it is placed; placing it asks nothing. Here {@link MoveOutcome#PLAYED} means the move
     * may be played.
     */
    public static MoveOutcome decide(
            MoveCandidate candidate, BoardFacts facts, boolean confirmOverwrite) {
        if (facts.ended) {
            return MoveOutcome.rejected(MoveOutcome.Reason.ENDED);
        }
        if (facts.isDuckMove(candidate) != facts.duckPending) {
            return MoveOutcome.rejected(MoveOutcome.Reason.NOT_YOUR_TURN);
        }
        if (facts.duckPending) {
            return MoveOutcome.PLAYED;
        }
        if (confirmOverwrite && facts.continuationToOverwrite && !candidate.overwriteConfirmed) {
            return MoveOutcome.CONFIRM_OVERWRITE;
        }
        if (facts.ambiguousCastle && candidate.castle == MoveCandidate.Castle.UNASKED) {
            return MoveOutcome.NEEDS_CASTLE_CHOICE;
        }
        if (candidate.castle == MoveCandidate.Castle.CASTLE) {
            return MoveOutcome.PLAYED;
        }
        // A king that stays on its square only moves by castling.
        if (candidate.from == candidate.to) {
            return MoveOutcome.rejected(MoveOutcome.Reason.ILLEGAL);
        }
        if (facts.promotion && candidate.promotion == MoveCandidate.NO_PROMOTION) {
            return MoveOutcome.NEEDS_PROMOTION;
        }
        return MoveOutcome.PLAYED;
    }

    /** Applies a candidate {@link #decide} allowed to the Game. */
    public static MoveOutcome play(GameApi game, MoveCandidate candidate) {
        boolean applied;
        if (candidate.from == game.getDuckPos()) {
            applied = game.requestDuckMove(candidate.to);
        } else if (candidate.castle == MoveCandidate.Castle.CASTLE) {
            applied = game.requestMoveCastle(candidate.from, candidate.to);
        } else {
            if (candidate.promotion != MoveCandidate.NO_PROMOTION) {
                game.setPromo(candidate.promotion);
            }
            applied = game.requestMove(candidate.from, candidate.to);
        }
        return applied ? MoveOutcome.PLAYED : MoveOutcome.rejected(MoveOutcome.Reason.ILLEGAL);
    }
}
