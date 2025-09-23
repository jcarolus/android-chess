package jwtc.android.chess.helpers;

import android.content.Context;
import android.widget.SimpleAdapter;

import java.util.ArrayList;
import java.util.HashMap;

import jwtc.android.chess.R;
import jwtc.android.chess.services.GameApi;
import jwtc.chess.JNI;
import jwtc.chess.PGNEntry;
import jwtc.chess.Pos;

public class MoveAdapter {

    private final ArrayList<HashMap<String, String>> mapMoves = new ArrayList<HashMap<String, String>>();
    private final GameApi gameApi;
    private final SimpleAdapter adapterMoves;

    public MoveAdapter(Context context, final GameApi gameApi) {
        this.gameApi = gameApi;

        adapterMoves = new SimpleAdapter(context, mapMoves, R.layout.pgn_item,
                new String[]{"turn", "nr", "move", "annotation"}, new int[]{R.id.ImageTurn, R.id.TextViewNumMove, R.id.TextViewMove, R.id.TextViewAnnotation});

    }

    public void update() {
        final JNI jni = JNI.getInstance();

        mapMoves.clear();
        ArrayList<PGNEntry> pgnEntries = gameApi.getPGNEntries();

        for (int i = 0; i < pgnEntries.size(); i++) {
            String sMove =  pgnEntries.get(i)._sMove;
            if (pgnEntries.get(i)._duckMove != -1) {
                sMove += "@" + Pos.toString(pgnEntries.get(i)._duckMove);
            }
            HashMap<String, String> item = new HashMap<String, String>();
            item.put("nr", i % 2 == 0 ? ((i/2 + 1) + ". ") : " ");
            item.put("move", sMove);
            item.put("annotation", pgnEntries.get(i)._sAnnotation);
            item.put("turn", Integer.toString(jni.getNumBoard() - 1 == i ? R.drawable.turnblack : 0));

            mapMoves.add(item);
        }

        adapterMoves.notifyDataSetChanged();
    }

    public SimpleAdapter getAdapter() {
        return adapterMoves;
    }
}
