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
import jwtc.android.chess.ChessSquareView;
import jwtc.android.chess.ImageCacheObject;
import jwtc.android.chess.MyBaseActivity;
import jwtc.android.chess.R;
import jwtc.android.chess.constants.PieceSets;
import jwtc.chess.JNI;
import jwtc.chess.Pos;
import jwtc.chess.board.BoardConstants;
import jwtc.chess.board.ChessBoard;

abstract public class ChessBoardActivity extends MyBaseActivity {

    protected MyDragListener myDragListener = new MyDragListener();
    protected MyTouchListener myTouchListener = new MyTouchListener();

    protected ChessBoardLayout chessBoardLayout;
    abstract public void handleClick(int index);

    public void afterCreate() {

        Log.d("ChessBoardActivity", " afterCreate");

        chessBoardLayout = findViewById(R.id.includeboard);

        for (int i = 0; i < 64; i++) {
            ChessSquareView csv = new ChessSquareView(this, i);
            csv.setOnDragListener(myDragListener);
            chessBoardLayout.addView(csv);
        }

        Log.d("ChessBoardActivity", " afterCreate done");

        // @TODO
        JNI.getInstance().newGame();

        updateBoard();
    }

    public void updateBoard() {
        ChessPieceView p = new ChessPieceView(this, PieceSets.ALPHA, BoardConstants.BLACK, BoardConstants.KING, BoardConstants.e4);


        /*
        for (i = 0; i < 64; i++) {

        iColor = ChessBoard.BLACK;
            iPiece = jni.pieceAt(iColor, i);

            if (iPiece == BoardConstants.FIELD) {
                iColor = ChessBoard.WHITE;
                iPiece = jni.pieceAt(iColor, i);

                if (iPiece == BoardConstants.FIELD)
                    bPiece = false;
            }

            }
         */

        p.setOnTouchListener(myTouchListener);

        chessBoardLayout.addView(p);

    }

    private final class MyDragListener implements View.OnDragListener {

        @Override
        public boolean onDrag(View v, DragEvent event) {
            int action = event.getAction();
            if (v instanceof ChessSquareView) {
                Log.i(TAG, "onDrag " + ((ChessSquareView) v).getPos());

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
                        if (view instanceof ChessPieceView) {
                            ((ChessPieceView) view).setPos(((ChessSquareView) v).getPos());
                            chessBoardLayout.layoutChild(view);
                            view.setVisibility(View.VISIBLE);
                        }
                        break;
                    case DragEvent.ACTION_DRAG_ENDED:
                        //v.setBackgroundDrawable(normalShape);
                    default:
                        break;
                }
                return true;
            }
            return false;
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
