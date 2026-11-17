package jwtc.android.chess.study;

import android.content.ContentResolver;
import android.database.Cursor;
import android.net.Uri;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import jwtc.android.chess.helpers.MyPGNProvider;
import jwtc.chess.PGNColumns;
import jwtc.chess.Pgn;

/** Looks up studies in the Game Database using the same selection for navigation and deletion. */
public final class StudyStore {
    private static final String MATCH = PGNColumns.TOPIC + " = ? AND " + PGNColumns.EVENT + " = ?";
    private final ContentResolver resolver;

    public StudyStore(ContentResolver resolver) {
        this.resolver = resolver;
    }

    Map<String, Map<String, Integer>> selections() {
        Map<String, Map<String, Integer>> selections = new LinkedHashMap<>();
        try (Cursor rows =
                resolver.query(
                        MyPGNProvider.CONTENT_URI,
                        new String[] {PGNColumns.TOPIC, PGNColumns.EVENT},
                        PGNColumns.TOPIC + " IS NOT NULL AND " + PGNColumns.EVENT + " IS NOT NULL",
                        null,
                        PGNColumns.TOPIC
                                + " COLLATE NOCASE, "
                                + PGNColumns.EVENT
                                + " COLLATE NOCASE")) {
            while (rows != null && rows.moveToNext()) {
                String event = rows.getString(1);
                if (Pgn.optionalTagValue(event) != null) {
                    selections
                            .computeIfAbsent(rows.getString(0), topic -> new LinkedHashMap<>())
                            .merge(event, 1, Integer::sum);
                }
            }
        }
        return selections;
    }

    public List<Long> ids(String topic, String event) {
        List<Long> ids = new ArrayList<>();
        try (Cursor rows =
                resolver.query(
                        MyPGNProvider.CONTENT_URI,
                        new String[] {PGNColumns._ID},
                        MATCH,
                        new String[] {topic, event},
                        PGNColumns._ID + " ASC")) {
            while (rows != null && rows.moveToNext()) {
                ids.add(rows.getLong(0));
            }
        }
        return ids;
    }

    public int deleteMatching(String topic, String event, int expectedCount) {
        String unchangedCount =
                " AND (SELECT COUNT(*) FROM games WHERE " + MATCH + ") = CAST(? AS INTEGER)";
        return resolver.delete(
                MyPGNProvider.CONTENT_URI,
                MATCH + unchangedCount,
                new String[] {topic, event, topic, event, Integer.toString(expectedCount)});
    }

    public static String resumeKey(String topic, String event) {
        return "studyLastGame/" + Uri.encode(topic) + "/" + Uri.encode(event);
    }
}
