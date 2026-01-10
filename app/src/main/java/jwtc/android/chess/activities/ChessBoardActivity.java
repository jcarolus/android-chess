package jwtc.android.chess.activities;

import android.app.AlertDialog;
import android.content.ClipData;
import android.content.DialogInterface;
import android.content.SharedPreferences;
import android.graphics.Rect;
import android.media.AudioManager;
import android.media.SoundPool;
import android.speech.tts.TextToSpeech;
import android.util.Log;
import android.view.DragEvent;
import android.view.KeyEvent;
import android.view.MotionEvent;
import android.view.View;
import android.speech.tts.TextToSpeech.OnInitListener;

import java.util.ArrayList;
import java.util.Locale;
import java.util.stream.Collectors;

import jwtc.android.chess.constants.ColorSchemes;
import jwtc.android.chess.helpers.MagnifyingDragShadowBuilder;
import jwtc.android.chess.helpers.Sounds;
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
import jwtc.android.chess.constants.Piece;
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
    protected ChessSquareView currentChessSquareView = null;
    protected ChessPieceView currentChessPieceView = null;
    protected Sounds sounds = null;

    protected TextToSpeechApi textToSpeech = null;
    protected int selectedPosition = -1, premoveFrom = -1, premoveTo = -1, dpadPos = -1, lastMoveFrom = -1, lastMoveTo = -1;
    protected ArrayList<Integer> highlightedPositions = new ArrayList<Integer>();
    protected ArrayList<Integer> moveToPositions = new ArrayList<Integer>();

    protected boolean skipReturn = true, showMoves = false, flipBoard = false, isBackGestureBlocked = false, moveToSpeech = false;
    private String keyboardBuffer = "";

    public boolean requestMove(final int from, final int to) {
        if (jni.getDuckPos() == from) {
            return gameApi.requestDuckMove(to);
        } else if (gameApi.isPromotionMove(from, to)) {

            final String[] items = getResources().getStringArray(R.array.promotionpieces);

            AlertDialog.Builder builder = new AlertDialog.Builder(this);
            builder.setTitle(R.string.title_pick_promo);
            builder.setCancelable(false);
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

            handleAmbiguousCastle(from, to);

            return true; // done, return from method!
        }
        return gameApi.requestMove(from, to);
    }

    @Override
    public void OnMove(int move) {
        Log.d(TAG, "OnMove " + Move.toDbgString(move));
        // selectPosition(-1);

        moveToPositions.clear();
        highlightedPositions.clear();
        lastMoveFrom = Move.getFrom(move);
        lastMoveTo = Move.getTo(move);

        rebuildBoard();

        if (sounds != null) {
            if (Move.isCheck(move)) {
                sounds.playCheck();
            } else if (Move.isHIT(move)) {
                sounds.playCapture();
            } else {
                sounds.playMove();
            }
        }

        String sMove = getLastMoveAndTurnDescription();
        Log.d(TAG, "last move " + sMove);
        if (isScreenReaderOn()) {
            //doToastShort(sMove);
        } else if (textToSpeech != null && moveToSpeech) {
            textToSpeech.moveToSpeech(sMove);
        }
    }

    @Override
    public void OnDuckMove(int duckMove) {
        Log.d(TAG, "OnDuckMove " + Pos.toString(duckMove));
        selectPosition(-1);

        rebuildBoard();
    }


    @Override
    public void OnState() {
        this.moveToPositions.clear();

        Log.d(TAG, "OnState");
        rebuildBoard();
    }

    public void afterCreate() {
        Log.d(TAG, " afterCreate");

        jni = JNI.getInstance();
        chessBoardView = findViewById(R.id.includeboard);
        chessBoardView.setNextFocusRightId(R.id.ButtonPlay);

        initDirectionalPad();

        myDragListener = new MyDragListener();
        myTouchListener = new MyTouchListener();
        myClickListener = new MyClickListener();

        for (int i = 0; i < 64; i++) {
            ChessSquareView csv = new ChessSquareView(this, i);
            csv.setOnDragListener(myDragListener);
            csv.setOnTouchListener(myTouchListener);
            csv.setOnClickListener(myClickListener);
            chessBoardView.addView(csv);
        }

        gameApi.addListener(this);
    }

    @Override
    protected void onResume() {
        super.onResume();

        SharedPreferences prefs = getPrefs();

        ColorSchemes.showCoords = prefs.getBoolean("showCoords", false);
        ColorSchemes.saturationFactor = prefs.getFloat("squareSaturation", 1.0f);

        skipReturn = prefs.getBoolean("skipReturn", true);
        keyboardBuffer = "";

        try {
            PieceSets.selectedSet = Integer.parseInt(prefs.getString("pieceset", "0"));
            ColorSchemes.selectedColorScheme = Integer.parseInt(prefs.getString("colorscheme", "0"));
            ColorSchemes.selectedPattern = Integer.parseInt(prefs.getString("squarePattern", "0"));

        } catch (NumberFormatException ex) {
            Log.e(TAG, ex.getMessage());
        }

        PieceSets.selectedBlindfoldMode = PieceSets.BLINDFOLD_SHOW_PIECES;

        moveToSpeech = prefs.getBoolean("moveToSpeech", false);
        textToSpeech = new TextToSpeechApi(this, this);

        showMoves = prefs.getBoolean("showMoves", false);

        sounds = new Sounds(this);
        sounds.initPrefs(prefs);
    }

    @Override
    protected void onPause() {
        Log.d(TAG, "onPause");
        super.onPause();
    }

    @Override
    protected void onDestroy() {
        Log.d(TAG, "onDestroy");

        gameApi.removeListener(this);
        super.onDestroy();
    }

    @Override
    public boolean dispatchTouchEvent(MotionEvent ev) {
        int action = ev.getActionMasked();

        switch (action) {
            case MotionEvent.ACTION_DOWN:
                isBackGestureBlocked = chessBoardView.findPieceViewAt(ev.getX() - chessBoardView.getX(), ev.getY() - chessBoardView.getY()) != null;
                break;
        }
        return super.dispatchTouchEvent(ev);
    }

    public void rebuildBoard() {

        chessBoardView.removePieces();
        chessBoardView.removeLabels();

        final int state = gameApi.getState();
        final int turn = jni.getTurn();
        final int duckPos = jni.getDuckPos();

        Log.d(TAG, "state " + state);

        // ⚑ ✓ ½
        String labelForWhiteKing = null;
        String labelForBlackKing = null;
        switch (state) {
            case ChessBoard.MATE:
                labelForWhiteKing = turn == BoardConstants.BLACK ? "✓" : "#";
                labelForBlackKing = turn == BoardConstants.WHITE ? "✓" : "#";
                break;
            case BoardConstants.BLACK_RESIGNED:
            case BoardConstants.BLACK_FORFEIT_TIME:
                labelForWhiteKing = "✓";
                labelForBlackKing = "⚑";
                break;
            case BoardConstants.WHITE_RESIGNED:
            case BoardConstants.WHITE_FORFEIT_TIME:
                labelForWhiteKing = "⚑";
                labelForBlackKing = "✓";
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
            int color = turn == ChessBoard.BLACK ? ChessBoard.WHITE : ChessBoard.BLACK;
            int piece = i == duckPos ? BoardConstants.DUCK : jni.pieceAt(color, i);
            if (piece == BoardConstants.FIELD) {
                color = turn;
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
                final int pos = squareView.getPos();
                squareView.setSelected(pos == selectedPosition);
                squareView.setFocussed(pos == dpadPos);
                squareView.setHighlighted(pos == lastMoveFrom || pos == lastMoveTo || highlightedPositions.contains(pos));
                squareView.setMove(moveToPositions.contains(i));
                int piece = jni.pieceAt(jni.getTurn() == BoardConstants.WHITE ? BoardConstants.BLACK : BoardConstants.WHITE, pos);
                squareView.setBelowPiece(piece != BoardConstants.FIELD);
                String nextDescription = getFieldDescription(pos);
                CharSequence currentDescription = squareView.getContentDescription();
                if (currentDescription == null || !nextDescription.contentEquals(currentDescription)) {
                    squareView.setContentDescription(nextDescription);
                }
            }
        }
    }

    public void resetSelectedSquares() {
        Log.d(TAG, "resetSelectedSquares");
        selectPosition(-1);
        premoveFrom = -1;
        premoveTo = -1;
        lastMoveTo = -1;
        lastMoveFrom = -1;

        highlightedPositions.clear();
        moveToPositions.clear();
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
        int c = (event.getUnicodeChar());
        Log.i(TAG, "onKeyDown " + keyCode + " = " + (char) c);
        if (keyCode == KeyEvent.KEYCODE_MENU) {
            //showMenu();
            return true;
        }

        if (keyCode == KeyEvent.KEYCODE_BACK) {
            if (!isBackGestureBlocked) {
                showExitConfirmationDialog();
            }
            isBackGestureBlocked = false;
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

                return handleMove(index);
             */
            keyboardBuffer = "";
        }

        return super.onKeyDown(keyCode, event);
    }

    @Override
    public void onInit(int status) {
        if (status == TextToSpeech.SUCCESS && textToSpeech != null) {
            int result = textToSpeech.setDefaults(getPrefs());

            if (result == TextToSpeech.LANG_MISSING_DATA || result == TextToSpeech.LANG_NOT_SUPPORTED) {
                doToast("Speech does not support US locale");
                textToSpeech = null;
            } else {

            }
        } else {
            doToast("Speech not supported");
            textToSpeech = null;
        }
    }

    protected void initDirectionalPad() {
        chessBoardView.setOnFocusChangeListener(new View.OnFocusChangeListener() {
            @Override
            public void onFocusChange(View v, boolean hasFocus) {
                Log.d(TAG, "onFocusChange " + hasFocus);
                dpadFocus(hasFocus);
            }
        });

        chessBoardView.setOnKeyListener(new View.OnKeyListener() {
            @Override
            public boolean onKey(View v, int keyCode, KeyEvent event) {
                if (event.getAction() == KeyEvent.ACTION_DOWN) {
                    Log.d(TAG, "onKey " + keyCode);
                    switch (keyCode) {
                        case KeyEvent.KEYCODE_DPAD_CENTER:
                        case KeyEvent.KEYCODE_ENTER:
                            return dpadSelect();
                        case KeyEvent.KEYCODE_DPAD_DOWN:
                            return chessBoardView.isRotated() ? dpadUp() : dpadDown();
                        case KeyEvent.KEYCODE_DPAD_LEFT:
                            return chessBoardView.isRotated() ? dpadRight() : dpadLeft();
                        case KeyEvent.KEYCODE_DPAD_RIGHT:
                            return chessBoardView.isRotated() ? dpadLeft() : dpadRight();
                        case KeyEvent.KEYCODE_DPAD_UP:
                            return chessBoardView.isRotated() ? dpadDown() : dpadUp();
                    }
                }
                return false;
            }
        });
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

    protected void setMoveToPositions(int from) {
        Log.d(TAG, "setMoveToPositions " + from);
        moveToPositions.clear();
        if (showMoves) {
            int size = jni.getMoveArraySize();
            int move;
            for (int i = 0; i < size; i++) {
                move = jni.getMoveArrayAt(i);
                if (Move.getFrom(move) == from) {
                    moveToPositions.add(Move.getTo(move));
                }
            }
        }
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

    protected String getFieldDescription(int pos) {
        if (PieceSets.selectedBlindfoldMode != PieceSets.BLINDFOLD_HIDE_PIECES) {
            int whitePiece = jni.pieceAt(BoardConstants.WHITE, pos);
            int blackPiece = jni.pieceAt(BoardConstants.BLACK, pos);
            int duckPos = jni.getDuckPos();
            if (whitePiece != BoardConstants.FIELD) {
                return getString(R.string.square_with_piece_description, getString(R.string.piece_white), getString(Piece.toResource(whitePiece)), Pos.toString(pos));
            } else if (blackPiece != BoardConstants.FIELD) {
                return getString(R.string.square_with_piece_description, getString(R.string.piece_black), getString(Piece.toResource(blackPiece)), Pos.toString(pos));
            } else if (duckPos != -1) {
                return getString(R.string.square_with_duck_description, getString(Piece.toResource(BoardConstants.DUCK)), Pos.toString(pos));
            }
        }
        return Pos.toString(pos);
    }

    protected String getLastMoveAndTurnDescription() {
        int move = jni.getMyMove();
        if (move != 0) {
            String sMove = gameApi.moveToSpeechString(jni.getMyMoveToString(), move);
            if (gameApi.isEnded()) {
                return sMove;
            }
            return jni.getTurn() == BoardConstants.BLACK
                    ? getString(R.string.last_white_move_description, sMove)
                    : getString(R.string.last_black_move_description, sMove);
        }
        return "";
    }

    protected void showAccessibilityForSelectedPosition(int pos) {
        if (isScreenReaderOn()) {
            doToastShort(getString(R.string.square_selected_description, Pos.toString(pos)));
        }
    }

    protected String getPiecesDescription(final int turn) {
        String ret = "";
        ArrayList<Integer> positions = new ArrayList<Integer>();
        for (int i = 0; i < 6; i++) {
            positions.clear();
            for (int pos = 0; pos < 64; pos++) {
                int piece = jni.pieceAt(turn, pos);
                if (piece == i) {
                    positions.add(pos);
                }
            }
            if (positions.size() > 0) {
                ret += Piece.toString(i)
                        + " " + positions
                        .stream()
                        .map(Pos::toString)
                        .collect(Collectors.joining(", "))
                + ". ";
            }
        }
        return getString(turn == BoardConstants.WHITE ? R.string.piece_white : R.string.piece_black) + ": " + ret;
    }

    protected class MyClickListener implements View.OnClickListener {

        @Override
        public void onClick(View view) {
            if (view instanceof ChessSquareView) {
                Log.d(TAG, "onClick");
                if (hasPremoved()) {
                    resetPremove();
                } else {
                    final int toPos = ((ChessSquareView) view).getPos();
                    selectPosition(toPos);
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
                        view.setSelected(true);
                        break;
                    case DragEvent.ACTION_DRAG_EXITED:
                        view.setSelected(false);
                        break;
                    case DragEvent.ACTION_DRAG_STARTED:
                       // all listeners allow drag started
                       break;
                    case DragEvent.ACTION_DRAG_LOCATION:
                        break;
                    case DragEvent.ACTION_DROP: {
                        View fromView = (View) event.getLocalState();
                        if (fromView != null) {
                            fromView.setVisibility(View.VISIBLE);

                            if (fromView instanceof ChessPieceView) {
                                ChessPieceView pieceViewFrom = (ChessPieceView) fromView;
                                final int toPos = ((ChessSquareView) view).getPos();
                                final int fromPos = pieceViewFrom.getPos();

                                if (toPos == fromPos) {
                                    // a click
                                    selectPosition(toPos);
                                } else {
                                    pieceViewFrom.setPos(toPos);
                                    chessBoardView.layoutChild(pieceViewFrom);
                                    requestMove(fromPos, toPos);
                                    selectPosition(-1);
                                }
                                ChessBoardActivity.this.updateSelectedSquares();
                            }
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
            int action = motionEvent.getAction();
            if (hasPremoved()) {
                resetPremove();
            } else if (view instanceof ChessPieceView) {
                if (action == MotionEvent.ACTION_DOWN) {
                    final ChessPieceView pieceView = (ChessPieceView) view;
                    int from = pieceView.getPos();
                    if (selectedPosition != from) {
                        ChessBoardActivity.this.setMoveToPositions(pieceView.getPos());
                        ChessBoardActivity.this.updateSelectedSquares();
                    }

                    ClipData data = ClipData.newPlainText("", "");
                    View.DragShadowBuilder shadowBuilder = new MagnifyingDragShadowBuilder(view);
                    view.startDrag(data, shadowBuilder, view, 0);
                    view.setVisibility(View.INVISIBLE);
                    return true;
                } else if (action == MotionEvent.ACTION_UP || action == MotionEvent.ACTION_CANCEL) {
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
            dpadPos = jni.getTurn() == ChessBoard.BLACK ? ChessBoard.e8 : ChessBoard.e1;
            updateSelectedSquares();
        } else {
            dpadPos = -1;
            updateSelectedSquares();
        }
        Log.d(TAG, "dpad focus " + hasFocus + ", " + dpadPos);
    }

    public boolean dpadUp() {
        Log.d(TAG, "dpadUp " + dpadPos);
        if (dpadPos == -1) {
            return false;
        }
        if (dpadPos >= 8) {
            dpadPos -= 8;
            updateSelectedSquares();
            return true;
        }
        return false;
    }

    public boolean dpadDown() {
        Log.d(TAG, "dpadDown " + dpadPos);
        if (dpadPos == -1) {
            return false;
        }
        if (dpadPos < 55) {
            dpadPos += 8;
            updateSelectedSquares();
            return true;
        }
        return false;
    }

    public boolean dpadLeft() {
        Log.d(TAG, "dpadLeft " + dpadPos);
        if (dpadPos == -1) {
            return false;
        }
        if (Pos.col(dpadPos) > 0) {
            dpadPos--;
            updateSelectedSquares();
            return true;
        }
        return false;
    }

    public boolean dpadRight() {
        Log.d(TAG, "dpadRight " + dpadPos);
        if (dpadPos == -1) {
            return false;
        }

        if (Pos.col(dpadPos) < 7) {
            dpadPos++;
            updateSelectedSquares();
            return true;
        }
        return false;
    }

    public boolean dpadSelect() {
        Log.d(TAG, "dpadSelect " + dpadPos);
        if (dpadPos != -1) {
            selectPosition(dpadPos);
            return true;
        }
        return false;
    }

    protected void handleAmbiguousCastle(int from, int to) {
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
    }

    protected void selectPosition(int pos) {
        Log.d(TAG, "selectPosition " + pos + ", " + selectedPosition);
        if (pos == -1) {
            selectedPosition = pos;
            updateSelectedSquares();
        } else {
            if (selectedPosition == -1) {
                selectedPosition = pos;
                showAccessibilityForSelectedPosition(pos);
                setMoveToPositions(pos);
                updateSelectedSquares();
            } else if (selectedPosition != pos){
                handleMove(pos);
            } else {
                if (jni.isAmbiguousCastle(selectedPosition, pos) != 0) {
                    handleAmbiguousCastle(selectedPosition, pos);
                }
                selectedPosition = -1;
                moveToPositions.clear();
                updateSelectedSquares();
            }
        }
    }

    protected void handleMove(int pos) {
        Log.d(TAG, "handleMove " + selectedPosition + ", " + pos);
        ChessPieceView pieceViewFrom = getPieceViewOnPosition(selectedPosition);
        if (pieceViewFrom != null) {
            pieceViewFrom.setPos(pos);
            chessBoardView.layoutChild(pieceViewFrom);
        }
        requestMove(selectedPosition, pos);
        selectedPosition = -1;
        ChessBoardActivity.this.updateSelectedSquares();
    }
}
