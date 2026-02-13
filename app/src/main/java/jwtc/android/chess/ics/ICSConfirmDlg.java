package jwtc.android.chess.ics;

import jwtc.android.chess.*;
import jwtc.android.chess.helpers.ResultDialog;
import jwtc.android.chess.helpers.ResultDialogListener;

import android.content.Context;
import android.os.Bundle;
import android.widget.TextView;

import com.google.android.material.button.MaterialButton;

/**
 * @TODO just openConfirmDialog?
 */
public class ICSConfirmDlg extends ResultDialog {

    private String _sendString;
    private TextView _tvText;

    public ICSConfirmDlg(Context context, ResultDialogListener listener, int requestCode) {
        super(context, listener, requestCode);

        setContentView(R.layout.icsconfirm);

        setCanceledOnTouchOutside(true);

        _tvText = findViewById(R.id.TextViewConfirm);

        MaterialButton butYes = findViewById(R.id.ButtonConfirmYes);
        butYes.setOnClickListener(arg0 -> {
            Bundle data = new Bundle();
            data.putCharSequence("data", _sendString);
            setResult(data);
            dismiss();
        });
        MaterialButton butNo = findViewById(R.id.ButtonConfirmNo);
        butNo.setOnClickListener(arg0 -> dismiss());
    }

    public void setText(String sTitle, String sText) {
        setTitle(sTitle);
        _tvText.setText(sText);
    }

    public void setSendString(String s) {
        _sendString = s;
    }
}