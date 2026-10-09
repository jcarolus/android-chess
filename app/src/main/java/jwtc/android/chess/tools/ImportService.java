package jwtc.android.chess.tools;

import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.database.Cursor;
import android.net.Uri;
import android.os.Binder;
import android.os.IBinder;
import android.provider.OpenableColumns;
import android.util.Log;

import java.io.InputStream;
import java.io.OutputStream;
import java.net.URISyntaxException;
import java.util.ArrayList;

import androidx.annotation.Nullable;

import jwtc.android.chess.helpers.GameStore;
import jwtc.android.chess.helpers.Utils;
import jwtc.android.chess.puzzle.MyPuzzleProvider;
import jwtc.android.chess.services.HMap;


public class ImportService extends Service {
    private static final String TAG = "ImportService";

    public static final int IMPORT_PUZZLES = 1;
    public static final int IMPORT_GAMES = 2;
    public static final int IMPORT_PRACTICE = 3;
    public static final int IMPORT_OPENINGS = 4;
    public static final int PRACTICE_RESET = 8;
    public static final int EXPORT_GAME_DATABASE = 10;
    public static final int PICK_BINARY = 12;

    protected ArrayList<ImportListener> listeners = new ArrayList<>();

    private ImportApi importApi;
    private final IBinder mBinder = new ImportService.LocalBinder();

    private final ImportListener dispatcher = new ImportListener() {
        @Override
        public void OnImportStarted(int mode) {
            for (ImportListener listener : listeners) {
                listener.OnImportStarted(mode);
            }
        }

        @Override
        public void OnImportProgress(int mode, int succeeded, int failed) {
            for (ImportListener listener : listeners) {
                listener.OnImportProgress(mode, succeeded, failed);
            }
        }

        @Override
        public void OnImportFinished(int mode) {
            for (ImportListener listener : listeners) {
                listener.OnImportFinished(mode);
            }
        }

        @Override
        public void OnImportFatalError(int mode) {
            for (ImportListener listener : listeners) {
                listener.OnImportFatalError(mode);
            }
        }
    };

    private final ImportPipeline pipeline = new ImportPipeline(dispatcher);

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

