package jwtc.android.chess.tools;

/** What one kind of import does with each item: store it, or reject it. */
public interface ImportHandler {
    /** @return whether the item was imported; false counts as a failed item */
    boolean handle(ImportItem item);
}
