package jwtc.android.chess.matecollection;

import android.util.Log;
import jwtc.android.chess.engine.RustNative;
import jwtc.android.chess.engine.RustSearchRequest;
import jwtc.android.chess.engine.RustSearchResult;
import jwtc.android.chess.services.GameApi;
import jwtc.chess.board.BoardConstants;

/** Chooses the starting ply for a full Practice Game on a worker with a private board. */
final class PracticePositionSelector {
    private PracticePositionSelector() {}

    /** Tries mate in two and falls back to the final mating move. */
    static int startPly(String pgn) {
        GameApi game = new GameApi();
        if (!game.loadPGN(pgn)
                || game.getEngineState() != BoardConstants.MATE
                || game.getCurrentPly() == 0) {
            return -1;
        }

        int mateInOne = game.getCurrentPly() - 1;
        if (mateInOne < 2 || Thread.currentThread().isInterrupted()) {
            return mateInOne;
        }

        // Three plies before mate, the mating side has the move again.
        int mateInTwo = mateInOne - 2;
        game.jumpToBoardNum(mateInTwo);
        GameApi.PositionSnapshot position = game.getPositionSnapshot(game.getCurrentNode());
        if (position == null) {
            return mateInOne;
        }
        try {
            RustSearchResult result =
                    RustNative.search(
                            RustSearchRequest.fromSnapshot(
                                    1,
                                    position,
                                    MateRules.plyBudget(0),
                                    0,
                                    false,
                                    game.rustVariant()));
            int mateScore =
                    position.turn == BoardConstants.WHITE
                            ? BoardConstants.VALUATION_MATE
                            : -BoardConstants.VALUATION_MATE;
            if (result.hasEvaluation
                    && !result.interrupted
                    && result.score == mateScore
                    && !result.bestMove.isEmpty()) {
                return mateInTwo;
            }
        } catch (RuntimeException error) {
            Log.w("PracticePosition", "Could not search for a longer forcing mate", error);
        }
        return mateInOne;
    }
}
