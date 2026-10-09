package jwtc.chess;

import java.io.IOException;
import java.io.Reader;

/**
 * Cuts the text of a file holding many games into single games, one at a time. A game runs from one
 * <code>[Event "</code> tag to the next, and text before the first such tag is skipped.
 */
public final class PgnSplitter {
    private static final String START = "[Event \"";
    private static final int CHUNK = 8192;

    private final Reader reader;
    private final StringBuilder buffer = new StringBuilder();
    private final char[] chunk = new char[CHUNK];
    private boolean eof;

    public PgnSplitter(Reader reader) {
        this.reader = reader;
    }

    /** The next game as written in the file, or null when there are no more. */
    public String next() throws IOException {
        int start = buffer.indexOf(START);
        while (start < 0) {
            if (eof) {
                buffer.setLength(0);
                return null;
            }
            // keep only what could be the beginning of a start marker split across two reads
            buffer.delete(0, Math.max(0, buffer.length() - (START.length() - 1)));
            fill();
            start = buffer.indexOf(START);
        }
        buffer.delete(0, start);

        int from = 1;
        int end = buffer.indexOf(START, from);
        while (end < 0 && !eof) {
            from = Math.max(1, buffer.length() - (START.length() - 1));
            fill();
            end = buffer.indexOf(START, from);
        }
        int length = end < 0 ? buffer.length() : end;
        String game = buffer.substring(0, length);
        buffer.delete(0, length);
        return game;
    }

    private void fill() throws IOException {
        int read = reader.read(chunk);
        if (read < 0) eof = true;
        else buffer.append(chunk, 0, read);
    }
}
