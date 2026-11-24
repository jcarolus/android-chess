package jwtc.android.chess.moveintake;

import static jwtc.chess.board.BoardConstants.BLACK;
import static jwtc.chess.board.BoardConstants.WHITE;
import static org.junit.Assert.assertEquals;

import jwtc.android.chess.moveintake.MoveOutcome.Reason;
import org.junit.Test;

public class PlayMoveIntakeTest {
    private static final int E2 = 52, E4 = 36, E1 = 60, D5 = 27;
    private static final MoveCandidate PAWN = MoveCandidate.of(E2, E4);
    private static final MoveOutcome NOT_YOUR_TURN = MoveOutcome.rejected(Reason.NOT_YOUR_TURN);

    @Test
    public void againstTheEngineTheUserOnlyMovesTheirOwnSide() {
        BoardFacts whiteToMove = BoardFacts.PLAYABLE.withSideToMove(WHITE);
        assertEquals(MoveOutcome.PLAYED, PlayMoveIntake.decide(PAWN, whiteToMove, true, WHITE));
        assertEquals(NOT_YOUR_TURN, PlayMoveIntake.decide(PAWN, whiteToMove, true, BLACK));
    }

    @Test
    public void againstAPersonEitherSideMoves() {
        BoardFacts blackToMove = BoardFacts.PLAYABLE.withSideToMove(BLACK);
        assertEquals(MoveOutcome.PLAYED, PlayMoveIntake.decide(PAWN, blackToMove, false, WHITE));
    }

    @Test
    public void theUserCannotChooseTheEnginesCastle() {
        BoardFacts enginesKing = BoardFacts.PLAYABLE.withSideToMove(BLACK).withAmbiguousCastle();
        assertEquals(
                NOT_YOUR_TURN,
                PlayMoveIntake.decide(MoveCandidate.of(E1, E1), enginesKing, true, WHITE));
    }

    @Test
    public void theUserPlacesTheDuckAfterTheirOwnMove() {
        // After White's piece move the board already reports Black to move.
        BoardFacts duckPending = BoardFacts.PLAYABLE.withSideToMove(BLACK).withDuck(D5, true);
        MoveCandidate duck = MoveCandidate.of(D5, E4);
        assertEquals(MoveOutcome.PLAYED, PlayMoveIntake.decide(duck, duckPending, true, WHITE));
        assertEquals(NOT_YOUR_TURN, PlayMoveIntake.decide(duck, duckPending, true, BLACK));
    }

    @Test
    public void confirmsOverwritingTheMovesThatFollow() {
        BoardFacts earlier = BoardFacts.PLAYABLE.withContinuationToOverwrite();
        assertEquals(
                MoveOutcome.CONFIRM_OVERWRITE, PlayMoveIntake.decide(PAWN, earlier, true, WHITE));
        assertEquals(
                MoveOutcome.PLAYED,
                PlayMoveIntake.decide(PAWN.withOverwriteConfirmed(), earlier, true, WHITE));
    }

    @Test
    public void anEndedGameReportsEndedRatherThanTheTurn() {
        BoardFacts ended = BoardFacts.PLAYABLE.withSideToMove(BLACK).withEnded();
        assertEquals(
                MoveOutcome.rejected(Reason.ENDED),
                PlayMoveIntake.decide(PAWN, ended, true, WHITE));
    }
}
