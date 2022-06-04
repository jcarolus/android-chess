package jwtc.android.chess.ui;

import android.app.Activity;
import android.content.ClipData;
import android.content.SharedPreferences;
import android.content.res.AssetManager;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Color;
import android.graphics.Matrix;
import android.graphics.Rect;
import android.os.Bundle;
import android.util.Log;
import android.view.DragEvent;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewTreeObserver;
import android.view.Window;
import android.widget.ImageView;
import android.widget.RelativeLayout;
import android.widget.TableRow;

import java.util.ArrayList;

import jwtc.android.chess.ChessBoardLayout;
import jwtc.android.chess.ChessImageView;
import jwtc.android.chess.ChessPieceView;
import jwtc.android.chess.ImageCacheObject;
import jwtc.android.chess.MyBaseActivity;
import jwtc.android.chess.R;
import jwtc.android.chess.constants.PieceSets;
import jwtc.chess.JNI;
import jwtc.chess.Pos;
import jwtc.chess.board.BoardConstants;
import jwtc.chess.board.ChessBoard;

abstract public class ChessBoardActivity extends MyBaseActivity {

    abstract public void handleClick(int index);

    public void afterCreate() {

        Log.d("ChessBoardActivity", " afterCreate");

        MyDragListener dl = new MyDragListener();
        MyTouchListener tl = new MyTouchListener();

        ChessBoardLayout l = findViewById(R.id.includeboard);
        l.setOnDragListener(dl);

        ChessPieceView p = new ChessPieceView(this);
        
        p.setOnTouchListener(tl);

        p.setImageResource(PieceSets.PIECES[PieceSets.ALPHA][ChessBoard.BLACK][BoardConstants.PAWN]);
        l.addView(p);

        p.invalidate();

        Log.d("ChessBoardActivity", " afterCreate done");

        // @TODO
        JNI.getInstance().newGame();
    }


    private final class MyDragListener implements View.OnDragListener {


        @Override
        public boolean onDrag(View v, DragEvent event) {
            int action = event.getAction();
            Log.i(TAG, "onDrag");
            switch (event.getAction()) {
                case DragEvent.ACTION_DRAG_STARTED:
                    // do nothing
                    break;
                case DragEvent.ACTION_DRAG_ENTERED:
//                    v.setBackgroundDrawable(enterShape);
                    break;
                case DragEvent.ACTION_DRAG_EXITED:
//                    v.setBackgroundDrawable(normalShape);
                    break;
                case DragEvent.ACTION_DROP:
                    // Dropped, reassign View to ViewGroup
                    View view = (View) event.getLocalState();
//                    ViewGroup owner = (ViewGroup) view.getParent();
//                    owner.removeView(view);
//
                    view.setVisibility(View.VISIBLE);
                    break;
                case DragEvent.ACTION_DRAG_ENDED:
                    //v.setBackgroundDrawable(normalShape);
                default:
                    break;
            }
            return true;
        }
    }

    private final class MyTouchListener implements View.OnTouchListener {
        public boolean onTouch(View view, MotionEvent motionEvent) {
            Log.i(TAG, "onTouch");
            if (motionEvent.getAction() == MotionEvent.ACTION_DOWN) {
                ClipData data = ClipData.newPlainText("", "");
                View.DragShadowBuilder shadowBuilder = new View.DragShadowBuilder(view);
                view.startDrag(data, shadowBuilder, view, 0);
                view.setVisibility(View.INVISIBLE);
                return true;
            } else if (motionEvent.getAction() == MotionEvent.ACTION_UP) {
                view.setVisibility(View.VISIBLE);
                view.invalidate();
                return true;
            }
            return false;
        }
    }
}
