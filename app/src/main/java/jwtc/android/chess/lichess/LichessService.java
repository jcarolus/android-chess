package jwtc.android.chess.lichess;

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

    public Auth getAuth() {
        return auth;
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
        return false;
    }

    @Override
    public void onRebind(Intent intent) {
        Log.d(TAG, "onRebind");
        super.onRebind(intent);
    }

    @Override
    public void onDestroy() {
        Log.i(TAG, "onDestroy");
        super.onDestroy();
    }

    public class LocalBinder extends Binder {
        LichessService getService() {
            Log.d(TAG, "LocalBinder.getService");
            return LichessService.this;
        }
    }
}
