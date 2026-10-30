package jwtc.android.chess.tools;

import android.content.ContentResolver;
import android.content.ContentValues;
import android.net.Uri;
import android.util.Log;
import java.util.TreeSet;
import jwtc.android.chess.engine.RustBoardNative;
import jwtc.android.chess.engine.RustNative;
import jwtc.android.chess.engine.RustSearchRequest;
import jwtc.android.chess.engine.RustSearchResult;
import jwtc.android.chess.puzzle.MyPuzzleProvider;
import jwtc.android.chess.services.GameApi;
import jwtc.chess.PGNColumns;
import jwtc.chess.board.BoardConstants;
import jwtc.chess.board.BoardPosition;

/**
 * Stores each mate in a game of an import file as a Practice Position; the same final position
 * counts once per job.
 */
public class PracticeImportHandler implements ImportHandler {
    private static final String TAG = "PracticeImportHandler";

    private GameApi gameApi;
    private ContentResolver contentResolver;
    private TreeSet<Long> _arrKeys;

    public PracticeImportHandler(GameApi gameApi, ContentResolver contentResolver) {
        _arrKeys = new TreeSet<Long>();
        this.gameApi = gameApi;
        this.contentResolver = contentResolver;
    }

    @Override
    public boolean handle(ImportItem item) {
        String sPGN = item.pgn;
        // Log.d(TAG, "processPGN " + sPGN);
        if (gameApi.loadPGN(sPGN)) {

            if (gameApi.getState() == BoardConstants.MATE) {

                long lKey = gameApi.getHashKey();

                if (false == _arrKeys.contains(lKey)) {
                    _arrKeys.add(lKey);
                } else {
                    return false;
                }

                int startExport = gameApi.getPGNSize();

                int plies = 2, undos = 0, moves = 0;
                String s = "";
                String[] arrMoves = {
                    gameApi.exportMovesPGNFromPly(startExport),
                    "", // not interested in move from opponent
                    gameApi.exportMovesPGNFromPly(startExport - 3),
                };

                if (startExport >= 3) { // at least 3 half moves for a 2 move mate

                    while (plies <= 4) {
                        undos = 0;
                        while (undos <= moves) { // undo one extra
                            // Log.i(TAG, "undo");
                            gameApi.undoMove();
                            undos++;
                        }
                        String sFEN = gameApi.getFEN();

                        GameApi.PositionSnapshot snapshot =
                                gameApi.getPositionSnapshot(gameApi.getCurrentNode());
                        RustSearchRequest.Variant variant =
                                gameApi.isDuckVariant()
                                        ? RustSearchRequest.Variant.DUCK
                                        : gameApi.isChess960Variant()
                                                ? RustSearchRequest.Variant.CHESS_960
                                                : RustSearchRequest.Variant.STANDARD;
                        RustSearchResult result =
                                RustNative.search(
                                        RustSearchRequest.fromSnapshot(
                                                1, snapshot, plies, 0, false, variant));
                        if (!result.hasEvaluation
                                || result.bestMove.isEmpty()
                                || !gameApi.isCurrentPosition(snapshot)) {
                            return false;
                        }
                        String san =
                                RustBoardNative.san(
                                                variant,
                                                snapshot.fen,
                                                new String[0],
                                                result.bestMove)
                                        .san;
                        int move = new BoardPosition(snapshot.fen).packedMove(result.bestMove, san);
                        int duckAt = result.bestMove.indexOf('@');
                        int duck =
                                duckAt < 0
                                        ? -1
                                        : BoardPosition.square(
                                                result.bestMove.substring(duckAt + 1));
                        int value = result.score * (snapshot.turn == BoardConstants.WHITE ? 1 : -1);

                        // Log.i(TAG, moves + ", Move " + Move.toDbgString(move) + " val: " + value
                        // + " at plies " + plies);

                        if (value == BoardConstants.VALUATION_MATE * (plies % 2 == 0 ? 1 : -1)
                                && gameApi.applyMove(move, duck)) {

                            // save when it's our move
                            if (plies % 2 == 0) {
                                s = "[FEN \"" + sFEN + "\"]\n" + arrMoves[moves];
                            }
                            moves++;
                            plies++;

                        } else {
                            // Log.i(TAG, "Stop at plies " + plies);
                            break;
                        }
                    }

                    if (s.length() > 0) {
                        try {

                            ContentValues values = new ContentValues();
                            values.put(PGNColumns.PGN, s);

                            Uri uri = MyPuzzleProvider.CONTENT_URI_PRACTICES;
                            Uri uriInsert = contentResolver.insert(uri, values);

                            return true;
                        } catch (Exception ex) {
                            Log.e(TAG, ex.toString());
                            return false;
                        }
                    }
                }
            }
        }
        return false;
    }
}
