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
    public void duckChessAlwaysUsesLocal() {
        assertEquals(EnginePolicy.Backend.LOCAL, EnginePolicy.forPlay(true, true, true));
    }
}
