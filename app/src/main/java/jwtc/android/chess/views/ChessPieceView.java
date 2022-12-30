package jwtc.android.chess.views;

import android.content.Context;
import android.util.Log;
import android.view.View;


import androidx.appcompat.widget.AppCompatImageView;
import jwtc.android.chess.R;
import jwtc.android.chess.constants.PieceSets;
import jwtc.chess.board.BoardConstants;

public class ChessPieceView extends AppCompatImageView {
    private int color;
    private int piece;
    private int pos;


    public ChessPieceView(Context context, int color, int piece, int pos) {
        super(context);

        this.pos = pos;
        this.piece = piece;
        this.color = color;

        setMyImageResource();
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
        if (this.pos != pos) {
            this.pos = pos;
            this.invalidate();
        }
    }

    public void promote(int piece) {
        this.piece = piece;
        resetImageResource();
    }


    @Override
    protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        super.onMeasure(widthMeasureSpec, heightMeasureSpec);
        View parent = (View) this.getParent();
        int widthSize = parent.getWidth();

//        Log.d("Piece", "onMeasure" + widthSize);

        setMeasuredDimension(widthSize, widthSize);
    }

    public void resetImageResource() {
        setMyImageResource();
        invalidate();
    }

    protected void setMyImageResource() {
        if (PieceSets.selectedBlindfoldMode == PieceSets.BLINDFOLD_SHOW_PIECES) {
            if (piece == BoardConstants.DUCK) {
                setImageResource(R.drawable.ic_duck);
            } else {
                setImageResource(PieceSets.PIECES[PieceSets.selectedSet][color][piece]);
            }
        } else if (PieceSets.selectedBlindfoldMode == PieceSets.BLINDFOLD_SHOW_PIECE_LOCATION) {
            setImageResource(color == BoardConstants.WHITE ? R.drawable.turnwhite : R.drawable.turnblack);
        } else {
            setImageResource(android.R.color.transparent);
        }
    }
}
