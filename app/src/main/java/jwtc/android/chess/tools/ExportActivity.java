package jwtc.android.chess.tools;

import android.app.Application;
import android.content.ContentResolver;
import android.content.Context;
import android.content.Intent;
import android.database.Cursor;
import android.net.Uri;
import android.os.Bundle;
import android.provider.DocumentsContract;
import android.provider.OpenableColumns;
import android.util.Log;
import android.view.View;
import android.widget.TextView;
import android.widget.Toast;
import androidx.activity.OnBackPressedCallback;
import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AlertDialog;
import androidx.lifecycle.AndroidViewModel;
import androidx.lifecycle.MutableLiveData;
import androidx.lifecycle.ViewModelProvider;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import jwtc.android.chess.R;
import jwtc.android.chess.activities.BaseActivity;
import jwtc.android.chess.helpers.ActivityHelper;
import jwtc.android.chess.helpers.ChessAlertDialogBuilder;
import jwtc.android.chess.helpers.GameStore;
import jwtc.chess.GameSelection;
import jwtc.chess.PgnExport;

/** Owns a PGN snapshot and destination selection independently of the board's lifecycle. */
public class ExportActivity extends BaseActivity {
    private static final String SELECTED_IDS = "exportSelectedIds";
    private static final String SNAPSHOT = "exportSnapshot";
    private static final String STATUS = "exportStatus";
    private static final String MODE = "exportMode";
    private static final String DESTINATION = "exportDestination";
    private static final int PREPARING = 0,
            CHOOSE = 1,
            PICKING = 2,
            CONFIRM = 3,
            WRITING = 4,
            DONE = 5,
            FAILED = 6,
            PARTIAL = 7,
            EMPTY = 8;
    private static final int CREATE = 0, APPEND = 1, REPLACE = 2;
    private ExportModel model;
    private AlertDialog confirmation;
    private ActivityResultLauncher<String> createFile;
    private ActivityResultLauncher<String[]> openFile;

    public static void exportGame(Context context, String pgn) {
        File snapshot = null;
        try {
            // The picker can outlive cache eviction when device storage is low.
            snapshot = File.createTempFile("export-", ".pgn", context.getNoBackupFilesDir());
            try (OutputStream output = new FileOutputStream(snapshot)) {
                PgnExport.writeGame(output, pgn);
            }
            context.startActivity(
                    new Intent(context, ExportActivity.class)
                            .putExtra(SNAPSHOT, snapshot.getAbsolutePath()));
        } catch (IOException failure) {
            if (snapshot != null) {
                snapshot.delete();
            }
            Log.e("PgnExport", "Could not prepare export", failure);
            Toast.makeText(context, R.string.export_failed, Toast.LENGTH_LONG).show();
        }
    }

    public static void exportDatabase(Context context, GameSelection selection) {
        context.startActivity(
                new Intent(context, ExportActivity.class).putExtra(GameSelection.EXTRA, selection));
    }

    public static void exportSelectedGames(Context context, long[] ids) {
        context.startActivity(
                new Intent(context, ExportActivity.class).putExtra(SELECTED_IDS, ids.clone()));
    }

    @Override
    public void onCreate(Bundle state) {
        super.onCreate(state);
        setContentView(R.layout.export_pgn);
        ActivityHelper.fixPaddings(this, findViewById(R.id.ExportRoot));
        createFile =
                registerForActivityResult(
                        new ActivityResultContracts.CreateDocument("application/x-chess-pgn"),
                        this::destinationSelected);
        openFile =
                registerForActivityResult(
                        new ActivityResultContracts.OpenDocument(), this::destinationSelected);
        model = new ViewModelProvider(this).get(ExportModel.class);
        findViewById(R.id.ExportCreate).setOnClickListener(view -> choose(CREATE));
        findViewById(R.id.ExportAppend).setOnClickListener(view -> choose(APPEND));
        findViewById(R.id.ExportReplace).setOnClickListener(view -> choose(REPLACE));
        findViewById(R.id.ExportClose).setOnClickListener(view -> finish());
        getOnBackPressedDispatcher()
                .addCallback(
                        this,
                        new OnBackPressedCallback(true) {
                            @Override
                            public void handleOnBackPressed() {
                                if (model.status.getValue() == null
                                        || model.status.getValue() != WRITING) {
                                    finish();
                                }
                            }
                        });
        model.initialize(getIntent(), state);
        model.status.observe(this, this::render);
    }

