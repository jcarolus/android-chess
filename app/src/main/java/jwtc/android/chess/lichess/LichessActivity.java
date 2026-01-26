package jwtc.android.chess.lichess;

import static jwtc.android.chess.helpers.ActivityHelper.pulseAnimation;

import android.app.AlertDialog;
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
import android.widget.LinearLayout;
import android.widget.ListView;
import android.widget.SimpleAdapter;
import android.widget.TextView;
import android.widget.ViewAnimator;

import com.google.android.material.switchmaterial.SwitchMaterial;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import jwtc.android.chess.R;
import jwtc.android.chess.activities.ChessBoardActivity;
import jwtc.android.chess.helpers.ActivityHelper;
import jwtc.android.chess.helpers.ResultDialogListener;
import jwtc.android.chess.lichess.models.Challenge;
import jwtc.android.chess.lichess.models.Game;
import jwtc.android.chess.lichess.models.GameFull;
import jwtc.android.chess.services.ClockListener;
import jwtc.android.chess.services.LocalClockApi;
import jwtc.chess.Move;
import jwtc.chess.Pos;
import jwtc.chess.board.BoardConstants;


public class LichessActivity extends ChessBoardActivity implements LichessApi.LichessApiListener, ClockListener, ResultDialogListener<Map<String, Object>>, AdapterView.OnItemClickListener {
    private static final String TAG = "LichessActivity";
    private static final int VIEW_ROOT_WAITING = 0, VIEW_ROOT_LOGIN = 1, VIEW_ROOT_SUB = 2;
    private static final int VIEW_SUB_LOBBY = 0, VIEW_SUB_PLAY = 1;

    private LichessApi lichessApi;
    private LocalClockApi localClockApi = new LocalClockApi();
    private ViewAnimator viewAnimatorRoot, viewAnimatorSub;
    private LinearLayout layoutConfirm, layoutResignDraw;
    private SwitchMaterial switchConfirmMoves;

    private ImageView imageTurnOpp, imageTurnMe;
    private TextView textViewClockOpp, textViewPlayerOpp, textViewRatingOpp;
    private TextView textViewClockMe, textViewPlayerMe, textViewRatingMe;
    private TextView textViewLastMove, textViewStatus, textViewOfferDraw, textViewWhitePieces, textViewBlackPieces;
    private TextView textViewLobbyStatus;
    private TextView textViewHandle;
    private Button buttonDraw, buttonResign, buttonSeek, buttonChallenge, buttonConfirmMove;
    private ListView listViewGames;
    private SimpleAdapter adapterGames;

    private ArrayList<HashMap<String, String>> mapGames = new ArrayList<HashMap<String, String>>();
    private List<Game> nowPlayingGames;
    private Intent pendingData;
    private boolean serviceConnected = false;

