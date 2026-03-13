package jwtc.android.chess.views;

import android.content.Context;
import android.graphics.Typeface;
import android.util.Log;
import android.util.TypedValue;
import android.view.Gravity;
import android.widget.TextView;

import androidx.appcompat.widget.AppCompatTextView;
import androidx.core.content.ContextCompat;

import jwtc.android.chess.R;
import jwtc.android.chess.constants.ColorSchemes;
import jwtc.chess.board.BoardConstants;

public class CapturedCountView extends AppCompatTextView {
    private static final String TAG = "CapturedCountView";
    private int piece;

    public CapturedCountView(Context context, int count, int piece, int color) {
        super(context);

        this.setFocusable(false);
        this.setImportantForAccessibility(IMPORTANT_FOR_ACCESSIBILITY_NO);
        this.piece = piece;

        setWillNotDraw(false);
        setGravity(Gravity.CENTER);
        setTypeface(Typeface.MONOSPACE);

        if (color == BoardConstants.BLACK) {
            setTextColor(0xFF000000);
            setBackgroundResource(R.drawable.whitecircle);
        } else {
            setTextColor(0xFFFFFFFF);
            setBackgroundResource(R.drawable.blackcircle);
        }

        if (count > 0) {
            setText("" + count);
        } else {
            setText("");
        }
    }

    public int getPiece() {
        return piece;
    }

    @Override
    protected void onSizeChanged(int w, int h, int oldW, int oldH) {
        super.onSizeChanged(w, h, oldW, oldH);
        int textSize = h * 3 / 4;
        setTextSize(TypedValue.COMPLEX_UNIT_PX, Math.max(textSize, 8));
    }
}
