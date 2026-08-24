package jwtc.android.chess.lichess;

import android.app.Activity;
import android.content.Intent;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;

import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import jwtc.android.chess.constants.Piece;
import jwtc.android.chess.lichess.models.Challenge;
import jwtc.android.chess.lichess.models.Game;
import jwtc.android.chess.lichess.models.GameFull;
import jwtc.android.chess.lichess.models.GameState;
import jwtc.android.chess.lichess.models.PuzzleAndGame;
import jwtc.android.chess.lichess.models.PuzzleBatchSelectResponse;
import jwtc.android.chess.lichess.models.PuzzleBatchSolveRequest;
import jwtc.android.chess.lichess.models.PuzzleBatchSolveResponse;
import jwtc.android.chess.lichess.models.PuzzleBatchSolveRound;
import jwtc.android.chess.lichess.models.PuzzleGlicko;
import jwtc.android.chess.lichess.models.SwissStanding;
import jwtc.android.chess.lichess.models.SwissTournament;
import jwtc.android.chess.lichess.models.Team;
import jwtc.android.chess.lichess.models.TeamPaginator;
import jwtc.android.chess.services.GameApi;
import jwtc.chess.Move;
import jwtc.chess.Pos;
import jwtc.chess.board.BoardConstants;

public class LichessApi extends GameApi {
    private static final String TAG = "LichessApi";
    private static final String PUZZLE_ANGLE_DEFAULT = "mix";
    public static final int VIEW_NONE = 0, VIEW_PLAY = 1, VIEW_PUZZLE = 2;

    // Default no-op methods so each screen (lobby / game / swiss) overrides only the callbacks it
    // owns. The single registered listener is always the foreground activity.
    public interface LichessApiListener {
        default void onAuthenticate(String user) {}

        default void onGameInit(String gameId, boolean boardCompatible) {}

        default void onGameUpdate(GameFull gameFull) {}

        // void onDrawAccepted(boolean accepted);
        default void onGameFinish() {}

        default void onGameDisconnected() {}

        default void onInvalidMove(String reason) {}

        default void onNowPlaying(List<Game> games, String me) {}

        default void onConnectionError() {}

        default void onChallenge(Challenge challenge) {}

        default void onChallengeCancelled(Challenge challenge) {}

        default void onChallengeDeclined(Challenge challenge) {}

        default void onMyChallengeCancelled() {}

        default void onMySeekCancelled() {}

        default void onPuzzle(PuzzleAndGame puzzle) {}
        default void onPuzzleSolve(PuzzleAndGame nextPuzzle, PuzzleBatchSolveRound solveRound, PuzzleGlicko glicko) {}
        default void onPuzzleMoveCorrect() {}
        default void onPuzzleUnexpectedMove(String sMove, int toPos) {}
        default void onPuzzleRetried() {}
        default void onPuzzleCompleted(int toPos) {}

        default void onMyTeams(List<Team> teams) {}
        default void onAllTeams(List<Team> teams, int page, int nbPages) {}
        default void onTeamJoined(String teamId) {}
        default void onTeamLeft(String teamId) {}
        default void onSwissList(List<SwissTournament> tournaments) {}
        default void onSwissDetail(SwissTournament tournament, List<SwissStanding> standings) {}
        default void onSwissJoined(String id) {}
        default void onSwissError(String message) {}
    }

    protected int turn = 0;
    private int promotionPiece = BoardConstants.QUEEN;
    private Auth auth;
    private LichessApiListener apiListener;
    private boolean eventStreamOpen = false;

    private GameFull ongoingGameFull;
    private LichessGameStateSnapshot lastGameStateSnapshot;
    private PuzzleAndGame ongoingPuzzle;
    private int puzzleMoveIndex = 0;
    private String currentPuzzleAngle = PUZZLE_ANGLE_DEFAULT;
    private boolean currentPuzzleRated = true;
    private boolean puzzleSolvedCleanly = true;
    private boolean hasPendingWrongMove = false;
    private boolean puzzleComputerMovePending = false;
    private final Handler puzzleHandler = new Handler(Looper.getMainLooper());
    private String user;

    public LichessApi() {
        super();

    }

    public void setAuth(Auth auth) {
        this.auth = auth;
    }

    public void setApiListener(LichessApiListener apiListener) {
        this.apiListener = apiListener;
    }

