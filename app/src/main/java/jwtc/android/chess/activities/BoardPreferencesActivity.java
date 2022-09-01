package jwtc.android.chess.activities;

import android.content.SharedPreferences;
import android.os.Bundle;
import android.widget.CheckBox;
import android.widget.Spinner;

import jwtc.android.chess.R;
import jwtc.android.chess.services.GameApi;

public class BoardPreferencesActivity extends ChessBoardActivity {
    private static final String TAG = "SetupActivity";
    private CheckBox checkBoxCoordinates;
    private Spinner spinnerPieceSet, spinnerColorScheme, spinnerTileSet;

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        setContentView(R.layout.chessboard_prefs);

        spinnerPieceSet = findViewById(R.id.SpinnerPieceSet);
        spinnerColorScheme = findViewById(R.id.SpinnerColorScheme);
        spinnerTileSet = findViewById(R.id.SpinnerTileSet);
        checkBoxCoordinates = findViewById(R.id.CheckBoxCoordinates);

        gameApi = new GameApi();

        afterCreate();
    }

    @Override
    protected void onResume() {
        super.onResume();

        SharedPreferences prefs = getPrefs();

        jni.newGame();

        checkBoxCoordinates.setChecked(prefs.getBoolean("showCoords", false));

        spinnerPieceSet.setSelection(Integer.parseInt(prefs.getString("pieceset", "0")));
        spinnerColorScheme.setSelection(Integer.parseInt(prefs.getString("colorscheme", "0")));

        rebuildBoard();
    }

    @Override
    protected void onPause() {
        super.onPause();

        SharedPreferences.Editor editor = this.getPrefs().edit();

        editor.commit();
    }

    @Override
    public boolean requestMove(int from, int to) {
        return false;
    }
}
