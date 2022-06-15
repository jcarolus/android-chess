package jwtc.android.chess.ics;

import android.content.Context;
import android.os.Bundle;
import android.view.View;
import android.widget.AdapterView;
import android.widget.ListView;
import android.widget.SimpleAdapter;

import java.util.HashMap;

import androidx.annotation.NonNull;
import jwtc.android.chess.R;
import jwtc.android.chess.helpers.ResultDialog;
import jwtc.android.chess.helpers.ResultDialogListener;

public class ICSMenuDialog extends ResultDialog implements AdapterView.OnItemClickListener {
    private SimpleAdapter menuAdapter;

    public ICSMenuDialog(@NonNull Context context, ResultDialogListener listener, int requestCode, SimpleAdapter menuAdapter) {
        super(context, listener, requestCode);

        setContentView(R.layout.ics_menu);

        this.menuAdapter = menuAdapter;
        ListView list = findViewById(R.id.ListMenu);

        list.setAdapter(menuAdapter);
        list.setOnItemClickListener(this);
    }

    @Override
    public void onItemClick(AdapterView<?> parent, View view, int position, long id) {
        HashMap<String,String> item = (HashMap<String,String>)menuAdapter.getItem(position);
        Bundle data = new Bundle();
        data.putCharSequence("item", item.get("menu_item"));
        setResult(data);
        dismiss();
    }
}
