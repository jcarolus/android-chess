package jwtc.chess;

import android.provider.BaseColumns;

public final class PGNColumns implements BaseColumns {

    public static final String CONTENT_TYPE = "vnd.android.cursor.dir/vnd.jwtc.pgn";
    public static final String CONTENT_ITEM_TYPE = "vnd.android.cursor.item/vnd.jwtc.pgn";

    /**
     * The default sort order for this table
     */
    public static final String DEFAULT_SORT_ORDER = "date DESC";

    public static final String WHITE = "white";
    public static final String BLACK = "black";
    public static final String PGN = "pgn";
    public static final String DATE = "date";
    public static final String RATING = "rating";
    public static final String EVENT = "event";
    
    public static final String[] COLUMNS = {PGNColumns._ID, 
											PGNColumns.WHITE,
											PGNColumns.BLACK,
											PGNColumns.PGN,
											PGNColumns.DATE,
											PGNColumns.RATING,
											PGNColumns.EVENT};
}
