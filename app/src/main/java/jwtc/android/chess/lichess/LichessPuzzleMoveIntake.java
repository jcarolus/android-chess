package jwtc.android.chess.lichess;

import jwtc.android.chess.moveintake.BoardFacts;
import jwtc.android.chess.moveintake.DefaultMoveIntake;
import jwtc.android.chess.moveintake.MoveCandidate;
import jwtc.android.chess.moveintake.MoveIntake;
import jwtc.android.chess.moveintake.MoveOutcome;

/**
 * Move intake for a Lichess Puzzle. The board takes the user's move only while the puzzle waits for
 * it: not while Lichess's reply or a wrong move is being shown. There are no premoves. With
 * "confirm moves" on, each move waits for the user's confirmation.
 */
final class LichessPuzzleMoveIntake implements MoveIntake {
    private final LichessApi lichess;
    private final boolean awaitingMove;
    private final boolean confirmMoves;

    LichessPuzzleMoveIntake(LichessApi lichess, boolean awaitingMove, boolean confirmMoves) {
        this.lichess = lichess;
        this.awaitingMove = awaitingMove;
        this.confirmMoves = confirmMoves;
    }

    @Override
    public MoveOutcome offer(MoveCandidate candidate) {
        MoveOutcome outcome =
                decide(
                        candidate,
                        BoardFacts.of(lichess, candidate),
                        awaitingMove,
                        lichess.getMyTurn(),
                        confirmMoves);
        return outcome == MoveOutcome.PLAYED
                ? LichessGameMoveIntake.send(lichess, candidate)
                : outcome;
    }

    static MoveOutcome decide(
            MoveCandidate candidate,
            BoardFacts facts,
            boolean awaitingMove,
            int userSide,
            boolean confirmMoves) {
        if (!awaitingMove) {
            return MoveOutcome.rejected(MoveOutcome.Reason.NOT_READY);
        }
        if (facts.sideToMove != userSide) {
            return MoveOutcome.rejected(MoveOutcome.Reason.NOT_YOUR_TURN);
        }
        return LichessGameMoveIntake.confirmed(
                DefaultMoveIntake.decide(candidate, facts, false), candidate, confirmMoves);
    }
}
