package jwtc.android.chess.activities;

import android.content.SharedPreferences;
import android.os.Bundle;
import android.util.Log;
import android.widget.CheckBox;

import com.google.android.material.slider.Slider;

import jwtc.android.chess.R;
import jwtc.android.chess.constants.ColorSchemes;
import jwtc.android.chess.constants.PieceSets;
import jwtc.android.chess.helpers.ActivityHelper;
import jwtc.android.chess.services.GameApi;
import jwtc.android.chess.views.FixedDropdownView;

public class BoardPreferencesActivity extends ChessBoardActivity {
    private static final String TAG = "BoardPreferences";
    private CheckBox checkBoxCoordinates, checkBoxShowMoves, checkBoxWakeLock, checkBoxFullscreen, checkBoxSound, checkBoxNightMode;
    private Slider sliderSaturation, sliderSpeechRate, sliderSpeechPitch;
    private FixedDropdownView dropDownPieces, dropDownColorScheme, dropDownTileSet;

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        setContentView(R.layout.chessboard_prefs);

        ActivityHelper.fixPaddings(this, findViewById(R.id.LayoutMain));

        dropDownPieces = findViewById(R.id.DropdownPieceSet);
        dropDownColorScheme = findViewById(R.id.DropdownColorScheme);
        dropDownTileSet = findViewById(R.id.DropdownTileSet);
        checkBoxCoordinates = findViewById(R.id.CheckBoxCoordinates);
        checkBoxShowMoves = findViewById(R.id.CheckBoxShowMoves);
        checkBoxWakeLock = findViewById(R.id.CheckBoxUseWakeLock);
        checkBoxFullscreen = findViewById(R.id.CheckBoxFullscreen);
        checkBoxSound = findViewById(R.id.CheckBoxUseSound);
        checkBoxNightMode = findViewById(R.id.CheckBoxForceNightMode);
        sliderSaturation = findViewById(R.id.SliderSaturation);
        sliderSpeechRate = findViewById(R.id.SliderSpeechRate);
        sliderSpeechPitch = findViewById(R.id.SliderSpeechPitch);

        dropDownPieces.setItems(getResources().getStringArray(R.array.piecesetarray));
        dropDownPieces.setOnItemClickListener((parent, view, position, id) -> {
            PieceSets.selectedSet = position;
            rebuildBoard();
        });

        dropDownColorScheme.setItems(getResources().getStringArray(R.array.colorschemes));
        dropDownColorScheme.setOnItemClickListener((parent, view, position, id) -> {
            ColorSchemes.selectedColorScheme = position;
            chessBoardView.invalidateSquares();
        });

        dropDownTileSet.setItems(getResources().getStringArray(R.array.tileArray));
        dropDownTileSet.setOnItemClickListener((parent, view, position, id) -> {
            ColorSchemes.selectedPattern = position;
            chessBoardView.invalidateSquares();
        });

        checkBoxCoordinates.setOnCheckedChangeListener((buttonView, isChecked) -> {
            ColorSchemes.showCoords = isChecked;
            chessBoardView.invalidateSquares();
        });

        sliderSaturation.addOnChangeListener((s, value, fromUser) -> {
            ColorSchemes.saturationFactor = value;
            chessBoardView.invalidateSquares();
        });

        sliderSpeechRate.addOnChangeListener((s, value, fromUser) -> {
            textToSpeech.setSpeechRate(value);
            textToSpeech.moveToSpeech("Bishop takes G7 check");
        });

        sliderSpeechPitch.addOnChangeListener((s, value, fromUser) -> {
            textToSpeech.setSpeechPitch(value);
            textToSpeech.moveToSpeech("Bishop takes G7 check");
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
        checkBoxShowMoves.setChecked(prefs.getBoolean("showMoves", true));
        checkBoxWakeLock.setChecked(prefs.getBoolean("wakeLock", false));
        checkBoxFullscreen.setChecked(prefs.getBoolean("fullScreen", false));
        checkBoxSound.setChecked(prefs.getBoolean("moveSounds", false));
        checkBoxNightMode.setChecked(prefs.getBoolean("nightMode", false));

        dropDownPieces.setSelection(Integer.parseInt(prefs.getString("pieceset", "0")));
        dropDownColorScheme.setSelection(Integer.parseInt(prefs.getString("colorscheme", "0")));
        dropDownTileSet.setSelection(Integer.parseInt(prefs.getString("squarePattern", "0")));

        sliderSaturation.setValue(prefs.getFloat("squareSaturation", 1.0f));
        sliderSpeechRate.setValue(prefs.getFloat("speechRate", 1.0f));
        sliderSpeechPitch.setValue(prefs.getFloat("speechPitch", 1.0f));

        rebuildBoard();
    }

    @Override
    protected void onPause() {
        super.onPause();

        SharedPreferences.Editor editor = this.getPrefs().edit();

        Log.d(TAG, "onPause " + dropDownPieces.getSelectedItemPosition());

        editor.putString("pieceset", "" + dropDownPieces.getSelectedItemPosition());
        editor.putString("colorscheme", "" + dropDownColorScheme.getSelectedItemPosition());
        editor.putString("squarePattern", "" + dropDownTileSet.getSelectedItemPosition());
        editor.putBoolean("showCoords", checkBoxCoordinates.isChecked());
        editor.putBoolean("showMoves", checkBoxShowMoves.isChecked());
        editor.putBoolean("wakeLock", checkBoxWakeLock.isChecked());
        editor.putBoolean("fullScreen", checkBoxFullscreen.isChecked());
        editor.putBoolean("moveSounds", checkBoxSound.isChecked());
        editor.putBoolean("nightMode", checkBoxNightMode.isChecked());
        editor.putFloat("squareSaturation", sliderSaturation.getValue());
        editor.putFloat("speechRate", sliderSpeechRate.getValue());
        editor.putFloat("speechPitch", sliderSpeechPitch.getValue());

        editor.commit();
    }

    @Override
    public boolean requestMove(int from, int to) {
        return false;
    }

}
