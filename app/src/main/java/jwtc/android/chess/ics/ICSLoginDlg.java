package jwtc.android.chess.ics;

import android.app.Dialog;
import android.content.Context;
import android.os.Bundle;
import android.os.Message;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.TextView;

import jwtc.android.chess.R;
import jwtc.android.chess.helpers.ResultDialog;
import jwtc.android.chess.helpers.ResultDialogListener;

public class ICSLoginDlg extends ResultDialog {

    public ICSLoginDlg(Context context, final ResultDialogListener listener, int requestCode) {
        super(context, listener, requestCode);

        setContentView(R.layout.ics_login);
//        setCancelable(false);

        Button buttonLogin = findViewById(R.id.ButICSLogin);
        buttonLogin.setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) {

                EditText editHandle = findViewById(R.id.EditICSHandle);
                EditText editPwd = findViewById(R.id.EditICSPwd);

                final String handle = editHandle.getText().toString();
                final String pwd = editPwd.getText().toString();

                /*
                defaultHost = chessclub.com
                hosts = chessclub.com queen.chessclub.com
                ports = 5000 23
                id = icc
                 */

                if (handle != "" && pwd != "") {

                    Bundle data = new Bundle();
                    data.putCharSequence("handle", handle);
                    data.putCharSequence("pwd", pwd);

                    setResult(data);

                    ICSLoginDlg.this.dismiss();
                } else {
                    if (handle == "") {
//                        globalToast(getString(R.string.msg_ics_enter_handle));
                    }
                    if (handle != "guest" && pwd == "") {
//                        globalToast(getString(R.string.msg_ics_enter_password));
                    }
                }
            }
        });
    }
}