    public void resume() {
        Log.d(TAG, "resume");

        auth.restoreTokens();

        if (auth.hasAccessToken()) {
            Log.d(TAG, "hasAccessToken");

            auth.authenticateWithToken(new OAuth2AuthCodePKCE.Callback<String, Exception>() {
                @Override
                public void onSuccess(String result) {
                    Log.d(TAG, "Logged in with token");
                    onAuthenticate(result);
                }

                @Override
                public void onError(Exception e) {
                    Log.d(TAG, "Auth failed: " + e.getMessage());
                    onAuthenticate(null);
                }
            });
        } else {
            onAuthenticate(null);
        }
    }

    public void login(Activity activity) {
        this.auth.login(activity);
    }

    public void logout() {
        this.auth.logout();
    }

    public void handleLoginData(Intent data) {
        auth.handleLoginResponse(data, new OAuth2AuthCodePKCE.Callback<String, Exception>() {
            @Override
            public void onSuccess(String result) {
                Log.d(TAG, "Logged in!" + result);
                onAuthenticate(result);
            }

            @Override
            public void onError(Exception e) {
                Log.d(TAG, "Auth failed: " + e.getMessage());
                onAuthenticate(null);
            }
        });
    }

    /** Ensure the account event stream is running. Safe to call after every authentication. */
    public void event() {
        if (eventStreamOpen || user == null) {
            return;
        }
        eventStreamOpen = true;
        this.auth.event(new Auth.AuthResponseHandler() {
            @Override
            public void onResponse(JsonObject jsonObject) {
                String type = jsonObject.get("type").getAsString();
                Log.d(TAG, "event " + jsonObject.get("type").getAsString());
                if (type.equals("gameStart")) {
                    Game ongoingGame = (new Gson()).fromJson(jsonObject.get("game").getAsJsonObject(), Game.class);

                    // The Board API only streams/plays rapid, classical and correspondence games;
                    // blitz/bullet (typical for swiss) come through with compat.board == false and would
                    // 4xx on the game stream. Treat a missing compat as compatible so nothing else breaks.
                    boolean boardCompatible = ongoingGame.compat == null || ongoingGame.compat.board;
                    if (queuePendingGameStart(ongoingGame.gameId, boardCompatible) && apiListener != null) {
                        apiListener.onGameInit(ongoingGame.gameId, boardCompatible);
                    }
                } else if (type.equals("gameFinish")) {
                    onGameFinish();
                } else if (type.equals("challenge")) {
                    Challenge challenge = (new Gson()).fromJson(jsonObject.get("challenge").getAsJsonObject(), Challenge.class);
                    if (apiListener != null && !user.equals(challenge.challenger.id) && (challenge.variant.key.equals("standard") || challenge.variant.key.equals("chess960"))) {
                        // ignore own challenge and variants we do not support
                        apiListener.onChallenge(challenge);
                    }
                } else if (type.equals("challengeCanceled")) {
                    Challenge challenge = (new Gson()).fromJson(jsonObject.get("challenge").getAsJsonObject(), Challenge.class);
                    if (apiListener != null) {
                        apiListener.onChallengeCancelled(challenge);
                    }
                } else if (type.equals("challengeDeclined")) {
                    Challenge challenge = (new Gson()).fromJson(jsonObject.get("challenge").getAsJsonObject(), Challenge.class);
                    if (apiListener != null) {
                        apiListener.onChallengeDeclined(challenge);
                    }
                }
            }

            @Override
            public void onClose(boolean success) {
                eventStreamOpen = false;
                Log.d(TAG, "event closed " + success);
                if (apiListener != null && !success) {
                    apiListener.onConnectionError();
                }
            }
        });
    }

    public void playing() {
        this.auth.playing(new OAuth2AuthCodePKCE.Callback<JsonObject, JsonObject>() {
            @Override
            public void onSuccess(JsonObject result) {
                Log.d(TAG, "playing");
                List<Game> gameList = new ArrayList<Game>();

                JsonArray jsonArray = result.getAsJsonArray("nowPlaying");
                for (JsonElement jsonElement : jsonArray) {
                    JsonObject jsonObject = jsonElement.getAsJsonObject();
                    gameList.add((new Gson()).fromJson(jsonObject, Game.class));
                }
                if (apiListener != null) {
                    apiListener.onNowPlaying(gameList, user);
                }
            }

            @Override
            public void onError(JsonObject e) {
                Log.d(TAG, "playing " + e);
                if (apiListener != null) {
                    apiListener.onConnectionError();
                }
            }
        });
    }

    public void challenge(Map<String, Object> payload) {
        this.auth.challenge(payload, new Auth.AuthResponseHandler() {
            @Override
            public void onResponse(JsonObject result) {
                Log.d(TAG, "challenge response");
            }

            @Override
            public void onClose(boolean success) {
                Log.d(TAG, "challenge closed " + success);
                if (apiListener != null) {
                    apiListener.onMyChallengeCancelled();
                }
            }
        });
    }

