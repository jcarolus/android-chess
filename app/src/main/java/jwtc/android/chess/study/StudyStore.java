package jwtc.android.chess.study;

import android.content.ContentResolver;
import android.database.Cursor;
import java.util.ArrayList;
import java.util.List;
import jwtc.android.chess.helpers.MyPGNProvider;
import jwtc.chess.GameFilter;
import jwtc.chess.PGNColumns;

/** Looks up studies in the Game Database using the same selection for navigation and deletion. */
public final class StudyStore {
    private final ContentResolver resolver;

    public StudyStore(ContentResolver resolver) {
        this.resolver = resolver;
    }

    public List<Long> ids(GameFilter filter) {
        List<Long> ids = new ArrayList<>();
        try (Cursor rows =
                resolver.query(
                        MyPGNProvider.CONTENT_URI,
                        new String[] {PGNColumns._ID},
                        filter.selection(),
                        filter.selectionArgs(),
                        filter.orderBy() + ", " + PGNColumns._ID + " ASC")) {
            while (rows != null && rows.moveToNext()) {
                ids.add(rows.getLong(0));
            }
        }
        return ids;
    }

    public int deleteMatching(GameFilter filter, int expectedCount) {
        String match = filter.selection() == null ? "1=1" : filter.selection();
        String[] args = filter.selectionArgs();
        List<String> values = new ArrayList<>();
        if (args != null) {
            java.util.Collections.addAll(values, args);
            java.util.Collections.addAll(values, args);
        }
        values.add(Integer.toString(expectedCount));
        return resolver.delete(
                MyPGNProvider.CONTENT_URI,
                "("
                        + match
                        + ") AND (SELECT COUNT(*) FROM games WHERE "
                        + match
                        + ") = CAST(? AS INTEGER)",
                values.toArray(new String[0]));
    }
}
