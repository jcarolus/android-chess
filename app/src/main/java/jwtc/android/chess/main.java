package jwtc.android.chess;

import jwtc.chess.*;

import android.app.AlertDialog;
import android.content.Context;
import android.media.Ringtone;
import android.media.RingtoneManager;
import android.net.Uri;
import android.os.Bundle;
import android.os.Environment;
import android.speech.tts.TextToSpeech.OnInitListener;
import android.text.ClipboardManager;
import android.util.Log;
import android.view.*;
import android.content.ContentUris;
import android.content.ContentValues;
import android.content.DialogInterface;
import android.content.SharedPreferences;
import android.content.Intent;
import android.database.Cursor;
import android.speech.tts.TextToSpeech;

import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.Calendar;
import java.util.Date;
import java.util.Locale;

import android.view.GestureDetector.OnGestureListener;
import android.view.GestureDetector;
import android.widget.TextView;

import com.google.android.gms.analytics.HitBuilders;

public class main extends ChessActivity implements OnInitListener, GestureDetector.OnGestureListener {

    public static final String TAG = "main";

    /**
     * instances for the view and game of chess *
     */
    private ChessView _chessView;
    private SaveGameDlg _dlgSave;
    //private ImageButton _butMenu;
    private String[] _itemsMenu; // convenience member for 'dynamic final variable' purpose
    //private Uri _uri;
    //private String _action;
    private long _lGameID;
    private float _fGameRating;

    private Uri _uriNotification;
    private Ringtone _ringNotification;
    private String _keyboardBuffer;
    private TextToSpeech _speech = null;

    public static final int REQUEST_SETUP = 1;
    public static final int REQUEST_OPEN = 2;
    public static final int REQUEST_OPTIONS = 3;
    public static final int REQUEST_NEWGAME = 4;
    public static final int REQUEST_FROM_QR_CODE = 5;

    private GestureDetector _gestureDetector;

    private boolean _skipReturn;

    /**
     * Called when the activity is first created.
     */
    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        _uriNotification = null;
        _ringNotification = null;

        setContentView(R.layout.main);

        this.makeActionOverflowMenuShown();

        SharedPreferences prefs = this.getPrefs();

        if (prefs.getBoolean("speechNotification", false)) {
            try {
                _speech = new TextToSpeech(this, this);
            } catch (Exception ex){
                _speech = null;
            }
        } else {
            _speech = null;
        }

        _chessView = new ChessView(this);
        _keyboardBuffer = "";

        _lGameID = 0;
        _fGameRating = 2.5F;
        _dlgSave = null;

