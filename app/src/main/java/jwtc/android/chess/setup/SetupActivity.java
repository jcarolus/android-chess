package jwtc.android.chess.setup;

import android.content.ClipData;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.util.Log;
import android.view.DragEvent;
import android.view.KeyEvent;
import android.view.MotionEvent;
import android.view.View;
import android.widget.CheckBox;
import android.widget.TextView;

import com.google.android.material.button.MaterialButtonToggleGroup;
import com.google.android.material.button.MaterialButton;

import java.util.Random;

import jwtc.android.chess.R;
import jwtc.android.chess.activities.ChessBoardActivity;
import jwtc.android.chess.constants.Piece;
import jwtc.android.chess.helpers.ActivityHelper;
import jwtc.android.chess.helpers.MagnifyingDragShadowBuilder;
import jwtc.android.chess.views.ChessBoardView;
import jwtc.android.chess.views.ChessPieceView;
import jwtc.android.chess.views.ChessPiecesStackView;
import jwtc.android.chess.views.ChessSquareView;
import jwtc.android.chess.views.FixedDropdownView;
import jwtc.chess.JNI;
import jwtc.chess.board.BoardConstants;

public class SetupActivity extends ChessBoardActivity {
    private static final String TAG = "SetupActivity";

    private ChessPiecesStackView blackPieces;
    private ChessPiecesStackView whitePieces;
    private ChessPiecesStackView duckStack;

    private FixedDropdownView spinnerEPFile;
    private MaterialButtonToggleGroup toggleTurnGroup;

    private CheckBox checkWhiteCastleShort;
    private CheckBox checkWhiteCastleLong;
    private CheckBox checkBlackCastleShort;
    private CheckBox checkBlackCastleLong;
    private TextView textViewWhitePieces, textViewBlackPieces;

    protected int selectedPiece = -1;
    protected int selectedColor = -1;
    protected int dpadPosWhitePieces = -1;
    protected int dpadPosBlackPieces = -1;


    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        setContentView(R.layout.setup);

        ActivityHelper.fixPaddings(this, findViewById(R.id.LayoutMain));

        gameApi = new SetupApi();

        blackPieces = findViewById(R.id.blackPieces);
        whitePieces = findViewById(R.id.whitePieces);
        duckStack = findViewById(R.id.duckStack);
        toggleTurnGroup = findViewById(R.id.RadioGroupSetup);
        checkWhiteCastleShort = findViewById(R.id.CheckBoxSetupWhiteCastleShort);
        checkWhiteCastleLong = findViewById(R.id.CheckBoxSetupWhiteCastleLong);
        checkBlackCastleShort = findViewById(R.id.CheckBoxSetupBlackCastleShort);
        checkBlackCastleLong = findViewById(R.id.CheckBoxSetupBlackCastleLong);

        spinnerEPFile = findViewById(R.id.SpinnerOptionsEnPassant);
        spinnerEPFile.setItems(getResources().getStringArray(R.array.field_files));

        textViewWhitePieces = findViewById(R.id.TextViewWhitePieces);
        textViewBlackPieces = findViewById(R.id.TextViewBlackPieces);

        MaterialButton buttonOk = findViewById(R.id.ButtonSetupOptionsOk);
        buttonOk.setOnClickListener(v -> onSave());

        MaterialButton buttonCancel = findViewById(R.id.ButtonSetupOptionsCancel);
        buttonCancel.setOnClickListener(v -> finish());

        MaterialButton buttonReset = findViewById(R.id.ButtonSetupOptionsReset);
        buttonReset.setOnClickListener(v -> initBoard());

        MaterialButton buttonClear = findViewById(R.id.ButtonSetupOptionsClear);
        buttonClear.setOnClickListener(v -> resetBoard());

        MaterialButton buttonRandom = findViewById(R.id.ButtonSetupOptionsRandom);
        buttonRandom.setOnClickListener(v -> randomBoard());

