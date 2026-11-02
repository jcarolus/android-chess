package jwtc.chess;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;

import java.util.Arrays;
import java.util.Calendar;
import java.util.Date;
import java.util.HashMap;
import java.util.Map;
import org.junit.Test;

public class GameRecordTest {
    private static Date date(int year, int month, int day) {
        Calendar calendar = Calendar.getInstance();
        calendar.clear();
        calendar.set(year, month - 1, day);
        return calendar.getTime();
    }

    private static Map<String, String> tags() {
        Map<String, String> tags = new HashMap<>();
        tags.put("Event", "Wijk");
        tags.put("White", "A");
        tags.put("Black", "B");
        tags.put("Date", "2023.05.17");
        tags.put("Result", "1-0");
        return tags;
    }

    @Test
    public void ofTakesTheColumnsFromTheTags() {
        GameRecord record = GameRecord.of("1. e4 1-0", tags());
        assertEquals(0, record.id);
        assertEquals("1. e4 1-0", record.pgn);
        assertEquals("Wijk", record.event);
        assertEquals("A", record.white);
        assertEquals("B", record.black);
        assertEquals(date(2023, 5, 17), record.date);
        assertEquals("1-0", record.result);
        assertNull(record.rating);
    }

    @Test
    public void ofKeepsMissingAndUnknownValuesNull() {
        Map<String, String> tags = new HashMap<>();
        tags.put("Date", "????.??.??");
        GameRecord record = GameRecord.of("", tags);
        assertNull(record.event);
        assertNull(record.white);
        assertNull(record.date);
        assertNull(record.result);
    }

    @Test
    public void withRatingKeepsTheRest() {
        GameRecord record = GameRecord.of("pgn", tags()).withRating(4.5f);
        assertEquals(Float.valueOf(4.5f), record.rating);
        assertEquals("pgn", record.pgn);
        assertEquals("Wijk", record.event);
    }

    @Test
    public void tagOverridesCarryTheColumnsInOrder() {
        GameRecord record =
                GameRecord.stored(7, "pgn", "Wijk", "A", "B", date(2023, 5, 17), "1-0", 3f);
        Map<String, String> overrides = record.tagOverrides();
        assertEquals(
                Arrays.asList("Event", "White", "Black", "Date"),
                Arrays.asList(overrides.keySet().toArray()));
        assertEquals("2023.05.17", overrides.get("Date"));
        assertEquals("A", overrides.get("White"));
    }

    @Test
    public void tagOverridesLeaveOutWhatIsUnknown() {
        GameRecord record = GameRecord.stored(7, "pgn", null, "A", null, null, null, null);
        Map<String, String> overrides = record.tagOverrides();
        assertEquals(1, overrides.size());
        assertTrue(overrides.containsKey("White"));
    }

    @Test
    public void aStoredGameKeepsItsId() {
        assertEquals(42, GameRecord.stored(42, "pgn", "e", "w", "b", null, "*", null).id);
    }

    @Test
    public void fromPgnPreservesTheOriginalTextAndExtractsColumns() {
        String pgn =
                "[Event \"Wijk\"]\r\n[White \"A\"]\r\n[Black \"B\"]\r\n[Date \"2023.05.17\"]\r\n\r\n{root} 1. e4!? $14 {main} e5 (1... c5 2. Nf3) 1-0\r\n";
        GameRecord record = GameRecord.fromPgn(pgn);
        assertEquals(pgn, record.pgn);
        assertEquals("Wijk", record.event);
        assertEquals("A", record.white);
        assertEquals("B", record.black);
        assertEquals(date(2023, 5, 17), record.date);
        assertEquals("1-0", record.result);
        assertNull(record.rating);
    }

    @Test
    public void fromPgnAcceptsSyntacticallyValidIllegalMovesAndStartingPositions() {
        for (String pgn :
                new String[] {
                    "1. e4 e5 2. Ke4 *", "1. e4 e5 (1... c5 2. Ke4) *", "[FEN \"invalid\"]\n1. e4 *"
                }) {
            assertEquals(pgn, GameRecord.fromPgn(pgn).pgn);
        }
    }

    @Test
    public void fromPgnUsesTheHeaderResultWhenMovetextHasNone() {
        assertEquals("1/2-1/2", GameRecord.fromPgn("[Result \"1/2-1/2\"]\n1. e4 e5").result);
    }

    @Test
    public void fromPgnDefaultsAnUnknownResultWithoutInferringItFromMoves() {
        assertEquals("*", GameRecord.fromPgn("1. f3 e5 2. g4 Qh4#").result);
        assertEquals("*", GameRecord.fromPgn("[Event \"Empty\"]").result);
        assertNull(GameRecord.fromPgn("[Date \"????.??.??\"]\n*").date);
    }

    @Test
    public void fromPgnRejectsMalformedSyntaxAndConflictingResults() {
        for (String pgn :
                new String[] {
                    "[Event \"broken]",
                    "1. e4 (1. d4 *",
                    "1. e4 {unclosed",
                    "[Result \"1-0\"]\n1. e4 0-1",
                    "1. not-a-move *"
                }) {
            assertThrows(PgnSyntaxError.class, () -> GameRecord.fromPgn(pgn));
        }
    }

    @Test
    public void fromPgnRejectsMissingOrOversizedGames() {
        assertThrows(PgnSyntaxError.class, () -> GameRecord.fromPgn(null));
        char[] chars = new char[Pgn.MAX_GAME_SIZE + 1];
        Arrays.fill(chars, ' ');
        assertThrows(PgnSyntaxError.class, () -> GameRecord.fromPgn(new String(chars)));
        assertEquals(
                Pgn.MAX_GAME_SIZE,
                GameRecord.fromPgn(new String(chars, 0, Pgn.MAX_GAME_SIZE)).pgn.length());
    }
}
