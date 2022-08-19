package jwtc.android.chess.ics;

import jwtc.android.chess.*;

import android.app.Dialog;
import android.content.Context;
import android.view.Gravity;
import android.view.View;
import android.widget.Button;
import android.widget.TextView;


// @TODO convert to ResultDialog without _parent
public class ICSGameOverDlg extends Dialog {

    public static final String TAG = "ICSGameOverDlg";

    private ICSClient _parent;
    private Button _butRematch, _butExamine, butSave, _butExit;
    private TextView _tvGameResult;
    private String handle;


    public ICSGameOverDlg(Context context) {
        super(context);

        _parent = (ICSClient) context;

        setContentView(R.layout.ics_over);

        setTitle(R.string.ics_game_over);

        _butExit = (Button) findViewById(R.id.ButtonGameExit);
        _butExit.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                ICSGameOverDlg.this.dismiss();
            }
        });

        _tvGameResult = (TextView) findViewById(R.id.tvGameResult);
        _tvGameResult.setGravity(Gravity.CENTER);


        _butRematch = (Button) findViewById(R.id.ButtonGameRematch);
        _butRematch.setOnClickListener(new View.OnClickListener() {

            @Override
            public void onClick(View view) {
                _parent.sendString("rematch");
                ICSGameOverDlg.this.dismiss();
            }
        });

        _butExamine = (Button) findViewById(R.id.ButtonGameExamine);
        _butExamine.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                _parent.sendString("examine " + handle + " -1"); // examine last game
                ICSGameOverDlg.this.dismiss();
            }
        });

        butSave = (Button) findViewById(R.id.ButtonGameSave);
        butSave.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                _parent.sendString("oldmoves " + handle);
                ICSGameOverDlg.this.dismiss();
            }
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
