package jwtc.android.chess.helpers;

import android.content.SharedPreferences;
import android.os.Build;

import androidx.recyclerview.widget.RecyclerView;

import java.util.Locale;

import jwtc.android.chess.constants.ColorSchemes;
import jwtc.android.chess.constants.PieceSets;

/**
 * Independent e-ink settings and a reapplicable preset for e-paper screens.
 *
 * Frequent redraws can cause ghosting on e-ink panels. These settings reduce
 * dragging, visual effects and animation:
 * <ul>
 *   <li>{@link #PREF_DISABLE_DRAG}: move by tapping; no drag shadow following the finger.</li>
 *   <li>{@link #PREF_THEME}: a black and white theme with outlines and no ripples.</li>
 *   <li>{@link #PREF_REDUCE_ANIMATIONS}: no indeterminate progress bars, pulse or list animations.</li>
 * </ul>
 *
 * The preset writes recommended values into ordinary preferences. Each setting
 * remains independently editable, and the preset can be applied again at any
 * time. Applying it replaces the affected choices; other preferences stay intact.
 *
 * The three flags are cached statically and reloaded on activity create and
 * resume, mirroring how {@link ColorSchemes} and {@link PieceSets} share board
 * settings.
 */
public class EinkMode {

    // Reuse the old toggle key as a first-run marker. Either legacy value means
    // setup already ran, so upgrading must preserve the user's current choices.
    private static final String PREF_INITIALISED = "einkMode";
    public static final String PREF_DISABLE_DRAG = "disableDrag";
    public static final String PREF_THEME = "einkTheme";
    public static final String PREF_REDUCE_ANIMATIONS = "reduceAnimations";

    /**
     * Vendor identifiers used as a best-effort e-ink heuristic, matched against
     * Build.MANUFACTURER, BRAND and MODEL. This does not inspect the display.
     * Mixed-display vendors such as Hisense and TCL are omitted to avoid
     * applying the preset to their LCD devices.
     */
    private static final String[] EINK_VENDORS = {
        "onyx", "boox", "bigme", "dasung", "meebook", "boyue", "likebook",
        "supernote", "ratta", "moaan", "pocketbook", "remarkable", "inkbook",
    };

    private static Boolean einkHardware = null;

    private static boolean theme = false;
    private static boolean reduceAnimations = false;
    private static boolean dragDisabled = false;

    public static boolean isThemeEnabled() {
        return theme;
    }

    public static boolean isReduceAnimations() {
        return reduceAnimations;
    }

    public static boolean isDragDisabled() {
        return dragDisabled;
    }

    /** Reload the cached flags from preferences. */
    public static void load(SharedPreferences prefs) {
        theme = prefs.getBoolean(PREF_THEME, false);
        reduceAnimations = prefs.getBoolean(PREF_REDUCE_ANIMATIONS, false);
        dragDisabled = prefs.getBoolean(PREF_DISABLE_DRAG, false);
    }

    /**
     * Check hardware once per preference store and apply the preset if recognised.
     * Record completion on other devices too, so subsequent starts preserve user
     * choices. Call before {@link #load} when creating an activity.
     */
    public static void ensureInitialised(SharedPreferences prefs) {
        if (prefs.contains(PREF_INITIALISED)) {
            return;
        }
        if (isEinkHardware()) {
            applyPreset(prefs);
        } else {
            prefs.edit().putBoolean(PREF_INITIALISED, true).apply();
        }
    }

    /**
     * Write the recommended values on every invocation and refresh the cached flags.
     * Individual settings remain editable afterward; previous values are not saved.
     */
    public static void applyPreset(SharedPreferences prefs) {
        prefs.edit()
            .putBoolean("nightMode", false)
            .putBoolean("pref_use_piece_animation", false)
            .putString("pieceset", String.valueOf(PieceSets.ALPHA))
            .putString("colorscheme", String.valueOf(ColorSchemes.EINK_COLOR_SCHEME))
            .putString("squarePattern", "0")
            .putBoolean("fullScreen", true)
            .putBoolean("minimal", true)
            .putBoolean(PREF_DISABLE_DRAG, true)
            .putBoolean(PREF_THEME, true)
            .putBoolean(PREF_REDUCE_ANIMATIONS, true)
            .putBoolean(PREF_INITIALISED, true)
            .apply();
        load(prefs);
    }

    /**
     * Cache the vendor-based hardware estimate described by {@link #EINK_VENDORS}.
     */
    public static boolean isEinkHardware() {
        if (einkHardware == null) {
            final String fingerprint = (Build.MANUFACTURER + ' ' + Build.BRAND + ' ' + Build.MODEL)
                .toLowerCase(Locale.ROOT);
            boolean match = false;
            for (String vendor : EINK_VENDORS) {
                if (fingerprint.contains(vendor)) {
                    match = true;
                    break;
                }
            }
            einkHardware = match;
        }
        return einkHardware;
    }

    /**
     * Disable RecyclerView item animations when reduce-animations is enabled.
     * Safe to call with null; does not restore a previously removed animator.
     */
    public static void applyTo(RecyclerView recyclerView) {
        if (reduceAnimations && recyclerView != null) {
            recyclerView.setItemAnimator(null);
        }
    }
}
