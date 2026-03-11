package jwtc.android.chess.lichess;

import android.app.Activity;
import android.content.Intent;
import android.util.Log;

import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

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
import jwtc.android.chess.services.GameApi;
import jwtc.chess.Pos;
import jwtc.chess.board.BoardConstants;

public class LichessApi extends GameApi {
    private static final String TAG = "LichessApi";
    private static final String PUZZLE_ANGLE_DEFAULT = "mix";

    public interface LichessApiListener {
        void onAuthenticate(String user);

        void onGameInit(String gameId);

        void onGameUpdate(GameFull gameFull);

        // void onDrawAccepted(boolean accepted);
        void onGameFinish();

        void onGameDisconnected();

        void onInvalidMove(String reason);

        void onNowPlaying(List<Game> games, String me);

        void onConnectionError();

        void onChallenge(Challenge challenge);

        void onChallengeCancelled(Challenge challenge);

        void onChallengeDeclined(Challenge challenge);

        void onMyChallengeCancelled();

        void onMySeekCancelled();

        void onPuzzle(PuzzleAndGame puzzle);
        void onPuzzleSolve(PuzzleAndGame nextPuzzle, PuzzleBatchSolveRound solveRound);
        void onPuzzleUnexpectedMove();
        void onPuzzleCompleted();
    }

    protected int turn = 0;
    private int promotionPiece = BoardConstants.QUEEN;
    private Auth auth;
    private LichessApiListener apiListener;

    private GameFull ongoingGameFull;
    private PuzzleAndGame ongoingPuzzle;
    private int puzzleMoveIndex = 0;
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

    public void event() {
        this.auth.event(new Auth.AuthResponseHandler() {
            @Override
            public void onResponse(JsonObject jsonObject) {
                String type = jsonObject.get("type").getAsString();
                Log.d(TAG, "event " + jsonObject.get("type").getAsString());
                if (type.equals("gameStart")) {
                    Game ongoingGame = (new Gson()).fromJson(jsonObject.get("game").getAsJsonObject(), Game.class);

                    if (apiListener != null) {
                        apiListener.onGameInit(ongoingGame.gameId);
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

    public void fetchPuzzle(String angle, String difficulty, String color) {
        String puzzleAngle = angle == null || angle.isEmpty() ? PUZZLE_ANGLE_DEFAULT : angle;
        int puzzleCount = 1;

        this.auth.puzzleBatchSelect(puzzleAngle, puzzleCount, difficulty, color, new OAuth2AuthCodePKCE.Callback<JsonObject, JsonObject>() {
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
                    if (!response.puzzles.isEmpty() && !response.rounds.isEmpty() && apiListener != null) {
                        apiListener.onPuzzleSolve(response.puzzles.get(0), response.rounds.get(0));
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

    private void applyUciMoveToBoard(String uciMove) {
        try {
            int from = Pos.fromString(uciMove.substring(0, 2));
            int to = Pos.fromString(uciMove.substring(2, 4));
            if (uciMove.length() == 5) {
                jni.setPromo(Piece.fromUCIPromo(uciMove.substring(4, 5).toLowerCase()));
            }
            if (jni.requestMove(from, to) != 0) {
                addPGNEntry(jni.getNumBoard(), jni.getMyMoveToString(), "", jni.getMyMove(), -1);
            } else {
                Log.d(TAG, "applyUciMoveToBoard failed: " + uciMove);
            }
        } catch (Exception e) {
            Log.d(TAG, "applyUciMoveToBoard exception: " + uciMove + " " + e);
        }
    }

    private void handlePuzzleError(JsonObject e) {
        String error = e.has("error") ? e.get("error").getAsString() : "";
        if (error.startsWith("Missing scope")) {
            Log.d(TAG, "Puzzle scope missing — clearing token and forcing re-login");
            auth.clearTokens();
            onAuthenticate(null);
        }
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
            List<String> solution = ongoingPuzzle.puzzle.solution;
            if (puzzleMoveIndex >= solution.size()) {
                Log.d(TAG, "Solution index");
                return;
            }
            if (!uciMove.equals(solution.get(puzzleMoveIndex))) {
                Log.d(TAG, "Not equal " + uciMove + " = " + solution.get(puzzleMoveIndex));
                dispatchIllegalMove();
                if (apiListener != null) {
                    apiListener.onPuzzleUnexpectedMove();
                }
                return;
            }

            // correct player move — apply it
            applyUciMoveToBoard(uciMove);
            dispatchMove(jni.getMyMove());
            puzzleMoveIndex++;

            if (puzzleMoveIndex >= solution.size()) {
                dispatchState();
                if (apiListener != null) {
                    apiListener.onPuzzleCompleted();
                }
                return;
            }

            // apply computer's response
            applyUciMoveToBoard(solution.get(puzzleMoveIndex));
            dispatchMove(jni.getMyMove());
            puzzleMoveIndex++;

            dispatchState();

            if (puzzleMoveIndex >= solution.size()) {
                if (apiListener != null) apiListener.onPuzzleCompleted();
            }
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
                    ;
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

    public int getMyTurn() {
        if (ongoingGameFull != null) {
            return ongoingGameFull.white.id.equals(user) ? BoardConstants.WHITE : BoardConstants.BLACK;
        } else if (ongoingPuzzle != null) {
            return ongoingPuzzle.puzzle.initialPly % 2 == 1 ? BoardConstants.WHITE : BoardConstants.BLACK;
        }
        return BoardConstants.WHITE;
    }

    public int getTurn() {
        return jni.getTurn();
    }

    private void onAuthenticate(String result) {
        user = result;
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
            // jni.initFEN(ongoingGame.fen);
            if (ongoingGameFull.initialFen.equals("startpos")) {
                jni.newGame();
            } else {
                jni.initFEN(ongoingGameFull.initialFen);
            }
            String moves = ongoingGameFull.state.moves;

            resetForPGN();
            processMoves(moves.isEmpty() ? Collections.emptyList() : Arrays.asList(moves.split(" ")));

            if (apiListener != null) {
                apiListener.onGameUpdate(ongoingGameFull);
            }
        }
    }

    private void processPuzzle() {
        Log.d(TAG, "ProcessPuzzle " + ongoingPuzzle);
        if (ongoingPuzzle != null) {
            puzzleMoveIndex = 0;
            jni.newGame();
            pgnMoves.clear();
            String[] allMoves = ongoingPuzzle.game.pgn.split(" ");
            int limit = Math.min(ongoingPuzzle.puzzle.initialPly, allMoves.length);
            for (int i = 0; i < limit; i++) {
                if (!applyPGNMove(allMoves[i])) {
                    Log.d(TAG, "processPuzzle: skipped token " + allMoves[i]);
                }
            }
            dispatchMove(jni.getMyMove());
            dispatchState();
        }
    }

    private void processMoves(List<String> moveList) {
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
                        break;
                    } else {
                        addPGNEntry(jni.getNumBoard(), jni.getMyMoveToString(), "", jni.getMyMove(), -1);
                    }

                } catch (Exception e) {
                    Log.d(TAG, "Exception processing move " + sMove);
                }
            } else {
                Log.d(TAG, "Invalid move length " + sMove);
            }
        }

        dispatchMove(jni.getMyMove());
        dispatchState();
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
