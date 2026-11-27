package jwtc.android.chess.play;

import android.content.Context;
import android.content.SharedPreferences;
import android.os.Bundle;
import androidx.annotation.NonNull;
import com.google.android.material.button.MaterialButtonToggleGroup;
import jwtc.android.chess.R;
import jwtc.android.chess.helpers.ResultDialog;
import jwtc.android.chess.helpers.ResultDialogListener;
import jwtc.chess.Variant;

public class NewGameDialog extends ResultDialog<Bundle> {
    /** The chosen {@link Variant}'s name. */
    public static final String EXTRA_VARIANT = "variant";

    private static final String PREF_VARIANT = "newGameVariantName";
    // Earlier versions stored an int code plus a separate Chess 960 flag.
    private static final String LEGACY_PREF_VARIANT = "newGameVariant";
    private static final String LEGACY_PREF_CHESS960 = "newGameChess960";

    public NewGameDialog(
            @NonNull Context context,
            ResultDialogListener<Bundle> listener,
            int requestCode,
            SharedPreferences prefs) {
        super(context, listener, requestCode);
        setContentView(R.layout.new_game);

        MaterialButtonToggleGroup toggleOpponent = findViewById(R.id.ToggleOpponentGroup);
        MaterialButtonToggleGroup toggleColor = findViewById(R.id.ToggleColorGroup);
        MaterialButtonToggleGroup toggleVariant = findViewById(R.id.ToggleVariantGroup);

        toggleOpponent.check(
                prefs.getBoolean("opponent", true) ? R.id.radioAndroid : R.id.radioHuman);
        toggleColor.check(prefs.getBoolean("myTurn", true) ? R.id.radioWhite : R.id.radioBlack);
        toggleVariant.check(buttonFor(savedVariant(prefs)));

        findViewById(R.id.ButtonOk)
                .setOnClickListener(
                        v -> {
                            Variant variant = variantFor(toggleVariant.getCheckedButtonId());
                            prefs.edit()
                                    .putBoolean(
                                            "opponent",
                                            toggleOpponent.getCheckedButtonId()
                                                    == R.id.radioAndroid)
                                    .putBoolean(
                                            "myTurn",
                                            toggleColor.getCheckedButtonId() == R.id.radioWhite)
                                    .putString(PREF_VARIANT, variant.name())
                                    .remove(LEGACY_PREF_VARIANT)
                                    .remove(LEGACY_PREF_CHESS960)
                                    .apply();

                            Bundle result = new Bundle();
                            result.putString(EXTRA_VARIANT, variant.name());
                            setResult(result);
                            dismiss();
                        });
        findViewById(R.id.ButtonCancel).setOnClickListener(v -> dismiss());
    }

    private static Variant savedVariant(SharedPreferences prefs) {
        String name = prefs.getString(PREF_VARIANT, null);
        if (name != null) {
            try {
                return Variant.valueOf(name);
            } catch (IllegalArgumentException ignored) {
                return Variant.STANDARD;
            }
        }
        if (prefs.getBoolean(LEGACY_PREF_CHESS960, false)) {
            return Variant.CHESS_960;
        }
        switch (prefs.getInt(LEGACY_PREF_VARIANT, 1)) {
            case 2:
                return Variant.DUCK;
            case 3:
                return Variant.KING_OF_THE_HILL;
            default:
                return Variant.STANDARD;
        }
    }

    private static int buttonFor(Variant variant) {
        switch (variant) {
            case CHESS_960:
                return R.id.radioChess960;
            case DUCK:
                return R.id.radioDuck;
            case KING_OF_THE_HILL:
                return R.id.radioKingOfTheHill;
            default:
                return R.id.radioStandard;
        }
    }

    private static Variant variantFor(int buttonId) {
        if (buttonId == R.id.radioChess960) {
            return Variant.CHESS_960;
        }
        if (buttonId == R.id.radioDuck) {
            return Variant.DUCK;
        }
        if (buttonId == R.id.radioKingOfTheHill) {
            return Variant.KING_OF_THE_HILL;
        }
        return Variant.STANDARD;
    }
}
