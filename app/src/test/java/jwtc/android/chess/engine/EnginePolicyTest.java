package jwtc.android.chess.engine;

import static org.junit.Assert.assertEquals;

import org.junit.Test;

public class EnginePolicyTest {
    @Test
    public void playFollowsPreferenceForOex() {
        assertEquals(EnginePolicy.Backend.OEX, EnginePolicy.forPlay(true, true, false));
    }

    @Test
    public void playUsesRustWhenBuiltinPreferred() {
        assertEquals(EnginePolicy.Backend.RUST, EnginePolicy.forPlay(false, true, false));
    }

    @Test
    public void playFallsBackToRustWithoutOexEngines() {
        assertEquals(EnginePolicy.Backend.RUST, EnginePolicy.forPlay(true, false, false));
    }

    @Test
    public void duckChessAlwaysUsesRust() {
        assertEquals(EnginePolicy.Backend.RUST, EnginePolicy.forPlay(true, true, true));
    }

    @Test
    public void analysisUsesOexWhenAvailable() {
        assertEquals(EnginePolicy.Backend.OEX, EnginePolicy.forAnalysis(true));
    }

    @Test
    public void analysisUsesRustWithoutOex() {
        assertEquals(EnginePolicy.Backend.RUST, EnginePolicy.forAnalysis(false));
    }
}
