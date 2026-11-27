package jwtc.android.chess.lichess;

import jwtc.android.chess.moveintake.BoardFacts;
import jwtc.android.chess.moveintake.DefaultMoveIntake;
import jwtc.android.chess.moveintake.MoveCandidate;
import jwtc.android.chess.moveintake.MoveIntake;
import jwtc.android.chess.moveintake.MoveOutcome;
import jwtc.chess.board.BoardConstants;

/**
 * Move intake for a Lichess Online Game. The user moves their own side while the game is in
 * progress; moving one of their pieces during the opponent's turn queues it as a premove. With
 * "confirm moves" on, each move waits for the user's confirmation. Moves go to Lichess, and the
 * board follows the game stream.
 */
final class LichessGameMoveIntake implements MoveIntake {
    private final LichessApi lichess;
    private final boolean inProgress;
    private final boolean confirmMoves;

    LichessGameMoveIntake(LichessApi lichess, boolean inProgress, boolean confirmMoves) {
        this.lichess = lichess;
        this.inProgress = inProgress;
        this.confirmMoves = confirmMoves;
    }

    @Override
    public MoveOutcome offer(MoveCandidate candidate) {
        int userSide = lichess.getMyTurn();
        boolean userPiece = lichess.pieceAt(userSide, candidate.from) != BoardConstants.FIELD;
        MoveOutcome outcome =
                decide(
                        candidate,
                        BoardFacts.of(lichess, candidate),
                        inProgress,
                        userSide,
                        userPiece,
                        confirmMoves);
        return outcome == MoveOutcome.PLAYED ? send(lichess, candidate) : outcome;
    }

    static MoveOutcome decide(
            MoveCandidate candidate,
            BoardFacts facts,
            boolean inProgress,
            int userSide,
            boolean userPieceOnFrom,
            boolean confirmMoves) {
        if (!inProgress) {
            return MoveOutcome.rejected(MoveOutcome.Reason.NOT_READY);
        }
        if (facts.sideToMove != userSide) {
            return userPieceOnFrom
                    ? MoveOutcome.QUEUED_AS_PREMOVE
                    : MoveOutcome.rejected(MoveOutcome.Reason.NOT_YOUR_TURN);
        }
        return confirmed(
                DefaultMoveIntake.decide(candidate, facts, false), candidate, confirmMoves);
    }

    /** Asks for confirmation of a move the rules allow, once every other question is answered. */
    static MoveOutcome confirmed(
            MoveOutcome outcome, MoveCandidate candidate, boolean confirmMoves) {
        return outcome == MoveOutcome.PLAYED && confirmMoves && !candidate.moveConfirmed
                ? MoveOutcome.CONFIRM_MOVE
                : outcome;
    }

    /**
     * Sends the move to Lichess as UCI. Castling chosen in the castle dialog is sent with the
     * board's own coordinate, which Chess 960 writes king to rook square.
     */
    static MoveOutcome send(LichessApi lichess, MoveCandidate candidate) {
        if (candidate.castle == MoveCandidate.Castle.CASTLE) {
            String coordinate = lichess.castleCoordinate(candidate.from, candidate.to);
            if (coordinate == null) {
                return MoveOutcome.rejected(MoveOutcome.Reason.ILLEGAL);
            }
            lichess.sendMove(coordinate);
            return MoveOutcome.PLAYED;
        }
        if (candidate.promotion != MoveCandidate.NO_PROMOTION) {
            lichess.setPromotionPiece(candidate.promotion);
        }
        lichess.move(candidate.from, candidate.to);
        return MoveOutcome.PLAYED;
    }
}
