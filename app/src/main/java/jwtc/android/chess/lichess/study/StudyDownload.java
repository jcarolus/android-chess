package jwtc.android.chess.lichess.study;

import android.os.Handler;
import android.os.Looper;
import android.os.SystemClock;
import android.util.Log;
import android.util.LruCache;
import java.io.IOException;
import java.io.Reader;
import java.io.StringReader;
import java.util.List;
import java.util.function.Consumer;
import jwtc.chess.Pgn;
import okhttp3.Call;
import okhttp3.Callback;
import okhttp3.HttpUrl;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;

final class StudyDownload {
    private static final int MAX_STUDY_SIZE = 4 * Pgn.MAX_GAME_SIZE;
    private static final OkHttpClient CLIENT = new OkHttpClient();
    private static final Handler MAIN = new Handler(Looper.getMainLooper());
    private static final LruCache<String, List<StudyPgn.Chapter>> STUDIES = new LruCache<>(3);
    private static volatile long retryAfter;

    static Call chapters(
            String studyId, boolean broadcast, Consumer<List<StudyPgn.Chapter>> result) {
        return download(
                pgnUrl(broadcast ? "api/broadcast" : "api/study", studyId + ".pgn").build(),
                reader ->
                        StudyPgn.chapters(
                                studyId,
                                broadcast,
                                new StringReader(readPgn(reader, MAX_STUDY_SIZE))),
                chapters -> {
                    if (chapters != null) {
                        STUDIES.put(studyId, chapters);
                    }
                    result.accept(chapters);
                });
    }

    static List<StudyPgn.Chapter> cachedChapters(String studyId) {
        return STUDIES.get(studyId);
    }

    static boolean isRateLimited() {
        return SystemClock.elapsedRealtime() < retryAfter;
    }

    static Call chapter(String studyId, String chapterId, Consumer<String> result) {
        return download(
                pgnUrl("api/study", studyId).addPathSegment(chapterId + ".pgn").build(),
                reader -> readPgn(reader, Pgn.MAX_GAME_SIZE),
                result);
    }

    private static String readPgn(Reader reader, int maxChars) throws IOException {
        StringBuilder pgn = new StringBuilder();
        char[] buffer = new char[8192];
        int count;
        while ((count = reader.read(buffer)) != -1) {
            if (pgn.length() + count > maxChars) {
                throw new IOException("Study export exceeds PGN size limit");
            }
            pgn.append(buffer, 0, count);
        }
        return pgn.toString();
    }

    private static HttpUrl.Builder pgnUrl(String endpoint, String studyId) {
        return new HttpUrl.Builder()
                .scheme("https")
                .host("lichess.org")
                .addPathSegments(endpoint)
                .addPathSegment(studyId)
                .addQueryParameter("clocks", "false")
                .addQueryParameter("comments", "true")
                .addQueryParameter("variations", "true")
                .addQueryParameter("orientation", "true");
    }

    private interface ReadPgn<T> {
        T read(Reader reader) throws IOException;
    }

    private static <T> Call download(HttpUrl url, ReadPgn<T> parser, Consumer<T> result) {
        Call request =
                CLIENT.newCall(
                        new Request.Builder()
                                .url(url)
                                .header("Accept", "application/x-chess-pgn")
                                .build());
        if (isRateLimited()) {
            MAIN.post(
                    () -> {
                        if (!request.isCanceled()) {
                            result.accept(null);
                        }
                    });
            return request;
        }
        request.enqueue(
                new Callback() {
                    @Override
                    public void onFailure(Call call, IOException error) {
                        if (!call.isCanceled()) {
                            Log.w("StudyDownload", "Could not load study PGN", error);
                            deliver(call, null);
                        }
                    }

                    @Override
                    public void onResponse(Call call, Response response) {
                        try (Response reply = response) {
                            if (reply.code() == 429) {
                                retryAfter = SystemClock.elapsedRealtime() + 60_000;
                            }
                            if (!reply.isSuccessful()) {
                                throw new IOException("Study export returned HTTP " + reply.code());
                            }
                            deliver(call, parser.read(reply.body().charStream()));
                        } catch (IOException | IllegalArgumentException error) {
                            onFailure(call, new IOException(error));
                        }
                    }

                    private void deliver(Call call, T value) {
                        MAIN.post(
                                () -> {
                                    if (!call.isCanceled()) {
                                        result.accept(value);
                                    }
                                });
                    }
                });
        return request;
    }

    private StudyDownload() {}
}