        _gestureDetector = new GestureDetector(this, this);
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        MenuInflater inflater = getMenuInflater();
        inflater.inflate(R.menu.main_topmenu, menu);
        return super.onCreateOptionsMenu(menu);
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        // Handle presses on the action bar items
        Intent intent;
        String s;
        switch (item.getItemId()) {
            case R.id.action_prefs:
                intent = new Intent();
                intent.setClass(main.this, mainPrefs.class);
                startActivity(intent);
                return true;
            case R.id.action_flip:
                _chessView.flipBoard();
                return true;
            case R.id.action_options:
                intent = new Intent();
                intent.setClass(main.this, options.class);
                intent.putExtra("requestCode", REQUEST_OPTIONS);
                startActivityForResult(intent, REQUEST_OPTIONS);
                return true;
            case R.id.action_setup:
                intent = new Intent();
                intent.setClass(main.this, setup.class);
                startActivityForResult(intent, main.REQUEST_SETUP);
                return true;
            case R.id.action_email:
                emailPGN();
                return true;
            case R.id.action_clock:
                AlertDialog.Builder builder = new AlertDialog.Builder(main.this);
                builder.setTitle(getString(R.string.title_menu));
                String sTime = getString(R.string.choice_clock_num_minutes);
                final String[] itemsMenu = new String[]{"no clock", String.format(sTime, 2), String.format(sTime, 5), String.format(sTime, 10), String.format(sTime, 30), String.format(sTime, 60)};
                builder.setItems(itemsMenu, new DialogInterface.OnClickListener() {
                    public void onClick(DialogInterface dialog, int item) {
                        dialog.dismiss();
                        if (item == 0)
                            _chessView._lClockTotal = 0;
                        else if (item == 1)
                            _chessView._lClockTotal = 120000;
                        else if (item == 2)
                            _chessView._lClockTotal = 300000;
                        else if (item == 3)
                            _chessView._lClockTotal = 600000;
                        else if (item == 4)
                            _chessView._lClockTotal = 1800000;
                        else if (item == 5)
                            _chessView._lClockTotal = 3600000;
                        _chessView.resetTimer();
                    }
                });
                AlertDialog alert = builder.create();
                alert.show();
                return true;
            case R.id.action_clip_pgn:
                copyToClipBoard(_chessView.exportFullPGN());
                return true;
            case R.id.action_fromclip:
                s = fromClipBoard();
                if (s != null) {
                    if (s.indexOf("1.") >= 0)
                        loadPGN(s);
                    else
                        loadFEN(s);
                }
                return true;
            case R.id.action_help:
                Intent i = new Intent();
                i.setClass(main.this, HtmlActivity.class);
                i.putExtra(HtmlActivity.HELP_MODE, "help_play");
                startActivity(i);
                return true;
            case R.id.action_from_qrcode:
                try {
                    intent = new Intent("com.google.zxing.client.android.SCAN");
                    //com.google.zxing.client.android.SCAN.
                    intent.putExtra("SCAN_MODE", "QR_CODE_MODE");
                    startActivityForResult(intent, REQUEST_FROM_QR_CODE);
                } catch (Exception ex) {
                    doToast(getString(R.string.err_install_barcode_scanner));
                }
                return true;
            case R.id.action_to_qrcode:
                s = "http://chart.apis.google.com/chart?chs=200x200&cht=qr&chl=";
                s += java.net.URLEncoder.encode(_chessView.getJNI().toFEN());
                copyToClipBoard(s);
                doToast(getString(R.string.msg_qr_code_on_clipboard));
                return true;
            default:
                return super.onOptionsItemSelected(item);
        }
    }

    public void showSubViewMenu() {

        _itemsMenu = new String[]{
                getString(R.string.menu_subview_cpu),
                getString(R.string.menu_subview_captured),
                getString(R.string.menu_subview_seek),
                getString(R.string.menu_subview_moves),
                getString(R.string.menu_subview_annotate),
                getString(R.string.menu_subview_guess),
                getString(R.string.menu_subview_blindfold)
        };

        AlertDialog.Builder builder = new AlertDialog.Builder(main.this);
        builder.setTitle(getString(R.string.menu_subview_title));
        final TextView tv_engine = (TextView) findViewById(R.id.TextViewEngine);

        builder.setItems(_itemsMenu, new DialogInterface.OnClickListener() {
            public void onClick(DialogInterface dialog, int item) {
                dialog.dismiss();
                _chessView.toggleControl(item);

                // show engine name
                SharedPreferences pref = getBaseContext().getSharedPreferences("ChessPlayer", Context.MODE_PRIVATE);
                String engName = pref.getString("UCIEngine", "Jeroen");
                tv_engine.setText(_itemsMenu[0] + ": " + engName);
            }
        });
        AlertDialog alert = builder.create();
        alert.show();
    }

    @Override  // bug report - dispatchKeyEvent is called before onKeyDown and some keys are overwritten in certain appcompat versions
    public boolean dispatchKeyEvent(KeyEvent event) {
        int keyCode = event.getKeyCode();
        int action = event.getAction();
        boolean isDown = action == 0;

        if(_skipReturn && keyCode == KeyEvent.KEYCODE_ENTER){  // skip enter key
            return true;
        }

        if (keyCode == KeyEvent.KEYCODE_MENU) {
            return isDown ? this.onKeyDown(keyCode, event) : this.onKeyUp(keyCode, event);
        }

        return super.dispatchKeyEvent(event);
    }

    @Override public boolean onKeyDown(int keyCode, KeyEvent event) {

        //View v = getWindow().getCurrentFocus();
        //Log.i("main", "current focus " + (v == null ? "NULL" : v.toString()));
        int c = (event.getUnicodeChar());
        Log.i("main", "onKeyDown " + keyCode + " = " + (char)c);
        if(keyCode == KeyEvent.KEYCODE_MENU){
            //showMenu();
            return true;
        }

        // preference is to skip a carriage return
        if(_skipReturn && (char)c == '\r'){
            return true;
        }

        if(c > 48 && c < 57 || c > 96 && c < 105){
            _keyboardBuffer += ("" + (char)c);
        }
        if(_keyboardBuffer.length() >= 2){
            Log.i("main", "handleClickFromPositionString " + _keyboardBuffer);
            _chessView.handleClickFromPositionString(_keyboardBuffer);
            _keyboardBuffer = "";
        }
    	/*
        switch (keyCode) {
            case KeyEvent.KEYCODE_DPAD_CENTER:
            	_chessView.dpadSelect();
                return true;
            case KeyEvent.KEYCODE_DPAD_DOWN:
            	_chessView.dpadDown();
                   return true;
            case KeyEvent.KEYCODE_DPAD_LEFT:
            	_chessView.dpadLeft();
                   return true;
            case KeyEvent.KEYCODE_DPAD_RIGHT:
            	_chessView.dpadRight();
                   return true;
            case KeyEvent.KEYCODE_DPAD_UP:
            	_chessView.dpadUp();
                   return true;
        }
        */
        return super.onKeyDown(keyCode, event);
    }

    /**
     *
     */
    @Override
    protected void onResume() {

        Log.i("main", "onResume");

        //Debug.startMethodTracing("chessplayertrace");

        SharedPreferences prefs = this.getPrefs();

        if (prefs.getBoolean("speechNotification", false)) {
            if(_speech == null)
            {_speech = new TextToSpeech(this, this);}
        } else {
            _speech = null;
        }

        _skipReturn = prefs.getBoolean("skipReturn", true);

        String sOpeningDb = prefs.getString("OpeningDb", null);
        if (sOpeningDb == null) {
            try {
                String outFilename = "/data/data/" + getPackageName() + "/db.bin";
                _chessView.loadDB(getAssets().open("db.bin"), outFilename, 17);
            } catch (IOException e) {

                Log.e("onResume", e.toString());
            }
        } else {
            Uri uri = Uri.parse(sOpeningDb);
            Log.i("onResume", "db : " + uri.getPath());
            _chessView.setOpeningDb(uri.getPath());
        }

        String sPGN = "";
        String sFEN = prefs.getString("FEN", null);

        String sTmp = prefs.getString("NotificationUri", null);
        if (sTmp == null) {
            _uriNotification = null;
            _ringNotification = null;
        } else {
            _uriNotification = Uri.parse(sTmp);
            _ringNotification = RingtoneManager.getRingtone(this, _uriNotification);
        }

        final Intent intent = getIntent();
        String action = intent.getAction();
        String type = intent.getType();
        Uri uri = intent.getData();

        if (Intent.ACTION_SEND.equals(action) && type != null) {
            _lGameID = 0;
            Log.i("onResume", "action send with type " + type);
            if ("application/x-chess-pgn".equals(type)) {
                sPGN = intent.getStringExtra(Intent.EXTRA_TEXT);
                if(sPGN != null) {
                    sPGN = sPGN.trim();
                    loadPGN(sPGN);
                }
            } else {
                sFEN = intent.getStringExtra(Intent.EXTRA_TEXT);
                if(sFEN != null) {
                    sFEN = sFEN.trim();
                    loadFEN(sFEN);
                }
            }
        } else if (uri != null) {
            _lGameID = 0;
            sPGN = "";
            Log.i("onResume", "opening " + uri.toString());
            InputStream is;
            try {
                is = getContentResolver().openInputStream(uri);
                byte[] b = new byte[4096];
                int len;

                while ((len = is.read(b)) > 0) {
                    sPGN += new String(b);
                }
                is.close();

                sPGN = sPGN.trim();

                loadPGN(sPGN);

            } catch (Exception e) {
                Log.e("onResume", "Failed " + e.toString());
            }
        } else if (sFEN != null) {
            // default, from prefs
            Log.i("onResume", "Loading FEN " + sFEN);
            _lGameID = 0;
            loadFEN(sFEN);
        } else {
            _lGameID = prefs.getLong("game_id", 0);
            if (_lGameID > 0) {
                Log.i("onResume", "loading saved game " + _lGameID);
                loadGame();
            } else {
                sPGN = prefs.getString("game_pgn", null);
                Log.i("onResume", "pgn: " + sPGN);
                loadPGN(sPGN);
            }
        }

        _chessView.OnResume(prefs);

        _chessView.updateState();

        super.onResume();
    }


    @Override
    protected void onPause() {
        //Debug.stopMethodTracing();

        if (_lGameID > 0) {
            ContentValues values = new ContentValues();

            values.put(PGNColumns.DATE, _chessView.getDate().getTime());
            values.put(PGNColumns.WHITE, _chessView.getWhite());
            values.put(PGNColumns.BLACK, _chessView.getBlack());
            values.put(PGNColumns.PGN, _chessView.exportFullPGN());
            values.put(PGNColumns.RATING, _fGameRating);
            values.put(PGNColumns.EVENT, _chessView.getPGNHeadProperty("Event"));

            saveGame(values, false);
        }
        SharedPreferences.Editor editor = this.getPrefs().edit();
        editor.putLong("game_id", _lGameID);
        editor.putString("game_pgn", _chessView.exportFullPGN());
        editor.putString("FEN", null); // 
        if (_uriNotification == null)
            editor.putString("NotificationUri", null);
        else
            editor.putString("NotificationUri", _uriNotification.toString());
        _chessView.OnPause(editor);

        editor.commit();

        super.onPause();
    }

    @Override
    protected void onDestroy() {
        _chessView.OnDestroy();
        if (_speech != null) {
            _speech.stop();
            _speech.shutdown();
        }
        super.onDestroy();
    }

    protected void onActivityResult(int requestCode, int resultCode, Intent data) {

        Log.i("main", "onActivityResult");

        if (requestCode == REQUEST_SETUP) {
            if (resultCode == RESULT_OK) {
                //data));
                _chessView.clearPGNView();
            }
        } else if (requestCode == REQUEST_OPEN) {
            if (resultCode == RESULT_OK) {

                Uri uri = data.getData();
                try {
                    _lGameID = Long.parseLong(uri.getLastPathSegment());
                } catch (Exception ex) {
                    _lGameID = 0;
                }
                SharedPreferences.Editor editor = this.getPrefs().edit();
                editor.putLong("game_id", _lGameID);
                editor.putInt("boardNum", 0);
                editor.putString("FEN", null);
                editor.putInt("playMode", _chessView.HUMAN_HUMAN);
                editor.putBoolean("playAsBlack", false);
                editor.commit();
            }
        } else if (requestCode == REQUEST_FROM_QR_CODE) {
            if (resultCode == RESULT_OK) {
                String contents = data.getStringExtra("SCAN_RESULT");
                //String format = data.getStringExtra("SCAN_RESULT_FORMAT");

                SharedPreferences.Editor editor = this.getPrefs().edit();
                editor.putLong("game_id", 0);
                editor.putInt("boardNum", 0);
                editor.putString("FEN", contents);
                editor.commit();
                //doToast("Content: " + contents + "::" + format);
            }
        } else if (requestCode == REQUEST_NEWGAME) {

            if (resultCode == options.RESULT_960) {
                newGameRandomFischer();
            } else if (resultCode == RESULT_OK) {
                newGame();
            }
        }
    }


    protected void emailPGN() {
        try {
            if (Environment.getExternalStorageState().equals(Environment.MEDIA_MOUNTED)) {

                String sFile = Environment.getExternalStorageDirectory() + "/chess_history.pgn";
                String s = _chessView.exportFullPGN();

                FileOutputStream fos;

                fos = new FileOutputStream(sFile);
                fos.write(s.getBytes());
                fos.flush();
                fos.close();

                Intent sendIntent = new Intent(Intent.ACTION_SEND);
                sendIntent.putExtra(Intent.EXTRA_SUBJECT, "chess pgn");
                sendIntent.putExtra(Intent.EXTRA_STREAM, Uri.parse("file://" + sFile));
                sendIntent.setType("application/x-chess-pgn");

                startActivity(sendIntent);
            } else {
                doToast(getString(R.string.err_sd_not_mounted));
            }
        } catch (Exception e) {

            doToast(getString(R.string.err_send_email));
            Log.e("ex", e.toString());
            return;
        }
    }


    private void copyToClipBoard(String s) {
        ClipboardManager cm = (ClipboardManager) getSystemService(CLIPBOARD_SERVICE);
        cm.setText(s);
    }

    private String fromClipBoard() {
        ClipboardManager cm = (ClipboardManager) getSystemService(CLIPBOARD_SERVICE);
        if (cm.hasText()) {
            return cm.getText().toString();
        }
        doToast(getString(R.string.err_no_clip_text));
        return null;
    }

    private void loadFEN(String sFEN) {
        if (sFEN != null) {
            Log.i("loadFEN", sFEN);
            if (false == _chessView.initFEN(sFEN, true)) {
                doToast(getString(R.string.err_load_fen));
                Log.e("loadFEN", "FAILED");
            }
            _chessView.updateState();
        }
    }

    private void loadPGN(String sPGN) {
        if (sPGN != null) {
            if (false == _chessView.loadPGN(sPGN)) {
                doToast(getString(R.string.err_load_pgn));
            }
            _chessView.updateState();
        }
    }

    private void newGame() {
        _lGameID = 0;
        _chessView.newGame();
        SharedPreferences.Editor editor = this.getPrefs().edit();
        editor.putString("FEN", null);
        editor.putInt("boardNum", 0);
        editor.putString("game_pgn", null);
        editor.putLong("game_id", _lGameID);
        editor.commit();

        trackEvent(TAG, "newGame", "default");
    }

    private void newGameRandomFischer() {
        _lGameID = 0;

        int seed = this.getPrefs().getInt("randomFischerSeed", -1);
        seed = _chessView.newGameRandomFischer(seed);
        doToast(String.format(getString(R.string.chess960_position_nr), seed));

        SharedPreferences.Editor editor = this.getPrefs().edit();
        editor.putString("FEN", _chessView.getJNI().toFEN());
        editor.putInt("boardNum", 0);
        editor.putString("game_pgn", null);
        editor.putLong("game_id", _lGameID);
        editor.commit();

        trackEvent(TAG, "newGame", "960");
    }

    private void loadGame() {
        if (_lGameID > 0) {
            Uri uri = ContentUris.withAppendedId(MyPGNProvider.CONTENT_URI, _lGameID);
            Cursor c = managedQuery(uri, PGNColumns.COLUMNS, null, null, null);
            if (c != null && c.getCount() == 1) {

                c.moveToFirst();

                _lGameID = c.getLong(c.getColumnIndex(PGNColumns._ID));
                String sPGN = c.getString(c.getColumnIndex(PGNColumns.PGN));
                _chessView.loadPGN(sPGN);

                _chessView.setPGNHeadProperty("Event", c.getString(c.getColumnIndex(PGNColumns.EVENT)));
                _chessView.setPGNHeadProperty("White", c.getString(c.getColumnIndex(PGNColumns.WHITE)));
                _chessView.setPGNHeadProperty("Black", c.getString(c.getColumnIndex(PGNColumns.BLACK)));
                _chessView.setDateLong(c.getLong(c.getColumnIndex(PGNColumns.DATE)));

                _fGameRating = c.getFloat(c.getColumnIndex(PGNColumns.RATING));

                trackEvent(TAG, "loadGame");
            } else {
                _lGameID = 0; // probably deleted
            }
        } else {
            _lGameID = 0;
        }
    }


    // 
    public void saveGame() {
        String sEvent = _chessView.getPGNHeadProperty("Event");
        if (sEvent == null)
            sEvent = getString(R.string.savegame_event_question);
        String sWhite = _chessView.getWhite();
        if (sWhite == null)
            sWhite = getString(R.string.savegame_white_question);
        String sBlack = _chessView.getBlack();
        if (sBlack == null)
            sBlack = getString(R.string.savegame_black_question);

        Date dd = _chessView.getDate();
        if (dd == null)
            dd = Calendar.getInstance().getTime();

        Calendar cal = Calendar.getInstance();
        cal.setTime(dd);

        if (_dlgSave == null)
            _dlgSave = new SaveGameDlg(this);
        _dlgSave.setItems(sEvent, sWhite, sBlack, cal, _chessView.exportFullPGN(), _fGameRating, _lGameID > 0);
        _dlgSave.show();
    }

    public void saveGame(ContentValues values, boolean bCopy) {

        SharedPreferences.Editor editor = this.getPrefs().edit();
        editor.putString("FEN", null);
        editor.commit();

        _chessView.setPGNHeadProperty("Event", (String) values.get(PGNColumns.EVENT));
        _chessView.setPGNHeadProperty("White", (String) values.get(PGNColumns.WHITE));
        _chessView.setPGNHeadProperty("Black", (String) values.get(PGNColumns.BLACK));
        _chessView.setDateLong((Long) values.get(PGNColumns.DATE));

        _fGameRating = (Float) values.get(PGNColumns.RATING);
        //

        if (_lGameID > 0 && (bCopy == false)) {
            Uri uri = ContentUris.withAppendedId(MyPGNProvider.CONTENT_URI, _lGameID);
            getContentResolver().update(uri, values, null, null);
        } else {
            Uri uri = MyPGNProvider.CONTENT_URI;
            Uri uriInsert = getContentResolver().insert(uri, values);
            Cursor c = managedQuery(uriInsert, new String[]{PGNColumns._ID}, null, null, null);
            if (c != null && c.getCount() == 1) {
                c.moveToFirst();
                _lGameID = c.getLong(c.getColumnIndex(PGNColumns._ID));
            }
        }
    }

    public void setLevelMode(int iLevelMode) {
        _chessView.setLevelMode(iLevelMode);
    }

    public void setLevel(int iLevel) {
        _chessView.setLevel(iLevel);
    }

    public void setLevelPly(int iLevelPly) {
        _chessView.setLevelPly(iLevelPly);
    }

    public void setPlayMode(int mode) {
        _chessView.setPlayMode(mode);
    }

    public void setAutoFlip(boolean b) {
        _chessView.setAutoFlip(b);
    }

    public void setShowMoves(boolean b) {
        _chessView.setShowMoves(b);
    }

    public void flipBoard() {
        _chessView.flipBoard();
    }

    public void soundNotification(String sSpeech) {
        if (_speech != null) {
            _speech.speak(sSpeech, TextToSpeech.QUEUE_FLUSH, null);
        }
        if (_ringNotification != null && _speech == null) {
            _ringNotification.play();
        }
    }

    @Override
    public void onInit(int status) {
        if (status == TextToSpeech.SUCCESS) {

            int result = _speech.setLanguage(Locale.US);

            if (result == TextToSpeech.LANG_MISSING_DATA || result == TextToSpeech.LANG_NOT_SUPPORTED) {
                doToast("Speech does not support US locale");
                _speech = null;
            } else {
                _speech.setSpeechRate(0.80F);
                _speech.setPitch(0.85F);
            }
        } else {
            doToast("Speech not supported");
            _speech = null;
        }

    }

    @Override
    public boolean onTouchEvent(MotionEvent event) {
        _gestureDetector.onTouchEvent(event);
        return super.onTouchEvent(event);
    }

    @Override
    public boolean onDown(MotionEvent motionEvent) {
        return false;
    }

    @Override
    public void onShowPress(MotionEvent motionEvent) {

    }

    @Override
    public boolean onSingleTapUp(MotionEvent motionEvent) {
        return false;
    }

    @Override
    public boolean onScroll(MotionEvent motionEvent, MotionEvent motionEvent1, float v, float v1) {
        return false;
    }

    @Override
    public void onLongPress(MotionEvent motionEvent) {
    }

    @Override
    public boolean onFling(MotionEvent motionEvent, MotionEvent motionEvent1, float v, float v1) {
        //Log.i("main", "onFling " + motionEvent.getX() + " " + motionEvent1.getX());

        int Xdiff = (int) motionEvent.getX() - (int) motionEvent1.getX();
        int Ydiff = (int) motionEvent.getY() - (int) motionEvent1.getY();

        if (Xdiff < -150) {
            //Log.i("main", "ButNext");
            _chessView.next();
        }

        if (Xdiff > 150) {
            //Log.i("main", "ButPrevious");
            _chessView.previous();
        }

        if (Ydiff > 150 || Ydiff < -150) {
            //Log.i("main", "flipBoard");
            _chessView.flipBoard();
        }
        return true;
    }

}