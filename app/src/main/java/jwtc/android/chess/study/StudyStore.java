package jwtc.android.chess.study;

import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import jwtc.chess.PGNProvider;

public final class StudyStore {
    private static final String CHAPTERS = "study_chapters";
    private static final String CHAPTER_KEY = "collection_id = ? AND study_id = ? AND chapter_id = ?";

    static final class Study {
        final String collectionId, collectionName, id, title, author;
        final List<Chapter> chapters = new ArrayList<>();

        Study(Cursor row) {
            collectionId = row.getString(0);
            id = row.getString(1);
            collectionName = row.getString(2);
            title = row.getString(3);
            author = row.getString(4);
        }

        boolean matches(String collectionId, String studyId) {
            return this.collectionId.equals(collectionId) && id.equals(studyId);
        }
    }

    static final class Chapter {
        final long id;
        final String title, topic, author;

        Chapter(Cursor row) {
            id = row.getLong(6);
            title = row.getString(7);
            topic = row.getString(5);
            author = row.getString(4);
        }
    }

    public static boolean importChapter(
            Context context, String pgn, String filename, int position, String title, String topic) {
        final StudyPgn.Chapter chapter;
        try {
            chapter = StudyPgn.parseChapter(pgn);
        } catch (IllegalArgumentException error) {
            return false;
        }
        Map<String, String> tags = chapter.tags;
        String collectionName = valueOr(tags, "CollectionName", filename);
        String studyTitle = valueOr(tags, "StudyTitle", title);
        String chapterTopic = StudyPgn.topic(tags);
        if (chapterTopic == null) {
            chapterTopic = topic;
        }
        if (studyTitle == null || chapterTopic == null) {
            return false;
        }
        String chapterTitle = StudyPgn.chapterTitle(tags);
        String collectionId = valueOr(tags, "CollectionId", collectionName);
        String studyId = valueOr(tags, "StudyId", studyTitle);
        String chapterId = valueOr(tags, "ChapterId", filename + "#" + position);

        ContentValues row = new ContentValues();
        row.put("collection_id", collectionId);
        row.put("study_id", studyId);
        row.put("chapter_id", chapterId);
        row.put("collection_name", collectionName);
        row.put("study_title", studyTitle);
        row.put("author", StudyPgn.value(tags, "StudyAuthor"));
        row.put("topic", chapterTopic);
        row.put("title", chapterTitle == null ? filename + " " + position : chapterTitle);
        row.put("pgn", chapter.pgn);
        SQLiteDatabase db = PGNProvider.getDatabaseHelper(context).getWritableDatabase();
        db.beginTransaction();
        try {
            if (db.update(CHAPTERS, row, CHAPTER_KEY, new String[] {collectionId, studyId, chapterId}) == 0) {
                db.insertOrThrow(CHAPTERS, null, row);
            }
            db.setTransactionSuccessful();
            return true;
        } finally {
            db.endTransaction();
        }
    }

    private static String valueOr(Map<String, String> tags, String name, String fallback) {
        String value = StudyPgn.value(tags, name);
        return value == null ? fallback : value;
    }

    public static long countStudies(Context context) {
        SQLiteDatabase db = PGNProvider.getDatabaseHelper(context).getReadableDatabase();
        try (Cursor rows = db.rawQuery(
                "SELECT COUNT(*) FROM (SELECT DISTINCT collection_id, study_id FROM study_chapters)",
                null)) {
            rows.moveToFirst();
            return rows.getLong(0);
        }
    }

    public static void deleteAll(Context context) {
        PGNProvider.getDatabaseHelper(context).getWritableDatabase().delete(CHAPTERS, null, null);
    }

    static List<Study> library(Context context) {
        SQLiteDatabase db = PGNProvider.getDatabaseHelper(context).getReadableDatabase();
        List<Study> library = new ArrayList<>();
        try (Cursor rows = db.rawQuery(
                "SELECT collection_id, study_id, collection_name, study_title, author, topic, id, title "
                        + "FROM study_chapters ORDER BY collection_id, study_id, id",
                null)) {
            Study study = null;
            while (rows.moveToNext()) {
                if (study == null || !study.matches(rows.getString(0), rows.getString(1))) {
                    study = new Study(rows);
                    library.add(study);
                }
                study.chapters.add(new Chapter(rows));
            }
        }
        library.sort(Comparator.comparing(
                (Study study) -> study.collectionName, String.CASE_INSENSITIVE_ORDER)
                .thenComparing(study -> study.title, String.CASE_INSENSITIVE_ORDER));
        return library;
    }

    static List<Chapter> chapters(Context context, String collectionId, String studyId) {
        SQLiteDatabase db = PGNProvider.getDatabaseHelper(context).getReadableDatabase();
        List<Chapter> chapters = new ArrayList<>();
        try (Cursor rows = db.rawQuery(
                "SELECT collection_id, study_id, collection_name, study_title, author, topic, id, title "
                        + "FROM study_chapters WHERE collection_id = ? AND study_id = ? ORDER BY id",
                new String[] {collectionId, studyId})) {
            while (rows.moveToNext()) {
                chapters.add(new Chapter(rows));
            }
        }
        return chapters;
    }

    static String chapterPgn(Context context, long id) {
        SQLiteDatabase db = PGNProvider.getDatabaseHelper(context).getReadableDatabase();
        try (Cursor row = db.query(
                CHAPTERS, new String[] {"pgn"}, "id = ?", new String[] {Long.toString(id)},
                null, null, null)) {
            return row.moveToFirst() ? row.getString(0) : null;
        }
    }

    private StudyStore() {}
}
