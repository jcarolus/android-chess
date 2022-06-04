package jwtc.android.chess;

import android.content.Context;
import android.util.AttributeSet;
import android.util.Log;
import android.view.View;
import android.view.ViewGroup;


import jwtc.chess.board.BoardConstants;

public class ChessBoardLayout extends ViewGroup {
    public static final String TAG = "ChessBoardLayout";

    public ChessBoardLayout(Context context) {
        super(context);
    }

    public ChessBoardLayout(Context context, AttributeSet attributeSet) {
        super(context, attributeSet);
    }

    public void removePieces() {
        final int count = getChildCount();
        for (int i = 0; i < count; i++) {
            final View child = getChildAt(i);
            if (child instanceof ChessPieceView) {
                removeView(child);
            }
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
        Log.i(TAG, " onLayout " + changed + " l " + l );

        final int count = getChildCount();
        final int width = getWidth() / 8;

        for (int i = 0; i < count; i++) {
            final View child = getChildAt(i);

            if (child.getVisibility() != GONE) {
                layoutChild(child);
            }
        }

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
        final int row = BoardConstants.ROW[pos];
        final int col = BoardConstants.COL[pos];
        child.layout(col * width, row * width, col * width + width, row * width + width);
    }

}
