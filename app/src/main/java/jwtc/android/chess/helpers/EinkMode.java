package jwtc.android.chess.helpers;

import android.content.SharedPreferences;
import android.os.Build;

import androidx.recyclerview.widget.RecyclerView;

import java.util.Locale;

import jwtc.android.chess.constants.ColorSchemes;
import jwtc.android.chess.constants.PieceSets;

/**
 * E-ink support: three settings of its own plus a preset that sets them, and a
 * few existing settings, to values that suit electrophoretic (e-paper) screens.
 *
 * E-ink panels refresh slowly and hold their previous image, so anything that
 * moves or repaints continuously leaves a visible ghost trail rather than an
 * animation. The settings each remove one source of that:
 * <ul>
 *   <li>{@link #PREF_DISABLE_DRAG}: move by tapping; no drag shadow following the finger.</li>
 *   <li>{@link #PREF_THEME}: a black and white theme with outlines and no ripples.</li>
 *   <li>{@link #PREF_REDUCE_ANIMATIONS}: no indeterminate progress bars, pulse or list animations.</li>
 * </ul>
 *
 * The preset ({@link #PREF_KEY}) is not a mode that overrides anything: it
 * writes the values in {@link #PRESET} into the ordinary preferences, where
 * each one can still be changed on its own. Switching it off restores the
 * values from before, except for any setting the user has changed since.
 *
 * The three flags are cached statically and reloaded on activity create and
 * resume, mirroring how {@link ColorSchemes} and {@link PieceSets} share board
 * settings.
 */
public class EinkMode {

    public static final String PREF_KEY = "einkMode";
    public static final String PREF_DISABLE_DRAG = "disableDrag";
    public static final String PREF_THEME = "einkTheme";
    public static final String PREF_REDUCE_ANIMATIONS = "reduceAnimations";

    /** Where the preset keeps each setting's value from before it was applied. */
    private static final String PREVIOUS_PREFIX = "einkPrevious.";

    /** One preference the preset sets: its key, the e-ink value, and the app's default. */
    private static final class Setting {
        final String key;
        final Object einkValue;
        final Object defaultValue;

        Setting(String key, Object einkValue, Object defaultValue) {
            this.key = key;
            this.einkValue = einkValue;
            this.defaultValue = defaultValue;
        }
    }

    /**
     * Everything the preset sets. Values are Boolean or String, matching how
     * each key is stored; defaults must match the ones its readers use.
     */
    private static final Setting[] PRESET = {
        // A sliding piece is a quarter second of continuous partial refreshes.
        new Setting("pref_use_piece_animation", false, true),
        // Alpha is the only flat, gradient-free set; the others lose detail in grey.
        new Setting("pieceset", String.valueOf(PieceSets.ALPHA), "0"),
        // Mid-grey and white; the coloured schemes collapse into similar greys.
        new Setting("colorscheme", String.valueOf(ColorSchemes.EINK_COLOR_SCHEME), "0"),
        // The tile patterns are alpha gradients.
        new Setting("squarePattern", "0", "0"),
        // The status bar keeps redrawing (clock, wifi, battery).
        new Setting("fullScreen", true, false),
        // E-ink readers are small; the full control stack pushes the board around.
        new Setting("minimal", true, false),
        new Setting(PREF_DISABLE_DRAG, true, false),
        new Setting(PREF_THEME, true, false),
        new Setting(PREF_REDUCE_ANIMATIONS, true, false),
    };

    /**
     * Vendors whose entire product line is electrophoretic, matched against
     * Build.MANUFACTURER / BRAND / MODEL.
     *
     * Android exposes no way to ask whether a display is e-ink — there is no
     * panel-technology API — so this is a vendor allowlist, not a property of
     * the screen. It is deliberately conservative: mixed-line vendors such as
     * Hisense and TCL ship both e-ink readers and ordinary LCD phones and are
     * left out, because a false positive would hand an LCD user a greyscale
     * board and no animations for no reason.
     */
    private static final String[] EINK_VENDORS = {
        "onyx", "boox", "bigme", "dasung", "meebook", "boyue", "likebook",
        "supernote", "ratta", "moaan", "pocketbook", "remarkable", "inkbook",
    };

