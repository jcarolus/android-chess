package jwtc.android.chess.puzzle;

import android.os.Bundle;

import jwtc.android.chess.MyPreferenceActivity;
import jwtc.android.chess.R;

public class puzzlePrefs extends MyPreferenceActivity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        addPreferencesFromResource(R.xml.puzzleprefs);

    }
}

