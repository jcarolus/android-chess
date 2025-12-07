package jwtc.android.chess.views;

import android.content.Context;
import android.util.AttributeSet;
import android.util.Log;
import android.view.View;
import android.view.ViewGroup;

public class ChessBoardContainerView extends ViewGroup {
    public static final String TAG = "ChessBoardContainerView";

    private View top;
    private View board;
    private View bottom;

    public ChessBoardContainerView(Context context) {
        super(context);
    }

    public ChessBoardContainerView(Context context, AttributeSet attributeSet) {
        super(context, attributeSet);
    }

    @Override
    protected void onFinishInflate() {
        super.onFinishInflate();

        // Now children exist
        if (getChildCount() != 3) {
            throw new IllegalStateException("StretchLayout must have exactly 3 children");
        }

        init();
    }

    protected void init() {
        // Expect exactly 3 children in layout XML
        top = getChildAt(0);
        board = getChildAt(1);
        bottom = getChildAt(2);

        Log.d(TAG, "init " + top + " " + board + " " + bottom);
    }

    @Override public LayoutParams generateLayoutParams(AttributeSet attrs) {
        return new MarginLayoutParams(getContext(), attrs);
    }

    @Override protected LayoutParams generateDefaultLayoutParams() {
        return new MarginLayoutParams(LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT);
    }

    @Override protected LayoutParams generateLayoutParams(LayoutParams lp) {
        return new MarginLayoutParams(lp);
    }

    @Override protected boolean checkLayoutParams(LayoutParams lp) {
        return lp instanceof MarginLayoutParams;
    }

    @Override
    protected void onLayout(boolean changed, int l, int t, int r, int b) {
        int curTop = 0;

        top.layout(0, curTop,
                top.getMeasuredWidth(),
                curTop + top.getMeasuredHeight());
        curTop += top.getMeasuredHeight();

        board.layout(0, curTop,
                board.getMeasuredWidth(),
                curTop + board.getMeasuredHeight());
        curTop += board.getMeasuredHeight();

        bottom.layout(0, curTop,
                bottom.getMeasuredWidth(),
                curTop + bottom.getMeasuredHeight());
    }

    @Override
    protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        super.onMeasure(widthMeasureSpec, heightMeasureSpec);
        int widthSize = MeasureSpec.getSize(widthMeasureSpec);
        int heightSize =  MeasureSpec.getSize(heightMeasureSpec);
        int minSize = Math.min(widthSize, heightSize);

        top.measure(
                MeasureSpec.makeMeasureSpec(minSize, MeasureSpec.UNSPECIFIED),
                MeasureSpec.makeMeasureSpec(minSize, MeasureSpec.UNSPECIFIED)
        );
        bottom.measure(
                MeasureSpec.makeMeasureSpec(minSize, MeasureSpec.UNSPECIFIED),
                MeasureSpec.makeMeasureSpec(minSize, MeasureSpec.UNSPECIFIED)
        );

        int topHeight = top.getMeasuredHeight();
        int bottomHeight = bottom.getMeasuredHeight();
        int boardSize = minSize - (topHeight + bottomHeight);

        int boardWidthSpec = getChildMeasureSpec(widthMeasureSpec, 0, boardSize);
        int boardHeightSpec = getChildMeasureSpec(heightMeasureSpec, 0, boardSize);

        top.measure(boardWidthSpec, MeasureSpec.makeMeasureSpec(topHeight, MeasureSpec.EXACTLY));
        board.measure(boardWidthSpec, boardHeightSpec);
        bottom.measure(boardWidthSpec, MeasureSpec.makeMeasureSpec(bottomHeight, MeasureSpec.EXACTLY));

        int width = resolveSize(boardSize, widthMeasureSpec);
        int height = resolveSize(minSize, heightMeasureSpec);

        setMeasuredDimension(width, height);
    }
}
