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
     * installed. Duck uses Rust because OEX Engines do not support it.
     */
    public static Backend forPlay(boolean prefersOex, boolean oexAvailable, boolean duckVariant) {
        if (duckVariant) return Backend.RUST;
        return prefersOex && oexAvailable ? Backend.OEX : Backend.LOCAL;
    }

    /**
     * Analysis uses an available OEX Engine unless the built-in Engine was picked or none is
     * installed. The built-in choice is Rust for supported variants. Callers exclude OEX for Duck.
     */
    public static Backend forAnalysis(boolean oexAvailable, boolean rustEligibleVariant) {
        if (oexAvailable) return Backend.OEX;
        return rustEligibleVariant ? Backend.RUST : Backend.LOCAL;
    }
}
