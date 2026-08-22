package jwtc.android.chess.lichess;

import android.content.Intent;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;
import android.view.View;
import android.widget.AdapterView;
import android.widget.ListView;
import android.widget.SimpleAdapter;
import android.widget.TextView;
import android.widget.ViewAnimator;

import com.google.android.material.button.MaterialButton;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import jwtc.android.chess.R;
import jwtc.android.chess.helpers.ActivityHelper;
import jwtc.android.chess.helpers.ResultDialogListener;
import jwtc.android.chess.lichess.models.Challenge;
import jwtc.android.chess.lichess.models.Game;

/**
 * Login + lobby: the OAuth entry point and the game list. This is the only Lichess activity that
 * runs the login flow (it keeps the oauth2redirect intent-filter and {@code singleTask}); the game
 * and swiss activities assume the shared api is already authenticated. Navigates to
 * {@link LichessGameActivity} (a game/puzzle) and {@link LichessSwissActivity} via startActivity.
 */
public class LichessLobbyActivity extends LichessBaseActivity
        implements ResultDialogListener<Map<String, Object>>, AdapterView.OnItemClickListener {
    private static final String TAG = "LichessLobbyActivity";
    private static final int VIEW_WAITING = 0, VIEW_LOGIN = 1, VIEW_LOBBY = 2;
    private static final int REQUEST_LOGIN = 1001;
    private static final long LOBBY_REFRESH_INTERVAL_MS = 60_000L;

    private ViewAnimator viewAnimatorRoot;
    private TextView textViewHandle, textViewLobbyStatus;
    private MaterialButton buttonSeek, buttonChallenge;
    private ListView listViewGames;
    private SimpleAdapter adapterGames;
    private final ArrayList<HashMap<String, String>> mapGames = new ArrayList<>();
    private List<Game> nowPlayingGames;

    private final Handler lobbyRefreshHandler = new Handler(Looper.getMainLooper());
    private final Runnable lobbyRefreshRunnable = new Runnable() {
        @Override
        public void run() {
            if (!isLobbyVisible()) {
                return;
            }
            lichessApi.playing();
            lobbyRefreshHandler.postDelayed(this, LOBBY_REFRESH_INTERVAL_MS);
        }
    };
    private final Runnable connectionRetryRunnable = () -> {
        if (!session.isConnected() || lichessApi == null) {
            return;
        }
        textViewLobbyStatus.setText("");
        lichessApi.event();
        lichessApi.playing();
    };

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        Log.d(TAG, "onCreate");
        setContentView(R.layout.lichess_lobby_activity);
        ActivityHelper.fixPaddings(this, findViewById(R.id.ViewAnimatorRoot));

        viewAnimatorRoot = findViewById(R.id.ViewAnimatorRoot);
        textViewHandle = findViewById(R.id.TextViewHandle);
        textViewLobbyStatus = findViewById(R.id.TextViewLobbyStatus);

        MaterialButton buttonLogin = findViewById(R.id.ButtonLogin);
        buttonLogin.setOnClickListener(v -> {
            if (lichessApi != null) {
                lichessApi.login(LichessLobbyActivity.this);
            }
        });

        MaterialButton buttonLogout = findViewById(R.id.ButtonLogout);
        buttonLogout.setOnClickListener(v -> {
            if (lichessApi != null) {
                lichessApi.logout();
            }
            finish();
        });

        buttonChallenge = findViewById(R.id.ButtonChallenge);
        buttonChallenge.setOnClickListener(v -> openChallengeDialog(ChallengeDialog.REQUEST_CHALLENGE));
        buttonSeek = findViewById(R.id.ButtonSeek);
        buttonSeek.setOnClickListener(v -> openChallengeDialog(ChallengeDialog.REQUEST_SEEK));

        MaterialButton buttonPuzzle = findViewById(R.id.ButtonPuzzle);
        buttonPuzzle.setOnClickListener(v -> openPuzzleDialog());

        MaterialButton buttonTeams = findViewById(R.id.ButtonTeams);
        buttonTeams.setOnClickListener(v -> startActivity(new Intent(this, LichessSwissActivity.class)));

        adapterGames = new SimpleAdapter(this, mapGames, R.layout.lichess_game_row,
            new String[]{"image_turn_white", "text_white", "image_turn_black", "text_black"},
            new int[]{R.id.image_turn_white, R.id.text_white, R.id.image_turn_black, R.id.text_black});
        listViewGames = findViewById(R.id.ListViewGames);
        listViewGames.setAdapter(adapterGames);
        listViewGames.setOnItemClickListener(this);
    }

    // The lobby owns OAuth, so it overrides the base auth flow: it must show the login screen (not
    // finish) when unauthenticated, and it may have a pending login redirect to complete.
    @Override
    public void onLichessApiReady(LichessApi api) {
        lichessApi = api;
        lichessApi.setApiListener(this);
        if (session.hasPendingData()) {
            api.handleLoginData(session.consumePendingData());
        } else {
            api.resume();
        }
        // Both paths drive onAuthenticate, which shows either the login screen or the lobby.
    }

    @Override
    protected void onResume() {
        super.onResume();
        startLobbyRefreshLoop();
    }

    @Override
    protected void onPause() {
        super.onPause();
        stopLobbyRefreshLoop();
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == REQUEST_LOGIN) {
            if (session.isConnected() && lichessApi != null) {
                lichessApi.handleLoginData(data);
            } else {
                session.setPendingData(data);
            }
        }
    }

    // --- View switching ---

    protected void displayLogin() {
        viewAnimatorRoot.setDisplayedChild(VIEW_LOGIN);
    }

    protected void displayLobby() {
        lichessApi.playing();
        viewAnimatorRoot.setDisplayedChild(VIEW_LOBBY);
        startLobbyRefreshLoop();
    }

    private boolean isLobbyVisible() {
        return session.isConnected()
            && lichessApi != null
            && viewAnimatorRoot != null
            && viewAnimatorRoot.getDisplayedChild() == VIEW_LOBBY;
    }

    private void startLobbyRefreshLoop() {
        stopLobbyRefreshLoop();
        if (!isLobbyVisible()) {
            return;
        }
        lobbyRefreshHandler.postDelayed(lobbyRefreshRunnable, LOBBY_REFRESH_INTERVAL_MS);
    }

    private void stopLobbyRefreshLoop() {
        lobbyRefreshHandler.removeCallbacks(lobbyRefreshRunnable);
        lobbyRefreshHandler.removeCallbacks(connectionRetryRunnable);
    }

    protected void openChallengeDialog(int requestCode) {
        ChallengeDialog dlg = new ChallengeDialog(this, this, requestCode, getPrefs());
        dlg.show();
    }

    protected void openPuzzleDialog() {
        PuzzleDialog dlg = new PuzzleDialog(this, this, getPrefs());
        dlg.show();
    }

    // --- LichessApiListener (auth + lobby callbacks) ---

    @Override
    public void onAuthenticate(String user) {
        Log.d(TAG, "onAuthenticate " + user);
        if (user != null) {
            textViewHandle.setText(user);
            displayLobby();
        } else {
            displayLogin();
        }
    }

    @Override
    public void onGameInit(String gameId, boolean boardCompatible) {
        lichessApi.playing();
        super.onGameInit(gameId, boardCompatible);
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
        lobbyRefreshHandler.removeCallbacks(connectionRetryRunnable);
        lobbyRefreshHandler.postDelayed(connectionRetryRunnable, 5000);
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

    // --- Challenge / seek / puzzle dialog results ---

    @Override
    public void OnDialogResult(int requestCode, Map<String, Object> data) {
        if (requestCode == PuzzleDialog.REQUEST_PUZZLE) {
            if (data != null) {
                Intent intent = new Intent(this, LichessGameActivity.class);
                intent.putExtra(LichessGameActivity.EXTRA_PUZZLE_ANGLE, (String) data.get("angle"));
                intent.putExtra(LichessGameActivity.EXTRA_PUZZLE_DIFFICULTY, (String) data.get("difficulty"));
                intent.putExtra(LichessGameActivity.EXTRA_PUZZLE_RATED,
                    data.get("rated") != null && (boolean) data.get("rated"));
                startActivity(intent);
            }
            return;
        }
        if (data == null) {
            buttonSeek.setEnabled(true);
        } else if (requestCode == ChallengeDialog.REQUEST_CHALLENGE) {
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
        if (parent == listViewGames && nowPlayingGames != null && nowPlayingGames.size() > position) {
            Game game = nowPlayingGames.get(position);
            if (game.compat == null || game.compat.board) {
                launchGame(game.gameId);
            } else {
                textViewLobbyStatus.setText(R.string.lichess_game_not_board_compatible);
            }
        }
    }
}
