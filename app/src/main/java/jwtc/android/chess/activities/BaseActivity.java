package jwtc.android.chess.activities;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Build;
import android.os.Bundle;
import android.os.Vibrator;
import android.util.Log;
import android.view.Gravity;
import android.view.WindowManager;
import android.view.accessibility.AccessibilityManager;
import android.widget.Toast;
import android.window.OnBackInvokedCallback;
import android.window.OnBackInvokedDispatcher;

import androidx.activity.OnBackPressedCallback;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.view.WindowInsetsControllerCompat;

import jwtc.android.chess.HtmlActivity;
import jwtc.android.chess.R;


public class BaseActivity extends AppCompatActivity {
    private static final String TAG = "BaseActivity";
    private OnBackInvokedCallback backCallback;
    private AccessibilityManager am;


    protected float fVolume = 1.0f;

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        this.am = (AccessibilityManager) getSystemService(Context.ACCESSIBILITY_SERVICE);

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            backCallback = new OnBackInvokedCallback() {
                @Override
                public void onBackInvoked() {
                    showExitConfirmationDialog();
                }
            };
            getOnBackInvokedDispatcher().registerOnBackInvokedCallback(
                    OnBackInvokedDispatcher.PRIORITY_DEFAULT,
                    backCallback
            );
        } else {
            getOnBackPressedDispatcher().addCallback(this, new OnBackPressedCallback(true) {
                @Override
                public void handleOnBackPressed() {
                    showExitConfirmationDialog();
                }
            });
        }
    }

    @Override
    protected void onResume() {

        SharedPreferences prefs = getPrefs();

        if (prefs.getBoolean("wakeLock", true)) {
            getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        }

        if (prefs.getBoolean("fullScreen", false)) {
            getWindow().setFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN, WindowManager.LayoutParams.FLAG_FULLSCREEN);
        }

        super.onResume();
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU && backCallback != null) {
            getOnBackInvokedDispatcher().unregisterOnBackInvokedCallback(backCallback);
        }
    }

    public void showExitConfirmationDialog() {
        if (needExitConfirmationDialog()) {
            new AlertDialog.Builder(this)
                    .setTitle(getString(R.string.menu_abort))
                    .setPositiveButton(getString(R.string.alert_yes), (dialog, which) -> finish())
                    .setNegativeButton(getString(R.string.alert_no), null)
                    .show();
        } else {
            finish();
        }
    }


    public boolean isScreenReaderOn() {
        return am.isEnabled() && am.isTouchExplorationEnabled();
    }

    public boolean needExitConfirmationDialog() {
        return false;
    }

    public SharedPreferences getPrefs() {
        return getSharedPreferences("ChessPlayer", Activity.MODE_PRIVATE);
    }

    public void doToast(final String text) {
        Toast t = Toast.makeText(this, text, Toast.LENGTH_LONG);
        t.setGravity(Gravity.BOTTOM, 0, 0);
        t.show();
    }

    public void doToastShort(final String text) {
        Toast t = Toast.makeText(this, text, Toast.LENGTH_SHORT);
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
