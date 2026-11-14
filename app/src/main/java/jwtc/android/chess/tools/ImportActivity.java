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
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;
import androidx.appcompat.app.AlertDialog;
import com.google.android.material.progressindicator.LinearProgressIndicator;
import jwtc.android.chess.R;
import jwtc.android.chess.activities.BaseActivity;
import jwtc.android.chess.helpers.ActivityHelper;
import jwtc.android.chess.helpers.ChessAlertDialogBuilder;

public class ImportActivity extends BaseActivity implements ImportListener {

    private ImportService importService = null;
    private TextView _tvWork, _tvWorkCnt, _tvWorkCntFail;
    private LinearProgressIndicator _progress;
    private int _mode = 0;
    private Intent _intent;
    private boolean importStarted;
    private AlertDialog studyDialog;
    private EditText studyTitle, studyTopic;
    private String titleDraft, topicDraft;

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
        if (savedInstanceState != null) {
            titleDraft = savedInstanceState.getString("studyTitle");
            topicDraft = savedInstanceState.getString("studyTopic");
        }
        _intent = getIntent();
        _mode = _intent.getIntExtra("mode", ImportService.IMPORT_GAMES);
    }

    @Override
    protected void onSaveInstanceState(Bundle outState) {
        super.onSaveInstanceState(outState);
        outState.putBoolean("importStarted", importStarted);
        if (studyDialog != null) {
            outState.putString("studyTitle", studyTitle.getText().toString());
            outState.putString("studyTopic", studyTopic.getText().toString());
        }
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
                doToast(getString(R.string.import_start_failed));
            }
        }
    }

    @Override
    protected void onDestroy() {
        Log.i(TAG, "onDestroy");

        if (studyDialog != null) {
            studyDialog.dismiss();
        }

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
            switch (_mode) {
                case ImportService.IMPORT_GAMES:
                    _tvWork.setText(R.string.importing_games);
                    break;
                case ImportService.IMPORT_PUZZLES:
                    _tvWork.setText(R.string.importing_puzzles);
                    break;
                case ImportService.IMPORT_PRACTICE:
                    _tvWork.setText(R.string.importing_practice_positions);
                    break;
                case ImportService.IMPORT_STUDIES:
                    _tvWork.setText(R.string.importing_studies);
                    break;
                case ImportService.IMPORT_OPENINGS:
                    _tvWork.setText(R.string.importing_openings);
                    break;
            }
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
    public void OnImportRejected(int mode) {
        doToast(getString(R.string.pgntool_import_busy));
    }

    @Override
    public void OnImportFatalError(int mode) {
        _progress.setVisibility(View.INVISIBLE);
        _tvWorkCnt.setText(R.string.import_failed);
    }

    @Override
    public void onStudyMetadataRequired(ImportService.StudyPrompt prompt) {
        if (isFinishing() || studyDialog != null) {
            return;
        }
        LinearLayout fields = new LinearLayout(this);
        fields.setOrientation(LinearLayout.VERTICAL);
        int padding = getResources().getDimensionPixelSize(R.dimen.screen_padding);
        fields.setPadding(padding, 0, padding, 0);
        studyTitle = addStudyField(fields, R.string.study_name, titleDraft, prompt.defaults.needsTitle);
        studyTopic = addStudyField(fields, R.string.study_topic, topicDraft, prompt.defaults.needsTopic);
        titleDraft = topicDraft = null;
        studyDialog = new ChessAlertDialogBuilder(this)
                .setTitle(R.string.study_import_metadata)
                .setMessage(prompt.filename)
                .setView(fields)
                .setPositiveButton(R.string.button_ok, null)
                .setNegativeButton(R.string.button_cancel, (dialog, which) -> cancelStudyImport(prompt))
                .setOnCancelListener(dialog -> cancelStudyImport(prompt))
                .create();
        studyDialog.show();
        studyDialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(view -> {
            String title = studyTitle.getText().toString().trim();
            String topic = studyTopic.getText().toString().trim();
            boolean valid = validateStudyField(studyTitle, title) & validateStudyField(studyTopic, topic);
            if (valid && importService != null) {
                studyDialog.dismiss();
                studyDialog = null;
                importService.answerStudyPrompt(prompt, title, topic, true);
            }
        });
    }

    private EditText addStudyField(LinearLayout fields, int hint, String draft, boolean visible) {
        EditText field = new EditText(this);
        field.setSingleLine(true);
        field.setHint(hint);
        field.setText(draft);
        if (visible) {
            fields.addView(field);
        }
        return field;
    }

    private boolean validateStudyField(EditText field, String text) {
        if (field.getParent() != null && (text.isEmpty() || text.equals("?"))) {
            field.setError(getString(R.string.study_import_value_required));
            return false;
        }
        return true;
    }

    private void cancelStudyImport(ImportService.StudyPrompt prompt) {
        finish();
        if (importService != null) {
            importService.answerStudyPrompt(prompt, null, null, false);
        }
    }
}
