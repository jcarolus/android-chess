package jwtc.android.chess.tools;

/** Reads the items out of one import file, in order. */
public interface ImportSource {
    /** Receives the items of a source; returns false to ask the source to stop reading. */
    interface Sink {
        boolean accept(ImportItem item);
    }

    /** Reads until the file ends or the sink returns false, then closes the underlying stream. */
    void forEach(Sink sink) throws Exception;
}
