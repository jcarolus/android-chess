package jwtc.chess;

import android.content.ContentResolver;
import android.content.Context;
import android.database.Cursor;
import android.database.DatabaseUtils;
import android.database.sqlite.SQLiteDatabase;

import jwtc.android.chess.helpers.GameStore;
import jwtc.android.chess.helpers.MyPGNProvider;
import jwtc.android.chess.puzzle.MyPuzzleProvider;
import jwtc.android.chess.study.StudyStore;
import jwtc.android.chess.tools.ImportApi;
import jwtc.android.chess.tools.ImportHandler;
import jwtc.android.chess.tools.ImportHandler.Result;
import jwtc.android.chess.tools.ImportItem;
import jwtc.android.chess.tools.PracticeImportHandler;
import jwtc.android.chess.tools.PuzzleImportHandler;

import java.util.UUID;

public final class ImportFingerprintInstrumentation {
    private static final String PGN = "1. e4 e5 *";

    public static void verify(Context context) {
        verifyGameAndStudyUpgrade(context, 7);
        verifyGameAndStudyUpgrade(context, 8);
        verifyMateCollectionUpgrade(context, 8);
        verifyMateCollectionUpgrade(context, 9);
        verifyGameImports(context);
        verifyStudyImports(context);
        verifyMateCollectionImports(context);
        verifyFullPracticeImport(context);
    }

    private static void verifyGameAndStudyUpgrade(Context context, int version) {
        String name = "test-" + UUID.randomUUID() + ".db";
        try {
            try (SQLiteDatabase db =
                    context.openOrCreateDatabase(name, Context.MODE_PRIVATE, null)) {
                db.execSQL(
                        "CREATE TABLE games (_id INTEGER PRIMARY KEY, white TEXT, black TEXT,"
                                + " pgn TEXT, date INTEGER, rating REAL, event TEXT, result TEXT)");
                db.execSQL(
                        "INSERT INTO games VALUES (12, 'White', 'Black', ?, 12345, 4.5, 'Event',"
                            + " '*')",
                        new Object[] {PGN});
                db.execSQL("INSERT INTO games (_id, pgn) VALUES (13, 'invalid')");
                if (version == 7) {
                    db.execSQL("CREATE TABLE studies (collection_id TEXT NOT NULL, study_id TEXT NOT NULL,"
                            + " collection_name TEXT NOT NULL, title TEXT NOT NULL, author TEXT NOT NULL,"
                            + " topic TEXT NOT NULL, PRIMARY KEY (collection_id, study_id))");
                    db.execSQL("INSERT INTO studies VALUES ('collection', 'study', 'Collection',"
                            + " 'Study', 'Author', 'Topic')");
                    db.execSQL("CREATE TABLE study_chapters (collection_id TEXT NOT NULL,"
                            + " study_id TEXT NOT NULL, chapter_id TEXT NOT NULL, chapter_order INTEGER NOT NULL,"
                            + " title TEXT NOT NULL, pgn TEXT NOT NULL, PRIMARY KEY (collection_id, study_id, chapter_id))");
                    db.execSQL("INSERT INTO study_chapters VALUES ('collection', 'study', 'chapter',"
                            + " 7, 'Chapter', ?)", new Object[] {PGN});
                } else {
                    db.execSQL(
                            "CREATE TABLE study_chapters (id INTEGER PRIMARY KEY, collection_id TEXT"
                                + " NOT NULL, study_id TEXT NOT NULL, chapter_id TEXT NOT NULL,"
                                + " collection_name TEXT NOT NULL, study_title TEXT NOT NULL, author"
                                + " TEXT, topic TEXT NOT NULL, title TEXT NOT NULL, pgn TEXT NOT NULL,"
                                + " UNIQUE (collection_id, study_id, chapter_id))");
                    db.execSQL(
                            "INSERT INTO study_chapters VALUES (7, 'collection', 'study', 'chapter',"
                                    + " 'Collection', 'Study', NULL, 'Topic', 'Chapter', ?)",
                            new Object[] {PGN});
                }
                db.setVersion(version);
            }
            try (PGNProvider.DatabaseHelper helper =
                    new PGNProvider.DatabaseHelper(context, name)) {
                SQLiteDatabase db = helper.getWritableDatabase();
                check(db.getVersion() == 9, "Game Database was not upgraded");
                check(
                        PgnFingerprint.of(PGN)
                                .equals(
                                        DatabaseUtils.stringForQuery(
                                                db,
                                                "SELECT fingerprint FROM games WHERE _id = 12 AND"
                                                    + " rating = 4.5 AND date = 12345",
                                                null)),
                        "Game migration lost its data or fingerprint");
                check(
                        "invalid"
                                .equals(
                                        DatabaseUtils.stringForQuery(
                                                db,
                                                "SELECT pgn FROM games WHERE _id = 13 AND"
                                                    + " fingerprint IS NULL",
                                                null)),
                        "Unparseable legacy Game was lost");
                check(
                        PgnFingerprint.of(PGN)
                                .equals(
                                        DatabaseUtils.stringForQuery(
                                                db,
                                                "SELECT fingerprint FROM study_chapters WHERE id ="
                                                    + " ? AND chapter_id = 'chapter'"
                                                    + " AND study_title = 'Study' AND title = 'Chapter'",
                                                new String[] {version == 7 ? "1" : "7"})),
                        "Study migration lost its chapter or fingerprint");
            }
        } finally {
            context.deleteDatabase(name);
        }
    }

