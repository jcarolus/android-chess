package jwtc.android.chess.matecollection;

import jwtc.android.chess.moveintake.BoardFacts;
import jwtc.android.chess.moveintake.DefaultMoveIntake;
import jwtc.android.chess.moveintake.MoveCandidate;
import jwtc.android.chess.moveintake.MoveIntake;
import jwtc.android.chess.moveintake.MoveOutcome;
import jwtc.android.chess.services.GameApi;

/**
 * Move intake for Mate-in-Two Puzzles and Practice Positions. The board takes a move only once the
 * position is ready and only on the user's turn; the replies are the collection's own. A solved or
 * finished position rejects moves as ended.
 */
public final class MateCollectionMoveIntake implements MoveIntake {
    private final GameApi game;
    private final MateCollection collection;

    public MateCollectionMoveIntake(GameApi game, MateCollection collection) {
        this.game = game;
        this.collection = collection;
    }

    @Override
    public MoveOutcome offer(MoveCandidate candidate) {
        MoveOutcome outcome =
                decide(
                        candidate,
                        BoardFacts.of(game, candidate),
                        collection.isPositionReady(),
                        collection.isUsersTurn());
        return outcome == MoveOutcome.PLAYED ? DefaultMoveIntake.play(game, candidate) : outcome;
    }

    public static MoveOutcome decide(
            MoveCandidate candidate, BoardFacts facts, boolean positionReady, boolean usersTurn) {
        if (!positionReady) {
            return MoveOutcome.rejected(MoveOutcome.Reason.NOT_READY);
        }
        if (!facts.ended && !usersTurn) {
            return MoveOutcome.rejected(MoveOutcome.Reason.NOT_YOUR_TURN);
        }
        return DefaultMoveIntake.decide(candidate, facts, false);
    }
}
