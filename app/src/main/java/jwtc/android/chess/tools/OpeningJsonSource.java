package jwtc.android.chess.tools;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.InputStream;
import java.util.Scanner;

/** The named openings of a JSON file: an array of objects with "name" and "moves". */
public final class OpeningJsonSource implements ImportSource {
    private final InputStream in;

    public OpeningJsonSource(InputStream in) {
        this.in = in;
    }

    @Override
    public void forEach(Sink sink) throws Exception {
        String json;
        try (InputStream stream = in) {
            Scanner scanner = new Scanner(stream).useDelimiter("\\A");
            json = scanner.hasNext() ? scanner.next() : "";
        }
        JSONArray array = new JSONArray(json);
        for (int i = 0; i < array.length(); i++) {
            ImportItem item;
            try {
                JSONObject object = array.getJSONObject(i);
                String pgn = "[Event \"Event\"]\n[White \"white\"]\n[Black \"black\"]\n"
                    + object.getString("moves") + "\n\n";
                item = new ImportItem(pgn, object.getString("name"));
            } catch (Exception malformed) {
                continue;
            }
            if (!sink.accept(item)) {
                return;
            }
        }
    }
}
