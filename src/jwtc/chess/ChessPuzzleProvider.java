package jwtc.chess;

import java.util.HashMap;
import android.content.ContentProvider;
import android.content.ContentUris;
import android.content.ContentValues;
import android.content.Context;
import android.content.UriMatcher;
import android.database.Cursor;
import android.database.SQLException;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;
import android.database.sqlite.SQLiteQueryBuilder;
import android.net.Uri;
import android.text.TextUtils;
import android.util.Log;

public class ChessPuzzleProvider extends ContentProvider {

	public static String AUTHORITY = "jwtc.android.chess.puzzle.ChessPuzzleProvider";
    public static Uri CONTENT_URI_PUZZLES = Uri.parse("content://"  + AUTHORITY + "/puzzles");
    public static Uri CONTENT_URI_PRACTICES = Uri.parse("content://"  + AUTHORITY + "/practices");
	
    private static final String TAG = "ChessPuzzleProvider";

    private static final String DATABASE_NAME = "chess_puzzles.db";
    private static final int DATABASE_VERSION = 8; // 7.6 lessons 
    private static final String GAMES_TABLE_NAME = "games";

    private static HashMap<String, String> sGamesProjectionMap;

    protected static final int PUZZLES = 1;
    protected static final int PUZZLES_ID = 2;
    protected static final int PRACTICES = 3;
    protected static final int PRACTICES_ID = 4;

    protected static final int TYPE_PUZZLE = 1;
    protected static final int TYPE_PRACTICE = 2;
    
    protected static UriMatcher sUriMatcher;
    
    public static final String COL_ID = "_ID";
    public static final String COL_PGN = "PGN";
    public static final String COL_TYPE = "PUZZLE_TYPE";
    
    public static final String[] COLUMNS = {
    		COL_ID,
    		COL_TYPE,
    		COL_PGN
    };
    
    public static final String DEFAULT_SORT_ORDER = "_ID ASC";
    public static final String CONTENT_TYPE = "vnd.android.cursor.dir/vnd.jwtc.chesspuzzle";
    public static final String CONTENT_ITEM_TYPE = "vnd.android.cursor.item/vnd.jwtc.chesspuzzle";

    /**
     * This class helps open, create, and upgrade the database file.
     */
    private static class DatabaseHelper extends SQLiteOpenHelper {

        DatabaseHelper(Context context) {
            super(context, DATABASE_NAME, null, DATABASE_VERSION);
        }

        public void onCreate(SQLiteDatabase db) {
            db.execSQL("CREATE TABLE " + GAMES_TABLE_NAME + " ("
                    + COL_ID + " INTEGER PRIMARY KEY,"
                    + COL_TYPE + " INTEGER,"
                    + COL_PGN + " TEXT"
                    + ");");
            
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
        
        return true;
    }

    public Cursor query(Uri uri, String[] projection, String selection, String[] selectionArgs,
            String sortOrder) {
        SQLiteQueryBuilder qb = new SQLiteQueryBuilder();

        switch (sUriMatcher.match(uri)) {
        case PUZZLES:
            qb.setTables(GAMES_TABLE_NAME);
            qb.setProjectionMap(sGamesProjectionMap);
            qb.appendWhere(COL_TYPE + "=" + TYPE_PUZZLE);
            break;

        case PUZZLES_ID:
            qb.setTables(GAMES_TABLE_NAME);
            qb.setProjectionMap(sGamesProjectionMap);
            qb.appendWhere(COL_ID + "=" + uri.getPathSegments().get(1));
            break;
        case PRACTICES:
            qb.setTables(GAMES_TABLE_NAME);
            qb.setProjectionMap(sGamesProjectionMap);
            qb.appendWhere(COL_TYPE + "=" + TYPE_PRACTICE);
            break;

        case PRACTICES_ID:
            qb.setTables(GAMES_TABLE_NAME);
            qb.setProjectionMap(sGamesProjectionMap);
            qb.appendWhere(COL_ID + "=" + uri.getPathSegments().get(1));
            break;
            

        default:
            throw new IllegalArgumentException("Unknown URI " + uri);
        }

        // If no sort order is specified use the default
        String orderBy;
        if (TextUtils.isEmpty(sortOrder)) {
            orderBy = DEFAULT_SORT_ORDER;
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
        case PUZZLES:
            return CONTENT_TYPE;
        case PUZZLES_ID:
            return CONTENT_ITEM_TYPE;
        case PRACTICES:
            return CONTENT_TYPE;
        case PRACTICES_ID:
            return CONTENT_ITEM_TYPE;

        default:
            throw new IllegalArgumentException("Unknown URI " + uri);
        }
    }

