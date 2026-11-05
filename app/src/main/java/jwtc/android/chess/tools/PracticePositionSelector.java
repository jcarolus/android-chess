package jwtc.android.chess.tools;

import java.util.HashSet;
import java.util.Set;
import jwtc.android.chess.engine.RustBoardNative;
import jwtc.android.chess.engine.RustNative;
import jwtc.android.chess.engine.RustSearchRequest;
import jwtc.android.chess.engine.RustSearchResult;
import jwtc.android.chess.services.GameApi;
import jwtc.chess.board.BoardConstants;
import jwtc.chess.board.BoardPosition;

/** Selects Practice Positions from imported Games, counting each final Position once per job. */
final class PracticePositionSelector {
    private final GameApi game;
    private final Set<Long> seenFinalPositions = new HashSet<>();

    PracticePositionSelector(GameApi game) {
        this.game = game;
    }

    /**
     * Returns the saved Practice Position PGN, or null when the Game has no qualifying Position.
     */
    String select(String pgn) {
        if (!game.loadPGN(pgn) || game.getState() != BoardConstants.MATE) {
            return null;
        }

        int finalPly = game.getPGNSize();
        if (finalPly == 0) {
            return null;
        }

        long finalPosition = game.getHashKey();
        if (!seenFinalPositions.add(finalPosition)) {
            return null;
        }

        if (finalPly == 1) {
            return game.exportFullPGN();
        }

        String[] moves = {
            game.exportMovesPGNFromPly(finalPly), "", game.exportMovesPGNFromPly(finalPly - 3),
        };
        String selected = null;
        int played = 0;
        int searchLimit = Math.min(4, finalPly + 1);
        for (int plies = 2; plies <= searchLimit; ) {
            for (int undo = 0; undo <= played; undo++) {
                game.undoMove();
            }
            String fen = game.getFEN();
            GameApi.PositionSnapshot position = game.getPositionSnapshot(game.getCurrentNode());
            RustSearchRequest.Variant variant =
                    game.isDuckVariant()
                            ? RustSearchRequest.Variant.DUCK
                            : game.isChess960Variant()
                                    ? RustSearchRequest.Variant.CHESS_960
                                    : RustSearchRequest.Variant.STANDARD;
            RustSearchResult result =
                    RustNative.search(
                            RustSearchRequest.fromSnapshot(1, position, plies, 0, false, variant));
            if (!result.hasEvaluation
                    || result.bestMove.isEmpty()
                    || !game.isCurrentPosition(position)) {
                return null;
            }

            String san =
                    RustBoardNative.san(variant, position.fen, new String[0], result.bestMove).san;
            int move = new BoardPosition(position.fen).packedMove(result.bestMove, san);
            int duckAt = result.bestMove.indexOf('@');
            int duck =
                    duckAt < 0 ? -1 : BoardPosition.square(result.bestMove.substring(duckAt + 1));
            int value = result.score * (position.turn == BoardConstants.WHITE ? 1 : -1);
            if (value != BoardConstants.VALUATION_MATE * (plies % 2 == 0 ? 1 : -1)
                    || !game.applyMove(move, duck)) {
                break;
            }
            if (plies % 2 == 0) {
                selected = "[FEN \"" + fen + "\"]\n" + moves[played];
            }
            played++;
            plies++;
        }
        return selected;
    }
}
