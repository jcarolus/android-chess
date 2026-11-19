package jwtc.android.chess.gamedatabase;

import android.text.Editable;
import android.text.TextWatcher;
import android.view.View;
import com.google.android.material.switchmaterial.SwitchMaterial;
import com.google.android.material.textfield.TextInputEditText;
import jwtc.android.chess.R;
import jwtc.android.chess.views.FixedDropdownView;
import jwtc.android.chess.views.PGNDateView;
import jwtc.chess.GameFilter;
import jwtc.chess.GameSelection;

/** Binds the shared selection controls without saving drafts to preferences. */
final class GameSelectionForm {
    private final TextInputEditText white, black, event, topic;
    private final SwitchMaterial useWhite,
            useBlack,
            useEvent,
            useTopic,
            useAfter,
            useBefore,
            useResult;
    private final PGNDateView after, before;
    private final FixedDropdownView result, sortColumn, sortDirection;

    GameSelectionForm(View root, GameSelection selection, Runnable changed) {
        // Compound controls reuse child IDs, so the selection owns their saved state.
        root.findViewById(R.id.GameSelectionFilters).setSaveFromParentEnabled(false);
        white = root.findViewById(R.id.EditTextFilterWhite);
        black = root.findViewById(R.id.EditTextFilterBlack);
        event = root.findViewById(R.id.EditTextFilterEvent);
        topic = root.findViewById(R.id.EditTextFilterTopic);
        useWhite = root.findViewById(R.id.SwitchFilterWhite);
        useBlack = root.findViewById(R.id.SwitchFilterBlack);
        useEvent = root.findViewById(R.id.SwitchFilterEvent);
        useTopic = root.findViewById(R.id.SwitchFilterTopic);
        useAfter = root.findViewById(R.id.SwitchFilterDateAfter);
        useBefore = root.findViewById(R.id.SwitchFilterDateBefore);
        useResult = root.findViewById(R.id.SwitchFilterResult);
        after = root.findViewById(R.id.PGNDateAfter);
        before = root.findViewById(R.id.PGNDateBefore);
        result = root.findViewById(R.id.DropdownResult);
        sortColumn = root.findViewById(R.id.DropdownOrderBy);
        sortDirection = root.findViewById(R.id.DropdownOrderDirection);

        white.setText(selection.white);
        black.setText(selection.black);
        event.setText(selection.event);
        topic.setText(selection.topic);
        useWhite.setChecked(selection.useWhite);
        useBlack.setChecked(selection.useBlack);
        useEvent.setChecked(selection.useEvent);
        useTopic.setChecked(selection.useTopic);
        useAfter.setChecked(selection.useAfter);
        useBefore.setChecked(selection.useBefore);
        useResult.setChecked(selection.useResult);
        after.setDate(selection.after);
        before.setDate(selection.before);
        result.setItems(new String[] {"1-0", "0-1", "1/2-1/2", "*"});
        result.setSelectionText(selection.result);
        sortColumn.setItems(GameFilter.SORT_COLUMNS);
        sortColumn.setSelectionText(selection.sortColumn);
        sortDirection.setItems(new String[] {"ASC", "DESC"});
        sortDirection.setSelectionText(selection.sortDirection);

        TextWatcher watcher =
                new TextWatcher() {
                    @Override
                    public void beforeTextChanged(
                            CharSequence s, int start, int count, int after) {}

                    @Override
                    public void onTextChanged(CharSequence s, int start, int before, int count) {}

                    @Override
                    public void afterTextChanged(Editable text) {
                        changed.run();
                    }
                };
        for (TextInputEditText input : new TextInputEditText[] {white, black, event, topic}) {
            input.addTextChangedListener(watcher);
        }
        after.addOnTextChangedListener(watcher);
        before.addOnTextChangedListener(watcher);
        result.addOnTextChangedListener(watcher);
        sortColumn.addOnTextChangedListener(watcher);
        sortDirection.addOnTextChangedListener(watcher);
        for (SwitchMaterial toggle :
                new SwitchMaterial[] {
                    useWhite, useBlack, useEvent, useTopic, useAfter, useBefore, useResult
                }) {
            toggle.setOnCheckedChangeListener((button, checked) -> changed.run());
        }
    }

    GameSelection read() {
        GameSelection selection = new GameSelection();
        selection.white = white.getText().toString();
        selection.black = black.getText().toString();
        selection.event = event.getText().toString();
        selection.topic = topic.getText().toString();
        selection.useWhite = useWhite.isChecked();
        selection.useBlack = useBlack.isChecked();
        selection.useEvent = useEvent.isChecked();
        selection.useTopic = useTopic.isChecked();
        selection.useAfter = useAfter.isChecked();
        selection.useBefore = useBefore.isChecked();
        selection.useResult = useResult.isChecked();
        selection.after = after.getEnteredDate();
        selection.before = before.getEnteredDate();
        selection.result = result.getSelectionText();
        selection.sortColumn = sortColumn.getSelectionText();
        selection.sortDirection = sortDirection.getSelectionText();
        return selection;
    }
}
