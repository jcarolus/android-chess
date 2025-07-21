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
    public int lastMove;

    public GameMessage(int type, String FEN, String white, String black, int lastMove) {
        this.type = type;
        this.FEN = FEN;
        this.white = white;
        this.black = black;
        this.lastMove = lastMove;
    }

    public GameMessage(String FEN, String white, String black, int lastMove) {
        this(TYPE_MOVE, FEN, white, black, lastMove);
    }

    public static GameMessage fromJson(String jsonString) throws JSONException {
        JSONObject json = new JSONObject(jsonString);
        int type = json.optInt("type", TYPE_MOVE);
        String FEN = json.getString("FEN");
        String white = json.getString("white");
        String black = json.getString("black");
        int lastMove = json.getInt("lastMove");
        return new GameMessage(type, FEN, white, black, lastMove);
    }

    public String toJsonString() throws JSONException {
        JSONObject json = new JSONObject();
        json.put("type", type);
        json.put("FEN", FEN);
        json.put("white", white);
        json.put("black", black);
        json.put("lastMove", lastMove);

        return json.toString();
    }
}
