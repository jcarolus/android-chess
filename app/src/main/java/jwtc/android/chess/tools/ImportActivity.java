package jwtc.android.chess.tools;

import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.ServiceConnection;
import android.os.Bundle;
import android.os.IBinder;
import android.text.InputType;
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
import jwtc.chess.Pgn;

public class ImportActivity extends BaseActivity implements ImportListener {

    private static final String PREF_LAST_STUDY_IMPORT_TOPIC = "lastStudyImportTopic";

    private static final String PREF_LAST_GAME_IMPORT_TOPIC = "lastGameImportTopic";

    private ImportService importService = null;
    private TextView _tvWork, _tvWorkCnt, _tvWorkCntFail, _tvWorkCntDuplicates;
    private LinearProgressIndicator _progress;
    private int _mode = 0;
    private Intent _intent;
    private boolean importStarted;
    private AlertDialog topicDialog;
    private EditText topicField;
    private String topicDraft;
    private AlertDialog selectionDialog;
    private EditText rangeFrom, rangeUntil;
    private boolean selectingRange;
    private String fromDraft, untilDraft;

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
        _tvWorkCntDuplicates = findViewById(R.id.TextViewDoImportCntDuplicates);
        _progress = findViewById(R.id.ProgressDoImport);
        findViewById(R.id.ButtonClose).setOnClickListener(view -> finish());

        _progress.setVisibility(View.INVISIBLE);

