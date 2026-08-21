package jwtc.android.chess.lichess;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;

/** Extracts readable text from both primitive and structured Lichess error responses. */
final class LichessJsonError {
    private LichessJsonError() {
    }

    static String message(JsonObject response) {
        if (response == null) {
            return "";
        }
        if (response.has("error")) {
            return value(response.get("error"));
        }
        if (response.has("message")) {
            return value(response.get("message"));
        }
        return response.toString();
    }

    private static String value(JsonElement element) {
        if (element == null || element.isJsonNull()) {
            return "";
        }
        if (element.isJsonPrimitive()) {
            return element.getAsString();
        }
        if (element.isJsonObject()) {
            JsonObject object = element.getAsJsonObject();
            if (object.has("message")) {
                return value(object.get("message"));
            }
            if (object.has("error")) {
                return value(object.get("error"));
            }
        }
        return element.toString();
    }
}
