package jwtc.android.chess.engine;

import static org.junit.Assert.assertEquals;

import org.junit.Test;

public class EnginePolicyTest {
    @Test
    public void playFollowsPreferenceForOex() {
        assertEquals(EnginePolicy.Backend.OEX, EnginePolicy.forPlay(true, true, false));
    }

    @Test
    public void playUsesLocalWhenBuiltinPreferred() {
        assertEquals(EnginePolicy.Backend.LOCAL, EnginePolicy.forPlay(false, true, false));
    }

    @Test
    public void playFallsBackToLocalWithoutOexEngines() {
        assertEquals(EnginePolicy.Backend.LOCAL, EnginePolicy.forPlay(true, false, false));
    }

    @Test
    public void duckChessAlwaysUsesRust() {
        assertEquals(EnginePolicy.Backend.RUST, EnginePolicy.forPlay(true, true, true));
    }

    @Test
    public void analysisUsesOexWhenAvailable() {
        assertEquals(EnginePolicy.Backend.OEX, EnginePolicy.forAnalysis(true, true));
    }

    @Test
    public void analysisUsesRustForStandardOrChess960WithoutOex() {
        assertEquals(EnginePolicy.Backend.RUST, EnginePolicy.forAnalysis(false, true));
    }

    @Test
    public void analysisFallsBackToLocalForUnsupportedVariantWithoutOex() {
        assertEquals(EnginePolicy.Backend.LOCAL, EnginePolicy.forAnalysis(false, false));
    }

    @Test
    public void analysisPrefersOexForUnsupportedRustVariant() {
        assertEquals(EnginePolicy.Backend.OEX, EnginePolicy.forAnalysis(true, false));
    }
}
