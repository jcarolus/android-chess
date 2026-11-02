package jwtc.android.chess.analyze;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;

/** Completed Analysis results, including unavailable results, scoped to Engine settings. */
final class AnalysisResults {
    private final LinkedHashMap<String, Analysis.Result> entries =
            new LinkedHashMap<String, Analysis.Result>(128, 0.75f, true) {
                @Override
                protected boolean removeEldestEntry(Map.Entry<String, Analysis.Result> eldest) {
                    return size() > 128;
                }
            };
    private String engine;
    private int millis;

    void configure(String engine, int millis) {
        if (!Objects.equals(this.engine, engine) || this.millis != millis) {
            clear();
        }
        this.engine = engine;
        this.millis = millis;
    }

    void restore(String engine, int millis, Analysis.Saved saved) {
        if (Objects.equals(engine, saved.engine) && millis == saved.millis) {
            for (Map.Entry<String, Analysis.Result> entry : saved.results.entrySet()) {
                put(entry.getKey(), entry.getValue());
            }
        }
    }

    Analysis.Result get(String history) {
        return entries.get(history);
    }

    void put(String history, Analysis.Result result) {
        entries.put(history, result);
    }

    void clear() {
        entries.clear();
    }

    Map<String, Analysis.Result> snapshot() {
        return new LinkedHashMap<>(entries);
    }
}
