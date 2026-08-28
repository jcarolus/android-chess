package jwtc.android.chess.activities;

import android.content.SharedPreferences;
import android.content.res.ColorStateList;
import android.graphics.Color;
import android.os.Bundle;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.CheckBox;
import android.widget.LinearLayout;

import androidx.core.graphics.ColorUtils;

import com.google.android.material.button.MaterialButton;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.android.material.slider.Slider;

import jwtc.android.chess.R;
import jwtc.android.chess.constants.ColorSchemes;
import jwtc.android.chess.constants.PieceSets;
import jwtc.android.chess.helpers.ActivityHelper;
import jwtc.android.chess.services.GameApi;
import jwtc.android.chess.views.FixedDropdownView;

public class BoardPreferencesActivity extends ChessBoardActivity {
    private static final String TAG = "BoardPreferences";
    private CheckBox checkBoxCoordinates, checkBoxShowMoves, checkBoxUsePieceAnimation, checkBoxShowCapturedPieces, checkBoxWakeLock, checkBoxFullscreen, checkBoxSound, checkBoxHapticFeedback, checkBoxNightMode;
    private Slider sliderSaturation;
    private FixedDropdownView dropDownPieces, dropDownColorScheme, dropDownTileSet;
    private LinearLayout customColorControls;
    private MaterialButton buttonCustomDarkColor, buttonCustomLightColor;

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
        checkBoxUsePieceAnimation = findViewById(R.id.CheckBoxUsePieceAnimation);
        checkBoxShowCapturedPieces = findViewById(R.id.CheckBoxShowCapturedPieces);
        checkBoxWakeLock = findViewById(R.id.CheckBoxUseWakeLock);
        checkBoxFullscreen = findViewById(R.id.CheckBoxFullscreen);
        checkBoxSound = findViewById(R.id.CheckBoxUseSound);
        checkBoxHapticFeedback = findViewById(R.id.CheckBoxUseHapticFeedback);
        checkBoxNightMode = findViewById(R.id.CheckBoxForceNightMode);
        sliderSaturation = findViewById(R.id.SliderSaturation);
        customColorControls = findViewById(R.id.CustomColorControls);
        buttonCustomDarkColor = findViewById(R.id.ButtonCustomDarkColor);
        buttonCustomLightColor = findViewById(R.id.ButtonCustomLightColor);

        dropDownPieces.setItems(getResources().getStringArray(R.array.piecesetarray));
        dropDownPieces.setOnItemClickListener((parent, view, position, id) -> {
            PieceSets.selectedSet = position;
            rebuildBoard();
        });

        dropDownColorScheme.setItems(getResources().getStringArray(R.array.colorschemes));
        dropDownColorScheme.setOnItemClickListener((parent, view, position, id) -> {
            ColorSchemes.selectedColorScheme = position;
            updateCustomColorControls();
            chessBoardView.invalidateSquares();
        });

        buttonCustomDarkColor.setOnClickListener(view -> showColorPicker(
            R.string.choose_dark_square_color, ColorSchemes.getCustomDarkColor(), true));
        buttonCustomLightColor.setOnClickListener(view -> showColorPicker(
            R.string.choose_light_square_color, ColorSchemes.getCustomLightColor(), false));

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

        gameApi = new GameApi();

