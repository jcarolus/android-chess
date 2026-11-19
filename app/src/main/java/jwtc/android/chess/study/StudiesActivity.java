package jwtc.android.chess.study;

import android.content.ActivityNotFoundException;
import android.content.Intent;
import android.net.Uri;
import android.util.Log;
import android.widget.Toast;
import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import java.util.concurrent.CompletableFuture;
import jwtc.android.chess.R;
import jwtc.android.chess.analyze.AnalyzeActivity;
import jwtc.android.chess.gamedatabase.GameSelectionActivity;
import jwtc.android.chess.gamedatabase.GameSelectionPreferences;
import jwtc.android.chess.helpers.ChessAlertDialogBuilder;
import jwtc.android.chess.play.MenuDialog;
import jwtc.android.chess.tools.ImportActivity;
import jwtc.android.chess.tools.ImportService;
import jwtc.chess.GameSelection;

public final class StudiesActivity extends GameSelectionActivity {
    private static final Uri DOWNLOADS = Uri.parse("https://www.jwtc.nl/files/#studies");

    @Override
    protected String preferencePrefix() {
        return GameSelectionPreferences.STUDY;
    }

    @Override
    protected boolean isStudySelection() {
        return true;
    }

    @Override
    protected void openSelection(GameSelection selection, boolean random) {
        startActivity(AnalyzeActivity.studyIntent(this, selection, random));
    }

    @Override
    protected void configureMenu() {
        ActivityResultLauncher<String[]> pickFile =
                registerForActivityResult(
                        new ActivityResultContracts.OpenDocument(),
                        uri -> {
                            if (uri != null) {
                                startActivity(
                                        new Intent(this, ImportActivity.class)
                                                .setData(uri)
                                                .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                                                .putExtra("mode", ImportService.IMPORT_STUDIES));
                            }
                        });
        findViewById(R.id.ButtonMenu)
                .setOnClickListener(
                        v ->
                                new MenuDialog(
                                                this,
                                                (request, data) ->
                                                        onMenuItem(
                                                                data.getString("item"), pickFile),
                                                0,
                                                R.array.studies_menu)
                                        .show());
    }

    private void onMenuItem(String item, ActivityResultLauncher<String[]> pickFile) {
        if (getString(R.string.study_import).equals(item)) {
            pickFile.launch(new String[] {"*/*"});
        } else if (getString(R.string.study_download_collections).equals(item)) {
            try {
                startActivity(new Intent(Intent.ACTION_VIEW, DOWNLOADS));
            } catch (ActivityNotFoundException error) {
                Toast.makeText(this, DOWNLOADS.toString(), Toast.LENGTH_LONG).show();
            }
        } else if (getString(R.string.study_delete_matching).equals(item) && canSubmit()) {
            confirmDelete();
        }
    }

    private void confirmDelete() {
        GameSelection selection = currentSelection();
        int count = matchingCount;
        new ChessAlertDialogBuilder(this)
                .setTitle(R.string.study_delete_matching)
                .setMessage(
                        getString(R.string.study_delete_filtered_confirmation)
                                + "\n\n"
                                + getString(R.string.pgntool_delete_item_count, count))
                .setNegativeButton(R.string.button_cancel, null)
                .setPositiveButton(
                        R.string.choice_delete, (dialog, which) -> deleteMatching(selection, count))
                .show();
    }

    private void deleteMatching(GameSelection selection, int count) {
        setBusy(true);
        CompletableFuture.supplyAsync(
                        () ->
                                new StudyStore(getContentResolver())
                                        .deleteMatching(selection.filter(), count))
                .whenComplete(
                        (deleted, error) ->
                                runOnUiThread(
                                        () -> {
                                            if (isDestroyed() || isFinishing()) {
                                                return;
                                            }
                                            if (error != null || deleted != count) {
                                                Log.w(
                                                        "Studies",
                                                        "Could not delete selected studies",
                                                        error);
                                                doToast(getString(R.string.study_delete_failed));
                                            } else {
                                                getPrefs()
                                                        .edit()
                                                        .remove(selection.resumeKey())
                                                        .apply();
                                            }
                                            setBusy(false);
                                        }));
    }
}
