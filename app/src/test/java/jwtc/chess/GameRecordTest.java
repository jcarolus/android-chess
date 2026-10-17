package jwtc.chess;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
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
        GameRecord record = GameRecord.stored(7, "pgn", "Wijk", "A", "B", date(2023, 5, 17), "1-0", 3f);
        Map<String, String> overrides = record.tagOverrides();
        assertEquals(Arrays.asList("Event", "White", "Black", "Date"), Arrays.asList(overrides.keySet().toArray()));
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
}