    public void seek(Map<String, Object> payload) {
        this.auth.seek(payload, new Auth.AuthResponseHandler() {
            @Override
            public void onResponse(JsonObject result) {
                Log.d(TAG, "seek response");
            }

            @Override
            public void onClose(boolean success) {
                Log.d(TAG, "seek closed " + success);
                if (apiListener != null) {
                    apiListener.onMySeekCancelled();
                }
            }
        });
    }

    public void fetchPuzzle(String angle, String difficulty, String color, boolean rated) {
        String puzzleAngle = angle == null || angle.isEmpty() ? PUZZLE_ANGLE_DEFAULT : angle;
        currentPuzzleAngle = puzzleAngle;
        currentPuzzleRated = rated;
        int puzzleCount = 1;

        this.auth.puzzleBatchSelect(puzzleAngle, puzzleCount, difficulty, color, rated, new OAuth2AuthCodePKCE.Callback<JsonObject, JsonObject>() {
            @Override
            public void onSuccess(JsonObject result) {
                try {
                    PuzzleBatchSelectResponse response = (new Gson()).fromJson(result, PuzzleBatchSelectResponse.class);
                    if (!response.puzzles.isEmpty() && apiListener != null) {
                        ongoingPuzzle = response.puzzles.get(0);
                        apiListener.onPuzzle(ongoingPuzzle);
                        ongoingGameFull = null;
                        processPuzzle();
                    }
                } catch (Exception ex) {
                    Log.d(TAG, "fetchPuzzleBatch parse error " + ex);
                    JsonObject error = new JsonObject();
                    error.addProperty("error", "Could not parse puzzle batch response");
                    // @TODO
                }
            }

            @Override
            public void onError(JsonObject e) {
                Log.d(TAG, "fetchPuzzleBatch onError " + e);
                handlePuzzleError(e);
            }
        });
    }

    public void nextPuzzle() {
        if (currentPuzzleRated && ongoingPuzzle != null) {
            solvePuzzle(currentPuzzleAngle, ongoingPuzzle.puzzle.id, puzzleSolvedCleanly, true);
        } else {
            fetchPuzzle(currentPuzzleAngle, null, null, currentPuzzleRated);
        }
    }

    public void solvePuzzle(String angle, String puzzleId, boolean win, boolean rated) {
        String puzzleAngle = angle == null || angle.isEmpty() ? PUZZLE_ANGLE_DEFAULT : angle;
        int puzzleCount = 1;

        PuzzleBatchSolveRequest.Solution solution = new PuzzleBatchSolveRequest.Solution();
        solution.id = puzzleId;
        solution.win = win;
        solution.rated = rated;

        List<PuzzleBatchSolveRequest.Solution> solutions = new ArrayList<>();
        solutions.add(solution);

        Map<String, Object> payload = new HashMap<>();
        payload.put("solutions", solutions);

        this.auth.puzzleBatchSolve(puzzleAngle, puzzleCount, payload, new OAuth2AuthCodePKCE.Callback<JsonObject, JsonObject>() {
            @Override
            public void onSuccess(JsonObject result) {
                try {
                    PuzzleBatchSolveResponse response = (new Gson()).fromJson(result, PuzzleBatchSolveResponse.class);
                    if (!response.puzzles.isEmpty() && !response.rounds.isEmpty()) {
                        ongoingPuzzle = response.puzzles.get(0);
                        ongoingGameFull = null;
                        processPuzzle();
                        if (apiListener != null) {
                            apiListener.onPuzzleSolve(ongoingPuzzle, response.rounds.get(0), response.glicko);
                        }
                    }
                } catch (Exception ex) {
                    Log.d(TAG, "solvePuzzleBatch parse error " + ex);
                    JsonObject error = new JsonObject();
                    error.addProperty("error", "Could not parse puzzle solve response");
                    // @TODO
                }
            }

            @Override
            public void onError(JsonObject e) {
                Log.d(TAG, "solvePuzzleBatch onError " + e);
                handlePuzzleError(e);
            }
        });
    }

    private boolean applyUciMoveToBoard(String uciMove) {
        try {
            int from = Pos.fromString(uciMove.substring(0, 2));
            int to = Pos.fromString(uciMove.substring(2, 4));
            if (uciMove.length() == 5) {
                jni.setPromo(Piece.fromUCIPromo(uciMove.substring(4, 5).toLowerCase()));
            }
            if (jni.requestMove(from, to) != 0) {
                addPGNEntry(jni.getNumBoard(), jni.getMyMoveToString(), "", jni.getMyMove(), -1);
                return true;
            } else {
                Log.d(TAG, "applyUciMoveToBoard failed: " + uciMove);
                return false;
            }
        } catch (Exception e) {
            Log.d(TAG, "applyUciMoveToBoard exception: " + uciMove + " " + e);
            return false;
        }
    }

