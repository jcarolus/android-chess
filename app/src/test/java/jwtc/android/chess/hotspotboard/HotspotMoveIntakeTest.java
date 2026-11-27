package jwtc.android.chess.hotspotboard;

import static jwtc.chess.board.BoardConstants.BLACK;
import static jwtc.chess.board.BoardConstants.WHITE;
import static org.junit.Assert.assertEquals;

import jwtc.android.chess.moveintake.BoardFacts;
import jwtc.android.chess.moveintake.MoveCandidate;
import jwtc.android.chess.moveintake.MoveOutcome;
import jwtc.android.chess.moveintake.MoveOutcome.Reason;
import org.junit.Test;

public class HotspotMoveIntakeTest {
    private static final int E2 = 52, E4 = 36, E1 = 60, D5 = 27;
    private static final MoveCandidate PAWN = MoveCandidate.of(E2, E4);
    private static final BoardFacts WHITE_TO_MOVE = BoardFacts.PLAYABLE.withSideToMove(WHITE);

    @Test
    public void theUserMovesTheirOwnSideInALiveSession() {
        assertEquals(
                MoveOutcome.PLAYED, HotspotMoveIntake.decide(PAWN, WHITE_TO_MOVE, true, WHITE));
        assertEquals(
                MoveOutcome.rejected(Reason.NOT_YOUR_TURN),
                HotspotMoveIntake.decide(PAWN, WHITE_TO_MOVE, true, BLACK));
    }

    @Test
    public void nothingMovesOutsideALiveSession() {
        assertEquals(
                MoveOutcome.rejected(Reason.NOT_READY),
                HotspotMoveIntake.decide(PAWN, WHITE_TO_MOVE, false, WHITE));
    }

    @Test
    public void theCastleChoiceFollowsTheSameRules() {
        BoardFacts ambiguous = WHITE_TO_MOVE.withAmbiguousCastle();
        MoveCandidate king = MoveCandidate.of(E1, E1);
        assertEquals(
                MoveOutcome.NEEDS_CASTLE_CHOICE,
                HotspotMoveIntake.decide(king, ambiguous, true, WHITE));
        assertEquals(
                MoveOutcome.rejected(Reason.NOT_YOUR_TURN),
                HotspotMoveIntake.decide(king.withCastle(true), ambiguous, true, BLACK));
        assertEquals(
                MoveOutcome.rejected(Reason.NOT_READY),
                HotspotMoveIntake.decide(king.withCastle(true), ambiguous, false, WHITE));
    }

    @Test
    public void theUserPlacesTheDuckAfterTheirOwnMove() {
        BoardFacts duckPending = BoardFacts.PLAYABLE.withSideToMove(BLACK).withDuck(D5, true);
        MoveCandidate duck = MoveCandidate.of(D5, E4);
        assertEquals(MoveOutcome.PLAYED, HotspotMoveIntake.decide(duck, duckPending, true, WHITE));
        assertEquals(
                MoveOutcome.rejected(Reason.NOT_YOUR_TURN),
                HotspotMoveIntake.decide(duck, duckPending, true, BLACK));
    }

    @Test
    public void keepsVariationsWithoutAsking() {
        assertEquals(
                MoveOutcome.PLAYED,
                HotspotMoveIntake.decide(
                        PAWN, WHITE_TO_MOVE.withContinuationToOverwrite(), true, WHITE));
    }
}
