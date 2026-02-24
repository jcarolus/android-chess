package jwtc.android.chess.setup;

import android.content.ClipData;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;
import android.view.DragEvent;
import android.view.KeyEvent;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
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
import jwtc.chess.Valuation;
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

    protected int selectedPiece = -1;
    protected int selectedColor = -1;
    protected int dpadPosWhitePieces = -1;
    protected int dpadPosBlackPieces = -1;
    private final Handler accessibilityDragHandler = new Handler(Looper.getMainLooper());
    private Runnable accessibilityDragDwellRunnable = null;
    private int accessibilityDragHoverPos = -1;
    private int accessibilityDragHoverArea = -1;
    private int accessibilityDragSourcePos = -1;
    private int accessibilityDragSourceArea = -1;
    private int accessibilityDragSourcePiece = -1;
    private int accessibilityDragSourceColor = -1;

    private static final int DRAG_AREA_NONE = -1;
    private static final int DRAG_AREA_BOARD = 0;
    private static final int DRAG_AREA_WHITE_STACK = 1;
    private static final int DRAG_AREA_BLACK_STACK = 2;
    private static final int DRAG_AREA_DUCK_STACK = 3;


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
        super.onResume();

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

        sounds.setEnabled(prefs.getBoolean("moveSounds", false));
        textToSpeech.setEnabled(prefs.getBoolean("moveToSpeech", false), prefs);
        useAccessibilityDrag = prefs.getBoolean("useAccessibilityDrag", false);
        Log.d(TAG, "useAcc" + useAccessibilityDrag);
        applySquareDragListeners();
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
            openConfirmDialog("Use illegal position?", getString(R.string.alert_yes), getString(R.string.alert_no), () -> {
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
        myAccessibilityDragListener = new MyAccessibilityDragListener();
        myTouchListener = new MyTouchListener();
        myClickListener = new MyClickListener();

        for (int i = 0; i < 64; i++) {
            ChessSquareView csv = new ChessSquareView(this, i);
            csv.setOnDragListener(useAccessibilityDrag ? myAccessibilityDragListener : myDragListener);
            csv.setOnTouchListener(myTouchListener);
            csv.setOnClickListener(myClickListener);

            String nextDescription = getFieldDescription(i);
            csv.setContentDescription(nextDescription);

            chessBoardView.addView(csv);
        }

        gameApi.addListener(this);
    }

    @Override
    protected void applySquareDragListeners() {
        final int count = chessBoardView.getChildCount();
        for (int i = 0; i < count; i++) {
            final View child = chessBoardView.getChildAt(i);
            if (child instanceof ChessSquareView) {
                child.setOnDragListener(useAccessibilityDrag ? myAccessibilityDragListener : myDragListener);
            }
        }
        applyStackDragListeners();
    }

    private void applyStackDragListeners() {
        View.OnDragListener listener = useAccessibilityDrag ? myAccessibilityDragListener : myDragListener;
        applyDragListenerToStackSquares(whitePieces, listener);
        applyDragListenerToStackSquares(blackPieces, listener);
        applyDragListenerToStackSquares(duckStack, listener);
    }

    private void applyDragListenerToStackSquares(ViewGroup stack, View.OnDragListener listener) {
        final int childCount = stack.getChildCount();
        for (int i = 0; i < childCount; i++) {
            View child = stack.getChildAt(i);
            if (child instanceof ChessSquareView) {
                child.setOnDragListener(listener);
            }
        }
    }

    public void buildPieces() {
        View.OnDragListener listener = useAccessibilityDrag ? myAccessibilityDragListener : myDragListener;

        for (int i = 0; i < 5; i++) {
            ChessSquareView squareForBlack = new ChessSquareView(this, i);
            squareForBlack.setOnDragListener(listener);
            squareForBlack.setOnClickListener(myClickListener);
            squareForBlack.setContentDescription(getPieceDescription(i, BoardConstants.BLACK));
            blackPieces.addView(squareForBlack);

            ChessSquareView squareForWhite = new ChessSquareView(this, i);
            squareForWhite.setOnDragListener(listener);
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
        squareForDuck.setOnDragListener(listener);
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
            int[] pieceSums = generateRandomBoardOnce(rng, turn);
            boolean balanced = isPieceValueBalanced(pieceSums[BoardConstants.WHITE], pieceSums[BoardConstants.BLACK]);
            if (balanced && jni.isLegalPosition() != 0 && jni.isEnded() == 0) {
                legal = true;
                Log.d(TAG, "randomBoard in " + attempt + " attempts, w: " + pieceSums[BoardConstants.WHITE] + " b: " + pieceSums[BoardConstants.BLACK]);
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

    private int[] generateRandomBoardOnce(Random rng, int turn) {
        jni.reset();

        jni.setTurn(turn);
        int[] pieceSums = new int[]{0, 0};

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
        int[] pieceTypes = new int[]{
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
            pieceSums[color] += Valuation.PIECES[piece];
        }
        jni.commitBoard();
        return pieceSums;
    }

    private boolean isPieceValueBalanced(int whiteSum, int blackSum) {
        int diff = Math.abs(whiteSum - blackSum);
        int smaller = Math.min(whiteSum, blackSum);
        return diff <= 2 * smaller;
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
        Log.d(TAG, "selectPosition!" + pos + " " + isPosOfKing(pos) + " " + selectedColor);
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

        ChessSquareView duckSquare = (ChessSquareView) duckStack.getChildAt(0);
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
        switch (ep) {
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

    @Override
    protected void onPause() {
        resetAccessibilityDragState();
        super.onPause();
    }

    private void resetAccessibilityDragState() {
        if (accessibilityDragDwellRunnable != null) {
            accessibilityDragHandler.removeCallbacks(accessibilityDragDwellRunnable);
            accessibilityDragDwellRunnable = null;
        }
        accessibilityDragHoverPos = -1;
        accessibilityDragHoverArea = DRAG_AREA_NONE;
        accessibilityDragSourcePos = -1;
        accessibilityDragSourceArea = DRAG_AREA_NONE;
        accessibilityDragSourcePiece = -1;
        accessibilityDragSourceColor = -1;
    }

    private int getDragAreaForSquare(ChessSquareView squareView) {
        View parent = (View) squareView.getParent();
        if (parent instanceof ChessBoardView) {
            return DRAG_AREA_BOARD;
        }
        if (parent == whitePieces) {
            return DRAG_AREA_WHITE_STACK;
        }
        if (parent == blackPieces) {
            return DRAG_AREA_BLACK_STACK;
        }
        if (parent == duckStack) {
            return DRAG_AREA_DUCK_STACK;
        }
        return DRAG_AREA_NONE;
    }

    private boolean hasBoardPieceOnPosition(int pos) {
        return jni.getDuckPos() == pos
            || jni.pieceAt(BoardConstants.WHITE, pos) != BoardConstants.FIELD
            || jni.pieceAt(BoardConstants.BLACK, pos) != BoardConstants.FIELD;
    }

    private boolean isAccessibilitySourceCandidate(int area, int pos) {
        if (area == DRAG_AREA_BOARD) {
            return hasBoardPieceOnPosition(pos);
        }
        return area == DRAG_AREA_WHITE_STACK
            || area == DRAG_AREA_BLACK_STACK
            || area == DRAG_AREA_DUCK_STACK;
    }

    private String getAccessibilityHoverDescription(int area, int pos) {
        switch (area) {
            case DRAG_AREA_WHITE_STACK:
                return getPieceDescription(pos, BoardConstants.WHITE);
            case DRAG_AREA_BLACK_STACK:
                return getPieceDescription(pos, BoardConstants.BLACK);
            case DRAG_AREA_DUCK_STACK:
                return getPieceDescription(BoardConstants.DUCK, BoardConstants.WHITE);
            case DRAG_AREA_BOARD:
            default:
                return getFieldDescription(pos);
        }
    }

    private void applyAccessibilitySourceSelection(int area, int pos) {
        accessibilityDragSourceArea = area;
        accessibilityDragSourcePos = pos;

        if (area == DRAG_AREA_BOARD) {
            accessibilityDragSourceColor = -1;
            accessibilityDragSourcePiece = -1;
            selectedColor = -1;
            selectedPiece = -1;
            selectedPosition = pos;
            updateSelectedSquares();
            return;
        }

        if (area == DRAG_AREA_WHITE_STACK) {
            accessibilityDragSourceColor = BoardConstants.WHITE;
            accessibilityDragSourcePiece = pos;
        } else if (area == DRAG_AREA_BLACK_STACK) {
            accessibilityDragSourceColor = BoardConstants.BLACK;
            accessibilityDragSourcePiece = pos;
        } else if (area == DRAG_AREA_DUCK_STACK) {
            accessibilityDragSourceColor = BoardConstants.BLACK;
            accessibilityDragSourcePiece = BoardConstants.DUCK;
        } else {
            accessibilityDragSourceColor = -1;
            accessibilityDragSourcePiece = -1;
        }

        selectedPosition = -1;
        selectedColor = accessibilityDragSourceColor;
        selectedPiece = accessibilityDragSourcePiece;
        updateSelectedSquares();
    }

    private void scheduleAccessibilityDwellSelection(int area, int pos) {
        if (accessibilityDragDwellRunnable != null) {
            accessibilityDragHandler.removeCallbacks(accessibilityDragDwellRunnable);
        }
        accessibilityDragDwellRunnable = () -> {
            if (accessibilityDragHoverArea != area || accessibilityDragHoverPos != pos) {
                return;
            }
            if (!isAccessibilitySourceCandidate(area, pos)) {
                return;
            }
            applyAccessibilitySourceSelection(area, pos);
            feedbackSelect();
            if (textToSpeech.isEnabled()) {
                textToSpeech.doSpeak(getAccessibilityHoverDescription(area, pos));
            }
        };
        accessibilityDragHandler.postDelayed(accessibilityDragDwellRunnable, accessibilityDragDwellMs);
    }

    private void handleAccessibilitySquareEntered(ChessSquareView squareView, boolean emitCrossingHaptic) {
        final int pos = squareView.getPos();
        final int area = getDragAreaForSquare(squareView);
        if (area == DRAG_AREA_NONE) {
            return;
        }

        if (accessibilityDragHoverArea == area && accessibilityDragHoverPos == pos) {
            return;
        }

        accessibilityDragHoverArea = area;
        accessibilityDragHoverPos = pos;

        if (accessibilityDragDwellRunnable != null) {
            accessibilityDragHandler.removeCallbacks(accessibilityDragDwellRunnable);
            accessibilityDragDwellRunnable = null;
        }

        boolean hasPiece = isAccessibilitySourceCandidate(area, pos);
        if (emitCrossingHaptic) {
            hapticFeedbackTick(hasPiece);
        }
        if (textToSpeech.isEnabled()) {
            textToSpeech.doSpeak(getAccessibilityHoverDescription(area, pos));
        }
        if (hasPiece) {
            scheduleAccessibilityDwellSelection(area, pos);
        }
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
                            droppedView.post(new Runnable() {
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

    protected class MyAccessibilityDragListener extends ChessBoardActivity.MyAccessibilityDragListener {
        @Override
        public boolean onDrag(View view, DragEvent event) {
            Log.d(TAG, "onDrag yes");
            if (!(view instanceof ChessSquareView)) {
                return false;
            }

            ChessSquareView squareView = (ChessSquareView) view;
            switch (event.getAction()) {
                case DragEvent.ACTION_DRAG_STARTED:
                    resetAccessibilityDragState();
                    View fromView = (View) event.getLocalState();
                    if (fromView instanceof ChessSquareView) {
                        handleAccessibilitySquareEntered((ChessSquareView) fromView, false);
                    } else if (fromView instanceof ChessPieceView) {
                        ViewGroup parent = (ViewGroup) ((ChessPieceView) fromView).getParent();
                        if (parent instanceof ChessPiecesStackView && parent.getChildCount() > 0 && parent.getChildAt(0) instanceof ChessSquareView) {
                            handleAccessibilitySquareEntered((ChessSquareView) parent.getChildAt(0), false);
                        } else {
                            int fromPos = ((ChessPieceView) fromView).getPos();
                            ChessSquareView fromSquare = getSquareAt(fromPos);
                            if (fromSquare != null) {
                                handleAccessibilitySquareEntered(fromSquare, false);
                            }
                        }
                    }
                    break;
                case DragEvent.ACTION_DRAG_ENTERED:
                    handleAccessibilitySquareEntered(squareView, true);
                    break;
                case DragEvent.ACTION_DRAG_EXITED:
                    view.setSelected(false);
                    break;
                case DragEvent.ACTION_DROP:
                    handleAccessibilityDrop(squareView);
                    break;
                case DragEvent.ACTION_DRAG_ENDED:
                    if (!event.getResult() && selectedPosition != -1) {
                        selectPosition(-1);
                    }
                    resetAccessibilityDragState();
                    break;
                default:
                    break;
            }
            return true;
        }

        private void handleAccessibilityDrop(ChessSquareView droppedOnSquare) {
            final int toPos = droppedOnSquare.getPos();
            final int toArea = getDragAreaForSquare(droppedOnSquare);
            boolean onChessBoard = toArea == DRAG_AREA_BOARD;

            if (accessibilityDragSourceArea == DRAG_AREA_BOARD && accessibilityDragSourcePos != -1) {
                if (onChessBoard) {
                    if (toPos == accessibilityDragSourcePos) {
                        selectPosition(toPos);
                    } else {
                        movePiece(accessibilityDragSourcePos, toPos);
                    }
                } else {
                    removePiece(accessibilityDragSourcePos);
                    rebuildAndDispatch();
                }
            } else if ((accessibilityDragSourceArea == DRAG_AREA_WHITE_STACK
                || accessibilityDragSourceArea == DRAG_AREA_BLACK_STACK
                || accessibilityDragSourceArea == DRAG_AREA_DUCK_STACK)
                && accessibilityDragSourcePiece != -1 && accessibilityDragSourceColor != -1) {
                if (onChessBoard) {
                    selectedColor = accessibilityDragSourceColor;
                    selectedPiece = accessibilityDragSourcePiece;
                    addPieceFromStack(toPos);
                } else {
                    selectPieceInStack(accessibilityDragSourceColor, accessibilityDragSourcePiece);
                }
            }

            selectedPosition = -1;
            selectedColor = -1;
            selectedPiece = -1;
            updateSelectedSquares();
        }
    }

    protected class MyTouchListener extends ChessBoardActivity.MyTouchListener {
        public boolean onTouch(View view, MotionEvent motionEvent) {
            int action = motionEvent.getAction();
            if (useAccessibilityDrag && (view instanceof ChessPieceView || view instanceof ChessSquareView)) {
                if (action == MotionEvent.ACTION_DOWN) {
                    ClipData data = ClipData.newPlainText("", "");
                    View.DragShadowBuilder shadowBuilder = new View.DragShadowBuilder(view);
                    view.startDrag(data, shadowBuilder, view, 0);
                    return true;
                } else if (action == MotionEvent.ACTION_UP || action == MotionEvent.ACTION_CANCEL) {
                    return true;
                }
            } else if (view instanceof ChessPieceView) {
                final int pos = ((ChessPieceView) view).getPos();

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

    protected class MyClickListener extends ChessBoardActivity.MyClickListener {

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
