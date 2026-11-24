package jwtc.android.chess.moveintake;

import jwtc.android.chess.services.GameApi;

/**
 * Move intake for Play. Against the Engine the user only moves their own side; against another
 * person on the same device either side may move. Moves played from earlier in the Game replace the
 * moves that follow, so the user confirms that first.
 */
public final class PlayMoveIntake implements MoveIntake {
    private final GameApi game;
    private final boolean againstEngine;
    private final int userSide;

    public PlayMoveIntake(GameApi game, boolean againstEngine, int userSide) {
        this.game = game;
        this.againstEngine = againstEngine;
        this.userSide = userSide;
    }

    @Override
    public MoveOutcome offer(MoveCandidate candidate) {
        MoveOutcome outcome =
                decide(candidate, BoardFacts.of(game, candidate), againstEngine, userSide);
        return outcome == MoveOutcome.PLAYED ? DefaultMoveIntake.play(game, candidate) : outcome;
    }

    public static MoveOutcome decide(
            MoveCandidate candidate, BoardFacts facts, boolean againstEngine, int userSide) {
        if (!facts.ended && againstEngine && facts.sideOnTurn() != userSide) {
            return MoveOutcome.rejected(MoveOutcome.Reason.NOT_YOUR_TURN);
        }
        return DefaultMoveIntake.decide(candidate, facts, true);
    }
}
