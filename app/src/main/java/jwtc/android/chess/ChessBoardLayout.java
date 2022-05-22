package jwtc.android.chess;

import android.content.Context;
import android.content.SharedPreferences;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Rect;
import android.util.AttributeSet;
import android.util.Log;
import android.widget.TableLayout;

import jwtc.chess.board.ChessBoard;

import static jwtc.android.chess.ChessImageView._arrColorScheme;

public class ChessBoardLayout extends TableLayout {

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
