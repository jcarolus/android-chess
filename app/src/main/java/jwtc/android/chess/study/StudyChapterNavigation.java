package jwtc.android.chess.study;

import android.view.View;
import android.widget.SeekBar;
import android.widget.TextView;
import java.util.List;
import java.util.function.IntConsumer;
import jwtc.android.chess.R;

/** The study title and chapter controls shown above the move history when a study is open. */
public final class StudyChapterNavigation {
    private final View header, navigation, previous, next;
    private final TextView title, chapterTitle;
    private final SeekBar seek;
    private final IntConsumer onSelect;
    private List<StudyStore.Chapter> chapters;
    private int index;
    private boolean seeking;

    public StudyChapterNavigation(View root, IntConsumer onSelect) {
        this.onSelect = onSelect;
        header = root.findViewById(R.id.StudyHeader);
        title = root.findViewById(R.id.StudyTitle);
        chapterTitle = root.findViewById(R.id.StudyChapterTitle);
        navigation = root.findViewById(R.id.StudyChapterNavigation);
        previous = root.findViewById(R.id.StudyPreviousChapter);
        next = root.findViewById(R.id.StudyNextChapter);
        seek = root.findViewById(R.id.StudyChapterSeek);
        previous.setOnClickListener(v -> request(index - 1));
        next.setOnClickListener(v -> request(index + 1));
        seek.setOnSeekBarChangeListener(
                new SeekBar.OnSeekBarChangeListener() {
                    @Override
                    public void onStartTrackingTouch(SeekBar seekBar) {
                        seeking = true;
                    }

                    @Override
                    public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                        if (fromUser) {
                            showChapterTitle(progress);
                            // Keyboard and accessibility changes arrive without a touch gesture.
                            if (!seeking) {
                                request(progress);
                            }
                        }
                    }

                    @Override
                    public void onStopTrackingTouch(SeekBar seekBar) {
                        seeking = false;
                        if (!request(seekBar.getProgress())) {
                            showChapterTitle(index);
                        }
                    }
                });
    }

    public void show(String studyTitle, String author, List<StudyStore.Chapter> chapters, int index) {
        this.chapters = chapters;
        header.setVisibility(View.VISIBLE);
        title.setText(
                author == null
                        ? studyTitle
                        : title.getContext()
                                .getString(R.string.study_title_by_author, studyTitle, author));
        navigation.setVisibility(chapters.size() > 1 ? View.VISIBLE : View.GONE);
        seek.setMax(chapters.size() - 1);
        setIndex(index);
    }

    public void setIndex(int index) {
        this.index = index;
        seek.setProgress(index);
        previous.setEnabled(index > 0);
        next.setEnabled(index < chapters.size() - 1);
        showChapterTitle(index);
    }

    private boolean request(int target) {
        if (chapters == null || target < 0 || target >= chapters.size() || target == index) {
            return false;
        }
        onSelect.accept(target);
        return true;
    }

    private void showChapterTitle(int target) {
        chapterTitle.setText(
                chapterTitle
                        .getContext()
                        .getString(
                                R.string.study_chapter_choice,
                                target + 1,
                                chapters.get(target).title));
    }
}
