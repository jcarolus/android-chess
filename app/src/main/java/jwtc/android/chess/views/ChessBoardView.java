package jwtc.android.chess.views;

import android.content.Context;
import android.util.AttributeSet;
import android.util.Log;
import android.view.View;
import android.view.ViewGroup;

import jwtc.chess.board.BoardConstants;

public class ChessBoardView extends ViewGroup {
    public static final String TAG = "ChessBoardView";

    private boolean rotated = false;

    public ChessBoardView(Context context) {
        super(context);
    }

    public ChessBoardView(Context context, AttributeSet attributeSet) {
        super(context, attributeSet);
    }

    public void removePieces() {
        int i = 0;
        while(i < getChildCount()) {
            final View child = getChildAt(i);
            if (child instanceof ChessPieceView) {
                removeView(child);
                continue;
            }
            i++;
        }
    }

    public void setRotated(boolean rotated) {
        if (this.rotated != rotated) {
            this.rotated = rotated;
            requestLayout();
        }
    }

    /**
     * Any layout manager that doesn't scroll will want this.
     */
    @Override
    public boolean shouldDelayChildPressedState() {
        return false;
    }

    @Override
    protected void onLayout(boolean changed, int l, int t, int r, int b) {
        layoutChildren();
    }

    @Override
    protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        // take the full width
        int widthSize = MeasureSpec.getSize(widthMeasureSpec);
        setMeasuredDimension(widthSize, widthSize);
    }

    public void layoutChild(View child) {
        final int pos = child instanceof ChessSquareView ? ((ChessSquareView) child).getPos() : ((ChessPieceView) child).getPos();
        final int width = getWidth() / 8;
        // rotated
        final int actualPos = rotated ? 63 - pos : pos;
        final int row = BoardConstants.ROW[actualPos];
        final int col = BoardConstants.COL[actualPos];
        child.layout(col * width, row * width, col * width + width, row * width + width);
    }

    protected void layoutChildren() {
        final int count = getChildCount();

        for (int i = 0; i < count; i++) {
            final View child = getChildAt(i);

            if (child.getVisibility() != GONE) {
                layoutChild(child);
            }
        }
    }
}
