package jwtc.android.chess.tools;

public interface ImportListener {
    void OnImportStarted(int mode);

    void OnImportProgress(int mode, int succeeded, int failed, int duplicates, int updated);

    void OnImportFinished(int mode);

    void OnImportFatalError(int mode);

    /** A new request was rejected while the accepted job continues. */
    default void OnImportRejected(int mode) {}

    default void onSelectionRequired(ImportService.SelectionPrompt prompt) {}

    default void onTopicRequired(ImportService.TopicPrompt prompt) {}
}
