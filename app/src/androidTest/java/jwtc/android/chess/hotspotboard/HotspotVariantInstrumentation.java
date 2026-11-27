package jwtc.android.chess.hotspotboard;

import java.util.EnumSet;
import jwtc.android.chess.services.GameApi;
import jwtc.chess.Pos;
import jwtc.chess.Variant;
import jwtc.chess.board.BoardConstants;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

public final class HotspotVariantInstrumentation {
    private HotspotVariantInstrumentation() {}

    public static void verify() throws Exception {
        JSONObject hello = new JSONObject(GameMessage.supportedVariantsMessage());
        check(!hello.has("FEN"), "A legacy receiver could load a capability message as a position");
        JSONArray supported = hello.getJSONArray("supportedVariants");
        supported.put("FUTURE_VARIANT");
        check(
                GameMessage.supportedVariantsFromJson(supported)
                        .equals(EnumSet.allOf(Variant.class)),
                "Capabilities lost a supported variant");

        GameApi host = new GameApi();
        HotspotBoardApi guest = new HotspotBoardApi();
        guest.setMyName("Guest");

        host.newGame(Variant.KING_OF_THE_HILL);
        sync(host, guest, 0);
        for (String san : new String[] {"Nf3", "Nf6", "Ng1", "Ng8", "Nf3", "Nf6", "Ng1", "Ng8"}) {
            check(host.requestMove(san), "Could not apply " + san);
            sync(host, guest, host.getMyMove());
        }
        check(guest.getState() == BoardConstants.DRAW_REPEAT, "Guest lost repetition history");

        host.newGame(Variant.DUCK);
        sync(host, guest, 0);
        check(
                host.requestMove(Pos.fromString("e2"), Pos.fromString("e4")),
                "Duck piece move failed");
        check(host.hasPendingDuckMove(), "Duck placement did not become pending");
        check(host.requestDuckMove(Pos.fromString("d5")), "Duck placement failed");
        sync(host, guest, host.getMyMove());
        check(guest.getDuckPos() == Pos.fromString("d5"), "Guest lost duck placement");
        check(!guest.hasPendingDuckMove(), "Guest received a partial Duck turn");

        guest.onGameUpdate(
                new GameMessage(
                        GameMessage.TYPE_MOVE,
                        "4k3/8/8/8/8/8/8/R3K3 w - - 0 1",
                        "Host",
                        "Guest",
                        0,
                        "",
                        Variant.CHESS_960));
        check(
                guest.variant() == Variant.CHESS_960,
                "Chess 960 without castling rights lost its variant");

        host.newGame();
        GameMessage legacy =
                new GameMessage(GameMessage.TYPE_MOVE, host.getFEN(), "Host", "Guest", 0, "");
        String packet = legacy.toJsonString();
        check(!new JSONObject(packet).has("variant"), "Standard update changed the legacy packet");
        guest.onGameUpdate(GameMessage.fromJson(packet));
        check(guest.isStandardVariant(), "Legacy update did not load as Standard");
    }

    private static void sync(GameApi host, HotspotBoardApi guest, int move) throws JSONException {
        GameMessage update =
                new GameMessage(
                        GameMessage.TYPE_MOVE,
                        host.getFEN(),
                        "Host",
                        "Guest",
                        move,
                        "",
                        host.variant());
        guest.onGameUpdate(GameMessage.fromJson(update.toJsonString()));
        check(guest.variant() == host.variant(), "Guest lost the selected variant");
        check(guest.getFEN().equals(host.getFEN()), "Guest position differs from the host");
    }

    private static void check(boolean condition, String message) {
        if (!condition) {
            throw new AssertionError(message);
        }
    }
}