    public Uri insert(Uri uri, ContentValues initialValues) {
        // Validate the requested uri
    	
    	int iType = sUriMatcher.match(uri); 
        if (iType != PUZZLES && iType != PRACTICES) {
            throw new IllegalArgumentException("Unknown URI " + uri);
        }

        ContentValues values;
        if (initialValues != null) {
            values = new ContentValues(initialValues);
        } else {
            values = new ContentValues();
        }

        Long now = Long.valueOf(System.currentTimeMillis());

        if(iType == PUZZLES)
        	values.put(COL_TYPE, TYPE_PUZZLE);
        else if(iType == PRACTICES)
        	values.put(COL_TYPE, TYPE_PRACTICE);


        SQLiteDatabase db = mOpenHelper.getWritableDatabase();
        long rowId = db.insert(GAMES_TABLE_NAME, null, values);
        if (rowId > 0) {
            Uri myUri;
            if(iType == PUZZLES)
            	myUri = ContentUris.withAppendedId(CONTENT_URI_PUZZLES, rowId);
            else
            	myUri = ContentUris.withAppendedId(CONTENT_URI_PRACTICES, rowId);
            
            getContext().getContentResolver().notifyChange(myUri, null);
            return myUri;
        }

        throw new SQLException("Failed to insert row into " + uri);
    }

    @Override
    public int delete(Uri uri, String where, String[] whereArgs) {
        SQLiteDatabase db = mOpenHelper.getWritableDatabase();
        int count;
        switch (sUriMatcher.match(uri)) {
        case PUZZLES:
            count = db.delete(GAMES_TABLE_NAME, COL_TYPE + "=" + TYPE_PUZZLE
            		+ (!TextUtils.isEmpty(where) ? " AND (" + where + ')' : ""), whereArgs);
            break;

        case PUZZLES_ID:
            String puzzleId = uri.getPathSegments().get(1);
            count = db.delete(GAMES_TABLE_NAME, COL_ID + "=" + puzzleId
                    + (!TextUtils.isEmpty(where) ? " AND (" + where + ')' : ""), whereArgs);
            break;

        case PRACTICES:
            count = db.delete(GAMES_TABLE_NAME, COL_TYPE + "=" + TYPE_PRACTICE
            		+ (!TextUtils.isEmpty(where) ? " AND (" + where + ')' : ""), whereArgs);
            break;

        case PRACTICES_ID:
            String practiceId = uri.getPathSegments().get(1);
            count = db.delete(GAMES_TABLE_NAME, COL_ID + "=" + practiceId
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
        case PUZZLES_ID:
        case PRACTICES_ID:
            String gameId = uri.getPathSegments().get(1);
            count = db.update(GAMES_TABLE_NAME, values, COL_ID + "=" + gameId
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
        sGamesProjectionMap.put(COL_ID, COL_ID);
        sGamesProjectionMap.put(COL_TYPE, COL_TYPE);
        sGamesProjectionMap.put(COL_PGN, COL_PGN);
        
        sUriMatcher = new UriMatcher(UriMatcher.NO_MATCH);
        sUriMatcher.addURI(AUTHORITY, "puzzles", PUZZLES);
        sUriMatcher.addURI(AUTHORITY, "puzzles/#", PUZZLES_ID);
        sUriMatcher.addURI(AUTHORITY, "practices", PRACTICES);
        sUriMatcher.addURI(AUTHORITY, "practices/#", PRACTICES_ID);
    }
}
