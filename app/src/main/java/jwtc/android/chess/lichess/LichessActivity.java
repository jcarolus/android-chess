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
import android.widget.AdapterView;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.ListView;
import android.widget.SimpleAdapter;
import android.widget.TextView;
import android.widget.ViewAnimator;

import androidx.core.content.ContextCompat;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import jwtc.android.chess.R;
import jwtc.android.chess.activities.ChessBoardActivity;
import jwtc.android.chess.helpers.ActivityHelper;
import jwtc.android.chess.helpers.ResultDialogListener;
import jwtc.android.chess.ics.ICSClient;
import jwtc.android.chess.lichess.models.Game;
import jwtc.android.chess.lichess.models.GameFull;
import jwtc.android.chess.services.ClockListener;
import jwtc.android.chess.services.LocalClockApi;
import jwtc.chess.board.BoardConstants;


public class LichessActivity extends ChessBoardActivity implements LichessApi.LichessApiListener, ClockListener, ResultDialogListener<Map<String, Object>>, AdapterView.OnItemClickListener {
    private static final String TAG = "LichessActivity";
    private static final int VIEW_ROOT_WAITING = 0, VIEW_ROOT_LOGIN = 1, VIEW_ROOT_SUB = 2;
    private static final int VIEW_SUB_LOBBY = 0, VIEW_SUB_PLAY = 1;

    private LichessApi lichessApi;
    private LocalClockApi localClockApi = new LocalClockApi();
    private ViewAnimator viewAnimatorRoot, viewAnimatorSub;

    private ImageView imageTurnOpp, imageTurnMe;
    private TextView textViewClockOpp, textViewPlayerOpp, textViewRatingOpp;
    private TextView textViewClockMe, textViewPlayerMe, textViewRatingMe;
    private TextView textViewLastMove, textViewStatus, textViewOfferDraw;
    private TextView textViewHandle;
    private ListView listViewGames;
    private SimpleAdapter adapterGames;

    private ArrayList<HashMap<String, String>> mapGames = new ArrayList<HashMap<String, String>>();
    private List<Game> nowPlayingGames;

