package jwtc.android.chess.activities;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.VibrationEffect;
import android.os.Vibrator;
import android.util.Log;
import android.view.Gravity;
import android.view.WindowManager;
import android.view.accessibility.AccessibilityManager;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.io.Reader;
import java.nio.charset.StandardCharsets;

import jwtc.android.chess.HtmlActivity;
import jwtc.android.chess.R;


public class BaseActivity extends AppCompatActivity {
    private static final String TAG = "BaseActivity";
    private AccessibilityManager am;

    protected Vibrator vibrator;

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        this.am = (AccessibilityManager) getSystemService(Context.ACCESSIBILITY_SERVICE);
        vibrator = (Vibrator) getSystemService(Context.VIBRATOR_SERVICE);
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


    public void vibrateSequence(int seq) {
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
            vibrator.vibrate(pattern, -1);
        } catch (Exception e) {
            Log.e(TAG, "vibrator process error", e);
        }

    }

    public void vibrate(long ms) {
        try {
            if (vibrator != null && vibrator.hasVibrator()) {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    vibrator.vibrate(VibrationEffect.createOneShot(ms, VibrationEffect.DEFAULT_AMPLITUDE));
                } else {
                    vibrator.vibrate(ms);
                }
            }
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

    public void startIntentForSaveDocument(String mimeType, String fileName, int resultCode) {
        Log.d(TAG, "start save document " + mimeType + ", " + fileName + " " + resultCode);
        Intent intent = new Intent(Intent.ACTION_CREATE_DOCUMENT);
        intent.addCategory(Intent.CATEGORY_OPENABLE);
        intent.setType(mimeType);
        intent.putExtra(Intent.EXTRA_TITLE, fileName);

        startActivityForResult(intent, resultCode);
    }

    public boolean saveToFile(Uri uri, String contents) {
        Log.d(TAG, "Saving to file " + uri.toString() + " " + contents.length());
        try {
            OutputStream fos = getContentResolver().openOutputStream(uri);
            if (fos != null) {
                fos.write(contents.getBytes());
                fos.flush();
                fos.close();
                return true;
            }
        } catch (Exception ex) {
            Log.d(TAG, "exception while writing to file " + uri + " " + ex.getMessage());
        }
        return false;
    }

    public String readInputStream(Uri uri, int maxChars) {
        Log.d(TAG, "readInputString " + uri);
        try {
            String s = "";
            InputStream is = getContentResolver().openInputStream(uri);

            try (Reader reader = new InputStreamReader(is, StandardCharsets.UTF_8)) {
                StringBuilder sb = new StringBuilder();
                char[] buffer = new char[4096];
                int total = 0;
                int read;

                while ((read = reader.read(buffer)) != -1) {
                    total += read;
                    if (total > maxChars) {
                        return null;
                    }
                    sb.append(buffer, 0, read);
                }

                is.close();
                return sb.toString();
            }
        } catch (Exception e) {
            Log.d(TAG, "Could not read from uri " + uri.toString());
        }
        return null;
    }
}
