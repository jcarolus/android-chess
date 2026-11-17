package jwtc.android.chess.study;

import android.view.View;
import android.widget.SeekBar;
import android.widget.TextView;
import java.util.function.IntConsumer;
import jwtc.android.chess.R;

/** The event and study controls shown above the move history. */
public final class StudyNavigation {
    private final View header, navigation, previous, next;
    private final TextView title, position;
    private final SeekBar seek;
    private final IntConsumer onSelect;
    private int count;
    private int index;
    private boolean seeking;

    public StudyNavigation(View root, IntConsumer onSelect) {
        this.onSelect = onSelect;
        header = root.findViewById(R.id.StudyHeader);
        title = root.findViewById(R.id.StudyTitle);
        position = root.findViewById(R.id.StudyPosition);
        navigation = root.findViewById(R.id.StudyNavigation);
        previous = root.findViewById(R.id.StudyPrevious);
        next = root.findViewById(R.id.StudyNext);
        seek = root.findViewById(R.id.StudySeek);
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
                            showPosition(progress);
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
                            showPosition(index);
                        }
                    }
                });
    }

    public void show(String event, int count, int index) {
        this.count = count;
        header.setVisibility(View.VISIBLE);
        title.setText(event);
        navigation.setVisibility(count > 1 ? View.VISIBLE : View.GONE);
        seek.setMax(count - 1);
        setIndex(index);
    }

    public void setIndex(int index) {
        this.index = index;
        seek.setProgress(index);
        previous.setEnabled(index > 0);
        next.setEnabled(index < count - 1);
        showPosition(index);
    }

    private boolean request(int target) {
        if (target < 0 || target >= count || target == index) {
            return false;
        }
        onSelect.accept(target);
        return true;
    }

    private void showPosition(int target) {
        position.setText(
                position.getContext().getString(R.string.study_position, target + 1, count));
    }
}
