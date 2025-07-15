package jwtc.android.chess.helpers;

import android.app.Activity;
import android.content.Context;
import android.content.res.Configuration;
import android.graphics.Color;
import android.graphics.Rect;
import android.os.Build;
import android.util.Log;
import android.view.View;

import androidx.appcompat.app.AppCompatDelegate;
import androidx.core.graphics.Insets;
import androidx.core.view.DisplayCutoutCompat;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;
import androidx.core.view.WindowInsetsControllerCompat;

public class ActivityHelper {
    public static void fixPaddings(Activity context, View rootView) {
        ViewCompat.setOnApplyWindowInsetsListener(rootView, (v, insets) -> {
            Insets systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars());
            DisplayCutoutCompat cutout = insets.getDisplayCutout();

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

            int orientation = context.getResources().getConfiguration().orientation;
            int topInset = systemBars.top, leftInset = systemBars.left;

            if (cutout != null) {
                if (orientation == Configuration.ORIENTATION_PORTRAIT) {
                    topInset = Math.max(systemBars.top, cutout.getSafeInsetTop());
                } else if (orientation == Configuration.ORIENTATION_LANDSCAPE){
                    leftInset = Math.max(systemBars.left, cutout.getSafeInsetLeft());
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
}