    private static void verifyMateCollectionUpgrade(Context context, int version) {
        String name = "test-" + UUID.randomUUID() + ".db";
        try {
            try (SQLiteDatabase db =
                    context.openOrCreateDatabase(name, Context.MODE_PRIVATE, null)) {
                db.execSQL(
                        "CREATE TABLE games (_ID INTEGER PRIMARY KEY, PUZZLE_TYPE INTEGER, PGN"
                            + " TEXT)");
                db.execSQL(
                        "INSERT INTO games VALUES (3, 1, ?), (8, 2, ?), (9, 2, ?)",
                        new Object[] {PGN, PGN, PGN});
                if (version == 9) {
                    db.execSQL("ALTER TABLE games ADD COLUMN fingerprint TEXT");
                    db.execSQL("UPDATE games SET fingerprint = ?", new Object[] {PgnFingerprint.of(PGN)});
                }
                db.setVersion(version);
            }
            try (ChessPuzzleProvider.DatabaseHelper helper =
                    new ChessPuzzleProvider.DatabaseHelper(context, name)) {
                SQLiteDatabase db = helper.getWritableDatabase();
                check(db.getVersion() == 10, "Mate Collection database was not upgraded");
                check(
                        DatabaseUtils.longForQuery(
                                        db,
                                        "SELECT COUNT(*) FROM games WHERE full_game = 0 AND fingerprint = ? AND ((_ID"
                                            + " = 3 AND PUZZLE_TYPE = 1) OR (_ID IN (8, 9) AND"
                                            + " PUZZLE_TYPE = 2))",
                                        new String[] {PgnFingerprint.of(PGN)})
                                == 3,
                        "Mate Collection migration lost rows, types, IDs or fingerprints");
            }
        } finally {
            context.deleteDatabase(name);
        }
    }

    private static void verifyGameImports(Context context) {
        ContentResolver resolver = context.getContentResolver();
        GameStore games = new GameStore(resolver);
        String event = UUID.randomUUID().toString();
        String tags = "[Event \"" + event + "\"]\n[White \"White\"]\n[Black \"Black\"]\n";
        try {
            GameRecord record = GameRecord.fromPgn(tags + PGN);
            long id = games.insert(record);
            check(
                    !games.importGame(GameRecord.fromPgn(tags + "1. e4 {comment} e5 *")),
                    "Game already saved with a legacy-style undated timestamp was imported again");
            check(games.save(record, true, id) != id, "Save Copy did not create a new row");
            check(
                    games.importGame(GameRecord.fromPgn(tags + "[Date \"2026.11.16\"]\n" + PGN)),
                    "Game with a different date was skipped");
            check(
                    !games.importGame(GameRecord.fromPgn(tags + "[Date \"2026.11.16\"]\n" + PGN)),
                    "Repeated dated Game was imported");
            String other = tags.replace("[White \"White\"]", "[White \"Other\"]");
            check(
                    games.importGame(GameRecord.fromPgn(other + PGN)),
                    "Different players were skipped");
            check(
                    !games.importGame(GameRecord.fromPgn(other + PGN)),
                    "Repeated undated Game was imported");
        } finally {
            resolver.delete(MyPGNProvider.CONTENT_URI, "event = ?", new String[] {event});
        }
    }

