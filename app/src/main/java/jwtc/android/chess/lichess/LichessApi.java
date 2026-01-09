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
import java.util.Map;

import jwtc.android.chess.constants.Piece;
import jwtc.android.chess.lichess.models.Challenge;
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
    }

    protected int turn = 0;
    private int promotionPiece = BoardConstants.QUEEN;
    private Auth auth;
    private LichessApiListener apiListener;

    private GameFull ongoingGameFull;
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
        if (ongoingGameFull != null) {
            String uciMove = Pos.toString(from) + Pos.toString(to);
            if (isPromotionMove(from, to)) {
                uciMove += Piece.toPromoUCI(promotionPiece);
            }
            this.auth.move(ongoingGameFull.id, uciMove, new OAuth2AuthCodePKCE.Callback<JsonObject, JsonObject>() {
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
                    ongoingGameFull.state = (new Gson()).fromJson(jsonObject, GameState.class);;
                } else if (type.equals("gameFull")) {
                    ongoingGameFull = (new Gson()).fromJson(jsonObject, GameFull.class);
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
        return ongoingGameFull != null
                ? ongoingGameFull.white.id.equals(user) ? BoardConstants.WHITE : BoardConstants.BLACK
                : BoardConstants.WHITE;
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

            resetForPGN();

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
                            jni.setPromo(Piece.fromUCIPromo(promo));
                        }

                        // @TODO Chess960 castling
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

            if (apiListener != null) {
                apiListener.onGameUpdate(ongoingGameFull);
            }
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
