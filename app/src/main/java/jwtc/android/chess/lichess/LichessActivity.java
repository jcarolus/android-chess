package jwtc.android.chess.lichess;

import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.ServiceConnection;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.os.IBinder;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.ViewAnimator;

import jwtc.android.chess.R;
import jwtc.android.chess.activities.ChessBoardActivity;
import jwtc.android.chess.helpers.ActivityHelper;
import jwtc.android.chess.lichess.models.Game;
import jwtc.android.chess.lichess.models.GameFull;
import jwtc.android.chess.services.ClockListener;
import jwtc.android.chess.services.LocalClockApi;
import jwtc.chess.board.BoardConstants;


public class LichessActivity extends ChessBoardActivity implements LichessApi.LichessApiListener, ClockListener {
    private static final String TAG = "LichessActivity";
    private static final int VIEW_ROOT_WAITING = 0, VIEW_ROOT_LOGIN = 1, VIEW_ROOT_SUB = 2;
    private static final int VIEW_SUB_LOBBY = 0, VIEW_SUB_CHALLENGE = 1, VIEW_SUB_PLAY = 2;

    private LichessApi lichessApi;
    private LocalClockApi localClockApi = new LocalClockApi();
    private ViewAnimator viewAnimatorRoot, viewAnimatorSub;

    private ImageView imageTurnOpp, imageTurnMe;
    private TextView textViewClockOpp, textViewPlayerOpp, textViewRatingOpp;
    private TextView textViewClockMe, textViewPlayerMe, textViewRatingMe;
    private TextView textViewLastMove;

    private final ServiceConnection mConnection = new ServiceConnection() {
        public void onServiceConnected(ComponentName className, IBinder service) {
            Log.i(TAG, "onServiceConnected");
            LichessService lichessService = ((LichessService.LocalBinder)service).getService();
            lichessApi.setAuth(lichessService.getAuth());
            lichessApi.setApiListener(LichessActivity.this);

            lichessApi.resume();
        }

        public void onServiceDisconnected(ComponentName className) {

            Log.i(TAG, "onServiceDisconnected");
            lichessApi.setAuth(null);
        }
    };

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        Log.d(TAG, "onCreate");

        setContentView(R.layout.lichess_main);

        ActivityHelper.fixPaddings(this, findViewById(R.id.ViewAnimatorRoot));

        gameApi = new LichessApi();
        lichessApi = (LichessApi)gameApi;

