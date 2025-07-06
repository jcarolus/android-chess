package jwtc.android.chess.hotspotboard;

import org.json.JSONException;
import org.json.JSONObject;

public class GameMessage {
    public static final int TYPE_MOVE = 0;
    public static final int TYPE_RESIGN = 1;
    public static final int TYPE_DRAW_OFFER = 2;
    public static final int TYPE_DRAW_ACCEPT = 3;
    public static final int TYPE_DRAW_DECLINE = 4;
    public static final int TYPE_NEW_GAME = 5;

    public int type;
    public String FEN;
    public String playerName;

    public GameMessage(int type, String FEN, String playerName) {
        this.type = type;
        this.FEN = FEN;
        this.playerName = playerName;
    }

    public GameMessage(String FEN, String playerName) {
        this(TYPE_MOVE, FEN, playerName);
    }

    public static GameMessage fromJson(String jsonString) throws JSONException {
        JSONObject json = new JSONObject(jsonString);
        int type = json.optInt("type", TYPE_MOVE);
        String FEN = json.getString("FEN");
        String playerName = json.optString("playerName", "");
        return new GameMessage(type, FEN, playerName);
    }

    public String toJsonString() throws JSONException {
        JSONObject json = new JSONObject();
        json.put("type", type);
        json.put("FEN", FEN);
        json.put("playerName", playerName);

        return json.toString();
    }
}
