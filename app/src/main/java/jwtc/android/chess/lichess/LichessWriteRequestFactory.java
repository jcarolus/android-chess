package jwtc.android.chess.lichess;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;

import okhttp3.FormBody;
import okhttp3.Request;

/** Builds the form-encoded Lichess write requests whose routes differ from the JSON API. */
final class LichessWriteRequestFactory {
    private LichessWriteRequestFactory() {
    }

    static Request teamJoin(String host, String accessToken, String teamId, Map<String, Object> fields) {
        return formPost(host, "/team/" + teamId + "/join", accessToken, fields);
    }

    static Request teamQuit(String host, String accessToken, String teamId) {
        return formPost(host, "/team/" + teamId + "/quit", accessToken, null);
    }

    static Request swissJoin(String host, String accessToken, String swissId, Map<String, Object> fields) {
        return formPost(host, "/api/swiss/" + swissId + "/join", accessToken, fields);
    }

    static Request swissWithdraw(String host, String accessToken, String swissId) {
        return formPost(host, "/api/swiss/" + swissId + "/withdraw", accessToken, null);
    }

    private static Request formPost(
        String host,
        String path,
        String accessToken,
        Map<String, Object> fields
    ) {
        FormBody.Builder body = new FormBody.Builder();
        if (fields != null) {
            List<String> names = new ArrayList<>(fields.keySet());
            Collections.sort(names);
            for (String name : names) {
                Object value = fields.get(name);
                if (value != null) {
                    body.add(name, String.valueOf(value));
                }
            }
        }

        return new Request.Builder()
            .url(host + path)
            .addHeader("Accept", "*/*")
            .addHeader("Authorization", "Bearer " + accessToken)
            .post(body.build())
            .build();
    }
}
