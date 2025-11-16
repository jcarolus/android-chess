package jwtc.android.chess.lichess;

import android.app.Activity;
import android.content.Intent;

import jwtc.android.chess.services.GameApi;

public class LichessApi extends GameApi implements LichessService.LichessServiceListener {
    private static final String TAG = "LichessApi";

    private LichessService lichessService;

    public interface LichessApiListener {
        public void onAuthenticate(boolean authenticated);
    }

    private LichessApiListener lichessApiListener;

    public LichessApi() {
        super();

    }

    public void setService(LichessService lichessService, LichessApiListener lichessApiListener) {
        this.lichessService = lichessService;
        this.lichessApiListener = lichessApiListener;
    }

    public void login(Activity activity) {
        lichessService.login(activity);
    }

    public void challenge() {
        lichessService.challenge();
    }
    public void handleLoginData(Intent data) {
        lichessService.handleLoginData(data);
    }

    @Override
    public void onAuthenticate(boolean authenticated) {
        if (lichessApiListener != null) {
            lichessApiListener.onAuthenticate(authenticated);
        }
    }

}

