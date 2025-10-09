package jwtc.android.chess.helpers;

import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Point;
import android.view.View;


public class MagnifyingDragShadowBuilder extends View.DragShadowBuilder {
    private static final float BITMAP_FACTOR = 1.25f;
    private static final float CIRCLE_FACTOR = 2.25f; // must be greater than BITMAP_FACTOR
    private final Bitmap scaledBitmap;
    private final int circleSize;
    private final int bitmapSize;

    private final Point touchPoint = new Point(0, 0); // Store touch point for use in onDrawShadow()

    public MagnifyingDragShadowBuilder(View view) {
        super(view);

        int originalSize = view.getWidth();

        bitmapSize = (int) (originalSize * BITMAP_FACTOR);
        circleSize = (int) (originalSize * CIRCLE_FACTOR);

        Bitmap bitmap = Bitmap.createBitmap(originalSize, originalSize, Bitmap.Config.ARGB_8888);
        Canvas canvas = new Canvas(bitmap);
        view.draw(canvas);

        scaledBitmap = Bitmap.createScaledBitmap(bitmap, bitmapSize, bitmapSize, true);
    }

    @Override
    public void onProvideShadowMetrics(Point shadowSize, Point shadowTouchPoint) {
        int shadowHeight = circleSize + bitmapSize;
        shadowSize.set(circleSize, shadowHeight);
        shadowTouchPoint.set(circleSize / 2, circleSize);

        touchPoint.set(shadowTouchPoint.x, shadowTouchPoint.y);
    }

    @Override
    public void onDrawShadow(Canvas canvas) {
        canvas.drawBitmap(scaledBitmap, (int)((circleSize - bitmapSize) / 2), (int)((circleSize - bitmapSize) / 2), null);

        int radius = circleSize / 2;

        Paint paint = new Paint();
        paint.setAntiAlias(true);
        paint.setColor(Color.BLACK);
        paint.setStyle(Paint.Style.FILL);
        paint.setAlpha(128);

        canvas.drawCircle(touchPoint.x, touchPoint.y, radius, paint);
    }
}