    public void showNextSolutionMove() {
        if (ongoingPuzzle == null || puzzleComputerMovePending) {
            return;
        }
        List<String> solution = ongoingPuzzle.puzzle.solution;
        if (puzzleMoveIndex >= solution.size()) {
            return;
        }
        if (hasPendingWrongMove) {
            hasPendingWrongMove = false;
            jni.undo();
            if (apiListener != null) {
                apiListener.onPuzzleRetried();
            }
        }
        puzzleSolvedCleanly = false;
        applyPuzzleMoveAndResponse(solution.get(puzzleMoveIndex));
    }

    private void applyPuzzleMoveAndResponse(String playerMove) {
        List<String> solution = ongoingPuzzle.puzzle.solution;

        if (!applyUciMoveToBoard(playerMove)) {
            Log.e(TAG, "Player puzzle move failed to apply: " + playerMove);
            dispatchState();
            return;
        }
        int move = jni.getMyMove();
        dispatchMove(move);
        puzzleMoveIndex++;

        if (apiListener != null) {
            apiListener.onPuzzleMoveCorrect();
        }

        if (puzzleMoveIndex >= solution.size()) {
            dispatchState();
            if (apiListener != null) {
                apiListener.onPuzzleCompleted(Move.getTo(move));
            }
            return;
        }

        // Delay the computer's response for smoother UX
        final String computerMove = solution.get(puzzleMoveIndex);
        puzzleComputerMovePending = true;
        puzzleHandler.postDelayed(() -> {
            puzzleComputerMovePending = false;
            if (!applyUciMoveToBoard(computerMove)) {
                Log.e(TAG, "Computer puzzle move failed: " + computerMove);
                dispatchState();
                return;
            }
            int cpuMove = jni.getMyMove();
            dispatchMove(cpuMove);
            puzzleMoveIndex++;
            dispatchState();
            if (puzzleMoveIndex >= solution.size()) {
                if (apiListener != null) apiListener.onPuzzleCompleted(Move.getTo(cpuMove));
            }
        }, 1000);
    }

    private void handlePuzzleError(JsonObject e) {
        handleScopeError(e);
    }

    /**
     * Returns true when the error was a missing-scope error and a re-login was triggered.
     */
    private boolean handleScopeError(JsonObject e) {
        String error = LichessJsonError.message(e);
        if (error.startsWith("Missing scope")) {
            Log.d(TAG, "Scope missing — clearing token and forcing re-login");
            auth.clearTokens();
            onAuthenticate(null);
            return true;
        }
        return false;
    }

    private String errorMessage(JsonObject e) {
        return LichessJsonError.message(e);
    }

    // --- Teams & Swiss tournaments ---

    public void fetchMyTeams() {
        this.auth.teamsOfUser(user, new OAuth2AuthCodePKCE.Callback<JsonArray, JsonObject>() {
            @Override
            public void onSuccess(JsonArray result) {
                List<Team> teams = new ArrayList<>();
                for (JsonElement element : result) {
                    teams.add((new Gson()).fromJson(element.getAsJsonObject(), Team.class));
                }
                if (apiListener != null) {
                    apiListener.onMyTeams(teams);
                }
            }

            @Override
            public void onError(JsonObject e) {
                Log.d(TAG, "fetchMyTeams onError " + e);
                if (apiListener != null) {
                    apiListener.onSwissError(errorMessage(e));
                }
            }
        });
    }

    public void fetchAllTeams(int page, String search) {
        this.auth.allTeams(page, search, new OAuth2AuthCodePKCE.Callback<JsonObject, JsonObject>() {
            @Override
            public void onSuccess(JsonObject result) {
                try {
                    TeamPaginator paginator = (new Gson()).fromJson(result, TeamPaginator.class);
                    List<Team> teams = paginator.currentPageResults != null ? paginator.currentPageResults : new ArrayList<>();
                    if (apiListener != null) {
                        apiListener.onAllTeams(teams, paginator.currentPage, paginator.nbPages);
                    }
                } catch (Exception ex) {
                    Log.d(TAG, "fetchAllTeams parse error " + ex);
                    if (apiListener != null) {
                        apiListener.onSwissError("Could not parse teams");
                    }
                }
            }

            @Override
            public void onError(JsonObject e) {
                Log.d(TAG, "fetchAllTeams onError " + e);
                if (apiListener != null) {
                    apiListener.onSwissError(errorMessage(e));
                }
            }
        });
    }

