package jwtc.android.chess.lichess;

import android.app.Activity;
import android.content.Intent;
import android.util.Log;

import com.google.gson.Gson;
import com.google.gson.JsonObject;

import jwtc.android.chess.lichess.models.Game;
import jwtc.android.chess.lichess.models.GameFull;
import jwtc.android.chess.lichess.models.GameState;
import jwtc.android.chess.services.GameApi;
import jwtc.chess.Move;
import jwtc.chess.Pos;

public class LichessApi extends GameApi {
    private static final String TAG = "LichessApi";

    public interface LichessApiListener {
        void onAuthenticate(boolean authenticated);
    }

    private Auth auth;
    private LichessApiListener apiListener;
    private Game ongoingGame;
    private GameState ongoingGameState;
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

            auth.authenticateWithToken(new OAuth2AuthCodePKCE.Callback<Void>() {
                @Override
                public void onSuccess(Void result) {
                    Log.d(TAG, "Logged in with token");
                    onAuthenticate(true);
                }

                @Override
                public void onError(Exception e) {
                    Log.d(TAG, "Auth failed: " + e.getMessage());
                    onAuthenticate(false);
                }
            });
        } else {
            onAuthenticate(false);
        }
    }

    public void login(Activity activity) {
        this.auth.login(activity);
    }

    public void logout() {
        this.auth.logout();
    }

    public void handleLoginData(Intent data) {
        auth.handleLoginResponse(data, new OAuth2AuthCodePKCE.Callback<Void>() {
            @Override
            public void onSuccess(Void result) {
                Log.d(TAG, "Logged in!");
                onAuthenticate(true);
            }

            @Override
            public void onError(Exception e) {
                Log.d(TAG, "Auth failed: " + e.getMessage());
                onAuthenticate(false);
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

                    ongoingGame = (new Gson()).fromJson(jsonObject.get("game").getAsJsonObject(), Game.class);

                    game(ongoingGame.gameId);
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

    public void challenge() {
        this.auth.challenge(new OAuth2AuthCodePKCE.Callback<JsonObject>() {
            @Override
            public void onSuccess(JsonObject result) {
                // {"id":"mIp9e6LD","url":"https://lichess.org/mIp9e6LD","status":"created","challenger":{"name":"jcarolus","id":"jcarolus","rating":1500,"provisional":true,"online":true},"destUser":{"name":"maia1","title":"BOT","id":"maia1","rating":1635,"online":true},"variant":{"key":"standard","name":"Standard","short":"Std"},"rated":false,"speed":"rapid","timeControl":{"type":"clock","limit":360,"increment":5,"show":"6+5"},"color":"random","finalColor":"white","perf":{"icon":"","name":"Rapid"},"direction":"out"}
                //

                Log.d(TAG, "challenge posted");
            }

            @Override
            public void onError(Exception e) {
                Log.d(TAG, "challenge " + e);
            }
        });
    }

    public void move(int from, int to) {
        if (ongoingGame != null) {
            this.auth.move(ongoingGame.gameId, Pos.toString(from) + Pos.toString(to), new OAuth2AuthCodePKCE.Callback<JsonObject>() {
                @Override
                public void onSuccess(JsonObject result) {
                    Log.d(TAG, "moved");
                }

                @Override
                public void onError(Exception e) {
                    Log.d(TAG, "moved " + e);
                    // reset failed move and dispatch state
                    // dispatchState();
                }
            });
        } else {
            Log.d(TAG, "Unexpected state; move without ongoing game");
        }
    }

    private void onAuthenticate(boolean authenticated) {
        apiListener.onAuthenticate(authenticated);
        event();
    }

    private void game(String gameId) {
        this.auth.game(gameId, new Auth.AuthResponseHandler() {
            @Override
            public void onResponse(JsonObject jsonObject) {
                String type = jsonObject.get("type").getAsString();
                Log.d(TAG, "game " + jsonObject.get("type").getAsString());
                if (type.equals("gameState")) {
                    ongoingGameState = (new Gson()).fromJson(jsonObject, GameState.class);
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

    private void processGameState() {
        Log.d(TAG, "processGameState");
        if (ongoingGame != null) {
            // jni.initFEN(ongoingGame.fen);
            Log.d(TAG, "ongoingGame set");
            if (ongoingGameFull.initialFen.equals("startpos")) {
                jni.newGame();
            } else {
                jni.initFEN(ongoingGameFull.initialFen);
            }

            String moves = ongoingGameState != null
                    ? ongoingGameState.moves
                    : ongoingGameFull.state.moves;

            String[] moveList = moves.split(" ");

            Log.d(TAG, "moves " + moves);

            for (String sMove : moveList) {
                Log.d(TAG, "process " + sMove);
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

                        if (jni.requestMove(from, to) != 0) {
                            Log.d(TAG, "moved");
                        } else {
                            Log.d(TAG, "Could not make move " + sMove + " " + sFrom + " " + sTo + " => " + from + " " + to);
                        }

                    } catch (Exception e) {
                        Log.d(TAG, "Exception processing move " + sMove);
                    }
                } else {
                    Log.d(TAG, "Invalid move length " + sMove);
                }
            }


            dispatchState();
        }
    }
}

