package jwtc.android.chess.play;

import android.content.Context;
import android.os.Bundle;
import android.widget.ArrayAdapter;
import android.widget.ListView;
import androidx.annotation.ArrayRes;
import androidx.annotation.NonNull;
import jwtc.android.chess.R;
import jwtc.android.chess.helpers.ResultDialog;
import jwtc.android.chess.helpers.ResultDialogListener;

public class MenuDialog extends ResultDialog<Bundle> {
    public MenuDialog(
            @NonNull Context context,
            ResultDialogListener<Bundle> listener,
            int requestCode,
            @ArrayRes int menuItems) {
        super(context, listener, requestCode);

        setTitle(R.string.title_menu);
        setContentView(R.layout.play_menu);

        ListView list = findViewById(R.id.ListMenu);
        list.setAdapter(
                new ArrayAdapter<>(
                        context,
                        android.R.layout.simple_list_item_1,
                        context.getResources().getStringArray(menuItems)));
        list.setOnItemClickListener(
                (parent, view, position, id) -> {
                    String item = parent.getItemAtPosition(position).toString();
                    Bundle data = new Bundle();
                    data.putCharSequence("item", item);
                    setResult(data);
                    dismiss();
                });
    }
}
