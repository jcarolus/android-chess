package jwtc.android.chess.hotspotboard;

import java.util.EnumSet;
import jwtc.android.chess.engine.RustSearchRequest.Variant;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

public class GameMessage {
    public static final int TYPE_MOVE = 0;
    public static final int TYPE_RESIGN = 1;
    public static final int TYPE_DRAW_OFFER = 2;
    public static final int TYPE_DRAW_ACCEPT = 3;
    public static final int TYPE_DRAW_DECLINE = 4;
    public static final int TYPE_SHARE_SNAPSHOT = 5;

    private static final EnumSet<Variant> SUPPORTED_VARIANTS =
            EnumSet.of(Variant.STANDARD, Variant.CHESS_960, Variant.DUCK, Variant.KING_OF_THE_HILL);

    public int type;
    public String FEN;
    public String white;
    public String black;
    public int lastMove;
    public String lastMovePgn;
    public final Variant variant;

    public GameMessage(
            int type,
            String FEN,
            String white,
            String black,
            int lastMove,
            String lastMovePgn,
            Variant variant) {
        this.type = type;
        this.FEN = FEN;
        this.white = white;
        this.black = black;
        this.lastMove = lastMove;
        this.lastMovePgn = lastMovePgn;
        this.variant = variant;
    }

    public GameMessage(
            int type, String FEN, String white, String black, int lastMove, String lastMovePgn) {
        this(type, FEN, white, black, lastMove, lastMovePgn, null);
    }

    public static GameMessage fromJson(String jsonString) throws JSONException {
        return fromJson(new JSONObject(jsonString));
    }

    static GameMessage fromJson(JSONObject json) throws JSONException {
        int type = json.optInt("type", TYPE_MOVE);
        String FEN = json.getString("FEN");
        String white = json.getString("white");
        String black = json.getString("black");
        int lastMove = json.getInt("lastMove");
        String lastMovePgn = json.getString("lastMovePgn");
        Variant variant = null;
        if (json.has("variant")) {
            try {
                variant = Variant.valueOf(json.getString("variant"));
            } catch (IllegalArgumentException e) {
                throw new JSONException("Unknown Hotspot game variant");
            }
        }
        return new GameMessage(type, FEN, white, black, lastMove, lastMovePgn, variant);
    }

    public String toJsonString() throws JSONException {
        JSONObject json = new JSONObject();
        json.put("type", type);
        json.put("FEN", FEN);
        json.put("white", white);
        json.put("black", black);
        json.put("lastMove", lastMove);
        json.put("lastMovePgn", lastMovePgn);
        if (variant != null) {
            json.put("variant", variant.name());
        }

        return json.toString();
    }

    static String supportedVariantsMessage() throws JSONException {
        JSONArray variants = new JSONArray();
        for (Variant variant : SUPPORTED_VARIANTS) {
            variants.put(variant.name());
        }
        // Legacy receivers require the FEN field and discard this message before updating the
        // board.
        return new JSONObject().put("supportedVariants", variants).toString();
    }

    static EnumSet<Variant> supportedVariantsFromJson(JSONArray names) {
        EnumSet<Variant> variants = EnumSet.of(Variant.STANDARD);
        for (int i = 0; i < names.length(); i++) {
            try {
                variants.add(Variant.valueOf(names.optString(i)));
            } catch (IllegalArgumentException ignored) {
                // A newer peer can advertise variants this app does not know.
            }
        }
        variants.retainAll(SUPPORTED_VARIANTS);
        return variants;
    }
}
