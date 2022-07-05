package jwtc.android.chess.tools;

import android.app.Service;
import android.content.Intent;
import android.net.Uri;
import android.os.Binder;
import android.os.Bundle;
import android.os.Handler;
import android.os.IBinder;
import android.os.Message;
import android.util.Log;

import java.io.IOException;
import java.io.InputStream;
import java.lang.ref.WeakReference;
import java.util.ArrayList;

import androidx.annotation.Nullable;
import jwtc.android.chess.services.GameApi;


public class ImportService extends Service {
    private static final String TAG = "ImportService";

    public static final int IMPORT_PUZZLES = 1;
    public static final int IMPORT_GAMES = 2;
    public static final int IMPORT_PRACTICE = 3;
    public static final int IMPORT_OPENINGS = 4;
    public static final int IMPORT_DATABASE = 5;

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
                    dispatchEvent(PGNProcessor.MSG_FATAL_ERROR, mode);
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
                        dispatchEvent(PGNProcessor.MSG_FATAL_ERROR, mode);
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
                    dispatchEvent(PGNProcessor.MSG_FATAL_ERROR, mode);
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
                        dispatchEvent(PGNProcessor.MSG_FATAL_ERROR, mode);
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
                        dispatchEvent(PGNProcessor.MSG_FATAL_ERROR, mode);
                    }
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
        dispatchEvent(msg.what, mode);
    }

    public class LocalBinder extends Binder {
        public ImportService getService() {
            return ImportService.this;
        }
    }

    protected void dispatchEvent(int what, int mode) {
        for (ImportListener listener : listeners) {
            switch (what) {
                case PGNProcessor.MSG_PROCESSED_PGN:
                    listener.OnImportProgress(mode);
                    break;
                case PGNProcessor.MSG_FINISHED:
                    listener.OnImportFinished(mode);
                    break;
                case PGNProcessor.MSG_FATAL_ERROR:
                    listener.OnImportFatalError(mode);
                    break;
            }
        }
    }

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
