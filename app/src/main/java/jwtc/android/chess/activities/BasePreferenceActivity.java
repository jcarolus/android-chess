package jwtc.android.chess.activities;

import android.os.Bundle;
import android.preference.PreferenceActivity;
import android.preference.PreferenceManager;
import android.view.View;

import jwtc.android.chess.helpers.ActivityHelper;

public class BasePreferenceActivity extends PreferenceActivity {
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        PreferenceManager pm = getPreferenceManager();
        pm.setSharedPreferencesName("ChessPlayer");

        View rootView = findViewById(android.R.id.content);
        ActivityHelper.fixPaddings(this, rootView);
    }

}
