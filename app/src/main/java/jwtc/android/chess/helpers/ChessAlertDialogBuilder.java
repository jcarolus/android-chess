package jwtc.android.chess.helpers;

import android.content.Context;
import androidx.appcompat.app.AlertDialog;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;

public final class ChessAlertDialogBuilder extends MaterialAlertDialogBuilder {
    public ChessAlertDialogBuilder(Context context) {
        super(context);
    }

    @Override
    public AlertDialog create() {
        AlertDialog dialog = super.create();
        ActivityHelper.constrainDialogToTvSafeArea(dialog);
        return dialog;
    }
}