    private static void verifyStudyImports(Context context) {
        SQLiteDatabase db = PGNProvider.getDatabaseHelper(context).getWritableDatabase();
        String collection = UUID.randomUUID().toString();
        String tag = "[CollectionId \"" + collection + "\"]\n";
        try {
            check(
                    StudyStore.importChapter(context, tag + PGN, "first", 1, "Study", "Topic")
                            == Result.INSERTED,
                    "Study chapter was not inserted");
            check(
                    StudyStore.importChapter(context, tag + PGN, "second", 4, "Study", "Topic")
                            == Result.DUPLICATE,
                    "Renamed or reordered Study chapter was imported again");
            check(
                    StudyStore.importChapter(context, tag + PGN, "second", 4, "Other", "Topic")
                            == Result.INSERTED,
                    "Another Study's chapter was skipped");
            String explicit = tag + "[ChapterId \"explicit\"]\n";
            check(
                    StudyStore.importChapter(context, explicit + PGN, "first", 2, "Study", "Topic")
                            == Result.INSERTED,
                    "Explicit chapter was skipped");
            check(
                    StudyStore.importChapter(
                                    context, explicit + "1. d4 *", "first", 2, "Study", "Topic")
                            == Result.UPDATED,
                    "Explicit chapter was not updated");
            check(
                    PgnFingerprint.of("1. d4 *")
                            .equals(
                                    DatabaseUtils.stringForQuery(
                                            db,
                                            "SELECT fingerprint FROM study_chapters WHERE"
                                                + " collection_id = ? AND chapter_id = 'explicit'",
                                            new String[] {collection})),
                    "Updating an explicit chapter left a stale fingerprint");
        } finally {
            db.delete("study_chapters", "collection_id = ?", new String[] {collection});
        }
    }

    private static void verifyMateCollectionImports(Context context) {
        ContentResolver resolver = context.getContentResolver();
        String pgn = "[FEN \"4k3/R7/1R6/8/8/8/8/4K3 w - - 0 4123\"]\n1. Rb8# 1-0";
        String selection = "fingerprint = ?";
        String[] args = {PgnFingerprint.of(pgn)};
        boolean addedPuzzle = false;
        boolean addedPractice = false;
        try {
            ImportHandler puzzle = new PuzzleImportHandler(new ImportApi(), resolver);
            ImportHandler practice = new PracticeImportHandler(resolver);
            addedPuzzle = puzzle.handle(new ImportItem(pgn, null)) == Result.INSERTED;
            addedPractice = practice.handle(new ImportItem(pgn, null)) == Result.INSERTED;
            check(
                    addedPuzzle && addedPractice,
                    "Both collections should accept the same fingerprint");
            check(
                    new PuzzleImportHandler(new ImportApi(), resolver)
                                    .handle(new ImportItem(pgn, null))
                            == Result.DUPLICATE,
                    "Repeated Puzzle was imported");
            check(
                    new PracticeImportHandler(resolver)
                                    .handle(new ImportItem(pgn, null))
                            == Result.DUPLICATE,
                    "Repeated Practice Position was imported");
        } finally {
            if (addedPuzzle) {
                resolver.delete(MyPuzzleProvider.CONTENT_URI_PUZZLES, selection, args);
            }
            if (addedPractice) {
                resolver.delete(MyPuzzleProvider.CONTENT_URI_PRACTICES, selection, args);
            }
        }
    }

    private static void verifyFullPracticeImport(Context context) {
        ContentResolver resolver = context.getContentResolver();
        ImportHandler practice = new PracticeImportHandler(resolver);
        String pgn = "[FEN \"" + jwtc.chess.board.BoardPosition.START_FEN.replace("0 1", "0 4123")
                + "\"]\n4123. f3 {comment} e5 4124. g4 Qh4#! 0-1";
        String selection = "fingerprint = ?";
        String[] args = {PgnFingerprint.of(pgn)};
        boolean inserted = false;
        try {
            check(practice.handle(new ImportItem("1. e4# *", null)) == Result.FAILED,
                    "A checkmate suffix bypassed board validation");
            check(practice.handle(new ImportItem("1. f3 e5 2. g4 Qh4 0-1", null)) == Result.FAILED,
                    "A source game without a mate suffix was imported");
            inserted = practice.handle(new ImportItem(pgn, null)) == Result.INSERTED;
            check(inserted, "Full Practice game was not imported");
            try (Cursor rows = resolver.query(MyPuzzleProvider.CONTENT_URI_PRACTICES,
                    new String[] {MyPuzzleProvider.COL_PGN, MyPuzzleProvider.COL_FULL_GAME},
                    selection, args, null)) {
                check(rows != null && rows.moveToFirst() && pgn.equals(rows.getString(0))
                        && rows.getInt(1) == 1, "Full Practice game or format marker was lost");
            }
            String duplicate = pgn.replace("{comment}", "(4123. Ke4)");
            check(practice.handle(new ImportItem(duplicate, null)) == Result.DUPLICATE,
                    "A duplicate entered legal replay before the fingerprint lookup");
        } finally {
            if (inserted) {
                resolver.delete(MyPuzzleProvider.CONTENT_URI_PRACTICES, selection, args);
            }
        }
    }

    private static void check(boolean condition, String message) {
        if (!condition) {
            throw new AssertionError(message);
        }
    }
}
