package jwtc.android.chess.tools;

public interface ImportListener {
    void OnImportStarted(int mode);
    void OnImportProgress(int mode);
    void OnImportFinished(int mode);
    void OnImportFatalError(int mode);
}
