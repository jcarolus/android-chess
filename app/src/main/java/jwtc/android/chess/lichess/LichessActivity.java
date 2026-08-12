package jwtc.android.chess.lichess;

import static jwtc.android.chess.helpers.ActivityHelper.pulseAnimation;

import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.ServiceConnection;
import android.content.SharedPreferences;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.util.Log;
import android.view.View;
import android.widget.AdapterView;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ListView;
import android.widget.SimpleAdapter;
import android.widget.TextView;
import android.widget.Toast;
import android.widget.ViewAnimator;

import com.google.android.material.button.MaterialButton;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.android.material.switchmaterial.SwitchMaterial;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import jwtc.android.chess.R;
import jwtc.android.chess.activities.ChessBoardActivity;
import jwtc.android.chess.helpers.ActivityHelper;
import jwtc.android.chess.helpers.ResultDialogListener;
import jwtc.android.chess.lichess.models.Challenge;
import jwtc.android.chess.lichess.models.Game;
import jwtc.android.chess.lichess.models.GameFull;
import jwtc.android.chess.lichess.models.PuzzleAndGame;
import jwtc.android.chess.lichess.models.PuzzleBatchSolveRound;
import jwtc.android.chess.lichess.models.PuzzleGlicko;
import jwtc.android.chess.lichess.models.SwissStanding;
import jwtc.android.chess.lichess.models.SwissTournament;
import jwtc.android.chess.lichess.models.Team;
import jwtc.android.chess.play.SaveGameDialog;
import jwtc.android.chess.services.ClockListener;
import jwtc.android.chess.services.LocalClockApi;
import jwtc.chess.Pos;
import jwtc.chess.board.BoardConstants;


public class LichessActivity extends ChessBoardActivity implements LichessApi.LichessApiListener, ClockListener, ResultDialogListener<Map<String, Object>>, AdapterView.OnItemClickListener {
    private static final String TAG = "LichessActivity";
    private static final int VIEW_ROOT_WAITING = 0, VIEW_ROOT_LOGIN = 1, VIEW_ROOT_SUB = 2;
    private static final int VIEW_SUB_LOBBY = 0, VIEW_SUB_PLAY = 1, VIEW_SUB_SWISS = 2;
    private static final int VIEW_SWISS_TEAMS = 0, VIEW_SWISS_LIST = 1, VIEW_SWISS_DETAIL = 2;
    private static final long LOBBY_REFRESH_INTERVAL_MS = 60_000L;
    public static final int REQUEST_SAVE_GAME_TO_FILE = 1;

    private LichessApi lichessApi;
    private LocalClockApi localClockApi;
    private ViewAnimator viewAnimatorRoot, viewAnimatorSub;
    private LinearLayout layoutConfirm, layoutResignDraw, layoutSave, layoutPuzzleControls;
    private SwitchMaterial switchConfirmMoves;

    private ImageView imageTurnOpp, imageTurnMe;
    private TextView textViewClockOpp, textViewPlayerOpp, textViewRatingOpp;
    private TextView textViewClockMe, textViewPlayerMe, textViewRatingMe;
    private TextView textViewLastMove, textViewStatus, textViewOfferDraw;
    private TextView textViewLobbyStatus;
    private TextView textViewHandle;
    private MaterialButton buttonDraw, buttonResign, buttonSeek, buttonChallenge, buttonConfirmMove, buttonPuzzle, buttonSwiss;
    private MaterialButton buttonPuzzleShow, buttonPuzzleNext, buttonPuzzleRetry;
    private ListView listViewGames;
    private SimpleAdapter adapterGames;

    private ViewAnimator viewAnimatorSwiss;
    private ListView listViewSwissTeams, listViewSwissList, listViewSwissStandings;
    private SimpleAdapter adapterSwissTeams, adapterSwissList, adapterSwissStandings;
    private final ArrayList<HashMap<String, String>> mapSwissTeams = new ArrayList<>();
    private final ArrayList<HashMap<String, String>> mapSwissList = new ArrayList<>();
    private final ArrayList<HashMap<String, String>> mapSwissStandings = new ArrayList<>();
    private List<Team> swissTeams = new ArrayList<>();
    private List<SwissTournament> swissTournaments = new ArrayList<>();
    private final Set<String> myTeamIds = new HashSet<>();
    private MaterialButton buttonSwissMyTeams, buttonSwissAllTeams, buttonSwissPrevPage, buttonSwissNextPage, buttonTeamJoinLeave;
    private LinearLayout layoutSwissPaging;
    private TextView textViewSwissTeamsStatus, textViewSwissTeamName, textViewSwissListStatus, textViewSwissName, textViewSwissInfo, textViewSwissPage;
    private boolean showingAllTeams = false;
    private int allTeamsPage = 1, allTeamsNbPages = 1;
    private Team currentTeam;
    private SwissTournament currentSwiss;

