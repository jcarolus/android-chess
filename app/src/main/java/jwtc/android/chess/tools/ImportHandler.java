package jwtc.android.chess.tools;

/** What one kind of import does with each item. */
public interface ImportHandler {
    enum Result {
        INSERTED,
        FAILED,
        DUPLICATE,
        UPDATED
    }

    Result handle(ImportItem item);
}
