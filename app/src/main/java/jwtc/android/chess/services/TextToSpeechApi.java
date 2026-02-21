package jwtc.android.chess.services;

import android.content.Context;
import android.content.SharedPreferences;
import android.content.res.Configuration;
import android.content.res.Resources;
import android.os.LocaleList;
import android.speech.tts.TextToSpeech;
import android.speech.tts.Voice;
import android.util.Log;

import androidx.annotation.Nullable;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;

public class TextToSpeechApi implements TextToSpeech.OnInitListener {
    private static final String TAG = "TextToSpeechApi";
    private final Context context;
    private TextToSpeech textToSpeech;
    private SharedPreferences prefs;
    private final InitStateListener initStateListener;
    private final LocaleList appLocales;
    private Locale selectedLocale = Locale.US;
    protected float speechRate = 1.0f;
    protected float speechPitch = 1.0f;
    private boolean enabled = true;
    private boolean initializing = false;
    private boolean ready = false;
    private int lastInitStatus = TextToSpeech.ERROR;
    private int lastLanguageStatus = TextToSpeech.LANG_NOT_SUPPORTED;

    public interface InitStateListener {
        void onTtsInitStateChanged(int initStatus, int languageStatus);
    }

    public TextToSpeechApi(Context context, @Nullable InitStateListener initStateListener) {
        this.context = context;
        this.initStateListener = initStateListener;
        textToSpeech = null;
        appLocales = context.getResources().getConfiguration().getLocales();
    }

    public int setDefaults() {
        if (textToSpeech == null) {
            ready = false;
            return TextToSpeech.ERROR;
        }
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
        ready = result != TextToSpeech.LANG_MISSING_DATA && result != TextToSpeech.LANG_NOT_SUPPORTED;

        return result;
    }

    public void saveDefaults(SharedPreferences.Editor editor) {
        editor.putFloat("speechRate", speechRate);
        editor.putFloat("speechPitch", speechPitch);
    }

    public void setSpeechRate(float speechRate) {
        this.speechRate = speechRate;
        if (textToSpeech != null) {
            textToSpeech.setSpeechRate(speechRate);
        }
    }

    public void setSpeechPitch(float speechPitch) {
        this.speechPitch = speechPitch;
        if (textToSpeech != null) {
            textToSpeech.setPitch(speechPitch);
        }
    }

    public void moveToSpeech(String sMoveSpeech) {
        if (!enabled || !ready) {
            return;
        }
        if (textToSpeech == null) {
            return;
        }
        String id = "utt-" + System.nanoTime();
        this.textToSpeech.speak(sMoveSpeech, TextToSpeech.QUEUE_FLUSH, null, id);
    }

    public void queueSpeech(String sSpeech) {
        if (!enabled || !ready) {
            return;
        }
        if (textToSpeech == null) {
            return;
        }
        String id = "utt-" + System.nanoTime();
        this.textToSpeech.speak(sSpeech, TextToSpeech.QUEUE_ADD, null, id);
    }

    public List<Voice> getSupportedVoices() {
        List<Voice> filteredVoices = new ArrayList<>();
        if (textToSpeech == null) {
            return filteredVoices;
        }
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
        if (textToSpeech == null) {
            return false;
        }
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
        if (textToSpeech == null) {
            return null;
        }
        Voice voice = textToSpeech.getVoice();
        return voice == null ? null : voice.getName();
    }

    public void useSystemDefaultVoice() {
        if (textToSpeech == null) {
            return;
        }
        selectedLocale = findBestSupportedLocale();
        textToSpeech.setLanguage(selectedLocale);
    }

    public List<TextToSpeech.EngineInfo> getInstalledEngines() {
        if (textToSpeech == null) {
            return new ArrayList<>();
        }
        return new ArrayList<>(textToSpeech.getEngines());
    }

    public void shutdown() {
        if (textToSpeech != null) {
            textToSpeech.stop();
            textToSpeech.shutdown();
            textToSpeech = null;
        }
        initializing = false;
        ready = false;
    }

    public void setEnabled(boolean enabled, SharedPreferences prefs) {
        this.enabled = enabled;
        if (enabled) {
            this.prefs = prefs;
            ensureInitialized();
        } else if (textToSpeech != null) {
            textToSpeech.stop();
            textToSpeech.shutdown();
            textToSpeech = null;
            initializing = false;
            ready = false;
        }
    }

    public boolean isEnabled() {
        return enabled;
    }

    public boolean isReady() {
        return ready;
    }

    public int getLastInitStatus() {
        return lastInitStatus;
    }

    public int getLastLanguageStatus() {
        return lastLanguageStatus;
    }

    @Override
    public void onInit(int status) {
        if (!enabled || textToSpeech == null) {
            initializing = false;
            ready = false;
            return;
        }
        initializing = false;
        lastInitStatus = status;
        if (status == TextToSpeech.SUCCESS) {
            lastLanguageStatus = setDefaults();
            ready = lastLanguageStatus != TextToSpeech.LANG_MISSING_DATA
                && lastLanguageStatus != TextToSpeech.LANG_NOT_SUPPORTED;
        } else {
            lastLanguageStatus = TextToSpeech.LANG_NOT_SUPPORTED;
            ready = false;
        }

        if (initStateListener != null) {
            initStateListener.onTtsInitStateChanged(lastInitStatus, lastLanguageStatus);
        }
    }

    private Locale findBestSupportedLocale() {
        if (textToSpeech == null) {
            return Locale.US;
        }
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
        if (textToSpeech == null) {
            return false;
        }
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

    private void ensureInitialized() {
        if (textToSpeech != null || initializing) {
            return;
        }
        initializing = true;
        String enginePackage = prefs.getString("speechEngine", null);
        if (enginePackage == null || enginePackage.isEmpty()) {
            textToSpeech = new TextToSpeech(context, this);
        } else {
            textToSpeech = new TextToSpeech(context, this, enginePackage);
        }
    }
}