        afterCreate();
        buildPieces();
    }

    @Override
    protected void onResume() {
        SharedPreferences prefs = getPrefs();

        final String sFEN = prefs.getString("FEN", null);
        if (sFEN == null) {
            resetBoard();
            toggleTurnGroup.check(R.id.RadioSetupTurnWhite);
        } else {
            jni.initFEN(sFEN);
            toggleTurnGroup.check(jni.getTurn() == BoardConstants.WHITE
                    ? R.id.RadioSetupTurnWhite
                    : R.id.RadioSetupTurnBlack);
            checkWhiteCastleLong.setChecked(jni.getWhiteCanCastleLong() != 0);
            checkWhiteCastleShort.setChecked(jni.getWhiteCanCastleShort() != 0);
            checkBlackCastleLong.setChecked(jni.getBlackCanCastleLong() != 0);
            checkBlackCastleShort.setChecked(jni.getBlackCanCastleShort() != 0);

            setEpPosition(jni.getEnpassantPosition());
        }

        selectedPosition = -1;
        selectedColor = -1;
        selectedPiece = -1;

        rebuildAndDispatch();

        super.onResume();
    }

    protected void onSave() {

        final int turn = toggleTurnGroup.getCheckedButtonId() == R.id.RadioSetupTurnWhite ? 1 : 0;

        jni.setTurn(turn);

        int iEP = getEpSquare(turn);

        jni.setCastlingsEPAnd50(
                checkWhiteCastleLong.isChecked() ? 1 : 0,
                checkWhiteCastleShort.isChecked() ? 1 : 0,
                checkBlackCastleLong.isChecked() ? 1 : 0,
                checkBlackCastleShort.isChecked() ? 1 : 0,
                iEP, 0);
        jni.commitBoard();

        if (jni.isLegalPosition() == 0) {
            openConfirmDialog("Use illegal position?", getString(R.string.alert_yes),getString(R.string.alert_no), () -> {
                commitFEN();
                SetupActivity.this.finish();
            }, null);
        } else {
            commitFEN();
            finish();
        }
    }

    @Override
    public boolean requestMove(int from, int to) {
        return false;
    }

    @Override
    public void OnMove(int move) {
        super.OnMove(move);
    }

    @Override
    public void OnState() {
        super.OnState();

        textViewWhitePieces.setText(getPiecesDescription(BoardConstants.WHITE));
        textViewBlackPieces.setText(getPiecesDescription(BoardConstants.BLACK));
    }

    @Override
    public void afterCreate() {
        Log.d(TAG, " afterCreate");

        jni = JNI.getInstance();
        chessBoardView = findViewById(R.id.includeboard);

        initDirectionalPad();

        chessBoardView.setNextFocusRightId(R.id.blackPieces);

        whitePieces.setFocusable(true);
        whitePieces.setNextFocusLeftId(R.id.ChessBoardLayout);
        whitePieces.setOnFocusChangeListener(new View.OnFocusChangeListener() {
             @Override
             public void onFocusChange(View v, boolean hasFocus) {
                 dpadPieceFocus(hasFocus, BoardConstants.WHITE);
             }
         });

        whitePieces.setOnKeyListener(new View.OnKeyListener() {
            @Override
            public boolean onKey(View v, int keyCode, KeyEvent event) {
                if (event.getAction() == KeyEvent.ACTION_DOWN) {
                    return dpadPieceKeyDown(keyCode, BoardConstants.WHITE);
                }
                return false;
            }
        });

        blackPieces.setFocusable(true);
        blackPieces.setNextFocusLeftId(R.id.ChessBoardLayout);
        blackPieces.setOnFocusChangeListener(new View.OnFocusChangeListener() {
            @Override
            public void onFocusChange(View v, boolean hasFocus) {
                dpadPieceFocus(hasFocus, BoardConstants.BLACK);
            }
        });
        blackPieces.setOnKeyListener(new View.OnKeyListener() {
            @Override
            public boolean onKey(View v, int keyCode, KeyEvent event) {
                if (event.getAction() == KeyEvent.ACTION_DOWN) {
                    return dpadPieceKeyDown(keyCode, BoardConstants.BLACK);
                }
                return false;
            }
        });

        duckStack.setFocusable(true);
        duckStack.setNextFocusLeftId(R.id.RadioGroupSetup);
        duckStack.setOnFocusChangeListener(new View.OnFocusChangeListener() {
            @Override
            public void onFocusChange(View v, boolean hasFocus) {
                updateSelectedSquares();
            }
        });
        duckStack.setOnKeyListener(new View.OnKeyListener() {
            @Override
            public boolean onKey(View v, int keyCode, KeyEvent event) {
                if (event.getAction() == KeyEvent.ACTION_DOWN) {
                    if (keyCode == KeyEvent.KEYCODE_DPAD_CENTER || keyCode == KeyEvent.KEYCODE_ENTER) {
                        if (selectedPiece == BoardConstants.DUCK) {
                            selectedColor = -1;
                            selectedPiece = -1;
                        } else {
                            selectedColor = BoardConstants.BLACK;
                            selectedPiece = BoardConstants.DUCK;
                        }
                        updateSelectedSquares();
                        return true;
                    }
                }
                return false;
            }
        });

        myDragListener = new MyDragListener();
        myTouchListener = new MyTouchListener();
        myClickListener = new MyClickListener();

        for (int i = 0; i < 64; i++) {
            ChessSquareView csv = new ChessSquareView(this, i);
            csv.setOnDragListener(myDragListener);
            csv.setOnClickListener(myClickListener);

            String nextDescription = getFieldDescription(i);
            csv.setContentDescription(nextDescription);

            chessBoardView.addView(csv);
        }

        gameApi.addListener(this);
    }

    public void buildPieces() {

        for (int i = 0; i < 5; i++) {
            ChessSquareView squareForBlack = new ChessSquareView(this, i);
            squareForBlack.setOnDragListener(myDragListener);
            squareForBlack.setOnClickListener(myClickListener);
            squareForBlack.setContentDescription(getPieceDescription(i, BoardConstants.BLACK));
            blackPieces.addView(squareForBlack);

            ChessSquareView squareForWhite = new ChessSquareView(this, i);
            squareForWhite.setOnDragListener(myDragListener);
            squareForWhite.setOnClickListener(myClickListener);
            squareForWhite.setContentDescription(getPieceDescription(i, BoardConstants.WHITE));
            whitePieces.addView(squareForWhite);

            ChessPieceView blackPieceView = new ChessPieceView(this, BoardConstants.BLACK, i, i);
            blackPieceView.setOnTouchListener(myTouchListener);
            blackPieces.addView(blackPieceView);

            ChessPieceView whitePieceView = new ChessPieceView(this, BoardConstants.WHITE, i, i);
            whitePieceView.setOnTouchListener(myTouchListener);
            whitePieces.addView(whitePieceView);
        }

        ChessSquareView squareForDuck = new ChessSquareView(this, 0);
        squareForDuck.setOnDragListener(myDragListener);
        squareForDuck.setOnClickListener(myClickListener);
        squareForDuck.setContentDescription(getPieceDescription(BoardConstants.DUCK, BoardConstants.WHITE));
        duckStack.addView(squareForDuck);

        ChessPieceView duckView = new ChessPieceView(this, BoardConstants.WHITE, BoardConstants.DUCK, 0);
        duckView.setOnTouchListener(myTouchListener);
        duckStack.addView(duckView);
    }

    public void resetBoard() {
        jni.reset();

        jni.putPiece(BoardConstants.e1, BoardConstants.KING, BoardConstants.WHITE);
        jni.putPiece(BoardConstants.e8, BoardConstants.KING, BoardConstants.BLACK);
        rebuildAndDispatch();
    }

    public void randomBoard() {
        final int turn = toggleTurnGroup.getCheckedButtonId() == R.id.RadioSetupTurnWhite ? 1 : 0;
        Random rng = new Random();
        final int maxAttempts = 200;
        boolean legal = false;
        for (int attempt = 0; attempt < maxAttempts; attempt++) {
            generateRandomBoardOnce(rng, turn);
            if (jni.isLegalPosition() != 0 && jni.isEnded() == 0) {
                legal = true;
                break;
            }
        }
        if (!legal) {
            jni.reset();
            jni.setTurn(turn);
            jni.putPiece(BoardConstants.e1, BoardConstants.KING, BoardConstants.WHITE);
            jni.putPiece(BoardConstants.e8, BoardConstants.KING, BoardConstants.BLACK);
            jni.commitBoard();
        }
        rebuildAndDispatch();
    }

    private void generateRandomBoardOnce(Random rng, int turn) {
        jni.reset();

        jni.setTurn(turn);

        boolean[] occupied = new boolean[64];

        int whiteKingPos = rng.nextInt(64);
        int blackKingPos;
        do {
            blackKingPos = rng.nextInt(64);
        } while (blackKingPos == whiteKingPos);

        occupied[whiteKingPos] = true;
        occupied[blackKingPos] = true;

        jni.putPiece(whiteKingPos, BoardConstants.KING, BoardConstants.WHITE);
        jni.putPiece(blackKingPos, BoardConstants.KING, BoardConstants.BLACK);

        int piecesToAdd = 1 + rng.nextInt(20);
        int[] pieceTypes = new int[] {
                BoardConstants.PAWN,
                BoardConstants.KNIGHT,
                BoardConstants.BISHOP,
                BoardConstants.ROOK,
                BoardConstants.QUEEN
        };

        for (int i = 0; i < piecesToAdd; i++) {
            int pos;
            do {
                pos = rng.nextInt(64);
            } while (occupied[pos]);

            occupied[pos] = true;
            int piece = pieceTypes[rng.nextInt(pieceTypes.length)];
            int color = rng.nextBoolean() ? BoardConstants.WHITE : BoardConstants.BLACK;
            jni.putPiece(pos, piece, color);
        }
        jni.commitBoard();
    }

    public void initBoard() {
        jni.newGame();
        rebuildAndDispatch();
    }

    public void rebuildAndDispatch() {
        rebuildBoard();
        OnState();
    }
    public void addPiece(final int pos, final int piece, final int turn) {
        Log.d(TAG, "addPiece " + pos + " " + piece + " " + turn);
        if (piece == BoardConstants.DUCK) {
            jni.setVariant(BoardConstants.VARIANT_DEFAULT); // resets duck
            jni.setVariant(BoardConstants.VARIANT_DUCK);
            jni.requestDuckMove(pos);
        } else {
            jni.putPiece(pos, piece, turn);
        }
    }

    public void removePiece(final int pos) {
        Log.d(TAG, "removePiece " + pos);
        if (jni.getDuckPos() == pos) {
            Log.d(TAG, "set VARIANT_DEFAULT");
            jni.setVariant(BoardConstants.VARIANT_DEFAULT);
        } else {
            final int whitePiece = jni.pieceAt(BoardConstants.WHITE, pos);
            if (whitePiece != BoardConstants.FIELD) {
                Log.d(TAG, "removePiece white " + pos + " " + whitePiece);
                jni.removePiece(BoardConstants.WHITE, pos);
            }
            final int blackPiece = jni.pieceAt(BoardConstants.BLACK, pos);
            if (blackPiece != BoardConstants.FIELD) {
                Log.d(TAG, "removePiece black " + pos + " " + blackPiece);
                jni.removePiece(BoardConstants.BLACK, pos);
            }
        }
    }

    protected void selectPosition(int pos) {
        Log.d(TAG, "selectPosition!"  + pos + " " + isPosOfKing(pos) + " " + selectedColor);
        if (selectedColor == -1) {
            if (selectedPosition == -1) {
                selectedPosition = pos;
                updateSelectedSquares();
            } else if (selectedPosition == pos) {
                selectedPosition = -1;
                updateSelectedSquares();
            } else {
                movePiece(selectedPosition, pos);
            }
        } else {
            addPieceFromStack(pos);
        }
    }

    protected void movePiece(int from, int to) {
        if (!isPosOfKing(to)) {
            int whitePieceFrom = jni.pieceAt(BoardConstants.WHITE, from);
            int blackPieceFrom = jni.pieceAt(BoardConstants.BLACK, from);

            removePiece(from);
            removePiece(to);

            if (whitePieceFrom != BoardConstants.FIELD) {
                addPiece(to, whitePieceFrom, BoardConstants.WHITE);
            } else if (blackPieceFrom != BoardConstants.FIELD) {
                addPiece(to, blackPieceFrom, BoardConstants.BLACK);
            }

            selectedPosition = -1;
            rebuildAndDispatch();
        } else {
            Log.d(TAG, "movePiece on king " + from + " " + to);
        }
    }

    protected void addPieceFromStack(int to) {
        if (!isPosOfKing(to) && selectedPiece != -1 && selectedColor != -1) {
            removePiece(to);
            addPiece(to, selectedPiece, selectedColor);
            selectedPosition = -1;

            rebuildAndDispatch();
        } else {
            Log.d(TAG, "can not addPieceFromStack " + to + " " + selectedPiece + " " + selectedColor);
            selectedPiece = -1;
            selectedColor = -1;
            selectedPosition = to;

            updateSelectedSquares();
        }
    }


    @Override
    public void updateSelectedSquares() {
        // Log.d(TAG, "updateSelectedSquares");
        super.updateSelectedSquares();

        final int count = whitePieces.getChildCount();
        for (int i = 0; i < count; i++) {
            View child = whitePieces.getChildAt(i);

            if (child instanceof ChessSquareView) {
                ((ChessSquareView) child).setSelected(selectedColor == BoardConstants.WHITE && selectedPiece == ((ChessSquareView) child).getPos());
                ((ChessSquareView) child).setFocussed(((ChessSquareView) child).getPos() == dpadPosWhitePieces);
            }

            child = blackPieces.getChildAt(i);

            if (child instanceof ChessSquareView) {
                ((ChessSquareView) child).setSelected(selectedColor == BoardConstants.BLACK && selectedPiece == ((ChessSquareView) child).getPos());
                ((ChessSquareView) child).setFocussed(((ChessSquareView) child).getPos() == dpadPosBlackPieces);
            }
        }

        ChessSquareView duckSquare = (ChessSquareView)duckStack.getChildAt(0);
        duckSquare.setFocussed(duckStack.hasFocus());
        duckSquare.setSelected(selectedPiece == BoardConstants.DUCK);
    }

    protected void commitFEN() {
        Log.d(TAG, "commitFEN");

        SharedPreferences.Editor editor = this.getPrefs().edit();
        editor.putString("FEN", jni.toFEN());
        editor.putString("game_pgn", null);
        editor.putInt("boardNum", 0);
        editor.putLong("game_id", 0);
        editor.commit();
    }

    protected String getPieceDescription(int piece, int color) {
        if (piece == BoardConstants.DUCK) {
            return getString(Piece.toResource(piece));
        }
        return getString(color == BoardConstants.WHITE ? R.string.piece_white : R.string.piece_black) + " " + getString(Piece.toResource(piece));
    }

    protected void dpadPieceFocus(boolean hasFocus, int color) {
        if (hasFocus) {
            dpadPosBlackPieces = color == BoardConstants.BLACK ? 0 : -1;
            dpadPosWhitePieces = color == BoardConstants.WHITE ? 0 : -1;
        } else {
            dpadPosBlackPieces = color == BoardConstants.BLACK ? -1 : dpadPosBlackPieces;
            dpadPosWhitePieces = color == BoardConstants.WHITE ? -1 : dpadPosWhitePieces;
        }
        updateSelectedSquares();
    }

    protected boolean dpadPieceKeyDown(int keyCode, int color) {
        Log.d(TAG, "dpadPieceKeyDown " + keyCode + " " + color);
        switch (keyCode) {
            case KeyEvent.KEYCODE_DPAD_CENTER:
            case KeyEvent.KEYCODE_ENTER:
                selectPieceInStack(color, color == BoardConstants.WHITE ? dpadPosWhitePieces : dpadPosBlackPieces);
                return true;
            case KeyEvent.KEYCODE_DPAD_LEFT:
                if (color == BoardConstants.WHITE && dpadPosWhitePieces > 0) {
                    dpadPosWhitePieces--;
                    updateSelectedSquares();
                    return true;
                }
                if (color == BoardConstants.BLACK && dpadPosBlackPieces > 0) {
                    dpadPosBlackPieces--;
                    updateSelectedSquares();
                    return true;
                }
                return false;
            case KeyEvent.KEYCODE_DPAD_RIGHT:
                if (color == BoardConstants.WHITE && dpadPosWhitePieces < 5) {
                    dpadPosWhitePieces++;
                    updateSelectedSquares();
                    return true;
                }
                if (color == BoardConstants.BLACK && dpadPosBlackPieces < 5) {
                    dpadPosBlackPieces++;
                    updateSelectedSquares();
                    return true;
                }
                return false;
        }
        return false;
    }

    protected void selectPieceInStackByViews(ChessPieceView pieceView) {
        if (pieceView.getParent() == whitePieces) {
            selectPieceInStack(BoardConstants.WHITE, pieceView.getPiece());
        } else if (pieceView.getParent() == blackPieces) {
            selectPieceInStack(BoardConstants.BLACK, pieceView.getPiece());
        } else if (pieceView.getParent() == duckStack) {
            selectPieceInStack(BoardConstants.BLACK, pieceView.getPiece());
        }
    }

    protected void selectPieceInStack(int color, int piece) {
        Log.d(TAG, "selectPieceInStack " + color + " " + piece);
        if (selectedColor == color && selectedPiece == piece) {
            selectedColor = -1;
            selectedPiece = -1;
            selectedPosition = -1;
            updateSelectedSquares();
        } else {
            if (selectedPosition != -1 && !isPosOfKing(selectedPosition)) {
                removePiece(selectedPosition);
            }
            selectedColor = color;
            selectedPiece = piece;
            selectedPosition = -1;
            rebuildAndDispatch();
        }
    }

    protected boolean isPosOfKing(int toPos) {
        return jni.pieceAt(BoardConstants.WHITE, toPos) == BoardConstants.KING || jni.pieceAt(BoardConstants.BLACK, toPos) == BoardConstants.KING;
    }

    protected int getEpSquare(int turn) {
        int iEP = -1;
        switch (spinnerEPFile.getSelectedItemPosition()) {
            case 1:
                iEP = turn == 1 ? 16 : 40;
                break;
            case 2:
                iEP = turn == 1 ? 17 : 41;
                break;
            case 3:
                iEP = turn == 1 ? 18 : 42;
                break;
            case 4:
                iEP = turn == 1 ? 19 : 43;
                break;
            case 5:
                iEP = turn == 1 ? 20 : 44;
                break;
            case 6:
                iEP = turn == 1 ? 21 : 45;
                break;
            case 7:
                iEP = turn == 1 ? 22 : 46;
                break;
            case 8:
                iEP = turn == 1 ? 23 : 47;
                break;
        }
        return iEP;
    }

    protected void setEpPosition(int ep) {
        Log.d(TAG, "ep " + ep);
        int position = 0;
        switch(ep) {
            case 16:
            case 40:
                position = 1;
                break;
            case 17:
            case 41:
                position = 2;
                break;
            case 18:
            case 42:
                position = 3;
                break;
            case 19:
            case 43:
                position = 4;
                break;
            case 20:
            case 44:
                position = 5;
                break;
            case 21:
            case 45:
                position = 6;
                break;
            case 22:
            case 46:
                position = 7;
                break;
            case 23:
            case 47:
                position = 8;
                break;
        }
        spinnerEPFile.setSelection(position);
    }

    protected class MyDragListener extends ChessBoardActivity.MyDragListener {

        @Override
        public boolean onDrag(View view, DragEvent event) {
            int action = event.getAction();
            if (view instanceof ChessSquareView) {
                final ChessSquareView droppedOnSquare = (ChessSquareView) view;
                final int toPos = droppedOnSquare.getPos();
                boolean onChessBoard = droppedOnSquare.getParent() instanceof ChessBoardView;

                switch (action) {
                    case DragEvent.ACTION_DROP:
                        Log.i(TAG, "onDrag DROP " + toPos);
                        // Dropped, reassign View to ViewGroup
                        View fromView = (View) event.getLocalState();
                        if (fromView != null) {
                            boolean fromPieceStack = fromView.getParent() instanceof ChessPiecesStackView;
                            if (fromView instanceof ChessPieceView) {
                                final ChessPieceView pieceView = (ChessPieceView) fromView;
                                final int fromPos = pieceView.getPos();

                                if (toPos == fromPos) {
                                    if (onChessBoard) {
                                        selectPosition(toPos);
                                    } else {
                                        selectPieceInStackByViews(pieceView);
                                    }
                                } else if (onChessBoard) {
                                    // drop on board
                                    if (fromPieceStack) {
                                        selectPieceInStackByViews(pieceView);
                                        addPieceFromStack(toPos);
                                    } else {
                                        movePiece(fromPos, toPos);
                                    }
                                } else {
                                    // dropped back
                                    if (pieceView.getParent() instanceof ChessBoardView) {
                                        removePiece(fromPos);
                                        rebuildAndDispatch();
                                    } else {
                                        selectPieceInStackByViews(pieceView);
                                    }
                                }

                                pieceView.setVisibility(View.VISIBLE);
                            }
                        }

                        break;

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

    protected class MyTouchListener extends ChessBoardActivity.MyTouchListener {
        public boolean onTouch(View view, MotionEvent motionEvent) {
            int action = motionEvent.getAction();
            if (view instanceof ChessPieceView) {
                final int pos =  ((ChessPieceView) view).getPos();

                if (action == MotionEvent.ACTION_DOWN) {
                    Log.i(TAG, "onTouch DOWN " + pos);

                    ClipData data = ClipData.newPlainText("", "");
                    View.DragShadowBuilder shadowBuilder = new MagnifyingDragShadowBuilder(view);
                    view.startDrag(data, shadowBuilder, view, 0);

                    view.setVisibility(View.INVISIBLE);
                    return true;
                } else if (action == MotionEvent.ACTION_UP || action == MotionEvent.ACTION_CANCEL) {
                    Log.i(TAG, "onTouch UP " + pos);

                    view.setVisibility(View.VISIBLE);
                    view.invalidate();
                    return true;
                }
            }
            return false;
        }
    }

    protected class MyClickListener  extends ChessBoardActivity.MyClickListener {

        @Override
        public void onClick(View view) {
            if (view instanceof ChessSquareView) {
                final int toPos = ((ChessSquareView) view).getPos();
                if (view.getParent() instanceof ChessBoardView) {
                    selectPosition(toPos);
                } else {
                    if (view.getParent() == duckStack) {
                        selectPieceInStack(BoardConstants.BLACK, BoardConstants.DUCK);
                    } else {
                        selectPieceInStack(view.getParent() == whitePieces ? BoardConstants.WHITE : BoardConstants.BLACK, ((ChessSquareView) view).getPos());
                    }
                }
            }
        }
    }
}
