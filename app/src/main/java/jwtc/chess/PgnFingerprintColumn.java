package jwtc.chess;

import android.content.ContentValues;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;

/** Maintains the fingerprint derived from a table's stored PGN. */
final class PgnFingerprintColumn {
    private PgnFingerprintColumn() {}

    static void set(ContentValues values, String pgnColumn) {
        for (String column : values.keySet()) {
            if (pgnColumn.equalsIgnoreCase(column)) {
                values.put(PGNColumns.FINGERPRINT, PgnFingerprint.of(values.getAsString(column)));
                return;
            }
        }
    }

    static void backfill(SQLiteDatabase db, String table, String idColumn, String pgnColumn) {
        ContentValues values = new ContentValues();
        // Oversized legacy PGNs cannot be parsed and may not fit in a CursorWindow.
        String boundedPgn =
                "CASE WHEN length(CAST("
                        + pgnColumn
                        + " AS BLOB)) <= "
                        + (4 * Pgn.MAX_GAME_SIZE)
                        + " THEN "
                        + pgnColumn
                        + " END";
        try (Cursor rows =
                db.query(
                        table,
                        new String[] {idColumn, boundedPgn},
                        null,
                        null,
                        null,
                        null,
                        idColumn)) {
            while (rows.moveToNext()) {
                String fingerprint = PgnFingerprint.of(rows.getString(1));
                if (fingerprint != null) {
                    values.put(PGNColumns.FINGERPRINT, fingerprint);
                    db.update(table, values, idColumn + " = ?", new String[] {rows.getString(0)});
                }
            }
        }
    }
}
