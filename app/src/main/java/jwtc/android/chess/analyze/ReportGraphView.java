package jwtc.android.chess.analyze;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Path;
import android.util.AttributeSet;
import androidx.appcompat.widget.AppCompatSeekBar;
import jwtc.android.chess.helpers.EinkMode;

/** An evaluation graph with SeekBar keyboard and accessibility navigation. */
public final class ReportGraphView extends AppCompatSeekBar {
    private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Path area = new Path();
    private AnalysisReport report;

    public ReportGraphView(Context context, AttributeSet attrs) {
        super(context, attrs);
        setSplitTrack(false);
        setKeyProgressIncrement(1);
        float density = getResources().getDisplayMetrics().density;
        setPadding(
                (int) (4 * density), (int) (3 * density), (int) (4 * density), (int) (3 * density));
    }

    void setReport(AnalysisReport report, int selected) {
        this.report = report;
        setMax(Math.max(0, report.positions.size() - 1));
        setKeyProgressIncrement(1);
        setProgress(selected);
        invalidate();
    }

    @Override
    protected synchronized void onDraw(Canvas canvas) {
        if (report == null || report.positions.size() < 2) {
            return;
        }
        float left = getPaddingLeft();
        float top = getPaddingTop();
        float width = getWidth() - left - getPaddingRight();
        float height = getHeight() - top - getPaddingBottom();
        float step = width / getMax();
        paint.setStyle(Paint.Style.FILL);
        paint.setColor(Color.DKGRAY);
        canvas.drawRect(left, top, left + width, top + height, paint);
        // Draw only evaluated segments; an unavailable score is a gap, never equality.
        for (int i = 1; i < report.positions.size(); i++) {
            double before = report.positions.get(i - 1).whiteWinPercent;
            double after = report.positions.get(i).whiteWinPercent;
            if (!Double.isFinite(before) || !Double.isFinite(after)) {
                paint.setColor(Color.GRAY);
                canvas.drawRect(left + (i - 1) * step, top, left + i * step, top + height, paint);
                continue;
            }
            float x1 = left + (i - 1) * step;
            float x2 = left + i * step;
            float y1 = top + height * (1 - (float) before / 100);
            float y2 = top + height * (1 - (float) after / 100);
            area.reset();
            area.moveTo(x1, top + height);
            area.lineTo(x1, y1);
            area.lineTo(x2, y2);
            area.lineTo(x2, top + height);
            area.close();
            paint.setStyle(Paint.Style.FILL);
            paint.setColor(Color.WHITE);
            canvas.drawPath(area, paint);
        }
        paint.setColor(Color.rgb(140, 140, 140));
        paint.setStrokeWidth(0);
        for (int i = 0; i <= getMax(); i++) {
            canvas.drawLine(left + i * step, top, left + i * step, top + height, paint);
        }
        paint.setStrokeWidth(getResources().getDisplayMetrics().density);
        canvas.drawLine(left, top + height / 2, left + width, top + height / 2, paint);
        paint.setColor(EinkMode.isThemeEnabled() ? Color.BLACK : Color.rgb(0, 130, 180));
        paint.setStrokeWidth(2 * getResources().getDisplayMetrics().density);
        float x = left + getProgress() * step;
        canvas.drawLine(x, top, x, top + height, paint);
        paint.setStyle(Paint.Style.STROKE);
        paint.setColor(Color.GRAY);
        canvas.drawRect(left, top, left + width, top + height, paint);
        paint.setStyle(Paint.Style.FILL);
    }
}
