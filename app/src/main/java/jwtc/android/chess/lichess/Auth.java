package jwtc.android.chess.lichess;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;

import com.google.gson.Gson;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.google.gson.reflect.TypeToken;

import java.io.IOException;
import java.lang.reflect.Type;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.TimeUnit;

import okhttp3.Call;
import okhttp3.Callback;
import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;

public class Auth {
    private static final String TAG = "lichess.Auth";
    private static final String LICHESS_HOST = "https://lichess.org";
    private static final String CLIENT_ID = "lichess-api-demo"; // "lichess-android-client";
    private static final String[] SCOPES = new String[]{"board:play"};
    private static final String PREFS_NAME = "AuthPrefs";
    private static final String KEY_ACCESS_TOKEN = "access_token";
    private static final String KEY_REFRESH_TOKEN = "refresh_token";
    private static final String KEY_EXPIRES_AT = "expires_at";

    private final Context context;
    private final OAuth2AuthCodePKCE oauth;
    private final OkHttpClient httpClient = new OkHttpClient();
    private final OkHttpClient httpStreamClient = new OkHttpClient.Builder()
            .connectTimeout(30, TimeUnit.SECONDS)
            .writeTimeout(30, TimeUnit.SECONDS)
            .readTimeout(0, TimeUnit.MILLISECONDS) // infinite timeout
            .build();
    private final Handler mainHandler = new Handler(Looper.getMainLooper());
    NdJsonStream.Stream eventStream, gameStream;
    private String accessToken, refreshToken;
    Long expiresAt;


    public interface AuthResponseHandler {
        void onResponse(JsonObject jsonObject);
        void onClose(boolean success);
    }

    public Auth(Context context) {
        this.context = context;
        String redirectUri = "jwtc.android.chess:/oauth2redirect"; // match Android manifest intent filter
        this.oauth = new OAuth2AuthCodePKCE(
                context,
                LICHESS_HOST + "/oauth",
                LICHESS_HOST + "/api/token",
                CLIENT_ID,
                redirectUri,
                SCOPES
        );
    }

    public void login(Activity activity) {
        Log.d(TAG, "login");
        oauth.startAuth(activity);
    }

    public void logout() {
        // /api/token`, { method: 'DELETE' });
        clearTokens();
    }

    public void authenticateWithToken(OAuth2AuthCodePKCE.Callback<String, Exception> callback) {
        // Already logged in, authenticate silently
        authenticate(new OAuth2AuthCodePKCE.Callback<String, Exception>() {
            @Override
            public void onSuccess(String result) {
                Log.d("Auth", "Restored session for " + result);
                mainHandler.post(() -> {
                    callback.onSuccess(result);
                });
            }

            @Override
            public void onError(Exception e) {
                Log.w("Auth", "Failed to restore session", e);
                mainHandler.post(() -> {
                    callback.onError(e);
                });
            }
        });
    }

    public void handleLoginResponse(Intent data, OAuth2AuthCodePKCE.Callback<String, Exception> callback) {
        Log.d(TAG, "handleLoginResponse");

        oauth.handleAuthResponse(data, new OAuth2AuthCodePKCE.Callback<net.openid.appauth.TokenResponse, Exception>() {
            @Override
            public void onSuccess(net.openid.appauth.TokenResponse result) {
                Log.d(TAG, "handleLoginResponse.onSuccess");

                accessToken = result.accessToken;
                refreshToken = result.refreshToken;
                expiresAt = result.accessTokenExpirationTime;

                saveTokens();

                authenticate(callback);
            }

            @Override
            public void onError(Exception e) {
                mainHandler.post(() -> {
                    callback.onError(e);
                });
            }
        });
    }

    public void playing(OAuth2AuthCodePKCE.Callback<JsonObject, JsonObject> callback) {
        get("/api/account/playing?nd=5", callback);
    }

    public void challenge(Map<String, Object> payload, OAuth2AuthCodePKCE.Callback<JsonObject, JsonObject> callback) {
        String username = (String) payload.get("username");
        payload.remove("username");
        post("/api/challenge/" + username, payload, callback);
    }

    public void acceptChallenge(String challengeId, OAuth2AuthCodePKCE.Callback<JsonObject, JsonObject> callback) {
        post("/api/challenge/" + challengeId + "/accept", null, callback);
    }

    public void declineChallenge(String challengeId, OAuth2AuthCodePKCE.Callback<JsonObject, JsonObject> callback) {
        post("/api/challenge/" + challengeId + "/decline", null, callback);
    }

