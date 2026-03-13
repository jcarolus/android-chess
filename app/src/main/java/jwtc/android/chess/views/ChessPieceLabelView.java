package jwtc.android.chess.views;

import android.content.Context;
import android.graphics.Canvas;
import android.util.TypedValue;
import android.view.Gravity;

import androidx.appcompat.widget.AppCompatTextView;

import jwtc.android.chess.R;
import jwtc.chess.board.BoardConstants;

public class ChessPieceLabelView extends AppCompatTextView {
    private final int position;
    public static final String MATE_LOSER = "#";
    public static final String MATE_WINNER = "\uD83D\uDF32";
    public static final String FLAG = "⚑";
    public static final String CHECK = "+";
    public static final String DRAW = "½";
    public static final String DRAW_50 = "50";
    public static final String CORRECT = "✓"; //"✓";
    public static final String WRONG = "✕"; //"✖";


    public ChessPieceLabelView(Context context, int position, int color, String label) {
        super(context);

        this.setFocusable(false);
        this.setImportantForAccessibility(IMPORTANT_FOR_ACCESSIBILITY_NO);

        this.position = position;

        setWillNotDraw(false);
        setGravity(Gravity.CENTER);
        setIncludeFontPadding(false);

        if (color == BoardConstants.BLACK) {
            setTextColor(0xFFFFFFFF);
            setBackgroundResource(R.drawable.turnblack);
        } else {
            setBackgroundResource(R.drawable.turnwhite);
            setTextColor(0xFF000000);
        }
        if (CORRECT.equals(label) || MATE_WINNER.equals(label)) {
            setTextColor(0xFF00CC00);
        } else if (WRONG.equals(label) || MATE_LOSER.equals(label) || FLAG.equals(label)) {
            setTextColor(0xFFCC0000);
        }
        setText(label);
    }

    public int getPos() {
        return position;
    }

    public void onDraw(Canvas canvas) {
        int textSize = 3 * getHeight() / 4;
        setTextSize(TypedValue.COMPLEX_UNIT_PX, textSize >= 8 ? textSize : 8);

        super.onDraw(canvas);
    }
}