    public void joinTeam(String teamId, String message, String password) {
        Map<String, Object> body = new HashMap<>();
        if (message != null && !message.isEmpty()) {
            body.put("message", message);
        }
        if (password != null && !password.isEmpty()) {
            body.put("password", password);
        }
        this.auth.joinTeam(teamId, body.isEmpty() ? null : body, new OAuth2AuthCodePKCE.Callback<JsonObject, JsonObject>() {
            @Override
            public void onSuccess(JsonObject result) {
                Log.d(TAG, "joinTeam success " + teamId);
                if (apiListener != null) {
                    apiListener.onTeamJoined(teamId);
                }
            }

            @Override
            public void onError(JsonObject e) {
                Log.d(TAG, "joinTeam onError " + e);
                if (!handleScopeError(e) && apiListener != null) {
                    apiListener.onSwissError(errorMessage(e));
                }
            }
        });
    }

    public void quitTeam(String teamId) {
        this.auth.quitTeam(teamId, new OAuth2AuthCodePKCE.Callback<JsonObject, JsonObject>() {
            @Override
            public void onSuccess(JsonObject result) {
                Log.d(TAG, "quitTeam success " + teamId);
                if (apiListener != null) {
                    apiListener.onTeamLeft(teamId);
                }
            }

            @Override
            public void onError(JsonObject e) {
                Log.d(TAG, "quitTeam onError " + e);
                if (!handleScopeError(e) && apiListener != null) {
                    apiListener.onSwissError(errorMessage(e));
                }
            }
        });
    }

    public void fetchTeamSwiss(String teamId, String status) {
        // Single-status fetch. Auth.teamSwiss cancels any in-flight stream, so switching
        // the status filter safely aborts a pending request.
        final List<SwissTournament> tournaments = new ArrayList<>();
        this.auth.teamSwiss(teamId, status, new Auth.AuthResponseHandler() {
            @Override
            public void onResponse(JsonObject jsonObject) {
                tournaments.add((new Gson()).fromJson(jsonObject, SwissTournament.class));
            }

            @Override
            public void onClose(boolean success) {
                Log.d(TAG, "fetchTeamSwiss " + status + " closed " + success + " count " + tournaments.size());
                if (apiListener != null) {
                    apiListener.onSwissList(tournaments);
                }
            }
        });
    }

    public void fetchSwissDetail(String id) {
        this.auth.swissInfo(id, new OAuth2AuthCodePKCE.Callback<JsonObject, JsonObject>() {
            @Override
            public void onSuccess(JsonObject result) {
                final SwissTournament tournament = (new Gson()).fromJson(result, SwissTournament.class);
                final List<SwissStanding> standings = new ArrayList<>();
                auth.swissResults(id, new Auth.AuthResponseHandler() {
                    @Override
                    public void onResponse(JsonObject jsonObject) {
                        standings.add((new Gson()).fromJson(jsonObject, SwissStanding.class));
                    }

                    @Override
                    public void onClose(boolean success) {
                        if (apiListener != null) {
                            apiListener.onSwissDetail(tournament, standings);
                        }
                    }
                });
            }

            @Override
            public void onError(JsonObject e) {
                Log.d(TAG, "fetchSwissDetail onError " + e);
                if (apiListener != null) {
                    apiListener.onSwissError(errorMessage(e));
                }
            }
        });
    }

    public void joinSwiss(String id, String password) {
        Map<String, Object> body = new HashMap<>();
        if (password != null && !password.isEmpty()) {
            body.put("password", password);
        }
        this.auth.joinSwiss(id, body.isEmpty() ? null : body, new OAuth2AuthCodePKCE.Callback<JsonObject, JsonObject>() {
            @Override
            public void onSuccess(JsonObject result) {
                Log.d(TAG, "joinSwiss success " + id);
                if (apiListener != null) {
                    apiListener.onSwissJoined(id);
                }
            }

            @Override
            public void onError(JsonObject e) {
                Log.d(TAG, "joinSwiss onError " + e);
                if (!handleScopeError(e) && apiListener != null) {
                    apiListener.onSwissError(errorMessage(e));
                }
            }
        });
    }

    public void withdrawSwiss(String id) {
        this.auth.withdrawSwiss(id, new OAuth2AuthCodePKCE.Callback<JsonObject, JsonObject>() {
            @Override
            public void onSuccess(JsonObject result) {
                Log.d(TAG, "withdrawSwiss success " + id);
            }

            @Override
            public void onError(JsonObject e) {
                Log.d(TAG, "withdrawSwiss onError " + e);
                if (!handleScopeError(e) && apiListener != null) {
                    apiListener.onSwissError(errorMessage(e));
                }
            }
        });
    }

