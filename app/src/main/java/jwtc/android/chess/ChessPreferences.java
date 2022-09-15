package jwtc.android.chess;

import android.content.Context;
import android.content.SharedPreferences;
import android.content.res.Configuration;
import android.content.res.Resources;
import android.os.Build;
import android.os.Bundle;
import android.preference.PreferenceManager;
import android.util.DisplayMetrics;

import java.util.Locale;

import jwtc.android.chess.activities.BasePreferenceActivity;


public class ChessPreferences extends BasePreferenceActivity implements SharedPreferences.OnSharedPreferenceChangeListener {
    private static String TAG = "ChessPreferences";


    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);


        SharedPreferences getData = getSharedPreferences("ChessPlayer", Context.MODE_PRIVATE);
        String myLanguage = getData.getString("localelanguage", "");

        Locale current = getResources().getConfiguration().locale;
        String language = current.getLanguage();
        if (myLanguage.equals("")) {    // localelanguage not used yet? then use device default locale
            myLanguage = language;
        }

        Locale locale = new Locale(myLanguage);    // myLanguage is current language
        Locale.setDefault(locale);

        Resources resources = getResources();
        Configuration configuration = resources.getConfiguration();
        DisplayMetrics displayMetrics = resources.getDisplayMetrics();
        configuration.setLocale(locale);

        if (Build.VERSION.SDK_INT > Build.VERSION_CODES.N){
            getApplicationContext().createConfigurationContext(configuration);
        } else {
            resources.updateConfiguration(configuration, displayMetrics);
        }


        PreferenceManager pm = getPreferenceManager();
        pm.setSharedPreferencesName("ChessPlayer");

        final SharedPreferences prefs = pm.getSharedPreferences();

        addPreferencesFromResource(R.xml.globalprefs);

        prefs.registerOnSharedPreferenceChangeListener(this);

        setResult(RESULT_CANCELED);
    }


    @Override
    public void onSharedPreferenceChanged(SharedPreferences sharedPreferences, String s) {

        if (s.equals("localelanguage") || s.equals("nightMode")) {
            setResult(RESULT_OK);
            finish();
        }
    }

    @Override
    protected void onSaveInstanceState(Bundle outState) {


        super.onSaveInstanceState(outState);
    }
}

