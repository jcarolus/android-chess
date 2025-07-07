package jwtc.android.chess.helpers;

import android.content.Context;
import android.content.res.Configuration;
import android.graphics.Rect;
import android.view.View;

import androidx.core.graphics.Insets;
import androidx.core.view.DisplayCutoutCompat;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;

public class ActivityHelper {
    public static void fixPaddings(Context context, View rootView) {
        ViewCompat.setOnApplyWindowInsetsListener(rootView, (v, insets) -> {
            Insets systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars());
            DisplayCutoutCompat cutout = insets.getDisplayCutout();

            boolean isPortrait = context.getResources().getConfiguration().orientation == Configuration.ORIENTATION_PORTRAIT;

            int topInset = 0;

            if (isPortrait) {
                // Only apply top inset if the cutout is at the top OR the status bar is present
                if (cutout != null) {
                    topInset = Math.max(systemBars.top, cutout.getSafeInsetTop());
                } else {
                    topInset = systemBars.top;
                }
                // Apply paddings to avoid cutout (status bar area / camera hole)
                v.setPadding(
                        systemBars.left,
                        topInset,
                        systemBars.right,
                        systemBars.bottom
                );
            }

            return insets;
        });
    }
}
