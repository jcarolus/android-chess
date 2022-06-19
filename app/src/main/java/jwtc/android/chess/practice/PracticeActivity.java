package jwtc.android.chess.practice;

import android.os.Bundle;
import android.util.Log;

import jwtc.android.chess.R;
import jwtc.android.chess.activities.ChessBoardActivity;
import jwtc.android.chess.tools.ImportListener;

public class PracticeActivity extends ChessBoardActivity implements ImportListener {
    private static final String TAG = "PracticeActivity";

    @Override
    public boolean requestMove(int from, int to) {
        return false;
    }

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.practice);

        gameApi = new PracticeApi();

        afterCreate();
    }

    @Override
    protected void onResume() {
        Log.i(TAG, "onResume");

        loadPuzzles();

        super.onResume();
    }

    protected void loadPuzzles() {

    }

    @Override
    public void OnImportStarted(int mode) {

    }

    @Override
    public void OnImportSuccess(int mode) {

    }

    @Override
    public void OnImportFail(int mode) {

    }

    @Override
    public void OnImportFinished(int mode) {

    }

    @Override
    public void OnImportFatalError(int mode) {

    }
}