        afterCreate();
        View boardAreaLayout = findViewById(R.id.board_area);
        if (boardAreaLayout == null) {
            boardAreaLayout = findViewById(R.id.includeboard);
        }
        initBoardLayoutSizing(findViewById(R.id.LayoutMain), boardAreaLayout, findViewById(R.id.play_controls), null, null);
    }

    @Override
    protected void onResume() {
        super.onResume();

        SharedPreferences prefs = getPrefs();

        jni.newGame();

        checkBoxCoordinates.setChecked(prefs.getBoolean("showCoords", false));
        checkBoxShowMoves.setChecked(prefs.getBoolean("showMoves", true));
        checkBoxUsePieceAnimation.setChecked(prefs.getBoolean(PREF_USE_PIECE_ANIMATION, true));
        checkBoxShowCapturedPieces.setChecked(prefs.getBoolean("showCapturedPieces", true));
        checkBoxWakeLock.setChecked(prefs.getBoolean("wakeLock", false));
        checkBoxFullscreen.setChecked(prefs.getBoolean("fullScreen", false));
        checkBoxSound.setChecked(prefs.getBoolean("moveSounds", false));
        checkBoxHapticFeedback.setChecked(prefs.getBoolean("useHapticFeedback", false));
        checkBoxNightMode.setChecked(prefs.getBoolean("nightMode", false));

        dropDownPieces.setSelection(Integer.parseInt(prefs.getString("pieceset", "0")));
        dropDownColorScheme.setSelection(Integer.parseInt(prefs.getString("colorscheme", "0")));
        dropDownTileSet.setSelection(Integer.parseInt(prefs.getString("squarePattern", "0")));

        sliderSaturation.setValue(prefs.getFloat("squareSaturation", 1.0f));

        updateCustomColorControls();
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
        editor.putBoolean(PREF_USE_PIECE_ANIMATION, checkBoxUsePieceAnimation.isChecked());
        editor.putBoolean("showCapturedPieces", checkBoxShowCapturedPieces.isChecked());
        editor.putBoolean("wakeLock", checkBoxWakeLock.isChecked());
        editor.putBoolean("fullScreen", checkBoxFullscreen.isChecked());
        editor.putBoolean("moveSounds", checkBoxSound.isChecked());
        editor.putBoolean("useHapticFeedback", checkBoxHapticFeedback.isChecked());
        editor.putBoolean("nightMode", checkBoxNightMode.isChecked());
        editor.putFloat("squareSaturation", sliderSaturation.getValue());
        editor.putInt("customDarkSquareColor", ColorSchemes.getCustomDarkColor());
        editor.putInt("customLightSquareColor", ColorSchemes.getCustomLightColor());

        editor.commit();
    }

    private void updateCustomColorControls() {
        boolean customSelected = ColorSchemes.selectedColorScheme == ColorSchemes.CUSTOM_COLOR_SCHEME;
        customColorControls.setVisibility(customSelected ? View.VISIBLE : View.GONE);
        updateColorButton(buttonCustomDarkColor, ColorSchemes.getCustomDarkColor());
        updateColorButton(buttonCustomLightColor, ColorSchemes.getCustomLightColor());
    }

    private void updateColorButton(MaterialButton button, int color) {
        button.setBackgroundTintList(ColorStateList.valueOf(color));
        button.setTextColor(ColorUtils.calculateLuminance(color) > 0.179
            ? Color.BLACK
            : Color.WHITE);
    }

    private void showColorPicker(int titleResource, int initialColor, boolean darkColor) {
        View dialogView = LayoutInflater.from(this).inflate(R.layout.dialog_color_picker, null);
        View colorPreview = dialogView.findViewById(R.id.ColorPreview);
        Slider hueSlider = dialogView.findViewById(R.id.SliderHue);
        Slider saturationSlider = dialogView.findViewById(R.id.SliderColorSaturation);
        Slider lightnessSlider = dialogView.findViewById(R.id.SliderLightness);

        float[] hsl = new float[3];
        ColorUtils.colorToHSL(initialColor, hsl);
        hueSlider.setValue(hsl[0]);
        saturationSlider.setValue(hsl[1]);
        lightnessSlider.setValue(hsl[2]);

        hueSlider.setLabelFormatter(value -> Math.round(value) + "°");
        saturationSlider.setLabelFormatter(value -> Math.round(value * 100) + "%");
        lightnessSlider.setLabelFormatter(value -> Math.round(value * 100) + "%");

        final int[] selectedColor = {ColorUtils.HSLToColor(hsl)};
        colorPreview.setBackgroundColor(selectedColor[0]);

        Slider.OnChangeListener listener = (slider, value, fromUser) -> {
            float[] selectedHsl = {
                hueSlider.getValue(),
                saturationSlider.getValue(),
                lightnessSlider.getValue()
            };
            selectedColor[0] = ColorUtils.HSLToColor(selectedHsl);
            colorPreview.setBackgroundColor(selectedColor[0]);
        };
        hueSlider.addOnChangeListener(listener);
        saturationSlider.addOnChangeListener(listener);
        lightnessSlider.addOnChangeListener(listener);

        new MaterialAlertDialogBuilder(this)
            .setTitle(titleResource)
            .setView(dialogView)
            .setNegativeButton(android.R.string.cancel, null)
            .setPositiveButton(android.R.string.ok, (dialog, which) -> {
                int customDarkColor = darkColor
                    ? selectedColor[0]
                    : ColorSchemes.getCustomDarkColor();
                int customLightColor = darkColor
                    ? ColorSchemes.getCustomLightColor()
                    : selectedColor[0];
                ColorSchemes.setCustomColors(customDarkColor, customLightColor);
                updateCustomColorControls();
                chessBoardView.invalidateSquares();
            })
            .show();
    }

    @Override
    public boolean requestMove(int from, int to) {
        return false;
    }

}
