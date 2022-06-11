package jwtc.android.chess.activities;

import android.content.ClipData;
import android.content.SharedPreferences;
import android.media.AudioManager;
import android.media.SoundPool;
import android.speech.tts.TextToSpeech;
import android.util.Log;
import android.view.DragEvent;
import android.view.KeyEvent;
import android.view.MotionEvent;
import android.view.View;
import android.view.WindowManager;
import android.speech.tts.TextToSpeech.OnInitListener;

import java.util.Locale;

import jwtc.android.chess.constants.ColorSchemes;
import jwtc.android.chess.services.TextToSpeechApi;
import jwtc.android.chess.views.ChessBoardView;
import jwtc.android.chess.views.ChessPieceView;
import jwtc.android.chess.views.ChessSquareView;
import jwtc.android.chess.R;
import jwtc.android.chess.constants.PieceSets;
import jwtc.android.chess.services.GameApi;
import jwtc.android.chess.services.GameListener;
import jwtc.chess.JNI;
import jwtc.chess.Move;
import jwtc.chess.board.BoardConstants;
import jwtc.chess.board.ChessBoard;

abstract public class ChessBoardActivity extends BaseActivity implements GameListener, OnInitListener {
    private static final String TAG = "ChessBoardActivity";

    protected GameApi gameApi;
    protected MyDragListener myDragListener;
    protected MyTouchListener myTouchListener;
    protected MyClickListener myClickListener;
    protected JNI jni;
    protected ChessBoardView chessBoardView;
    protected SoundPool spSound = null;
    protected TextToSpeechApi textToSpeech = null;
    protected int lastPosition = -1;

    public static final int MODE_BLINDFOLD_SHOWPIECES = 0;
    public static final int MODE_BLINDFOLD_HIDEPIECES = 1;
    public static final int MODE_BLINDFOLD_SHOWPIECELOCATION = 2;

    protected int soundTickTock, soundCheck, soundMove, soundCapture;

    protected int modeBlindfold = MODE_BLINDFOLD_SHOWPIECES;
    protected boolean allowPremove = true;
    protected boolean skipReturn = true;
    private String keyboardBuffer = "";

    abstract public boolean requestMove(int from, int to);

    @Override
    public void OnMove(int move) {
        Log.d(TAG, "OnMove " + move);
        updateBoardWithMove(move);

        if (spSound != null) {
            if (Move.isCheck(move)) {
                spSound.play(soundCheck, fVolume, fVolume, 2, 0, 1);
            } else if (Move.isHIT(move)) {
                spSound.play(soundCapture, fVolume, fVolume, 1, 0, 1);
            } else {
                spSound.play(soundMove, fVolume, fVolume, 1, 0, 1);
            }
        }
        if (textToSpeech != null) {
            textToSpeech.moveToSpeech(jni.getMyMoveToString(), move);
        }
    }


    // @TODO spSound.play(soundTickTock, fVolume, fVolume, 1, 0, 1);

    @Override
    public void OnState() {
        Log.d(TAG, "OnState");
        rebuildBoard();
    }

    public void afterCreate() {
        Log.d(TAG, " afterCreate");

        jni = JNI.getInstance();
        chessBoardView = findViewById(R.id.includeboard);

        myDragListener = new MyDragListener();
        myTouchListener = new MyTouchListener();
        myClickListener = new MyClickListener();
//        LayoutTransition lt = new LayoutTransition();
//        lt.disableTransitionType(LayoutTransition.DISAPPEARING);
//        lt.setDuration(200);
//        chessBoardView.setLayoutTransition(lt);


        for (int i = 0; i < 64; i++) {
            ChessSquareView csv = new ChessSquareView(this, i);
            csv.setOnDragListener(myDragListener);
            csv.setOnClickListener(myClickListener);
            chessBoardView.addView(csv);
        }

        gameApi.addListener(this);
    }

    @Override
    protected void onResume() {
        super.onResume();

        SharedPreferences prefs = getPrefs();
        if (prefs.getBoolean("fullScreen", true)) {
            getWindow().setFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN, WindowManager.LayoutParams.FLAG_FULLSCREEN);
        }

        skipReturn = prefs.getBoolean("skipReturn", true);
        keyboardBuffer = "";

        try {
            PieceSets.selectedSet = Integer.parseInt(prefs.getString("pieceset", "0"));
            ColorSchemes.selectedColorScheme = Integer.parseInt(prefs.getString("colorscheme", "0"));

        } catch (NumberFormatException ex) {
            Log.e(TAG, ex.getMessage());
        }
        rebuildBoard();


