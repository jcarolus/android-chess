package jwtc.android.chess.services;

import android.content.Context;
import android.content.SharedPreferences;
import android.speech.tts.TextToSpeech;
import android.util.Log;

import java.util.Locale;

public class TextToSpeechApi {
    private static final String TAG = "TextToSpeechApi";
    private final TextToSpeech textToSpeech;
    protected float speechRate = 1.0f;
    protected float speechPitch = 1.0f;

    public TextToSpeechApi(Context context, TextToSpeech.OnInitListener listener) {
        textToSpeech = new TextToSpeech(context, listener);
    }

    public int setDefaults(SharedPreferences prefs) {
        setSpeechRate(prefs.getFloat("speechRate", 1.0F));
        setSpeechPitch(prefs.getFloat("speechPitch", 1.0F));
        return textToSpeech.setLanguage(Locale.US);
    }

    public void saveDefaults(SharedPreferences.Editor editor) {
        editor.putFloat("speechRate", speechRate);
        editor.putFloat("speechPitch", speechPitch);
    }

    public void setSpeechRate(float speechRate) {
        this.speechRate = speechRate;
        textToSpeech.setSpeechRate(speechRate);
    }

    public void setSpeechPitch(float speechPitch) {
        this.speechPitch = speechPitch;
        textToSpeech.setPitch(speechPitch);
    }

    public void moveToSpeech(String sMoveSpeech) {
        this.textToSpeech.speak(sMoveSpeech, TextToSpeech.QUEUE_FLUSH, null, sMoveSpeech);
    }
}
