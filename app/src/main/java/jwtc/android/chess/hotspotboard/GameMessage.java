package jwtc.android.chess.hotspotboard;

import org.json.JSONException;
import org.json.JSONObject;

public class GameMessage {
    public static final int TYPE_MOVE = 0;
    public static final int TYPE_RESIGN = 1;
    public static final int TYPE_DRAW_OFFER = 2;
    public static final int TYPE_DRAW_ACCEPT = 3;
    public static final int TYPE_DRAW_DECLINE = 4;

    public int type;
    public String FEN;
    public String white;
    public String black;


    public GameMessage(int type, String FEN, String white, String black) {
        this.type = type;
        this.FEN = FEN;
        this.white = white;
        this.black = black;
    }

    public GameMessage(String FEN, String white, String black) {
        this(TYPE_MOVE, FEN, white, black);
    }

    public static GameMessage fromJson(String jsonString) throws JSONException {
        JSONObject json = new JSONObject(jsonString);
        int type = json.optInt("type", TYPE_MOVE);
        String FEN = json.getString("FEN");
        String white = json.getString("white");
        String black = json.getString("black");
        return new GameMessage(type, FEN, white, black);
    }

    public String toJsonString() throws JSONException {
        JSONObject json = new JSONObject();
        json.put("type", type);
        json.put("FEN", FEN);
        json.put("white", white);
        json.put("black", black);

        return json.toString();
    }
}