        if (prefs.getBoolean("moveToSpeech", false)) {
            textToSpeech = new TextToSpeechApi(this, this);
        } else {
            textToSpeech = null;
        }

        if (prefs.getBoolean("moveSounds", false)) {
            spSound = new SoundPool(7, AudioManager.STREAM_MUSIC, 0);
            soundTickTock = spSound.load(this, R.raw.ticktock, 1);
            soundCheck = spSound.load(this, R.raw.smallneigh, 2);
            soundMove = spSound.load(this, R.raw.move, 1);
            soundCapture = spSound.load(this, R.raw.capture, 1);
        } else {
            spSound = null;
        }
    }

    public void rebuildBoard() {

        chessBoardView.removePieces();

        for (int i = 0; i < 64; i++) {
            int color = ChessBoard.BLACK;
            int piece = jni.pieceAt(color, i);
            if (piece == BoardConstants.FIELD) {
                color = ChessBoard.WHITE;
                piece = jni.pieceAt(color, i);
            }

            if (piece != BoardConstants.FIELD){
                ChessPieceView p = new ChessPieceView(this, color, piece, i);
                p.setOnTouchListener(myTouchListener);

                chessBoardView.addView(p);
            }
        }

        updateSelectedSquares();
    }

    public void updateBoardWithMove(int move) {
        final int from = Move.getFrom(move);
        final int to = Move.getTo(move);
        final boolean isOO = Move.isOO(move);
        final boolean isOOO = Move.isOOO(move);
        final boolean isHit = Move.isHIT(move);
        final boolean isPromo = Move.isPromotionMove(move);
        final int turnOfMove = jni.getTurn() == ChessBoard.BLACK ? ChessBoard.WHITE : ChessBoard.BLACK;
        final int count = chessBoardView.getChildCount();

        ChessPieceView rook = null;
        if (isOO || isOOO) {
            // find a rook that is not on it;s place
            for (int i = 0; i < count; i++) {
                final View child = chessBoardView.getChildAt(i);
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
                            chessBoardView.layoutChild(rook);
                            break;
                        }
                    }
                }
            }

        }

        if (isHit) {
            for (int i = 0; i < count; i++) {
                final View child = chessBoardView.getChildAt(i);

                if (child instanceof ChessPieceView) {
                    final int currentPos = ((ChessPieceView) child).getPos();
                    if (currentPos == to) {
                        chessBoardView.removeView(child);
                        break;
                    }
                }
            }
        }
        for (int i = 0; i < count; i++) {
            final View child = chessBoardView.getChildAt(i);

            if (child instanceof ChessPieceView) {
                final int currentPos = ((ChessPieceView) child).getPos();
                if (currentPos == from) {
                    ((ChessPieceView) child).setPos(to);
                    if (isPromo) {
                        ((ChessPieceView) child).promote(Move.getPromotionPiece(move));
                    }
                    chessBoardView.layoutChild(child);
                }
            }
        }
    }

    public void updateSelectedSquares() {
        final int count = chessBoardView.getChildCount();
        for (int i = 0; i < count; i++) {
            final View child = chessBoardView.getChildAt(i);

            if (child instanceof ChessSquareView) {
                //((ChessSquareView) child).setHighlighted(true);
            }
        }
    }


    @Override
    // bug report - dispatchKeyEvent is called before onKeyDown and some keys are overwritten in certain appcompat versions
    public boolean dispatchKeyEvent(KeyEvent event) {
        int keyCode = event.getKeyCode();
        int action = event.getAction();
        boolean isDown = action == 0;

        if (skipReturn && keyCode == KeyEvent.KEYCODE_ENTER) {  // skip enter key
            return true;
        }

        if (keyCode == KeyEvent.KEYCODE_MENU) {
            return isDown ? this.onKeyDown(keyCode, event) : this.onKeyUp(keyCode, event);
        }

        return super.dispatchKeyEvent(event);
    }

    @Override
    public boolean onKeyDown(int keyCode, KeyEvent event) {

        //View v = getWindow().getCurrentFocus();
        //Log.i("main", "current focus " + (v == null ? "NULL" : v.toString()));
        int c = (event.getUnicodeChar());
        Log.i(TAG, "onKeyDown " + keyCode + " = " + (char) c);
        if (keyCode == KeyEvent.KEYCODE_MENU) {
            //showMenu();
            return true;
        }

        // preference is to skip a carriage return
        if (skipReturn && (char) c == '\r') {
            return true;
        }

        if (c > 48 && c < 57 || c > 96 && c < 105) {
            keyboardBuffer += ("" + (char) c);
        }
        if (keyboardBuffer.length() >= 2) {
            Log.i(TAG, "handleClickFromPositionString " + keyboardBuffer);
            // @TODO
            //_chessView.handleClickFromPositionString(keyboardBuffer);
            keyboardBuffer = "";
        }
        return super.onKeyDown(keyCode, event);
    }

    @Override
    public void onInit(int status) {
        if (status == TextToSpeech.SUCCESS) {

            int result = textToSpeech.setLanguage(Locale.US);

            if (result == TextToSpeech.LANG_MISSING_DATA || result == TextToSpeech.LANG_NOT_SUPPORTED) {
                doToast("Speech does not support US locale");
                textToSpeech = null;
            } else {
                textToSpeech.setDefaults();
            }
        } else {
            doToast("Speech not supported");
            textToSpeech = null;
        }
    }


    protected ChessPieceView getPieceOnBoard(int pos, int color, int piece) {
        final int count = chessBoardView.getChildCount();
        for (int i = 0; i < count; i++) {
            final View child = chessBoardView.getChildAt(i);

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
        final int count = chessBoardView.getChildCount();
        for (int i = 0; i < count; i++) {
            final View child = chessBoardView.getChildAt(i);

            if (child instanceof ChessSquareView) {
                final ChessSquareView squareView = (ChessSquareView) child;
                if (squareView.getSelected()){
                    squareView.setSelected(squareView.getPos() == lastPosition);
                }
            }
        }
    }

    protected ChessSquareView getSquareAt(int pos) {
        final int count = chessBoardView.getChildCount();
        for (int i = 0; i < count; i++) {
            final View child = chessBoardView.getChildAt(i);

            if (child instanceof ChessSquareView) {
                final ChessSquareView squareView = (ChessSquareView)child;
                if (squareView.getPos() == pos) {
                    return squareView;
                }
            }
        }
        return null;
    }

    private final class MyClickListener implements View.OnClickListener {

        @Override
        public void onClick(View view) {
            if (view instanceof ChessSquareView) {
                final int toPos = ((ChessSquareView) view).getPos();
                if (lastPosition != -1) {
                    requestMove(lastPosition, toPos);
                }
                lastPosition = -1;
                ChessBoardActivity.this.resetSelectedSquares();
            }
        }
    }

    protected class MyDragListener implements View.OnDragListener {

        @Override
        public boolean onDrag(View view, DragEvent event) {
            int action = event.getAction();
            if (view instanceof ChessSquareView) {
                final int pos = ((ChessSquareView) view).getPos();

                switch (event.getAction()) {
                    case DragEvent.ACTION_DRAG_ENTERED:
                        Log.i(TAG, "onDrag ENTERED " + pos);
                        view.setSelected(true);
                        break;
                    case DragEvent.ACTION_DRAG_EXITED:
                        Log.i(TAG, "onDrag EXITED" + pos);
                        view.setSelected(false);
                        break;
                    case DragEvent.ACTION_DROP:
                        Log.i(TAG, "onDrag DROP " + pos);
                        // Dropped, reassign View to ViewGroup
                        View fromView = (View) event.getLocalState();
                        if (fromView instanceof ChessPieceView) {
                            final int toPos = ((ChessSquareView) view).getPos();
                            final int fromPos = ((ChessPieceView) fromView).getPos();

                            if (toPos == fromPos) {
                                // a click
                                Log.d(TAG, "click " + lastPosition);
                                if (lastPosition != -1) {
                                    requestMove(lastPosition, toPos);
                                    lastPosition = -1;
                                } else {
                                    lastPosition = toPos;
                                }
                            } else {
                                requestMove(fromPos, toPos);
                                lastPosition = -1;
                            }
                            ChessBoardActivity.this.resetSelectedSquares();
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

    protected class MyTouchListener implements View.OnTouchListener {
        public boolean onTouch(View view, MotionEvent motionEvent) {
            if (view instanceof ChessPieceView) {
                final int pos =  ((ChessPieceView) view).getPos();

                if (motionEvent.getAction() == MotionEvent.ACTION_DOWN) {
                    Log.i(TAG, "onTouch DOWN " + pos);

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