    private static Boolean einkHardware = null;

    private static boolean enabled = false;
    private static boolean theme = false;
    private static boolean reduceAnimations = false;
    private static boolean dragDisabled = false;

    /** Whether the preset is currently applied. */
    public static boolean isEnabled() {
        return enabled;
    }

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
        enabled = prefs.getBoolean(PREF_KEY, false);
        theme = prefs.getBoolean(PREF_THEME, false);
        reduceAnimations = prefs.getBoolean(PREF_REDUCE_ANIMATIONS, false);
        dragDisabled = prefs.getBoolean(PREF_DISABLE_DRAG, false);
    }

    /**
     * On the first run only, apply the preset on recognised e-ink hardware.
     * Once {@link #PREF_KEY} has been stored this does nothing, so a setting the
     * user has turned back on is never forced off again. Call before
     * {@link #load} in the first activity's onCreate.
     */
    public static void ensureInitialised(SharedPreferences prefs) {
        if (prefs.contains(PREF_KEY)) {
            return;
        }
        if (isEinkHardware()) {
            applyPreset(prefs);
        } else {
            prefs.edit().putBoolean(PREF_KEY, false).commit();
        }
    }

    /**
     * Write the e-ink value of every setting in {@link #PRESET}, remembering
     * the current values so {@link #revertPreset} can put them back.
     */
    public static void applyPreset(SharedPreferences prefs) {
        if (prefs.getBoolean(PREF_KEY, false)) {
            return;
        }
        final SharedPreferences.Editor editor = prefs.edit();
        for (Setting setting : PRESET) {
            put(editor, PREVIOUS_PREFIX + setting.key, get(prefs, setting.key, setting.defaultValue));
            put(editor, setting.key, setting.einkValue);
        }
        editor.putBoolean(PREF_KEY, true);
        editor.commit();
        load(prefs);
    }

    /**
     * Put back the values from before {@link #applyPreset}, for every setting
     * that still has its e-ink value. A setting the user has changed since is
     * their own choice and stays as it is.
     */
    public static void revertPreset(SharedPreferences prefs) {
        if (!prefs.getBoolean(PREF_KEY, false)) {
            return;
        }
        final SharedPreferences.Editor editor = prefs.edit();
        for (Setting setting : PRESET) {
            final String previousKey = PREVIOUS_PREFIX + setting.key;
            if (setting.einkValue.equals(get(prefs, setting.key, setting.defaultValue))) {
                put(editor, setting.key, get(prefs, previousKey, setting.defaultValue));
            }
            editor.remove(previousKey);
        }
        editor.putBoolean(PREF_KEY, false);
        editor.commit();
        load(prefs);
    }

    private static Object get(SharedPreferences prefs, String key, Object defaultValue) {
        if (defaultValue instanceof Boolean) {
            return prefs.getBoolean(key, (Boolean) defaultValue);
        }
        return prefs.getString(key, (String) defaultValue);
    }

    private static void put(SharedPreferences.Editor editor, String key, Object value) {
        if (value instanceof Boolean) {
            editor.putBoolean(key, (Boolean) value);
        } else {
            editor.putString(key, (String) value);
        }
    }

    /**
     * Best-effort identification of e-ink hardware by vendor. See
     * {@link #EINK_VENDORS} for why this cannot query the display itself.
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
     * Disable item change animations, which fade and slide rows on every
     * update. Safe to call with null.
     */
    public static void applyTo(RecyclerView recyclerView) {
        if (reduceAnimations && recyclerView != null) {
            recyclerView.setItemAnimator(null);
        }
    }
}
