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
     * installed. Duck and King of the Hill always use it.
     */
    public static Backend forPlay(boolean prefersOex, boolean oexAvailable, boolean requiresBuiltinEngine) {
        return !requiresBuiltinEngine && prefersOex && oexAvailable ? Backend.OEX : Backend.RUST;
    }

    /**
     * Analysis uses an available OEX Engine, otherwise the built-in Rust Engine. Callers report
     * no OEX Engine as available for Duck.
     */
    public static Backend forAnalysis(boolean oexAvailable) {
        return oexAvailable ? Backend.OEX : Backend.RUST;
    }
}
