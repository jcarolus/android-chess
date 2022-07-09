package jwtc.android.chess.tools;

import android.app.Service;
import android.content.Intent;
import android.content.SharedPreferences;
import android.net.Uri;
import android.os.Binder;
import android.os.Bundle;
import android.os.Handler;
import android.os.IBinder;
import android.os.Message;
import android.util.Log;

import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.lang.ref.WeakReference;
import java.util.ArrayList;

import androidx.annotation.Nullable;
import jwtc.android.chess.engine.UCIEngine;
import jwtc.android.chess.puzzle.MyPuzzleProvider;
import jwtc.android.chess.services.GameApi;


public class ImportService extends Service {
    private static final String TAG = "ImportService";

    public static final int IMPORT_PUZZLES = 1;
    public static final int IMPORT_GAMES = 2;
    public static final int IMPORT_PRACTICE = 3;
    public static final int IMPORT_OPENINGS = 4;
    public static final int IMPORT_DATABASE = 5;
    public static final int UCI_INSTALL = 6;
    public static final int UCI_DB_INSTALL = 7;
    public static final int PRACTICE_RESET = 8;
    public static final int DB_POINT = 9;


    protected ArrayList<ImportListener> listeners = new ArrayList<>();
    private PuzzleImportProcessor puzzleImportProcessor = null;
    private GameImportProcessor gameImportProcessor = null;
    private PracticeImportProcessor practiceImportProcessor = null;
    private OpeningImportProcessor openingImportProcessor = null;
    private PGNDbProcessor pgnDbProcessor = null;

    private ImportApi importApi;
    private final IBinder mBinder = new ImportService.LocalBinder();
    private Handler updateHandler = new ImportService.ThreadMessageHandler(this);

    public void addListener(ImportListener listener) {
        this.listeners.add(listener);
    }

    public void removeListener(ImportListener listener) {
        this.listeners.remove(listener);
    }

    @Nullable
    @Override
    public IBinder onBind(Intent intent) {
        return mBinder;
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        Log.i(TAG, "Received start id " + startId + ": " + intent);
        return START_STICKY;
    }

