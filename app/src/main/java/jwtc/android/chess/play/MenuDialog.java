package jwtc.android.chess.play;

import android.content.Context;
import android.os.Bundle;
import android.view.View;
import android.widget.AdapterView;
import android.widget.ListView;

import androidx.annotation.NonNull;
import jwtc.android.chess.R;
import jwtc.android.chess.helpers.ResultDialog;
import jwtc.android.chess.helpers.ResultDialogListener;

public class MenuDialog extends ResultDialog {
    public MenuDialog(@NonNull Context context, ResultDialogListener listener, int requestCode) {
        super(context, listener, requestCode);

        setTitle(R.string.title_menu);
        setContentView(R.layout.play_menu);

        ListView list = findViewById(R.id.ListMenu);
        list.setOnItemClickListener(new AdapterView.OnItemClickListener() {
            @Override
            public void onItemClick(AdapterView<?> parent, View view, int position, long id) {
                String item = parent.getItemAtPosition(position).toString();
                Bundle data = new Bundle();
                data.putCharSequence("item", item);
                setResult(data);
                dismiss();
            }
        });
    }
}
