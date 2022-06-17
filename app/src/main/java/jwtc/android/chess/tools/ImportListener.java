package jwtc.android.chess.tools;

public interface ImportListener {
    void OnImportSuccess();
    void OnImportFail();
    void OnImportFinished();
    void OnImportFatalError();
}
