package jwtc.android.chess.play;

import android.content.Context;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.view.View;
import android.widget.Button;
import android.widget.TextView;

import androidx.annotation.NonNull;

import com.google.android.material.button.MaterialButtonToggleGroup;
import com.google.android.material.switchmaterial.SwitchMaterial;

import java.util.ArrayList;
import java.util.List;

import jwtc.android.chess.R;
import jwtc.android.chess.engine.EngineApi;
import jwtc.android.chess.engine.oex.OexEngineDescriptor;
import jwtc.android.chess.engine.oex.OexEngineResolver;
import jwtc.android.chess.helpers.ResultDialog;
import jwtc.android.chess.helpers.ResultDialogListener;
import jwtc.android.chess.views.FixedDropdownView;

public class GameSettingsDialog extends ResultDialog {
    public GameSettingsDialog(@NonNull Context context, ResultDialogListener listener, int requestCode, final SharedPreferences prefs, boolean isDuckGame) {
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
        final FixedDropdownView spinnerEngine = findViewById(R.id.SpinnerOptionsEngine);
        final FixedDropdownView spinnerLevelTime = findViewById(R.id.SpinnerOptionsLevelTime);
        final FixedDropdownView spinnerLevelPly = findViewById(R.id.SpinnerOptionsLevelPly);
        final SwitchMaterial toggleQuiescent = findViewById(R.id.ToggleQuiescent);
        final TextView textEngineBackendHint = findViewById(R.id.TextViewEngineBackendHint);

        spinnerLevelTime.setItems(context.getResources().getStringArray(R.array.levels_time));
        spinnerLevelPly.setItems(context.getResources().getStringArray(R.array.levels_ply));

        toggleQuiescent.setChecked(quiescentSearchOn);
        toggleQuiescent.setText(quiescentSearchOn
            ? R.string.options_quiescent_on
            : R.string.options_quiescent_off);

        toggleOpponent.check(vsCPU ? R.id.radioAndroid : R.id.radioHuman);

        toggleColor.check(playAsWhite ? R.id.radioWhite : R.id.radioBlack);

        final List<EngineOption> engineOptions = new ArrayList<>();
        final List<String> engineLabels = new ArrayList<>();

        engineOptions.add(new EngineOption("builtin", null, true));
        engineLabels.add(context.getString(R.string.options_engine_builtin));

        List<OexEngineDescriptor> oexEngines = new OexEngineResolver(context).resolveEngines();
        for (OexEngineDescriptor descriptor : oexEngines) {
            String label = context.getString(R.string.options_engine_oex) + ": " + descriptor.getName();
            boolean selectable = !isDuckGame;
            if (!selectable) {
                label += " (" + context.getString(R.string.options_engine_unavailable_short) + ")";
            }
            engineOptions.add(new EngineOption("oex", descriptor.getId(), selectable));
            engineLabels.add(label);
        }
        spinnerEngine.setItems(engineLabels);

        if (isDuckGame && oexEngines.size() > 0) {
            textEngineBackendHint.setVisibility(View.VISIBLE);
            textEngineBackendHint.setText(R.string.options_engine_oex_duck_disabled);
        } else if (oexEngines.isEmpty()) {
            textEngineBackendHint.setVisibility(View.VISIBLE);
            textEngineBackendHint.setText(R.string.options_engine_oex_unavailable);
        } else {
            textEngineBackendHint.setVisibility(View.GONE);
        }

        String engineBackend = prefs.getString("engineBackend", "builtin");
        String oexEngineId = prefs.getString("oexEngineId", null);

        int selectedEnginePos = 0;
        for (int i = 0; i < engineOptions.size(); i++) {
            EngineOption option = engineOptions.get(i);
            if ("builtin".equals(engineBackend) && "builtin".equals(option.backend)) {
                selectedEnginePos = i;
                break;
            }
            if ("oex".equals(engineBackend) &&
                "oex".equals(option.backend) &&
                option.oexEngineId != null &&
                option.oexEngineId.equals(oexEngineId)) {
                selectedEnginePos = i;
                break;
            }
        }
        spinnerEngine.setSelection(selectedEnginePos);

        boolean useTimeLevel = levelMode == EngineApi.LEVEL_TIME;
        toggleLevelMode.check(useTimeLevel ? R.id.RadioOptionsTime : R.id.RadioOptionsPly);
        spinnerLevelTime.setVisibility(useTimeLevel ? View.VISIBLE : View.GONE);
        spinnerLevelPly.setVisibility(useTimeLevel ? View.GONE : View.VISIBLE);

        toggleLevelMode.addOnButtonCheckedListener((group, checkedId, isChecked) -> {
            if (!isChecked) {
                return;
            }
            boolean isTime = checkedId == R.id.RadioOptionsTime;
            spinnerLevelTime.setVisibility(isTime ? View.VISIBLE : View.GONE);
            spinnerLevelPly.setVisibility(isTime ? View.GONE : View.VISIBLE);
        });

        toggleQuiescent.setOnCheckedChangeListener((buttonView, isChecked) ->
            toggleQuiescent.setText(isChecked
                ? R.string.options_quiescent_on
                : R.string.options_quiescent_off));

        spinnerLevelTime.setSelection(levelTime - 1);
        spinnerLevelPly.setSelection(levelPly - 1);

        Button buttonOk = findViewById(R.id.ButtonOk);
        buttonOk.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {

                SharedPreferences.Editor editor = prefs.edit();

                editor.putBoolean("opponent", toggleOpponent.getCheckedButtonId() == R.id.radioAndroid);
                editor.putBoolean("myTurn", toggleColor.getCheckedButtonId() == R.id.radioWhite);
                int engineSelection = spinnerEngine.getSelectedItemPosition();
                EngineOption selectedOption = engineSelection >= 0 && engineSelection < engineOptions.size()
                    ? engineOptions.get(engineSelection)
                    : engineOptions.get(0);
                if (!selectedOption.selectable) {
                    selectedOption = engineOptions.get(0);
                }
                editor.putString("engineBackend", selectedOption.backend);
                editor.putString("oexEngineId", selectedOption.oexEngineId);

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

    private static class EngineOption {
        final String backend;
        final String oexEngineId;
        final boolean selectable;

        EngineOption(String backend, String oexEngineId, boolean selectable) {
            this.backend = backend;
            this.oexEngineId = oexEngineId;
            this.selectable = selectable;
        }
    }
}
