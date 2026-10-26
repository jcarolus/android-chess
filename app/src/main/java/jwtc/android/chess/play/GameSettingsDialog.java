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
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import jwtc.android.chess.R;
import jwtc.android.chess.engine.EngineApi;
import jwtc.android.chess.engine.OexEngine;
import jwtc.android.chess.engine.oex.OexEngineDescriptor;
import jwtc.android.chess.engine.oex.OexEngineResolver;
import jwtc.android.chess.helpers.ResultDialog;
import jwtc.android.chess.helpers.ResultDialogListener;
import jwtc.android.chess.views.FixedDropdownView;

public class GameSettingsDialog extends ResultDialog<Bundle> {
    private OexEngine capabilityProbe;
    private int probeGeneration;
    private String eloEngineId;
    private List<Integer> eloChoices = new ArrayList<>();
    private final Map<String, Integer> eloValues = new HashMap<>();

    private void rememberElo() {
        FixedDropdownView dropdown = findViewById(R.id.SpinnerOptionsElo);
        int index = dropdown.getSelectedItemPosition();
        if (eloEngineId != null && index >= 0 && index <= eloChoices.size()) {
            eloValues.put(eloEngineId, index == 0 ? null : eloChoices.get(index - 1));
        }
    }

    private void discoverElo(Context context, SharedPreferences prefs, String engineId) {
        rememberElo();
        int generation = ++probeGeneration;
        if (capabilityProbe != null) {
            capabilityProbe.destroy();
            capabilityProbe = null;
        }
        eloEngineId = null;
        eloChoices.clear();
        findViewById(R.id.SpinnerOptionsElo).setVisibility(View.GONE);
        findViewById(R.id.TextViewEloHint).setVisibility(View.GONE);
        findViewById(R.id.ButtonOk).setEnabled(true);
        if (engineId == null) {
            return;
        }
        findViewById(R.id.ButtonOk).setEnabled(false);
        TextView hint = findViewById(R.id.TextViewEloHint);
        hint.setText(R.string.options_target_elo_checking);
        hint.setVisibility(View.VISIBLE);
        capabilityProbe = new OexEngine(context, null, engineId);
        capabilityProbe.discoverStrength(
                strength -> {
                    if (generation != probeGeneration) {
                        return;
                    }
                    capabilityProbe.destroy();
                    capabilityProbe = null;
                    findViewById(R.id.ButtonOk).setEnabled(true);
                    hint.setText(R.string.options_target_elo_hint);
                    hint.setVisibility(View.GONE);
                    if (strength == null || !strength.supported()) {
                        return;
                    }
                    eloEngineId = engineId;
                    Integer saved =
                            eloValues.containsKey(engineId)
                                    ? eloValues.get(engineId)
                                    : prefs.getBoolean("oexEloEnabled:" + engineId, false)
                                            ? prefs.getInt(
                                                    "oexElo:" + engineId, strength.defaultElo())
                                            : null;
                    Integer selected = saved == null ? null : strength.clamp(saved);
                    eloChoices =
                            strength.choices(selected == null ? strength.defaultElo() : selected);
                    List<String> labels = new ArrayList<>();
                    labels.add(context.getString(R.string.options_full_strength));
                    for (int elo : eloChoices) {
                        labels.add(Integer.toString(elo));
                    }
                    FixedDropdownView dropdown = findViewById(R.id.SpinnerOptionsElo);
                    dropdown.setItems(labels);
                    dropdown.setSelection(selected == null ? 0 : eloChoices.indexOf(selected) + 1);
                    dropdown.setVisibility(View.VISIBLE);
                    hint.setVisibility(View.VISIBLE);
                });
    }

    @Override
    protected void onStop() {
        ++probeGeneration;
        if (capabilityProbe != null) {
            capabilityProbe.destroy();
            capabilityProbe = null;
        }
        super.onStop();
    }