    private final ServiceConnection mConnection = new ServiceConnection() {
        public void onServiceConnected(ComponentName className, IBinder service) {
            Log.i(TAG, "onServiceConnected");
            LichessService lichessService = ((LichessService.LocalBinder)service).getService();
            lichessApi.setAuth(lichessService.getAuth());
            lichessApi.resume();
        }

        public void onServiceDisconnected(ComponentName className) {

            Log.i(TAG, "onServiceDisconnected");
            // lichessApi.setAuth(null);
            lichessApi.setApiListener(null);
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

        Button buttonResign = findViewById(R.id.ButtonResign);
        buttonResign.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                lichessApi.resign();
            }
        });

        Button buttonDraw = findViewById(R.id.ButtonDraw);
        buttonDraw.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                lichessApi.draw(true);
            }
        });

        localClockApi.addListener(this);

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
        textViewStatus = findViewById(R.id.TextViewStatus);
        textViewOfferDraw = findViewById(R.id.TextViewOfferDraw);

        textViewHandle = findViewById(R.id.TextViewHandle);

        adapterGames = new SimpleAdapter(LichessActivity.this, mapGames, R.layout.lichess_game_row,
                new String[]{"image_turn_white", "text_white", "image_turn_black", "text_black"},
                new int[]{R.id.image_turn_white, R.id.text_white, R.id.image_turn_black, R.id.text_black});

        listViewGames = findViewById(R.id.ListViewGames);
        listViewGames.setAdapter(adapterGames);
        listViewGames.setOnItemClickListener(this);

        afterCreate();
    }

    @Override
    protected void onResume() {
        super.onResume();
        Log.i(TAG, "onResume");
        lichessApi.setApiListener(LichessActivity.this);
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
    public void onAuthenticate(String user) {
        Log.d(TAG, "onAuthenticate " + user);

        if (user != null) {
            textViewHandle.setText(user);
            displayLobby();
            lichessApi.event();
        } else {
            displayLogin();
        }
    }

    @Override
    public void onGameInit(String gameId) {
        openGame(gameId);
    }

    @Override
    public void onGameUpdate(GameFull gameFull) {
        int myTurn = lichessApi.getMyTurn();
        int turn = lichessApi.getTurn();
        boolean isMyTurn = myTurn == turn;
        boolean playAsWhite = myTurn == BoardConstants.WHITE;
        textViewPlayerOpp.setText(playAsWhite ? gameFull.black.name : gameFull.white.name);
        textViewPlayerMe.setText(playAsWhite ? gameFull.white.name : gameFull.black.name);

        textViewRatingOpp.setText(""  + (playAsWhite ? gameFull.black.rating : gameFull.white.rating));
        textViewRatingMe.setText("" + (playAsWhite ? gameFull.white.rating : gameFull.black.rating));

        if (gameFull.clock != null) {
            localClockApi.startClock(gameFull.clock.increment, gameFull.state.wtime, gameFull.state.btime, turn, System.currentTimeMillis());
        }
        textViewStatus.setText(gameFull.state.status + " " + (gameFull.state.winner != null ? "Winner: " + gameFull.state.winner : ""));

        boolean isDrawOffer = playAsWhite ? gameFull.state.bdraw : gameFull.state.wdraw;
        if (isDrawOffer) {
            textViewOfferDraw.setText("Your opponent offers a draw. Tap Draw to accept");
            textViewOfferDraw.setBackgroundColor(ContextCompat.getColor(this, R.color.secondaryColor));
        } else {
            textViewOfferDraw.setText("");
            textViewOfferDraw.setBackgroundColor(ContextCompat.getColor(this, R.color.primaryColor));
        }
    }

    @Override
    public void onGameFinish() {
        localClockApi.stopClock();
    }

    @Override
    public void onInvalidMove(String reason) {
        textViewStatus.setText(reason);
    }

    @Override
    public void onNowPlaying(List<Game> games, String me) {
        Log.d(TAG, "onNowPlaying " + games.size());
        nowPlayingGames = games;
        mapGames.clear();
        for (int i = 0; i < games.size(); i++) {
            Game game = games.get(i);
            HashMap<String, String> gameMap = new HashMap<>();
            if (game.color.equals("white")) {
                gameMap.put("image_turn_white", "" + (game.isMyTurn ? R.drawable.turnwhite : R.drawable.turnempty));
                gameMap.put("text_white", me);
                gameMap.put("text_black", game.opponent.username);
            } else {
                gameMap.put("image_turn_white", "" + (game.isMyTurn ? R.drawable.turnempty : R.drawable.turnwhite));
                gameMap.put("text_white", game.opponent.username);
                gameMap.put("text_black", me);
            }
            mapGames.add(gameMap);
        }
        adapterGames.notifyDataSetChanged();
    }

    @Override
    public boolean requestMove(int from, int to) {
        if (lichessApi.getMyTurn() == lichessApi.getTurn()) {
            lastMoveFrom = from;
            lastMoveTo = to;

            lichessApi.move(from, to);

            return false;
        }
        rebuildBoard();
        return false;
    }

    @Override
    public void OnState() {
        super.OnState();

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

        chessBoardView.setRotated(myTurn == BoardConstants.BLACK);

        textViewLastMove.setText(getLastMoveAndTurnDescription());
    }

    protected void displayLogin() {
        viewAnimatorRoot.setDisplayedChild(VIEW_ROOT_LOGIN);
    }
    protected void displayLobby() {
        lichessApi.playing();
        viewAnimatorRoot.setDisplayedChild(VIEW_ROOT_SUB);
        viewAnimatorSub.setDisplayedChild(VIEW_SUB_LOBBY);
    }

    protected void displayCreateChallenge() {
        viewAnimatorRoot.setDisplayedChild(VIEW_ROOT_SUB);
        ChallengeDialog dlg = new ChallengeDialog(this, this, 10, getPrefs());
        dlg.show();
    }
    protected void displayPlay() {
        // @TOD reset info?
        viewAnimatorRoot.setDisplayedChild(VIEW_ROOT_SUB);
        viewAnimatorSub.setDisplayedChild(VIEW_SUB_PLAY);
    }

    protected void openGame(String gameId) {
        lichessApi.game(gameId);
        displayPlay();
    }

    @Override
    public boolean needExitConfirmationDialog() {
        return true;
    }

    @Override
    public void showExitConfirmationDialog() {
        if (viewAnimatorRoot.getDisplayedChild() == VIEW_ROOT_SUB && viewAnimatorSub.getDisplayedChild() == VIEW_SUB_PLAY) {
            displayLobby();
        } else {
            finish();
        }
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

    @Override
    public void OnDialogResult(int requestCode, Map<String, Object> data) {
        lichessApi.challenge(data);
    }

    @Override
    public void onItemClick(AdapterView<?> parent, View view, int position, long id) {
        Log.d(TAG, "onItemClick");
        if (parent == listViewGames) {
            Log.d(TAG, "listViewGames " + nowPlayingGames.size() + " " + position);
            if (nowPlayingGames.size() > position) {
                Game game = nowPlayingGames.get(position);
                lichessApi.game(game.gameId);
                displayPlay();
            }
        }
    }
}
