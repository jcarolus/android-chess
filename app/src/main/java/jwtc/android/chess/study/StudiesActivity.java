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
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.concurrent.CompletableFuture;
import java.util.function.IntConsumer;
import jwtc.android.chess.R;
import jwtc.android.chess.activities.BaseActivity;
import jwtc.android.chess.analyze.AnalyzeActivity;
import jwtc.android.chess.helpers.ActivityHelper;
import jwtc.android.chess.study.StudyStore.Study;
import jwtc.android.chess.tools.ImportActivity;
import jwtc.android.chess.tools.ImportService;
import jwtc.android.chess.views.FixedDropdownView;

public final class StudiesActivity extends BaseActivity {
    private static final Uri DOWNLOADS = Uri.parse("https://www.jwtc.nl/files/#studies");
    private final Random random = new Random();
    private List<Study> library = Collections.emptyList();
    private List<Study> filtered = Collections.emptyList();
    private FixedDropdownView collections, topics, authors, studies;
    private View filters;
    private TextView status;
    private Study selected;
    private String collectionId, topic, author, studyCollectionId, studyId;
    private int libraryRequest;

    @Override
    public void onCreate(Bundle state) {
        super.onCreate(state);
        setContentView(R.layout.study_selector);
        ActivityHelper.fixPaddings(this, findViewById(R.id.StudiesRoot));
        filters = findViewById(R.id.StudyFilters);
        status = findViewById(R.id.StudyStatus);
        collections = findViewById(R.id.StudyCollection);
        topics = findViewById(R.id.StudyTopic);
        authors = findViewById(R.id.StudyAuthor);
        studies = findViewById(R.id.StudyName);
        for (View field : new View[] {collections, topics, authors, studies}) {
            field.setSaveFromParentEnabled(false);
        }
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
        findViewById(R.id.StudyImport).setOnClickListener(v -> pickFile.launch(new String[] {"*/*"}));
        findViewById(R.id.StudyDownloads)
                .setOnClickListener(
                        v -> {
                            try {
                                startActivity(new Intent(Intent.ACTION_VIEW, DOWNLOADS));
                            } catch (ActivityNotFoundException error) {
                                Toast.makeText(this, DOWNLOADS.toString(), Toast.LENGTH_LONG).show();
                            }
                        });
        findViewById(R.id.StudyOpen).setOnClickListener(v -> openChapter(0));
        findViewById(R.id.StudySurprise)
                .setOnClickListener(
                        v -> {
                            int index = random.nextInt(filtered.size());
                            studies.setSelection(index);
                            selectStudy(filtered.get(index));
                            int chapter = random.nextInt(selected.chapters.size());
                            openChapter(chapter);
                        });
    }

    @Override
    protected void onResume() {
        super.onResume();
        SharedPreferences prefs = getPrefs();
        collectionId = prefs.getString("studies_filter_collection", null);
        topic = prefs.getString("studies_filter_topic", null);
        author = prefs.getString("studies_filter_author", null);
        studyCollectionId = prefs.getString("studies_selected_study_collection", null);
        studyId = prefs.getString("studies_selected_study", null);
        loadLibrary();
    }

    @Override
    protected void onPause() {
        super.onPause();
        getPrefs().edit()
                .putString("studies_filter_collection", collectionId)
                .putString("studies_filter_topic", topic)
                .putString("studies_filter_author", author)
                .putString("studies_selected_study_collection", studyCollectionId)
                .putString("studies_selected_study", studyId)
                .apply();
    }

    private void loadLibrary() {
        int request = ++libraryRequest;
        filters.setVisibility(View.GONE);
        status.setVisibility(View.VISIBLE);
        status.setText(R.string.study_loading_chapters);
        CompletableFuture.supplyAsync(() -> StudyStore.library(this))
                .whenComplete(
                        (result, error) ->
                                runOnUiThread(
                                        () -> {
                                            if (request != libraryRequest || isDestroyed() || isFinishing()) {
                                                return;
                                            }
                                            if (error != null) {
                                                Log.w("Studies", "Could not read study library", error);
                                                status.setText(R.string.study_load_failed);
                                            } else if (result.isEmpty()) {
                                                status.setText(R.string.study_library_empty);
                                            } else {
                                                library = result;
                                                showCollections();
                                                status.setVisibility(View.GONE);
                                                filters.setVisibility(View.VISIBLE);
                                            }
                                        }));
    }

