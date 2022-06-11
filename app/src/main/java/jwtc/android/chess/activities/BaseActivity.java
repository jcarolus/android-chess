package jwtc.android.chess.activities;

import android.app.Activity;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.view.Gravity;
import android.view.WindowManager;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;


public class BaseActivity extends AppCompatActivity {
    private static final String TAG = "BaseActivity";

    protected float fVolume = 1.0f;

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
    }

    @Override
    protected void onResume() {

        SharedPreferences prefs = getPrefs();

        if (prefs.getBoolean("wakeLock", true)) {
            getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        }

        // fVolume = prefs.getBoolean("moveSoundOn", true) ? 1 : 0;

        super.onResume();
    }

    public SharedPreferences getPrefs() {
        return getSharedPreferences("ChessPlayer", Activity.MODE_PRIVATE);
    }

    public void doToast(final String text) {
        Toast t = Toast.makeText(this, text, Toast.LENGTH_LONG);
        t.setGravity(Gravity.BOTTOM, 0, 0);
        t.show();
    }
}
