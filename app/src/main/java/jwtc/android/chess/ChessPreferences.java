package jwtc.android.chess;

import android.content.SharedPreferences;
import android.os.Bundle;
import android.preference.PreferenceManager;
import android.util.Log;

import jwtc.android.chess.activities.BasePreferenceActivity;


public class ChessPreferences extends BasePreferenceActivity implements SharedPreferences.OnSharedPreferenceChangeListener {
    private static String TAG = "ChessPreferences";


    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        PreferenceManager pm = getPreferenceManager();
        pm.setSharedPreferencesName("ChessPlayer");

        final SharedPreferences prefs = pm.getSharedPreferences();

        addPreferencesFromResource(R.xml.globalprefs);

        prefs.registerOnSharedPreferenceChangeListener(this);

        setResult(0);
    }


    @Override
    public void onSharedPreferenceChanged(SharedPreferences sharedPreferences, String s) {

        if (s.equals("localelanguage")) {
            Log.i(TAG, s);
            setResult(1);
        }
    }
}

