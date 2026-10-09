package jwtc.chess;

import java.util.Date;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * A Game as the Game Database stores it: the PGN plus the columns that are searched and listed. Fields that are
 * unknown are null; a null rating is left as it is on update.
 */
public final class GameRecord {
    /** The database id, or 0 for a Game that is not stored yet. */
    public final long id;
    public final String pgn;
    public final String event;
    public final String white;
    public final String black;
    /** Null when the date is unknown. */
    public final Date date;
    public final String result;
    public final Float rating;

    private GameRecord(long id, String pgn, String event, String white, String black, Date date, String result,
                       Float rating) {
        this.id = id;
        this.pgn = pgn;
        this.event = event;
        this.white = white;
        this.black = black;
        this.date = date;
        this.result = result;
        this.rating = rating;
    }

    /** A Game to store, taking its columns from the tags of the PGN. Call after the PGN is exported, so its tags are final. */
    public static GameRecord of(String pgn, Map<String, String> tags) {
        return new GameRecord(0, pgn, tags.get("Event"), tags.get("White"), tags.get("Black"),
            PgnDate.parse(tags.get("Date")), tags.get("Result"), null);
    }

    /** A Game as read from the database. */
    public static GameRecord stored(long id, String pgn, String event, String white, String black, Date date,
                                    String result, Float rating) {
        return new GameRecord(id, pgn, event, white, black, date, result, rating);
    }

    public GameRecord withRating(float rating) {
        return new GameRecord(id, pgn, event, white, black, date, result, rating);
    }

    /** The tags whose column values win over what the stored PGN says, because the columns are what the user edits. */
    public Map<String, String> tagOverrides() {
        Map<String, String> tags = new LinkedHashMap<>();
        if (event != null) tags.put("Event", event);
        if (white != null) tags.put("White", white);
        if (black != null) tags.put("Black", black);
        if (date != null) tags.put("Date", PgnDate.format(date));
        return tags;
    }
}
