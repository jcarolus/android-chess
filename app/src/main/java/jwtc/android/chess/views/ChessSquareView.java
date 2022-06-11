package jwtc.android.chess.views;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Rect;
import android.view.View;

import jwtc.android.chess.constants.ColorSchemes;
import jwtc.chess.board.ChessBoard;

public class ChessSquareView extends View {
    private int pos;
    private boolean selected;
    private boolean highlighted;

    private static Paint paint = new Paint();
    private static Paint highlightPaint = new Paint();

    public ChessSquareView(Context context, int pos) {
        super(context);
        this.pos = pos;
        selected = false;
        highlighted = false;
    }

    public int getPos() {
        return pos;
    }

    public void setPos(int pos) {
        if (this.pos != pos) {
            this.pos = pos;
            this.invalidate();
        }
    }

    public void setSelected(boolean selected) {
        if (this.selected != selected) {
            this.selected = selected;
            this.invalidate();
        }
    }

    public boolean getSelected() {
        return this.selected;
    }

    public void setHighlighted(boolean highlighted) {
        if (this.highlighted != highlighted) {
            this.highlighted = highlighted;
            this.invalidate();
        }
    }

    public boolean getHighlighted() {
        return this.highlighted;
    }

    public void onDraw(Canvas canvas) {
        if (selected) {
            paint.setColor(ColorSchemes.getSelectedColor());
        } else {
            final int fieldColor = (pos & 1) == 0 ? (((pos >> 3) & 1) == 0 ? ChessBoard.WHITE : ChessBoard.BLACK) : (((pos >> 3) & 1) == 0 ? ChessBoard.BLACK : ChessBoard.WHITE);
            paint.setColor(fieldColor == ChessBoard.WHITE ? ColorSchemes.getLight() : ColorSchemes.getDark());
        }
        canvas.drawRect(new Rect(0, 0, getWidth(), getHeight()), paint);

        if (highlighted) {
            highlightPaint.setStyle(Paint.Style.STROKE);
            highlightPaint.setStrokeWidth(getWidth() / 10);
            highlightPaint.setColor(ColorSchemes.getHightlightColor());
            canvas.drawRect(new Rect(0, 0, getWidth(), getHeight()), highlightPaint);
        }
    }
}