    private void choose(int mode) {
        model.mode = mode;
        model.status.setValue(PICKING);
        try {
            if (mode == CREATE) {
                createFile.launch(
                        getIntent().hasExtra(GameSelection.EXTRA)
                                        || getIntent().hasExtra(SELECTED_IDS)
                                ? "chess.pgn"
                                : "game.pgn");
            } else {
                openFile.launch(new String[] {"*/*"});
            }
        } catch (RuntimeException failure) {
            Log.e("PgnExport", "Could not open document picker", failure);
            model.status.setValue(FAILED);
        }
    }

    private void destinationSelected(Uri uri) {
        if (uri == null) {
            model.status.setValue(CHOOSE);
            return;
        }
        model.destination = uri;
        if (model.mode == REPLACE) {
            model.status.setValue(CONFIRM);
        } else {
            model.write();
        }
    }

    private void render(int status) {
        boolean choose =
                (status == CHOOSE || status == FAILED || status == PARTIAL)
                        && model.snapshot != null
                        && model.snapshot.isFile()
                        && model.snapshot.length() > 0;
        findViewById(R.id.ExportActions).setVisibility(choose ? View.VISIBLE : View.GONE);
        findViewById(R.id.ExportProgress)
                .setVisibility(status == PREPARING || status == WRITING ? View.VISIBLE : View.GONE);
        findViewById(R.id.ExportClose).setEnabled(status != WRITING);
        int message = R.string.export_choose_action;
        switch (status) {
            case PREPARING:
                message = R.string.export_preparing;
                break;
            case WRITING:
                message = R.string.export_writing;
                break;
            case DONE:
                message = R.string.export_done;
                break;
            case FAILED:
                message = R.string.export_failed;
                break;
            case PARTIAL:
                message = R.string.export_partial;
                break;
            case EMPTY:
                message = R.string.export_no_games;
                break;
        }
        ((TextView) findViewById(R.id.ExportStatus)).setText(message);
        if (status == CONFIRM && confirmation == null) {
            confirmation =
                    new ChessAlertDialogBuilder(this)
                            .setTitle(R.string.export_replace)
                            .setMessage(
                                    getString(R.string.export_replace_confirm, destinationName()))
                            .setPositiveButton(
                                    R.string.export_replace, (dialog, which) -> model.write())
                            .setNegativeButton(
                                    R.string.button_cancel,
                                    (dialog, which) -> model.status.setValue(CHOOSE))
                            .setOnCancelListener(dialog -> model.status.setValue(CHOOSE))
                            .create();
            confirmation.setOnDismissListener(dialog -> confirmation = null);
            confirmation.show();
        }
    }

    private String destinationName() {
        try (Cursor cursor =
                getContentResolver()
                        .query(
                                model.destination,
                                new String[] {OpenableColumns.DISPLAY_NAME},
                                null,
                                null,
                                null)) {
            if (cursor != null && cursor.moveToFirst() && !cursor.isNull(0)) {
                return cursor.getString(0);
            }
        } catch (RuntimeException failure) {
            Log.w("PgnExport", "Could not read destination name", failure);
        }
        return model.destination.toString();
    }

    @Override
    protected void onSaveInstanceState(@NonNull Bundle state) {
        super.onSaveInstanceState(state);
        state.putInt(STATUS, model.status.getValue() == null ? PREPARING : model.status.getValue());
        state.putInt(MODE, model.mode);
        state.putParcelable(DESTINATION, model.destination);
        if (model.snapshot != null) {
            state.putString(SNAPSHOT, model.snapshot.getAbsolutePath());
        }
    }

    @Override
    protected void onDestroy() {
        if (confirmation != null) {
            confirmation.dismiss();
        }
        super.onDestroy();
    }

