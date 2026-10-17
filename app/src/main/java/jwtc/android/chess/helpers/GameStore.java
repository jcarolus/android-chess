package jwtc.android.chess.helpers;

import android.content.ContentResolver;
import android.content.ContentUris;
import android.content.ContentValues;
import android.database.Cursor;
import android.net.Uri;
import android.util.Log;

import java.util.Date;

import jwtc.chess.GameFilter;
import jwtc.chess.GameRecord;
import jwtc.chess.PGNColumns;

/** The Game Database: saved Games in the PGN content provider. */
public final class GameStore {
    private static final String TAG = "GameStore";

    private final ContentResolver resolver;

    public GameStore(ContentResolver resolver) {
        this.resolver = resolver;
    }

    /**
     * Updates the Game with this id, or inserts a new one when the id is not set or a copy is wanted.
     *
     * @return the id of the stored Game, or the id given when the insert returned none
     */
    public long save(GameRecord record, boolean asCopy, long id) {
        if (id > 0 && !asCopy) {
            update(id, record);
            return id;
        }
        long inserted = insert(record);
        return inserted > 0 ? inserted : id;
    }

    /** @return the id of the new Game, or -1 */
    public long insert(GameRecord record) {
        Uri uri = resolver.insert(MyPGNProvider.CONTENT_URI, toValues(record));
        return uri == null ? -1 : ContentUris.parseId(uri);
    }

    /** Writes the columns the record has; a record without a rating keeps the stored rating. */
    public boolean update(long id, GameRecord record) {
        return resolver.update(uriOf(id), toValues(record), null, null) > 0;
    }

    /** @return the Game, or null when there is none with this id */
    public GameRecord load(long id) {
        try (Cursor cursor = resolver.query(uriOf(id), PGNColumns.COLUMNS, null, null, null)) {
            if (cursor != null && cursor.getCount() == 1 && cursor.moveToFirst()) {
                return fromCursor(cursor);
            }
        } catch (Exception e) {
            Log.d(TAG, "Caught exception loading game " + id + " " + e.getMessage());
        }
        return null;
    }

    public boolean delete(long id) {
        return resolver.delete(uriOf(id), null, null) > 0;
    }

    public int deleteAll() {
        return resolver.delete(MyPGNProvider.CONTENT_URI, "1=1", null);
    }

    /** The Games that match, as a cursor over {@link PGNColumns#COLUMNS} that the caller closes. */
    public Cursor query(GameFilter filter) {
        return resolver.query(MyPGNProvider.CONTENT_URI, PGNColumns.COLUMNS, filter.selection(), filter.selectionArgs(),
            filter.orderBy());
    }

    /** All Games, newest first, each PGN followed by blank lines. */
    public String exportAllAsPgn() {
        StringBuilder out = new StringBuilder();
        try (Cursor cursor = resolver.query(MyPGNProvider.CONTENT_URI, PGNColumns.COLUMNS, null, null,
            PGNColumns.DEFAULT_SORT_ORDER)) {
            while (cursor != null && cursor.moveToNext()) {
                out.append(Utils.getColumnString(cursor, PGNColumns.PGN)).append("\n\n\n");
            }
        }
        return out.toString();
    }

    /** The Game the cursor is on. */
    public static GameRecord fromCursor(Cursor cursor) {
        int dateIndex = cursor.getColumnIndex(PGNColumns.DATE);
        Date date = dateIndex < 0 || cursor.isNull(dateIndex) ? null : new Date(cursor.getLong(dateIndex));
        int ratingIndex = cursor.getColumnIndex(PGNColumns.RATING);
        Float rating = ratingIndex < 0 || cursor.isNull(ratingIndex) ? null : cursor.getFloat(ratingIndex);
        return GameRecord.stored(
            Utils.getColumnLong(cursor, PGNColumns._ID),
            Utils.getColumnString(cursor, PGNColumns.PGN),
            Utils.getColumnString(cursor, PGNColumns.EVENT),
            Utils.getColumnString(cursor, PGNColumns.WHITE),
            Utils.getColumnString(cursor, PGNColumns.BLACK),
            date,
            Utils.getColumnString(cursor, PGNColumns.RESULT),
            rating);
    }

    private static Uri uriOf(long id) {
        return ContentUris.withAppendedId(MyPGNProvider.CONTENT_URI, id);
    }

    private static ContentValues toValues(GameRecord record) {
        ContentValues values = new ContentValues();
        values.put(PGNColumns.EVENT, record.event);
        values.put(PGNColumns.WHITE, record.white);
        values.put(PGNColumns.BLACK, record.black);
        values.put(PGNColumns.PGN, record.pgn);
        values.put(PGNColumns.RESULT, record.result);
        if (record.date != null) values.put(PGNColumns.DATE, record.date.getTime());
        if (record.rating != null) values.put(PGNColumns.RATING, record.rating);
        return values;
    }
}
