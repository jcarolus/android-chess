package jwtc.android.chess.lichess;


import android.util.Log;

import com.google.gson.Gson;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import okhttp3.Call;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;

public class NdJsonStream {
    private static final String TAG = "lichess.NdJsonStream";

    public interface Handler {
        void onResponse(JsonObject jsonObject);

        void onClose(boolean success);
    }

    public static class Stream {
        private final Call call;

        Stream(Call call) {
            this.call = call;
        }

        public void close() {
            call.cancel();
        }
    }

    private static final ExecutorService executor = Executors.newCachedThreadPool();
    private static final Gson gson = new Gson();

    public static Stream readStream(String name, OkHttpClient client, Request request, Handler handler) {
        Call call = client.newCall(request);
        Stream stream = new Stream(call);

        executor.submit(() -> {
            try (Response response = call.execute()) {
                if (!response.isSuccessful()) {
                    throw new IOException("Unexpected code " + response);
                }

                InputStream inputStream = response.body().byteStream();
                BufferedReader reader = new BufferedReader(new InputStreamReader(inputStream, StandardCharsets.UTF_8));

                String line;
                while ((line = reader.readLine()) != null) {
                    line = line.trim();
                    if (!line.isEmpty()) {
                        Log.d(TAG, "line: " + line);
                        try {
                            JsonObject jsonObject = JsonParser.parseString(line).getAsJsonObject();
                            handler.onResponse(jsonObject);
                        } catch (Exception e) {
                            Log.d(TAG, name + "Invalid JSON: " + line + " " + e);
                        }
                    }
                }
                handler.onClose(true);
            } catch (IOException e) {
                Log.d(TAG, "Exception " + e);
                handler.onClose(false);
            }
        });

        return stream;
    }
}
