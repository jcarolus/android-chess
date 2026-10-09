package jwtc.android.chess.tools;

/** One import to run: what is read, and what is done with each item. */
public final class ImportJob {
    /** One of the {@link ImportService} modes, reported back with every event. */
    public final int mode;
    public final ImportSource source;
    public final ImportHandler handler;

    public ImportJob(int mode, ImportSource source, ImportHandler handler) {
        this.mode = mode;
        this.source = source;
        this.handler = handler;
    }
}
