package jwtc.android.chess.views;

import android.content.Context;
import android.util.AttributeSet;
import android.util.Log;
import android.view.View;
import android.view.ViewGroup;

import jwtc.android.chess.constants.ColorSchemes;
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

    public void removeLabels() {
        int i = 0;
        while(i < getChildCount()) {
            final View child = getChildAt(i);
            if (child instanceof ChessPieceLabelView) {
                removeView(child);
                continue;
            }
            i++;
        }
    }

    public void setRotated(boolean rotated) {
        ColorSchemes.isRotated = rotated;
        if (this.rotated != rotated) {
            this.rotated = rotated;
            final int count = getChildCount();
            for (int i = 0; i < count; i++) {
                final View child = getChildAt(i);
                if (child instanceof ChessSquareView) {
                    // depends on rotated state, so invalidate
                    child.invalidate();
                }
            }

            requestLayout();
        }
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

    public void invalidateSquares() {
        final int count = getChildCount();
        for (int i = 0; i < count; i++) {
            final View child = getChildAt(i);
            if (child instanceof ChessSquareView) {
                ((ChessSquareView)child).invalidate();
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
        layoutChildren();
    }

    @Override
    protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        super.onMeasure(widthMeasureSpec, heightMeasureSpec);
        // take the full width in portrait
        int widthSize = MeasureSpec.getSize(widthMeasureSpec);
        int heightSize =  MeasureSpec.getSize(heightMeasureSpec);
        int size = widthSize < heightSize ? widthSize : heightSize;
        setMeasuredDimension(size, size);
    }

    public void layoutChild(View child) {
        final int pos = child instanceof ChessSquareView
                ? ((ChessSquareView) child).getPos()
                : (child instanceof ChessPieceView
                    ? ((ChessPieceView) child).getPos()
                    : ((ChessPieceLabelView) child).getPos());
        final int width = getWidth() / 8;
        // rotated
        final int actualPos = rotated ? 63 - pos : pos;
        final int row = BoardConstants.ROW[actualPos];
        final int col = BoardConstants.COL[actualPos];

        if (child instanceof ChessPieceLabelView) {
            child.layout(col * width, row * width, col * width + width / 2, row * width + width / 2);
        } else {
            child.layout(col * width, row * width, col * width + width, row * width + width);
        }
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
