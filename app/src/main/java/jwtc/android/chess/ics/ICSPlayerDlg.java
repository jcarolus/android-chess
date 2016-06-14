package jwtc.android.chess.ics;

import android.app.AlertDialog;
import android.app.Dialog;
import android.content.Context;
import android.content.DialogInterface;
import android.text.InputFilter;
import android.text.InputType;
import android.util.Log;
import android.view.Gravity;
import android.view.KeyEvent;
import android.view.View;
import android.view.inputmethod.EditorInfo;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ScrollView;
import android.widget.TextView;

import jwtc.android.chess.R;

/**
 * Created by Profile on 5/8/2016.
 */
public class ICSPlayerDlg extends Dialog {

    public static final String TAG = "ICSPlayerDlg";

    private String _opponentName;
    private ICSClient _parent;
    private Button _butHistory, _butFinger, _butMatch, _butFollow, _butUnfollow, _butFriendsList,  _butSmoves, _butExit;
    private TextView _tvOpponentName;
    protected TextView _tvPlayerListConsole;
    protected ScrollView _scrollPlayerListConsole;
    private EditText _editPlayerChat;

    public ICSPlayerDlg(Context context) {
        super(context);

        _parent = (ICSClient) context;

        setContentView(R.layout.ics_player);

        _tvOpponentName = (TextView)findViewById(R.id.tvopponentname);

        _tvPlayerListConsole = (TextView)findViewById(R.id.TextViewICSPlayerList);
        _scrollPlayerListConsole = (ScrollView)findViewById(R.id.ScrollICSPlayerList);


        _butExit = (Button)findViewById(R.id.ButtonGameExit);
        _butExit.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                ICSPlayerDlg.this.dismiss();
            }
        });

        _butHistory = (Button)findViewById(R.id.ButHistory);
        _butHistory.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                _parent.sendString("History " + _opponentName);
                _tvPlayerListConsole.setText("");  // clear screen
            }
        });

        _butFinger = (Button)findViewById(R.id.ButFinger);
        _butFinger.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                _parent.sendString("Finger " + _opponentName);
                _tvPlayerListConsole.setText("");
            }
        });

        _butFollow = (Button)findViewById(R.id.ButFollow);
        _butFollow.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                _parent.sendString("follow " + _opponentName);
                _tvPlayerListConsole.setText("");
            }
        });

        _butUnfollow = (Button)findViewById(R.id.ButUnFollow);
        _butUnfollow.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                _parent.sendString("unfollow " + _opponentName);
                _tvPlayerListConsole.setText("");
            }
        });

        _butMatch = (Button)findViewById(R.id.ButMatch);
        _butMatch.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                _parent._dlgMatch._rbChallenge.setChecked(true);
                _parent._dlgMatch._rbChallenge.performClick();
                _parent._dlgMatch.setPlayer(_opponentName);
                _parent._dlgMatch.show();

            }
        });

        View.OnKeyListener onKeyListen = new View.OnKeyListener() {
            public boolean onKey(View v, int keyCode, KeyEvent event) {
                if (((event.getAction() == KeyEvent.ACTION_DOWN) && (keyCode == KeyEvent.KEYCODE_ENTER)) ||
                        event.getAction() == EditorInfo.IME_ACTION_DONE
                        ) {
                    // Perform action on key press
                    EditText et = (EditText) v;
                    String _sConsoleEditText = et.getText().toString();

                    _parent.sendString("tell " + _opponentName + " " + _sConsoleEditText);
                    _parent.addConsoleText("\n" + getContext().getResources().getString(R.string.ics_you) + ": " + _sConsoleEditText + "\n");
                    et.setText("");

                    return true;
                }
                return false;
            }
        };

        _butSmoves = (Button)findViewById(R.id.ButSmoves);
        _butSmoves.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                final AlertDialog alertDialog = new AlertDialog.Builder(getContext()).create(); //Read Update
                alertDialog.setTitle(R.string.ics_historygamenumber);
                alertDialog.setMessage(getContext().getResources().getString(R.string.ics_typegamenumber) + " " + _opponentName);

                final EditText input = new EditText(getContext());
                input.setInputType(InputType.TYPE_CLASS_NUMBER);
                input.setGravity(Gravity.CENTER_HORIZONTAL);
                InputFilter[] FilterArray = new InputFilter[1];
                FilterArray[0] = new InputFilter.LengthFilter(2);  // Max length of input
                input.setFilters(FilterArray);

                alertDialog.setView(input);
                alertDialog.setOnKeyListener(new OnKeyListener() {
                         @Override
                         public boolean onKey(DialogInterface dialog, int keyCode, KeyEvent event) {
                             if (keyCode == KeyEvent.KEYCODE_ENTER) {
                                 // set _sConsoleEditText for a match pattern
                                 _parent._sConsoleEditText = "smoves " + _opponentName + " " + input.getText();
                                 _parent.sendString("smoves " + _opponentName + " " + input.getText());
                                 alertDialog.dismiss();
                                 return true;
                             }
                             return false;
                         }
                     }
                );

                alertDialog.show();

                _tvPlayerListConsole.setText("");
            }
        });

        _editPlayerChat = (EditText)findViewById(R.id.EditPlayerChat);
        _editPlayerChat.setOnKeyListener(onKeyListen);
    }

    public void opponentName(String op){
        _opponentName = op.replaceAll("\\(\\w+\\)","");  // remove (FM), (GM), etc. (if applicable)
        _tvOpponentName.setText(_opponentName);

        _editPlayerChat.setHint(getContext().getResources().getString(R.string.ics_playerchat) + " " + _opponentName);
    }

}
