package jwtc.android.chess.services;

import android.content.Context;
import android.speech.tts.TextToSpeech;
import android.util.Log;

import java.util.regex.Matcher;

import jwtc.chess.Move;

public class TextToSpeechApi extends TextToSpeech {
    private static final String TAG = "TextToSpeechApi";
    public TextToSpeechApi(Context context, OnInitListener listener) {
        super(context, listener);
    }

    public void setDefaults() {
        setSpeechRate(0.80F);
        setPitch(0.85F);
    }

    public void moveToSpeech(String sMoveSpeech) {
        speak(sMoveSpeech, TextToSpeech.QUEUE_FLUSH, null, sMoveSpeech);
    }

    public void defaultSpeak(String text) {
        Log.d(TAG, "say " + text);
        speak(text, TextToSpeech.QUEUE_FLUSH, null, text);
    }

}
