package jwtc.chess;

/** A PGN text that is not well formed. The offset is the character position of the offending token, or -1 when unknown. */
public final class PgnSyntaxError extends IllegalArgumentException {
    private final int offset;

    PgnSyntaxError(String message, int offset) {
        super(message + " at character " + offset);
        this.offset = offset;
    }

    PgnSyntaxError(String message) {
        super(message);
        this.offset = -1;
    }

    public int getOffset() { return offset; }
}
