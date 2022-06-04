package jwtc.android.chess.ui;

import android.animation.LayoutTransition;
import android.content.ClipData;
import android.util.Log;
import android.view.DragEvent;
import android.view.MotionEvent;
import android.view.View;
import jwtc.android.chess.ChessBoardLayout;
import jwtc.android.chess.ChessPieceView;
import jwtc.android.chess.ChessSquareView;
import jwtc.android.chess.MyBaseActivity;
import jwtc.android.chess.R;
import jwtc.android.chess.constants.PieceSets;
import jwtc.android.chess.controllers.GameApi;
import jwtc.chess.JNI;
import jwtc.chess.Move;
import jwtc.chess.board.BoardConstants;
import jwtc.chess.board.ChessBoard;

abstract public class ChessBoardActivity extends MyBaseActivity {

    protected GameApi gameApi;
    protected MyDragListener myDragListener = new MyDragListener();
    protected MyTouchListener myTouchListener = new MyTouchListener();
    protected JNI jni;
    protected ChessBoardLayout chessBoardLayout;
    protected int lastPosition = -1;


    public static final int MODE_BLINDFOLD_SHOWPIECES = 0;
    public static final int MODE_BLINDFOLD_HIDEPIECES = 1;
    public static final int MODE_BLINDFOLD_SHOWPIECELOCATION = 2;
    protected int modeBlindfold = MODE_BLINDFOLD_SHOWPIECES;
    protected boolean flippedBoard = false;

    abstract public boolean requestMove(int from, int to);

    public void afterCreate() {

        Log.d("ChessBoardActivity", " afterCreate");

        jni = JNI.getInstance();
        chessBoardLayout = findViewById(R.id.includeboard);

//        LayoutTransition lt = new LayoutTransition();
//        lt.disableTransitionType(LayoutTransition.DISAPPEARING);
//        lt.setDuration(200);
//        chessBoardLayout.setLayoutTransition(lt);


        for (int i = 0; i < 64; i++) {
            ChessSquareView csv = new ChessSquareView(this, i);
            csv.setOnDragListener(myDragListener);
            chessBoardLayout.addView(csv);
        }

        Log.d("ChessBoardActivity", " afterCreate done");

        rebuildBoard();
    }

    public void rebuildBoard() {

        chessBoardLayout.removePieces();

        for (int i = 0; i < 64; i++) {
            int color = ChessBoard.BLACK;
            int piece = jni.pieceAt(color, i);
            if (piece == BoardConstants.FIELD) {
                color = ChessBoard.WHITE;
                piece = jni.pieceAt(color, i);
            }

            if (piece != BoardConstants.FIELD){
                ChessPieceView p = new ChessPieceView(this, PieceSets.ALPHA, color, piece, i);
                p.setOnTouchListener(myTouchListener);

                chessBoardLayout.addView(p);
            }
        }
    }

    public void updateBoardWithMove(int move) {
        final int from = Move.getFrom(move);
        final int to = Move.getTo(move);
        final boolean isOO = Move.isOO(move);
        final boolean isOOO = Move.isOOO(move);
        final boolean isHit = Move.isHIT(move);
        final boolean isPromo = Move.isPromotionMove(move);
        final int turnOfMove = jni.getTurn() == ChessBoard.BLACK ? ChessBoard.WHITE : ChessBoard.BLACK;
        final int count = chessBoardLayout.getChildCount();

        ChessPieceView rook = null;
        if (isOO || isOOO) {
            // find a rook that is not on it;s place
            for (int i = 0; i < count; i++) {
                final View child = chessBoardLayout.getChildAt(i);
                if (child instanceof ChessPieceView) {
                    final ChessPieceView chessPieceView = (ChessPieceView) child;
                    if (chessPieceView.getPiece() == BoardConstants.ROOK && chessPieceView.getColor() == turnOfMove) {
                        final int pos = ((ChessPieceView) child).getPos();
                        final int piece = jni.pieceAt(turnOfMove, pos);
                        if (piece != BoardConstants.ROOK) {
                            rook = (ChessPieceView)child;
                        }
                    }
                }
            }
            if (rook != null) {
                for (int i = 0; i < 64; i++) {
                    final int piece = jni.pieceAt(turnOfMove, i);
                    if (piece == BoardConstants.ROOK) {
                        final ChessPieceView pieceView = getPieceOnBoard(i, turnOfMove, BoardConstants.ROOK);
                        if (pieceView == null){
                            rook.setPos(i);
                            chessBoardLayout.layoutChild(rook);
                            break;
                        }
                    }
                }
            }

        }

        if (isHit) {
            for (int i = 0; i < count; i++) {
                final View child = chessBoardLayout.getChildAt(i);

                if (child instanceof ChessPieceView) {
                    final int currentPos = ((ChessPieceView) child).getPos();
                    if (currentPos == to) {
                        chessBoardLayout.removeView(child);
                    }
                }
            }
        }
        for (int i = 0; i < count; i++) {
            final View child = chessBoardLayout.getChildAt(i);

            if (child instanceof ChessPieceView) {
                final int currentPos = ((ChessPieceView) child).getPos();
                if (currentPos == from) {
                    ((ChessPieceView) child).setPos(to);
                    if (isPromo) {
                        ((ChessPieceView) child).promote(Move.getPromotionPiece(move));
                    }
                    chessBoardLayout.layoutChild(child);
                }
            }
        }
    }

