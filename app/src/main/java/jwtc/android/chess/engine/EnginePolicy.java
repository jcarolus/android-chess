package jwtc.android.chess.engine;

/** Which Engine a Mode uses; the decision is pure so it can be checked without an Engine. */
public final class EnginePolicy {
    public enum Backend {
        OEX,
        RUST
    }

    private EnginePolicy() {}

    /**
     * Play follows the user's choice, but uses the built-in Rust Engine when no OEX Engine is
     * installed. Duck always uses it because OEX Engines do not support Duck.
     */
    public static Backend forPlay(boolean prefersOex, boolean oexAvailable, boolean duckVariant) {
        return !duckVariant && prefersOex && oexAvailable ? Backend.OEX : Backend.RUST;
    }

    /**
     * Analysis uses an available OEX Engine, otherwise the built-in Rust Engine. Callers report
     * no OEX Engine as available for Duck.
     */
    public static Backend forAnalysis(boolean oexAvailable) {
        return oexAvailable ? Backend.OEX : Backend.RUST;
    }
}