        Button buttonLogin = findViewById(R.id.ButtonLogin);
        buttonLogin.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                lichessApi.login(LichessActivity.this);
            }
        });

        Button buttonChallenge = findViewById(R.id.ButtonChallenge);
        buttonChallenge.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                displayCreateChallenge();
            }
        });

        Button buttonLogout = findViewById(R.id.ButtonLogout);
        buttonLogout.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                lichessApi.logout();
            }
        });

        Button buttonChallengeOk = findViewById(R.id.ButtonChallengeOk);
        buttonChallengeOk.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                lichessApi.challenge();
            }
        });

        Button buttonResign = findViewById(R.id.ButtonResign);
        buttonResign.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                lichessApi.resign();
            }
        });

        //

        viewAnimatorRoot = findViewById(R.id.ViewAnimatorRoot);
        viewAnimatorSub = findViewById(R.id.ViewAnimatorSub);

        imageTurnOpp = findViewById(R.id.ImageTurnOpp);
        textViewClockOpp = findViewById(R.id.TextViewClockOpp);
        textViewPlayerOpp = findViewById(R.id.TextViewPlayerOpp);
        textViewRatingOpp = findViewById(R.id.TextViewRatingOpp);

        imageTurnMe = findViewById(R.id.ImageTurnMe);
        textViewClockMe = findViewById(R.id.TextViewClockMe);
        textViewPlayerMe = findViewById(R.id.TextViewPlayerMe);
        textViewRatingMe = findViewById(R.id.TextViewRatingMe);

        textViewLastMove = findViewById(R.id.TextViewLastMove);

        afterCreate();
    }

    @Override
    protected void onResume() {
        super.onResume();
        Log.i(TAG, "onResume");

    }

    @Override
    protected void onPause() {
        super.onPause();

        SharedPreferences.Editor editor = this.getPrefs().edit();
        editor.commit();
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);

        Log.d(TAG, "onActivityResult " + requestCode);
        if (requestCode == 1001) {
            lichessApi.handleLoginData(data);
        }
    }

    @Override
    protected void onStart() {
        Log.d(TAG, "onStart");
        super.onStart();

        startService(new Intent(LichessActivity.this, LichessService.class));
        bindService(new Intent(LichessActivity.this, LichessService.class), mConnection, Context.BIND_AUTO_CREATE);
    }

    @Override
    protected void onStop() {
        Log.i(TAG, "onStop");
        super.onStop();

        unbindService(mConnection);
    }

    @Override
    public void onAuthenticate(boolean authenticated) {
        Log.d(TAG, "onAuthenticate " + authenticated);

        if (authenticated) {
            displayLobby();
        } else {
            displayLogin();
        }
    }

    @Override
    public void onGameInit(Game game) {

    }

    @Override
    public void onGameUpdate(GameFull gameFull) {
        Log.d(TAG, "black " + gameFull.black.name);

        int myTurn = lichessApi.getMyTurn();
        int turn = lichessApi.getTurn();
        boolean isMyTurn = myTurn == turn;
        boolean playAsWhite = myTurn == BoardConstants.WHITE;
        textViewPlayerOpp.setText(playAsWhite ? gameFull.black.name : gameFull.white.name);
        textViewPlayerMe.setText(playAsWhite ? gameFull.white.name : gameFull.black.name);

        textViewRatingOpp.setText(""  + (playAsWhite ? gameFull.black.rating : gameFull.white.rating));
        textViewRatingMe.setText("" + (playAsWhite ? gameFull.white.rating : gameFull.black.rating));

        localClockApi.startClock(gameFull.clock.increment, gameFull.state.wtime, gameFull.state.btime, 0, System.currentTimeMillis());

    }

    @Override
    public void onGameFinish() {
        localClockApi.stopClock();
    }

    @Override
    public void onInvalidMove(String reason) {
        textViewLastMove.setText(reason);
    }

    @Override
    public boolean requestMove(int from, int to) {
        lastMoveFrom = from;
        lastMoveTo = to;

        lichessApi.move(from, to);

        return true;
    }

    @Override
    public void OnState() {
        super.OnState();

        displayPlay();

        int myTurn = lichessApi.getMyTurn();
        int turn = lichessApi.getTurn();
        boolean isMyTurn = myTurn == turn;
        imageTurnOpp.setImageResource(isMyTurn
                ? R.drawable.turnempty
                : turn == BoardConstants.BLACK
                ? R.drawable.turnblack
                : R.drawable.turnwhite
        );

        imageTurnMe.setImageResource(isMyTurn
                ? turn == BoardConstants.BLACK
                ? R.drawable.turnblack
                : R.drawable.turnwhite
                : R.drawable.turnempty
        );
    }

    protected void displayLogin() {
        viewAnimatorRoot.setDisplayedChild(VIEW_ROOT_LOGIN);
    }
    protected void displayLobby() {
        viewAnimatorRoot.setDisplayedChild(VIEW_ROOT_SUB);
        viewAnimatorSub.setDisplayedChild(VIEW_SUB_LOBBY);
    }

    protected void displayCreateChallenge() {
        viewAnimatorRoot.setDisplayedChild(VIEW_ROOT_SUB);
        viewAnimatorSub.setDisplayedChild(VIEW_SUB_CHALLENGE);
    }
    protected void displayPlay() {
        viewAnimatorRoot.setDisplayedChild(VIEW_ROOT_SUB);
        viewAnimatorSub.setDisplayedChild(VIEW_SUB_PLAY);
    }

    @Override
    public void OnClockTime() {
        int myTurn = lichessApi.getMyTurn();

        boolean playAsWhite = myTurn == BoardConstants.WHITE;
        String blackRemaining = localClockApi.getBlackRemainingTime();
        String whiteRemaining = localClockApi.getWhiteRemainingTime();

        textViewClockOpp.setText(playAsWhite ? blackRemaining : whiteRemaining);
        textViewClockMe.setText(playAsWhite ? whiteRemaining : blackRemaining);
    }
}
