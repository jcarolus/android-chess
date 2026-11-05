package jwtc.android.chess.helpers;

import android.app.Activity;
import android.app.Dialog;
import android.content.Context;
import android.content.SharedPreferences;
import android.content.res.Configuration;
import android.graphics.Rect;
import android.view.View;
import android.view.ViewTreeObserver;
import android.view.Window;
import android.view.WindowManager;
import android.view.animation.Animation;
import android.view.animation.ScaleAnimation;
import androidx.core.graphics.Insets;
import androidx.core.view.DisplayCutoutCompat;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;
import androidx.core.view.WindowInsetsControllerCompat;
import jwtc.android.chess.R;

public class ActivityHelper {
    private static Insets tvSafeInsets(Context context) {
        int uiMode = context.getResources().getConfiguration().uiMode;
        if ((uiMode & Configuration.UI_MODE_TYPE_MASK) != Configuration.UI_MODE_TYPE_TELEVISION) {
            return Insets.NONE;
        }
        int horizontal =
                context.getResources().getDimensionPixelSize(R.dimen.tv_safe_margin_horizontal);
        int vertical =
                context.getResources().getDimensionPixelSize(R.dimen.tv_safe_margin_vertical);
        return Insets.of(horizontal, vertical, horizontal, vertical);
    }

    public static void padTvContent(View content) {
        if (content == null) {
            return;
        }
        Insets safe = tvSafeInsets(content.getContext());
        if (!Insets.NONE.equals(safe)) {
            content.setPadding(safe.left, safe.top, safe.right, safe.bottom);
        }
    }

    public static void fixPaddings(Activity context, View rootView) {
        ViewCompat.setOnApplyWindowInsetsListener(
                rootView,
                (v, insets) -> {
                    int nightModeFlags =
                            context.getResources().getConfiguration().uiMode
                                    & Configuration.UI_MODE_NIGHT_MASK;

                    boolean isDarkMode = (nightModeFlags == Configuration.UI_MODE_NIGHT_YES);

                    WindowInsetsControllerCompat controller =
                            new WindowInsetsControllerCompat(context.getWindow(), rootView);

                    if (isDarkMode) {
                        controller.setAppearanceLightStatusBars(false);
                    } else {
                        controller.setAppearanceLightStatusBars(true);
                    }

                    SharedPreferences prefs =
                            context.getSharedPreferences("ChessPlayer", Activity.MODE_PRIVATE);
                    boolean fullScreen = prefs.getBoolean("fullScreen", false);

                    Insets systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars());
                    DisplayCutoutCompat cutout = insets.getDisplayCutout();
                    int orientation = context.getResources().getConfiguration().orientation;
                    int topInset = fullScreen ? 0 : systemBars.top;
                    int leftInset = fullScreen ? 0 : systemBars.left;

                    if (cutout != null) {
                        if (orientation == Configuration.ORIENTATION_PORTRAIT) {
                            topInset =
                                    fullScreen
                                            ? cutout.getSafeInsetTop()
                                            : Math.max(systemBars.top, cutout.getSafeInsetTop());
                        } else if (orientation == Configuration.ORIENTATION_LANDSCAPE) {
                            leftInset =
                                    fullScreen
                                            ? cutout.getSafeInsetLeft()
                                            : Math.max(systemBars.left, cutout.getSafeInsetLeft());
                        }
                    }
                    Insets padding =
                            Insets.max(
                                    Insets.of(
                                            leftInset,
                                            topInset,
                                            systemBars.right,
                                            systemBars.bottom),
                                    tvSafeInsets(context));
                    v.setPadding(padding.left, padding.top, padding.right, padding.bottom);

                    return WindowInsetsCompat.CONSUMED;
                });
    }

    public static void constrainDialogToTvSafeArea(Dialog dialog) {
        Insets safe = tvSafeInsets(dialog.getContext());
        if (!Insets.NONE.equals(safe)) {
            Window window = dialog.getWindow();
            View decor = window.getDecorView();
            decor.addOnAttachStateChangeListener(new TvDialogBounds(window, decor, safe));
        }
    }

    private static final class TvDialogBounds
            implements View.OnAttachStateChangeListener, ViewTreeObserver.OnPreDrawListener {
        private final Window window;
        private final View decor;
        private final Insets safe;
        private final Rect frame = new Rect();
        private final int originalWidth;
        private final int originalHeight;
        private int lastMaxWidth;
        private int lastMaxHeight;

        TvDialogBounds(Window window, View decor, Insets safe) {
            this.window = window;
            this.decor = decor;
            this.safe = safe;
            WindowManager.LayoutParams params = window.getAttributes();
            originalWidth = params.width;
            originalHeight = params.height;
        }

        @Override
        public void onViewAttachedToWindow(View view) {
            lastMaxWidth = 0;
            lastMaxHeight = 0;
            WindowManager.LayoutParams params = window.getAttributes();
            if (params.width != originalWidth || params.height != originalHeight) {
                window.setLayout(originalWidth, originalHeight);
            }
            view.getViewTreeObserver().addOnPreDrawListener(this);
        }

        @Override
        public void onViewDetachedFromWindow(View view) {
            view.getViewTreeObserver().removeOnPreDrawListener(this);
        }

        @Override
        public boolean onPreDraw() {
            decor.getWindowVisibleDisplayFrame(frame);
            int maxWidth = frame.width() - safe.left - safe.right;
            int maxHeight = frame.height() - safe.top - safe.bottom;
            if (maxWidth <= 0 || maxHeight <= 0) {
                return true;
            }

            WindowManager.LayoutParams params = window.getAttributes();
            int width =
                    limitDimension(
                            decor.getWidth(), maxWidth, lastMaxWidth, params.width, originalWidth);
            int height =
                    limitDimension(
                            decor.getHeight(),
                            maxHeight,
                            lastMaxHeight,
                            params.height,
                            originalHeight);
            lastMaxWidth = maxWidth;
            lastMaxHeight = maxHeight;
            if (width == params.width && height == params.height) {
                return true;
            }

            window.setLayout(width, height);
            // Wait for the window manager to lay out the dialog at its new size.
            return false;
        }

        private static int limitDimension(
                int measured, int maximum, int previousMaximum, int current, int original) {
            if (measured > maximum) {
                return maximum;
            }
            if (maximum > previousMaximum && current != original) {
                return original;
            }
            return current;
        }
    }

    public static void pulseAnimation(View v) {
        pulseAnimation(v, 2f, 1);
    }

    public static void pulseAnimation(View v, float factor, int repeatCount) {
        if (EinkMode.isReduceAnimations()) {
            // A repeated grow/shrink is a continuous partial refresh on e-ink,
            // which ghosts. The view stays put instead.
            return;
        }

        ScaleAnimation pulse =
                new ScaleAnimation(
                        1f,
                        factor, // fromX, toX
                        1f,
                        factor, // fromY, toY
                        Animation.RELATIVE_TO_SELF,
                        0.5f, // pivotX (center)
                        Animation.RELATIVE_TO_SELF,
                        0.5f // pivotY (center)
                        );
        pulse.setDuration(300); // time for grow phase
        pulse.setRepeatMode(Animation.REVERSE); // reverse back to original
        pulse.setRepeatCount(repeatCount); // do the shrink once
        pulse.setFillAfter(false); // end at original scale
        pulse.setInterpolator(new android.view.animation.AccelerateDecelerateInterpolator());

        v.startAnimation(pulse);
    }
}
