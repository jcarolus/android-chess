package jwtc.android.chess.hotspotboard;

import org.json.JSONException;
import org.json.JSONObject;

public class GameMessage {
    public String FEN;
    public String white;
    public String black;

    public GameMessage(String FEN, String white, String black) {
        this.FEN = FEN;
        this.white = white;
        this.black = black;
    }

    public static GameMessage fromJson(String jsonString) throws JSONException {
        JSONObject json = new JSONObject(jsonString);
        String FEN = json.getString("FEN");
        String white = json.getString("white");
        String black = json.getString("black");
        return new GameMessage(FEN, white, black);
    }

    public String toJsonString() throws JSONException {
        JSONObject json = new JSONObject();
        json.put("FEN", FEN);
        json.put("white", white);
        json.put("black", black);

        return json.toString();
    }
}
