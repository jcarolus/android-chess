package jwtc.android.chess.ics;

import jwtc.android.chess.*;

import android.app.Dialog;
import android.content.Context;
import android.view.Gravity;
import android.view.View;
import android.widget.TextView;

import com.google.android.material.button.MaterialButton;


// @TODO convert to ResultDialog without _parent
public class ICSGameOverDlg extends Dialog {

    public static final String TAG = "ICSGameOverDlg";

    private ICSClient _parent;
    private MaterialButton _butRematch, _butExamine, butSave, _butExit;
    private TextView _tvGameResult;
    private String handle;


    public ICSGameOverDlg(Context context) {
        super(context, R.style.ChessDialogTheme);

        _parent = (ICSClient) context;

        setContentView(R.layout.ics_over);

        setTitle(R.string.ics_game_over);

        _butExit = findViewById(R.id.ButtonGameExit);
        _butExit.setOnClickListener(view -> ICSGameOverDlg.this.dismiss());

        _tvGameResult = findViewById(R.id.tvGameResult);
        _tvGameResult.setGravity(Gravity.CENTER);


        _butRematch = findViewById(R.id.ButtonGameRematch);
        _butRematch.setOnClickListener(view -> {
            _parent.sendString("rematch");
            ICSGameOverDlg.this.dismiss();
        });

        _butExamine = findViewById(R.id.ButtonGameExamine);
        _butExamine.setOnClickListener(v -> {
            _parent.sendString("examine " + handle + " -1"); // examine last game
            ICSGameOverDlg.this.dismiss();
        });

        butSave = findViewById(R.id.ButtonGameSave);
        butSave.setOnClickListener(view -> {
            _parent.sendString("oldmoves " + handle);
            ICSGameOverDlg.this.dismiss();
        });
    }

    public void setHandle(String handle) {
        this.handle = handle;
        _butExamine.setVisibility(this.handle.startsWith("Guest") ? View.INVISIBLE : View.VISIBLE);
    }

    public void setWasPlaying(boolean bWasPlaying) {
        _butRematch.setVisibility(bWasPlaying ? View.VISIBLE : View.GONE);
    }

    public void updateGameResultText(String GRText) {
        _tvGameResult.setText(GRText);
    }
}
