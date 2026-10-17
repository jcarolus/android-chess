package jwtc.android.chess.engine;

/** Which Engine a Mode uses; the decision is pure so it can be checked without an Engine. */
public final class EnginePolicy {
    public enum Backend {
        LOCAL,
        OEX,
        RUST
    }

    private EnginePolicy() {}

    /**
     * Play follows the user's choice, but falls back to the Local Engine when no OEX Engine is
     * installed, and always uses it for Duck chess, which OEX Engines do not support.
     */
    public static Backend forPlay(boolean prefersOex, boolean oexAvailable, boolean duckVariant) {
        return prefersOex && oexAvailable && !duckVariant ? Backend.OEX : Backend.LOCAL;
    }

    /**
     * Analysis uses an available OEX Engine unless the built-in Engine was picked or none is
     * installed. The built-in choice is the Rust Engine for Standard and Chess 960 games; Duck,
     * which Rust does not yet support, falls back to the Local (C++) Engine.
     */
    public static Backend forAnalysis(boolean oexAvailable, boolean rustEligibleVariant) {
        if (oexAvailable) return Backend.OEX;
        return rustEligibleVariant ? Backend.RUST : Backend.LOCAL;
    }
}
