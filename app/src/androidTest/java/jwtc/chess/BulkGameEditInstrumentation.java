package jwtc.chess;

import android.content.ContentProviderOperation;
import android.content.ContentUris;
import android.content.Context;
import android.content.OperationApplicationException;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.ArrayList;
import jwtc.android.chess.helpers.GameStore;
import jwtc.android.chess.helpers.MyPGNProvider;

/** Exercises real provider transactions and Topic indexing without an Activity. */
public final class BulkGameEditInstrumentation {
    public static void verify(Context context) throws Exception {
        GameStore store = new GameStore(context.getContentResolver());
        String pgn =
                "[Event \"Bulk edit\"]\n[Topic \"Old\"]\n\n" + "1. e4 {keep this} (1. d4 d5) e5 *";
        GameRecord record =
                GameRecord.stored(
                        0,
                        pgn,
                        "Bulk edit",
                        "White",
                        "Black",
                        PgnDate.parse("2026.01.02"),
                        "*",
                        4.5f);
        long first = store.insert(record);
        long second = store.insert(record);
        long unselected = store.insert(record);
        try {
            check(first > 0 && second > 0 && unselected > 0, "Could not insert fixtures");
            store.setSelectedTopic(new long[] {first, second}, "New topic");
            check(
                    store.load(first).pgn.equals(Pgn.withTag(pgn, "Topic", "New topic")),
                    "Movetext or other tags changed");
            check(store.load(first).rating == 4.5f, "Rating changed");
            check(store.load(first).date.equals(record.date), "Date changed");
            check(store.load(unselected).pgn.equals(pgn), "Unselected game changed");
            try (android.database.Cursor cursor =
                    store.query(new GameFilter().topic("New topic"))) {
                boolean hasFirst = false, hasSecond = false;
                while (cursor.moveToNext()) {
                    long id = cursor.getLong(cursor.getColumnIndexOrThrow(PGNColumns._ID));
                    hasFirst |= id == first;
                    hasSecond |= id == second;
                }
                check(hasFirst && hasSecond, "Topic index was not updated");
            }

            String secondPgn = Pgn.withTag(pgn, "Topic", "Second export topic");
            store.setSelectedTopic(new long[] {second}, "Second export topic");
            ByteArrayOutputStream exported = new ByteArrayOutputStream();
            check(
                    store.exportSelectedAsPgn(new long[] {second, first}, exported) == 2,
                    "Wrong export count");
            check(
                    exported.toString("UTF-8")
                            .equals(secondPgn + "\n\n" + store.load(first).pgn + "\n\n"),
                    "Selected export changed PGN content, included other games or changed order");
            check(
                    store.exportSelectedAsPgn(new long[0], new ByteArrayOutputStream()) == 0,
                    "Empty selection exported games");
            try {
                store.exportSelectedAsPgn(new long[] {Long.MAX_VALUE}, new ByteArrayOutputStream());
                throw new AssertionError("Missing game export succeeded");
            } catch (IOException expected) {
                // An incomplete selection must not become a successful export.
            }

            String edited = store.load(first).pgn;
            ArrayList<ContentProviderOperation> operations = new ArrayList<>();
            operations.add(
                    ContentProviderOperation.newUpdate(
                                    ContentUris.withAppendedId(MyPGNProvider.CONTENT_URI, first))
                            .withValue(PGNColumns.PGN, Pgn.withTag(edited, "Topic", "Rolled back"))
                            .withExpectedCount(1)
                            .build());
            operations.add(
                    ContentProviderOperation.newUpdate(
                                    ContentUris.withAppendedId(MyPGNProvider.CONTENT_URI, second))
                            .withValue(PGNColumns.PGN, pgn)
                            .withSelection(PGNColumns.PGN + " = ?", new String[] {"stale PGN"})
                            .withExpectedCount(1)
                            .build());
            try {
                context.getContentResolver().applyBatch(MyPGNProvider.AUTHORITY, operations);
                throw new AssertionError("Stale edit succeeded");
            } catch (OperationApplicationException expected) {
                check(store.load(first).pgn.equals(edited), "Partial topic edit was committed");
            }

            store.setSelectedTopic(new long[] {first, second}, null);
            check(
                    !Pgn.readHeaderTags(store.load(first).pgn).containsKey("Topic"),
                    "Topic was not cleared");
            try (android.database.Cursor cursor =
                    store.query(new GameFilter().topic("New topic"))) {
                while (cursor.moveToNext()) {
                    long id = cursor.getLong(cursor.getColumnIndexOrThrow(PGNColumns._ID));
                    check(id != first && id != second, "Cleared Topic remains indexed");
                }
            }
            try {
                store.deleteSelected(new long[] {first, Long.MAX_VALUE});
                throw new AssertionError("Missing game deletion succeeded");
            } catch (OperationApplicationException expected) {
                check(store.load(first) != null, "Partial deletion was committed");
            }
            store.deleteSelected(new long[] {first, second});
            check(
                    store.load(first) == null && store.load(second) == null,
                    "Selected games were not deleted");
            check(store.load(unselected) != null, "Unselected game was deleted");
        } finally {
            store.delete(first);
            store.delete(second);
            store.delete(unselected);
        }
    }

    private static void check(boolean condition, String message) {
        if (!condition) {
            throw new AssertionError(message);
        }
    }
}
