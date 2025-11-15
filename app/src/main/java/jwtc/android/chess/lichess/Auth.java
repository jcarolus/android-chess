package jwtc.android.chess.lichess;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.util.Log;

import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;

import java.io.IOException;
import java.lang.reflect.Type;
import java.util.Map;

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
    private static final String CLIENT_ID = "lichess-android-client";
    private static final String[] SCOPES = new String[]{"board:play"};
    private static final String PREFS_NAME = "AuthPrefs";
    private static final String KEY_ACCESS_TOKEN = "access_token";
    private static final String KEY_REFRESH_TOKEN = "refresh_token";
    private static final String KEY_EXPIRES_AT = "expires_at";

    private final Context context;
    private final OAuth2AuthCodePKCE oauth;
    private final OkHttpClient httpClient = new OkHttpClient();
    private String accessToken, refreshToken;
    Long expiresAt;
    private Me me;

    public static class Me {
        public String id;
        public String username;
        public Map<String, Object> perfs;
    }

    public Auth(Context context) {
        this.context = context;
        String redirectUri = "jwtc.android.chess:/oauth2redirect"; // match your Android manifest intent filter
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

    public void authenticateWithToken(OAuth2AuthCodePKCE.Callback<Void> callback) {
        // Already logged in, authenticate silently
        authenticate(new OAuth2AuthCodePKCE.Callback<Void>() {
            @Override
            public void onSuccess(Void result) {
                Log.d("Auth", "Restored session for " + me.username);
                callback.onSuccess(null);
            }

            @Override
            public void onError(Exception e) {
                Log.w("Auth", "Failed to restore session", e);
                callback.onError(e);
            }
        });
    }

    public void handleLoginResponse(Intent data, OAuth2AuthCodePKCE.Callback<Void> callback) {
        Log.d(TAG, "handleLoginResponse");

        oauth.handleAuthResponse(data, new OAuth2AuthCodePKCE.Callback<net.openid.appauth.TokenResponse>() {
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
                callback.onError(e);
            }
        });
    }

    public boolean hasAccessToken() {
        return accessToken != null;
    }

    public void authenticate(OAuth2AuthCodePKCE.Callback<Void> callback) {
        Request req = new Request.Builder()
                .url(LICHESS_HOST + "/api/account")
                .addHeader("Authorization", "Bearer " + accessToken)
                .build();

        httpClient.newCall(req).enqueue(new Callback() {
            @Override
            public void onFailure(Call call, IOException e) {
                callback.onError(e);
            }

            @Override
            public void onResponse(Call call, Response response) throws IOException {
                if (!response.isSuccessful()) {
                    callback.onError(new IOException("HTTP " + response.code()));
                    return;
                }

                String json = response.body().string();
                Type type = new TypeToken<Me>() {}.getType();
                me = new Gson().fromJson(json, type);
                callback.onSuccess(null);
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

    public void post(String path, Map<String, Object> jsonBody, OAuth2AuthCodePKCE.Callback<Object> callback) {
        String json = new Gson().toJson(jsonBody);
        RequestBody body = RequestBody.create(
                json,
                MediaType.get("application/json; charset=utf-8")
        );

        Request req = new Request.Builder()
                .url(LICHESS_HOST + path)
                .addHeader("Authorization", "Bearer " + accessToken)
                .post(body)
                .build();

        httpClient.newCall(req).enqueue(new Callback() {
            @Override
            public void onFailure(Call call, IOException e) {
                callback.onError(e);
            }

            @Override
            public void onResponse(Call call, Response response) throws IOException {
                if (!response.isSuccessful()) {
                    callback.onError(new IOException("HTTP " + response.code()));
                    return;
                }
                Object result = new Gson().fromJson(response.body().string(), Object.class);
                callback.onSuccess(result);
            }
        });
    }

    public NdJsonStream.Stream openStream(String path, NdJsonStream.Handler handler) {
        Log.d(TAG, "openStream " + path);
        Request request = new Request.Builder()
                .url(LICHESS_HOST + path)
                .addHeader("Authorization", "Bearer " + accessToken)
                .build();

        NdJsonStream.Stream stream = NdJsonStream.readStream("STREAM " + path, httpClient, request, handler);

        stream.getClosePromise().thenRun(() -> {
            Log.d(TAG, "Stream closed for path: " + path);
        });

        return stream;
    }
}