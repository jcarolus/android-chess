package jwtc.android.chess.lichess;

import android.app.Activity;
import android.content.Intent;
import android.util.Log;

import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;

import java.util.ArrayList;
import java.util.List;

import jwtc.android.chess.lichess.models.Game;
import jwtc.android.chess.lichess.models.GameFull;
import jwtc.android.chess.lichess.models.GameState;
import jwtc.android.chess.services.GameApi;
import jwtc.chess.Pos;
import jwtc.chess.board.BoardConstants;

public class LichessApi extends GameApi {
    private static final String TAG = "LichessApi";

    public interface LichessApiListener {
        void onAuthenticate(String user);
        void onGameInit(Game game);
        void onGameUpdate(GameFull gameFull);
        // void onDrawAccepted(boolean accepted);
        void onGameFinish();
        void onInvalidMove(String reason);
        // void onChallenge();
        void onNowPlaying(List<Game> games);
    }

    protected int myTurn = 0, turn = 0;
    private Auth auth;
    private LichessApiListener apiListener;
    private Game ongoingGame;
    private GameFull ongoingGameFull;

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

                    // @TODO close game stream / keep track of multiple games

                    ongoingGame = (new Gson()).fromJson(jsonObject.get("game").getAsJsonObject(), Game.class);

                    myTurn = ongoingGame.color.equals("white") ? BoardConstants.WHITE : BoardConstants.BLACK;
                    if (apiListener != null) {
                        apiListener.onGameInit(ongoingGame);
                    }

                    game(ongoingGame.gameId);
                } else if (type.equals("gameFinish")) {
                    onGameFinish();
                } else if (type.equals("challenge")) {
                    onChallenge();
                }
            }

            @Override
            public void onError() {

            }

            @Override
            public void onClose() {
                Log.d(TAG, "event closed");
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
                    apiListener.onNowPlaying(gameList);
                }
            }

            @Override
            public void onError(JsonObject e) {
                Log.d(TAG, "playing " + e);
            }
        });
    }

    public void challenge() {
        this.auth.challenge(new OAuth2AuthCodePKCE.Callback<JsonObject, JsonObject>() {
            @Override
            public void onSuccess(JsonObject result) {
                Log.d(TAG, "challenge posted");
            }

            @Override
            public void onError(JsonObject e) {
                Log.d(TAG, "challenge " + e);
            }
        });
    }

    public void move(int from, int to) {
        if (ongoingGame != null) {
            this.auth.move(ongoingGame.gameId, Pos.toString(from) + Pos.toString(to), new OAuth2AuthCodePKCE.Callback<JsonObject, JsonObject>() {
                @Override
                public void onSuccess(JsonObject result) {
                    Log.d(TAG, "moved");
                }

                @Override
                public void onError(JsonObject result) {
                    Log.d(TAG, "moved " + result);
                    dispatchState();
                    if (apiListener != null) {
                        apiListener.onInvalidMove(result.get("error").getAsString());
                    }
                }
            });
        } else {
            Log.d(TAG, "Unexpected state; move without ongoing game");
        }
    }

    public void resign() {
        if (ongoingGame != null) {
            this.auth.resign(ongoingGame.gameId, new OAuth2AuthCodePKCE.Callback<JsonObject, JsonObject>() {
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
        if (ongoingGame != null) {
            this.auth.draw(ongoingGame.gameId, accept ? "yes" : "no", new OAuth2AuthCodePKCE.Callback<JsonObject, JsonObject>() {
                @Override
                public void onSuccess(JsonObject result) {

                }

                @Override
                public void onError(JsonObject e) {

                }
            });
        }
    }

    public int getMyTurn() {
        return myTurn;
    }

    public int getTurn() {
        return jni.getTurn();
    }

    private void onAuthenticate(String user) {
        if (apiListener != null) {
            apiListener.onAuthenticate(user);
        }
        if (user != null) {
            // auth.closeStreams() ?
            playing();
            event();
        }
    }

    private void game(String gameId) {
        this.auth.game(gameId, new Auth.AuthResponseHandler() {
            @Override
            public void onResponse(JsonObject jsonObject) {
                String type = jsonObject.get("type").getAsString();
                Log.d(TAG, "game " + jsonObject.get("type").getAsString());
                if (type.equals("gameState")) {
                    ongoingGameFull.state = (new Gson()).fromJson(jsonObject, GameState.class);;
                } else if (type.equals("gameFull")) {
                    ongoingGameFull = (new Gson()).fromJson(jsonObject, GameFull.class);
                }
                processGameState();
            }

            @Override
            public void onError() {

            }

            @Override
            public void onClose() {

            }
        });
    }

    private void onGameFinish() {
        if (apiListener != null) {
            apiListener.onGameFinish();
        }
    }

    private void onChallenge() {

    }
    private void processGameState() {
        Log.d(TAG, "processGameState");
        if (ongoingGame != null) {
            // jni.initFEN(ongoingGame.fen);
            if (ongoingGameFull.initialFen.equals("startpos")) {
                jni.newGame();
            } else {
                jni.initFEN(ongoingGameFull.initialFen);
            }

            String moves = ongoingGameFull.state.moves;

            String[] moveList = moves.split(" ");

//            Log.d(TAG, "moves " + moves);

            for (String sMove : moveList) {
                if (sMove.length() >= 4) {
                    try {
                        String sFrom = sMove.substring(0, 2);
                        String sTo = sMove.substring(2, 4);
                        int from = Pos.fromString(sFrom);
                        int to = Pos.fromString(sTo);

                        if (sMove.length() == 5) {
                            String promo = sMove.substring(4, 5).toLowerCase();
                            // jni.setPromo(4 - item);
                        }

                        if (jni.requestMove(from, to) == 0) {
                            Log.d(TAG, "Could not make move " + sMove + " " + sFrom + " " + sTo + " => " + from + " " + to);
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

            if (apiListener != null) {
                apiListener.onGameUpdate(ongoingGameFull);
            }
        }
    }
}
