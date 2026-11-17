package jwtc.android.chess.matecollection;

import android.content.Context;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.widget.Toast;
import androidx.appcompat.app.AlertDialog;
import jwtc.android.chess.R;
import jwtc.android.chess.helpers.ChessAlertDialogBuilder;

/** Default Positions and the user's choice to keep a Mate Collection empty. */
public final class MateCollectionDefaults {
    private static final int AUTOMATIC = 0;
    private static final int RESTORE_PENDING = 1;
    private static final int KEEP_EMPTY = 2;
    private static final String CHECKED = "collectionDefaultsChecked";
    private static final String PROMPT_VISIBLE = "collectionRestorePromptVisible";

    private final Context context;
    private final MateKind kind;
    private final Runnable importDefaults;
    private boolean checkedOnEntry;
    private boolean recreatePrompt;
    private AlertDialog dialog;

    public MateCollectionDefaults(
            Context context, MateKind kind, Bundle savedState, Runnable importDefaults) {
        this.context = context;
        this.kind = kind;
        this.importDefaults = importDefaults;
        checkedOnEntry = savedState != null && savedState.getBoolean(CHECKED);
        recreatePrompt = savedState != null && savedState.getBoolean(PROMPT_VISIBLE);
    }

    public void onEmptyCollection() {
        if (checkedOnEntry && !recreatePrompt) {
            return;
        }
        checkedOnEntry = true;
        recreatePrompt = false;
        int state = preferences(context).getInt(key(kind), AUTOMATIC);
        if (state == AUTOMATIC) {
            restoreDefaults();
        } else if (state == RESTORE_PENDING) {
            dialog =
                    new ChessAlertDialogBuilder(context)
                            .setMessage(R.string.collection_restore_prompt)
                            .setPositiveButton(
                                    R.string.alert_yes,
                                    (view, which) -> {
                                        dialog = null;
                                        restoreDefaults();
                                    })
                            .setNegativeButton(R.string.alert_no, (view, which) -> declineRestore())
                            .setOnCancelListener(view -> declineRestore())
                            .create();
            dialog.show();
        }
    }

    public void deleteAllPositions() {
        // Remember the deletion before removing rows, so an interrupted deletion cannot
        // cause an empty collection to be restored automatically on the next launch.
        if (!preferences(context).edit().putInt(key(kind), RESTORE_PENDING).commit()) {
            Toast.makeText(context, R.string.collection_delete_failed, Toast.LENGTH_LONG).show();
            return;
        }
        checkedOnEntry = true;
        MateCollection.clearCollection(context, kind);
    }

    public void restoreDefaults() {
        checkedOnEntry = true;
        importDefaults.run();
    }

    private void declineRestore() {
        dialog = null;
        preferences(context).edit().putInt(key(kind), KEEP_EMPTY).apply();
    }

    static void positionsAvailable(Context context, MateKind kind) {
        SharedPreferences prefs = preferences(context);
        if (prefs.getInt(key(kind), AUTOMATIC) == RESTORE_PENDING) {
            prefs.edit().putInt(key(kind), KEEP_EMPTY).apply();
        }
    }

    public void saveState(Bundle state) {
        state.putBoolean(CHECKED, checkedOnEntry);
        state.putBoolean(PROMPT_VISIBLE, dialog != null && dialog.isShowing());
    }

    public void close() {
        if (dialog != null) {
            dialog.dismiss();
        }
    }

    private static SharedPreferences preferences(Context context) {
        return context.getSharedPreferences("ChessPlayer", Context.MODE_PRIVATE);
    }

    private static String key(MateKind kind) {
        return kind.positionKey + ".defaults";
    }
}
