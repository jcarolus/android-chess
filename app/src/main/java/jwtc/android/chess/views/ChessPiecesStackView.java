package jwtc.android.chess.views;

import android.content.Context;
import android.util.AttributeSet;
import android.util.Log;
import android.view.View;
import android.view.ViewGroup;

public class ChessPiecesStackView extends ViewGroup {
    private static final String TAG = "ChessPiecesStackView";

    public ChessPiecesStackView(Context context) {
        super(context);
    }

    public ChessPiecesStackView(Context context, AttributeSet attributeSet) {
        super(context, attributeSet);
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
        int width = MeasureSpec.getSize(widthMeasureSpec);
        int height = width / 5;
        setMeasuredDimension(width , height);
    }

    public void layoutChild(View child, int index, int height) {
        child.layout(index * height, 0, index * height + height, height);
    }
}
