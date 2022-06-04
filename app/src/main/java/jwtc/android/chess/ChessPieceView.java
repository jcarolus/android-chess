package jwtc.android.chess;

import android.content.Context;
import android.util.Log;
import android.view.View;


import androidx.appcompat.widget.AppCompatImageView;

public class ChessPieceView extends AppCompatImageView {

    public ChessPieceView(Context context) {
        super(context);
        setFocusable(true);
    }

    @Override
    protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        View parent = (View) this.getParent();
        int widthSize = parent.getWidth();

        Log.d("Piece", "onMeasure" + widthSize);

        setMeasuredDimension(widthSize, widthSize);
    }
}
