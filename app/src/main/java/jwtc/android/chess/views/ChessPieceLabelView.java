package jwtc.android.chess.views;

import android.content.Context;
import android.util.TypedValue;
import android.view.Gravity;

import androidx.appcompat.widget.AppCompatTextView;
import jwtc.android.chess.R;
import jwtc.chess.board.ChessBoard;

public class ChessPieceLabelView extends AppCompatTextView {
    private int position;

    public ChessPieceLabelView(Context context, int position, int color, String label) {
        super(context);

        this.position = position;
        setTextSize(TypedValue.COMPLEX_UNIT_SP,12);

        setGravity(Gravity.CENTER);

        if (color == ChessBoard.BLACK) {
            setTextColor(0xFFFFFFFF);
            setBackgroundResource(R.drawable.turnblack);
        } else {
            setBackgroundResource(R.drawable.turnwhite);
            setTextColor(0xFF000000);
        }
        setText(label);
    }

    public int getPos() {
        return position;
    }
}
