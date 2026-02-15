package jwtc.android.chess.activities;

import android.content.SharedPreferences;
import android.os.Bundle;
import android.widget.CheckBox;

import com.google.android.material.slider.Slider;

import jwtc.android.chess.R;
import jwtc.android.chess.helpers.ActivityHelper;
import jwtc.android.chess.services.GameApi;

public class AccessibilityPreferences extends ChessBoardActivity {
    private static final String TAG = "AccessibilityPreferences";
    private CheckBox checkBoxShowPiecesDescriptions, checkBoxShowAccessibilityDrag;
    private Slider sliderSpeechRate, sliderSpeechPitch, sliderAccessibilityDelay;

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        setContentView(R.layout.accessibility_prefs);

        ActivityHelper.fixPaddings(this, findViewById(R.id.LayoutMain));

        checkBoxShowPiecesDescriptions = findViewById(R.id.CheckBoxShowPiecesDescriptions);
        checkBoxShowAccessibilityDrag = findViewById(R.id.CheckBoxShowAccessibilityDrag);
        sliderSpeechRate = findViewById(R.id.SliderSpeechRate);
        sliderSpeechPitch = findViewById(R.id.SliderSpeechPitch);
        sliderAccessibilityDelay = findViewById(R.id.SliderAccessibilityDragDelay);

        sliderSpeechRate.addOnChangeListener((s, value, fromUser) -> {
            textToSpeech.setSpeechRate(value);
            textToSpeech.moveToSpeech("Bishop takes G7 check");
        });

        sliderSpeechPitch.addOnChangeListener((s, value, fromUser) -> {
            textToSpeech.setSpeechPitch(value);
            textToSpeech.moveToSpeech("Bishop takes G7 check");
        });

        sliderAccessibilityDelay.addOnChangeListener((s, value, fromUser) -> {
            accessibilityDragDwellMs = (int)value;
        });

        gameApi = new GameApi();

        afterCreate();
    }

    @Override
    protected void onResume() {
        super.onResume();

        SharedPreferences prefs = getPrefs();

        jni.newGame();

        checkBoxShowPiecesDescriptions.setChecked(prefs.getBoolean("show_pieces_descriptions", true));

        sliderSpeechRate.setValue(prefs.getFloat("speechRate", 1.0f));
        sliderSpeechPitch.setValue(prefs.getFloat("speechPitch", 1.0f));
        sliderAccessibilityDelay.setValue(accessibilityDragDwellMs);

        checkBoxShowAccessibilityDrag.setChecked(prefs.getBoolean("show_accessibility_drag_toggle", false));
        accessibilityDragDwellMs = prefs.getInt("accessibilityDragDelay", 300);
        useAccessibilityDrag = true;
        applySquareDragListeners();

        rebuildBoard();
    }

    @Override
    protected void onPause() {
        super.onPause();

        SharedPreferences.Editor editor = this.getPrefs().edit();

        editor.putBoolean("show_pieces_descriptions", checkBoxShowPiecesDescriptions.isChecked());
        editor.putFloat("speechRate", sliderSpeechRate.getValue());
        editor.putFloat("speechPitch", sliderSpeechPitch.getValue());
        editor.putBoolean("show_accessibility_drag_toggle", checkBoxShowAccessibilityDrag.isChecked());
        if (!checkBoxShowAccessibilityDrag.isChecked()) {
            editor.putBoolean("useAccessibilityDrag", false);
        }
        editor.putInt("accessibilityDragDelay", (int)sliderAccessibilityDelay.getValue());

        editor.commit();
    }

    @Override
    public boolean requestMove(int from, int to) {
        return false;
    }

}
