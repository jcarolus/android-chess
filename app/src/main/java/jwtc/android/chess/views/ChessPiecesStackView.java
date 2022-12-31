package jwtc.android.chess.views;

import android.content.Context;
import android.util.AttributeSet;
import android.util.Log;
import android.view.View;
import android.view.ViewGroup;

import jwtc.android.chess.R;

public class ChessPiecesStackView extends ViewGroup {
    private static final String TAG = "ChessPiecesStackView";

    public ChessPiecesStackView(Context context) {
        super(context);
    }

    public ChessPiecesStackView(Context context, AttributeSet attributeSet) {
        super(context, attributeSet);
    }

    public void invalidatePieces() {
        final int count = getChildCount();
        for (int i = 0; i < count; i++) {
            final View child = getChildAt(i);
            if (child instanceof ChessPieceView) {
                ((ChessPieceView)child).resetImageResource();
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
        final int count = getChildCount();
        final int height = getHeight();

        for (int i = 0; i < count; i++) {
            final View child = getChildAt(i);

            if (child.getVisibility() != GONE) {
                final int pos = child instanceof ChessSquareView
                        ? ((ChessSquareView) child).getPos()
                        : (child instanceof ChessPieceView
                            ? ((ChessPieceView) child).getPos()
                            : ((CapturedCountView) child).getPiece());
                layoutChild(child, pos, height);
            }
        }
    }

    @Override
    protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        super.onMeasure(widthMeasureSpec, heightMeasureSpec);
        int width = MeasureSpec.getSize(widthMeasureSpec);
        int heightSize = MeasureSpec.getSize(heightMeasureSpec);
        int numChilds = getId() == R.id.duckStack ? 1 : 5; // hack, an attr is another solution

        int height = width / numChilds;
        if (height > heightSize) {
            height = heightSize;
        }

        setMeasuredDimension(width , height);
    }

    public void layoutChild(View child, int index, int height) {
        child.layout(index * height, 0, index * height + height, height);
    }
}
