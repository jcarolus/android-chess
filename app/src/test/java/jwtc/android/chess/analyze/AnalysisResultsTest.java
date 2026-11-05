package jwtc.android.chess.analyze;

import static org.junit.Assert.*;

import jwtc.android.chess.engine.EngineEvaluation;
import org.junit.Test;

public class AnalysisResultsTest {
    private static AnalysisResults.Result score(int value) {
        return new AnalysisResults.Result("e2e4", new EngineEvaluation(false, value, 8));
    }

    @Test
    public void equalBoardPlacementsWithDifferentHistoriesRemainDistinct() {
        AnalysisResults results = new AnalysisResults();
        String direct = "position startpos";
        String repeated = "position startpos moves g1f3 g8f6 f3g1 f6g8";
        results.put(direct, score(20));
        results.put(repeated, score(0));
        assertEquals(20, results.get(direct).evaluation.value);
        assertEquals(0, results.get(repeated).evaluation.value);
    }

    @Test
    public void settingsInvalidateButIdenticalSettingsRetainResults() {
        AnalysisResults results = new AnalysisResults();
        results.configure("local", 100, null);
        results.put("history", score(20));
        results.configure("local", 100, null);
        assertNotNull(results.get("history"));
        results.configure("local", 200, null);
        assertNull(results.get("history"));
        results.put("history", score(30));
        results.configure("oex", 200, null);
        assertNull(results.get("history"));
    }

    @Test
    public void restorationRequiresMatchingSettingsAndKeepsUnavailableResults() {
        AnalysisResults source = new AnalysisResults();
        source.configure("local", 100, null);
        source.put("failed", new AnalysisResults.Result((String) null, null));
        source.put("complete", score(40));
        AnalysisResults.Saved saved = source.save();
        source.clear();
        AnalysisResults restored = new AnalysisResults();
        restored.configure("oex", 100, saved);
        assertTrue(restored.save().results.isEmpty());
        restored.configure("local", 200, saved);
        assertTrue(restored.save().results.isEmpty());
        restored.configure("local", 100, saved);
        assertNotNull(restored.get("failed"));
        assertNull(restored.get("failed").evaluation);
        assertEquals(40, restored.get("complete").evaluation.value);
    }

    @Test
    public void cacheEvictsLeastRecentlyUsedAndSnapshotsAreDetached() {
        AnalysisResults results = new AnalysisResults();
        for (int i = 0; i < 128; i++) {
            results.put("path" + i, score(i));
        }
        AnalysisResults.Saved saved = results.save();
        results.get("path0");
        results.put("path128", score(128));
        assertEquals(128, results.save().results.size());
        assertNotNull(results.get("path0"));
        assertNull(results.get("path1"));
        assertTrue(saved.results.containsKey("path1"));
        assertFalse(saved.results.containsKey("path128"));
    }
}
