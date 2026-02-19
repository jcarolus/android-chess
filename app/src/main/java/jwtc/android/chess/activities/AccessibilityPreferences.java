package jwtc.android.chess.activities;

import android.content.ActivityNotFoundException;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.content.res.Resources;
import android.speech.tts.TextToSpeech;
import android.speech.tts.Voice;
import android.widget.AdapterView;
import android.widget.Button;
import android.widget.CheckBox;

import com.google.android.material.button.MaterialButton;
import com.google.android.material.slider.Slider;

import java.util.ArrayList;
import java.util.List;

import jwtc.android.chess.R;
import jwtc.android.chess.helpers.ActivityHelper;
import jwtc.android.chess.services.GameApi;
import jwtc.android.chess.views.FixedDropdownView;

public class AccessibilityPreferences extends ChessBoardActivity {
    private static final String TAG = "AccessibilityPreferences";
    private static final String ACTION_TTS_SETTINGS = "com.android.settings.TTS_SETTINGS";
    private CheckBox checkBoxShowPiecesDescriptions, checkBoxShowAccessibilityDrag;
    private Slider sliderSpeechRate, sliderSpeechPitch, sliderAccessibilityDelay;
    private MaterialButton buttonTtsSettings;
    private FixedDropdownView dropDownSpeechVoice;
    private final ArrayList<String> speechVoiceNames = new ArrayList<>();

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
        buttonTtsSettings = findViewById(R.id.ButtonTtsSettings);
        dropDownSpeechVoice = findViewById(R.id.DropDownSpeechVoice);

        sliderSpeechRate.addOnChangeListener((s, value, fromUser) -> {
            if (textToSpeech == null) {
                return;
            }
            textToSpeech.setSpeechRate(value);
            textToSpeech.moveToSpeech(getPreviewMove());
        });

        sliderSpeechPitch.addOnChangeListener((s, value, fromUser) -> {
            if (textToSpeech == null) {
                return;
            }
            textToSpeech.setSpeechPitch(value);
            textToSpeech.moveToSpeech(getPreviewMove());
        });

        sliderAccessibilityDelay.addOnChangeListener((s, value, fromUser) -> {
            accessibilityDragDwellMs = (int)value;
        });

        buttonTtsSettings.setOnClickListener(v -> openSystemTtsSettings());

        dropDownSpeechVoice.setOnItemClickListener((AdapterView<?> parent, android.view.View view, int position, long id) -> {
            if (position >= 0 && position < speechVoiceNames.size() && textToSpeech != null) {
                textToSpeech.setVoiceByName(speechVoiceNames.get(position));
                textToSpeech.moveToSpeech(getPreviewMove());
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

        checkBoxShowPiecesDescriptions.setChecked(prefs.getBoolean("show_pieces_descriptions", true));

        accessibilityDragDwellMs = prefs.getInt("accessibilityDragDelay", 300);
        sliderSpeechRate.setValue(prefs.getFloat("speechRate", 1.0f));
        sliderSpeechPitch.setValue(prefs.getFloat("speechPitch", 1.0f));
        sliderAccessibilityDelay.setValue(accessibilityDragDwellMs);

        checkBoxShowAccessibilityDrag.setChecked(prefs.getBoolean("show_accessibility_drag_toggle", false));
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
        int selectedVoicePosition = dropDownSpeechVoice.getSelectedItemPosition();
        if (selectedVoicePosition >= 0 && selectedVoicePosition < speechVoiceNames.size()) {
            editor.putString("speechVoice", speechVoiceNames.get(selectedVoicePosition));
        }

        editor.commit();
    }

    @Override
    public void onInit(int status) {
        super.onInit(status);
        if (status == TextToSpeech.SUCCESS) {
            populateSpeechVoiceDropdown();
        }
    }

    @Override
    public boolean requestMove(int from, int to) {
        return false;
    }

    private void populateSpeechVoiceDropdown() {
        speechVoiceNames.clear();

        if (textToSpeech == null) {
            dropDownSpeechVoice.setItems(new ArrayList<>());
            return;
        }

        List<Voice> supportedVoices = textToSpeech.getSupportedVoices();
        ArrayList<String> labels = new ArrayList<>();

        for (Voice voice : supportedVoices) {
            if (voice == null || voice.getLocale() == null) {
                continue;
            }
            speechVoiceNames.add(voice.getName());
            labels.add(voice.getLocale().getDisplayName() + " - " + voice.getName());
        }

        dropDownSpeechVoice.setItems(labels);

        String selectedVoice = getPrefs().getString("speechVoice", textToSpeech.getCurrentVoiceName());
        if (selectedVoice == null) {
            return;
        }

        for (int i = 0; i < speechVoiceNames.size(); i++) {
            if (selectedVoice.equals(speechVoiceNames.get(i))) {
                dropDownSpeechVoice.setSelection(i);
                return;
            }
        }
    }

    private void openSystemTtsSettings() {
        Intent intent = new Intent(ACTION_TTS_SETTINGS);
        try {
            startActivity(intent);
        } catch (ActivityNotFoundException ex) {
            doToast(getString(R.string.tts_settings_not_found));
        }
    }

    private String getPreviewMove() {
        if (textToSpeech == null) {
            return getString(R.string.tts_example_move);
        }
        Resources resources = textToSpeech.getLocalizedResources();
        return resources != null ? resources.getString(R.string.tts_example_move) : getString(R.string.tts_example_move);
    }

}
