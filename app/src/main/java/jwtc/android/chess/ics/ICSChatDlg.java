package jwtc.android.chess.ics;

import jwtc.android.chess.*;

import android.app.Dialog;
import android.content.Context;
import android.view.WindowManager;
import android.widget.EditText;
import android.widget.TextView;

import com.google.android.material.button.MaterialButton;

/**
 *
 */
public class ICSChatDlg extends Dialog {

    private ICSClient _parent;
    public TextView _tvChat;
    private EditText _editChat;

    public ICSChatDlg(Context context) {
        super(context, R.style.ChessDialogTheme);

        _parent = (ICSClient) context;

        setContentView(R.layout.ics_chat);
        //setTitle("Chat");

        _tvChat = (TextView) findViewById(R.id.TextViewChat);
        _editChat = (EditText) findViewById(R.id.EditChat);

        MaterialButton butYes = findViewById(R.id.ButtonChatOk);
        butYes.setOnClickListener(arg0 -> {
//                String opponent = _parent.get_view().getOpponent();
//                if (opponent.length() > 0) {
//                    _parent.sendString("tell " + opponent + " " + _editChat.getText().toString());
//                } else {
//                    _parent.sendString("whisper " + _editChat.getText().toString());
//                }
            dismiss();
        });
        MaterialButton butNo = findViewById(R.id.ButtonChatCancel);
        butNo.setOnClickListener(arg0 -> dismiss());

        getWindow().setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_STATE_ALWAYS_VISIBLE);
    }


    public void prepare() {
//        String opponent = _parent.get_view().getOpponent();
//        if (opponent.length() > 0) {
//            _tvChat.setText("tell opponent (" + opponent + ")");
//        } else {
//            _tvChat.setText("whisper");
//        }
        _editChat.setText("");
        _editChat.requestFocus();
    }
}