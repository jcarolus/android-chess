package jwtc.android.chess.ics;

import jwtc.android.chess.*;

import android.app.Dialog;
import android.content.Context;
import android.util.Log;
import android.view.Gravity;
import android.view.View;
import android.widget.Button;
import android.widget.TextView;

import java.util.regex.Matcher;
import java.util.regex.Pattern;


public class ICSGameOverDlg extends Dialog {

    public static final String TAG = "ICSGameOverDlg";

    private ICSClient _parent;
    private Button _butGoodGame, _butRematch, _butExamine, _butClipBoard, _butSend, _butExit;
    private TextView _tvGameResult, _tvSendMessagesTitle;

    private Pattern _pattSmoves = Pattern.compile("[s|S]moves (\\w+) (\\d+)");
    private Matcher match;


    public ICSGameOverDlg(Context context) {
        super(context);

        _parent = (ICSClient)context;

        setContentView(R.layout.ics_over);

        setTitle(R.string.ics_game_over);

        _butExit = (Button)findViewById(R.id.ButtonGameExit);
        _butExit.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                ICSGameOverDlg.this.dismiss();
            }
        });

        _tvGameResult = (TextView)findViewById(R.id.tvGameResult);
        _tvGameResult.setGravity(Gravity.CENTER);

        _tvSendMessagesTitle= (TextView)findViewById(R.id.textView);

        _butGoodGame = (Button)findViewById(R.id.ButtonGameGoodGame);
        _butGoodGame.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                _parent.sendString("tell " + _parent.get_view().getOpponent() + " Good Game!");
                _parent.gameToast("You told " + _parent.get_view().getOpponent() + ", \"Good Game!\"", false);
            }
        });

        _butRematch = (Button)findViewById(R.id.ButtonGameRematch);
        _butRematch.setOnClickListener(new View.OnClickListener(){

            @Override
            public void onClick(View view) {
                _parent.sendString("rematch");
            }
        });

        _butExamine = (Button)findViewById(R.id.ButtonGameExamine);
        _butExamine.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {

                if(_parent._sConsoleEditText != null) {  // _parent._sConsoleEditText is last text typed in console
                    match = _pattSmoves.matcher(_parent._sConsoleEditText);
                    if (match.matches()) {  // match: smoves (player) (number)
                        _parent.sendString("examine " + match.group(1) + " " + match.group(2));
                        _parent._sConsoleEditText = "";
                        return;
                    }
                }

                try {
                    if(!_parent.get_whiteRating().equals("UNR")){  // can't examine unrated players
                        _parent.sendString("examine " + _parent.get_whiteHandle() + " -1"); // examine last game
                    }else if (!_parent.get_blackRating().equals("UNR")){
                        _parent.sendString("examine " + _parent.get_blackHandle() + " -1");
                    }
                } catch (Exception e){
                    _parent.doToast(e.toString());
                    Log.e(TAG, "Exception error ->" + e.toString());
                }
                ICSGameOverDlg.this.dismiss(); // close dialog
            }
        });

        _butClipBoard = (Button)findViewById(R.id.ButtonGameClipBoard);
        _butClipBoard.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                _parent.copyToClipBoard();
            }
        });

        _butSend = (Button)findViewById(R.id.ButtonGameSend);
        _butSend.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                _parent.SendToApp();
            }
        });
    }

    public void setWasPlaying(boolean bWasPlaying){
        _tvSendMessagesTitle.setVisibility((bWasPlaying ? View.VISIBLE : View.GONE));
        _butGoodGame.setVisibility(bWasPlaying ? View.VISIBLE : View.GONE);
        _butRematch.setVisibility(bWasPlaying ? View.VISIBLE : View.GONE);
    }

    public void updateGameResultText(String GRText){
        _tvGameResult.setText(GRText);
    }
}
