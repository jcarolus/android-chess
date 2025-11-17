package jwtc.android.chess.lichess;

import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.ServiceConnection;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.os.IBinder;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.ViewAnimator;

import jwtc.android.chess.R;
import jwtc.android.chess.activities.ChessBoardActivity;
import jwtc.android.chess.helpers.ActivityHelper;


public class LichessActivity extends ChessBoardActivity implements LichessApi.LichessApiListener {
    private static final String TAG = "LichessActivity";
    private static final int VIEW_WAITING = 0, VIEW_LOGIN = 1, VIEW_LOBBY = 2, VIEW_CHALLENGE = 3, VIEW_PLAY = 4;

    private LichessApi lichessApi;
    private ViewAnimator viewAnimator;

    private final ServiceConnection mConnection = new ServiceConnection() {
        public void onServiceConnected(ComponentName className, IBinder service) {
            Log.i(TAG, "onServiceConnected");
            LichessService lichessService = ((LichessService.LocalBinder)service).getService();
            lichessApi.setAuth(lichessService.getAuth());
            lichessApi.setApiListener(LichessActivity.this);

            lichessApi.resume();
        }

        public void onServiceDisconnected(ComponentName className) {

            Log.i(TAG, "onServiceDisconnected");
            lichessApi.setAuth(null);
        }
    };

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        Log.d(TAG, "onCreate");

        setContentView(R.layout.lichess_main);

        ActivityHelper.fixPaddings(this, findViewById(R.id.ViewAnimatorRoot));

        gameApi = new LichessApi();
        lichessApi = (LichessApi)gameApi;

        Button buttonLogin = findViewById(R.id.ButtonLogin);
        buttonLogin.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                lichessApi.login(LichessActivity.this);
            }
        });

        Button buttonChallenge = findViewById(R.id.ButtonChallenge);
        buttonChallenge.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                lichessApi.challenge();
            }
        });

        Button buttonLogout = findViewById(R.id.ButtonLogout);
        buttonLogout.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                lichessApi.logout();
            }
        });

        viewAnimator = findViewById(R.id.ViewAnimatorRoot);

        afterCreate();
    }

    @Override
    protected void onResume() {
        super.onResume();
        Log.i(TAG, "onResume");

    }

    @Override
    protected void onPause() {
        super.onPause();

        SharedPreferences.Editor editor = this.getPrefs().edit();
        editor.commit();
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);

        Log.d(TAG, "onActivityResult " + requestCode);
        if (requestCode == 1001) {
            lichessApi.handleLoginData(data);
        }
    }

    @Override
    protected void onStart() {
        Log.d(TAG, "onStart");
        super.onStart();

        startService(new Intent(LichessActivity.this, LichessService.class));
        bindService(new Intent(LichessActivity.this, LichessService.class), mConnection, Context.BIND_AUTO_CREATE);
    }

    @Override
    protected void onStop() {
        Log.i(TAG, "onStop");
        super.onStop();

        unbindService(mConnection);
    }

    @Override
    public void onAuthenticate(boolean authenticated) {
        Log.d(TAG, "onAuthenticate " + authenticated);

        if (authenticated) {
            viewAnimator.setDisplayedChild(VIEW_LOBBY);
        } else {
            viewAnimator.setDisplayedChild(VIEW_LOGIN);
        }
    }

    @Override
    public boolean requestMove(int from, int to) {
        lastMoveFrom = from;
        lastMoveTo = to;

        lichessApi.move(from, to);

        return true;
    }

    @Override
    public void OnState() {
        super.OnState();

        viewAnimator.setDisplayedChild(VIEW_PLAY);
    }
}
