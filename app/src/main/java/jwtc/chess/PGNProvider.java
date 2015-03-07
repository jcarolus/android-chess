package jwtc.chess;

import java.util.Calendar;
import java.util.HashMap;

import android.content.ContentProvider;
import android.content.ContentUris;
import android.content.ContentValues;
import android.content.Context;
import android.content.UriMatcher;
import android.content.res.Resources;
import android.database.Cursor;
import android.database.SQLException;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;
import android.database.sqlite.SQLiteQueryBuilder;
import android.net.Uri;
import android.text.TextUtils;
import android.util.Log;

public class PGNProvider extends ContentProvider {

	public static String AUTHORITY = "jwtc.chess";
    public static Uri CONTENT_URI = Uri.parse("content://"  + AUTHORITY + "/games");
	
    private static final String TAG = "PGNProvider";

    private static final String DATABASE_NAME = "chess_pgn.db";
    private static final int DATABASE_VERSION = 3; // DO NOT EDIT {3}
    private static final String GAMES_TABLE_NAME = "games";

    private static HashMap<String, String> sGamesProjectionMap;

    protected static final int GAMES = 1;
    protected static final int GAMES_ID = 2;

    protected static UriMatcher sUriMatcher;

    /**
     * This class helps open, create, and upgrade the database file.
     */
    private static class DatabaseHelper extends SQLiteOpenHelper {

        DatabaseHelper(Context context) {
            super(context, DATABASE_NAME, null, DATABASE_VERSION);
        }

        public void onCreate(SQLiteDatabase db) {
            db.execSQL("CREATE TABLE " + GAMES_TABLE_NAME + " ("
                    + PGNColumns._ID + " INTEGER PRIMARY KEY,"
                    + PGNColumns.WHITE + " TEXT,"
                    + PGNColumns.BLACK + " TEXT,"
                    + PGNColumns.PGN + " TEXT,"
                    + PGNColumns.DATE + " INTEGER,"
                    + PGNColumns.RATING + " REAL,"
                    + PGNColumns.EVENT + " TEXT"
                    + ");");
            
            String sPGN = "1. Nf3 Nf6 2. c4 g6 3. Nc3 Bg7 4. d4 O-O 5. Bf4 d5 6. Qb3 dxc4 7. Qxc4 c6 8. e4 Nbd7 9. Rd1 Nb6 10. Qc5 Bg4 11. Bg5 Na4 12. Qa3 Nxc3 13. bxc3 Nxe4 14. Bxe7 Qb6 15. Bc4 Nxc3 16. Bc5 Rfe8+ 17. Kf1 Be6 18. Bxb6 Bxc4+ 19. Kg1 Ne2+ 20. Kf1 Nxd4+ 21. Kg1 Ne2+ 22. Kf1 Nc3+ 23. Kg1 axb6 24. Qb4 Ra4 25. Qxb6 Nxd1 26. h3 Rxa2 27. Kh2 Nxf2 28. Re1 Rxe1 29. Qd8+ Bf8 30. Nxe1 Bd5 31. Nf3 Ne4 32. Qb8 b5 33. h4 h5 34. Ne5 Kg7 35. Kg1 Bc5+ 36. Kf1 Ng3+ 37. Ke1 Bb4+ 38. Kd1 Bb3+ 39. Kc1 Ne2+ 40. Kb1 Nc3+ 41. Kc1 Rc2# 0-1";
            Calendar c = Calendar.getInstance();
            c.set(1956, 10, 17);
            String sDate = "" + c.getTimeInMillis();
            db.execSQL("INSERT INTO "
                    + GAMES_TABLE_NAME
                    + " (" + PGNColumns.WHITE + ", " + PGNColumns.BLACK + ", " + PGNColumns.PGN + ", " + PGNColumns.DATE + ", " + PGNColumns.RATING + ", " + PGNColumns.EVENT + ")"
                    + " VALUES ('Donald Byrne', 'Robert James Fischer', '" + sPGN + "', " + sDate + ", 5.0, 'Great game');"); 
        }

        public void onUpgrade(SQLiteDatabase db, int oldVersion, int newVersion) {
            Log.w(TAG, "Upgrading database from version " + oldVersion + " to "
                    + newVersion + ", which will destroy all old data");
            db.execSQL("DROP TABLE IF EXISTS " + GAMES_TABLE_NAME);
            onCreate(db);
        }
    }

    private DatabaseHelper mOpenHelper;

    @Override
    public boolean onCreate() {
        mOpenHelper = new DatabaseHelper(getContext());
        
        Log.i("onCreate PGNProvider", CONTENT_URI.toString());
        
        return true;
    }