    private final ServiceConnection mConnection = new ServiceConnection() {
        public void onServiceConnected(ComponentName className, IBinder service) {
            Log.i(TAG, "onServiceConnected");
            serviceConnected = true;
            LichessService lichessService = ((LichessService.LocalBinder)service).getService();
            lichessApi.setAuth(lichessService.getAuth());

            if (pendingData != null) {
                handleActivityResult(pendingData);
            } else {
                lichessApi.resume();
            }
        }

        public void onServiceDisconnected(ComponentName className) {
            Log.i(TAG, "onServiceDisconnected");
            serviceConnected = false;
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
        buttonLogin.setOnClickListener(v -> lichessApi.login(LichessActivity.this));

        buttonChallenge = findViewById(R.id.ButtonChallenge);
        buttonChallenge.setOnClickListener(v -> openChallengeDialog(ChallengeDialog.REQUEST_CHALLENGE));
        buttonSeek = findViewById(R.id.ButtonSeek);
        buttonSeek.setOnClickListener(v -> openChallengeDialog(ChallengeDialog.REQUEST_SEEK));

        buttonResign = findViewById(R.id.ButtonResign);
        buttonResign.setOnClickListener(v -> {
            openConfirmDialog(getString(R.string.lichess_confirm_resign),
                    getString(R.string.lichess_play_button_resign),
                    getString(R.string.button_cancel),
                    () -> lichessApi.resign(), null);

        });

        Button buttonLogout = findViewById(R.id.ButtonLogout);
        buttonLogout.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                lichessApi.logout();
                finish();
            }
        });

        Button buttonCancelMove = findViewById(R.id.ButtonCancelMove);
        buttonCancelMove.setOnClickListener(v -> {
            layoutConfirm.setVisibility(View.GONE);
            layoutResignDraw.setVisibility(View.VISIBLE);
            rebuildBoard();
        });

        buttonDraw = findViewById(R.id.ButtonDraw);
        buttonConfirmMove = findViewById(R.id.ButtonConfirmMove);

        localClockApi.addListener(this);

        switchConfirmMoves = findViewById(R.id.SwitchConfirmMoves);
        layoutResignDraw = findViewById(R.id.LayoutResignDraw);
        layoutConfirm = findViewById(R.id.LayoutConfirm);

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
        textViewWhitePieces = findViewById(R.id.TextViewWhitePieces);
        textViewBlackPieces = findViewById(R.id.TextViewBlackPieces);

        textViewHandle = findViewById(R.id.TextViewHandle);
        textViewLobbyStatus = findViewById(R.id.TextViewLobbyStatus);

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
        SharedPreferences prefs = this.getPrefs();
        lichessApi.setApiListener(LichessActivity.this);

        layoutConfirm.setVisibility(View.GONE);
        layoutResignDraw.setVisibility(View.VISIBLE);
        switchConfirmMoves.setChecked(prefs.getBoolean("lichess_confirm_moves", false));
    }

    @Override
    protected void onPause() {
        super.onPause();

        SharedPreferences.Editor editor = this.getPrefs().edit();

        editor.putBoolean("lichess_confirm_moves", switchConfirmMoves.isChecked());

        editor.commit();
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);

        Log.d(TAG, "onActivityResult " + requestCode);
        if (requestCode == 1001) {
            if (serviceConnected) {
                handleActivityResult(data);
            } else {
                pendingData = data;
            }
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
            lichessApi.event();
            displayLobby();
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
        boolean playAsWhite = myTurn == BoardConstants.WHITE;
        boolean isStarted = gameFull.state.status.equals("started");
        textViewPlayerOpp.setText(playAsWhite ? gameFull.black.name : gameFull.white.name);
        textViewPlayerMe.setText(playAsWhite ? gameFull.white.name : gameFull.black.name);

        textViewRatingOpp.setText(""  + (playAsWhite ? gameFull.black.rating : gameFull.white.rating));
        textViewRatingMe.setText("" + (playAsWhite ? gameFull.white.rating : gameFull.black.rating));

        if (gameFull.clock != null && isStarted) {
            localClockApi.startClock(gameFull.clock.increment, gameFull.state.wtime, gameFull.state.btime, turn, System.currentTimeMillis());
        }
        buttonDraw.setEnabled(isStarted);
        buttonResign.setEnabled(isStarted);

        String stateMessage = gameStateToTranslated(gameFull.state.status);
        if (gameFull.state.winner != null){
            stateMessage += ". " + getString(R.string.lichess_game_winner, gameFull.state.winner);
        }
        textViewStatus.setText(stateMessage);

        boolean isDrawOffer = playAsWhite ? gameFull.state.bdraw : gameFull.state.wdraw;
        if (isDrawOffer) {
            textViewOfferDraw.setText(R.string.lichess_opponent_offers_draw);
            pulseAnimation(buttonDraw, 1.05f, 1);
            buttonDraw.setOnClickListener(v -> lichessApi.draw(true));
        } else {
            textViewOfferDraw.setText("");
            buttonDraw.setOnClickListener(v -> {
                openConfirmDialog(getString(R.string.lichess_confirm_offer_draw),
                        getString(R.string.lichess_play_button_draw),
                        getString(R.string.button_cancel),
                        () -> lichessApi.draw(true),
                        null);

            });
        }
    }

    @Override
    public void onGameFinish() {
        localClockApi.stopClock();
    }

    @Override
    public void onGameDisconnected() {
        textViewLobbyStatus.setText(R.string.lichess_game_disconnected);
        displayLobby();
    }

    @Override
    public void onInvalidMove(String reason) {
        textViewStatus.setText(reason);
    }

    @Override
    public void onNowPlaying(List<Game> games, String me) {
        Log.d(TAG, "onNowPlaying " + games.size());
        textViewLobbyStatus.setText(R.string.lichess_lobby_connected);
        nowPlayingGames = games;
        mapGames.clear();
        for (int i = 0; i < games.size(); i++) {
            Game game = games.get(i);
            HashMap<String, String> gameMap = new HashMap<>();
            if (game.color.equals("white")) {
                gameMap.put("image_turn_white", "" + (game.isMyTurn ? R.drawable.turnwhite : R.drawable.turnempty));
                gameMap.put("image_turn_black", "" + (game.isMyTurn ? R.drawable.turnempty : R.drawable.turnblack));
                gameMap.put("text_white", me);
                gameMap.put("text_black", game.opponent.username);
            } else {
                gameMap.put("image_turn_white", "" + (game.isMyTurn ? R.drawable.turnempty : R.drawable.turnwhite));
                gameMap.put("image_turn_black", "" + (game.isMyTurn ? R.drawable.turnblack : R.drawable.turnempty));
                gameMap.put("text_white", game.opponent.username);
                gameMap.put("text_black", me);
            }
            mapGames.add(gameMap);
        }
        adapterGames.notifyDataSetChanged();
    }

    @Override
    public void onConnectionError() {
        textViewLobbyStatus.setText(R.string.lichess_games_connection_error_retry);
        new java.util.Timer().schedule(
            new java.util.TimerTask() {
                @Override
                public void run() {
                    textViewLobbyStatus.post(() -> {
                        textViewLobbyStatus.setText("");
                    });
                    lichessApi.event();
                    lichessApi.playing();
                }
            }, 5000
        );
    }

    @Override
    public void onChallenge(Challenge challenge) {
        // no challenge disruption while playing
        if (viewAnimatorSub.getDisplayedChild() != VIEW_SUB_PLAY) {
            int minutes = challenge.timeControl.limit / 60;

            String message = challenge.challenger.name +
                    (challenge.rated ? " " + getString(R.string.lichess_challenge_dialog_message_rating) + "\n" : "\n") +
                    getString(R.string.lichess_challenge_dialog_message_variant, challenge.variant.name) + "\n" +
                    getString(R.string.lichess_challenge_dialog_message_time_control, challenge.timeControl.type) + "\n" +
                    (challenge.timeControl.limit > 0 ? " " + minutes + "+" + challenge.timeControl.increment : "") + "\n" +
                    (challenge.rated ? getString(R.string.lichess_challenge_dialog_message_rated) : getString(R.string.lichess_challenge_dialog_message_unrated));

            openConfirmDialog(message,
                    getString(R.string.lichess_challenge_dialog_button_accept),
                    getString(R.string.lichess_challenge_dialog_button_decline),
                    () -> lichessApi.acceptChallenge(challenge),
                    () -> lichessApi.declineChallenge(challenge));
        }
    }

    @Override
    public void onChallengeCancelled(Challenge challenge) {
        textViewLobbyStatus.setText(getString(R.string.lichess_challenge_by_cancelled, challenge.challenger.name));
    }

    @Override
    public void onChallengeDeclined(Challenge challenge) {
        textViewLobbyStatus.setText(getString(R.string.lichess_challenge_by_declined, challenge.challenger.name));
    }

    @Override
    public void onMyChallengeCancelled() {
        buttonChallenge.setEnabled(true);
        textViewLobbyStatus.setText(R.string.lichess_my_challenge_closed);
    }

    @Override
    public void onMySeekCancelled() {
        buttonSeek.setEnabled(true);
        textViewLobbyStatus.setText(R.string.lichess_my_seek_closed);
    }

    @Override
    public boolean requestMove(int from, int to) {
        if (lichessApi.getMyTurn() == lichessApi.getTurn()) {
            lastMoveFrom = from;
            lastMoveTo = to;

            if (lichessApi.isPromotionMove(from, to)) {
                final String[] items = getResources().getStringArray(R.array.promotionpieces);

                AlertDialog.Builder builder = new AlertDialog.Builder(this);
                builder.setTitle(R.string.title_pick_promo);
                builder.setCancelable(false);
                builder.setSingleChoiceItems(items, 0, (dialog, item) -> {
                    dialog.dismiss();
                    lichessApi.setPromotionPiece(4 - item);
                    lichessApi.move(from, to);
                });
                AlertDialog alert = builder.create();
                alert.show();

                return true;
            } else if (switchConfirmMoves.isChecked()) {
                layoutConfirm.setVisibility(View.VISIBLE);
                layoutResignDraw.setVisibility(View.GONE);
                buttonConfirmMove.setText(getString(R.string.lichess_game_confirm_move, Pos.toString(from) + " " + Pos.toString(to)));
                pulseAnimation(buttonConfirmMove, 1.05f, 1);
                buttonConfirmMove.setOnClickListener(v -> {
                    lichessApi.move(from, to);
                    layoutConfirm.setVisibility(View.GONE);
                    layoutResignDraw.setVisibility(View.VISIBLE);
                });
            } else {
                lichessApi.move(from, to);
            }
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
        textViewWhitePieces.setText(getPiecesDescription(BoardConstants.WHITE));
        textViewBlackPieces.setText(getPiecesDescription(BoardConstants.BLACK));
    }

    protected void displayLogin() {
        viewAnimatorRoot.setDisplayedChild(VIEW_ROOT_LOGIN);
    }
    protected void displayLobby() {
        lichessApi.playing();
        viewAnimatorRoot.setDisplayedChild(VIEW_ROOT_SUB);
        viewAnimatorSub.setDisplayedChild(VIEW_SUB_LOBBY);
    }

    protected void displayPlay() {
        // reset info
        textViewLastMove.setText("");
        textViewStatus.setText("");
        textViewOfferDraw.setText("");
        viewAnimatorRoot.setDisplayedChild(VIEW_ROOT_SUB);
        viewAnimatorSub.setDisplayedChild(VIEW_SUB_PLAY);
    }

    protected void openChallengeDialog(int requestCode) {
        ChallengeDialog dlg = new ChallengeDialog(this, this, requestCode, getPrefs());
        dlg.show();
    }

    protected void openGame(String gameId) {
        lichessApi.game(gameId);
        displayPlay();
    }

    protected String gameStateToTranslated(String state) {
        if (state.equals("created")) {
            return getString(R.string.lichess_game_state_created);
        } else if (state.equals("started")) {
            return getString(R.string.lichess_game_state_started);
        } else if (state.equals("aborted")) {
            return getString(R.string.lichess_game_state_aborted);
        } else if (state.equals("mate")) {
            return getString(R.string.lichess_game_state_mate);
        } else if (state.equals("resign")) {
            return getString(R.string.lichess_game_state_resigned);
        }

        return "";
    }

    protected void handleActivityResult(Intent data) {
        lichessApi.handleLoginData(data);
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
        if (data == null) {
            buttonSeek.setEnabled(true);
        }
        else if (requestCode == ChallengeDialog.REQUEST_CHALLENGE) {
            textViewLobbyStatus.setText(R.string.lichess_challenge_posted);
            lichessApi.challenge(data);
        } else {
            buttonSeek.setEnabled(false);
            textViewLobbyStatus.setText(R.string.lichess_seek_posted);
            lichessApi.seek(data);
        }
    }

    @Override
    public void onItemClick(AdapterView<?> parent, View view, int position, long id) {
        if (parent == listViewGames && nowPlayingGames.size() > position) {
            Game game = nowPlayingGames.get(position);
            lichessApi.game(game.gameId);
            displayPlay();
        }
    }
}
