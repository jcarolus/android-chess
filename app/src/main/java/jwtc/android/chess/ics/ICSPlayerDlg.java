package jwtc.android.chess.ics;

import android.app.AlertDialog;
import android.app.Dialog;
import android.content.Context;
import android.content.DialogInterface;
import android.text.InputFilter;
import android.text.InputType;
import android.view.Gravity;
import android.view.KeyEvent;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.TextView;

import jwtc.android.chess.R;

public class ICSPlayerDlg extends Dialog {

    public static final String TAG = "ICSPlayerDlg";

    private String _opponentName;
    private ICSClient _parent;
    private Button _butHistory, _butFinger, _butMatch, _butFollow, _butUnfollow, _butSmoves;
    private TextView _tvOpponentName;

    public ICSPlayerDlg(Context context) {
        super(context);

        _parent = (ICSClient) context;

        setContentView(R.layout.ics_player);

        _tvOpponentName = (TextView) findViewById(R.id.tvopponentname);

        _butHistory = (Button) findViewById(R.id.ButHistory);
        _butHistory.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                _parent.sendString("History " + _opponentName);
                _parent.setConsoleView();
                ICSPlayerDlg.this.dismiss();
            }
        });

        _butFinger = (Button) findViewById(R.id.ButFinger);
        _butFinger.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                _parent.sendString("Finger " + _opponentName);
                _parent.setConsoleView();
                ICSPlayerDlg.this.dismiss();
            }
        });

        _butFollow = (Button) findViewById(R.id.ButFollow);
        _butFollow.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                _parent.sendString("follow " + _opponentName);
                ICSPlayerDlg.this.dismiss();
            }
        });

        _butUnfollow = (Button) findViewById(R.id.ButUnFollow);
        _butUnfollow.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                _parent.sendString("unfollow " + _opponentName);
                ICSPlayerDlg.this.dismiss();
            }
        });

        _butMatch = (Button) findViewById(R.id.ButMatch);
        _butMatch.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                _parent._dlgMatch._rbChallenge.setChecked(true);
                _parent._dlgMatch._rbChallenge.performClick();
                _parent._dlgMatch.setPlayer(_opponentName);
                _parent._dlgMatch.show();
                ICSPlayerDlg.this.dismiss();
            }
        });

        _butSmoves = (Button) findViewById(R.id.ButSmoves);
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
                             _parent.sendString("smoves " + _opponentName + " " + input.getText());
                             alertDialog.dismiss();
                             _parent.setConsoleView();
                             ICSPlayerDlg.this.dismiss();

                             return true;
                         }
                         return false;
                         }
                     }
                );

                alertDialog.show();

            }
        });

    }

    public void opponentName(String op) {
        _opponentName = op.replaceAll("\\(\\w+\\)", "");  // remove (FM), (GM), etc. (if applicable)
        _tvOpponentName.setText(_opponentName);

    }

}
