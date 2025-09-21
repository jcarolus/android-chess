package jwtc.android.chess.play;

import android.app.Dialog;
import android.content.Context;
import android.view.View;
import android.view.Window;
import android.widget.AdapterView;
import android.widget.Button;
import android.widget.GridView;
import android.widget.ImageButton;
import android.widget.SimpleAdapter;


import java.util.ArrayList;
import java.util.HashMap;

import androidx.annotation.NonNull;
import jwtc.android.chess.R;
import jwtc.android.chess.helpers.Clipboard;
import jwtc.android.chess.play.MoveItem;
import jwtc.android.chess.services.GameApi;
import jwtc.chess.JNI;
import jwtc.chess.PGNEntry;
import jwtc.chess.Pos;

public class PGNDialog extends Dialog {
    private static final String TAG = "PGNDialog";

    public PGNDialog(@NonNull final Context context, final GameApi gameApi) {
        super(context);

        requestWindowFeature(Window.FEATURE_NO_TITLE);

        setContentView(R.layout.full_pgn);

        final JNI jni = JNI.getInstance();

        ArrayList<MoveItem> mapMoves = new ArrayList<MoveItem>();
        MoveItemAdapter adapterMoves = new MoveItemAdapter(context, mapMoves);

        GridView contentLayout = findViewById(R.id.LayoutContent);

        contentLayout.setAdapter(adapterMoves);

        ArrayList<PGNEntry> pgnEntries = gameApi.getPGNEntries();

        for (int i = 0; i < pgnEntries.size(); i++) {
            String sMove =  pgnEntries.get(i)._sMove;
            if (pgnEntries.get(i)._duckMove != -1) {
                sMove += "@" + Pos.toString(pgnEntries.get(i)._duckMove);
            }
            String nr = i % 2 == 0 ? ((i/2+1) + ". ") : " ";
            String annotation = pgnEntries.get(i)._sAnnotation;
            int turn = (jni.getNumBoard() - 1 == i ? R.drawable.turnblack : 0);

            mapMoves.add(new MoveItem(nr, sMove, annotation, turn));

        }

        adapterMoves.notifyDataSetChanged();
        contentLayout.smoothScrollToPosition(adapterMoves.getCount());

        contentLayout.setOnItemClickListener(new AdapterView.OnItemClickListener() {
            @Override
            public void onItemClick(AdapterView<?> parent, View view, int position, long id) {
                if (jni.getNumBoard() > position) {
                    position++;
                }
                gameApi.jumpToBoardNum(position);
                dismiss();
            }
        });

        ImageButton buttonClip = findViewById(R.id.ButtonClip);
        buttonClip.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                Clipboard.stringToClipboard(context, gameApi.exportFullPGN(), context.getString(R.string.copied_clipboard_success));
                dismiss();
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