    public GameSettingsDialog(
            @NonNull Context context,
            ResultDialogListener<Bundle> listener,
            int requestCode,
            final SharedPreferences prefs,
            boolean requiresBuiltinEngine) {
        super(context, listener, requestCode);

        setContentView(R.layout.game_settings);

        final boolean vsCPU = prefs.getBoolean("opponent", true);
        final boolean playAsWhite = prefs.getBoolean("myTurn", true);

        final int levelMode = prefs.getInt("levelMode", EngineApi.LEVEL_TIME);
        final int levelTime = prefs.getInt("level", 2);
        final int levelPly = prefs.getInt("levelPly", 2);
        final int[] levelTimes = {levelTime, prefs.getInt("oexLevel", levelTime)};
        final int[] levelPlies = {levelPly, prefs.getInt("oexLevelPly", levelPly)};
        final boolean quiescentSearchOn = prefs.getBoolean("quiescentSearchOn", true);

        final MaterialButtonToggleGroup toggleOpponent = findViewById(R.id.ToggleOpponentGroup);
        final MaterialButtonToggleGroup toggleColor = findViewById(R.id.ToggleColorGroup);
        final MaterialButtonToggleGroup toggleLevelMode = findViewById(R.id.ToggleLevelModeGroup);
        final FixedDropdownView spinnerEngine = findViewById(R.id.SpinnerOptionsEngine);
        final FixedDropdownView spinnerLevelTime = findViewById(R.id.SpinnerOptionsLevelTime);
        final FixedDropdownView spinnerLevelPly = findViewById(R.id.SpinnerOptionsLevelPly);
        final SwitchMaterial toggleQuiescent = findViewById(R.id.ToggleQuiescent);
        final TextView textEngineBackendHint = findViewById(R.id.TextViewEngineBackendHint);

        String[] timeChoices = context.getResources().getStringArray(R.array.levels_time);
        spinnerLevelTime.setItems(timeChoices);
        for (int i = 0; i < levelTimes.length; i++) {
            levelTimes[i] = Math.max(1, Math.min(levelTimes[i], timeChoices.length));
        }
        spinnerLevelPly.setItems(context.getResources().getStringArray(R.array.levels_ply));

        toggleQuiescent.setChecked(quiescentSearchOn);
        toggleQuiescent.setText(
                quiescentSearchOn ? R.string.options_quiescent_on : R.string.options_quiescent_off);

        toggleOpponent.check(vsCPU ? R.id.radioAndroid : R.id.radioHuman);

        toggleColor.check(playAsWhite ? R.id.radioWhite : R.id.radioBlack);

        final List<EngineOption> engineOptions = new ArrayList<>();
        final List<String> engineLabels = new ArrayList<>();

        engineOptions.add(new EngineOption("builtin", null, true));
        engineLabels.add(context.getString(R.string.options_engine_builtin));

        List<OexEngineDescriptor> oexEngines = new OexEngineResolver(context).resolveEngines();
        for (OexEngineDescriptor descriptor : oexEngines) {
            String label =
                    context.getString(R.string.options_engine_oex) + ": " + descriptor.getName();
            boolean selectable = !requiresBuiltinEngine;
            if (!selectable) {
                label += " (" + context.getString(R.string.options_engine_unavailable_short) + ")";
            }
            engineOptions.add(new EngineOption("oex", descriptor.getId(), selectable));
            engineLabels.add(label);
        }
        spinnerEngine.setItems(engineLabels);

        if (requiresBuiltinEngine && oexEngines.size() > 0) {
            textEngineBackendHint.setVisibility(View.VISIBLE);
            textEngineBackendHint.setText(R.string.options_engine_oex_variant_disabled);
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
            if (!requiresBuiltinEngine
                    && "oex".equals(engineBackend)
                    && "oex".equals(option.backend)
                    && option.oexEngineId != null
                    && option.oexEngineId.equals(oexEngineId)) {
                selectedEnginePos = i;
                break;
            }
        }
        spinnerEngine.setSelection(selectedEnginePos);

        boolean useTimeLevel = levelMode != EngineApi.LEVEL_PLY;
        toggleLevelMode.check(useTimeLevel ? R.id.RadioOptionsTime : R.id.RadioOptionsPly);
        spinnerLevelTime.setVisibility(useTimeLevel ? View.VISIBLE : View.GONE);
        spinnerLevelPly.setVisibility(useTimeLevel ? View.GONE : View.VISIBLE);

        toggleLevelMode.addOnButtonCheckedListener(
                (group, checkedId, isChecked) -> {
                    if (!isChecked) {
                        return;
                    }
                    boolean isPly = checkedId == R.id.RadioOptionsPly;
                    spinnerLevelTime.setVisibility(isPly ? View.GONE : View.VISIBLE);
                    spinnerLevelPly.setVisibility(isPly ? View.VISIBLE : View.GONE);
                });

        toggleQuiescent.setOnCheckedChangeListener(
                (buttonView, isChecked) ->
                        toggleQuiescent.setText(
                                isChecked
                                        ? R.string.options_quiescent_on
                                        : R.string.options_quiescent_off));

        final int[] selectedBackend = {selectedEnginePos == 0 ? 0 : 1};
        spinnerLevelTime.setSelection(levelTimes[selectedBackend[0]] - 1);
        spinnerLevelPly.setSelection(levelPlies[selectedBackend[0]] - 1);
        spinnerEngine.setOnItemClickListener(
                (parent, view, position, id) -> {
                    levelTimes[selectedBackend[0]] = spinnerLevelTime.getSelectedItemPosition() + 1;
                    levelPlies[selectedBackend[0]] = spinnerLevelPly.getSelectedItemPosition() + 1;
                    if (!engineOptions.get(position).selectable) {
                        spinnerEngine.setSelection(0);
                        position = 0;
                    }
                    selectedBackend[0] = position == 0 ? 0 : 1;
                    spinnerLevelTime.setSelection(levelTimes[selectedBackend[0]] - 1);
                    spinnerLevelPly.setSelection(levelPlies[selectedBackend[0]] - 1);
                    discoverElo(context, prefs, engineOptions.get(position).oexEngineId);
                });

        Button buttonOk = findViewById(R.id.ButtonOk);
        buttonOk.setOnClickListener(
                new View.OnClickListener() {
                    @Override
                    public void onClick(View v) {

                        SharedPreferences.Editor editor = prefs.edit();

                        editor.putBoolean(
                                "opponent",
                                toggleOpponent.getCheckedButtonId() == R.id.radioAndroid);
                        editor.putBoolean(
                                "myTurn", toggleColor.getCheckedButtonId() == R.id.radioWhite);
                        int engineSelection = spinnerEngine.getSelectedItemPosition();
                        EngineOption selectedOption =
                                engineSelection >= 0 && engineSelection < engineOptions.size()
                                        ? engineOptions.get(engineSelection)
                                        : engineOptions.get(0);
                        if (!selectedOption.selectable) {
                            selectedOption = engineOptions.get(0);
                        }
                        editor.putString("engineBackend", selectedOption.backend);
                        editor.putString("oexEngineId", selectedOption.oexEngineId);

                        editor.putInt(
                                "levelMode",
                                toggleLevelMode.getCheckedButtonId() == R.id.RadioOptionsPly
                                        ? EngineApi.LEVEL_PLY
                                        : EngineApi.LEVEL_TIME);
                        rememberElo();
                        for (Map.Entry<String, Integer> entry : eloValues.entrySet()) {
                            editor.putBoolean(
                                    "oexEloEnabled:" + entry.getKey(), entry.getValue() != null);
                            if (entry.getValue() != null) {
                                editor.putInt("oexElo:" + entry.getKey(), entry.getValue());
                            }
                        }
                        levelTimes[selectedBackend[0]] =
                                spinnerLevelTime.getSelectedItemPosition() + 1;
                        levelPlies[selectedBackend[0]] =
                                spinnerLevelPly.getSelectedItemPosition() + 1;
                        editor.putInt("level", levelTimes[0]);
                        editor.putInt("levelPly", levelPlies[0]);
                        editor.putInt("oexLevel", levelTimes[1]);
                        editor.putInt("oexLevelPly", levelPlies[1]);

                        editor.putBoolean("quiescentSearchOn", toggleQuiescent.isChecked());

                        editor.commit();

                        setResult(new Bundle());

                        dismiss();
                    }
                });

        discoverElo(context, prefs, engineOptions.get(selectedEnginePos).oexEngineId);

        Button buttonCancel = findViewById(R.id.ButtonCancel);
        buttonCancel.setOnClickListener(
                new View.OnClickListener() {
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
