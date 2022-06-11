package jwtc.android.chess.setup;

import android.content.ClipData;
import android.content.DialogInterface;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.util.Log;
import android.view.DragEvent;
import android.view.MotionEvent;
import android.view.View;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.RadioButton;
import android.widget.Spinner;
import android.app.AlertDialog;

import jwtc.android.chess.R;
import jwtc.android.chess.activities.ChessBoardActivity;
import jwtc.android.chess.views.ChessBoardView;
import jwtc.android.chess.views.ChessPieceView;
import jwtc.android.chess.views.ChessPiecesStackView;
import jwtc.android.chess.views.ChessSquareView;
import jwtc.chess.JNI;
import jwtc.chess.board.BoardConstants;

public class SetupActivity extends ChessBoardActivity {
    private static final String TAG = "SetupActivity";

    private ChessPiecesStackView blackPieces;
    private ChessPiecesStackView whitePieces;
    private Spinner spinnerEPFile;

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        setContentView(R.layout.setup);

        gameApi = new SetupApi();

        afterCreate();

        blackPieces = findViewById(R.id.blackPieces);
        whitePieces = findViewById(R.id.whitePieces);

        ArrayAdapter<CharSequence> adapter = ArrayAdapter.createFromResource(this, R.array.field_files, android.R.layout.simple_spinner_item);
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);

        spinnerEPFile = findViewById(R.id.SpinnerOptionsEnPassant);
        spinnerEPFile.setPrompt(getString(R.string.title_pick_en_passant));
        spinnerEPFile.setAdapter(adapter);

        Button buttonOk = findViewById(R.id.ButtonSetupOptionsOk);
        buttonOk.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                onSave();
            }
        });

        Button buttonCancel = findViewById(R.id.ButtonSetupOptionsCancel);
        buttonCancel.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                finish();
            }
        });

        buildPieces();
    }

    @Override
    protected void onResume() {
        SharedPreferences prefs = getPrefs();

        resetBoard();
        RadioButton radioTurnWhite = findViewById(R.id.RadioSetupTurnWhite);
        radioTurnWhite.setChecked(true);

        super.onResume();
    }

    protected void onSave() {

        RadioButton radioTurnWhite = findViewById(R.id.RadioSetupTurnWhite);
        RadioButton radioTurnBlack = findViewById(R.id.RadioSetupTurnBlack);

        CheckBox checkWhiteCastleShort = findViewById(R.id.CheckBoxSetupWhiteCastleShort);
        CheckBox checkWhiteCastleLong = findViewById(R.id.CheckBoxSetupWhiteCastleLong);
        CheckBox checkBlackCastleShort = findViewById(R.id.CheckBoxSetupBlackCastleShort);
        CheckBox checkBlackCastleLong = findViewById(R.id.CheckBoxSetupBlackCastleLong);

        final int turn = radioTurnWhite.isChecked() ? 1 : 0;

        jni.setTurn(turn);

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

        ///////////////////////////////////////////////////////////////////////////////////////

        jni.setCastlingsEPAnd50(
                checkWhiteCastleLong.isChecked() ? 1 : 0,
                checkWhiteCastleShort.isChecked() ? 1 : 0,
                checkBlackCastleLong.isChecked() ? 1 : 0,
                checkBlackCastleShort.isChecked() ? 1 : 0,
                iEP, 0);
        jni.commitBoard();

        if (jni.isLegalPosition() == 0) {
            new AlertDialog.Builder(this)
                    .setTitle("Use illegal position?")
                    .setPositiveButton(getString(R.string.alert_yes),
                            new DialogInterface.OnClickListener() {
                                public void onClick(DialogInterface dialog, int whichButton) {
                                    dialog.dismiss();

                                    commitFEN();
                                    SetupActivity.this.finish();
                                }
                            })
                    .setNegativeButton(getString(R.string.alert_no), new DialogInterface.OnClickListener() {
                        public void onClick(DialogInterface dialog, int whichButton) {
                            dialog.dismiss();
                        }
                    })
                    .show();
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
    }

    @Override
    public void afterCreate() {
        Log.d(TAG, " afterCreate");

        jni = JNI.getInstance();
        chessBoardView = findViewById(R.id.includeboard);

        myDragListener = new MyDragListener();
        myTouchListener = new MyTouchListener();

        for (int i = 0; i < 64; i++) {
            ChessSquareView csv = new ChessSquareView(this, i);
            csv.setOnDragListener(myDragListener);
            chessBoardView.addView(csv);
        }

        gameApi.addListener(this);
    }

    public void buildPieces() {

        for (int i = 0; i < 5; i++) {
            ChessSquareView squareForBlack = new ChessSquareView(this, i);
            squareForBlack.setOnDragListener(myDragListener);
            blackPieces.addView(squareForBlack);

            ChessSquareView squareForWhite = new ChessSquareView(this, i);
            squareForWhite.setOnDragListener(myDragListener);
            whitePieces.addView(squareForWhite);


            ChessPieceView blackPieceView = new ChessPieceView(this, BoardConstants.BLACK, i, i);
            blackPieceView.setOnTouchListener(myTouchListener);
            blackPieces.addView(blackPieceView);

            ChessPieceView whitePieceView = new ChessPieceView(this, BoardConstants.WHITE, i, i);
            whitePieceView.setOnTouchListener(myTouchListener);
            whitePieces.addView(whitePieceView);
        }
    }

    public void resetBoard() {
        jni.reset();

        jni.putPiece(BoardConstants.e1, BoardConstants.KING, BoardConstants.WHITE);
        jni.putPiece(BoardConstants.e8, BoardConstants.KING, BoardConstants.BLACK);

        rebuildBoard();
    }

    // p = _jni.pieceAt(t, _selectedPosition);
    public void addPiece(final int pos, final int piece, final int turn) {
        jni.putPiece(pos, piece, turn);
    }

    public void removePiece(final int pos) {
        if (jni.pieceAt(BoardConstants.WHITE, pos) != BoardConstants.FIELD) {
            jni.removePiece(BoardConstants.WHITE, pos);
        }
        if (jni.pieceAt(BoardConstants.BLACK, pos) != BoardConstants.FIELD) {
            jni.removePiece(BoardConstants.BLACK, pos);
        }
    }

    protected void commitFEN() {
        Log.d(TAG, "commitFEN");

        SharedPreferences.Editor editor = this.getPrefs().edit();
        editor.putString("FEN", jni.toFEN());
        editor.putString("game_pgn", "");
        editor.putInt("boardNum", 0);
        editor.putLong("game_id", 0);
        editor.commit();
    }

    protected class MyDragListener extends ChessBoardActivity.MyDragListener {

        @Override
        public boolean onDrag(View view, DragEvent event) {
            int action = event.getAction();
            if (view instanceof ChessSquareView) {
                final int pos = ((ChessSquareView) view).getPos();

                switch (event.getAction()) {
                    case DragEvent.ACTION_DROP:
                        Log.i(TAG, "onDrag DROP " + pos);
                        // Dropped, reassign View to ViewGroup
                        View fromView = (View) event.getLocalState();
                        if (fromView instanceof ChessPieceView) {
                            final ChessPieceView pieceView = (ChessPieceView) fromView;
                            final int toPos = ((ChessSquareView) view).getPos();
                            final int fromPos = pieceView.getPos();

                            boolean droppedOnKing = false;

                            if (view.getParent() instanceof ChessBoardView) {
                                final int count = chessBoardView.getChildCount();
                                for (int i = 0; i < count; i++) {
                                    final View child = chessBoardView.getChildAt(i);
                                    if (child instanceof ChessPieceView) {
                                        if (((ChessPieceView) child).getPos() == toPos) {
                                            if (((ChessPieceView) child).getPiece() == BoardConstants.KING) {
                                                droppedOnKing = true;
                                            } else {
                                                chessBoardView.removeView(child);
                                            }
                                        }
                                    }
                                }
                            }

                            if (!droppedOnKing) {
                                removePiece(fromPos);

                                if (view.getParent() instanceof ChessBoardView) {
                                    removePiece(toPos);

                                    addPiece(toPos, pieceView.getPiece(), pieceView.getColor());

                                    if (pieceView.getParent() instanceof ChessPiecesStackView) {
                                        ChessPieceView droppedPiece = new ChessPieceView(SetupActivity.this, pieceView.getColor(), pieceView.getPiece(), toPos);
                                        droppedPiece.setOnTouchListener(myTouchListener);
                                        chessBoardView.addView(droppedPiece);
                                    } else {
                                        pieceView.setPos(toPos);
                                        chessBoardView.layoutChild(pieceView);
                                    }
                                } else {
                                    chessBoardView.removeView(pieceView);
                                }
                            }

                            pieceView.setVisibility(View.VISIBLE);
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

    protected class MyTouchListener extends ChessBoardActivity.MyTouchListener {
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
