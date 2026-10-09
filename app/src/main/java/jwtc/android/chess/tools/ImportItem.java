package jwtc.android.chess.tools;

/** One game or opening read from an import file: its PGN, and a name when the file gives one. */
public final class ImportItem {
    public final String pgn;
    /** Null when the file does not name the item. */
    public final String name;

    public ImportItem(String pgn, String name) {
        this.pgn = pgn;
        this.name = name;
    }
}
