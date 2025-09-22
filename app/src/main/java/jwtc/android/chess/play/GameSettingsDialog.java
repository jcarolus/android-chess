package jwtc.android.chess.play;

import android.content.Context;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.RadioButton;
import android.widget.Spinner;
import android.widget.ToggleButton;

import androidx.annotation.NonNull;
import jwtc.android.chess.R;
import jwtc.android.chess.engine.EngineApi;
import jwtc.android.chess.helpers.ResultDialog;
import jwtc.android.chess.helpers.ResultDialogListener;

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

        final RadioButton radioAndroid = findViewById(R.id.radioAndroid);
        final RadioButton radioHuman = findViewById(R.id.radioHuman);
        final RadioButton radioWhite = findViewById(R.id.radioWhite);
        final RadioButton radioBlack = findViewById(R.id.radioBlack);
        final RadioButton radioTime = findViewById(R.id.RadioOptionsTime);
        final RadioButton radioPly = findViewById(R.id.RadioOptionsPly);
        final Spinner spinnerLevelTime = findViewById(R.id.SpinnerOptionsLevelTime);
        final Spinner spinnerLevelPly = findViewById(R.id.SpinnerOptionsLevelPly);
        final ToggleButton toggleQuiescent = findViewById(R.id.ToggleQuiescent);

        ArrayAdapter<CharSequence> adapterTime = ArrayAdapter.createFromResource(context, R.array.levels_time, android.R.layout.simple_spinner_item);
        adapterTime.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        spinnerLevelTime.setPrompt(context.getString(R.string.title_pick_level));
        spinnerLevelTime.setAdapter(adapterTime);

        ArrayAdapter<CharSequence> adapterPly = ArrayAdapter.createFromResource(context, R.array.levels_ply, android.R.layout.simple_spinner_item);
        adapterPly.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        spinnerLevelPly.setPrompt(context.getString(R.string.title_pick_level));
        spinnerLevelPly.setAdapter(adapterPly);

        toggleQuiescent.setChecked(quiescentSearchOn);

        radioAndroid.setChecked(vsCPU);
        radioHuman.setChecked(!vsCPU);

        radioWhite.setChecked(playAsWhite);
        radioBlack.setChecked(!playAsWhite);

        radioTime.setChecked(levelMode == EngineApi.LEVEL_TIME);
        radioPly.setChecked(levelMode == EngineApi.LEVEL_PLY);

        // radio group
        radioTime.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                radioPly.setChecked(!radioTime.isChecked());
            }
        });

        radioPly.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                radioTime.setChecked(!radioPly.isChecked());
            }
        });

        spinnerLevelTime.setSelection(levelTime - 1);
        spinnerLevelPly.setSelection(levelPly - 1);

        Button buttonOk = findViewById(R.id.ButtonOk);
        buttonOk.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {

                SharedPreferences.Editor editor = prefs.edit();

                editor.putBoolean("opponent", radioAndroid.isChecked());
                editor.putBoolean("myTurn", radioWhite.isChecked());

                editor.putInt("levelMode", radioTime.isChecked() ? EngineApi.LEVEL_TIME : EngineApi.LEVEL_PLY);
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
