package jwtc.android.chess.helpers;

import android.app.Activity;
import android.content.Context;
import android.content.SharedPreferences;
import android.content.res.Configuration;
import android.graphics.Color;
import android.graphics.Rect;
import android.os.Build;
import android.util.Log;
import android.view.View;
import android.view.WindowManager;
import android.view.animation.Animation;
import android.view.animation.ScaleAnimation;

import androidx.appcompat.app.AppCompatDelegate;
import androidx.core.graphics.Insets;
import androidx.core.view.DisplayCutoutCompat;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;
import androidx.core.view.WindowInsetsControllerCompat;

public class ActivityHelper {
    public static void fixPaddings(Activity context, View rootView) {
        ViewCompat.setOnApplyWindowInsetsListener(rootView, (v, insets) -> {

            int nightModeFlags = context.getResources().getConfiguration().uiMode
                    & Configuration.UI_MODE_NIGHT_MASK;

            boolean isDarkMode = (nightModeFlags == Configuration.UI_MODE_NIGHT_YES);

            WindowInsetsControllerCompat controller =
                    new WindowInsetsControllerCompat(context.getWindow(), rootView);

            if (isDarkMode) {
                controller.setAppearanceLightStatusBars(false);
            } else {
                controller.setAppearanceLightStatusBars(true);
            }

            SharedPreferences prefs = context.getSharedPreferences("ChessPlayer", Activity.MODE_PRIVATE);
            boolean fullScreen = prefs.getBoolean("fullScreen", false);

            Insets systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars());
            DisplayCutoutCompat cutout = insets.getDisplayCutout();
            int orientation = context.getResources().getConfiguration().orientation;
            int topInset = fullScreen ? 0 : systemBars.top;
            int leftInset = fullScreen ? 0 : systemBars.left;

            if (cutout != null) {
                if (orientation == Configuration.ORIENTATION_PORTRAIT) {
                    topInset = fullScreen ? cutout.getSafeInsetTop() : Math.max(systemBars.top, cutout.getSafeInsetTop());
                } else if (orientation == Configuration.ORIENTATION_LANDSCAPE){
                    leftInset = fullScreen ? cutout.getSafeInsetLeft() : Math.max(systemBars.left, cutout.getSafeInsetLeft());
                }
            }
            // Apply paddings to avoid cutout (status bar area / camera hole)
            v.setPadding(
                    leftInset,
                    topInset,
                    systemBars.right,
                    systemBars.bottom
            );

            return WindowInsetsCompat.CONSUMED;
        });
    }

    public static void pulseAnimation(View v) {
        ScaleAnimation pulse = new ScaleAnimation(
                1f, 2f,          // fromX, toX
                1f, 2f,          // fromY, toY
                Animation.RELATIVE_TO_SELF, 0.5f, // pivotX (center)
                Animation.RELATIVE_TO_SELF, 0.5f  // pivotY (center)
        );
        pulse.setDuration(300);                     // time for grow phase
        pulse.setRepeatMode(Animation.REVERSE);     // reverse back to original
        pulse.setRepeatCount(1);                    // do the shrink once
        pulse.setFillAfter(false);                  // end at original scale
        pulse.setInterpolator(new android.view.animation.AccelerateDecelerateInterpolator());

        v.startAnimation(pulse);
    }
}
