package jwtc.android.chess.play;

import android.app.Dialog;
import android.content.Context;
import android.view.Window;
import android.widget.GridView;

import java.util.ArrayList;

import androidx.annotation.NonNull;

import com.google.android.material.button.MaterialButton;

import jwtc.android.chess.R;
import jwtc.android.chess.helpers.Clipboard;
import jwtc.android.chess.services.GameApi;
import jwtc.chess.JNI;
import jwtc.chess.PGNEntry;
import jwtc.chess.Pos;

public class PGNDialog extends Dialog {
    private static final String TAG = "PGNDialog";

    public PGNDialog(@NonNull final Context context, final GameApi gameApi) {
        super(context, R.style.ChessDialogTheme);

        requestWindowFeature(Window.FEATURE_NO_TITLE);

        setContentView(R.layout.full_pgn);

        final JNI jni = JNI.getInstance();

        ArrayList<MoveItem> mapMoves = new ArrayList<MoveItem>();
        MoveItemAdapter adapterMoves = new MoveItemAdapter(context, mapMoves);

        GridView contentLayout = findViewById(R.id.LayoutContent);

        contentLayout.setAdapter(adapterMoves);

        ArrayList<PGNEntry> pgnEntries = gameApi.getPGNEntries();

        for (int i = 0; i < pgnEntries.size(); i++) {
            String sMove =  pgnEntries.get(i).sMove;
            if (pgnEntries.get(i).duckMove != -1) {
                sMove += "@" + Pos.toString(pgnEntries.get(i).duckMove);
            }
            String nr = i % 2 == 0 ? ((i/2+1) + ". ") : " ";
            String annotation = pgnEntries.get(i).sAnnotation;
            int turn = (jni.getNumBoard() - 1 == i ? R.drawable.turnblack : 0);

            mapMoves.add(new MoveItem(nr, sMove, pgnEntries.get(i).move, annotation, turn));

        }

        adapterMoves.notifyDataSetChanged();
        contentLayout.smoothScrollToPosition(adapterMoves.getCount());

        contentLayout.setOnItemClickListener((parent, view, position, id) -> {
            if (jni.getNumBoard() > position) {
                position++;
            }
            gameApi.jumpToBoardNum(position);
            dismiss();
        });

        MaterialButton buttonClip = findViewById(R.id.ButtonClip);
        buttonClip.setOnClickListener(view -> {
            Clipboard.stringToClipboard(context, gameApi.exportFullPGN(), context.getString(R.string.copied_clipboard_success));
            dismiss();
        });

        MaterialButton buttonClose = findViewById(R.id.ButtonClose);
        buttonClose.setOnClickListener(view -> dismiss());
    }
}
