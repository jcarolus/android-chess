package jwtc.android.chess.services;

import android.content.Context;
import android.content.SharedPreferences;
import android.os.LocaleList;
import android.speech.tts.TextToSpeech;
import android.util.Log;

import java.util.Locale;

public class TextToSpeechApi {
    private static final String TAG = "TextToSpeechApi";
    private final TextToSpeech textToSpeech;
    private final LocaleList appLocales;
    protected float speechRate = 1.0f;
    protected float speechPitch = 1.0f;

    public TextToSpeechApi(Context context, TextToSpeech.OnInitListener listener) {
        textToSpeech = new TextToSpeech(context, listener);
        appLocales = context.getResources().getConfiguration().getLocales();
    }

    public int setDefaults(SharedPreferences prefs) {
        setSpeechRate(prefs.getFloat("speechRate", 1.0F));
        setSpeechPitch(prefs.getFloat("speechPitch", 1.0F));

        Locale locale = findBestSupportedLocale();
        Log.i(TAG, "setDefaults locale=" + locale);
        return textToSpeech.setLanguage(locale);
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

    private Locale findBestSupportedLocale() {
        for (int i = 0; i < appLocales.size(); i++) {
            Locale candidate = appLocales.get(i);
            if (isLocaleSupported(candidate)) {
                return candidate;
            }

            Locale languageOnly = new Locale(candidate.getLanguage());
            if (isLocaleSupported(languageOnly)) {
                return languageOnly;
            }
        }

        return Locale.US;
    }

    private boolean isLocaleSupported(Locale locale) {
        int status = textToSpeech.isLanguageAvailable(locale);
        return status == TextToSpeech.LANG_AVAILABLE
                || status == TextToSpeech.LANG_COUNTRY_AVAILABLE
                || status == TextToSpeech.LANG_COUNTRY_VAR_AVAILABLE;
    }
}