    protected ChessPieceView getPieceOnBoard(int pos, int color, int piece) {
        final int count = chessBoardLayout.getChildCount();
        for (int i = 0; i < count; i++) {
            final View child = chessBoardLayout.getChildAt(i);

            if (child instanceof ChessPieceView) {
                final ChessPieceView pieceView = (ChessPieceView)child;
                if (pieceView.getPiece() == piece && pieceView.getColor() == color && pieceView.getPos() == pos) {
                    return pieceView;
                }
            }
        }
        return null;
    }

    protected void resetSelectedSquares() {
        final int count = chessBoardLayout.getChildCount();
        for (int i = 0; i < count; i++) {
            final View child = chessBoardLayout.getChildAt(i);

            if (child instanceof ChessSquareView) {
                final ChessSquareView squareView = (ChessSquareView) child;
                if (squareView.getSelected()){
                    squareView.setSelected(false);
                }
            }
        }
    }

    protected ChessSquareView getSquareAt(int pos) {
        final int count = chessBoardLayout.getChildCount();
        for (int i = 0; i < count; i++) {
            final View child = chessBoardLayout.getChildAt(i);

            if (child instanceof ChessSquareView) {
                final ChessSquareView squareView = (ChessSquareView)child;
                if (squareView.getPos() == pos) {
                    return squareView;
                }
            }
        }
        return null;
    }

    private final class MyDragListener implements View.OnDragListener {

        @Override
        public boolean onDrag(View view, DragEvent event) {
            int action = event.getAction();
            if (view instanceof ChessSquareView) {
                final int pos = ((ChessSquareView) view).getPos();

                switch (event.getAction()) {
                    case DragEvent.ACTION_DRAG_ENTERED:
                        Log.i(TAG, "onDrag ENTERED " + pos);
                        view.setSelected(true);
//                    v.setBackgroundDrawable(enterShape);
                        break;
                    case DragEvent.ACTION_DRAG_EXITED:
                        Log.i(TAG, "onDrag EXITED" + pos);
                        view.setSelected(false);
//                    v.setBackgroundDrawable(normalShape);
                        break;
                    case DragEvent.ACTION_DROP:
                        Log.i(TAG, "onDrag DROP " + pos);
                        // Dropped, reassign View to ViewGroup
                        View fromView = (View) event.getLocalState();
                        if (fromView instanceof ChessPieceView) {
                            final int toPos = ((ChessSquareView) view).getPos();
                            final int fromPos = ((ChessPieceView) fromView).getPos();

                            if (lastPosition == toPos) {
                                lastPosition = -1;
                            } else {
                                if (requestMove(fromPos, toPos)) {
                                    ChessBoardActivity.this.updateBoardWithMove(jni.getMyMove());
                                } else {
                                    view.setSelected(false);
                                }
                            }
                            fromView.setVisibility(View.VISIBLE);
                        }

                        break;
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
            if (view instanceof ChessPieceView) {
                final int pos =  ((ChessPieceView) view).getPos();

                if (motionEvent.getAction() == MotionEvent.ACTION_DOWN) {
                    Log.i(TAG, "onTouch DOWN " + pos);

                    ChessBoardActivity.this.resetSelectedSquares();
                    lastPosition = pos;

                    ClipData data = ClipData.newPlainText("", "");
                    View.DragShadowBuilder shadowBuilder = new View.DragShadowBuilder(view);
                    view.startDrag(data, shadowBuilder, view, 0);
                    view.setVisibility(View.INVISIBLE);
                    return true;
                } else if (motionEvent.getAction() == MotionEvent.ACTION_UP) {
                    Log.i(TAG, "onTouch UP " + pos);

                    view.setVisibility(View.VISIBLE);
                    view.invalidate();
                    return true;
                }
            }
            return false;
        }
    }
}
