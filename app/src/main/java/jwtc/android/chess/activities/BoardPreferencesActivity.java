package jwtc.android.chess.activities;

import android.content.SharedPreferences;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.widget.AdapterView;
import android.widget.CheckBox;
import android.widget.CompoundButton;
import android.widget.Spinner;

import java.util.Arrays;

import jwtc.android.chess.R;
import jwtc.android.chess.constants.ColorSchemes;
import jwtc.android.chess.constants.PieceSets;
import jwtc.android.chess.services.GameApi;

public class BoardPreferencesActivity extends ChessBoardActivity {
    private static final String TAG = "BoardPreferences";
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

        spinnerPieceSet.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(AdapterView<?> parent, View view, int pos, long id) {
                PieceSets.selectedSet = pos;
                rebuildBoard();
            }

            @Override
            public void onNothingSelected(AdapterView<?> parent) {}

        });

        spinnerColorScheme.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(AdapterView<?> parent, View view, int pos, long id) {
                ColorSchemes.selectedColorScheme = pos;
                chessBoardView.invalidateSquares();
            }

            @Override
            public void onNothingSelected(AdapterView<?> parent) {}

        });

        spinnerTileSet.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(AdapterView<?> parent, View view, int pos, long id) {
                updateTileSet(getSelectedTileSet(pos));
                chessBoardView.invalidateSquares();
            }

            @Override
            public void onNothingSelected(AdapterView<?> parent) {}

        });

        checkBoxCoordinates.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
           public void onCheckedChanged(CompoundButton buttonView, boolean isChecked) {
               ColorSchemes.showCoords = isChecked;
               chessBoardView.invalidateSquares();
           }
       });

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

        int pos = Arrays.asList(getTileItems()).indexOf(prefs.getString("tileSet", ""));
        if (pos >= 0) {
            spinnerTileSet.setSelection(pos);
        }

        rebuildBoard();
    }

    @Override
    protected void onPause() {
        super.onPause();

        SharedPreferences.Editor editor = this.getPrefs().edit();

        editor.putString("pieceset", "" + spinnerPieceSet.getSelectedItemPosition());
        editor.putString("colorscheme", "" + spinnerColorScheme.getSelectedItemPosition());
        editor.putString("tileSet", getSelectedTileSet(spinnerTileSet.getSelectedItemPosition()));
        editor.putBoolean("showCoords", checkBoxCoordinates.isChecked());

        editor.commit();
    }

    @Override
    public boolean requestMove(int from, int to) {
        return false;
    }

    protected String getSelectedTileSet(int pos) {
        return getTileItems()[pos];
    }

    protected String[] getTileItems() {
        return getResources().getStringArray(R.array.tileKeyArray);
    }
}
