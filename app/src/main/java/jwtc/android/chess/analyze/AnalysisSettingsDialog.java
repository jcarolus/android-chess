package jwtc.android.chess.analyze;

import android.content.Context;
import android.content.SharedPreferences;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.EditText;

import androidx.appcompat.app.AlertDialog;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;

import java.util.ArrayList;
import java.util.List;

import jwtc.android.chess.R;
import jwtc.android.chess.engine.oex.OexEngineDescriptor;
import jwtc.android.chess.engine.oex.OexEngineResolver;
import jwtc.android.chess.views.FixedDropdownView;

final class AnalysisSettingsDialog {
    static final String PREF_ENGINE = "analysisOexEngineId";
    private static final String PREF_TIME = "analysisTimeSeconds";
    private static final int MAX_SECONDS = Integer.MAX_VALUE / 1000;

    static int getTimeSeconds(SharedPreferences prefs) {
        return Math.max(1, Math.min(MAX_SECONDS, prefs.getInt(PREF_TIME, 1)));
    }

    static void show(Context context, SharedPreferences prefs, Runnable onSaved) {
        View content = LayoutInflater.from(context).inflate(R.layout.analysis_settings, null);
        FixedDropdownView engine = content.findViewById(R.id.AnalysisEngine);
        EditText time = content.findViewById(R.id.AnalysisTime);
        List<OexEngineDescriptor> engines = new OexEngineResolver(context).resolveEngines();
        List<String> labels = new ArrayList<>();
        String preferred = prefs.getString(PREF_ENGINE, null);
        int selected = 0;
        for (int i = 0; i < engines.size(); i++) {
            labels.add(engines.get(i).getName());
            if (engines.get(i).getId().equals(preferred)) selected = i;
        }
        engine.setItems(labels);
        engine.setSelection(selected);
        engine.setVisibility(engines.isEmpty() ? View.GONE : View.VISIBLE);
        content.findViewById(R.id.AnalysisNoEngines).setVisibility(
            engines.isEmpty() ? View.VISIBLE : View.GONE);
        time.setText(String.valueOf(getTimeSeconds(prefs)));

        AlertDialog dialog = new MaterialAlertDialogBuilder(context)
            .setTitle(R.string.analysis_settings)
            .setView(content)
            .setNegativeButton(R.string.button_cancel, null)
            .setPositiveButton(R.string.button_ok, null)
            .create();
        dialog.setOnShowListener(ignored -> dialog.getButton(AlertDialog.BUTTON_POSITIVE)
            .setOnClickListener(v -> {
                int seconds;
                try {
                    seconds = Integer.parseInt(time.getText().toString().trim());
                    if (seconds < 1 || seconds > MAX_SECONDS) throw new NumberFormatException();
                } catch (NumberFormatException e) {
                    time.setError(context.getString(R.string.analysis_time_invalid, MAX_SECONDS));
                    return;
                }
                SharedPreferences.Editor editor = prefs.edit().putInt(PREF_TIME, seconds);
                if (!engines.isEmpty()) {
                    editor.putString(PREF_ENGINE,
                        engines.get(engine.getSelectedItemPosition()).getId());
                }
                editor.apply();
                dialog.dismiss();
                onSaved.run();
            }));
        dialog.show();
    }
}
