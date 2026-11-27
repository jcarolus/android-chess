package jwtc.android.chess.matecollection;

import static org.junit.Assert.assertEquals;

import jwtc.android.chess.moveintake.BoardFacts;
import jwtc.android.chess.moveintake.MoveCandidate;
import jwtc.android.chess.moveintake.MoveOutcome;
import jwtc.android.chess.moveintake.MoveOutcome.Reason;
import org.junit.Test;

public class MateCollectionMoveIntakeTest {
    private static final int E2 = 52, E4 = 36, E1 = 60, E7 = 12, E8 = 4;
    private static final MoveCandidate PAWN = MoveCandidate.of(E2, E4);
    private static final BoardFacts PLAYABLE = BoardFacts.PLAYABLE;

    @Test
    public void takesTheUsersMoveOnceThePositionIsReady() {
        assertEquals(
                MoveOutcome.PLAYED, MateCollectionMoveIntake.decide(PAWN, PLAYABLE, true, true));
    }

    @Test
    public void waitsForThePosition() {
        assertEquals(
                MoveOutcome.rejected(Reason.NOT_READY),
                MateCollectionMoveIntake.decide(PAWN, PLAYABLE, false, false));
    }

    @Test
    public void leavesTheRepliesToTheCollection() {
        assertEquals(
                MoveOutcome.rejected(Reason.NOT_YOUR_TURN),
                MateCollectionMoveIntake.decide(PAWN, PLAYABLE, true, false));
    }

    @Test
    public void aSolvedPositionReportsEnded() {
        assertEquals(
                MoveOutcome.rejected(Reason.ENDED),
                MateCollectionMoveIntake.decide(PAWN, PLAYABLE.withEnded(), true, false));
    }

    @Test
    public void theCastleChoiceWaitsForTheUsersTurn() {
        MoveCandidate castle = MoveCandidate.of(E1, E1).withCastle(true);
        BoardFacts ambiguous = PLAYABLE.withAmbiguousCastle();
        assertEquals(
                MoveOutcome.rejected(Reason.NOT_YOUR_TURN),
                MateCollectionMoveIntake.decide(castle, ambiguous, true, false));
        assertEquals(
                MoveOutcome.rejected(Reason.NOT_READY),
                MateCollectionMoveIntake.decide(castle, ambiguous, false, false));
    }

    @Test
    public void asksForThePromotionPiece() {
        assertEquals(
                MoveOutcome.NEEDS_PROMOTION,
                MateCollectionMoveIntake.decide(
                        MoveCandidate.of(E7, E8), PLAYABLE.withPromotion(), true, true));
    }
}
