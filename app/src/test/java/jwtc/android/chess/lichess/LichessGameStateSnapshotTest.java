package jwtc.android.chess.lichess;

import static org.junit.Assert.assertEquals;

import jwtc.android.chess.lichess.models.GameFull;
import jwtc.android.chess.lichess.models.GameState;
import jwtc.chess.board.BoardConstants;
import org.junit.Test;

public class LichessGameStateSnapshotTest {
    private static int finalState(String status, String winner) {
        GameFull game = new GameFull();
        game.id = "game";
        game.state = new GameState();
        game.state.status = status;
        game.state.winner = winner;
        return LichessGameStateSnapshot.from(game).finalState();
    }

    @Test
    public void resignationRecordsTheLoser() {
        assertEquals(BoardConstants.BLACK_RESIGNED, finalState("resign", "white"));
        assertEquals(BoardConstants.WHITE_RESIGNED, finalState("resign", "black"));
    }

    @Test
    public void timeForfeitRecordsTheLoser() {
        for (String status : new String[] {"outoftime", "timeout"}) {
            assertEquals(BoardConstants.BLACK_FORFEIT_TIME, finalState(status, "white"));
            assertEquals(BoardConstants.WHITE_FORFEIT_TIME, finalState(status, "black"));
        }
    }

    @Test
    public void serverDrawRecordsADrawIncludingFlagFallWithoutWinner() {
        assertEquals(BoardConstants.DRAW_AGREEMENT, finalState("draw", null));
        assertEquals(BoardConstants.DRAW_AGREEMENT, finalState("outoftime", null));
    }

    @Test
    public void inProgressOrBoardDecidedGamesHaveNoExternalFinalState() {
        assertEquals(-1, finalState("started", null));
        assertEquals(-1, finalState("mate", "white"));
        assertEquals(-1, finalState("stalemate", null));
        assertEquals(-1, finalState("aborted", null));
        assertEquals(-1, finalState("resign", null));
    }
}
