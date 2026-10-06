package jwtc.android.chess.views;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.util.AttributeSet;
import android.view.View;

import jwtc.android.chess.R;

/** White-relative evaluation in pawns; black fills the top, white the bottom. */
public class EngineEvaluationView extends View {
    private final Paint paint = new Paint();
    private float whiteFraction = 0.5f;

    public EngineEvaluationView(Context context, AttributeSet attrs) {
        super(context, attrs);
        setImportantForAccessibility(IMPORTANT_FOR_ACCESSIBILITY_YES);
        clearEvaluation();
    }

    public void setEvaluation(float whitePawns) {
        if (Float.isNaN(whitePawns)) {
            clearEvaluation();
            return;
        }
        // Smoothly map an unbounded pawn score into [0, 1], balanced at zero.
        whiteFraction = (float) (0.5 + 0.5 * Math.tanh(whitePawns / 4.0));
        setContentDescription(getResources().getString(R.string.engine_evaluation_score, whitePawns));
        invalidate();
    }

    public void clearEvaluation() {
        whiteFraction = 0.5f;
        setContentDescription(getResources().getString(R.string.engine_evaluation_pending));
        invalidate();
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        float border = getResources().getDisplayMetrics().density;
        float width = getWidth();
        float height = getHeight();
        float split = border + Math.max(0, height - 2 * border) * (1 - whiteFraction);
        paint.setColor(Color.GRAY);
        canvas.drawRect(0, 0, width, height, paint);
        paint.setColor(Color.BLACK);
        canvas.drawRect(border, border, width - border, split, paint);
        paint.setColor(Color.WHITE);
        canvas.drawRect(border, split, width - border, height - border, paint);
    }
}
