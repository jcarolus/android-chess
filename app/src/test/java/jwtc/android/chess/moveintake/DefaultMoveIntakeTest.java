package jwtc.android.chess.moveintake;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;

import jwtc.android.chess.moveintake.MoveOutcome.Reason;
import jwtc.chess.board.BoardConstants;
import org.junit.Test;

public class DefaultMoveIntakeTest {
    private static final int E2 = 52, E4 = 36, E7 = 12, E8 = 4, E1 = 60, G1 = 62, D5 = 27;
    private static final BoardFacts PLAYABLE = BoardFacts.PLAYABLE;

    private static MoveOutcome decide(MoveCandidate candidate, BoardFacts facts) {
        return DefaultMoveIntake.decide(candidate, facts, false);
    }

    private static MoveOutcome decideConfirmingOverwrite(
            MoveCandidate candidate, BoardFacts facts) {
        return DefaultMoveIntake.decide(candidate, facts, true);
    }

    @Test
    public void playsAnOrdinaryMove() {
        assertEquals(MoveOutcome.PLAYED, decide(MoveCandidate.of(E2, E4), PLAYABLE));
    }

    @Test
    public void rejectsEveryMoveOnceTheGameHasEnded() {
        BoardFacts ended = PLAYABLE.withEnded().withDuck(D5, false);
        assertEquals(MoveOutcome.rejected(Reason.ENDED), decide(MoveCandidate.of(E2, E4), ended));
        assertEquals(MoveOutcome.rejected(Reason.ENDED), decide(MoveCandidate.of(D5, E4), ended));
    }

    @Test
    public void asksForThePromotionPieceUntilOneIsChosen() {
        BoardFacts promotion = PLAYABLE.withPromotion();
        MoveCandidate candidate = MoveCandidate.of(E7, E8);
        assertEquals(MoveOutcome.NEEDS_PROMOTION, decide(candidate, promotion));
        assertEquals(
                MoveOutcome.PLAYED,
                decide(candidate.withPromotion(BoardConstants.KNIGHT), promotion));
    }

    @Test
    public void asksWhetherAnAmbiguousKingMoveCastles() {
        BoardFacts ambiguous = PLAYABLE.withAmbiguousCastle();
        MoveCandidate stays = MoveCandidate.of(E1, E1);
        assertEquals(MoveOutcome.NEEDS_CASTLE_CHOICE, decide(stays, ambiguous));
        assertEquals(MoveOutcome.PLAYED, decide(stays.withCastle(true), ambiguous));

        MoveCandidate steps = MoveCandidate.of(E1, G1);
        assertEquals(MoveOutcome.NEEDS_CASTLE_CHOICE, decide(steps, ambiguous));
        assertEquals(MoveOutcome.PLAYED, decide(steps.withCastle(false), ambiguous));
    }

    @Test
    public void aKingThatStaysOnItsSquareOnlyMovesByCastling() {
        MoveCandidate stays = MoveCandidate.of(E1, E1).withCastle(false);
        assertEquals(
                MoveOutcome.rejected(Reason.ILLEGAL),
                decide(stays, PLAYABLE.withAmbiguousCastle()));
    }

    @Test
    public void castlingNeverAsksForAPromotionPiece() {
        BoardFacts facts = PLAYABLE.withAmbiguousCastle().withPromotion();
        assertEquals(MoveOutcome.PLAYED, decide(MoveCandidate.of(E1, G1).withCastle(true), facts));
    }

    @Test
    public void onlyTheDuckMovesWhileItStillHasToBePlaced() {
        BoardFacts duckPending = PLAYABLE.withDuck(D5, true).withPromotion();
        assertEquals(MoveOutcome.PLAYED, decide(MoveCandidate.of(D5, E4), duckPending));
        assertEquals(
                MoveOutcome.rejected(Reason.NOT_YOUR_TURN),
                decide(MoveCandidate.of(E2, E4), duckPending));
    }

    @Test
    public void theDuckWaitsForAPieceToMoveFirst() {
        BoardFacts duckPlaced = PLAYABLE.withDuck(D5, false);
        assertEquals(
                MoveOutcome.rejected(Reason.NOT_YOUR_TURN),
                decide(MoveCandidate.of(D5, E4), duckPlaced));
        assertEquals(MoveOutcome.PLAYED, decide(MoveCandidate.of(E2, E4), duckPlaced));
    }

    @Test
    public void placingTheDuckAsksNothing() {
        BoardFacts facts =
                PLAYABLE.withDuck(D5, true)
                        .withPromotion()
                        .withAmbiguousCastle()
                        .withContinuationToOverwrite();
        assertEquals(
                MoveOutcome.PLAYED, decideConfirmingOverwrite(MoveCandidate.of(D5, E4), facts));
    }

    @Test
    public void keepsVariationsWithoutAskingByDefault() {
        assertEquals(
                MoveOutcome.PLAYED,
                decide(MoveCandidate.of(E2, E4), PLAYABLE.withContinuationToOverwrite()));
    }

    @Test
    public void confirmsOverwritingBeforeAnyOtherQuestion() {
        BoardFacts facts = PLAYABLE.withContinuationToOverwrite().withPromotion();
        MoveCandidate candidate = MoveCandidate.of(E7, E8);
        assertEquals(MoveOutcome.CONFIRM_OVERWRITE, decideConfirmingOverwrite(candidate, facts));
        assertEquals(
                MoveOutcome.NEEDS_PROMOTION,
                decideConfirmingOverwrite(candidate.withOverwriteConfirmed(), facts));
        assertEquals(
                MoveOutcome.PLAYED,
                decideConfirmingOverwrite(
                        candidate.withOverwriteConfirmed().withPromotion(BoardConstants.QUEEN),
                        facts));
    }

    @Test
    public void onlyRejectionsCarryAReason() {
        assertNull(MoveOutcome.PLAYED.reason());
        assertEquals(Reason.NOT_READY, MoveOutcome.rejected(Reason.NOT_READY).reason());
        assertEquals(MoveOutcome.Kind.REJECTED, MoveOutcome.rejected(Reason.ILLEGAL).kind());
    }
}
