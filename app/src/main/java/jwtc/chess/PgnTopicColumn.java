package jwtc.chess;

import android.content.ContentValues;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;

final class PgnTopicColumn {
    private PgnTopicColumn() {}

    static void set(ContentValues values, String pgnColumn) {
        for (String column : values.keySet()) {
            if (pgnColumn.equalsIgnoreCase(column)) {
                values.put(PGNColumns.TOPIC, topic(values.getAsString(column)));
                return;
            }
        }
    }

    static void backfill(SQLiteDatabase db, String table, String idColumn, String pgnColumn) {
        String candidate =
                "instr("
                        + pgnColumn
                        + ", '[Topic') > 0 AND length(CAST("
                        + pgnColumn
                        + " AS BLOB)) <= "
                        + (4 * Pgn.MAX_GAME_SIZE);
        ContentValues values = new ContentValues();
        try (Cursor rows =
                db.query(
                        table,
                        new String[] {idColumn, pgnColumn},
                        candidate,
                        null,
                        null,
                        null,
                        idColumn)) {
            while (rows.moveToNext()) {
                String topic = topic(rows.getString(1));
                if (topic != null) {
                    values.put(PGNColumns.TOPIC, topic);
                    db.update(table, values, idColumn + " = ?", new String[] {rows.getString(0)});
                }
            }
        }
    }

    private static String topic(String pgn) {
        return pgn == null || !pgn.contains("[Topic")
                ? null
                : Pgn.optionalTagValue(Pgn.readHeaderTags(pgn).get("Topic"));
    }
}
