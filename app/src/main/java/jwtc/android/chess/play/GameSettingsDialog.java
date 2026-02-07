package jwtc.android.chess.play;

import android.content.Context;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.ToggleButton;

import androidx.annotation.NonNull;
import com.google.android.material.button.MaterialButtonToggleGroup;
import jwtc.android.chess.R;
import jwtc.android.chess.engine.EngineApi;
import jwtc.android.chess.helpers.ResultDialog;
import jwtc.android.chess.helpers.ResultDialogListener;
import jwtc.android.chess.views.FixedDropdownView;

public class GameSettingsDialog extends ResultDialog {
    public GameSettingsDialog(@NonNull Context context, ResultDialogListener listener, int requestCode, final SharedPreferences prefs) {
        super(context, listener, requestCode);

        setContentView(R.layout.game_settings);

        final boolean vsCPU = prefs.getBoolean("opponent", true);
        final boolean playAsWhite = prefs.getBoolean("myTurn", true);

        final int levelMode = prefs.getInt("levelMode", EngineApi.LEVEL_TIME);
        final int levelTime = prefs.getInt("level", 2);
        final int levelPly = prefs.getInt("levelPly", 2);
        final boolean quiescentSearchOn = prefs.getBoolean("quiescentSearchOn", true);

        final MaterialButtonToggleGroup toggleOpponent = findViewById(R.id.ToggleOpponentGroup);
        final MaterialButtonToggleGroup toggleColor = findViewById(R.id.ToggleColorGroup);
        final MaterialButtonToggleGroup toggleLevelMode = findViewById(R.id.ToggleLevelModeGroup);
        final FixedDropdownView spinnerLevelTime = findViewById(R.id.SpinnerOptionsLevelTime);
        final FixedDropdownView spinnerLevelPly = findViewById(R.id.SpinnerOptionsLevelPly);
        final ToggleButton toggleQuiescent = findViewById(R.id.ToggleQuiescent);

        spinnerLevelTime.setItems(context.getResources().getStringArray(R.array.levels_time));
        spinnerLevelPly.setItems(context.getResources().getStringArray(R.array.levels_ply));

        toggleQuiescent.setChecked(quiescentSearchOn);

        toggleOpponent.check(vsCPU ? R.id.radioAndroid : R.id.radioHuman);

        toggleColor.check(playAsWhite ? R.id.radioWhite : R.id.radioBlack);

        toggleLevelMode.check(levelMode == EngineApi.LEVEL_TIME
                ? R.id.RadioOptionsTime
                : R.id.RadioOptionsPly);

        spinnerLevelTime.setSelection(levelTime - 1);
        spinnerLevelPly.setSelection(levelPly - 1);

        Button buttonOk = findViewById(R.id.ButtonOk);
        buttonOk.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {

                SharedPreferences.Editor editor = prefs.edit();

                editor.putBoolean("opponent", toggleOpponent.getCheckedButtonId() == R.id.radioAndroid);
                editor.putBoolean("myTurn", toggleColor.getCheckedButtonId() == R.id.radioWhite);

                editor.putInt("levelMode", toggleLevelMode.getCheckedButtonId() == R.id.RadioOptionsTime
                        ? EngineApi.LEVEL_TIME
                        : EngineApi.LEVEL_PLY);
                editor.putInt("level", spinnerLevelTime.getSelectedItemPosition() + 1);
                editor.putInt("levelPly", spinnerLevelPly.getSelectedItemPosition() + 1);

                editor.putBoolean("quiescentSearchOn", toggleQuiescent.isChecked());

                editor.commit();

                setResult(new Bundle());

                dismiss();
            }
        });

        Button buttonCancel = findViewById(R.id.ButtonCancel);
        buttonCancel.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                dismiss();
            }
        });
    }
}
