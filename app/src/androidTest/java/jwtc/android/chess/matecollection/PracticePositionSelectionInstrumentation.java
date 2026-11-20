package jwtc.android.chess.matecollection;

import android.app.Instrumentation;
import android.content.ContentUris;
import android.content.ContentValues;
import android.content.Context;
import android.content.SharedPreferences;
import android.database.Cursor;
import android.net.Uri;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import jwtc.android.chess.puzzle.MyPuzzleProvider;
import jwtc.android.chess.services.GameApi;
import jwtc.chess.board.BoardPosition;

/** Exercises Practice preparation with the real board, engine, and collection. */
public final class PracticePositionSelectionInstrumentation {
    private static final String FOOLS_MATE = "1. f3 e5 2. g4 Qh4# 0-1";

    public static void verify(Instrumentation instrumentation) throws InterruptedException {
        checkStart("1. e4 e5 *", -1);
        checkStart("[FEN \"1R2k3/R7/8/8/8/8/8/4K3 b - - 1 1\"]\n1-0", -1);
        checkStart("[FEN \"4k3/R7/1R6/8/8/8/8/4K3 w - - 0 1\"]\n1. Rb8# 1-0", 0);
        checkStart(FOOLS_MATE, 3);
        checkStart("[FEN \"8/7B/5Q2/6p1/6k1/8/5K2/8 w - - 0 1\"]\n1. Qh8 Kh5 2. Bf5# 1-0", 0);

        verifyPlayback(instrumentation, false, 0);
        verifyPlayback(instrumentation, true, 3);
    }

    private static void checkStart(String pgn, int expectedPly) {
        int actual = PracticePositionSelector.startPly(pgn);
        check(
                actual == expectedPly,
                "Expected ply " + expectedPly + ", got " + actual + " for " + pgn);
    }

    private static void verifyPlayback(
            Instrumentation instrumentation, boolean fullGame, int expectedPly)
            throws InterruptedException {
        Context context = instrumentation.getTargetContext();
        SharedPreferences prefs = context.getSharedPreferences("ChessPlayer", Context.MODE_PRIVATE);
        String[] keys = {
            MateKind.PRACTICE_POSITIONS.positionKey,
            MateKind.PRACTICE_POSITIONS.playedKey,
            MateKind.PRACTICE_POSITIONS.solvedKey
        };
        Map<String, ?> previous = prefs.getAll();
        ContentValues values = new ContentValues();
        String fen = BoardPosition.START_FEN.replace("0 1", fullGame ? "0 4231" : "0 4232");
        values.put(MyPuzzleProvider.COL_PGN, "[FEN \"" + fen + "\"]\n" + FOOLS_MATE);
        values.put(MyPuzzleProvider.COL_FULL_GAME, fullGame ? 1 : 0);
        Uri row =
                context.getContentResolver().insert(MyPuzzleProvider.CONTENT_URI_PRACTICES, values);
        check(row != null, "Could not insert a Practice fixture");
        CountDownLatch opened = new CountDownLatch(1);
        int[] preparing = {0};
        int[] started = {0};
        GameApi game = new GameApi();
        MateCollection collection =
                new MateCollection(
                        context,
                        MateKind.PRACTICE_POSITIONS,
                        game,
                        new MateCollection.Listener() {
                            @Override
                            public void onPositionPreparing() {
                                preparing[0]++;
                            }

                            @Override
                            public void onPositionStarted(int position, int total, int usersColor) {
                                started[0]++;
                                opened.countDown();
                            }

                            @Override
                            public void onEmptyCollection() {}

                            @Override
                            public void onScore(MateScore score) {}

                            @Override
                            public void onOutcome(MateCollection.Outcome outcome, String move) {}

                            @Override
                            public void onEndOfCollection() {}
                        });
        try {
            int index = 0;
            try (Cursor rows =
                    context.getContentResolver()
                            .query(
                                    MyPuzzleProvider.CONTENT_URI_PRACTICES,
                                    new String[] {MyPuzzleProvider.COL_ID},
                                    null,
                                    null,
                                    null)) {
                check(rows != null, "Missing Practice cursor");
                while (rows.moveToNext() && rows.getLong(0) != ContentUris.parseId(row)) {
                    index++;
                }
                check(!rows.isAfterLast(), "Missing Practice fixture");
            }
            prefs.edit().putInt(keys[0], index).commit();
            instrumentation.runOnMainSync(collection::resume);
            check(opened.await(10, TimeUnit.SECONDS), "Practice exercise did not open");
            instrumentation.runOnMainSync(
                    () -> {
                        check(
                                game.getCurrentPly() == expectedPly,
                                "Practice opened at the wrong ply");
                        collection.retry();
                        check(
                                game.getCurrentPly() == expectedPly && started[0] == 2,
                                "Retry changed the starting ply");
                        check(
                                preparing[0] == (fullGame ? 1 : 0),
                                "Practice unexpectedly repeated preparation");
                    });
        } finally {
            instrumentation.runOnMainSync(collection::pause);
            context.getContentResolver().delete(row, null, null);
            SharedPreferences.Editor editor = prefs.edit();
            for (String key : keys) {
                if (previous.containsKey(key)) {
                    editor.putInt(key, (Integer) previous.get(key));
                } else {
                    editor.remove(key);
                }
            }
            editor.commit();
        }
    }

    private static void check(boolean condition, String message) {
        if (!condition) {
            throw new AssertionError(message);
        }
    }
}
