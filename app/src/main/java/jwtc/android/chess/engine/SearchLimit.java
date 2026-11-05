package jwtc.android.chess.engine;

/** How far one search may go: a fixed depth in ply, or a time budget in milliseconds. */
public final class SearchLimit {
    private final int ply;
    private final int millis;

    private SearchLimit(int ply, int millis) {
        this.ply = ply;
        this.millis = millis;
    }

    public static SearchLimit ply(int ply) {
        return new SearchLimit(ply, 0);
    }

    public static SearchLimit millis(int millis) {
        return new SearchLimit(0, millis);
    }

    void applyTo(EngineApi engine) {
        if (ply > 0) {
            engine.setPly(ply);
        } else {
            engine.setMsecs(millis);
        }
    }
}
