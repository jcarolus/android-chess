package jwtc.android.chess;

import jwtc.chess.GameControl;

import android.app.AlertDialog;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.text.InputType;
import android.view.View;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.CompoundButton;
import android.widget.EditText;
import android.widget.RadioButton;
import android.widget.Spinner;
import android.widget.CompoundButton.OnCheckedChangeListener;
import android.widget.TableRow;

public class options extends MyBaseActivity {

    public static final String TAG = "options";

    public static final int RESULT_960 = 1;

    private CheckBox _checkAutoFlip, _checkMoves, _check960;
    private Spinner _spinLevel, _spinLevelPly;
    private Button _butCancel, _butOk;
    private RadioButton _radioTime, _radioPly, _radioWhite, _radioBlack, _radioAndroid, _radioHuman;
    private TableRow _tableRowOption960;

    private static boolean _sbFlipTopPieces, _sbPlayAsBlack;

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        setContentView(R.layout.options);

        this.makeActionOverflowMenuShown();

        setTitle(R.string.title_options);

        _radioAndroid = (RadioButton) findViewById(R.id.rbAndroid);
        _radioAndroid.setOnCheckedChangeListener(new OnCheckedChangeListener() {
            public void onCheckedChanged(CompoundButton buttonView, boolean isChecked) {
                _radioHuman.setChecked(!isChecked);
                _checkAutoFlip.setEnabled(false);
            }
        });
        _radioHuman = (RadioButton) findViewById(R.id.rbHuman);
        _radioHuman.setOnCheckedChangeListener(new OnCheckedChangeListener() {
            public void onCheckedChanged(CompoundButton buttonView, boolean isChecked) {
                _radioAndroid.setChecked(!_radioHuman.isChecked());
                _checkAutoFlip.setEnabled(true);
            }
        });

        _checkAutoFlip = (CheckBox) findViewById(R.id.CheckBoxOptionsAutoFlip);
        _checkMoves = (CheckBox) findViewById(R.id.CheckBoxOptionsShowMoves);

        _tableRowOption960 = (TableRow) findViewById(R.id.TableRowOptions960);
        _check960 = (CheckBox) findViewById(R.id.CheckBoxOptions960);

