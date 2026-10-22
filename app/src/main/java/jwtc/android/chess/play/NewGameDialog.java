package jwtc.android.chess.play;

import android.content.Context;
import android.content.SharedPreferences;
import android.os.Bundle;
import androidx.annotation.NonNull;
import com.google.android.material.button.MaterialButtonToggleGroup;
import jwtc.android.chess.R;
import jwtc.android.chess.helpers.ResultDialog;
import jwtc.android.chess.helpers.ResultDialogListener;
import jwtc.chess.board.BoardConstants;

public class NewGameDialog extends ResultDialog<Bundle> {
    public static final String EXTRA_VARIANT = "variant";
    public static final String EXTRA_CHESS960 = "chess960";
    private static final String PREF_VARIANT = "newGameVariant";
    private static final String PREF_CHESS960 = "newGameChess960";

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
        int savedVariant = prefs.getInt(PREF_VARIANT, BoardConstants.VARIANT_DEFAULT);
        if (prefs.getBoolean(PREF_CHESS960, false)) {
            toggleVariant.check(R.id.radioChess960);
        } else if (savedVariant == BoardConstants.VARIANT_DUCK) {
            toggleVariant.check(R.id.radioDuck);
        } else if (savedVariant == BoardConstants.VARIANT_KING_OF_THE_HILL) {
            toggleVariant.check(R.id.radioKingOfTheHill);
        } else {
            toggleVariant.check(R.id.radioStandard);
        }

        findViewById(R.id.ButtonOk)
                .setOnClickListener(
                        v -> {
                            int selectedVariant = toggleVariant.getCheckedButtonId();
                            int variant = BoardConstants.VARIANT_DEFAULT;
                            if (selectedVariant == R.id.radioDuck) {
                                variant = BoardConstants.VARIANT_DUCK;
                            } else if (selectedVariant == R.id.radioKingOfTheHill) {
                                variant = BoardConstants.VARIANT_KING_OF_THE_HILL;
                            }
                            prefs.edit()
                                    .putBoolean(
                                            "opponent",
                                            toggleOpponent.getCheckedButtonId()
                                                    == R.id.radioAndroid)
                                    .putBoolean(
                                            "myTurn",
                                            toggleColor.getCheckedButtonId() == R.id.radioWhite)
                                    .putInt(PREF_VARIANT, variant)
                                    .putBoolean(
                                            PREF_CHESS960, selectedVariant == R.id.radioChess960)
                                    .apply();

                            Bundle result = new Bundle();
                            result.putInt(EXTRA_VARIANT, variant);
                            result.putBoolean(
                                    EXTRA_CHESS960, selectedVariant == R.id.radioChess960);
                            setResult(result);
                            dismiss();
                        });
        findViewById(R.id.ButtonCancel).setOnClickListener(v -> dismiss());
    }
}
