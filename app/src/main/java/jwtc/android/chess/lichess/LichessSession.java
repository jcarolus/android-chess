package jwtc.android.chess.lichess;

import android.app.Activity;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.ServiceConnection;
import android.os.IBinder;
import android.util.Log;

/**
 * Encapsulates an activity's connection to {@link LichessService} and the single, service-owned
 * {@link LichessApi} it exposes. This is the unit of reuse shared by the (boardless) Lichess
 * screens and the board-bearing game screen, which can't share a common Activity base class
 * because of Java single inheritance.
 *
 * <p>The service owns the api, so its game/puzzle/auth state survives activity transitions and
 * rotation. Each activity binds in onStart and unbinds in onStop; the shared api is delivered
 * asynchronously via {@link Callbacks#onLichessApiReady(LichessApi)} once the service connects.
 */
public class LichessSession {
    private static final String TAG = "LichessSession";

    public interface Callbacks {
        /**
         * Delivered on the main thread once the shared api is available. The activity should adopt
         * the api (wire its listeners/clock, register as the api listener) and kick off resume()
         * or handle a pending OAuth result.
         */
        void onLichessApiReady(LichessApi api);

        /** The service disconnected unexpectedly (service process death). */
        void onLichessApiLost();
    }

    private final Callbacks callbacks;
    private boolean bound = false;
    private boolean connected = false;
    // A login redirect (onActivityResult) can arrive before the service connects; stash it here
    // until the api is ready. Only the OAuth-owning (lobby) activity uses this.
    private Intent pendingData;

    private final ServiceConnection connection = new ServiceConnection() {
        @Override
        public void onServiceConnected(ComponentName className, IBinder service) {
            Log.i(TAG, "onServiceConnected");
            connected = true;
            LichessApi api = ((LichessService.LocalBinder) service).getService().getLichessApi();
            callbacks.onLichessApiReady(api);
        }

        @Override
        public void onServiceDisconnected(ComponentName className) {
            Log.i(TAG, "onServiceDisconnected");
            connected = false;
            callbacks.onLichessApiLost();
        }
    };

    public LichessSession(Callbacks callbacks) {
        this.callbacks = callbacks;
    }

    /** Binds the service. Returns false if binding failed (caller should abort the activity). */
    public boolean bind(Activity activity) {
        bound = activity.bindService(new Intent(activity, LichessService.class), connection, Context.BIND_AUTO_CREATE);
        return bound;
    }

    public void unbind(Activity activity) {
        if (bound) {
            activity.unbindService(connection);
            bound = false;
        }
        connected = false;
    }

    public boolean isConnected() {
        return connected;
    }

    public void setPendingData(Intent data) {
        pendingData = data;
    }

    public boolean hasPendingData() {
        return pendingData != null;
    }

    public Intent consumePendingData() {
        Intent data = pendingData;
        pendingData = null;
        return data;
    }
}