    public void cancelChallenge() {
        this.auth.cancelChallenge();
    }

    public void cancelSeek() {
        this.auth.cancelSeek();
    }

    public void acceptChallenge(Challenge challenge) {
        this.auth.acceptChallenge(challenge.id, new OAuth2AuthCodePKCE.Callback<JsonObject, JsonObject>() {
            @Override
            public void onSuccess(JsonObject result) {
                Log.d(TAG, "challenge accepted");
            }

            @Override
            public void onError(JsonObject e) {
                Log.d(TAG, "challenge " + e);
            }
        });
    }

    public void declineChallenge(Challenge challenge) {
        this.auth.declineChallenge(challenge.id, new OAuth2AuthCodePKCE.Callback<JsonObject, JsonObject>() {
            @Override
            public void onSuccess(JsonObject result) {
                Log.d(TAG, "challenge accepted");
            }

            @Override
            public void onError(JsonObject e) {
                Log.d(TAG, "challenge " + e);
            }
        });
    }

    public void move(int from, int to) {
        String uciMove = Pos.toString(from) + Pos.toString(to);
        if (isPromotionMove(from, to)) {
            uciMove += Piece.toPromoUCI(promotionPiece);
        }
        if (ongoingGameFull != null) {
            this.auth.move(ongoingGameFull.id, uciMove, new OAuth2AuthCodePKCE.Callback<JsonObject, JsonObject>() {
                @Override
                public void onSuccess(JsonObject result) {
                    Log.d(TAG, "moved");
                }

                @Override
                public void onError(JsonObject result) {
                    Log.d(TAG, "moved " + result);
                    dispatchIllegalMove();
                    if (apiListener != null) {
                        apiListener.onInvalidMove(result.get("error").getAsString());
                    }
                }
            });
        } else if (ongoingPuzzle != null) {
            if (puzzleComputerMovePending) {
                return;
            }
            List<String> solution = ongoingPuzzle.puzzle.solution;
            if (puzzleMoveIndex >= solution.size()) {
                Log.d(TAG, "Solution index");
                return;
            }
            if (!uciMove.equals(solution.get(puzzleMoveIndex))) {
                Log.d(TAG, "Not equal " + uciMove + " = " + solution.get(puzzleMoveIndex));
                puzzleSolvedCleanly = false;
                if (applyUciMoveToBoard(uciMove)) {
                    hasPendingWrongMove = true;
                    dispatchMove(jni.getMyMove());
                    dispatchState();
                    if (apiListener != null) {
                        String displayMove = uciMove.substring(0, 2) + "-" + uciMove.substring(2, 4);
                        try {
                            int toPos = Pos.fromString(uciMove.substring(2, 4));
                            apiListener.onPuzzleUnexpectedMove(displayMove, toPos);
                        } catch (Exception ignore) {
                            Log.d(TAG, "Unexpected uci move format " + uciMove);
                        }
                    }
                } else {
                    // Truly illegal chess move — board unchanged, no retry needed
                    dispatchIllegalMove();
                }
                return;
            }

            // correct player move — apply it
            applyPuzzleMoveAndResponse(uciMove);
        } else {
            Log.d(TAG, "Unexpected state; move without ongoing game");
        }
    }

    public void setPromotionPiece(int piece) {
        promotionPiece = piece;
    }

    public void resign() {
        if (ongoingGameFull != null) {
            this.auth.resign(ongoingGameFull.id, new OAuth2AuthCodePKCE.Callback<JsonObject, JsonObject>() {
                @Override
                public void onSuccess(JsonObject result) {

                }

                @Override
                public void onError(JsonObject e) {

                }
            });
        }
    }

    public void draw(boolean accept) {
        if (ongoingGameFull != null) {
            this.auth.draw(ongoingGameFull.id, accept ? "yes" : "no", new OAuth2AuthCodePKCE.Callback<JsonObject, JsonObject>() {
                @Override
                public void onSuccess(JsonObject result) {
                    Log.d(TAG, "Draw success " + result);
                }

                @Override
                public void onError(JsonObject e) {
                    Log.d(TAG, "Draw error " + e);
                }
            });
        }
    }