    public static class ExportModel extends AndroidViewModel {
        final MutableLiveData<Integer> status = new MutableLiveData<>();
        private final ExecutorService executor = Executors.newSingleThreadExecutor();
        volatile File snapshot;
        int mode;
        Uri destination;

        public ExportModel(@NonNull Application application) {
            super(application);
        }

        void initialize(Intent intent, Bundle state) {
            if (status.getValue() != null) {
                return;
            }
            if (state != null && state.getInt(STATUS) != PREPARING) {
                mode = state.getInt(MODE);
                destination = state.getParcelable(DESTINATION);
                String path = state.getString(SNAPSHOT);
                snapshot = path == null ? null : new File(path);
                int restored = state.getInt(STATUS);
                // A write interrupted by process death must never be replayed.
                status.setValue(restored == WRITING ? PARTIAL : restored);
                return;
            }
            String path = intent.getStringExtra(SNAPSHOT);
            if (path != null) {
                snapshot = new File(path);
                status.setValue(snapshot.isFile() && snapshot.length() > 0 ? CHOOSE : FAILED);
                return;
            }
            status.setValue(PREPARING);
            GameSelection selection =
                    (GameSelection) intent.getSerializableExtra(GameSelection.EXTRA);
            long[] ids = intent.getLongArrayExtra(SELECTED_IDS);
            executor.execute(
                    () -> {
                        File prepared = null;
                        try {
                            if (ids == null && (selection == null || !selection.hasValidDates())) {
                                throw new IOException("Invalid export selection");
                            }
                            prepared =
                                    File.createTempFile(
                                            "export-",
                                            ".pgn",
                                            getApplication().getNoBackupFilesDir());
                            int games;
                            try (OutputStream output = new FileOutputStream(prepared)) {
                                GameStore store =
                                        new GameStore(getApplication().getContentResolver());
                                games =
                                        ids == null
                                                ? store.exportAsPgn(selection.filter(), output)
                                                : store.exportSelectedAsPgn(ids, output);
                            }
                            snapshot = prepared;
                            status.postValue(games == 0 ? EMPTY : CHOOSE);
                        } catch (Exception failure) {
                            if (prepared != null) {
                                prepared.delete();
                            }
                            Log.e("PgnExport", "Could not prepare export", failure);
                            status.postValue(FAILED);
                        }
                    });
        }

        void write() {
            status.setValue(WRITING);
            Uri uri = destination;
            int operation = mode;
            executor.execute(
                    () -> {
                        boolean mayHaveChanged = false;
                        try (FileInputStream input = new FileInputStream(snapshot)) {
                            if (snapshot.length() == 0) {
                                throw new IOException("No games to export");
                            }
                            ContentResolver resolver = getApplication().getContentResolver();
                            if (operation != CREATE) {
                                requireWritable(resolver, uri);
                            }
                            // Opening with truncation can change the file even before the first
                            // write.
                            mayHaveChanged = true;
                            try (OutputStream output =
                                    resolver.openOutputStream(
                                            uri, operation == APPEND ? "wa" : "wt")) {
                                if (output == null) {
                                    throw new IOException("Could not open destination");
                                }
                                PgnExport.copy(input, output, operation == APPEND);
                                output.flush();
                            }
                            status.postValue(DONE);
                        } catch (Exception failure) {
                            Log.e("PgnExport", "Could not export PGN", failure);
                            status.postValue(mayHaveChanged ? PARTIAL : FAILED);
                        }
                    });
        }

        private static void requireWritable(ContentResolver resolver, Uri uri) throws IOException {
            try (Cursor cursor =
                    resolver.query(
                            uri,
                            new String[] {DocumentsContract.Document.COLUMN_FLAGS},
                            null,
                            null,
                            null)) {
                if (cursor == null
                        || !cursor.moveToFirst()
                        || (cursor.getInt(0) & DocumentsContract.Document.FLAG_SUPPORTS_WRITE)
                                == 0) {
                    throw new IOException("Destination does not support writing");
                }
            }
        }

        @Override
        protected void onCleared() {
            executor.execute(
                    () -> {
                        if (snapshot != null) {
                            snapshot.delete();
                        }
                    });
            executor.shutdown();
        }
    }
}
