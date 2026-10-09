package jwtc.android.chess.engine;

/** Which Engine a Mode uses; the decision is pure so it can be checked without an Engine. */
public final class EnginePolicy {
    public enum Backend {
        LOCAL,
        OEX
    }

    private EnginePolicy() {}

    /**
     * Play follows the user's choice, but falls back to the Local Engine when no OEX Engine is
     * installed, and always uses it for Duck chess, which OEX Engines do not support.
     */
    public static Backend forPlay(boolean prefersOex, boolean oexAvailable, boolean duckVariant) {
        return prefersOex && oexAvailable && !duckVariant ? Backend.OEX : Backend.LOCAL;
    }
}
