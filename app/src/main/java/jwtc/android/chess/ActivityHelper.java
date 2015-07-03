package jwtc.android.chess;


import android.app.Activity;
import android.content.SharedPreferences;
import android.content.pm.ActivityInfo;
import android.content.res.Configuration;
import android.os.PowerManager;
import android.util.Log;
import android.view.ViewConfiguration;
import android.view.Window;
import android.view.WindowManager;

import java.lang.reflect.Field;

public class ActivityHelper {


    // @see http://stackoverflow.com/questions/9739498/android-action-bar-not-showing-overflow
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

    public static SharedPreferences getPrefs(Activity activity){

        return activity.getSharedPreferences("ChessPlayer", Activity.MODE_PRIVATE);
    }

    public static void prepareWindowSettings(Activity activity){

        SharedPreferences prefs = getPrefs(activity);
        if(prefs.getBoolean("fullScreen", true)){
            activity.getWindow().setFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN, WindowManager.LayoutParams.FLAG_FULLSCREEN);

        }

        if(activity.getResources().getBoolean(R.bool.portraitOnly)){
            activity.setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_PORTRAIT);
        }

        int configOrientation = activity.getResources().getConfiguration().orientation;
        if(configOrientation == Configuration.ORIENTATION_LANDSCAPE) {
            activity.requestWindowFeature(Window.FEATURE_NO_TITLE);
        } else {
            activity.getActionBar().setDisplayHomeAsUpEnabled(true);
        }
    }

    public static PowerManager.WakeLock getWakeLock(Activity activity){
        PowerManager pm = (PowerManager) activity.getSystemService(Activity.POWER_SERVICE);
        return pm.newWakeLock(PowerManager.SCREEN_DIM_WAKE_LOCK, "DoNotDimScreen");

    }

}
