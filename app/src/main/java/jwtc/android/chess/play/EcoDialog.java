package jwtc.android.chess.play;

import android.content.Context;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.ListView;

import androidx.annotation.NonNull;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.ArrayList;

import jwtc.android.chess.R;
import jwtc.android.chess.helpers.ResultDialog;
import jwtc.android.chess.helpers.ResultDialogListener;
import jwtc.android.chess.services.EcoService;

public class EcoDialog extends ResultDialog {
    private static final String TAG = "EcoDialog";
    public EcoDialog(@NonNull Context context, ResultDialogListener listener, int requestCode, String title, JSONArray jArray) {
        super(context, listener, requestCode);

        setTitle(title);
        setContentView(R.layout.eco_menu);

        ListView list = findViewById(R.id.EcoMenu);

        ArrayList<String> itemList = new ArrayList<>();
        for (int i = 0; i < jArray.length(); i++) {
            try {
                JSONObject obj = jArray.getJSONObject(i);
                String name = obj.getString("name");
                String move = obj.getString("move");
                itemList.add(move + ": " + name);
            } catch (Exception ignored) {}
        }

        ArrayAdapter<String> adapter = new ArrayAdapter<>(
                context,
                R.layout.simple_text,
                itemList
        );

        list.setAdapter(adapter);

        list.setOnItemClickListener(new AdapterView.OnItemClickListener() {
            @Override
            public void onItemClick(AdapterView<?> parent, View view, int position, long id) {
                Bundle data = new Bundle();
                try {
                    JSONObject jObj = jArray.getJSONObject(position);
                    data.putCharSequence("item", jObj.getString("move"));
                } catch (Exception ignored) {}
                setResult(data);
                dismiss();
            }
        });
    }
}
