package jwtc.android.chess.activities;

import android.app.Activity;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.os.Vibrator;
import android.util.Log;
import android.view.Gravity;
import android.view.WindowManager;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;
import jwtc.android.chess.HtmlActivity;


public class BaseActivity extends AppCompatActivity {
    private static final String TAG = "BaseActivity";
    public static final int NO_RESULT = 0;


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

        if (prefs.getBoolean("fullScreen", true)) {
            getWindow().setFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN, WindowManager.LayoutParams.FLAG_FULLSCREEN);
        }

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

    public void shareString(String s) {
        Intent sendIntent = new Intent();
        sendIntent.setAction(Intent.ACTION_SEND);
        sendIntent.putExtra(Intent.EXTRA_TEXT, s);
        sendIntent.setType("application/x-chess-pgn");

        Intent shareIntent = Intent.createChooser(sendIntent, null);
        startActivity(shareIntent);
    }

    // @TODO
    public void vibration(int seq) {
        try {
            int v1, v2;
            if (seq == 1) {
                v1 = 200;    // increase
                v2 = 500;
            } else {
                v1 = 500;    // decrease
                v2 = 200;
            }
            long[] pattern = {500, v1, 100, v2};
            Vibrator v = (Vibrator) getSystemService(Context.VIBRATOR_SERVICE);
            v.vibrate(pattern, -1);
        } catch (Exception e) {
            Log.e(TAG, "vibrator process error", e);
        }

    }

    public void showHelp(int resource) {
        Intent i = new Intent();
        i.setClass(this, HtmlActivity.class);
        i.putExtra(HtmlActivity.HELP_STRING_RESOURCE, resource);
        startActivity(i);
    }
}
