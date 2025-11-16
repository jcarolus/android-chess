package jwtc.android.chess.lichess;

import android.app.Activity;
import android.app.Service;
import android.content.Intent;
import android.os.Binder;
import android.os.IBinder;
import android.util.Log;

import androidx.annotation.Nullable;


public class LichessService extends Service {
    protected static final String TAG = "LichessService";

    private Auth auth;
    private final IBinder mBinder = new LichessService.LocalBinder();


    public interface LichessServiceListener {
        void onAuthenticate(boolean authenticated);

    }

    private LichessServiceListener lichessServiceListener;

    public void setLichessServiceListener(LichessServiceListener listener) {
        Log.d(TAG, "setLichessServiceListener");
        this.lichessServiceListener = listener;

        auth.restoreTokens();

        if (auth.hasAccessToken()) {
            Log.d(TAG, "hasAccessToken()");
            auth.authenticateWithToken(new OAuth2AuthCodePKCE.Callback<Void>() {
                @Override
                public void onSuccess(Void result) {
                    Log.d(TAG, "Logged in with token");

                    onAuthenticate(true);
                    // lichessApi.challenge();
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
        auth.login(activity);
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
        this.auth.challenge();
    }

    private void onAuthenticate(boolean authenticated) {
        if (lichessServiceListener != null) {
            lichessServiceListener.onAuthenticate(true);
        }
    }

    @Override
    public void onCreate() {
        super.onCreate();
        Log.d(TAG, "onCreate");
        auth = new Auth(this);
    }

    @Nullable
    @Override
    public IBinder onBind(Intent intent) {
        Log.d(TAG, "onBind");
        return mBinder;
    }

    @Override
    public boolean onUnbind(Intent intent) {
        Log.d(TAG, "onUnbind");
        return true;
    }

    @Override
    public void onRebind(Intent intent) {
        Log.d(TAG, "onRebind");
        super.onRebind(intent);
    }

    public class LocalBinder extends Binder {
        LichessService getService() {
            Log.d(TAG, "LocalBinder.getService");
            return LichessService.this;
        }
    }
}
