package jwtc.android.chess;

import android.content.Context;
import android.content.SharedPreferences;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Rect;
import android.util.AttributeSet;
import android.util.Log;
import android.view.View;
import android.view.ViewGroup;


import jwtc.chess.board.ChessBoard;

import static jwtc.android.chess.ChessImageView._arrColorScheme;

public class ChessBoardLayout extends ViewGroup {
    public static final String TAG = "ChessBoardLayout";
    public static Paint _paint = new Paint();

    static {
        _paint.setStyle(Paint.Style.FILL);
    }

    public ChessBoardLayout(Context context) {
        super(context);

        setWillNotDraw(false);
    }

    public ChessBoardLayout(Context context, AttributeSet attributeSet) {
        super(context, attributeSet);

        setWillNotDraw(false);
    }

//    @Override
//    public void addView(View view) {
//        super.addView(view);
//    }

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

            Log.i(TAG, "onLayout" + i);

            if (child.getVisibility() != GONE) {
                child.layout(0, 0, width, width);
            }
        }

    }

    @Override
    protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        // take the full width
        int widthSize = MeasureSpec.getSize(widthMeasureSpec);
        setMeasuredDimension(widthSize, widthSize);

        Log.i(TAG, "onMeasure" + widthSize);
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);

        int w = getWidth() / 8;

        if (w == 0) {
            Log.i("ChessBoard", "No width");
            return;
        }

        SharedPreferences prefs = getContext().getSharedPreferences("ChessPlayer", Context.MODE_PRIVATE);
        int _colorScheme = prefs.getInt("ColorScheme", 0);
        int whiteColor = _colorScheme == 6 ? prefs.getInt("color2", 0xffdddddd) : _arrColorScheme[_colorScheme][1];
        int blackColor = _colorScheme == 6 ?prefs.getInt("color1", 0xffff0066) : _arrColorScheme[_colorScheme][0];
        int fieldColor;

        for (int row = 0; row < 8; row++) {
            for (int col = 0; col < 8; col++) {
                // @TODO flippedBoard
                fieldColor = (row & 1) == 0
                        ? ((col & 1) == 0 ? ChessBoard.WHITE : ChessBoard.BLACK)
                        : ((col & 1) == 0 ? ChessBoard.BLACK : ChessBoard.WHITE);


                _paint.setColor(fieldColor == ChessBoard.WHITE ? whiteColor : blackColor);
                canvas.drawRect(new Rect(w * col, w * row, w * col + w, w * row + w), _paint);
            }
        }
    }
}