    public void event(AuthResponseHandler responseHandler) {
        if (eventStream != null) {
            eventStream.close();
        }
        eventStream = openStream("/api/stream/event", null, new NdJsonStream.Handler() {
            @Override
            public void onResponse(JsonObject jsonObject) {
                mainHandler.post(() -> {
                    responseHandler.onResponse(jsonObject);
                });
            }

            @Override
            public void onClose(boolean success) {
                mainHandler.post(() -> {
                    responseHandler.onClose(success);
                    eventStream = null;
                });
            }
        });
    }

    public void game(String gameId, AuthResponseHandler responseHandler) {
        if (gameStream != null) {
            gameStream.close();
        }
        gameStream = openStream("/api/board/game/stream/" + gameId, null, new NdJsonStream.Handler() {
            @Override
            public void onResponse(JsonObject jsonObject) {
                String type = jsonObject.get("type").getAsString();
                if (type.equals("gameFull") || type.equals("gameState")) {
                    mainHandler.post(() -> {
                        responseHandler.onResponse(jsonObject);
                    });
                }
            }

            @Override
            public void onClose(boolean sucess) {
                mainHandler.post(() -> {
                    responseHandler.onClose(sucess);
                    gameStream = null;
                });
            }
        });
    }

    public void move(String gameId, String move, OAuth2AuthCodePKCE.Callback<JsonObject, JsonObject> callback) {
        post("/api/board/game/" + gameId + "/move/" + move, null, callback);
    }

    public void resign(String gameId, OAuth2AuthCodePKCE.Callback<JsonObject, JsonObject> callback) {
        post("/api/board/game/" + gameId + "/resign", null, callback);
    }

    public void abort(String gameId, OAuth2AuthCodePKCE.Callback<JsonObject, JsonObject> callback) {
        post("/api/board/game/" + gameId + "/abort", null, callback);
    }

    public void draw(String gameId, String accept /* yes|no*/, OAuth2AuthCodePKCE.Callback<JsonObject, JsonObject> callback) {
        post("/api/board/game/" + gameId + "/draw/" + accept, null, callback);
    }

    public boolean hasAccessToken() {
        return accessToken != null;
    }

    public void authenticate(OAuth2AuthCodePKCE.Callback<String, Exception> callback) {
        Request req = new Request.Builder()
                .url(LICHESS_HOST + "/api/account")
                .addHeader("Authorization", "Bearer " + accessToken)
                .build();

        httpClient.newCall(req).enqueue(new Callback() {
            @Override
            public void onFailure(Call call, IOException e) {
                mainHandler.post(() -> {
                    callback.onError(e);
                });
            }

            @Override
            public void onResponse(Call call, Response response) throws IOException {
                if (!response.isSuccessful()) {
                    mainHandler.post(() -> {
                        callback.onError(new IOException("HTTP " + response.code()));
                    });
                    return;
                }

                String json = response.body().string();
                JsonObject jsonObject = JsonParser.parseString(json).getAsJsonObject();
                Log.d(TAG, json);
                mainHandler.post(() -> {
                    callback.onSuccess(jsonObject.get("id").getAsString());
                });

            }
        });
    }

    public void saveTokens() {
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
                .edit()
                .putString(KEY_ACCESS_TOKEN, accessToken)
                .putString(KEY_REFRESH_TOKEN, refreshToken)
                .putLong(KEY_EXPIRES_AT, expiresAt != null ? expiresAt : 0L)
                .apply();
    }

    public void clearTokens() {
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
                .edit()
                .clear()
                .apply();
    }

    public void restoreTokens() {
        SharedPreferences prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
        accessToken = prefs.getString(KEY_ACCESS_TOKEN, null);
        expiresAt = prefs.getLong(KEY_EXPIRES_AT, 0L);
        if (expiresAt > 0 && System.currentTimeMillis() > expiresAt) {
            accessToken = null; // expired, force refresh or re-login
        }
    }

    public void closeStreams() {
        Log.d(TAG, "closeStreams");
        if (gameStream != null) {
            gameStream.close();
        }
        if (eventStream != null) {
            eventStream.close();
        }
    }

