package jwtc.android.chess.tools;

public interface ImportListener {
    void OnImportStarted(int mode);
    void OnImportSuccess(int mode);
    void OnImportFail(int mode);
    void OnImportFinished(int mode);
    void OnImportFatalError(int mode);
}