    private String currentPuzzleAngle, currentPuzzleDifficulty;

    private ArrayList<HashMap<String, String>> mapGames = new ArrayList<HashMap<String, String>>();
    private List<Game> nowPlayingGames;
    private Intent pendingData;
    private boolean serviceConnected = false;
    private boolean serviceBound = false;
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
        if (!serviceConnected) {
            return;
        }
        textViewLobbyStatus.setText("");
        lichessApi.event();
        lichessApi.playing();
    };

    private final ServiceConnection mConnection = new ServiceConnection() {
        public void onServiceConnected(ComponentName className, IBinder service) {
            Log.i(TAG, "onServiceConnected");
            serviceConnected = true;
            LichessService lichessService = ((LichessService.LocalBinder) service).getService();
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
        lichessApi = (LichessApi) gameApi;
        localClockApi = new LocalClockApi(gameApi);

        MaterialButton buttonLogin = findViewById(R.id.ButtonLogin);
        buttonLogin.setOnClickListener(v -> lichessApi.login(LichessActivity.this));

        buttonChallenge = findViewById(R.id.ButtonChallenge);
        buttonChallenge.setOnClickListener(v -> openChallengeDialog(ChallengeDialog.REQUEST_CHALLENGE));
        buttonSeek = findViewById(R.id.ButtonSeek);
        buttonSeek.setOnClickListener(v -> openChallengeDialog(ChallengeDialog.REQUEST_SEEK));

        buttonPuzzle = findViewById(R.id.ButtonPuzzle);
        buttonPuzzle.setOnClickListener(v -> openPuzzleDialog());

        buttonSwiss = findViewById(R.id.ButtonSwiss);
        buttonSwiss.setOnClickListener(v -> openSwiss());

        buttonResign = findViewById(R.id.ButtonResign);
        buttonResign.setOnClickListener(v -> {
            openConfirmDialog(getString(R.string.lichess_confirm_resign),
                getString(R.string.lichess_play_button_resign),
                getString(R.string.button_cancel),
                () -> lichessApi.resign(), null);

        });

        MaterialButton buttonLogout = findViewById(R.id.ButtonLogout);
        buttonLogout.setOnClickListener(v -> {
            lichessApi.logout();
            finish();
        });

        MaterialButton buttonCancelMove = findViewById(R.id.ButtonCancelMove);
        buttonCancelMove.setOnClickListener(v -> {
            layoutConfirm.setVisibility(View.GONE);
            layoutResignDraw.setVisibility(View.VISIBLE);
            rebuildBoard();
        });

        buttonDraw = findViewById(R.id.ButtonDraw);
        buttonConfirmMove = findViewById(R.id.ButtonConfirmMove);

        MaterialButton buttonSaveToFile = findViewById(R.id.ButtonSaveToFile);
        buttonSaveToFile.setOnClickListener(v -> {
            startIntentForSaveDocument("application/x-chess-pgn", "game.pgn", REQUEST_SAVE_GAME_TO_FILE);
        });
        MaterialButton buttonSaveToDatabase = findViewById(R.id.ButtonSaveToDatabase);
        buttonSaveToDatabase.setOnClickListener(v -> {
            SaveGameDialog saveDialog = new SaveGameDialog(this, gameApi, 0, this::saveGameFromDialog);
            saveDialog.show();
        });

        localClockApi.addListener(this);

        switchConfirmMoves = findViewById(R.id.SwitchConfirmMoves);
        switchSound = findViewById(R.id.SwitchSound);
        switchMoveToSpeech = findViewById(R.id.SwitchSpeech);
        switchAccessibilityDrag = findViewById(R.id.SwitchAccessibilityDrag);

        layoutResignDraw = findViewById(R.id.LayoutResignDraw);
        layoutConfirm = findViewById(R.id.LayoutConfirm);
        layoutSave = findViewById(R.id.LayoutSave);
        layoutPuzzleControls = findViewById(R.id.LayoutPuzzleControls);

        buttonPuzzleShow = findViewById(R.id.ButtonPuzzleShow);
        buttonPuzzleShow.setOnClickListener(v -> lichessApi.showNextSolutionMove());

        buttonPuzzleNext = findViewById(R.id.ButtonPuzzleNext);
        buttonPuzzleNext.setOnClickListener(v -> lichessApi.nextPuzzle());

        buttonPuzzleRetry = findViewById(R.id.ButtonPuzzleRetry);
        buttonPuzzleRetry.setVisibility(View.GONE);
        buttonPuzzleRetry.setOnClickListener(v -> lichessApi.retryWrongPuzzleMove());

        viewAnimatorRoot = findViewById(R.id.ViewAnimatorRoot);
        viewAnimatorSub = findViewById(R.id.ViewAnimatorSub);

        imageTurnOpp = findViewById(R.id.ImageTopTurn);
        textViewClockOpp = findViewById(R.id.TextViewClockOpp);
        textViewPlayerOpp = findViewById(R.id.TextViewPlayerOpp);
        textViewRatingOpp = findViewById(R.id.TextViewRatingOpp);

        imageTurnMe = findViewById(R.id.ImageBottomTurn);
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

        setupSwissViews();

        View boardAreaLayout = findViewById(R.id.board_area);
        if (boardAreaLayout == null) {
            boardAreaLayout = findViewById(R.id.includeboard);
        }
        initBoardLayoutSizing(
            findViewById(R.id.ViewAnimatorRoot),
            boardAreaLayout,
            findViewById(R.id.play_controls),
            findViewById(R.id.play_board_top),
            findViewById(R.id.play_board_bottom)
        );
        afterCreate();
    }

    @Override
    protected void onResume() {
        super.onResume();
        Log.i(TAG, "onResume");
        SharedPreferences prefs = this.getPrefs();
        lichessApi.setApiListener(LichessActivity.this);

        layoutConfirm.setVisibility(View.GONE);
        layoutSave.setVisibility(View.GONE);
        layoutResignDraw.setVisibility(View.VISIBLE);
        layoutPuzzleControls.setVisibility(View.GONE);
        switchConfirmMoves.setChecked(prefs.getBoolean("lichess_confirm_moves", false));
        startLobbyRefreshLoop();
    }

    @Override
    protected void onPause() {
        super.onPause();
        lichessApi.setApiListener(null);
        stopLobbyRefreshLoop();

        SharedPreferences.Editor editor = this.getPrefs().edit();

        editor.putBoolean("lichess_confirm_moves", switchConfirmMoves.isChecked());

        editor.commit();
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);

        Uri uri = null;
        if (data != null) {
            uri = data.getData();
        }
        Log.d(TAG, "onActivityResult " + requestCode);
        if (requestCode == 1001) {
            if (serviceConnected) {
                handleActivityResult(data);
            } else {
                pendingData = data;
            }
        } else if (requestCode == REQUEST_SAVE_GAME_TO_FILE) {
            if (uri != null) {
                saveToFile(uri, gameApi.exportFullPGN());
            }
        }
    }

    @Override
    protected void onStart() {
        Log.d(TAG, "onStart");
        super.onStart();

        serviceBound = bindService(new Intent(LichessActivity.this, LichessService.class), mConnection, Context.BIND_AUTO_CREATE);
        if (!serviceBound) {
            Log.e(TAG, "Failed to bind LichessService");
            Toast.makeText(this, R.string.lichess_service_unavailable, Toast.LENGTH_LONG).show();
            finish();
            return;
        }
    }

    @Override
    protected void onStop() {
        Log.i(TAG, "onStop");
        super.onStop();
        stopLobbyRefreshLoop();

        if (serviceBound) {
            unbindService(mConnection);
            serviceBound = false;
        }
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
        lichessApi.playing();
    }

    @Override
    public void onGameUpdate(GameFull gameFull) {
        int myTurn = lichessApi.getMyTurn();
        int turn = lichessApi.getTurn();
        boolean playAsWhite = myTurn == BoardConstants.WHITE;
        boolean isStarted = gameFull.state.status.equals("started");
        textViewPlayerOpp.setText(playAsWhite ? gameFull.black.name : gameFull.white.name);
        textViewPlayerMe.setText(playAsWhite ? gameFull.white.name : gameFull.black.name);

        textViewRatingOpp.setText("" + (playAsWhite ? gameFull.black.rating : gameFull.white.rating));
        textViewRatingMe.setText("" + (playAsWhite ? gameFull.white.rating : gameFull.black.rating));

        if (gameFull.clock != null && isStarted) {
            localClockApi.startClock(gameFull.clock.increment, gameFull.state.wtime, gameFull.state.btime, turn, System.currentTimeMillis());
        }
        buttonDraw.setEnabled(isStarted);
        buttonResign.setEnabled(isStarted);

        layoutSave.setVisibility(isStarted ? View.GONE : View.VISIBLE);

        String stateMessage = gameStateToTranslated(gameFull.state.status);
        if (gameFull.state.winner != null) {
            stateMessage += ". " + getString(R.string.lichess_game_winner, gameFull.state.winner);
        }
        updateGameStateMessage(stateMessage);

        boolean isDrawOffer = playAsWhite ? gameFull.state.bdraw : gameFull.state.wdraw;
        if (isDrawOffer) {
            updateTextViewOrSpeech(textViewOfferDraw, getString(R.string.lichess_opponent_offers_draw));
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
        lichessApi.playing();
    }

    @Override
    public void onGameDisconnected() {
        textViewLobbyStatus.setText(R.string.lichess_game_disconnected);
        displayLobby();
    }

    @Override
    public void onInvalidMove(String reason) {
        feedbackIllegalMove();
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
    public void onPuzzle(PuzzleAndGame puzzle) {
        displayPuzzle();
    }

    @Override
    public void onPuzzleSolve(PuzzleAndGame nextPuzzle, PuzzleBatchSolveRound solveRound, PuzzleGlicko glicko) {
        onPuzzle(nextPuzzle);
        if (glicko != null) {
            textViewRatingMe.setText("" + (int) glicko.rating);
            String diffStr = solveRound.ratingDiff >= 0 ? "+" + solveRound.ratingDiff : "" + solveRound.ratingDiff;
            String msg = solveRound.win
                ? getString(R.string.lichess_puzzle_solved_rated, diffStr)
                : getString(R.string.lichess_puzzle_solved_hint_rated, diffStr);
            updateGameStateMessage(msg);
        }
    }

    @Override
    public void onPuzzleMoveCorrect() {
        updateGameStateMessage(getString(R.string.puzzle_correct_move));
    }

    @Override
    public void onPuzzleUnexpectedMove(String sMove, int toPos) {
        wrongPosition = toPos;
        rebuildBoard();
        buttonPuzzleRetry.setVisibility(View.VISIBLE);
        feedbackIllegalMove();
        updateGameStateMessage(sMove + " " + getString(R.string.puzzle_not_correct_move));
    }

    @Override
    public void onPuzzleRetried() {
        wrongPosition = -1;
        buttonPuzzleRetry.setVisibility(View.GONE);
        rebuildBoard();
    }

    @Override
    public void onPuzzleCompleted(int toPos) {
        correctPosition = toPos;
        rebuildBoard();
        buttonPuzzleNext.setEnabled(true);
    }

    @Override
    public void onMyTeams(List<Team> teams) {
        myTeamIds.clear();
        for (Team team : teams) {
            myTeamIds.add(team.id);
        }
        if (showingAllTeams) {
            return; // user switched to All teams before this returned
        }
        populateTeams(teams);
        textViewSwissTeamsStatus.setText(teams.isEmpty()
            ? getString(R.string.lichess_swiss_no_teams)
            : getString(R.string.lichess_swiss_title));
    }

    @Override
    public void onAllTeams(List<Team> teams, int page, int nbPages) {
        if (!showingAllTeams) {
            return;
        }
        allTeamsPage = page > 0 ? page : allTeamsPage;
        allTeamsNbPages = nbPages > 0 ? nbPages : 1;
        populateTeams(teams);
        textViewSwissPage.setText(getString(R.string.lichess_swiss_page, allTeamsPage, allTeamsNbPages));
        buttonSwissPrevPage.setEnabled(allTeamsPage > 1);
        buttonSwissNextPage.setEnabled(allTeamsPage < allTeamsNbPages);
    }

    @Override
    public void onTeamJoined(String teamId) {
        myTeamIds.add(teamId);
        Toast.makeText(this, getString(R.string.lichess_team_joined,
            currentTeam != null && currentTeam.name != null ? currentTeam.name : teamId), Toast.LENGTH_SHORT).show();
        refreshTeamJoinLeaveButton();
        lichessApi.fetchTeamSwiss(teamId);
    }

    @Override
    public void onTeamLeft(String teamId) {
        myTeamIds.remove(teamId);
        if (currentTeam != null) {
            currentTeam.joined = false;
        }
        Toast.makeText(this, getString(R.string.lichess_team_left,
            currentTeam != null && currentTeam.name != null ? currentTeam.name : teamId), Toast.LENGTH_SHORT).show();
        refreshTeamJoinLeaveButton();
    }

    @Override
    public void onSwissList(List<SwissTournament> tournaments) {
        swissTournaments = tournaments;
        mapSwissList.clear();
        for (SwissTournament t : tournaments) {
            HashMap<String, String> row = new HashMap<>();
            row.put("text_swiss_name", t.name != null ? t.name : t.id);
            row.put("text_swiss_info", getString(R.string.lichess_swiss_row_info, swissStatusLabel(t.status), t.nbPlayers));
            mapSwissList.add(row);
        }
        adapterSwissList.notifyDataSetChanged();
        textViewSwissListStatus.setText(tournaments.isEmpty()
            ? getString(R.string.lichess_swiss_no_tournaments) : "");
    }

    @Override
    public void onSwissDetail(SwissTournament tournament, List<SwissStanding> standings) {
        currentSwiss = tournament;
        textViewSwissName.setText(tournament.name != null ? tournament.name : tournament.id);
        textViewSwissInfo.setText(getString(R.string.lichess_swiss_detail_info,
            swissStatusLabel(tournament.status), tournament.round, tournament.nbRounds, tournament.nbPlayers));

        boolean joinable = "created".equals(tournament.status) || "started".equals(tournament.status);
        findViewById(R.id.ButtonSwissJoin).setEnabled(joinable);
        findViewById(R.id.ButtonSwissWithdraw).setEnabled(joinable);

        mapSwissStandings.clear();
        for (SwissStanding s : standings) {
            HashMap<String, String> row = new HashMap<>();
            row.put("text_rank", "" + s.rank);
            String player = s.title != null && !s.title.isEmpty() ? s.title + " " + s.username : s.username;
            row.put("text_player", player);
            row.put("text_points", "" + s.points);
            mapSwissStandings.add(row);
        }
        adapterSwissStandings.notifyDataSetChanged();
        viewAnimatorSwiss.setDisplayedChild(VIEW_SWISS_DETAIL);
    }

    @Override
    public void onSwissJoined(String id) {
        Toast.makeText(this, R.string.lichess_swiss_joined, Toast.LENGTH_SHORT).show();
        lichessApi.fetchSwissDetail(id);
    }

    @Override
    public void onSwissError(String message) {
        Toast.makeText(this, message, Toast.LENGTH_LONG).show();
    }

    @Override
    public boolean requestMove(int from, int to) {
        if (lichessApi.getMyTurn() == lichessApi.getTurn()) {
            if (lichessApi.isPromotionMove(from, to)) {
                final String[] items = getResources().getStringArray(R.array.promotionpieces);

                MaterialAlertDialogBuilder builder = new MaterialAlertDialogBuilder(this);
                builder.setTitle(R.string.title_pick_promo);
                builder.setCancelable(false);
                builder.setSingleChoiceItems(items, 0, (dialog, item) -> {
                    dialog.dismiss();
                    lichessApi.setPromotionPiece(4 - item);
                    lichessApi.move(from, to);
                });
                builder.create().show();

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

        updateLastMoveDescription(getLastMoveAndTurnDescription(false));
    }

    protected void displayLogin() {
        viewAnimatorRoot.setDisplayedChild(VIEW_ROOT_LOGIN);
    }

    protected void displayLobby() {
        lichessApi.playing();
        startLobbyRefreshLoop();
        viewAnimatorRoot.setDisplayedChild(VIEW_ROOT_SUB);
        viewAnimatorSub.setDisplayedChild(VIEW_SUB_LOBBY);
    }

    protected void displayPlay() {
        displayBoard();
        textViewLastMove.setText("");
        textViewStatus.setText("");
        textViewOfferDraw.setText("");
        textViewOfferDraw.setVisibility(View.VISIBLE);
        textViewClockOpp.setVisibility(View.VISIBLE);
        textViewClockMe.setVisibility(View.VISIBLE);
        layoutResignDraw.setVisibility(View.VISIBLE);
        layoutPuzzleControls.setVisibility(View.GONE);
    }

    protected void displayPuzzle() {
        displayBoard();
        textViewLastMove.setText("");
        textViewStatus.setText("");
        textViewOfferDraw.setText("");
        textViewOfferDraw.setVisibility(View.GONE);
        textViewPlayerMe.setText(lichessApi.getUser());
        textViewPlayerOpp.setText("");
        textViewRatingOpp.setText(lichessApi.getPuzzleId());
        textViewClockOpp.setVisibility(View.GONE);
        textViewClockMe.setVisibility(View.GONE);
        layoutResignDraw.setVisibility(View.GONE);
        layoutSave.setVisibility(View.GONE);
        layoutPuzzleControls.setVisibility(View.VISIBLE);
        buttonPuzzleNext.setEnabled(false);
        buttonPuzzleRetry.setVisibility(View.GONE);
        correctPosition = -1;
        wrongPosition = -1;
    }

    protected void displayBoard() {
        stopLobbyRefreshLoop();
        viewAnimatorRoot.setDisplayedChild(VIEW_ROOT_SUB);
        viewAnimatorSub.setDisplayedChild(VIEW_SUB_PLAY);
    }

    private boolean isLobbyVisible() {
        return serviceConnected
            && viewAnimatorRoot != null
            && viewAnimatorSub != null
            && viewAnimatorRoot.getDisplayedChild() == VIEW_ROOT_SUB
            && viewAnimatorSub.getDisplayedChild() == VIEW_SUB_LOBBY;
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

    protected void openGame(String gameId) {
        layoutResignDraw.setVisibility(View.VISIBLE);
        layoutPuzzleControls.setVisibility(View.GONE);
        lichessApi.game(gameId);
        displayPlay();
    }

    // --- Swiss tournaments ---

    private void setupSwissViews() {
        viewAnimatorSwiss = findViewById(R.id.ViewAnimatorSwiss);

        textViewSwissTeamsStatus = findViewById(R.id.TextViewSwissTeamsStatus);
        textViewSwissTeamName = findViewById(R.id.TextViewSwissTeamName);
        textViewSwissListStatus = findViewById(R.id.TextViewSwissListStatus);
        textViewSwissName = findViewById(R.id.TextViewSwissName);
        textViewSwissInfo = findViewById(R.id.TextViewSwissInfo);
        textViewSwissPage = findViewById(R.id.TextViewSwissPage);
        layoutSwissPaging = findViewById(R.id.LayoutSwissPaging);

        buttonSwissMyTeams = findViewById(R.id.ButtonSwissMyTeams);
        buttonSwissMyTeams.setOnClickListener(v -> showMyTeams());
        buttonSwissAllTeams = findViewById(R.id.ButtonSwissAllTeams);
        buttonSwissAllTeams.setOnClickListener(v -> showAllTeams(1));

        buttonSwissPrevPage = findViewById(R.id.ButtonSwissPrevPage);
        buttonSwissPrevPage.setOnClickListener(v -> showAllTeams(allTeamsPage - 1));
        buttonSwissNextPage = findViewById(R.id.ButtonSwissNextPage);
        buttonSwissNextPage.setOnClickListener(v -> showAllTeams(allTeamsPage + 1));

        buttonTeamJoinLeave = findViewById(R.id.ButtonTeamJoinLeave);
        buttonTeamJoinLeave.setOnClickListener(v -> onTeamJoinLeaveClicked());

        findViewById(R.id.ButtonSwissTeamsBack).setOnClickListener(v -> displayLobby());
        findViewById(R.id.ButtonSwissListBack).setOnClickListener(v -> viewAnimatorSwiss.setDisplayedChild(VIEW_SWISS_TEAMS));
        findViewById(R.id.ButtonSwissDetailBack).setOnClickListener(v -> viewAnimatorSwiss.setDisplayedChild(VIEW_SWISS_LIST));

        MaterialButton buttonSwissJoin = findViewById(R.id.ButtonSwissJoin);
        buttonSwissJoin.setOnClickListener(v -> {
            if (currentSwiss != null) {
                lichessApi.joinSwiss(currentSwiss.id, null);
            }
        });
        MaterialButton buttonSwissWithdraw = findViewById(R.id.ButtonSwissWithdraw);
        buttonSwissWithdraw.setOnClickListener(v -> {
            if (currentSwiss != null) {
                lichessApi.withdrawSwiss(currentSwiss.id);
            }
        });

        adapterSwissTeams = new SimpleAdapter(this, mapSwissTeams, R.layout.lichess_swiss_team_row,
            new String[]{"text_team_name", "text_team_members"},
            new int[]{R.id.text_team_name, R.id.text_team_members});
        listViewSwissTeams = findViewById(R.id.ListViewSwissTeams);
        listViewSwissTeams.setAdapter(adapterSwissTeams);
        listViewSwissTeams.setOnItemClickListener(this);

        adapterSwissList = new SimpleAdapter(this, mapSwissList, R.layout.lichess_swiss_row,
            new String[]{"text_swiss_name", "text_swiss_info"},
            new int[]{R.id.text_swiss_name, R.id.text_swiss_info});
        listViewSwissList = findViewById(R.id.ListViewSwissList);
        listViewSwissList.setAdapter(adapterSwissList);
        listViewSwissList.setOnItemClickListener(this);

        adapterSwissStandings = new SimpleAdapter(this, mapSwissStandings, R.layout.lichess_swiss_standing_row,
            new String[]{"text_rank", "text_player", "text_points"},
            new int[]{R.id.text_rank, R.id.text_player, R.id.text_points});
        listViewSwissStandings = findViewById(R.id.ListViewSwissStandings);
        listViewSwissStandings.setAdapter(adapterSwissStandings);
    }

    protected void openSwiss() {
        displaySwiss();
        showMyTeams();
    }

    protected void displaySwiss() {
        stopLobbyRefreshLoop();
        viewAnimatorRoot.setDisplayedChild(VIEW_ROOT_SUB);
        viewAnimatorSub.setDisplayedChild(VIEW_SUB_SWISS);
        viewAnimatorSwiss.setDisplayedChild(VIEW_SWISS_TEAMS);
    }

    private void showMyTeams() {
        showingAllTeams = false;
        layoutSwissPaging.setVisibility(View.GONE);
        textViewSwissTeamsStatus.setText(R.string.lichess_swiss_title);
        lichessApi.fetchMyTeams();
    }

    private void showAllTeams(int page) {
        if (page < 1) {
            page = 1;
        }
        showingAllTeams = true;
        allTeamsPage = page;
        layoutSwissPaging.setVisibility(View.VISIBLE);
        textViewSwissTeamsStatus.setText(R.string.lichess_swiss_all_teams);
        lichessApi.fetchAllTeams(page);
    }

    private void populateTeams(List<Team> teams) {
        swissTeams = teams;
        mapSwissTeams.clear();
        for (Team team : teams) {
            HashMap<String, String> row = new HashMap<>();
            row.put("text_team_name", team.name != null ? team.name : team.id);
            row.put("text_team_members", getString(R.string.lichess_team_members, team.nbMembers));
            mapSwissTeams.add(row);
        }
        adapterSwissTeams.notifyDataSetChanged();
    }

    private void openTeamDetail(Team team) {
        currentTeam = team;
        textViewSwissTeamName.setText(team.name != null ? team.name : team.id);
        textViewSwissListStatus.setText("");
        mapSwissList.clear();
        adapterSwissList.notifyDataSetChanged();
        refreshTeamJoinLeaveButton();
        viewAnimatorSwiss.setDisplayedChild(VIEW_SWISS_LIST);
        lichessApi.fetchTeamSwiss(team.id);
    }

    private boolean isMemberOfCurrentTeam() {
        return currentTeam != null &&
            (myTeamIds.contains(currentTeam.id) || Boolean.TRUE.equals(currentTeam.joined));
    }

    private void refreshTeamJoinLeaveButton() {
        buttonTeamJoinLeave.setText(isMemberOfCurrentTeam() ? R.string.lichess_team_leave : R.string.lichess_team_join);
    }

    private void onTeamJoinLeaveClicked() {
        if (currentTeam == null) {
            return;
        }
        if (isMemberOfCurrentTeam()) {
            lichessApi.quitTeam(currentTeam.id);
        } else {
            openJoinTeamDialog(currentTeam);
        }
    }

    private void openJoinTeamDialog(Team team) {
        LinearLayout container = new LinearLayout(this);
        container.setOrientation(LinearLayout.VERTICAL);
        int pad = (int) (16 * getResources().getDisplayMetrics().density);
        container.setPadding(pad, pad, pad, 0);

        EditText editMessage = new EditText(this);
        editMessage.setHint(R.string.lichess_team_join_message_hint);
        editMessage.setSingleLine(false);
        container.addView(editMessage);

        EditText editPassword = new EditText(this);
        editPassword.setHint(R.string.lichess_team_join_password_hint);
        editPassword.setSingleLine(true);
        container.addView(editPassword);

        new MaterialAlertDialogBuilder(this)
            .setTitle(R.string.lichess_team_join)
            .setView(container)
            .setPositiveButton(R.string.lichess_team_join, (dialog, which) ->
                lichessApi.joinTeam(team.id, editMessage.getText().toString(), editPassword.getText().toString()))
            .setNegativeButton(R.string.button_cancel, null)
            .show();
    }

    private String swissStatusLabel(String status) {
        if ("created".equals(status)) {
            return getString(R.string.lichess_swiss_status_created);
        } else if ("started".equals(status)) {
            return getString(R.string.lichess_swiss_status_started);
        } else if ("finished".equals(status)) {
            return getString(R.string.lichess_swiss_status_finished);
        }
        return status != null ? status : "";
    }

    private boolean isSwissVisible() {
        return viewAnimatorRoot != null && viewAnimatorSub != null
            && viewAnimatorRoot.getDisplayedChild() == VIEW_ROOT_SUB
            && viewAnimatorSub.getDisplayedChild() == VIEW_SUB_SWISS;
    }

    private boolean swissBack() {
        if (!isSwissVisible()) {
            return false;
        }
        int child = viewAnimatorSwiss.getDisplayedChild();
        if (child == VIEW_SWISS_DETAIL) {
            viewAnimatorSwiss.setDisplayedChild(VIEW_SWISS_LIST);
        } else if (child == VIEW_SWISS_LIST) {
            viewAnimatorSwiss.setDisplayedChild(VIEW_SWISS_TEAMS);
        } else {
            displayLobby();
        }
        return true;
    }

    protected String gameStateToTranslated(String state) {
        if (state.equals("created")) {
            return getString(R.string.lichess_game_state_created);
        } else if (state.equals("started")) {
            return "";
        } else if (state.equals("aborted")) {
            return getString(R.string.lichess_game_state_aborted);
        } else if (state.equals("mate")) {
            return getString(R.string.lichess_game_state_mate);
        } else if (state.equals("resign")) {
            return getString(R.string.lichess_game_state_resigned);
        }

        return "";
    }

    protected void updateGameStateMessage(String message) {
        updateTextViewOrSpeech(textViewStatus, message);
    }

    protected void updateLastMoveDescription(String sMove) {
        updateTextViewOrSpeech(textViewLastMove, sMove);
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
        } else if (swissBack()) {
            // handled: stepped back within the Swiss section
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
    public void OnTimeWarning(int turn, long remainingMillies) {
        if (turn == lichessApi.getMyTurn()) {
            feedBackDescribeTimeWarning(remainingMillies);
        }
    }

    @Override
    public void OnDialogResult(int requestCode, Map<String, Object> data) {
        if (requestCode == PuzzleDialog.REQUEST_PUZZLE) {
            if (data != null) {
                currentPuzzleAngle = (String) data.get("angle");
                currentPuzzleDifficulty = (String) data.get("difficulty");
                boolean rated = data.get("rated") != null && (boolean) data.get("rated");
                lichessApi.fetchPuzzle(currentPuzzleAngle, currentPuzzleDifficulty, null, rated);
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
        if (parent == listViewGames && nowPlayingGames.size() > position) {
            Game game = nowPlayingGames.get(position);
            lichessApi.game(game.gameId);
            displayPlay();
        } else if (parent == listViewSwissTeams && swissTeams.size() > position) {
            openTeamDetail(swissTeams.get(position));
        } else if (parent == listViewSwissList && swissTournaments.size() > position) {
            lichessApi.fetchSwissDetail(swissTournaments.get(position).id);
        }
    }
}
