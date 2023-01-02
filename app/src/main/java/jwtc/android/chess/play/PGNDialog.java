package jwtc.android.chess.play;

import android.app.Dialog;
import android.content.Context;

import androidx.annotation.NonNull;
import jwtc.android.chess.R;
import jwtc.android.chess.services.GameApi;

public class PGNDialog extends Dialog {

    public PGNDialog(@NonNull Context context, GameApi gameApi) {
        super(context);

        setContentView(R.layout.full_pgn);



    }
}
