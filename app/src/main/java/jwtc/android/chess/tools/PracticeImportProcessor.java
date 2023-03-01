package jwtc.android.chess.tools;

import android.content.ContentResolver;
import android.content.ContentValues;
import android.net.Uri;
import android.os.Handler;
import android.util.Log;

import java.util.TreeSet;

import jwtc.android.chess.puzzle.MyPuzzleProvider;
import jwtc.android.chess.services.GameApi;
import jwtc.chess.JNI;
import jwtc.chess.PGNColumns;
import jwtc.chess.board.ChessBoard;

public class PracticeImportProcessor extends PGNProcessor {
    private static final String TAG = "PracticeImportProcessor";

    private JNI jni;
    private GameApi gameApi;
    private ContentResolver contentResolver;
    private TreeSet<Long> _arrKeys;

    public PracticeImportProcessor(int mode, Handler updateHandler, GameApi gameApi, ContentResolver contentResolver) {
        super(mode, updateHandler);
        jni = JNI.getInstance();
        _arrKeys = new TreeSet<Long>();
        this.gameApi = gameApi;
        this.contentResolver = contentResolver;
    }

    @Override
    public boolean processPGN(String sPGN) {
        //Log.i("processPGN", sPGN);
        if (gameApi.loadPGN(sPGN)) {

            if (jni.getState() == ChessBoard.MATE) {

                long lKey = jni.getHashKey();

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
                            //Log.i(TAG, "undo");
                            gameApi.undoMove();
                            undos++;
                        }
                        String sFEN = jni.toFEN();

                        jni.searchDepth(plies, 0);

                        int move = jni.getMove();
                        int value = jni.peekSearchBestValue();

                        //Log.i(TAG, moves + ", Move " + Move.toDbgString(move) + " val: " + value + " at plies " + plies);

                        if (value == 100000 * (plies % 2 == 0 ? 1 : -1) && jni.move(move) != 0) {
                            gameApi.addPGNEntry(jni.getNumBoard() - 1, jni.getMyMoveToString(), "", jni.getMyMove(), -1);

                            // save when it's our move
                            if (plies % 2 == 0) {
                                if (plies == 4) {
                                    Log.i(TAG, "YESS");
                                }
                                s = "[FEN \"" + sFEN + "\"]\n" + arrMoves[moves];
                            }
                            moves++;
                            plies++;

                        } else {
                            //Log.i(TAG, "Stop at plies " + plies);
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

    @Override
    public String getString() {
        return null;
    }
}
