package jwtc.android.chess.play;

import android.app.Dialog;
import android.content.Context;
import android.util.Log;
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
import jwtc.android.chess.helpers.MoveAdapter;
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

        MoveAdapter moveAdapter = new MoveAdapter(context, gameApi);

        GridView contentLayout = findViewById(R.id.LayoutContent);

        contentLayout.setAdapter(moveAdapter.getAdapter());

        moveAdapter.update();
        contentLayout.smoothScrollToPosition(moveAdapter.getAdapter().getCount());

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
