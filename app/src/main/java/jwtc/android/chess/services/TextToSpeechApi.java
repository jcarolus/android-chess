package jwtc.android.chess.services;

import android.content.Context;
import android.content.SharedPreferences;
import android.speech.tts.TextToSpeech;
import android.util.Log;

import java.util.Locale;

public class TextToSpeechApi {
    private static final String TAG = "TextToSpeechApi";
    private final TextToSpeech textToSpeech;
    public TextToSpeechApi(Context context, TextToSpeech.OnInitListener listener) {
        textToSpeech = new TextToSpeech(context, listener);
    }

    public int setDefaults(SharedPreferences prefs) {
        textToSpeech.setSpeechRate(prefs.getFloat("speechRate", 1.0F));
        textToSpeech.setPitch(prefs.getFloat("speechPitch", 1.0F));
        return textToSpeech.setLanguage(Locale.US);
    }

    public void moveToSpeech(String sMoveSpeech) {
        this.textToSpeech.speak(sMoveSpeech, TextToSpeech.QUEUE_FLUSH, null, sMoveSpeech);
    }
}
