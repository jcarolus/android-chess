package jwtc.android.chess.tools;

/** Reads the items out of one import file, in order. */
public interface ImportSource extends java.io.Closeable {
    /** Receives the items of a source; returns false to ask the source to stop reading. */
    interface Sink {
        boolean accept(ImportItem item);
    }

    /** Reads until the file ends or the sink returns false, then closes the underlying stream. */
    void forEach(Sink sink) throws Exception;

    /** Closes the underlying stream of a source that was never read; closing a read source does nothing more. */
    @Override
    void close() throws java.io.IOException;
}