        importStarted =
                savedInstanceState != null && savedInstanceState.getBoolean("importStarted");
        if (savedInstanceState != null) {
            topicDraft = savedInstanceState.getString("importTopic");
            selectingRange = savedInstanceState.getBoolean("selectingRange");
            fromDraft = savedInstanceState.getString("rangeFrom");
            untilDraft = savedInstanceState.getString("rangeUntil");
        }
        _intent = getIntent();
        _mode = _intent.getIntExtra("mode", ImportService.IMPORT_GAMES);
        boolean tracksDuplicates =
                _mode == ImportService.IMPORT_GAMES
                        || _mode == ImportService.IMPORT_PRACTICE
                        || _mode == ImportService.IMPORT_PUZZLES
                        || _mode == ImportService.IMPORT_STUDIES;
        findViewById(R.id.ImportDuplicatesRow)
                .setVisibility(tracksDuplicates ? View.VISIBLE : View.GONE);
    }

    @Override
    protected void onSaveInstanceState(Bundle outState) {
        super.onSaveInstanceState(outState);
        outState.putBoolean("importStarted", importStarted);
        outState.putBoolean("selectingRange", selectingRange);
        if (selectingRange && rangeFrom != null) {
            outState.putString("rangeFrom", rangeFrom.getText().toString());
            outState.putString("rangeUntil", rangeUntil.getText().toString());
        }
        if (topicDialog != null) {
            outState.putString("importTopic", topicField.getText().toString());
        }
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        Intent intent;
        switch (item.getItemId()) {
            case android.R.id.home:
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

        if (topicDialog != null) {
            topicDialog.dismiss();
        }
        if (selectionDialog != null) {
            selectionDialog.dismiss();
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
        _tvWorkCntDuplicates.setText(String.valueOf(0));
        _progress.setVisibility(View.VISIBLE);
        findViewById(R.id.ButtonClose).setVisibility(View.GONE);
    }

    @Override
    public void OnImportProgress(int mode, int succeeded, int failed, int duplicates, int updated) {
        _tvWorkCnt.setText(String.valueOf(succeeded));
        _tvWorkCntFail.setText(String.valueOf(failed));
        _tvWorkCntDuplicates.setText(String.valueOf(duplicates));
    }

    @Override
    public void OnImportFinished(int mode) {
        _progress.setVisibility(View.INVISIBLE);
        setResult(RESULT_OK);
        findViewById(R.id.ButtonClose).setVisibility(View.VISIBLE);
    }

    @Override
    public void OnImportRejected(int mode) {
        doToast(getString(R.string.pgntool_import_busy));
    }

    @Override
    public void OnImportFatalError(int mode) {
        _progress.setVisibility(View.INVISIBLE);
        _tvWorkCnt.setText(R.string.import_failed);
        findViewById(R.id.ButtonClose).setVisibility(View.VISIBLE);
    }

    @Override
    public void onSelectionRequired(ImportService.SelectionPrompt prompt) {
        if (isFinishing() || selectionDialog != null) {
            return;
        }
        if (selectingRange && prompt.count > 0) {
            showRange(prompt);
            return;
        }
        ChessAlertDialogBuilder builder = new ChessAlertDialogBuilder(this);
        builder.setTitle(prompt.filename);
        if (prompt.count == 0) {
            builder.setMessage(R.string.import_no_games)
                    .setPositiveButton(
                            R.string.button_close,
                            (dialog, which) -> answerSelection(prompt, null));
        } else {
            builder.setMessage(getString(R.string.import_game_estimate, prompt.count))
                    .setPositiveButton(
                            R.string.import_all,
                            (dialog, which) ->
                                    answerSelection(prompt, new TopicImport.Range(1, prompt.count)))
                    .setNeutralButton(
                            R.string.import_subsection,
                            (dialog, which) -> {
                                selectionDialog = null;
                                selectingRange = true;
                                showRange(prompt);
                            })
                    .setNegativeButton(
                            R.string.button_cancel,
                            (dialog, which) -> answerSelection(prompt, null));
        }
        builder.setOnCancelListener(dialog -> answerSelection(prompt, null));
        selectionDialog = builder.create();
        selectionDialog.show();
    }

    private void showRange(ImportService.SelectionPrompt prompt) {
        LinearLayout fields = new LinearLayout(this);
        fields.setOrientation(LinearLayout.VERTICAL);
        int padding = getResources().getDimensionPixelSize(R.dimen.screen_padding);
        fields.setPadding(padding, 0, padding, 0);
        rangeFrom =
                rangeField(fields, R.string.import_games_from, fromDraft == null ? "1" : fromDraft);
        rangeUntil =
                rangeField(
                        fields,
                        R.string.import_games_until,
                        untilDraft == null ? String.valueOf(prompt.count) : untilDraft);
        fromDraft = untilDraft = null;
        selectionDialog =
                new ChessAlertDialogBuilder(this)
                        .setTitle(prompt.filename)
                        .setMessage(getString(R.string.import_game_estimate, prompt.count))
                        .setView(fields)
                        .setPositiveButton(R.string.import_continue, null)
                        .setNegativeButton(
                                R.string.import_back,
                                (dialog, which) -> {
                                    selectionDialog = null;
                                    selectingRange = false;
                                    onSelectionRequired(prompt);
                                })
                        .setOnCancelListener(dialog -> answerSelection(prompt, null))
                        .create();
        selectionDialog.show();
        selectionDialog
                .getButton(AlertDialog.BUTTON_POSITIVE)
                .setOnClickListener(
                        view -> {
                            Integer from = rangeNumber(rangeFrom, prompt.count);
                            Integer until = rangeNumber(rangeUntil, prompt.count);
                            if (from == null || until == null) {
                                return;
                            }
                            if (until < from) {
                                rangeUntil.setError(getString(R.string.import_range_order));
                                return;
                            }
                            selectionDialog.dismiss();
                            answerSelection(prompt, new TopicImport.Range(from, until));
                        });
    }

    private EditText rangeField(LinearLayout fields, int label, String value) {
        TextView title = new TextView(this);
        title.setText(label);
        fields.addView(title);
        EditText field = new EditText(this);
        field.setInputType(InputType.TYPE_CLASS_NUMBER);
        field.setSingleLine(true);
        field.setHint(label);
        field.setText(value);
        fields.addView(field);
        title.setLabelFor(View.generateViewId());
        field.setId(title.getLabelFor());
        return field;
    }

    private Integer rangeNumber(EditText field, int count) {
        try {
            int value = Integer.parseInt(field.getText().toString());
            if (value >= 1 && value <= count) {
                field.setError(null);
                return value;
            }
        } catch (NumberFormatException ignored) {
        }
        field.setError(getString(R.string.import_range_bounds, count));
        return null;
    }

    private void answerSelection(ImportService.SelectionPrompt prompt, TopicImport.Range range) {
        selectionDialog = null;
        selectingRange = false;
        fromDraft = untilDraft = null;
        rangeFrom = rangeUntil = null;
        if (importService != null) {
            importService.answerSelectionPrompt(prompt, range);
        }
    }

    @Override
    public void onTopicRequired(ImportService.TopicPrompt prompt) {
        if (isFinishing() || topicDialog != null) {
            return;
        }
        LinearLayout fields = new LinearLayout(this);
        fields.setOrientation(LinearLayout.VERTICAL);
        int padding = getResources().getDimensionPixelSize(R.dimen.screen_padding);
        fields.setPadding(padding, 0, padding, 0);
        topicField = new EditText(this);
        topicField.setSingleLine(true);
        topicField.setHint(R.string.study_topic);
        String topicPreference =
                _mode == ImportService.IMPORT_STUDIES
                        ? PREF_LAST_STUDY_IMPORT_TOPIC
                        : PREF_LAST_GAME_IMPORT_TOPIC;
        if (topicDraft == null) {
            topicDraft = getPrefs().getString(topicPreference, null);
        }
        topicField.setText(topicDraft);
        fields.addView(topicField);
        topicDraft = null;
        topicDialog =
                new ChessAlertDialogBuilder(this)
                        .setTitle(R.string.study_import_topic)
                        .setMessage(prompt.filename)
                        .setView(fields)
                        .setPositiveButton(R.string.button_ok, null)
                        .setNegativeButton(
                                R.string.button_cancel,
                                (dialog, which) -> cancelTopicImport(prompt))
                        .setOnCancelListener(dialog -> cancelTopicImport(prompt))
                        .create();
        topicDialog.show();
        topicDialog
                .getButton(AlertDialog.BUTTON_POSITIVE)
                .setOnClickListener(
                        view -> {
                            String topic = Pgn.optionalTagValue(topicField.getText().toString());
                            if (topic == null) {
                                topicField.setError(
                                        getString(R.string.study_import_value_required));
                            } else if (importService != null) {
                                getPrefs().edit().putString(topicPreference, topic).apply();
                                topicDialog.dismiss();
                                topicDialog = null;
                                importService.answerTopicPrompt(prompt, topic, true);
                            }
                        });
    }

    private void cancelTopicImport(ImportService.TopicPrompt prompt) {
        topicDialog = null;
        if (importService != null) {
            importService.answerTopicPrompt(prompt, null, false);
        }
    }
}
