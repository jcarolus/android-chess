package jwtc.android.chess;

import android.content.Context;
import android.util.Log;
import android.view.View;


import androidx.appcompat.widget.AppCompatImageView;
import jwtc.android.chess.constants.PieceSets;

public class ChessPieceView extends AppCompatImageView {
    private int pos;

    public ChessPieceView(Context context, int set, int color, int piece, int pos) {
        super(context);
        setFocusable(true);

        setImageResource(PieceSets.PIECES[set][color][piece]);
        this.pos = pos;
    }

    public int getPos() {
        return pos;
    }

    public void setPos(int pos) {
        this.pos = pos;
        this.invalidate();
    }

    @Override
    protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        View parent = (View) this.getParent();
        int widthSize = parent.getWidth();

        Log.d("Piece", "onMeasure" + widthSize);

        setMeasuredDimension(widthSize, widthSize);
    }
}
