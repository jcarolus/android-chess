package jwtc.android.chess.study;

import android.content.ActivityNotFoundException;
import android.content.Intent;
import android.content.SharedPreferences;
import android.net.Uri;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.widget.TextView;
import android.widget.Toast;
import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import jwtc.android.chess.R;
import jwtc.android.chess.activities.BaseActivity;
import jwtc.android.chess.analyze.AnalyzeActivity;
import jwtc.android.chess.helpers.ActivityHelper;
import jwtc.android.chess.helpers.ChessAlertDialogBuilder;
import jwtc.android.chess.tools.ImportActivity;
import jwtc.android.chess.tools.ImportService;
import jwtc.android.chess.views.FixedDropdownView;

public final class StudiesActivity extends BaseActivity {
    private static final Uri DOWNLOADS = Uri.parse("https://www.jwtc.nl/files/#studies");
    private static final String PREF_TOPIC = "studies_filter_topic";
    private static final String PREF_EVENT = "studies_filter_event";

    private StudyStore store;
    private Map<String, Map<String, Integer>> selections = Collections.emptyMap();
    private FixedDropdownView topics, events;
    private View filters;
    private TextView status;
    private String topic, event;
    private int libraryRequest;

    @Override
    public void onCreate(Bundle state) {
        super.onCreate(state);
        setContentView(R.layout.study_selector);
        ActivityHelper.fixPaddings(this, findViewById(R.id.StudiesRoot));
        store = new StudyStore(getContentResolver());
        filters = findViewById(R.id.StudyFilters);
        status = findViewById(R.id.StudyStatus);
        topics = findViewById(R.id.StudyTopic);
        events = findViewById(R.id.StudyEvent);
        topics.setSaveFromParentEnabled(false);
        events.setSaveFromParentEnabled(false);
        ActivityResultLauncher<String[]> pickFile =
                registerForActivityResult(
                        new ActivityResultContracts.OpenDocument(),
                        uri -> {
                            if (uri != null) {
                                Intent intent = new Intent(this, ImportActivity.class);
                                intent.setData(uri);
                                intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
                                intent.putExtra("mode", ImportService.IMPORT_STUDIES);
                                startActivity(intent);
                            }
                        });
        findViewById(R.id.StudyImport)
                .setOnClickListener(v -> pickFile.launch(new String[] {"*/*"}));
        findViewById(R.id.StudyDownloads)
                .setOnClickListener(
                        v -> {
                            try {
                                startActivity(new Intent(Intent.ACTION_VIEW, DOWNLOADS));
                            } catch (ActivityNotFoundException error) {
                                Toast.makeText(this, DOWNLOADS.toString(), Toast.LENGTH_LONG)
                                        .show();
                            }
                        });
        findViewById(R.id.StudyOpen).setOnClickListener(v -> openStudy(false));
        findViewById(R.id.StudySurprise).setOnClickListener(v -> openStudy(true));
        findViewById(R.id.StudyDelete).setOnClickListener(v -> confirmDelete());
    }

    @Override
    protected void onResume() {
        super.onResume();
        SharedPreferences prefs = getPrefs();
        topic = prefs.getString(PREF_TOPIC, null);
        event = prefs.getString(PREF_EVENT, null);
        loadLibrary();
    }

    @Override
    protected void onPause() {
        super.onPause();
        getPrefs().edit().putString(PREF_TOPIC, topic).putString(PREF_EVENT, event).apply();
    }

    private void loadLibrary() {
        int request = ++libraryRequest;
        filters.setVisibility(View.GONE);
        status.setVisibility(View.VISIBLE);
        status.setText(R.string.study_loading);
        CompletableFuture.supplyAsync(store::selections)
                .whenComplete(
                        (result, error) ->
                                runOnUiThread(
                                        () -> {
                                            if (request != libraryRequest
                                                    || isDestroyed()
                                                    || isFinishing()) {
                                                return;
                                            }
                                            if (error != null) {
                                                Log.w(
                                                        "Studies",
                                                        "Could not read study library",
                                                        error);
                                                status.setText(R.string.study_load_failed);
                                            } else if (result.isEmpty()) {
                                                status.setText(R.string.study_library_empty);
                                            } else {
                                                selections = result;
                                                showTopics();
                                                status.setVisibility(View.GONE);
                                                filters.setVisibility(View.VISIBLE);
                                            }
                                        }));
    }

    private void showTopics() {
        List<String> names = new ArrayList<>(selections.keySet());
        topics.setItems(names);
        int index = Math.max(0, names.indexOf(topic));
        topics.setSelection(index);
        topic = names.get(index);
        topics.setOnItemClickListener(
                (parent, row, position, id) -> {
                    topic = names.get(position);
                    showEvents();
                });
        showEvents();
    }

    private void showEvents() {
        List<String> names = new ArrayList<>(selections.get(topic).keySet());
        events.setItems(names);
        int index = Math.max(0, names.indexOf(event));
        events.setSelection(index);
        event = names.get(index);
        events.setOnItemClickListener((parent, row, position, id) -> event = names.get(position));
    }

    private void openStudy(boolean surprise) {
        startActivity(AnalyzeActivity.studyIntent(this, topic, event, surprise));
    }

    private void confirmDelete() {
        String selectedTopic = topic;
        String selectedEvent = event;
        int count = selections.get(selectedTopic).get(selectedEvent);
        new ChessAlertDialogBuilder(this)
                .setTitle(R.string.study_delete_matching)
                .setMessage(
                        getString(R.string.study_delete_confirmation, selectedTopic, selectedEvent)
                                + "\n\n"
                                + getString(R.string.pgntool_delete_item_count, count))
                .setNegativeButton(R.string.button_cancel, null)
                .setPositiveButton(
                        R.string.choice_delete,
                        (dialog, which) -> deleteMatching(selectedTopic, selectedEvent, count))
                .show();
    }

    private void deleteMatching(String selectedTopic, String selectedEvent, int count) {
        filters.setVisibility(View.GONE);
        CompletableFuture.supplyAsync(
                        () -> store.deleteMatching(selectedTopic, selectedEvent, count))
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
                                                Toast.makeText(
                                                                this,
                                                                R.string.study_delete_failed,
                                                                Toast.LENGTH_LONG)
                                                        .show();
                                            } else {
                                                getPrefs()
                                                        .edit()
                                                        .remove(
                                                                StudyStore.resumeKey(
                                                                        selectedTopic,
                                                                        selectedEvent))
                                                        .apply();
                                            }
                                            loadLibrary();
                                        }));
    }
}
