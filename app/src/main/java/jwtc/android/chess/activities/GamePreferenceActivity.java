package jwtc.android.chess.activities;

import android.os.Bundle;

import jwtc.android.chess.R;

public class GamePreferenceActivity extends BasePreferenceActivity {
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        addPreferencesFromResource(R.xml.game_prefs);

    }
}
