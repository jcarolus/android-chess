package jwtc.android.chess.helpers;

import android.content.Context;
import android.os.Build;
import android.os.VibrationEffect;
import android.os.Vibrator;

public class HapticFeedback {
    private final Vibrator vibrator;
    private boolean enabled = false;

    public HapticFeedback(Context context) {
        vibrator = (Vibrator) context.getSystemService(Context.VIBRATOR_SERVICE);
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public boolean getEnabled() {
        return enabled;
    }

    public boolean isEnabled() {
        return enabled;
    }

    public void hapticFeedbackTick() {
        try {
            if (enabled && vibrator != null && vibrator.hasVibrator()) {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    vibrator.vibrate(VibrationEffect.createPredefined(VibrationEffect.EFFECT_TICK));
                } else {
                    vibrator.vibrate(15);
                }
            }
        } catch (Exception ignore) {}
    }

    public void feedbackSelect() {
        try {
            if (enabled && vibrator != null && vibrator.hasVibrator()) {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    vibrator.vibrate(VibrationEffect.createPredefined(VibrationEffect.EFFECT_DOUBLE_CLICK));
                } else {
                    vibrator.vibrate(100);
                }
            }
        } catch (Exception ignore) {}
    }
}
