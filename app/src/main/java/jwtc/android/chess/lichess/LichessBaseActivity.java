package jwtc.android.chess.lichess;

import android.util.Log;
import android.widget.Toast;

import jwtc.android.chess.R;
import jwtc.android.chess.activities.BaseActivity;

/**
 * Shared base for the boardless Lichess screens (lobby, swiss). It binds {@link LichessService},
 * adopts the single service-owned {@link LichessApi}, and registers/unregisters itself as the
 * foreground api listener. The board-bearing game screen can't extend this (it needs
 * {@code ChessBoardActivity}), so it reuses the same {@link LichessSession} helper directly.
 *
 * <p>Subclasses receive the connected, authenticated api via {@link #onLichessApiConnected} and
 * override only the {@link LichessApi.LichessApiListener} callbacks they care about (the rest are
 * default no-ops on the interface).
 */
abstract public class LichessBaseActivity extends BaseActivity
        implements LichessApi.LichessApiListener, LichessSession.Callbacks {

    private static final String TAG = "LichessBaseActivity";

    protected final LichessSession session = new LichessSession(this);
    protected LichessApi lichessApi;

    @Override
    protected void onStart() {
        super.onStart();
        if (!session.bind(this)) {
            Log.e(TAG, "Failed to bind LichessService");
            Toast.makeText(this, R.string.lichess_service_unavailable, Toast.LENGTH_LONG).show();
            finish();
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (lichessApi != null) {
            lichessApi.setApiListener(this);
        }
    }

    @Override
    protected void onPause() {
        super.onPause();
        if (lichessApi != null) {
            lichessApi.setApiListener(null);
        }
    }

    @Override
    protected void onStop() {
        super.onStop();
        session.unbind(this);
        lichessApi = null;
    }

    @Override
    public void onLichessApiReady(LichessApi api) {
        lichessApi = api;
        lichessApi.setApiListener(this);
        // The shared api is (re)created unauthenticated when the service starts. Only once it has a
        // user may this screen load. If it isn't authenticated yet, restore tokens (async) and let
        // onAuthenticate drive onLichessApiConnected; on failure we finish back to the lobby.
        if (api.getUser() != null) {
            onLichessApiConnected(api);
        } else {
            api.resume();
        }
    }

    @Override
    public void onAuthenticate(String user) {
        if (user != null && lichessApi != null) {
            onLichessApiConnected(lichessApi);
        } else {
            // No session; the lobby owns login, so bail back to it.
            finish();
        }
    }

    @Override
    public void onLichessApiLost() {
        if (lichessApi != null) {
            lichessApi.setApiListener(null);
        }
    }

    /** The shared api is connected and authenticated. Subclasses load their data here. */
    protected void onLichessApiConnected(LichessApi api) {
    }
}
