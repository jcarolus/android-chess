package jwtc.android.chess.play;

import android.app.Dialog;
import android.content.Context;
import android.util.Log;
import android.view.View;
import android.widget.AdapterView;
import android.widget.Button;
import android.widget.GridView;
import android.widget.SimpleAdapter;


import java.util.ArrayList;
import java.util.HashMap;

import androidx.annotation.NonNull;
import jwtc.android.chess.R;
import jwtc.android.chess.services.GameApi;
import jwtc.chess.PGNEntry;

public class PGNDialog extends Dialog {
    private static final String TAG = "PGNDialog";

    public PGNDialog(@NonNull Context context, GameApi gameApi) {
        super(context);

        setContentView(R.layout.full_pgn);

        ArrayList<HashMap<String, String>> mapMoves = new ArrayList<HashMap<String, String>>();
        SimpleAdapter adapterMoves = new SimpleAdapter(context, mapMoves, R.layout.pgn_item,
                new String[]{"nr", "move"}, new int[]{R.id.TextViewNumMove, R.id.TextViewNumMove});

        GridView contentLayout = findViewById(R.id.LayoutContent);

        contentLayout.setAdapter(adapterMoves);

        ArrayList<PGNEntry> pgnEntries = gameApi.getPGNEntries();

        for (int i = 0; i < pgnEntries.size(); i++) {
            HashMap<String, String> item = new HashMap<String, String>();
            item.put("nr", "" + i);
            item.put("move", pgnEntries.get(i)._sMove);

            mapMoves.add(item);
        }

        adapterMoves.notifyDataSetChanged();

        contentLayout.setOnItemClickListener(new AdapterView.OnItemClickListener() {
            @Override
            public void onItemClick(AdapterView<?> parent, View view, int position, long id) {


            }
        });


        Button buttonClose = findViewById(R.id.ButtonClose);
        buttonClose.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                dismiss();
            }
        });
    }
}
