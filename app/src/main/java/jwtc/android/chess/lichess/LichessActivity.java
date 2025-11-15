package jwtc.android.chess.lichess;

import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Build;
import android.os.Bundle;
import android.util.Log;

import java.util.HashMap;
import java.util.Map;

import jwtc.android.chess.R;
import jwtc.android.chess.activities.ChessBoardActivity;
import jwtc.android.chess.helpers.ActivityHelper;

public class LichessActivity  extends ChessBoardActivity {
    private static final String TAG = "LichessActivity";
    private Auth auth;
    private NdJsonStream.Stream stream;
    private LichessApi lichessApi;

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        setContentView(R.layout.puzzle);

        ActivityHelper.fixPaddings(this, findViewById(R.id.LayoutMain));

        auth = new Auth(this);
        gameApi = new LichessApi(auth);
        lichessApi = (LichessApi)gameApi;

        afterCreate();
    }



    @Override
    protected void onResume() {
        super.onResume();
        Log.i(TAG, "onResume");

        auth.restoreTokens();

        if (auth.hasAccessToken()) {
            Log.d(TAG, "hasAccessToken()");
            auth.authenticateWithToken(new OAuth2AuthCodePKCE.Callback<Void>() {
                @Override
                public void onSuccess(Void result) {
                    Log.d(TAG, "Logged in with token");

                    lichessApi.challenge();
                }

                @Override
                public void onError(Exception e) {
                    Log.d(TAG, "Auth failed: " + e.getMessage());
                }
            });
        } else {

            auth.login(this);
        }
    }

    @Override
    protected void onPause() {
        super.onPause();

        SharedPreferences.Editor editor = this.getPrefs().edit();
        editor.commit();

        if (stream != null) {
            stream.close();
        }
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);

        Log.d(TAG, "onActivityResult " + requestCode);
        if (requestCode == 1001) {
            auth.handleLoginResponse(data, new OAuth2AuthCodePKCE.Callback<Void>() {
                @Override
                public void onSuccess(Void result) {
                    Log.d(TAG, "Logged in!");

                    lichessApi.challenge();
                }

                @Override
                public void onError(Exception e) {
                    Log.d(TAG, "Auth failed: " + e.getMessage());
                }
            });
        }
    }
}