        ArrayAdapter<CharSequence> adapter = ArrayAdapter.createFromResource(this, R.array.levels_time, android.R.layout.simple_spinner_item);
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);

        _spinLevel = (Spinner) findViewById(R.id.SpinnerOptionsLevel);
        _spinLevel.setPrompt(getString(R.string.title_pick_level));
        _spinLevel.setAdapter(adapter);

        ArrayAdapter<CharSequence> adapterPly = ArrayAdapter.createFromResource(this, R.array.levels_ply, android.R.layout.simple_spinner_item);
        adapterPly.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        _spinLevelPly = (Spinner) findViewById(R.id.SpinnerOptionsLevelPly);
        _spinLevelPly.setPrompt(getString(R.string.title_pick_level));
        _spinLevelPly.setAdapter(adapterPly);

        _radioTime = (RadioButton) findViewById(R.id.RadioOptionsTime);
        _radioTime.setOnCheckedChangeListener(new OnCheckedChangeListener() {
            public void onCheckedChanged(CompoundButton buttonView, boolean isChecked) {
                _radioPly.setChecked(!_radioTime.isChecked());
            }
        });
        _radioPly = (RadioButton) findViewById(R.id.RadioOptionsPly);
        _radioPly.setOnCheckedChangeListener(new OnCheckedChangeListener() {
            public void onCheckedChanged(CompoundButton buttonView, boolean isChecked) {
                _radioTime.setChecked(!_radioPly.isChecked());
            }
        });

        _radioWhite = (RadioButton) findViewById(R.id.rbWhite);
        _radioWhite.setOnCheckedChangeListener(new OnCheckedChangeListener() {
            @Override
            public void onCheckedChanged(CompoundButton compoundButton, boolean b) {
                _radioBlack.setChecked(!_radioWhite.isChecked());
            }
        });
        _radioBlack = (RadioButton) findViewById(R.id.rbBlack);
        _radioBlack.setOnCheckedChangeListener(new OnCheckedChangeListener() {
            @Override
            public void onCheckedChanged(CompoundButton buttonView, boolean isChecked) {
                _radioWhite.setChecked(!_radioBlack.isChecked());
            }
        });

        _butCancel = (Button) findViewById(R.id.ButtonOptionsCancel);
        _butCancel.setOnClickListener(new View.OnClickListener() {
            public void onClick(View arg0) {
                setResult(RESULT_CANCELED);
                finish();
            }
        });

        _butOk = (Button) findViewById(R.id.ButtonOptionsOk);
        _butOk.setOnClickListener(new View.OnClickListener() {
            public void onClick(View arg0) {

                SharedPreferences.Editor editor = options.this.getPrefs().edit();

                editor.putInt("levelMode", _radioTime.isChecked() ? GameControl.LEVEL_TIME : GameControl.LEVEL_PLY);
                editor.putInt("level", _spinLevel.getSelectedItemPosition() + 1);
                editor.putInt("levelPly", _spinLevelPly.getSelectedItemPosition() + 1);
                editor.putInt("playMode", _radioAndroid.isChecked() ? GameControl.HUMAN_PC : GameControl.HUMAN_HUMAN);
                editor.putBoolean("autoflipBoard", _checkAutoFlip.isChecked());
                editor.putBoolean("showMoves", _checkMoves.isChecked());
                editor.putBoolean("playAsBlack", _radioBlack.isChecked());

                editor.apply();

                if (_tableRowOption960.getVisibility() == View.VISIBLE && _check960.isChecked()) {


                    AlertDialog.Builder builder = new AlertDialog.Builder(options.this);
                    builder.setTitle(getString(R.string.title_chess960_manual_random));
                    final EditText input = new EditText(options.this);
                    input.setInputType(InputType.TYPE_CLASS_PHONE);
                    builder.setView(input);
                    builder.setPositiveButton(getString(R.string.choice_manually), new DialogInterface.OnClickListener() {
                        public void onClick(DialogInterface dialog, int whichButton) {
                            try {
                                int seed = Integer.parseInt(input.getText().toString());

                                if (seed >= 0 && seed <= 960) {
                                    //_chessView.newGameRandomFischer(seed);
                                    SharedPreferences.Editor editor = options.this.getPrefs().edit();
                                    editor.putString("FEN", null);
                                    editor.putInt("boardNum", 0);
                                    editor.putInt("randomFischerSeed", seed % 960);
                                    editor.apply();

                                    finish();
                                } else {
                                    doToast(getString(R.string.err_chess960_position_range));
                                }
                            } catch (Exception ex) {
                                doToast(getString(R.string.err_chess960_position_format));
                            }
                        }
                    });
                    builder.setNegativeButton(getString(R.string.choice_random), new DialogInterface.OnClickListener() {

                        public void onClick(DialogInterface dialog, int which) {
                            int seed = -1;
                            //seed = _chessView.newGameRandomFischer(seed);

                            SharedPreferences.Editor editor = options.this.getPrefs().edit();
                            editor.putString("FEN", null);
                            editor.putInt("boardNum", -1);
                            editor.putInt("randomFischerSeed", seed);
                            editor.apply();

                            finish();
                        }
                    });

                    AlertDialog alert = builder.create();
                    alert.show();

                    setResult(RESULT_960);

                } else {
                    setResult(RESULT_OK);
                    finish();
                }
            }
        });
    }


    @Override
    protected void onResume() {

        final Intent intent = getIntent();

        if (intent.getExtras().getInt("requestCode") == main.REQUEST_NEWGAME) {
            setTitle(R.string.menu_new);
            _tableRowOption960.setVisibility(View.VISIBLE);
        } else {
            _check960.setChecked(false);
            _tableRowOption960.setVisibility(View.GONE);
        }

        SharedPreferences prefs = this.getPrefs();

        _radioAndroid.setChecked(prefs.getInt("playMode", GameControl.HUMAN_PC) == GameControl.HUMAN_PC);
        _radioHuman.setChecked(!_radioAndroid.isChecked());

        _checkAutoFlip.setChecked(prefs.getBoolean("autoflipBoard", false));
        _checkAutoFlip.setEnabled(_radioHuman.isChecked());
        _checkMoves.setChecked(prefs.getBoolean("showMoves", true));

        _radioBlack.setChecked(prefs.getBoolean("playAsBlack", false));
        _radioWhite.setChecked(!_radioBlack.isChecked());

        _radioTime.setChecked(prefs.getInt("levelMode", GameControl.LEVEL_TIME) == GameControl.LEVEL_TIME);
        _radioPly.setChecked(prefs.getInt("levelMode", GameControl.LEVEL_TIME) == GameControl.LEVEL_PLY);

        _spinLevel.setSelection(prefs.getInt("level", 2) - 1);
        _spinLevelPly.setSelection(prefs.getInt("levelPly", 2) - 1);

        super.onResume();
    }

    @Override
    protected void onPause() {

        _sbFlipTopPieces = (_radioHuman.isChecked() && !_checkAutoFlip.isChecked());
        _sbPlayAsBlack = _radioBlack.isChecked();

        super.onPause();
    }

    public static boolean is_sbFlipTopPieces(){
        return _sbFlipTopPieces;
    }

    public static boolean is_sbPlayAsBlack(){
        return _sbPlayAsBlack;
    }

}
