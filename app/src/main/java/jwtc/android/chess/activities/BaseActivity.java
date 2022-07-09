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

        if (prefs.getBoolean("fullScreen", true)) {
            getWindow().setFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN, WindowManager.LayoutParams.FLAG_FULLSCREEN);
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


    /*
     public static void makeActionOverflowMenuShown(Activity activity) {
        //devices with hardware menu button (e.g. Samsung Note) don't show action overflow menu
        try {
            ViewConfiguration config = ViewConfiguration.get(activity);
            Field menuKeyField = ViewConfiguration.class.getDeclaredField("sHasPermanentMenuKey");
            if (menuKeyField != null) {
                menuKeyField.setAccessible(true);
                menuKeyField.setBoolean(config, false);
            }
        } catch (Exception e) {
            Log.d("main", e.getLocalizedMessage());
        }
    }

    // @see http://stackoverflow.com/questions/9739498/android-action-bar-not-showing-overflow
    public void makeActionOverflowMenuShown() {
        makeActionOverflowMenuShown(this);
    }
     */
}
