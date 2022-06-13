package jwtc.android.chess.views;

import android.content.Context;
import android.util.TypedValue;
import android.widget.TextView;
import androidx.appcompat.widget.AppCompatTextView;

public class CapturedCountView extends AppCompatTextView {
    private int piece;

    public CapturedCountView(Context context, int count, int piece) {
        super(context);

        this.piece = piece;
        setTextSize(TypedValue.COMPLEX_UNIT_SP,8);

        if (count > 0) {
            setText("" + count);
        } else {
            setText("");
        }
    }

    public int getPiece() {
        return piece;
    }
}
