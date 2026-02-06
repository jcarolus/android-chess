package jwtc.android.chess.ics;

import android.app.Dialog;
import android.content.Context;
import android.text.InputFilter;
import android.text.InputType;
import android.view.Gravity;
import android.view.KeyEvent;
import android.widget.EditText;
import android.widget.TextView;

import androidx.appcompat.app.AlertDialog;

import com.google.android.material.button.MaterialButton;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;

import jwtc.android.chess.R;

public class ICSPlayerDlg extends Dialog {

    public static final String TAG = "ICSPlayerDlg";

    private String _opponentName;
    private ICSClient _parent;
    private MaterialButton _butHistory, _butFinger, _butMatch, _butFollow, _butUnfollow, _butSmoves;
    private TextView _tvOpponentName;

    public ICSPlayerDlg(Context context) {
        super(context, R.style.ChessDialogTheme);

        _parent = (ICSClient) context;

        setContentView(R.layout.ics_player);

        _tvOpponentName = findViewById(R.id.tvopponentname);

        _butHistory = findViewById(R.id.ButHistory);
        _butHistory.setOnClickListener(v -> {
            _parent.sendString("History " + _opponentName);
            _parent.setConsoleView();
            ICSPlayerDlg.this.dismiss();
        });

        _butFinger = findViewById(R.id.ButFinger);
        _butFinger.setOnClickListener(v -> {
            _parent.sendString("Finger " + _opponentName);
            _parent.setConsoleView();
            ICSPlayerDlg.this.dismiss();
        });

        _butFollow = findViewById(R.id.ButFollow);
        _butFollow.setOnClickListener(v -> {
            _parent.sendString("follow " + _opponentName);
            ICSPlayerDlg.this.dismiss();
        });

        _butUnfollow = findViewById(R.id.ButUnFollow);
        _butUnfollow.setOnClickListener(v -> {
            _parent.sendString("unfollow " + _opponentName);
            ICSPlayerDlg.this.dismiss();
        });

        _butMatch = findViewById(R.id.ButMatch);
        _butMatch.setOnClickListener(v -> {
            _parent._dlgMatch._rbChallenge.setChecked(true);
            _parent._dlgMatch._rbChallenge.performClick();
            _parent._dlgMatch.setPlayer(_opponentName);
            _parent._dlgMatch.show();
            ICSPlayerDlg.this.dismiss();
        });

        _butSmoves = findViewById(R.id.ButSmoves);
        _butSmoves.setOnClickListener(v -> {
            final AlertDialog alertDialog = new MaterialAlertDialogBuilder(getContext()).create(); //Read Update
            alertDialog.setTitle(R.string.ics_historygamenumber);
            alertDialog.setMessage(getContext().getResources().getString(R.string.ics_typegamenumber) + " " + _opponentName);

            final EditText input = new EditText(getContext());
            input.setInputType(InputType.TYPE_CLASS_NUMBER);
            input.setGravity(Gravity.CENTER_HORIZONTAL);
            InputFilter[] FilterArray = new InputFilter[1];
            FilterArray[0] = new InputFilter.LengthFilter(2);  // Max length of input
            input.setFilters(FilterArray);

            alertDialog.setView(input);
            alertDialog.setOnKeyListener((dialog, keyCode, event) -> {
            if (keyCode == KeyEvent.KEYCODE_ENTER) {
                _parent.sendString("smoves " + _opponentName + " " + input.getText());
                alertDialog.dismiss();
                _parent.setConsoleView();
                ICSPlayerDlg.this.dismiss();

                return true;
            }
            return false;
            }
            );

            alertDialog.show();

        });

    }

    public void opponentName(String op) {
        _opponentName = op.replaceAll("\\(\\w+\\)", "");  // remove (FM), (GM), etc. (if applicable)
        _tvOpponentName.setText(_opponentName);

    }

}
