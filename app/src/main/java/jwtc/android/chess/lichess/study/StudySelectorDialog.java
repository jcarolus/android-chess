package jwtc.android.chess.lichess.study;

import android.app.Dialog;
import android.os.Bundle;
import android.view.View;
import android.widget.TextView;
import androidx.annotation.NonNull;
import androidx.fragment.app.DialogFragment;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Random;
import jwtc.android.chess.R;
import jwtc.android.chess.helpers.ChessAlertDialogBuilder;
import jwtc.android.chess.lichess.study.StudyCatalog.Topic;
import jwtc.android.chess.lichess.study.StudyPgn.Chapter;
import jwtc.android.chess.views.FixedDropdownView;
import okhttp3.Call;

public final class StudySelectorDialog extends DialogFragment {
    private final Random random = new Random();
    private Topic topic = Topic.OPENINGS;
    private String author;
    private StudyCatalog study;
    private String chapterId;
    private List<StudyCatalog> studies = Collections.emptyList();
    private List<Chapter> chapters = Collections.emptyList();
    private FixedDropdownView topicView, authorView, studyView, chapterView;
    private TextView status;
    private View retry, open, surprise;
    private Call request;

    @NonNull
    @Override
    public Dialog onCreateDialog(Bundle state) {
        View content = requireActivity().getLayoutInflater().inflate(R.layout.study_selector, null);
        topicView = content.findViewById(R.id.StudyTopic);
        authorView = content.findViewById(R.id.StudyAuthor);
        studyView = content.findViewById(R.id.StudyName);
        chapterView = content.findViewById(R.id.StudyChapter);
        for (View field : new View[] {topicView, authorView, studyView, chapterView}) {
            field.setSaveFromParentEnabled(false);
        }
        status = content.findViewById(R.id.StudyStatus);
        retry = content.findViewById(R.id.StudyRetry);
        open = content.findViewById(R.id.StudyOpen);
        surprise = content.findViewById(R.id.StudySurprise);

        String studyId = null;
        if (state != null) {
            topic = Topic.values()[state.getInt("topic")];
            author = state.getString("author");
            studyId = state.getString("study");
            chapterId = state.getString("chapter");
        }
        List<String> topics = new ArrayList<>();
        for (Topic item : Topic.values()) {
            topics.add(getString(item.label));
        }
        topicView.setItems(topics);
        topicView.setSelection(topic.ordinal());
        topicView.setOnItemClickListener(
                (parent, row, position, id) -> {
                    topic = Topic.values()[position];
                    author = null;
                    chapterId = null;
                    updateAuthors(null);
                });
        studyView.setOnItemClickListener(
                (parent, row, position, id) -> {
                    chapterId = null;
                    loadChapters(studies.get(position), false);
                });
        chapterView.setOnItemClickListener(
                (parent, row, position, id) -> {
                    chapterId = chapters.get(position).id;
                    open.setEnabled(true);
                });
        open.setOnClickListener(v -> openChapter());
        retry.setOnClickListener(v -> loadChapters(study, false));
        surprise.setOnClickListener(
                v -> {
                    int index = random.nextInt(studies.size());
                    studyView.setSelection(index);
                    chapterId = null;
                    loadChapters(studies.get(index), true);
                });
        updateAuthors(studyId);

        return new ChessAlertDialogBuilder(requireContext())
                .setTitle(R.string.lichess_studies)
                .setView(content)
                .setNegativeButton(R.string.button_cancel, null)
                .create();
    }

    private void updateAuthors(String studyId) {
        LinkedHashSet<String> names = new LinkedHashSet<>();
        for (StudyCatalog entry : StudyCatalog.STUDIES) {
            if (entry.topic == topic) {
                names.add(entry.author);
            }
        }
        List<String> authors = new ArrayList<>();
        authors.add(getString(R.string.study_any_author));
        authors.addAll(names);
        authorView.setItems(authors);
        int selected = author == null ? 0 : authors.indexOf(author);
        authorView.setSelection(Math.max(0, selected));
        authorView.setOnItemClickListener(
                (parent, row, position, id) -> {
                    author = position == 0 ? null : authors.get(position);
                    chapterId = null;
                    updateStudies(null);
                });
        updateStudies(studyId);
    }

    private void updateStudies(String studyId) {
        studies = new ArrayList<>();
        List<String> titles = new ArrayList<>();
        int selection = 0;
        for (StudyCatalog entry : StudyCatalog.STUDIES) {
            if (entry.topic == topic && (author == null || author.equals(entry.author))) {
                if (entry.id.equals(studyId)) {
                    selection = studies.size();
                }
                studies.add(entry);
                titles.add(getString(R.string.study_title_by_author, entry.title, entry.author));
            }
        }
        studyView.setItems(titles);
        studyView.setSelection(selection);
        loadChapters(studies.get(selection), false);
    }

    private void loadChapters(StudyCatalog selected, boolean surprisePick) {
        if (request != null) {
            request.cancel();
            request = null;
        }
        study = selected;
        chapters = Collections.emptyList();
        chapterView.setItems(Collections.emptyList());
        chapterView.setSelectionText("");
        chapterView.setVisibility(View.GONE);
        open.setEnabled(false);
        surprise.setEnabled(!surprisePick);
        retry.setVisibility(View.GONE);
        status.setVisibility(View.GONE);
        List<Chapter> cached = StudyDownload.cachedChapters(study.id);
        if (cached != null) {
            showChapters(cached, surprisePick);
            return;
        }
        status.setText(R.string.study_loading_chapters);
        status.setVisibility(View.VISIBLE);
        request =
                StudyDownload.chapters(
                        study.id,
                        study.broadcast,
                        result -> {
                            request = null;
                            showChapters(result, surprisePick);
                        });
    }

    private void showChapters(List<Chapter> result, boolean surprisePick) {
        surprise.setEnabled(true);
        if (result == null || result.isEmpty()) {
            status.setText(
                    result != null
                            ? R.string.study_no_chapters
                            : StudyDownload.isRateLimited()
                                    ? R.string.study_rate_limited
                                    : R.string.study_load_failed);
            status.setVisibility(View.VISIBLE);
            retry.setVisibility(result == null ? View.VISIBLE : View.GONE);
            return;
        }
        chapters = result;
        List<String> titles = new ArrayList<>();
        int selected = 0;
        for (int i = 0; i < chapters.size(); i++) {
            Chapter chapter = chapters.get(i);
            titles.add(getString(R.string.study_chapter_choice, i + 1, chapter.title));
            if (!surprisePick && chapter.id.equals(chapterId)) {
                selected = i;
            }
        }
        chapterId = chapters.get(selected).id;
        chapterView.setItems(titles);
        chapterView.setSelection(selected);
        chapterView.setVisibility(View.VISIBLE);
        status.setVisibility(View.GONE);
        open.setEnabled(true);
        if (surprisePick) {
            openChapter();
        }
    }

    private void openChapter() {
        startActivity(StudyReaderActivity.intent(requireContext(), study, chapterId));
    }

    @Override
    public void onSaveInstanceState(@NonNull Bundle state) {
        super.onSaveInstanceState(state);
        state.putInt("topic", topic.ordinal());
        state.putString("author", author);
        state.putString("study", study.id);
        state.putString("chapter", chapterId);
    }

    @Override
    public void onDestroyView() {
        if (request != null) {
            request.cancel();
            request = null;
        }
        super.onDestroyView();
    }
}
