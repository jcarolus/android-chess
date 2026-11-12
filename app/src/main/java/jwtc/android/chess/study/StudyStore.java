package jwtc.android.chess.study;

import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.database.DatabaseUtils;
import android.database.sqlite.SQLiteDatabase;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import jwtc.chess.PGNProvider;

public final class StudyStore {
    private static final String STUDIES = "studies";
    private static final String CHAPTERS = "study_chapters";
    private static final String STUDY_KEY = "collection_id = ? AND study_id = ?";
    private static final String CHAPTER_ORDER = "chapter_order, chapter_id";

    static final class Study {
        final String collectionId, collectionName, id, title, author, topic;
        final List<Chapter> chapters = new ArrayList<>();

        Study(Cursor row) {
            collectionId = row.getString(0);
            id = row.getString(1);
            collectionName = row.getString(2);
            title = row.getString(3);
            author = row.getString(4);
            topic = row.getString(5);
        }

        boolean matches(String collectionId, String studyId) {
            return this.collectionId.equals(collectionId) && id.equals(studyId);
        }
    }

    static final class Chapter {
        final String id, title;

        Chapter(String id, String title) {
            this.id = id;
            this.title = title;
        }
    }

    static final class ReaderChapter {
        final String pgn;
        final Chapter next;

        ReaderChapter(String pgn, Chapter next) {
            this.pgn = pgn;
            this.next = next;
        }
    }

    public static boolean importChapter(Context context, String pgn) {
        final StudyPgn.Chapter chapter;
        try {
            chapter = StudyPgn.parseChapter(pgn);
        } catch (IllegalArgumentException error) {
            return false;
        }
        Map<String, String> tags = chapter.tags;
        String collectionId = tags.get("CollectionId");
        String studyId = tags.get("StudyId");
        SQLiteDatabase db = PGNProvider.getDatabaseHelper(context).getWritableDatabase();
        db.beginTransaction();
        try {
            ContentValues study = new ContentValues();
            study.put("collection_id", collectionId);
            study.put("study_id", studyId);
            study.put("collection_name", tags.get("CollectionName"));
            study.put("title", tags.get("StudyTitle"));
            study.put("author", tags.get("StudyAuthor"));
            study.put("topic", tags.get("StudyTopic"));
            if (db.update(STUDIES, study, STUDY_KEY, new String[] {collectionId, studyId}) == 0) {
                db.insertOrThrow(STUDIES, null, study);
            }

            ContentValues row = new ContentValues();
            row.put("collection_id", collectionId);
            row.put("study_id", studyId);
            row.put("chapter_id", tags.get("ChapterId"));
            row.put("chapter_order", chapter.order);
            row.put("title", tags.get("ChapterName"));
            row.put("pgn", chapter.pgn);
            db.replaceOrThrow(CHAPTERS, null, row);
            db.setTransactionSuccessful();
            return true;
        } finally {
            db.endTransaction();
        }
    }

    public static long countStudies(Context context) {
        SQLiteDatabase db = PGNProvider.getDatabaseHelper(context).getReadableDatabase();
        return DatabaseUtils.queryNumEntries(db, STUDIES);
    }

    public static void deleteAll(Context context) {
        SQLiteDatabase db = PGNProvider.getDatabaseHelper(context).getWritableDatabase();
        db.beginTransaction();
        try {
            db.delete(CHAPTERS, null, null);
            db.delete(STUDIES, null, null);
            db.setTransactionSuccessful();
        } finally {
            db.endTransaction();
        }
    }

    static List<Study> library(Context context) {
        SQLiteDatabase db = PGNProvider.getDatabaseHelper(context).getReadableDatabase();
        List<Study> studies = new ArrayList<>();
        try (Cursor rows =
                db.rawQuery(
                        "SELECT s.collection_id, s.study_id, s.collection_name, s.title,"
                                + " s.author, s.topic, c.chapter_id, c.title"
                                + " FROM studies s JOIN study_chapters c"
                                + " USING (collection_id, study_id)"
                                + " ORDER BY s.collection_name COLLATE NOCASE, s.collection_id,"
                                + " s.title COLLATE NOCASE, s.study_id, " + CHAPTER_ORDER,
                        null)) {
            Study study = null;
            while (rows.moveToNext()) {
                if (study == null || !study.matches(rows.getString(0), rows.getString(1))) {
                    study = new Study(rows);
                    studies.add(study);
                }
                study.chapters.add(new Chapter(rows.getString(6), rows.getString(7)));
            }
        }
        return studies;
    }

    static ReaderChapter chapter(Context context, String collectionId, String studyId, String chapterId) {
        SQLiteDatabase db = PGNProvider.getDatabaseHelper(context).getReadableDatabase();
        String pgn;
        try (Cursor rows =
                db.query(
                        CHAPTERS,
                        new String[] {"pgn"},
                        STUDY_KEY + " AND chapter_id = ?",
                        new String[] {collectionId, studyId, chapterId},
                        null,
                        null,
                        null)) {
            if (!rows.moveToFirst()) {
                return new ReaderChapter(null, null);
            }
            pgn = rows.getString(0);
        }
        try (Cursor rows =
                db.query(
                        CHAPTERS,
                        new String[] {"chapter_id", "title"},
                        STUDY_KEY,
                        new String[] {collectionId, studyId},
                        null,
                        null,
                        CHAPTER_ORDER)) {
            while (rows.moveToNext()) {
                if (chapterId.equals(rows.getString(0))) {
                    if (rows.moveToNext()) {
                        return new ReaderChapter(pgn, new Chapter(rows.getString(0), rows.getString(1)));
                    }
                    break;
                }
            }
        }
        return new ReaderChapter(pgn, null);
    }

    private StudyStore() {}
}
