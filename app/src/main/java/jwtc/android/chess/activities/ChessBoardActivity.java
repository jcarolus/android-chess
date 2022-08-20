package jwtc.android.chess.activities;

import android.app.AlertDialog;
import android.content.ClipData;
import android.content.ContentValues;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.res.AssetManager;
import android.graphics.BitmapFactory;
import android.media.AudioManager;
import android.media.SoundPool;
import android.net.Uri;
import android.os.Environment;
import android.speech.tts.TextToSpeech;
import android.util.Log;
import android.view.DragEvent;
import android.view.KeyEvent;
import android.view.MotionEvent;
import android.view.View;
import android.view.WindowManager;
import android.speech.tts.TextToSpeech.OnInitListener;

import java.io.FileOutputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Locale;

import jwtc.android.chess.constants.ColorSchemes;
import jwtc.android.chess.helpers.MyPGNProvider;
import jwtc.android.chess.services.TextToSpeechApi;
import jwtc.android.chess.views.ChessBoardView;
import jwtc.android.chess.views.ChessPieceLabelView;
import jwtc.android.chess.views.ChessPieceView;
import jwtc.android.chess.views.ChessSquareView;
import jwtc.android.chess.R;
import jwtc.android.chess.constants.PieceSets;
import jwtc.android.chess.services.GameApi;
import jwtc.android.chess.services.GameListener;
import jwtc.chess.JNI;
import jwtc.chess.Move;
import jwtc.chess.PGNColumns;
import jwtc.chess.Pos;
import jwtc.chess.board.BoardConstants;
import jwtc.chess.board.BoardMembers;
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
    protected int lastPosition = -1, premoveFrom = -1, premoveTo = -1, _dpadPos = -1;
    protected ArrayList<Integer> highlightedPositions = new ArrayList<Integer>();
    protected int soundTickTock, soundCheck, soundMove, soundCapture, soundNewGame;
    protected boolean skipReturn = true;
    private String keyboardBuffer = "";

    public boolean requestMove(final int from, final int to) {
        if (jni.pieceAt(BoardConstants.WHITE, from) == BoardConstants.PAWN &&
                BoardMembers.ROW_TURN[BoardConstants.WHITE][from] == 6 &&
                BoardMembers.ROW_TURN[BoardConstants.WHITE][to] == 7
                ||
                jni.pieceAt(BoardConstants.BLACK, from) == BoardConstants.PAWN &&
                        BoardMembers.ROW_TURN[BoardConstants.BLACK][from] == 6 &&
                        BoardMembers.ROW_TURN[BoardConstants.BLACK][to] == 7) {

            final String[] items = getResources().getStringArray(R.array.promotionpieces);

            AlertDialog.Builder builder = new AlertDialog.Builder(this);
            builder.setTitle(R.string.title_pick_promo);
            builder.setSingleChoiceItems(items, 0, new DialogInterface.OnClickListener() {
                public void onClick(DialogInterface dialog, int item) {
                    dialog.dismiss();
                    jni.setPromo(4 - item);
                    if (!gameApi.requestMove(from, to)) {
                        rebuildBoard();
                    }
                }
            });
            AlertDialog alert = builder.create();
            alert.show();

//            if (_vibrator != null) {
//                _vibrator.vibrate(40L);
//            }

            return true;
        } else if (jni.isAmbiguousCastle(from, to) != 0) { // in case of Fischer

            AlertDialog.Builder builder = new AlertDialog.Builder(this);
            builder.setTitle(R.string.title_castle);
            builder.setPositiveButton(R.string.alert_yes, new DialogInterface.OnClickListener() {
                public void onClick(DialogInterface dialog, int item) {
                    dialog.dismiss();
                    gameApi.requestMoveCastle(from, to);
                }
            });
            builder.setNegativeButton(R.string.alert_no, new DialogInterface.OnClickListener() {
                public void onClick(DialogInterface dialog, int item) {
                    dialog.dismiss();
                    if (from != to) {
                        gameApi.requestMove(from, to);
                    }
                }
            });
            AlertDialog alert = builder.create();
            alert.show();

//            if (_vibrator != null) {
//                _vibrator.vibrate(40L);
//            }

            return true; // done, return from method!
        }
        return gameApi.requestMove(from, to);
    }

    @Override
    public void OnMove(int move) {
        Log.d(TAG, "OnMove " + move);
        lastPosition = -1;

        rebuildBoard();

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



    @Override
    public void OnState() {
        rebuildBoard();
    }

    public void afterCreate() {
        Log.d(TAG, " afterCreate");

        jni = JNI.getInstance();
        chessBoardView = findViewById(R.id.includeboard);
        chessBoardView.setOnFocusChangeListener(new View.OnFocusChangeListener() {
            @Override
            public void onFocusChange(View v, boolean hasFocus) {
                dpadFocus(hasFocus);
            }
        });

        chessBoardView.setOnKeyListener(new View.OnKeyListener() {
            @Override
            public boolean onKey(View v, int keyCode, KeyEvent event) {
                if (event.getAction() == KeyEvent.ACTION_DOWN) {
                    switch (keyCode) {
                        case KeyEvent.KEYCODE_DPAD_CENTER:
                            return dpadSelect();
                        case KeyEvent.KEYCODE_DPAD_DOWN:
                            return dpadDown();
                        case KeyEvent.KEYCODE_DPAD_LEFT:
                            return dpadLeft();
                        case KeyEvent.KEYCODE_DPAD_RIGHT:
                            return dpadRight();
                        case KeyEvent.KEYCODE_DPAD_UP:
                            return dpadUp();
                    }
                }
                return false;
            }
        });

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

        String sPat = prefs.getString("tileSet", "");
        if (sPat.length() > 0) {
            AssetManager assetManager = getAssets();
            try {
                ChessSquareView.bitmapPattern = BitmapFactory.decodeStream(assetManager.open("tiles/" + sPat + ".png"));
            } catch (IOException ex) {
                ChessSquareView.bitmapPattern = null;
            }
        } else {
            ChessSquareView.bitmapPattern = null;
        }
        ColorSchemes.showCoords = prefs.getBoolean("showCoords", false);

        skipReturn = prefs.getBoolean("skipReturn", true);
        keyboardBuffer = "";

        try {
            PieceSets.selectedSet = Integer.parseInt(prefs.getString("pieceset", "0"));
            ColorSchemes.selectedColorScheme = Integer.parseInt(prefs.getString("colorscheme", "0"));

        } catch (NumberFormatException ex) {
            Log.e(TAG, ex.getMessage());
        }

        PieceSets.selectedBlindfoldMode = PieceSets.BLINDFOLD_SHOW_PIECES;

        if (prefs.getBoolean("moveToSpeech", false)) {
            textToSpeech = new TextToSpeechApi(this, this);
        } else {
            textToSpeech = null;
        }

        fVolume = prefs.getBoolean("moveSounds", false) ? 1.0f : 0.0f;

        spSound = new SoundPool(7, AudioManager.STREAM_MUSIC, 0);
        soundTickTock = spSound.load(this, R.raw.ticktock, 1);
        soundCheck = spSound.load(this, R.raw.smallneigh, 2);
        soundMove = spSound.load(this, R.raw.move, 1);
        soundCapture = spSound.load(this, R.raw.capture, 1);
        soundNewGame = spSound.load(this, R.raw.chesspiecesfall, 1);
    }

    @Override
    protected void onPause() {
        Log.i(TAG, "onPause");

        SharedPreferences.Editor editor = this.getPrefs().edit();
        editor.putBoolean("moveSounds", fVolume == 1.0f);

        editor.commit();

        super.onPause();
    }

    public void rebuildBoard() {

        chessBoardView.removePieces();
        chessBoardView.removeLabels();

        final int state = jni.getState();
        final int turn = jni.getTurn();

        // ⚑ ✓ ½
        String labelForWhiteKing = null;
        String labelForBlackKing = null;
        switch (state) {
            case ChessBoard.MATE:
                labelForWhiteKing = turn == BoardConstants.BLACK ? "✓" : "#";
                labelForBlackKing = turn == BoardConstants.WHITE ? "✓" : "#";
                break;
            case ChessBoard.DRAW_MATERIAL:
            case ChessBoard.DRAW_REPEAT:
            case ChessBoard.DRAW_AGREEMENT:
            case ChessBoard.STALEMATE:
                labelForWhiteKing = "½";
                labelForBlackKing = "½";
                break;
            case ChessBoard.DRAW_50:
                labelForWhiteKing = "50";
                labelForBlackKing = "50";
                break;
            case ChessBoard.CHECK:
                if (turn == ChessBoard.WHITE) {
                    labelForWhiteKing = "+";
                } else {
                    labelForBlackKing = "+";
                }
                break;
        }

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

                if (piece == BoardConstants.KING) {
                    if (color == BoardConstants.WHITE && labelForWhiteKing != null) {
                        ChessPieceLabelView labelView = new ChessPieceLabelView(this, i, color, labelForWhiteKing);
                        chessBoardView.addView(labelView);
                    } else if (color == BoardConstants.BLACK && labelForBlackKing != null) {
                        ChessPieceLabelView labelView = new ChessPieceLabelView(this, i, color, labelForBlackKing);
                        chessBoardView.addView(labelView);
                    }
                }
            }
        }

        updateSelectedSquares();
    }

    public void updateSelectedSquares() {
        final int count = chessBoardView.getChildCount();
        for (int i = 0; i < count; i++) {
            final View child = chessBoardView.getChildAt(i);

            if (child instanceof ChessSquareView) {
                final ChessSquareView squareView = (ChessSquareView) child;

                squareView.setSelected(squareView.getPos() == lastPosition || squareView.getPos() == _dpadPos);
                squareView.setHighlighted(highlightedPositions.contains(i));
            }
        }
    }

    public void resetSelectedSquares() {
        lastPosition = -1;
        premoveFrom = -1;
        premoveTo = -1;

        highlightedPositions.clear();
        updateSelectedSquares();
    }

