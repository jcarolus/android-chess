package jwtc.android.chess.play;

import android.content.Context;
import android.os.Bundle;
import android.widget.ListView;
import android.widget.ArrayAdapter;
import java.util.ArrayList;
import java.util.Arrays;

import androidx.annotation.NonNull;

import jwtc.android.chess.R;
import jwtc.android.chess.helpers.ResultDialog;
import jwtc.android.chess.helpers.ResultDialogListener;

public class MenuDialog extends ResultDialog<Bundle> {
    public void setKingOfTheHill(boolean active) {
        ArrayList<String> items = new ArrayList<>(Arrays.asList(getContext().getResources().getStringArray(R.array.play_menu)));
        if (active) {
            items.remove(getContext().getString(R.string.menu_analyse_game));
            items.remove(getContext().getString(R.string.menu_setup));
        }
        ListView list = findViewById(R.id.ListMenu);
        list.setAdapter(new ArrayAdapter<>(getContext(), android.R.layout.simple_list_item_1, items));
    }
    public MenuDialog(@NonNull Context context, ResultDialogListener<Bundle> listener, int requestCode) {
        super(context, listener, requestCode);

        setTitle(R.string.title_menu);
        setContentView(R.layout.play_menu);

        ListView list = findViewById(R.id.ListMenu);
        list.setOnItemClickListener((parent, view, position, id) -> {
            String item = parent.getItemAtPosition(position).toString();
            Bundle data = new Bundle();
            data.putCharSequence("item", item);
            setResult(data);
            dismiss();
        });
    }
}