    public void startImport(final Uri uri, final int mode) {
        Log.d(TAG, "mode " + mode);

        importApi = new ImportApi();

        switch (mode) {
            case IMPORT_PUZZLES:
                if (puzzleImportProcessor == null) {
                    puzzleImportProcessor = new PuzzleImportProcessor(mode, updateHandler, importApi, getContentResolver());
                }
                InputStream isPuzzles;
                try {
                    if (uri == null) {
                        isPuzzles = getAssets().open("puzzles.pgn");
                    } else {
                        isPuzzles = getContentResolver().openInputStream(uri);
                    }
                    puzzleImportProcessor.processPGNFile(isPuzzles);
                } catch (IOException e) {
                    dispatchEvent(PGNProcessor.MSG_FATAL_ERROR, mode, 0, 1);
                }

                break;

            case IMPORT_GAMES:
                if (uri != null) {
                    if (gameImportProcessor == null) {
                        gameImportProcessor = new GameImportProcessor(mode, updateHandler, importApi, getContentResolver());
                    }
                    try {
                        InputStream isGames = getContentResolver().openInputStream(uri);
                        if (uri.getPath().lastIndexOf(".zip") > 0) {
                            gameImportProcessor.processZipFile(isGames);
                        } else {
                            gameImportProcessor.processPGNFile(isGames);
                        }
                    } catch (Exception ex) {
                        Log.e(TAG, ex.toString());
                        dispatchEvent(PGNProcessor.MSG_FATAL_ERROR, mode, 0, 0);
                    }
                }
                break;
            case IMPORT_PRACTICE:
                if (practiceImportProcessor == null) {
                    practiceImportProcessor = new PracticeImportProcessor(mode, updateHandler, importApi, getContentResolver());
                }
                try {
                    InputStream isPractice;
                    if (uri == null) {
                        isPractice = getAssets().open("practice.pgn");
                    } else {
                        isPractice = getContentResolver().openInputStream(uri);
                    }
                    practiceImportProcessor.processPGNFile(isPractice);
                } catch (IOException e) {
                    dispatchEvent(PGNProcessor.MSG_FATAL_ERROR, mode, 0, 0);
                }
                break;
            case IMPORT_OPENINGS:
                if (uri != null) {
                    if (openingImportProcessor == null) {
                        openingImportProcessor = new OpeningImportProcessor(mode, updateHandler, importApi);
                    }
                    try {
                        InputStream isOpenings = getContentResolver().openInputStream(uri);
                        openingImportProcessor.processPGNFile(isOpenings);

                    } catch (Exception ex) {
                        Log.e(TAG, ex.toString());
                        dispatchEvent(PGNProcessor.MSG_FATAL_ERROR, mode, 0, 0);
                    }
                }
                break;
            case IMPORT_DATABASE:
                if (uri != null) {
                    if (pgnDbProcessor == null) {
                        pgnDbProcessor = new PGNDbProcessor(mode, updateHandler, importApi);
                    }
                    try {
                        InputStream isDatabase = getContentResolver().openInputStream(uri);
                        pgnDbProcessor.processPGNFile(isDatabase);

                    } catch (Exception ex) {
                        Log.e(TAG, ex.toString());
                        dispatchEvent(PGNProcessor.MSG_FATAL_ERROR, mode, 0, 1);
                    }
                }
                break;
            case UCI_INSTALL:
                if (uri != null) {
                    String sPath = uri.getPath();
                    String sEngine = uri.getLastPathSegment(); //"robbolito-android"; //bikjump2.1    stockfish2.0

                    Log.i(TAG, "Install UCI " + sPath + " as " + sEngine);

                    try {
                        FileInputStream fis = new FileInputStream(sPath);
                        UCIEngine.install(fis, sEngine);

                        SharedPreferences.Editor editor = getSharedPreferences("ChessPlayer", MODE_PRIVATE).edit();
                        editor.putString("UCIEngine", sEngine);
                        editor.commit();

                        dispatchEvent(PGNProcessor.MSG_FINISHED, mode, 1, 0);

                    } catch (IOException e) {
                        Log.e(TAG, e.toString());
                        dispatchEvent(PGNProcessor.MSG_FATAL_ERROR, mode, 0, 1);
                    }
                }
                break;
            case UCI_DB_INSTALL:
                if (uri != null) {
                    String sPath = uri.getPath();
                    String sDatabase = uri.getLastPathSegment(); //"goi.bin"

                    Log.i(TAG, "Install UCI database " + sPath + " as " + sDatabase);

                    try {
                        FileInputStream fis = new FileInputStream(sPath);
                        UCIEngine.installDb(fis, sDatabase);

                        SharedPreferences.Editor editor = getSharedPreferences("ChessPlayer", MODE_PRIVATE).edit();
                        editor.putString("UCIDatabase", sDatabase);
                        editor.commit();

                        dispatchEvent(PGNProcessor.MSG_FINISHED, mode, 1, 0);

                    } catch (IOException e) {
                        Log.e(TAG, e.toString());
                        dispatchEvent(PGNProcessor.MSG_FATAL_ERROR, mode, 0, 1);
                    }
                }
                break;
            case PRACTICE_RESET:
                try {
                    SharedPreferences prefs = getSharedPreferences("ChessPlayer", MODE_PRIVATE);
                    SharedPreferences.Editor editor = prefs.edit();
                    editor.putInt("practicePos", 0);
                    editor.putInt("practiceTicks", 0);
                    editor.commit();

                    getContentResolver().delete(MyPuzzleProvider.CONTENT_URI_PRACTICES, "1=1", null);

                    dispatchEvent(PGNProcessor.MSG_FINISHED, mode, 1, 0);

                } catch (Exception e) {
                    Log.e(TAG, e.toString());
                    dispatchEvent(PGNProcessor.MSG_FATAL_ERROR, mode, 0, 1);
                }
                break;
            case DB_POINT:
                try {
                    if (uri != null) {
                        SharedPreferences.Editor editor = getSharedPreferences("ChessPlayer", MODE_PRIVATE).edit();

                        editor.putString("OpeningDb", uri.toString());
                        editor.commit();

                        dispatchEvent(PGNProcessor.MSG_FINISHED, mode, 1, 0);
                    }
                } catch (Exception e) {
                    Log.e(TAG, e.toString());
                    dispatchEvent(PGNProcessor.MSG_FATAL_ERROR, mode, 0, 1);
                }
                break;
        }
    }

