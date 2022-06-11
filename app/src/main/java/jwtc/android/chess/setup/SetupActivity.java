package jwtc.android.chess.setup;

import android.content.DialogInterface;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.widget.ArrayAdapter;
import android.widget.CheckBox;
import android.widget.RadioButton;
import android.widget.Spinner;
import android.app.AlertDialog;

import jwtc.android.chess.R;
import jwtc.android.chess.activities.ChessBoardActivity;
import jwtc.android.chess.views.ChessPieceView;
import jwtc.android.chess.views.ChessPiecesStackView;
import jwtc.chess.board.BoardConstants;

public class SetupActivity extends ChessBoardActivity {

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

        buildPieces();
    }

    @Override
    protected void onResume() {
        SharedPreferences prefs = getPrefs();

        resetBoard();

        super.onResume();
    }

    @Override
    protected void onPause() {
        super.onPause();

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

    public void buildPieces() {

        for (int i = 0; i < 5; i++) {
            ChessPieceView blackPieceView = new ChessPieceView(this, BoardConstants.BLACK, i, i);
            blackPieces.addView(blackPieceView);

            ChessPieceView whitePieceView = new ChessPieceView(this, BoardConstants.WHITE, i, i);
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
    public void addPiece() {
        // jni.putPiece(index, p, t);
    }

    public void removePiece() {
        // jni.removePiece(t, _selectedPosition);
    }


    protected void commitFEN() {
        SharedPreferences.Editor editor = this.getPrefs().edit();
        editor.putString("FEN", jni.toFEN());
        editor.putString("game_pgn", "");
        editor.putInt("boardNum", 0);
        editor.putLong("game_id", 0);
        editor.commit();
    }
}