//    @Override
//    // bug report - dispatchKeyEvent is called before onKeyDown and some keys are overwritten in certain appcompat versions
//    public boolean dispatchKeyEvent(KeyEvent event) {
//        int keyCode = event.getKeyCode();
//        int action = event.getAction();
//        boolean isDown = action == 0;
//
//        if (skipReturn && keyCode == KeyEvent.KEYCODE_ENTER) {  // skip enter key
//            return true;
//        }
//
//        if (keyCode == KeyEvent.KEYCODE_MENU) {
//            return isDown ? this.onKeyDown(keyCode, event) : this.onKeyUp(keyCode, event);
//        }
//
//        return super.dispatchKeyEvent(event);
//    }

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
            /*
              int index = Pos.fromString(keyboardBuffer);

                return handleClick(index);
             */
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

    protected void setPremove(int from, int to) {
        Log.d(TAG, "setPremove");
        this.premoveFrom = from;
        this.premoveTo = to;
        highlightedPositions.clear();
        highlightedPositions.add(from);
        highlightedPositions.add(to);
    }

    protected void resetPremove() {
        Log.d(TAG, "resetPremove");
        this.premoveFrom = -1;
        this.premoveTo = -1;
        highlightedPositions.clear();

        rebuildBoard();
        updateSelectedSquares();
    }

    protected boolean hasPremoved() {
        return premoveFrom != -1;
    }

    protected ChessPieceView getPieceViewOnPosition(int pos) {
        final int count = chessBoardView.getChildCount();
        for (int i = 0; i < count; i++) {
            final View child = chessBoardView.getChildAt(i);

            if (child instanceof ChessPieceView) {
                final ChessPieceView pieceView = (ChessPieceView)child;
                if (pieceView.getPos() == pos) {
                    return pieceView;
                }
            }
        }
        return null;
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
                if (hasPremoved()) {
                    resetPremove();
                } else {
                    final int toPos = ((ChessSquareView) view).getPos();
                    if (lastPosition != -1) {
                        handleClick(toPos);
                    }
                }
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
//                        Log.i(TAG, "onDrag ENTERED " + pos);
                        view.setSelected(true);
                        break;
                    case DragEvent.ACTION_DRAG_EXITED:
//                        Log.i(TAG, "onDrag EXITED" + pos);
                        view.setSelected(false);
                        break;
                    case DragEvent.ACTION_DROP: {
//                        Log.i(TAG, "onDrag DROP " + pos);
                        // Dropped, reassign View to ViewGroup
                        View fromView = (View) event.getLocalState();
                        if (fromView != null) {
                            if (fromView instanceof ChessPieceView) {
                                ChessPieceView pieceViewFrom = (ChessPieceView) fromView;
                                final int toPos = ((ChessSquareView) view).getPos();
                                final int fromPos = pieceViewFrom.getPos();

                                if (toPos == fromPos) {
                                    // a click
                                    if (lastPosition != -1) {
                                        handleClick(toPos);
                                    } else {
                                        lastPosition = toPos;
                                    }
                                } else {
                                    pieceViewFrom.setPos(toPos);
                                    chessBoardView.layoutChild(pieceViewFrom);
                                    requestMove(fromPos, toPos);
                                    lastPosition = -1;
                                }
                                ChessBoardActivity.this.updateSelectedSquares();
                            }
                            fromView.setVisibility(View.VISIBLE);
                        }

                        break;
                    }
                    case DragEvent.ACTION_DRAG_ENDED: {
                        final View droppedView = (View) event.getLocalState();
                        if (droppedView != null && droppedView.getVisibility() != View.VISIBLE) {
                            droppedView.post(new Runnable(){
                                @Override
                                public void run() {
                                    droppedView.setVisibility(View.VISIBLE);
                                }
                            });
                        }
                        break;
                    }
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
            if (hasPremoved()) {
                resetPremove();
            } else if (view instanceof ChessPieceView) {
                if (motionEvent.getAction() == MotionEvent.ACTION_DOWN) {
//                    Log.i(TAG, "onTouch DOWN " + pos);

                    ClipData data = ClipData.newPlainText("", "");
                    View.DragShadowBuilder shadowBuilder = new View.DragShadowBuilder(view);
                    view.startDrag(data, shadowBuilder, view, 0);
                    view.setVisibility(View.INVISIBLE);
                    return true;
                } else if (motionEvent.getAction() == MotionEvent.ACTION_UP) {
//                    Log.i(TAG, "onTouch UP");

                    view.setVisibility(View.VISIBLE);
                    view.invalidate();
                    return true;
                }
            }
            return false;
        }
    }

    public static int chessStateToR(int s) {
        switch (s) {
            case ChessBoard.MATE:
                return R.string.state_mate;
            case ChessBoard.DRAW_MATERIAL:
                return R.string.state_draw_material;
            case ChessBoard.CHECK:
                return R.string.state_check;
            case ChessBoard.STALEMATE:
                return R.string.state_draw_stalemate;
            case ChessBoard.DRAW_50:
                return R.string.state_draw_50;
            case ChessBoard.DRAW_REPEAT:
                return R.string.state_draw_repeat;
            case ChessBoard.BLACK_FORFEIT_TIME:
                return R.string.state_black_forfeits_time;
            case ChessBoard.WHITE_FORFEIT_TIME:
                return R.string.state_white_forfeits_time;
            case ChessBoard.BLACK_RESIGNED:
                return R.string.state_black_resigned;
            case ChessBoard.WHITE_RESIGNED:
                return R.string.state_white_resigned;
            case ChessBoard.DRAW_AGREEMENT:
                return R.string.state_draw_agreement;
            default:
                return R.string.state_play;
        }
    }

    protected void dpadFocus(boolean hasFocus) {
        if (hasFocus) {
            _dpadPos = jni.getTurn() == ChessBoard.BLACK ? ChessBoard.e8 : ChessBoard.e1;
            updateSelectedSquares();
        } else {
            _dpadPos = -1;
            updateSelectedSquares();
        }
        Log.d(TAG, "dpad focus " + hasFocus + ", " + _dpadPos);
    }

    public boolean dpadUp() {
        if (_dpadPos == -1) {
            return false;
        }
        if (_dpadPos >= 8) {
            _dpadPos -= 8;
            updateSelectedSquares();
            return true;
        }
        return false;
    }

    public boolean dpadDown() {
        if (_dpadPos == -1) {
            return false;
        }
        if (_dpadPos < 55) {
            _dpadPos += 8;
            updateSelectedSquares();
            return true;
        }
        return false;
    }

    public boolean dpadLeft() {
        if (_dpadPos == -1) {
            return false;
        }
        if (Pos.col(_dpadPos) > 0) {
            _dpadPos--;
            updateSelectedSquares();
            return true;
        }
        return false;
    }

    public boolean dpadRight() {
        if (_dpadPos == -1) {
            return false;
        }

        if (Pos.col(_dpadPos) < 7) {
            _dpadPos++;
            updateSelectedSquares();
            return true;
        }
        return false;
    }

    public boolean dpadSelect() {
        if (_dpadPos != -1) {
            if (lastPosition == -1) {
                lastPosition = _dpadPos;
                updateSelectedSquares();
            } else {
                handleClick(_dpadPos);
            }
            return true;
        }
        return false;
    }

    protected void handleClick(int index) {
        Log.d(TAG, "handleClick " + index);
        ChessPieceView pieceViewFrom = getPieceViewOnPosition(lastPosition);
        if (pieceViewFrom != null) {
            pieceViewFrom.setPos(index);
            chessBoardView.layoutChild(pieceViewFrom);
        }
        requestMove(lastPosition, index);
        lastPosition = -1;
        ChessBoardActivity.this.updateSelectedSquares();
    }
}
