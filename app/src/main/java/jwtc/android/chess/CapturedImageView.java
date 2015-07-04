package jwtc.android.chess;


import android.content.Context;
import android.content.res.AssetManager;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Matrix;
import android.graphics.Paint;
import android.util.AttributeSet;
import android.util.Log;
import android.view.View;

public class CapturedImageView extends View {

    private Bitmap _bitMap;

    public CapturedImageView(Context context) {
        super(context);
        setFocusable(false);
    }
    public CapturedImageView(Context context, AttributeSet atts) {
        super(context, atts);
        setFocusable(false);
    }

    public void initBitmap(String sPiece){

        AssetManager am = this.getContext().getAssets();
        String sFolder = "highres/";
        try {
            _bitMap = BitmapFactory.decodeStream(am.open(sFolder + sPiece));
            //Log.i("CapturedImageView", "initBitmap " + sFolder + sPiece);
        } catch (Exception ex){

        }
    }

    public void onDraw(Canvas canvas) {

        float scale = (float)getWidth() / _bitMap.getWidth();
        Matrix m = new Matrix();
        m.setScale(scale, scale);
        Paint p = new Paint();
        p.setFlags(Paint.ANTI_ALIAS_FLAG);
        p.setFilterBitmap(true);
        p.setColor(Color.BLACK);

        canvas.drawBitmap(_bitMap, m, p);

        //Log.i("CapturedImageView", "onDraw");
    }
}
