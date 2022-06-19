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

    public static final String IMPORT_MODE = "import_mode";
    public static final int IMPORT_PUZZLES = 1;
    public static final int IMPORT_GAMES = 2;
    public static final int IMPORT_PRACTICE = 3;
    public static final int IMPORT_OPENINGS = 4;
    public static final int IMPORT_DATABASE = 5;

    protected ArrayList<ImportListener> listeners = new ArrayList<>();
    private PGNProcessor _processor; // @TODO could be multiple
    private ImportApi importApi;
    private final IBinder mBinder = new ImportService.LocalBinder();

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

        if (intent == null) {
            return START_STICKY;
        }
        final Uri uri = intent.getData();
        Bundle extras = intent.getExtras();

        final int mode = extras.getInt(IMPORT_MODE);
        Handler updateHandler = new ImportService.ThreadMessageHandler(this);

        Log.d(TAG, "mode " + mode);

        importApi = new ImportApi();

        switch (mode) {
            case IMPORT_PUZZLES:
                _processor = new PuzzleImportProcessor(mode, updateHandler, importApi, getContentResolver());

                InputStream isPuzzles;
                try {
                    if (uri == null) {
                        isPuzzles = getAssets().open("puzzles.pgn");
                    } else {
                        isPuzzles = getContentResolver().openInputStream(uri);
                    }
                    _processor.processPGNFile(isPuzzles);
                } catch (IOException e) {
                    dispatchEvent(PGNProcessor.MSG_FATAL_ERROR, mode);
                    _processor = null;
                }

                break;

            case IMPORT_GAMES:
                if (uri != null) {
                    _processor = new GameImportProcessor(mode, updateHandler, importApi, getContentResolver());
                    try {
                        InputStream isGames = getContentResolver().openInputStream(uri);
                        if (uri.getPath().lastIndexOf(".zip") > 0) {
                            _processor.processZipFile(isGames);
                        } else {
                            _processor.processPGNFile(isGames);
                        }
                    } catch (Exception ex) {
                        Log.e(TAG, ex.toString());
                        dispatchEvent(PGNProcessor.MSG_FATAL_ERROR, mode);
                        _processor = null;
                    }
                }
                break;
            case IMPORT_PRACTICE:
                _processor = new PracticeImportProcessor(mode, updateHandler, importApi, getContentResolver());
                try {
                    InputStream isPractice;
                    if (uri == null) {
                        isPractice = getAssets().open("practice.pgn");
                    } else {
                        isPractice = getContentResolver().openInputStream(uri);
                    }
                    _processor.processPGNFile(isPractice);
                } catch (IOException e) {
                    dispatchEvent(PGNProcessor.MSG_FATAL_ERROR, mode);
                    _processor = null;
                }
                break;
            case IMPORT_OPENINGS:
                if (uri != null) {
                    _processor = new OpeningImportProcessor(mode, updateHandler, importApi);
                    try {
                        InputStream isOpenings = getContentResolver().openInputStream(uri);
                        _processor.processPGNFile(isOpenings);

                    } catch (Exception ex) {
                        Log.e(TAG, ex.toString());
                        dispatchEvent(PGNProcessor.MSG_FATAL_ERROR, mode);
                        _processor = null;
                    }
                }
                break;
            case IMPORT_DATABASE:
                if (uri != null) {
                    _processor = new PGNDbProcessor(mode, updateHandler, importApi);
                    try {
                        InputStream isDatabase = getContentResolver().openInputStream(uri);
                        _processor.processPGNFile(isDatabase);

                    } catch (Exception ex) {
                        Log.e(TAG, ex.toString());
                        dispatchEvent(PGNProcessor.MSG_FATAL_ERROR, mode);
                        _processor = null;
                    }
                }
                break;
        }

        return START_STICKY;
    }

    @Override
    public void onDestroy() {
        Log.i(TAG, "onDestroy");

        if (_processor != null) {
            _processor.stopProcessing();
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
                    listener.OnImportSuccess(mode);
                    break;
                case PGNProcessor.MSG_FAILED_PGN:
                    listener.OnImportFail(mode);
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
