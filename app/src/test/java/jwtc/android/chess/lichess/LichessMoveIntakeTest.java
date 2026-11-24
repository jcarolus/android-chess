package jwtc.android.chess.lichess;

import static jwtc.chess.board.BoardConstants.BLACK;
import static jwtc.chess.board.BoardConstants.QUEEN;
import static jwtc.chess.board.BoardConstants.WHITE;
import static org.junit.Assert.assertEquals;

import jwtc.android.chess.moveintake.BoardFacts;
import jwtc.android.chess.moveintake.MoveCandidate;
import jwtc.android.chess.moveintake.MoveOutcome;
import jwtc.android.chess.moveintake.MoveOutcome.Reason;
import org.junit.Test;

public class LichessMoveIntakeTest {
    private static final int E2 = 52, E4 = 36, E1 = 60, E7 = 12, E8 = 4;
    private static final MoveCandidate PAWN = MoveCandidate.of(E2, E4);
    private static final BoardFacts WHITE_TO_MOVE = BoardFacts.PLAYABLE.withSideToMove(WHITE);
    private static final BoardFacts BLACK_TO_MOVE = BoardFacts.PLAYABLE.withSideToMove(BLACK);

    private static MoveOutcome game(
            MoveCandidate candidate,
            BoardFacts facts,
            boolean inProgress,
            boolean userPiece,
            boolean confirm) {
        return LichessGameMoveIntake.decide(
                candidate, facts, inProgress, WHITE, userPiece, confirm);
    }

    private static MoveOutcome puzzle(
            MoveCandidate candidate, BoardFacts facts, boolean awaiting, boolean confirm) {
        return LichessPuzzleMoveIntake.decide(candidate, facts, awaiting, WHITE, confirm);
    }

    @Test
    public void aGameTakesMovesOnlyWhileInProgress() {
        assertEquals(MoveOutcome.PLAYED, game(PAWN, WHITE_TO_MOVE, true, true, false));
        assertEquals(
                MoveOutcome.rejected(Reason.NOT_READY),
                game(PAWN, WHITE_TO_MOVE, false, true, false));
    }

    @Test
    public void aGameQueuesTheUsersPieceAsAPremoveOnTheOpponentsTurn() {
        assertEquals(MoveOutcome.QUEUED_AS_PREMOVE, game(PAWN, BLACK_TO_MOVE, true, true, false));
        assertEquals(
                MoveOutcome.rejected(Reason.NOT_YOUR_TURN),
                game(PAWN, BLACK_TO_MOVE, true, false, false));
    }

    @Test
    public void aGameAsksTheCastleChoiceSoTheCastleReachesLichess() {
        BoardFacts ambiguous = WHITE_TO_MOVE.withAmbiguousCastle();
        MoveCandidate king = MoveCandidate.of(E1, E1);
        assertEquals(MoveOutcome.NEEDS_CASTLE_CHOICE, game(king, ambiguous, true, true, false));
        assertEquals(MoveOutcome.PLAYED, game(king.withCastle(true), ambiguous, true, true, false));
    }

    @Test
    public void confirmationComesAfterEveryOtherQuestion() {
        BoardFacts promotion = WHITE_TO_MOVE.withPromotion();
        MoveCandidate pawn = MoveCandidate.of(E7, E8);
        assertEquals(MoveOutcome.NEEDS_PROMOTION, game(pawn, promotion, true, true, true));
        MoveCandidate chosen = pawn.withPromotion(QUEEN);
        assertEquals(MoveOutcome.CONFIRM_MOVE, game(chosen, promotion, true, true, true));
        assertEquals(
                MoveOutcome.PLAYED, game(chosen.withMoveConfirmed(), promotion, true, true, true));
    }

    @Test
    public void aGameNeverAsksToOverwrite() {
        assertEquals(
                MoveOutcome.PLAYED,
                game(PAWN, WHITE_TO_MOVE.withContinuationToOverwrite(), true, true, false));
    }

    @Test
    public void aPuzzleTakesMovesOnlyWhileAwaitingOne() {
        assertEquals(MoveOutcome.PLAYED, puzzle(PAWN, WHITE_TO_MOVE, true, false));
        assertEquals(
                MoveOutcome.rejected(Reason.NOT_READY), puzzle(PAWN, WHITE_TO_MOVE, false, false));
    }

    @Test
    public void aPuzzleHasNoPremoves() {
        assertEquals(
                MoveOutcome.rejected(Reason.NOT_YOUR_TURN),
                puzzle(PAWN, BLACK_TO_MOVE, true, false));
    }

    @Test
    public void aPuzzleConfirmsMovesWhenAsked() {
        assertEquals(MoveOutcome.CONFIRM_MOVE, puzzle(PAWN, WHITE_TO_MOVE, true, true));
        assertEquals(
                MoveOutcome.PLAYED, puzzle(PAWN.withMoveConfirmed(), WHITE_TO_MOVE, true, true));
    }
}