    public Cursor query(Uri uri, String[] projection, String selection, String[] selectionArgs, String sortOrder) {
        SQLiteQueryBuilder qb = new SQLiteQueryBuilder();

        switch (sUriMatcher.match(uri)) {
        case GAMES:
            qb.setTables(GAMES_TABLE_NAME);
            qb.setProjectionMap(sGamesProjectionMap);
            break;

        case GAMES_ID:
            qb.setTables(GAMES_TABLE_NAME);
            qb.setProjectionMap(sGamesProjectionMap);
            qb.appendWhere(PGNColumns._ID + "=" + uri.getPathSegments().get(1));
            break;

        default:
            throw new IllegalArgumentException("Unknown URI " + uri);
        }

        // If no sort order is specified use the default
        String orderBy;
        if (TextUtils.isEmpty(sortOrder)) {
            orderBy = PGNColumns.DEFAULT_SORT_ORDER;
        } else {
            orderBy = sortOrder;
        }

        // Get the database and run the query
        SQLiteDatabase db = mOpenHelper.getReadableDatabase();
        Cursor c = qb.query(db, projection, selection, selectionArgs, null, null, orderBy);

        // Tell the cursor what uri to watch, so it knows when its source data changes
        c.setNotificationUri(getContext().getContentResolver(), uri);
        return c;
    }

    @Override
    public String getType(Uri uri) {
        switch (sUriMatcher.match(uri)) {
        case GAMES:
            return PGNColumns.CONTENT_TYPE;

        case GAMES_ID:
            return PGNColumns.CONTENT_ITEM_TYPE;

        default:
            throw new IllegalArgumentException("Unknown URI " + uri);
        }
    }

    public Uri insert(Uri uri, ContentValues initialValues) {
        // Validate the requested uri
        if (sUriMatcher.match(uri) != GAMES) {
            throw new IllegalArgumentException("Unknown URI " + uri);
        }

        ContentValues values;
        if (initialValues != null) {
            values = new ContentValues(initialValues);
        } else {
            values = new ContentValues();
        }

        Long now = Long.valueOf(System.currentTimeMillis());

        // Make sure that the fields are all set
        if (values.containsKey(PGNColumns.DATE) == false) {
            values.put(PGNColumns.DATE, now);
        }

        if (values.containsKey(PGNColumns.WHITE) == false) {
            Resources r = Resources.getSystem();
            values.put(PGNColumns.WHITE, "white ?");
        }
        if (values.containsKey(PGNColumns.BLACK) == false) {
            Resources r = Resources.getSystem();
            values.put(PGNColumns.BLACK, "black ?");
        }
        if (values.containsKey(PGNColumns.PGN) == false) {
            values.put(PGNColumns.PGN, "");
        }
        if (values.containsKey(PGNColumns.RATING) == false) {
            values.put(PGNColumns.RATING, 2.5);
        }
        if (values.containsKey(PGNColumns.EVENT) == false) {
            values.put(PGNColumns.RATING, "event ?");
        }

        SQLiteDatabase db = mOpenHelper.getWritableDatabase();
        long rowId = db.insert(GAMES_TABLE_NAME, null /*PGNColumns.PGN*/, values);
        if (rowId > 0) {
            Uri gameUri = ContentUris.withAppendedId(CONTENT_URI, rowId);
            getContext().getContentResolver().notifyChange(gameUri, null);
            return gameUri;
        }

        throw new SQLException("Failed to insert row into " + uri);
    }

    @Override
    public int delete(Uri uri, String where, String[] whereArgs) {
        SQLiteDatabase db = mOpenHelper.getWritableDatabase();
        int count;
        switch (sUriMatcher.match(uri)) {
        case GAMES:
            count = db.delete(GAMES_TABLE_NAME, where, whereArgs);
            break;

        case GAMES_ID:
            String gameId = uri.getPathSegments().get(1);
            count = db.delete(GAMES_TABLE_NAME, PGNColumns._ID + "=" + gameId
                    + (!TextUtils.isEmpty(where) ? " AND (" + where + ')' : ""), whereArgs);
            break;

        default:
            throw new IllegalArgumentException("Unknown URI " + uri);
        }

        getContext().getContentResolver().notifyChange(uri, null);
        return count;
    }

    @Override
    public int update(Uri uri, ContentValues values, String where, String[] whereArgs) {
        SQLiteDatabase db = mOpenHelper.getWritableDatabase();
        int count;
        switch (sUriMatcher.match(uri)) {
        case GAMES:
            count = db.update(GAMES_TABLE_NAME, values, where, whereArgs);
            break;

        case GAMES_ID:
            String gameId = uri.getPathSegments().get(1);
            count = db.update(GAMES_TABLE_NAME, values, PGNColumns._ID + "=" + gameId
                    + (!TextUtils.isEmpty(where) ? " AND (" + where + ')' : ""), whereArgs);
            break;

        default:
            throw new IllegalArgumentException("Unknown URI " + uri);
        }

        getContext().getContentResolver().notifyChange(uri, null);
        return count;
    }

    static {
        

        sGamesProjectionMap = new HashMap<String, String>();
        sGamesProjectionMap.put(PGNColumns._ID, PGNColumns._ID);
        sGamesProjectionMap.put(PGNColumns.WHITE, PGNColumns.WHITE);
        sGamesProjectionMap.put(PGNColumns.BLACK, PGNColumns.BLACK);
        sGamesProjectionMap.put(PGNColumns.PGN, PGNColumns.PGN);
        sGamesProjectionMap.put(PGNColumns.DATE, PGNColumns.DATE);
        sGamesProjectionMap.put(PGNColumns.RATING, PGNColumns.RATING);
        sGamesProjectionMap.put(PGNColumns.EVENT, PGNColumns.EVENT);
    }
}
