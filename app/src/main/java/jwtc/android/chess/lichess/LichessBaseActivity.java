package jwtc.android.chess.lichess;

import android.content.Intent;
import android.util.Log;
import android.widget.Toast;

import jwtc.android.chess.R;
import jwtc.android.chess.activities.BaseActivity;
import jwtc.android.chess.lichess.models.Challenge;

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
            openPendingGameStart();
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
            openPendingGameStart();
        } else {
            api.resume();
        }
    }

    @Override
    public void onAuthenticate(String user) {
        if (user != null && lichessApi != null) {
            onLichessApiConnected(lichessApi);
            openPendingGameStart();
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

    @Override
    public void onGameInit(String gameId, boolean boardCompatible) {
        openPendingGameStart();
    }

    private void openPendingGameStart() {
        if (lichessApi == null) {
            return;
        }
        LichessApi.PendingGameStart gameStart = lichessApi.consumePendingGameStart();
        if (gameStart == null) {
            return;
        }
        if (gameStart.boardCompatible) {
            launchGame(gameStart.gameId);
        } else {
            onAutoOpenGameNotCompatible();
        }
    }

    protected void launchGame(String gameId) {
        Intent intent = new Intent(this, LichessGameActivity.class);
        intent.putExtra(LichessGameActivity.EXTRA_GAME_ID, gameId);
        startActivity(intent);
    }

    protected void onAutoOpenGameNotCompatible() {
        Toast.makeText(this, R.string.lichess_game_not_board_compatible, Toast.LENGTH_LONG).show();
    }

    @Override
    public void onChallenge(Challenge challenge) {
        int minutes = challenge.timeControl.limit / 60;
        String message = challenge.challenger.name
            + (challenge.rated
                ? " " + getString(R.string.lichess_challenge_dialog_message_rating) + "\n"
                : "\n")
            + getString(R.string.lichess_challenge_dialog_message_variant, challenge.variant.name) + "\n"
            + getString(R.string.lichess_challenge_dialog_message_time_control, challenge.timeControl.type) + "\n"
            + (challenge.timeControl.limit > 0
                ? " " + minutes + "+" + challenge.timeControl.increment
                : "")
            + "\n"
            + (challenge.rated
                ? getString(R.string.lichess_challenge_dialog_message_rated)
                : getString(R.string.lichess_challenge_dialog_message_unrated));

        openConfirmDialog(message,
            getString(R.string.lichess_challenge_dialog_button_accept),
            getString(R.string.lichess_challenge_dialog_button_decline),
            () -> lichessApi.acceptChallenge(challenge),
            () -> lichessApi.declineChallenge(challenge));
    }
}