    @Override
    public void onDestroy() {
        Log.i(TAG, "onDestroy");

        if (puzzleImportProcessor != null) {
            puzzleImportProcessor.stopProcessing();
        }
        if (gameImportProcessor != null) {
            gameImportProcessor.stopProcessing();
        }
        if (practiceImportProcessor != null) {
            practiceImportProcessor.stopProcessing();
        }
        if (openingImportProcessor != null) {
            openingImportProcessor.stopProcessing();
        }
        if (pgnDbProcessor != null) {
            pgnDbProcessor.stopProcessing();
        }
    }

    public void handleThreadMessage(Message msg) {
        Bundle data = msg.getData();
        final int mode = data.getInt("mode", -1);
        final int successCount = data.getInt("successCount", 0);
        final int failCount = data.getInt("failCount", 0);
        dispatchEvent(msg.what, mode, successCount, failCount);
    }

    public class LocalBinder extends Binder {
        public ImportService getService() {
            return ImportService.this;
        }
    }

    protected void dispatchEvent(final int what, final int mode, final int successCount, final int failCount) {
        for (ImportListener listener : listeners) {
            switch (what) {
                case PGNProcessor.MSG_PROCESSED_PGN:
                    listener.OnImportProgress(mode, successCount, failCount);
                    break;
                case PGNProcessor.MSG_FINISHED:
                    // @TODO some final task (e.g. opening process write keys)
                    listener.OnImportFinished(mode);
                    break;
                case PGNProcessor.MSG_FATAL_ERROR:
                    listener.OnImportFatalError(mode);
                    break;
            }
        }
    }

//    protected TreeSet<Long> _arrKeys;
//    protected String _outFile;
//
//    public void readDB(InputStream isDB) {
//        Log.i("import", "readDB executing");
//        _arrKeys.clear();
//        long l;
//        int len;
//        byte[] bytes = new byte[8];
//        try {
//            while ((len = isDB.read(bytes, 0, bytes.length)) != -1) {
//                l = 0L;
//                l |= (long) bytes[0] << 56;
//                l |= (long) bytes[1] << 48;
//                l |= (long) bytes[2] << 40;
//                l |= (long) bytes[3] << 32;
//                l |= (long) bytes[4] << 24;
//                l |= (long) bytes[5] << 16;
//                l |= (long) bytes[6] << 8;
//                l |= (long) bytes[7];
//
//                // assume file keys are allready unique
//
//                _arrKeys.add(l);
//            }
//        } catch (IOException e) {
//            Log.e("import", "readDB: " + e.toString());
//            // TODO Auto-generated catch block
//            e.printStackTrace();
//        }
//
//    }

    private class ThreadMessageHandler extends Handler {
        private WeakReference<ImportService> serverWeakReference;

        ThreadMessageHandler(ImportService importService) {
            this.serverWeakReference = new WeakReference<ImportService>(importService);
        }

        @Override
        public void handleMessage(Message msg) {
            ImportService importService = serverWeakReference.get();
            if (importService != null) {
                importService.handleThreadMessage(msg);
                super.handleMessage(msg);
            }
        }
    }

    private class ImportApi extends GameApi {}
}
