package jwtc.android.chess.views;

import android.content.Context;
import android.util.Log;
import android.view.View;


import androidx.appcompat.widget.AppCompatImageView;
import jwtc.android.chess.constants.PieceSets;

public class ChessPieceView extends AppCompatImageView {
    private int set;
    private int color;
    private int piece;
    private int pos;


    public ChessPieceView(Context context, int set, int color, int piece, int pos) {
        super(context);
        setFocusable(true);

        setImageResource(PieceSets.PIECES[set][color][piece]);
        this.set = set;
        this.pos = pos;
        this.piece = piece;
        this.color = color;
    }

    public int getPos() {
        return pos;
    }

    public int getPiece() {
        return piece;
    }

    public int getColor() {
        return color;
    }

    public void setPos(int pos) {
        this.pos = pos;
        this.invalidate();
    }

    public void promote(int piece) {
        setImageResource(PieceSets.PIECES[set][color][piece]);
        this.piece = piece;
        invalidate();
    }

    @Override
    protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        View parent = (View) this.getParent();
        int widthSize = parent.getWidth();

        Log.d("Piece", "onMeasure" + widthSize);

        setMeasuredDimension(widthSize, widthSize);
    }
}