    public void game(String gameId) {
        jni.reset();
        dispatchState();
        this.auth.game(gameId, new Auth.AuthResponseHandler() {
            @Override
            public void onResponse(JsonObject jsonObject) {
                String type = jsonObject.get("type").getAsString();
                Log.d(TAG, "game " + jsonObject.get("type").getAsString());
                if (type.equals("gameState") && ongoingGameFull != null) {
                    ongoingGameFull.state = (new Gson()).fromJson(jsonObject, GameState.class);
                } else if (type.equals("gameFull")) {
                    ongoingGameFull = (new Gson()).fromJson(jsonObject, GameFull.class);
                    ongoingPuzzle = null;
                }
                processGameState();
            }

            @Override
            public void onClose(boolean success) {
                Log.d(TAG, "game closed " + success);
                if (apiListener != null && !success) {
                    apiListener.onGameDisconnected();
                }
            }
        });
    }

    public String getPuzzleId() {
        return ongoingPuzzle != null ? ongoingPuzzle.puzzle.id : "";
    }

    public int getViewMode() {
        if (ongoingPuzzle != null) {
            return VIEW_PUZZLE;
        }
        if (ongoingGameFull != null) {
            return VIEW_PLAY;
        }
        return VIEW_NONE;
    }

    public String getOngoingGameId() {
        return ongoingGameFull != null ? ongoingGameFull.id : null;
    }

    public boolean isOngoingGameInProgress() {
        return ongoingGameFull != null && ongoingGameFull.state != null
            && "started".equals(ongoingGameFull.state.status);
    }

    /** A gameStart retained until a foreground Lichess screen can act on it. */
    public static final class PendingGameStart {
        public final String gameId;
        public final boolean boardCompatible;

        private PendingGameStart(String gameId, boolean boardCompatible) {
            this.gameId = gameId;
            this.boardCompatible = boardCompatible;
        }
    }

    // gameStart events auto-open the board, but the event stream re-emits gameStart for every
    // ongoing game whenever it (re)connects. This state lives in the service-owned api so both the
    // dedupe history and an event received between activity listeners survive screen transitions.
    private final Set<String> autoOpenedGameIds = new HashSet<>();
    private PendingGameStart pendingGameStart;
    // Blocks a second gameStart while the first selected game is still loading and ongoingGameFull
    // has not arrived yet. Once that game is known to be finished, the next pairing may replace it.
    private String autoOpeningGameId;

    private boolean queuePendingGameStart(String gameId, boolean boardCompatible) {
        if (gameId == null || autoOpenedGameIds.contains(gameId)) {
            return false;
        }
        if (pendingGameStart != null) {
            return gameId.equals(pendingGameStart.gameId);
        }
        if (getViewMode() == VIEW_PUZZLE) {
            return false;
        }
        if (isOngoingGameInProgress() && !gameId.equals(getOngoingGameId())) {
            return false;
        }
        if (autoOpeningGameId != null && ongoingGameFull == null
            && !gameId.equals(autoOpeningGameId)) {
            return false;
        }
        pendingGameStart = new PendingGameStart(gameId, boardCompatible);
        return true;
    }

    /**
     * Consume the pending pairing, recording it so reconnects don't reopen the same board.
     * Returns null when no foreground navigation is pending.
     */
    public PendingGameStart consumePendingGameStart() {
        PendingGameStart result = pendingGameStart;
        if (result != null) {
            pendingGameStart = null;
            autoOpenedGameIds.add(result.gameId);
            if (result.boardCompatible) {
                autoOpeningGameId = result.gameId;
            }
        }
        return result;
    }

    public int getMyTurn() {
        if (ongoingGameFull != null) {
            return ongoingGameFull.white != null && user != null && user.equals(ongoingGameFull.white.id) ? BoardConstants.WHITE : BoardConstants.BLACK;
        } else if (ongoingPuzzle != null) {
            return ongoingPuzzle.puzzle.initialPly % 2 == 1 ? BoardConstants.WHITE : BoardConstants.BLACK;
        }
        return BoardConstants.WHITE;
    }

    public int getTurn() {
        return jni.getTurn();
    }

    public String getUser() {
        return user;
    }

    private void onAuthenticate(String result) {
        user = result;
        if (user != null) {
            event();
        }
        if (apiListener != null) {
            apiListener.onAuthenticate(user);
        }
    }

    private void onGameFinish() {
        Log.d(TAG, exportFullPGN());
        if (apiListener != null) {
            apiListener.onGameFinish();
        }
    }

