package jwtc.android.chess.hotspotboard;

import jwtc.android.chess.moveintake.BoardFacts;
import jwtc.android.chess.moveintake.DefaultMoveIntake;
import jwtc.android.chess.moveintake.MoveCandidate;
import jwtc.android.chess.moveintake.MoveIntake;
import jwtc.android.chess.moveintake.MoveOutcome;
import jwtc.android.chess.services.GameApi;

/**
 * Move intake for a Hotspot Game. The user moves only their own side, and only while the session is
 * live: connected, synchronised with the peer, not decided by resignation or agreement, and not
 * merely observed or shared. A played move reaches the peer through the Game's move listener.
 */
public final class HotspotMoveIntake implements MoveIntake {
    private final GameApi game;
    private final boolean sessionLive;
    private final int userSide;

    public HotspotMoveIntake(GameApi game, boolean sessionLive, int userSide) {
        this.game = game;
        this.sessionLive = sessionLive;
        this.userSide = userSide;
    }

    @Override
    public MoveOutcome offer(MoveCandidate candidate) {
        MoveOutcome outcome =
                decide(candidate, BoardFacts.of(game, candidate), sessionLive, userSide);
        return outcome == MoveOutcome.PLAYED ? DefaultMoveIntake.play(game, candidate) : outcome;
    }

    public static MoveOutcome decide(
            MoveCandidate candidate, BoardFacts facts, boolean sessionLive, int userSide) {
        if (!sessionLive) {
            return MoveOutcome.rejected(MoveOutcome.Reason.NOT_READY);
        }
        if (!facts.ended && facts.sideOnTurn() != userSide) {
            return MoveOutcome.rejected(MoveOutcome.Reason.NOT_YOUR_TURN);
        }
        return DefaultMoveIntake.decide(candidate, facts, false);
    }
}
