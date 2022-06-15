package jwtc.android.chess.helpers;

import android.app.Dialog;
import android.content.Context;
import android.os.Bundle;

import androidx.annotation.NonNull;

public class ResultDialog extends Dialog {
    protected int requestCode;
    protected ResultDialogListener listener;

    public ResultDialog(@NonNull Context context, ResultDialogListener listener, int requestCode) {
        super(context);

        this.listener = listener;
        this.requestCode = requestCode;

    }

    protected void setResult(Bundle data) {
        listener.OnDialogResult(requestCode, data);
    }

}
