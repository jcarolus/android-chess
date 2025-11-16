package jwtc.android.chess.lichess;

import android.app.Activity;
import android.content.Intent;
import android.util.Log;

import com.google.gson.JsonObject;

import jwtc.android.chess.services.GameApi;

public class LichessApi extends GameApi {
    private static final String TAG = "LichessApi";

    public interface LichessApiListener {
        void onAuthenticate(boolean authenticated);
    }

    private Auth auth;
    private LichessApiListener apiListener;

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
        }
    }

    public void login(Activity activity) {
        this.auth.login(activity);
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

    public void challenge() {
        this.auth.challenge(new Auth.AuthResponseHandler() {
            @Override
            public void onResponse(JsonObject jsonObject) {
                // @TODO init ongoing game id
                game(jsonObject.get("id").getAsString());

                //  {id=3c0lBlW9, url=https://lichess.org/3c0lBlW9, status=created,
                //  challenger={name=jcarolus, id=jcarolus, rating=1500.0, provisional=true},
                //  destUser={name=maia1, title=BOT, id=maia1, rating=1603.0, online=true},
                //  variant={key=standard, name=Standard, short=Std}, rated=false, speed=rapid, timeControl={type=clock, limit=360.0, increment=5.0, show=6+5},
                //  color=random, finalColor=black, perf={icon=, name=Rapid}, direction=out}
            }

            @Override
            public void onError() {

            }

            @Override
            public void onClose() {

            }
        });
    }

    public void move(int move) {
        // this.auth.move()
    }

    private void onAuthenticate(boolean authenticated) {
        apiListener.onAuthenticate(authenticated);
    }

    private void game(String gameId) {
        this.auth.game(gameId, new Auth.AuthResponseHandler() {
            @Override
            public void onResponse(JsonObject jsonObject) {
/*
{
    "type": "gameState",
    "moves": "e2e4 ...",
    "wtime": 424040,
    "btime": 484040,
    "winc": 0,
    "binc": 0,
    "status": "started"
}

{
    "id": "o2RUSazI",
    "variant": {
        "key": "standard",
        "name": "Standard",
        "short": "Std"
    },
    "speed": "rapid",
    "perf": {
        "name": "Rapid"
    },
    "rated": false,
    "createdAt": 1763321708536,
    "white": {
        "id": "jcarolus",
        "name": "jcarolus",
        "title": null,
        "rating": 1500,
        "provisional": true
    },
    "black": {
        "id": "maia1",
        "name": "maia1",
        "title": "BOT",
        "rating": 1566
    },
    "initialFen": "startpos",
    "clock": {
        "initial": 600000,
        "increment": 0
    },
    "type": "gameFull",
    "state": {
        "type": "gameState",
        "moves": "e2e4 ..",
        "wtime": 421290,
        "btime": 484040,
        "winc": 0,
        "binc": 0,
        "status": "mate",
        "winner": "white"
    }
}

 */

            }

            @Override
            public void onError() {

            }

            @Override
            public void onClose() {

            }
        });
    }
}

