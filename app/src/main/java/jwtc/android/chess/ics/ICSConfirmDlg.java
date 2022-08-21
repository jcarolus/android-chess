package jwtc.android.chess.ics;

import jwtc.android.chess.*;
import jwtc.android.chess.helpers.ResultDialog;
import jwtc.android.chess.helpers.ResultDialogListener;

import android.content.Context;
import android.os.Bundle;
import android.view.View;
import android.widget.Button;
import android.widget.TextView;

/**
 *
 */
public class ICSConfirmDlg extends ResultDialog {

    private String _sendString;
    private TextView _tvText;

    public ICSConfirmDlg(Context context, ResultDialogListener listener, int requestCode) {
        super(context, listener, requestCode);

        setContentView(R.layout.icsconfirm);

        setCanceledOnTouchOutside(true);

        _tvText = (TextView) findViewById(R.id.TextViewConfirm);

        Button butYes = (Button) findViewById(R.id.ButtonConfirmYes);
        butYes.setOnClickListener(new View.OnClickListener() {
            public void onClick(View arg0) {
                Bundle data = new Bundle();
                data.putCharSequence("data", _sendString);
                setResult(data);
                dismiss();
            }
        });
        Button butNo = (Button) findViewById(R.id.ButtonConfirmNo);
        butNo.setOnClickListener(new View.OnClickListener() {
            public void onClick(View arg0) {
                dismiss();
            }
        });
    }

    public void setText(String sTitle, String sText) {
        setTitle(sTitle);
        _tvText.setText(sText);
    }

    public void setSendString(String s) {
        _sendString = s;
    }
}