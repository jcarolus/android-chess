package jwtc.android.chess.lichess;

import android.content.Context;
import android.content.SharedPreferences;
import android.widget.Button;

import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import jwtc.android.chess.R;
import jwtc.android.chess.helpers.ResultDialog;
import jwtc.android.chess.helpers.ResultDialogListener;
import jwtc.android.chess.views.FixedDropdownView;

public class PuzzleDialog extends ResultDialog<Map<String, Object>> {

    public static final int REQUEST_PUZZLE = 3;

    private static final String[] DIFFICULTY_VALUES = {"", "easiest", "easier", "normal", "harder", "hardest"};
    private static final String[] DIFFICULTY_LABELS = {"Default", "Easiest", "Easier", "Normal", "Harder", "Hardest"};

    private static final String[] ANGLE_VALUES = {
        "mix", "endgame", "opening", "middlegame",
        "fork", "pin", "skewer", "mate",
        "mateIn1", "mateIn2", "mateIn3", "sacrifice",
        "discoveredAttack", "promotion", "enPassant"
    };

    public PuzzleDialog(Context context, ResultDialogListener<Map<String, Object>> listener, SharedPreferences prefs) {
        super(context, listener, REQUEST_PUZZLE);

        setContentView(R.layout.lichess_puzzle_dialog);
        setTitle(R.string.lichess_puzzle_title);

        FixedDropdownView dropdownDifficulty = findViewById(R.id.DropdownDifficulty);
        FixedDropdownView dropdownAngle = findViewById(R.id.DropdownAngle);

        dropdownDifficulty.setItems(Arrays.asList(DIFFICULTY_LABELS));
        dropdownAngle.setItems(Arrays.asList(ANGLE_VALUES));

        String savedDifficulty = prefs.getString("lichess_puzzle_difficulty", "");
        String savedAngle = prefs.getString("lichess_puzzle_angle", "mix");

        for (int i = 0; i < DIFFICULTY_VALUES.length; i++) {
            if (DIFFICULTY_VALUES[i].equals(savedDifficulty)) {
                dropdownDifficulty.setSelection(i);
                break;
            }
        }
        for (int i = 0; i < ANGLE_VALUES.length; i++) {
            if (ANGLE_VALUES[i].equals(savedAngle)) {
                dropdownAngle.setSelection(i);
                break;
            }
        }

        Button buttonOk = findViewById(R.id.ButtonPuzzleOk);
        buttonOk.setOnClickListener(v -> {
            SharedPreferences.Editor editor = prefs.edit();
            Map<String, Object> data = new HashMap<>();

            int diffPos = dropdownDifficulty.getSelectedItemPosition();
            String difficulty = (diffPos >= 0 && diffPos < DIFFICULTY_VALUES.length) ? DIFFICULTY_VALUES[diffPos] : "";
            editor.putString("lichess_puzzle_difficulty", difficulty);
            data.put("difficulty", difficulty);

            int anglePos = dropdownAngle.getSelectedItemPosition();
            String angle = (anglePos >= 0 && anglePos < ANGLE_VALUES.length) ? ANGLE_VALUES[anglePos] : "mix";
            editor.putString("lichess_puzzle_angle", angle);
            data.put("angle", angle);

            editor.apply();
            PuzzleDialog.this.dismiss();
            setResult(data);
        });

        Button buttonCancel = findViewById(R.id.ButtonPuzzleCancel);
        buttonCancel.setOnClickListener(v -> {
            PuzzleDialog.this.dismiss();
            setResult(null);
        });
    }
}
