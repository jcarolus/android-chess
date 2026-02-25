package jwtc.android.chess.play;

import android.content.Context;
import android.os.Bundle;
import android.widget.ArrayAdapter;
import android.widget.ListView;
import android.widget.Toast;

import androidx.annotation.NonNull;

import java.util.ArrayList;
import java.util.List;

import jwtc.android.chess.R;
import jwtc.android.chess.engine.oex.OexEngineDescriptor;
import jwtc.android.chess.engine.oex.OexEngineResolver;
import jwtc.android.chess.helpers.ResultDialog;
import jwtc.android.chess.helpers.ResultDialogListener;

public class EnginePickerDialog extends ResultDialog {
    private final List<Row> rows = new ArrayList<>();

    public EnginePickerDialog(
        @NonNull Context context,
        ResultDialogListener listener,
        int requestCode,
        String currentBackend,
        String currentOexEngineId,
        boolean isDuckGame
    ) {
        super(context, listener, requestCode);

        setTitle(R.string.title_engine_picker);
        setContentView(R.layout.engine_picker);

        ListView listView = findViewById(R.id.ListEnginePicker);

        rows.add(new Row("builtin", null, decorateLabel(context.getString(R.string.options_engine_builtin), "builtin".equals(currentBackend)), true));

        List<OexEngineDescriptor> oexEngines = new OexEngineResolver(context).resolveEngines();
        for (OexEngineDescriptor descriptor : oexEngines) {
            String label = context.getString(R.string.options_engine_oex) + ": " + descriptor.getName();
            boolean selected = "oex".equals(currentBackend) && descriptor.getId().equals(currentOexEngineId);
            rows.add(new Row("oex", descriptor.getId(), decorateLabel(label, selected), !isDuckGame));
        }

        if (rows.size() == 1) {
            rows.add(new Row("info", null, context.getString(R.string.options_engine_oex_unavailable), false));
        } else if (isDuckGame) {
            rows.add(new Row("info", null, context.getString(R.string.options_engine_oex_duck_disabled), false));
        }

        List<String> labels = new ArrayList<>();
        for (Row row : rows) {
            labels.add(row.label);
        }

        listView.setAdapter(new ArrayAdapter<>(context, android.R.layout.simple_list_item_1, labels));
        listView.setOnItemClickListener((parent, view, position, id) -> {
            Row row = rows.get(position);
            if (!row.selectable) {
                if ("oex".equals(row.backend) && isDuckGame) {
                    Toast.makeText(context, R.string.options_engine_oex_duck_disabled, Toast.LENGTH_SHORT).show();
                }
                return;
            }
            Bundle result = new Bundle();
            result.putString("engineBackend", row.backend);
            result.putString("oexEngineId", row.oexEngineId);
            setResult(result);
            dismiss();
        });
    }

    private String decorateLabel(String label, boolean selected) {
        return selected ? "\u2713 " + label : label;
    }

    private static class Row {
        final String backend;
        final String oexEngineId;
        final String label;
        final boolean selectable;

        Row(String backend, String oexEngineId, String label, boolean selectable) {
            this.backend = backend;
            this.oexEngineId = oexEngineId;
            this.label = label;
            this.selectable = selectable;
        }
    }
}