    public void post(String path, Map<String, Object> jsonBody, OAuth2AuthCodePKCE.Callback<JsonObject, JsonObject> callback) {
        Log.d(TAG, "post " + path);
        Request.Builder reqBuilder =  new Request.Builder()
                .url(LICHESS_HOST + path)
                .addHeader("Authorization", "Bearer " + accessToken)
                .addHeader("Accept", "*/*");

        if (jsonBody != null) {
            String json = new Gson().toJson(jsonBody);
            RequestBody body = RequestBody.create(
                    json,
                    MediaType.get("application/json; charset=utf-8")
            );
            reqBuilder.post(body);
        } else {
            reqBuilder.post(RequestBody.create(new byte[0]));
        }

        httpClient.newCall(reqBuilder.build()).enqueue(new Callback() {
            @Override
            public void onFailure(Call call, IOException e) {
                Log.d(TAG, "onFailure " + e);
                mainHandler.post(() -> {
                    //callback.onError(e);
                    // @TODO general error
                });
            }

            @Override
            public void onResponse(Call call, Response response) throws IOException {
                if (!response.isSuccessful()) {
                    String responseBody = response.body().string();
                    try {
                        JsonObject jsonObject = JsonParser.parseString(responseBody).getAsJsonObject();
                        mainHandler.post(() -> {
                            callback.onError(jsonObject);
                        });
                    } catch (Exception ex) {
                        Log.d(TAG, "could not parse " + response.code() + " => " + responseBody);
                    }
                    return;
                }
                Log.d(TAG, "wating for response...");
                String responseBody = response.body().string();
                Log.d(TAG, "responseBody " + responseBody);
                try {
                    String[] lines = responseBody.split("\r?\n");
                    for (String line : lines) {
                        if (line == null || line.trim().isEmpty()) {
                            continue;
                        }

                        JsonObject jsonObject = JsonParser.parseString(line).getAsJsonObject();
                        mainHandler.post(() -> {
                            callback.onSuccess(jsonObject);
                        });
                    }
                } catch (Exception ex) {
                    Log.d(TAG, "Caught " + ex);
                    mainHandler.post(() -> {
                        // callback.onError(ex);
                        // @TODO another general error
                    });
                }
            }
        });
    }

    public void get(String path, OAuth2AuthCodePKCE.Callback<JsonObject, JsonObject> callback) {
        Log.d(TAG, "get " + path);
        Request.Builder reqBuilder =  new Request.Builder()
                .url(LICHESS_HOST + path)
                .addHeader("Authorization", "Bearer " + accessToken)
                .addHeader("Accept", "*/*")
                .get();

        httpClient.newCall(reqBuilder.build()).enqueue(new Callback() {
            @Override
            public void onFailure(Call call, IOException e) {
                Log.d(TAG, "onFailure " + e);
                mainHandler.post(() -> {
                    //callback.onError(e);
                    // @TODO general error
                });
            }

            @Override
            public void onResponse(Call call, Response response) throws IOException {
                if (!response.isSuccessful()) {
                    String responseBody = response.body().string();
                    try {
                        JsonObject jsonObject = JsonParser.parseString(responseBody).getAsJsonObject();
                        mainHandler.post(() -> {
                            callback.onError(jsonObject);
                        });
                    } catch (Exception ex) {
                        Log.d(TAG, "could not parse " + response.code() + " => " + responseBody);
                    }
                    return;
                }
                Log.d(TAG, "wating for response...");
                String responseBody = response.body().string();
                Log.d(TAG, "responseBody " + responseBody);
                try {
                    String[] lines = responseBody.split("\r?\n");
                    for (String line : lines) {
                        if (line == null || line.trim().isEmpty()) {
                            continue;
                        }

                        JsonObject jsonObject = JsonParser.parseString(line).getAsJsonObject();
                        mainHandler.post(() -> {
                            callback.onSuccess(jsonObject);
                        });
                    }
                } catch (Exception ex) {
                    Log.d(TAG, "Caught " + ex);
                    mainHandler.post(() -> {
                        // callback.onError(ex);
                        // @TODO another general error
                    });
                }
            }
        });
    }

    public NdJsonStream.Stream openStream(String path, Map<String, Object> jsonBody, NdJsonStream.Handler handler) {
        Log.d(TAG, "openStream " + path);
        Request.Builder reqBuilder =  new Request.Builder()
                .url(LICHESS_HOST + path)
                .addHeader("Authorization", "Bearer " + accessToken);

        if (jsonBody != null) {
            String json = new Gson().toJson(jsonBody);
            RequestBody body = RequestBody.create(
                    json,
                    MediaType.get("application/json; charset=utf-8")
            );
            reqBuilder.post(body);
        }

        Request request = reqBuilder.build();

        return NdJsonStream.readStream("STREAM " + path, httpStreamClient, request, handler);
    }
}