    public void startImport(final Intent intent, final int mode) {
        Uri uri = intent.getData();
        Log.d(TAG, "mode " + mode);
        if (uri != null) {
            Log.d(TAG, "uri " + uri.toString());
        }

        importApi = new ImportApi();

        switch (mode) {
            case IMPORT_PUZZLES:
                Log.d(TAG, "IMPORT_PUZZLES");
                try {
                    pipeline.start(new ImportJob(mode,
                        PgnSource.file(openInput(uri, "puzzles.pgn")),
                        new PuzzleImportHandler(importApi, getContentResolver())));
                } catch (Exception e) {
                    Log.e(TAG, e.toString());
                    dispatcher.OnImportFatalError(mode);
                }
                break;

            case IMPORT_GAMES:
                Log.d(TAG, "IMPORT_GAMES");
                if (uri != null) {
                    try {
                        InputStream in = getContentResolver().openInputStream(uri);
                        pipeline.start(new ImportJob(mode,
                            uri.getPath().lastIndexOf(".zip") > 0 ? PgnSource.zip(in) : PgnSource.file(in),
                            new GameImportHandler(importApi, getContentResolver())));
                    } catch (Exception ex) {
                        Log.e(TAG, ex.toString());
                        dispatcher.OnImportFatalError(mode);
                    }
                }
                break;
            case IMPORT_PRACTICE:
                Log.d(TAG, "IMPORT_PRACTICE");
                try {
                    pipeline.start(new ImportJob(mode,
                        PgnSource.file(openInput(uri, "practice.pgn")),
                        new PracticeImportHandler(importApi, getContentResolver())));
                } catch (Exception e) {
                    Log.e(TAG, e.toString());
                    dispatcher.OnImportFatalError(mode);
                }
                break;
            case IMPORT_OPENINGS:
                Log.d(TAG, "IMPORT_OPENINGS " + uri);
                if (uri != null) {
                    try {
                        pipeline.start(new ImportJob(mode,
                            new OpeningJsonSource(getContentResolver().openInputStream(uri)),
                            new OpeningImportHandler(importApi)));
                    } catch (Exception ex) {
                        Log.e(TAG, ex.toString());
                        dispatcher.OnImportFatalError(mode);
                    }
                }
                break;
            case PRACTICE_RESET:
                Log.d(TAG, "PRACTICE_RESET");
                try {
                    SharedPreferences prefs = getSharedPreferences("ChessPlayer", MODE_PRIVATE);
                    SharedPreferences.Editor editor = prefs.edit();
                    editor.putInt("practicePos", 0);
                    editor.putInt("practiceTicks", 0);
                    editor.commit();

                    getContentResolver().delete(MyPuzzleProvider.CONTENT_URI_PRACTICES, "1=1", null);

                    dispatcher.OnImportFinished(mode);

                } catch (Exception e) {
                    Log.e(TAG, e.toString());
                    dispatcher.OnImportFatalError(mode);
                }
                break;
            case EXPORT_GAME_DATABASE:
                Log.d(TAG, "EXPORT_GAME_DATABASE");
                try {
                    if (uri != null) {
                        OutputStream fos = getContentResolver().openOutputStream(uri);
                        String PGN = collectGameDatabaseAsPGN();
                        fos.write(PGN.getBytes());
                        fos.flush();
                        fos.close();

                        dispatcher.OnImportFinished(mode);
                    }
                } catch (Exception e) {
                    Log.e(TAG, e.toString());
                    dispatcher.OnImportFatalError(mode);
                }
                break;
            case PICK_BINARY:
                Log.d(TAG, "PICK_BINARY");
                ArrayList<HMap.Pair> hashMap = importApi.getHashMap();
                if (hashMap != null) {

                    Log.d(TAG, "Pick binary " + hashMap.size());

                    try {
                        getContentResolver().takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION | Intent.FLAG_GRANT_WRITE_URI_PERMISSION);

                        writeHMapToDownloads(uri, hashMap);

                        Log.d(TAG, "Wrote hashMap");

                    } catch (Exception ex) {
                        Log.d(TAG, "Could not get the flags " + ex.getMessage());
                    }
                } else {
                    Log.d(TAG, "No hashmap");
                }

                dispatcher.OnImportFinished(mode);

                break;
        }
    }

    /** The file the user picked, or the bundled asset when none was picked. */
    private InputStream openInput(@Nullable Uri uri, String assetName) throws java.io.IOException {
        return uri == null ? getAssets().open(assetName) : getContentResolver().openInputStream(uri);
    }

    @Override
    public void onDestroy() {
        Log.i(TAG, "onDestroy");
        pipeline.cancel();
    }

    public class LocalBinder extends Binder {
        public ImportService getService() {
            return ImportService.this;
        }
    }

    protected String getUriDisplayName(Context context, Uri uri) throws URISyntaxException {
        Log.d(TAG, "getUriDisplayName " + uri.getScheme());

        if ("content".equalsIgnoreCase(uri.getScheme())) {
            Cursor cursor = null;
            try {
                cursor = context.getContentResolver().query(uri, null, null, null, null);
                if (cursor.moveToFirst()) {
                    return Utils.getColumnString(cursor, OpenableColumns.DISPLAY_NAME);
                }
            } catch (Exception e) {
                Log.d(TAG, e.getMessage());
            }
        } else if ("file".equalsIgnoreCase(uri.getScheme())) {
            return uri.getLastPathSegment();
        }

        return null;
    }

    protected String collectGameDatabaseAsPGN() {
        return new GameStore(getBaseContext().getContentResolver()).exportAllAsPgn();
    }


    public void writeHMapToDownloads(Uri uri, ArrayList<HMap.Pair> list) {
        Log.d(TAG, "write Hashmap with size:" + list.size());

        try {
            HMap.write(this, uri, list);
        } catch (Exception e) {
            Log.d(TAG, "An error during writing of hash map " + e.getMessage());
        }
    }
}