    private void processGameState() {
        Log.d(TAG, "processGameState");
        if (ongoingGameFull != null) {
            LichessGameStateSnapshot snapshot = LichessGameStateSnapshot.from(ongoingGameFull);
            LichessGameStateSnapshot.Transition transition =
                snapshot.transitionFrom(lastGameStateSnapshot);

            Log.d(TAG, transition.toString());

            // jni.initFEN(ongoingGame.fen);
            if (ongoingGameFull.initialFen.equals("startpos")) {
                jni.newGame();
            } else {
                jni.initFEN(ongoingGameFull.initialFen);
            }
            resetForPGN();
            boolean replayedEntireSnapshot = processMoves(snapshot.moves);

            if (replayedEntireSnapshot) {
                dispatchGameStateTransition(snapshot, transition);
                lastGameStateSnapshot = snapshot;
            }
            dispatchState();

            if (apiListener != null) {
                apiListener.onGameUpdate(ongoingGameFull);
            }
        }
    }

    private void processPuzzle() {
        Log.d(TAG, "ProcessPuzzle " + ongoingPuzzle);
        if (ongoingPuzzle != null) {
            puzzleMoveIndex = 0;
            puzzleSolvedCleanly = true;
            puzzleComputerMovePending = false;
            hasPendingWrongMove = false;
            puzzleHandler.removeCallbacksAndMessages(null);
            jni.newGame();
            pgnMoves.clear();
            String[] allMoves = ongoingPuzzle.game.pgn.split(" ");
            int limit = Math.min(ongoingPuzzle.puzzle.initialPly + 1, allMoves.length);
            for (int i = 0; i < limit; i++) {
                if (!applyPGNMove(allMoves[i])) {
                    Log.d(TAG, "processPuzzle: skipped token " + allMoves[i]);
                }
            }
            dispatchMove(jni.getMyMove());
            dispatchState();
        }
    }

    private boolean processMoves(List<String> moveList) {
        for (String sMove : moveList) {
            if (sMove.length() >= 4) {
                try {
                    String sFrom = sMove.substring(0, 2);
                    String sTo = sMove.substring(2, 4);
                    int from = Pos.fromString(sFrom);
                    int to = Pos.fromString(sTo);

                    if (sMove.length() == 5) {
                        String promo = sMove.substring(4, 5).toLowerCase();
                        jni.setPromo(Piece.fromUCIPromo(promo));
                    }

                    if (jni.requestMove(from, to) == 0) {
                        Log.d(TAG, "Could not make move " + sMove + " " + sFrom + " " + sTo + " => " + from + " " + to);
                        return false;
                    } else {
                        addPGNEntry(jni.getNumBoard(), jni.getMyMoveToString(), "", jni.getMyMove(), -1);
                    }

                } catch (Exception e) {
                    Log.d(TAG, "Exception processing move " + sMove);
                    return false;
                }
            } else {
                Log.d(TAG, "Invalid move length " + sMove);
                return false;
            }
        }
        return true;
    }

    private void dispatchGameStateTransition(
        LichessGameStateSnapshot snapshot,
        LichessGameStateSnapshot.Transition transition
    ) {
        if (transition.newGame) {
            if (snapshot.isStarted()) {
                dispatchNewGameStarted(jni.getVariant());
            }
            return;
        }

        if (transition.moveApplied && !snapshot.moves.isEmpty()) {
            dispatchMove(jni.getMyMove());
        } else if (transition.historyPositionChanged) {
            dispatchHistoryPositionChanged(snapshot.moves.size());
        }

        if (transition.resignedColor != -1) {
            dispatchPlayerResigned(transition.resignedColor);
        }
        if (transition.drawEnded && !isBoardDetectedDraw()) {
            dispatchDrawAgreed();
        }
    }

    private boolean isBoardDetectedDraw() {
        int state = jni.getState();
        return state == BoardConstants.DRAW_MATERIAL
            || state == BoardConstants.DRAW_50
            || state == BoardConstants.STALEMATE
            || state == BoardConstants.DRAW_REPEAT;
    }

    public void retryWrongPuzzleMove() {
        hasPendingWrongMove = false;
        jni.undo();
        dispatchMove(jni.getMyMove());
        dispatchState();
        if (apiListener != null) {
            apiListener.onPuzzleRetried();
        }
    }

    private void resetForPGN() {
        pgnTags.clear();
        pgnTags.put("Event", "Lichess " + (ongoingGameFull.rated ? "rated" : "unrated"));
        pgnTags.put("White", ongoingGameFull.white.name);
        pgnTags.put("Black", ongoingGameFull.black.name);

        if (ongoingGameFull.variant.key.equals("chess960")) {
            pgnTags.put("Variant", "Fischerandom");
            pgnTags.put("Setup", "1");
            pgnTags.put("FEN", jni.toFEN());
        }

        pgnMoves.clear();
    }
}
