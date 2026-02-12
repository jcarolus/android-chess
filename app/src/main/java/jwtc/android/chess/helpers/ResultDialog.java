package jwtc.android.chess.helpers;

import android.app.Dialog;
import android.content.Context;

import androidx.annotation.NonNull;

import jwtc.android.chess.R;

public class ResultDialog<T> extends Dialog {
    protected int requestCode;
    protected ResultDialogListener<T> listener;

    public ResultDialog(@NonNull Context context, ResultDialogListener listener, int requestCode) {
        super(context, R.style.ChessDialogTheme);

        this.listener = listener;
        this.requestCode = requestCode;

    }

    protected void setResult(T data) {
        listener.OnDialogResult(requestCode, data);
    }

}
