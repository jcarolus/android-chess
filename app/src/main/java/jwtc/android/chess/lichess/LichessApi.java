package jwtc.android.chess.lichess;

import java.util.HashMap;
import java.util.Map;

import jwtc.android.chess.services.GameApi;

public class LichessApi extends GameApi {
    private final Auth auth;
    public LichessApi(Auth auth) {
        super();
        this.auth = auth;
    }

    public void challenge() {

        Map<String, Object> payload = new HashMap<>();
        payload.put("username", "maia1");
        payload.put("rated", false);
        payload.put("clock.limit", 360);
        payload.put("clock.increment", 5);
        payload.put("keepAliveStream", true);

        auth.post("", payload, new OAuth2AuthCodePKCE.Callback<Object>() {
            @Override
            public void onSuccess(Object result) {

            }

            @Override
            public void onError(Exception e) {

            }
        });

//        stream = auth.openStream("/api/tv/channels", jsonObj -> {
//            // Each NDJSON line arrives here as a parsed Object (usually a Map)
//            Log.d(TAG, "Received: " + jsonObj.toString());
//        });
    }
}