    private void showCollections() {
        Map<String, String> names = new LinkedHashMap<>();
        for (Study study : library) {
            names.put(study.collectionId, study.collectionName);
        }
        List<String> ids = new ArrayList<>(names.keySet());
        collectionId = showFilter(
                collections, R.string.study_any_collection, ids, new ArrayList<>(names.values()),
                collectionId, position -> {
                    collectionId = position == 0 ? null : ids.get(position - 1);
                    showTopics();
                });
        showTopics();
    }

    private void showTopics() {
        LinkedHashSet<String> names = new LinkedHashSet<>();
        for (Study study : library) {
            if (collectionId == null || collectionId.equals(study.collectionId)) {
                for (StudyStore.Chapter chapter : study.chapters) {
                    names.add(chapter.topic);
                }
            }
        }
        List<String> values = new ArrayList<>(names);
        topic = showFilter(
                topics, R.string.study_any_topic, values, values, topic, position -> {
                    topic = position == 0 ? null : values.get(position - 1);
                    showAuthors();
                });
        showAuthors();
    }

    private void showAuthors() {
        LinkedHashSet<String> names = new LinkedHashSet<>();
        for (Study study : library) {
            if (matchesCollection(study)) {
                for (StudyStore.Chapter chapter : study.chapters) {
                    if (chapter.author != null && (topic == null || topic.equals(chapter.topic))) {
                        names.add(chapter.author);
                    }
                }
            }
        }
        List<String> values = new ArrayList<>(names);
        author = showFilter(
                authors, R.string.study_any_author, values, values, author, position -> {
                    author = position == 0 ? null : values.get(position - 1);
                    showStudies();
                });
        showStudies();
    }

    private String showFilter(
            FixedDropdownView view, int any, List<String> values, List<String> labels,
            String value, IntConsumer onSelect) {
        List<String> options = new ArrayList<>();
        options.add(getString(any));
        options.addAll(labels);
        view.setItems(options);
        int position = values.indexOf(value) + 1;
        view.setSelection(position);
        view.setOnItemClickListener((parent, row, index, id) -> onSelect.accept(index));
        return position == 0 ? null : value;
    }

    private boolean matchesCollection(Study study) {
        return collectionId == null || collectionId.equals(study.collectionId);
    }

    private boolean matchesFilters(Study study) {
        if (matchesCollection(study)) {
            for (StudyStore.Chapter chapter : study.chapters) {
                if ((topic == null || topic.equals(chapter.topic))
                        && (author == null || author.equals(chapter.author))) {
                    return true;
                }
            }
        }
        return false;
    }

    private void showStudies() {
        filtered = new ArrayList<>();
        List<String> labels = new ArrayList<>();
        int index = 0;
        for (Study study : library) {
            if (matchesFilters(study)) {
                if (study.matches(studyCollectionId, studyId)) {
                    index = filtered.size();
                }
                filtered.add(study);
                labels.add(study.author == null ? study.title
                        : getString(R.string.study_title_by_author, study.title, study.author));
            }
        }
        studies.setItems(labels);
        studies.setSelection(index);
        studies.setOnItemClickListener((parent, row, position, id) -> selectStudy(filtered.get(position)));
        selectStudy(filtered.get(index));
    }

    private void selectStudy(Study study) {
        selected = study;
        studyCollectionId = study.collectionId;
        studyId = study.id;
    }

    private void openChapter(int index) {
        startActivity(
                AnalyzeActivity.studyIntent(
                        this,
                        selected.collectionId,
                        selected.id,
                        selected.title,
                        selected.author,
                        index));
    }
}
