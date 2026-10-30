package jwtc.android.chess.tools;

import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.ServiceConnection;
import android.os.Bundle;
import android.os.IBinder;
import android.util.Log;
import android.view.Gravity;
import android.view.MenuItem;
import android.view.View;
import android.widget.TextView;
import android.widget.Toast;
import com.google.android.material.progressindicator.LinearProgressIndicator;
import jwtc.android.chess.R;
import jwtc.android.chess.activities.BaseActivity;
import jwtc.android.chess.helpers.ActivityHelper;

public class ImportActivity extends BaseActivity implements ImportListener {

    private ImportService importService = null;
    private TextView _tvWork, _tvWorkCnt, _tvWorkCntFail;
    private LinearProgressIndicator _progress;
    private int _mode = 0;
    private Intent _intent;
    private boolean importStarted;

    protected boolean _processing;

    private final String TAG = "ImportActivity";

    private ServiceConnection mConnection =
            new ServiceConnection() {
                public void onServiceConnected(ComponentName className, IBinder service) {
                    Log.i(TAG, "onServiceConnected");
                    importService = ((ImportService.LocalBinder) service).getService();
                    importService.addListener(ImportActivity.this);
                    // A recreated screen re-attaches to the running job instead of starting over.
                    if (!importStarted) {
                        importStarted = true;
                        importService.startImport(_intent, _mode);
                    }
                }

                public void onServiceDisconnected(ComponentName className) {
                    importService = null;
                    Log.i(TAG, "onServiceDisconnected");
                }
            };

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        setContentView(R.layout.doimport);

        ActivityHelper.fixPaddings(this, findViewById(R.id.root_layout));

        _processing = false;

        _tvWork = findViewById(R.id.TextViewDoImport);
        _tvWorkCnt = findViewById(R.id.TextViewDoImportCnt);
        _tvWorkCntFail = findViewById(R.id.TextViewDoImportCntFail);
        _progress = findViewById(R.id.ProgressDoImport);

        _progress.setVisibility(View.INVISIBLE);

        importStarted =
                savedInstanceState != null && savedInstanceState.getBoolean("importStarted");
    }

    @Override
    protected void onSaveInstanceState(Bundle outState) {
        super.onSaveInstanceState(outState);
        outState.putBoolean("importStarted", importStarted);
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        // Handle presses on the action bar items
        Intent intent;
        switch (item.getItemId()) {
            case android.R.id.home:
                // API 5+ solution
                onBackPressed();
                return true;
            default:
                return super.onOptionsItemSelected(item);
        }
    }

    @Override
    protected void onStart() {
        Log.i(TAG, "onStart");
        super.onStart();

        if (importService == null) {
            // Started as well as bound, so the job outlives this screen being recreated.
            startService(new Intent(this, ImportService.class));
            if (!bindService(
                    new Intent(this, ImportService.class), mConnection, Context.BIND_AUTO_CREATE)) {
                doToast("Could not start import");
            }
        }
    }

    @Override
    protected void onDestroy() {
        Log.i(TAG, "onDestroy");

        if (importService != null) {
            importService.removeListener(this);
        }

        unbindService(mConnection);
        importService = null;
        if (isFinishing()) {
            stopService(new Intent(this, ImportService.class));
        }

        super.onDestroy();
    }

    @Override
    protected void onPause() {
        super.onPause();
    }

    @Override
    protected void onResume() {
        super.onResume();

        if (_processing == false) {
            _intent = getIntent();

            Bundle extras = _intent.getExtras();
            if (extras != null) {
                _mode = extras.getInt("mode");
            } else {
                _mode = ImportService.IMPORT_GAMES;
            }
            // @TODO based on the _mode, set a description
            // _tvWork
        }
    }

    public void doToast(String s) {
        Toast t = Toast.makeText(this, s, Toast.LENGTH_LONG);
        t.setGravity(Gravity.CENTER, 0, 0);
        t.show();
    }

    @Override
    public void OnImportStarted(int mode) {
        _progress.setVisibility(View.VISIBLE);
    }

    @Override
    public void OnImportProgress(int mode, int succeeded, int failed) {
        _tvWorkCnt.setText("" + succeeded);
        _tvWorkCntFail.setText("" + failed);
    }

    @Override
    public void OnImportFinished(int mode) {
        _progress.setVisibility(View.INVISIBLE);
        setResult(RESULT_OK);
        finish();
    }

    @Override
    public void OnImportFatalError(int mode) {
        _progress.setVisibility(View.INVISIBLE);
        _tvWorkCnt.setText("An error occured, import failed");
    }
}
