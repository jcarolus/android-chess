package jwtc.android.chess.services;

import android.content.Context;
import android.content.SharedPreferences;
import android.content.res.Configuration;
import android.content.res.Resources;
import android.os.LocaleList;
import android.speech.tts.TextToSpeech;
import android.speech.tts.Voice;
import android.util.Log;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;

public class TextToSpeechApi {
    private static final String TAG = "TextToSpeechApi";
    private final Context context;
    private final TextToSpeech textToSpeech;
    private final LocaleList appLocales;
    private Locale selectedLocale = Locale.US;
    protected float speechRate = 1.0f;
    protected float speechPitch = 1.0f;

    public TextToSpeechApi(Context context, TextToSpeech.OnInitListener listener) {
        this(context, listener, null);
    }

    public TextToSpeechApi(Context context, TextToSpeech.OnInitListener listener, String enginePackage) {
        this.context = context;
        if (enginePackage == null || enginePackage.isEmpty()) {
            textToSpeech = new TextToSpeech(context, listener);
        } else {
            textToSpeech = new TextToSpeech(context, listener, enginePackage);
        }
        appLocales = context.getResources().getConfiguration().getLocales();
    }

    public int setDefaults(SharedPreferences prefs) {
        setSpeechRate(prefs.getFloat("speechRate", 1.0F));
        setSpeechPitch(prefs.getFloat("speechPitch", 1.0F));

        String defaultEngine = textToSpeech.getDefaultEngine();
        Log.i(TAG, "TTS default engine package: " + defaultEngine);

        for (TextToSpeech.EngineInfo engine : textToSpeech.getEngines()) {
            Log.i(TAG, "Available engine: " + engine.name + " label=" + engine.label);
        }

        selectedLocale = findBestSupportedLocale();
        Log.i(TAG, "setDefaults locale=" + selectedLocale);
        int result = textToSpeech.setLanguage(selectedLocale);

        String selectedVoiceName = prefs.getString("speechVoice", null);
        if (selectedVoiceName != null && !selectedVoiceName.isEmpty() && setVoiceByName(selectedVoiceName)) {
            result = TextToSpeech.SUCCESS;
        }

        return result;
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
        String id = "utt-" + System.nanoTime();
        this.textToSpeech.speak(sMoveSpeech, TextToSpeech.QUEUE_FLUSH, null, id);
    }

    public void queueSpeech(String sSpeech) {
        String id = "utt-" + System.nanoTime();
        this.textToSpeech.speak(sSpeech, TextToSpeech.QUEUE_ADD, null, id);
    }

    public List<Voice> getSupportedVoices() {
        List<Voice> filteredVoices = new ArrayList<>();
        if (textToSpeech.getVoices() == null) {
            return filteredVoices;
        }

        for (Voice voice : textToSpeech.getVoices()) {
            if (voice == null || voice.getLocale() == null) {
                continue;
            }
            if (isLocaleSupported(voice.getLocale()) && isSupportedByAppLocales(voice.getLocale())) {
                filteredVoices.add(voice);
            }
        }

        filteredVoices.sort(Comparator
                .comparing((Voice voice) -> voice.getLocale().toLanguageTag())
                .thenComparing(Voice::getName));
        return filteredVoices;
    }

    public boolean setVoiceByName(String voiceName) {
        if (voiceName == null || textToSpeech.getVoices() == null) {
            return false;
        }

        for (Voice voice : textToSpeech.getVoices()) {
            if (voice != null && voiceName.equals(voice.getName())) {
                int result = textToSpeech.setVoice(voice);
                if (result == TextToSpeech.SUCCESS) {
                    selectedLocale = voice.getLocale();
                    return true;
                }
            }
        }
        return false;
    }

    public String getCurrentVoiceName() {
        Voice voice = textToSpeech.getVoice();
        return voice == null ? null : voice.getName();
    }

    public void useSystemDefaultVoice() {
        selectedLocale = findBestSupportedLocale();
        textToSpeech.setLanguage(selectedLocale);
    }

    public List<TextToSpeech.EngineInfo> getInstalledEngines() {
        return new ArrayList<>(textToSpeech.getEngines());
    }

    public void shutdown() {
        textToSpeech.stop();
        textToSpeech.shutdown();
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

    private boolean isSupportedByAppLocales(Locale locale) {
        String language = locale.getLanguage();
        if (language == null || language.isEmpty()) {
            return false;
        }

        for (int i = 0; i < appLocales.size(); i++) {
            Locale appLocale = appLocales.get(i);
            if (language.equals(appLocale.getLanguage())) {
                return true;
            }
        }
        return false;
    }

    public Resources getLocalizedResources() {
        Configuration configuration = new Configuration(context.getResources().getConfiguration());
        configuration.setLocales(new LocaleList(selectedLocale));
        return context.createConfigurationContext(configuration).getResources();
    }
}
