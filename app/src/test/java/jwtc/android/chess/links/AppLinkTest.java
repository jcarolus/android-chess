package jwtc.android.chess.links;

import static org.junit.Assert.*;

import org.junit.Test;

public class AppLinkTest {
    @Test
    public void roundTripsPgnTagsUnicodeCommentsAndVariationSelection() {
        String pgn = "[Variant \"Duck\"]\n[White \"Jérôme\"]\n\n1. e4@d5 {A & B + # / ? %} *";
        String url = AppLink.game(pgn, new int[] {0, 1, 0});
        AppLink parsed = AppLink.parse(url);
        assertEquals("game", parsed.route);
        assertEquals(pgn, parsed.content);
        assertArrayEquals(new int[] {0, 1, 0}, parsed.path);
        assertNull(java.net.URI.create(url).getRawQuery());
    }

    @Test
    public void roundTripsPositionIncludingDuckMarker() {
        String fen = "rnbqkbnr/pppppppp/8/3@4/4P3/8/PPPP1PPP/RNBQKBNR b KQkq - 0 1";
        assertEquals(fen, AppLink.parse(AppLink.position(fen)).content);
    }

    @Test
    public void supportsRootSelectionHomeInvitationsAndUserIds() {
        assertArrayEquals(new int[0], AppLink.parse(AppLink.game("1. e4 *", new int[0])).path);
        assertEquals("home", AppLink.parse(AppLink.HOME).route);
        assertEquals("home", AppLink.parse("https://jwtc.nl/app").route);
        assertEquals("192.168.43.1", AppLink.parse(AppLink.join("192.168.43.1")).content);
        assertEquals("Chess_User-1", AppLink.parse(AppLink.online("Chess_User-1")).content);
    }

    @Test
    public void rejectsForeignOriginsUnknownRoutesAndParameters() {
        String valid = AppLink.online("alice");
        for (String url :
                new String[] {
                    valid.replace("https:", "http:"),
                    valid.replace("jwtc.nl", "evil.example"),
                    valid.replace("jwtc.nl", "jwtc.nl.evil.example"),
                    valid.replace("jwtc.nl", "alice@jwtc.nl"),
                    valid.replace("jwtc.nl", "jwtc.nl:443"),
                    valid.replace("online", "import"),
                    valid.replace("#", "?"),
                    valid.replace("v=1", "v=2"),
                    valid + "&user=bob",
                    valid + "&extra=1",
                    "https://jwtc.nl/app/game/#v=1&pgn=x&path=-1",
                    "https://jwtc.nl/app/game/#v=1&pgn=x&path=99999999999999999999",
                    "https://jwtc.nl/app/game/#v=1&pgn=x",
                    "https://jwtc.nl/app/online/#v=1&user=%FF",
                    "https://jwtc.nl/app/online/#v=1&user=%ZZ",
                    "https://jwtc.nl/app/online/#v=1&user=al%00ice",
                    "https://jwtc.nl/app/online/#v=1&user=",
                    "https://jwtc.nl/app/#v=1",
                    "https://jwtc.nl/app/online/"
                }) {
            assertThrows(url, IllegalArgumentException.class, () -> AppLink.parse(url));
        }
    }

    @Test
    public void rejectsInvalidHostsAndUserIds() {
        for (String host :
                new String[] {
                    "192.168.0.256",
                    "192.168.00.1",
                    "localhost",
                    "192.168.1",
                    "1.2.3.4:80",
                    "1.2.3.-1"
                }) {
            assertThrows(IllegalArgumentException.class, () -> AppLink.join(host));
        }
        assertThrows(IllegalArgumentException.class, () -> AppLink.online("alice/../../x"));
        assertThrows(IllegalArgumentException.class, () -> AppLink.online("a\nb"));
    }

    @Test
    public void enforcesLimitOnEncodedUrlRatherThanRawText() {
        String prefix = "https://jwtc.nl/app/game/#v=1&pgn=";
        String suffix = "&path=";
        StringBuilder pgn = new StringBuilder();
        for (int i = prefix.length() + suffix.length(); i < AppLink.MAX_LENGTH; i++) {
            pgn.append('a');
        }
        assertEquals(AppLink.MAX_LENGTH, AppLink.game(pgn.toString(), new int[0]).length());
        assertThrows(IllegalArgumentException.class, () -> AppLink.game(pgn + "a", new int[0]));
        assertThrows(IllegalArgumentException.class, () -> AppLink.game(pgn + "é", new int[0]));
        assertThrows(IllegalArgumentException.class, () -> AppLink.parse(null));
    }
}
