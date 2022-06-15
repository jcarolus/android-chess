package jwtc.android.chess.ics;

import android.Manifest;
import android.app.AlertDialog;
import android.app.Notification;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.ComponentName;
import android.content.ServiceConnection;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.graphics.Typeface;
import android.media.Ringtone;
import android.media.RingtoneManager;
import android.net.Uri;
import android.os.Bundle;
import android.os.Environment;
import android.os.Handler;
import android.os.IBinder;
import android.os.Message;
import android.os.Vibrator;

import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;

import android.text.ClipboardManager;
import android.util.Log;
import android.view.*;
import android.view.View.OnClickListener;
import android.view.View.OnKeyListener;
import android.view.inputmethod.EditorInfo;
import android.widget.*;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.SharedPreferences;
import android.widget.AdapterView.OnItemClickListener;

import java.io.FileOutputStream;
import java.lang.ref.WeakReference;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.TimeZone;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.json.JSONArray;
import org.json.JSONException;

import jwtc.android.chess.*;
import jwtc.android.chess.activities.ChessBoardActivity;
import jwtc.android.chess.helpers.ResultDialogListener;
import jwtc.chess.Pos;

public class ICSClient extends ChessBoardActivity implements ICSListener, ResultDialogListener {
    public static final String TAG = "ICSClient";

    public static final int REQUEST_LOGIN = 1, REQUEST_CHALLENGE = 2, REQUEST_CONFIRM = 3, REQUEST_MENU = 4;

    private ICSServer icsServer = null;

    protected String _sConsoleEditText;
    private String _server, _handle, _pwd, _ficsHandle, _ficsPwd, _sFile, _FEN = "", _whiteRating, _blackRating, _whiteHandle, _blackHandle;
    private int _port, _serverType, _TimeWarning, _gameStartSound, _iConsoleCharacterSize;
    private boolean _bAutoSought, _bTimeWarning, _bEndGameDialog, _bShowClockPGN,
            _notifyON, _bICSVolume, _ICSNotifyLifeCycle;
    private Button _butLogin;
    private TextView _tvHeader, _tvConsole, _tvPlayConsole;
//	public ICSChatDlg _dlgChat;

    private EditText _editHandle, _editPwd, _editConsole, _editBoard;

    private Spinner _spinnerHandles;
    private ArrayAdapter<String> _adapterHandles;
    private ArrayList<String> _arrayPasswords;

    //private EditText _editPrompt;
    private ListView _listChallenges, _listPlayers, _listGames, _listStored, _listWelcome;
    private ICSChessView _view;
    private ChessViewBase _viewbase;
    protected ICSLoginDlg _dlgLogin;
    protected ICSMatchDlg _dlgMatch;
    private ICSPlayerDlg _dlgPlayer;
    private ICSConfirmDlg _dlgConfirm;
    private ICSGameOverDlg _dlgOver;
    private StringBuilder PGN;

    private Ringtone _ringNotification;

    private TimeZone tz = TimeZone.getDefault();

    private Matcher _matgame;

    private ArrayList<HashMap<String, String>> mapMenu = new ArrayList<HashMap<String, String>>();
    private ArrayList<HashMap<String, String>> _mapChallenges = new ArrayList<HashMap<String, String>>();
    private ArrayList<HashMap<String, String>> _mapPlayers = new ArrayList<HashMap<String, String>>();
    private ArrayList<HashMap<String, String>> _mapGames = new ArrayList<HashMap<String, String>>();
    private ArrayList<HashMap<String, String>> _mapStored = new ArrayList<HashMap<String, String>>();

    private AlternatingRowColorAdapter _adapterGames, _adapterPlayers, _adapterChallenges, _adapterStored;

    ArrayAdapter<String> _adapterWelcome;

    protected static final int SERVER_FICS = 1;

    protected static final int VIEW_MAIN_BOARD = 0;
    protected static final int VIEW_MAIN_LOBBY = 1;
    protected static final int VIEW_MAIN_NOT_CONNECTED = 2;
    protected static final int VIEW_MAIN_LOGIN = 3;

    protected static final int VIEW_SUB_PLAYERS = 0;
    protected static final int VIEW_SUB_GAMES = 1;
    protected static final int VIEW_SUB_WELCOME = 2;
    protected static final int VIEW_SUB_CHALLENGES = 3;
    protected static final int VIEW_SUB_PROGRESS = 4;
    protected static final int VIEW_SUB_CONSOLE = 5;
    protected static final int VIEW_SUB_STORED = 6;

    protected static final int DECREASE = 0;

    protected static int[] whiteClk = new int[200]; // PGN time clock
    protected static int[] blackClk = new int[200];

    private ServiceConnection mConnection = new ServiceConnection() {
        public void onServiceConnected(ComponentName className, IBinder service) {
            // This is called when the connection with the service has been
            // established, giving us the service object we can use to
            // interact with the service.  Because we have bound to a explicit
            // service that we know is running in our own process, we can
            // cast its IBinder to a concrete class and directly access it.
            Log.i(TAG, "onServiceConnected");
            icsServer = ((ICSServer.LocalBinder)service).getService();
            icsServer.addListener(ICSClient.this);
            icsServer.addListener((ICSApi)gameApi);

            _dlgLogin = new ICSLoginDlg(ICSClient.this, ICSClient.this, REQUEST_LOGIN);

            openLoginDialogOfNotConnected();
        }

        public void onServiceDisconnected(ComponentName className) {
            // This is called when the connection with the service has been
            // unexpectedly disconnected -- that is, its process crashed.
            // Because it is running in our same process, we should never
            // see this happen.
            OnSessionEnded();
            icsServer = null;
            Log.i(TAG, "onServiceDisconnected");
        }
    };

    /**
     * Called when the activity is first created.
     */
    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        Log.i(TAG, "onCreate");

        setContentView(R.layout.icsclient);

        gameApi = new ICSApi();

        afterCreate();

        _dlgMatch = new ICSMatchDlg(this, this, REQUEST_CHALLENGE);
        _dlgPlayer = new ICSPlayerDlg(this);
        _dlgConfirm = new ICSConfirmDlg(this, this, REQUEST_CONFIRM);
        _dlgOver = new ICSGameOverDlg(this);


        _serverType = SERVER_FICS;
        _iConsoleCharacterSize = 10;
        _bAutoSought = true;
        _bTimeWarning = true;
        _bEndGameDialog = true;
        _bShowClockPGN = true;

        Button buttonChallenge = findViewById(R.id.ButtonChallenge);
        buttonChallenge.setOnClickListener(new OnClickListener() {
            @Override
            public void onClick(View v) {
                //                SharedPreferences myPrefs = getContext().getSharedPreferences(_parent.get_ficsHandle().toLowerCase(), getContext().MODE_PRIVATE);
//                _dlgMatch.showWithPrefs();
            }
        });

        Button buttonMate = findViewById(R.id.ButtonPuzzle);
        buttonMate.setOnClickListener(new OnClickListener() {
            @Override
            public void onClick(View v) {
                sendString("tell puzzlebot getmate");
            }
        });
        //

        String[] from = { "menu_item" };
        int[] to = { R.id.MenuText };
        SimpleAdapter menuAdapter = new SimpleAdapter(this, mapMenu, R.layout.menu_item, from, to);

        final ICSMenuDialog menuDialog = new ICSMenuDialog(this, this, REQUEST_MENU, menuAdapter);

        ImageButton buttonMenu = findViewById(R.id.ButtonMenu);
        buttonMenu.setOnClickListener(new OnClickListener() {
            @Override
            public void onClick(View v) {
                loadMenu();
                menuDialog.show();
            }
        });

//        _adapterChallenges = new AlternatingRowColorAdapter(ICSClient.this, _mapChallenges, R.layout.ics_seek_row,
//                new String[]{"text_game", "text_name", "text_rating"}, new int[]{R.id.text_game, R.id.text_name, R.id.text_rating});
//
//        _listChallenges = (ListView) findViewById(R.id.ICSChallenges);
//        _listChallenges.setAdapter(_adapterChallenges);
//        _listChallenges.setOnItemClickListener(this);
//
//        _adapterPlayers = new AlternatingRowColorAdapter(ICSClient.this, _mapPlayers, R.layout.ics_player_row,
//                new String[]{"text_name", "text_rating"}, new int[]{R.id.text_name, R.id.text_rating});
//
//
//        _adapterStored = new AlternatingRowColorAdapter(ICSClient.this, _mapStored, R.layout.ics_stored_row,
//                new String[]{"nr_stored", "color_stored", "text_name_stored", "available_stored"}, new int[]{R.id.nr_stored, R.id.color_stored, R.id.text_name_stored, R.id.available_stored});

//        _listStored = (ListView) findViewById(R.id.ICSStored);
//        _listStored.setAdapter(_adapterStored);
//         _listStored.setOnItemClickListener(this);


        ImageButton butQuick2 = (ImageButton) findViewById(R.id.ButtonICSConsoleQuickCmd);
        if (butQuick2 != null) { // crashes reported on this being null
            butQuick2.setOnClickListener(new OnClickListener() {
                public void onClick(View arg0) {
                    //showMenu();
                    openOptionsMenu();
                }
            });
        }


//        _spinnerHandles = (Spinner) findViewById(R.id.SpinnerLoginPresets);
//
//        _spinnerHandles.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
//
//            @Override
//            public void onItemSelected(AdapterView<?> parentView, View selectedItemView, int position, long id) {
//                _editHandle.setText(_spinnerHandles.getSelectedItem().toString());
//                _editPwd.setText(_arrayPasswords.get(position));
//                if (_arrayPasswords.get(position).length() < 2) {
//                    _editPwd.setText("");
//                }
//                Log.d(TAG, _spinnerHandles.getSelectedItem().toString());
//            }
//
//            @Override
//            public void onNothingSelected(AdapterView<?> parentView) {
//                Log.d(TAG, "nothing selected in spinner");
//            }
//        });

//        final Handler actionHandler = new Handler();
//        final Runnable runnable = new Runnable() {
//            @Override
//            public void run() {
//                new AlertDialog.Builder(ICSClient.this)
//                        .setTitle("Delete entry")
//                        .setMessage("Are you sure you want to delete " + _spinnerHandles.getSelectedItem().toString() + "?")
//                        .setPositiveButton(android.R.string.yes, new DialogInterface.OnClickListener() {
//                            public void onClick(DialogInterface dialog, int which) {
//                                String newData = _spinnerHandles.getSelectedItem().toString();
//                                _adapterHandles.remove(newData);
//                                _adapterHandles.notifyDataSetChanged();
//                                _arrayPasswords.remove(_spinnerHandles.getSelectedItemPosition());
//                                _editHandle.setText("");
//                                _editPwd.setText("");
//                            }
//                        })
//                        .setNegativeButton(android.R.string.no, new DialogInterface.OnClickListener() {
//                            public void onClick(DialogInterface dialog, int which) {
//                                // do nothing
//                            }
//                        })
//                        .setIcon(android.R.drawable.ic_dialog_alert)
//                        .show();
//            }
//        };
//
//        _spinnerHandles.setOnTouchListener(new View.OnTouchListener() { // simulate long press on spinner
//            @Override
//            public boolean onTouch(View v, MotionEvent event) {
//                if (event.getAction() == MotionEvent.ACTION_DOWN) {
//                    actionHandler.postDelayed(runnable, 650);
//                } else if (event.getAction() == MotionEvent.ACTION_UP) {
//                    actionHandler.removeCallbacks(runnable);
//                }
//                return false;
//            }
//        });
        /////////////////////


//        OnKeyListener okl = new OnKeyListener() {
//            public boolean onKey(View v, int keyCode, KeyEvent event) {
//                if (((event.getAction() == KeyEvent.ACTION_DOWN) && (keyCode == KeyEvent.KEYCODE_ENTER)) ||
//                        event.getAction() == EditorInfo.IME_ACTION_DONE
//                ) {
//                    // Perform action on key press
//                    EditText et = (EditText) v;
//                    _sConsoleEditText = et.getText().toString();
//
//                    _bConsoleText = true;  // show text when user types to ICS
//                    sendString(_sConsoleEditText);
//                    et.setText("");
//
//                    return true;
//                }
//                return false;
//            }
//        };

//        _editConsole = (EditText) findViewById(R.id.EditICSConsole);
//        if (_editConsole != null) {
//            _editConsole.setTextColor(getResources().getColor(android.R.color.white));
//            _editConsole.setSingleLine(true);
//            _editConsole.setOnKeyListener(okl);
//        }
//        _editBoard = (EditText) findViewById(R.id.EditICSBoard);
//        if (_editBoard != null) {
//            _editBoard.setSingleLine(true);
//            _editBoard.setOnKeyListener(okl);
//        }
//
//        Button butReg = (Button) findViewById(R.id.ButICSRegister);
//        if (butReg != null) {
//            butReg.setOnClickListener(new View.OnClickListener() {
//                public void onClick(View arg0) {
//                    try {
//                        Intent i = new Intent();
//                        i.setAction(Intent.ACTION_VIEW);
//                        i.setData(Uri.parse("http://www.freechess.org/Register/index.html"));
//                        startActivity(i);
//                    } catch (Exception ex) {
//
//                        doToast("Could not go to registration page");
//                    }
//                }
//            });
//        }


        _ringNotification = null;

//        switchToLoginView();

        Log.i("ICSClient", "onCreate");
    }

    public void loadMenu() {
        mapMenu.clear();
        mapMenu.add(new HashMap<String, String>() {{ put("menu_item", "test"); }}) ;
    }


    @Override
    public boolean onCreateOptionsMenu(Menu menu) {

//        menu.add(Menu.NONE, R.string.menu_prefs, Menu.NONE, R.string.menu_prefs);
//        menu.add(Menu.NONE, R.string.menu_flip, Menu.NONE, R.string.menu_flip);
        menu.add(Menu.NONE, R.string.ics_menu_takeback, Menu.NONE, R.string.ics_menu_takeback);
        menu.add(Menu.NONE, R.string.ics_menu_adjourn, Menu.NONE, R.string.ics_menu_adjourn);
        menu.add(Menu.NONE, R.string.ics_menu_draw, Menu.NONE, R.string.ics_menu_draw);
        menu.add(Menu.NONE, R.string.ics_menu_resign, Menu.NONE, R.string.ics_menu_resign);
        menu.add(Menu.NONE, R.string.ics_menu_abort, Menu.NONE, R.string.ics_menu_abort);
        menu.add(Menu.NONE, R.string.ics_menu_flag, Menu.NONE, R.string.ics_menu_flag);
        menu.add(Menu.NONE, R.string.ics_menu_refresh, Menu.NONE, R.string.ics_menu_refresh);
//        menu.add(Menu.NONE, R.string.ics_menu_challenges, Menu.NONE, R.string.ics_menu_challenges);
//        menu.add(Menu.NONE, R.string.ics_menu_games, Menu.NONE, R.string.ics_menu_games);
//        menu.add(Menu.NONE, R.string.ics_menu_stored, Menu.NONE, R.string.ics_menu_stored);
//        menu.add(Menu.NONE, R.string.ics_menu_seek, Menu.NONE, R.string.ics_menu_seek);
//        menu.add(Menu.NONE, R.string.ics_menu_players, Menu.NONE, R.string.ics_menu_players);
//        menu.add(Menu.NONE, R.string.ics_menu_top_blitz, Menu.NONE, R.string.ics_menu_top_blitz);
//        menu.add(Menu.NONE, R.string.ics_menu_top_standard, Menu.NONE, R.string.ics_menu_top_standard);
//        menu.add(Menu.NONE, R.string.ics_menu_stop_puzzle, Menu.NONE, R.string.ics_menu_stop_puzzle);

//        menu.add(Menu.NONE, R.string.ics_menu_console, Menu.NONE, R.string.ics_menu_console);

        menu.add("tell puzzlebot hint");
//        menu.add("unexamine");
        menu.add("tell endgamebot hint");
        menu.add("tell endgamebot move");
//        menu.add("tell endgamebot stop");

//        menu.add(Menu.NONE, R.string.ics_menu_unobserve, Menu.NONE, R.string.ics_menu_unobserve);
//        menu.add(Menu.NONE, R.string.menu_help, Menu.NONE, R.string.menu_help);

        try {
            SharedPreferences prefs = this.getPrefs();
            JSONArray jArray = new JSONArray(prefs.getString("ics_custom_commands", CustomCommands.DEFAULT_COMMANDS));

            for (int i = 0; i < jArray.length(); i++) {

                try {
                    menu.add(jArray.getString(i));
                } catch (JSONException e) {
                }
            }
        } catch (JSONException e) {
        }


        return true;
    }

    @Override
    public boolean onPrepareOptionsMenu(Menu menu) {

//        boolean isConnected = isConnected();
//        boolean isUserPlaying = get_view().isUserPlaying();
//        boolean isNotGuest = isConnected && icsServer.isGuest();
//        int viewMode = this.get_view()._viewMode;
//
////        menu.findItem(R.string.menu_flip).setVisible(isConnected && viewMode != ICSChessView.VIEW_NONE);
//        menu.findItem(R.string.ics_menu_takeback).setVisible(isConnected && isUserPlaying);
//
//        menu.findItem(R.string.ics_menu_adjourn).setVisible(isConnected && isUserPlaying && isNotGuest);
//        menu.findItem(R.string.ics_menu_draw).setVisible(isConnected && isUserPlaying);
//        menu.findItem(R.string.ics_menu_resign).setVisible(isConnected && isUserPlaying);
//        menu.findItem(R.string.ics_menu_abort).setVisible(isConnected && isUserPlaying);
//        menu.findItem(R.string.ics_menu_flag).setVisible(isConnected && isUserPlaying);
//        menu.findItem(R.string.ics_menu_refresh).setVisible(isConnected && isUserPlaying);

//        menu.findItem(R.string.ics_menu_challenges).setVisible(isConnected && viewMode == ICSChessView.VIEW_NONE);
//        menu.findItem(R.string.ics_menu_games).setVisible(isConnected && viewMode == ICSChessView.VIEW_NONE);
//        menu.findItem(R.string.ics_menu_stored).setVisible(isConnected && viewMode == ICSChessView.VIEW_NONE && isNotGuest);
//        menu.findItem(R.string.ics_menu_seek).setVisible(isConnected && viewMode == ICSChessView.VIEW_NONE);
//        menu.findItem(R.string.ics_menu_players).setVisible(isConnected && viewMode == ICSChessView.VIEW_NONE);

//        menu.findItem(R.string.ics_menu_stop_puzzle).setVisible(isConnected && viewMode == ICSChessView.VIEW_PUZZLE);

//        menu.findItem(R.string.ics_menu_unobserve).setVisible(isConnected && viewMode == ICSChessView.VIEW_WATCH);

//        menu.findItem(R.string.ics_menu_console).setVisible(isConnected);

//        for (int i = 0; i < menu.size(); i++) {
//            MenuItem item = menu.getItem(i);
//            String title = item.getTitle().toString();
//            if (title.equals("tell puzzlebot hint")) {
//                item.setVisible(isConnected && viewMode == ICSChessView.VIEW_PUZZLE);
//            } else if (title.equals("tell endgamebot hint")) {
//                item.setVisible(isConnected && viewMode == ICSChessView.VIEW_ENDGAME);
//            } else if (title.equals("tell endgamebot move")) {
//                item.setVisible(isConnected && viewMode == ICSChessView.VIEW_ENDGAME);
//            } else if (title.equals("tell endgamebot stop")) {
//                item.setVisible(isConnected && viewMode == ICSChessView.VIEW_ENDGAME);
//            }
//        }

        return super.onPrepareOptionsMenu(menu);
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {

        Intent i;
        switch (item.getItemId()) {
            case R.string.menu_prefs:
                i = new Intent();
                i.setClass(ICSClient.this, ICSPrefs.class);
                startActivity(i);
                return true;
            case R.string.menu_flip:
                _view.flipBoard();
                _view.paint();
                sendString("refresh");
                return true;
            case R.string.ics_menu_refresh:
                sendString("refresh");
                return true;
            case R.string.ics_menu_takeback:
                sendString("takeback 2");
                return true;
            case R.string.ics_menu_resume:
                sendString("resume");
                return true;
            case R.string.ics_menu_abort:
                sendString("abort");
                // game will stop by way of toast
                return true;
            case R.string.ics_menu_adjourn:
                sendString("adjourn");
                return true;
            case R.string.ics_menu_draw:
                sendString("draw");
                return true;
            case R.string.ics_menu_flag:
                sendString("flag");
                return true;
            case R.string.ics_menu_resign:
                sendString("resign");
                return true;
            case R.string.ics_menu_games:


                return true;
            case R.string.ics_menu_stored:


                sendString("stored");
                return true;
            case R.string.ics_menu_challenges:

                return true;
        }

        // check menu for ending tag of <NR>, then delete tag and allow a command with no return
//        if (item.getTitle() != null) {
//            String itemTitle = item.getTitle().toString();
//            if (itemTitle.length() > 4 && itemTitle.substring(itemTitle.length() - 4).equals("<NR>")) {
//                if (_viewAnimatorLobby.getDisplayedChild() == VIEW_SUB_CONSOLE) {
//                    _editConsole.setText(itemTitle.substring(0, itemTitle.length() - 4));
//                    _editConsole.requestFocus();
//                    _editConsole.setSelection(_editConsole.getText().length());
//                } else {
//                    _editBoard.setText(itemTitle.substring(0, itemTitle.length() - 4));
//                    _editBoard.requestFocus();
//                    _editBoard.setSelection(_editBoard.getText().length());
//                }
//            } else {
//                sendString(item.getTitle().toString());
//            }
//        }
        return true;
    }


    public boolean onKeyDown(int keyCode, KeyEvent event) {
        if (keyCode == KeyEvent.KEYCODE_BACK && event.getRepeatCount() == 0) {
                // @TODO
//                switch(get_view().getViewMode()) {
//                    case ICSChessView.VIEW_PLAY:
//                        new AlertDialog.Builder(ICSClient.this)
//                                .setTitle(ICSClient.this.getString(R.string.ics_menu_abort) + "?")
//                                .setPositiveButton(getString(R.string.alert_yes),
//                                        new DialogInterface.OnClickListener() {
//                                            public void onClick(DialogInterface dialog, int whichButton) {
//                                                dialog.dismiss();
//                                                sendString("abort");
//                                                get_view().stopGame();
//                                                switchToWelcomeView();
//
//                                            }
//                                        })
//                                .setNegativeButton(getString(R.string.alert_no), new DialogInterface.OnClickListener() {
//                                    public void onClick(DialogInterface dialog, int whichButton) {
//                                        dialog.dismiss();
//                                    }
//                                })
//                                .show();
//                        break;
//                    case ICSChessView.VIEW_WATCH:
//                        sendString("unobserve");
//                        get_view().stopGame();
//                        switchToWelcomeView();
//                        break;
//                    case ICSChessView.VIEW_PUZZLE:
//                        sendString("tell puzzlebot stop");
//                        get_view().stopGame();
//                        switchToWelcomeView();
//                        break;
//                    case ICSChessView.VIEW_ENDGAME:
//                        sendString("tell endgamebot stop");
//                        get_view().stopGame();
//                        switchToWelcomeView();
//                        break;
//                    case ICSChessView.VIEW_EXAMINE:
//                        sendString("unexamine");
//                        get_view().stopGame();
//                        switchToWelcomeView();
//                        break;
//                    case ICSChessView.VIEW_NONE:
//                        get_view().stopGame();
//                        switchToWelcomeView();
//                        break;
//                }
//

            } else {
                finish();
            }

        return super.onKeyDown(keyCode, event);
    }

//    public void stopSession(String sReason) {
//        Log.e("stopSession", sReason);
//        if (isFinishing()) {
//            return;
//        }
//        new AlertDialog.Builder(ICSClient.this)
//                .setTitle(R.string.title_error)
//                .setMessage(sReason)
//                .setPositiveButton(R.string.alert_ok,
//                        new DialogInterface.OnClickListener() {
//                            public void onClick(DialogInterface dialog, int whichButton) {
//                                dialog.dismiss();
//                                finish();
//                            }
//                        })
//                .show();
//    }

    protected void makeGamePGN(String sEnd) {

        String sBeg;

        sEnd = sEnd.trim().replaceAll(" +", " ");
        sEnd = sEnd.replaceAll("\\{.*\\}", "");

        String site = "FICS";
        String _FEN1, _FEN2;

        sBeg = sEnd.substring(sEnd.indexOf("1."), sEnd.length());

        if (_bShowClockPGN) {
            sBeg = convertTimeUsedToClock(sBeg);
        } else {
            sBeg = sBeg.replaceAll("\\s*\\([^\\)]*\\)\\s*", " ");  // gets rid of timestamp and parentheses
        }

        //Log.d(TAG, "\n" + sBeg);

        PGN = new StringBuilder("");
        PGN.append("[Event \"" + _matgame.group(7) + "\"]\n");
        PGN.append("[Site \"" + site + "\"]\n");
        PGN.append("[Date \"" + _matgame.group(5) + _matgame.group(6) + "\"]\n");
        PGN.append("[White \"" + _matgame.group(1) + "\"]\n");
        PGN.append("[Black \"" + _matgame.group(3) + "\"]\n");
        PGN.append("[Result \"" + _matgame.group(12) + "\"]\n");
        PGN.append("[WhiteElo \"" + _matgame.group(2) + "\"]\n");
        PGN.append("[BlackElo \"" + _matgame.group(4) + "\"]\n");
        String _minutestoseconds = Integer.toString(Integer.parseInt(_matgame.group(8)) * 60);
        PGN.append("[TimeControl \"" + _minutestoseconds + "+" +
                _matgame.group(9) + "\"]\n");

        if (!_FEN.equals("")) {  // As for now, used for Chess960 FEN.
            _FEN1 = _FEN.substring(0, _FEN.indexOf(" "));
            _FEN2 = _FEN.substring(_FEN.indexOf("P") + 9, _FEN.indexOf("W") - 1);
            if (!_FEN1.equals("rnbqkbnr") || !_FEN2.equals("RNBQKBNR")) {
                PGN.append("[FEN \"" + _FEN1 + "/pppppppp/8/8/8/8/PPPPPPPP/" + _FEN2 + " w KQkq - 0 1" + "\"]\n");
            }
            _FEN = "";  // reset to capture starting FEN for next game
        }

        PGN.append(sBeg + "\n\n");

//        saveGameSDCard();

//        _dlgOver.updateGameResultText(_matgame.group(11)); // game result message sent to dialog
//
//        _dlgOver.setWasPlaying(get_view().getOpponent().length() > 0);
//        _dlgOver.show();
        //_dlgOver.prepare();

    }

    private String convertTimeUsedToClock(String sBeg) {

        int time = Integer.parseInt(_matgame.group(8)), incTime = Integer.parseInt(_matgame.group(9));
        ;

        int incTurn = 1;
        boolean turn = true;

        time = time * 60; // convert minutes to seconds

        whiteClk[0] = time;  // initial start time
        blackClk[0] = time;

        Pattern p = Pattern.compile("\\((\\d+):(\\d+)\\)");
        Matcher m = p.matcher(sBeg);

        while (m.find()) {

            int min = 0, sec = 0, time1 = 0;

            min = min + Integer.parseInt(m.group(1)) * 60;
            sec = Integer.parseInt(m.group(2));
            time1 = min + sec;

            if (turn) {
                time1 = whiteClock(time1, incTurn, incTime);
                turn = false;
            } else {
                time1 = blackClock(time1, incTurn, incTime);
                turn = true;
                incTurn++;
            }

            String clock1 = convertSecondsToClock(time1);

            sBeg = sBeg.replaceFirst("\\((\\d+):(\\d+)\\)", clock1);  // replace time used with clock

        }

        return sBeg;
    }

    private int whiteClock(int time1, int incTurn, int incTime) {

        whiteClk[incTurn] = whiteClk[incTurn - 1] - time1;
        if (incTurn > 1) {
            whiteClk[incTurn] = whiteClk[incTurn] + incTime;
        }

        return whiteClk[incTurn];
    }

    private int blackClock(int time1, int incTurn, int incTime) {

        blackClk[incTurn] = blackClk[incTurn - 1] - time1;
        if (incTurn > 1) {
            blackClk[incTurn] = blackClk[incTurn] + incTime;
        }

        return blackClk[incTurn];
    }

    private String convertSecondsToClock(int time1) {
        String clock, timeString;
        int hours, minutes, seconds;
        hours = time1 / 3600;
        minutes = (time1 % 3600) / 60;
        seconds = time1 % 60;

        timeString = String.format("%02d:%02d:%02d", hours, minutes, seconds);

        clock = "{[%clk " + timeString + "]}";

        return clock;
    }

    protected void gameOverToast(String line) {  // send toast result of the game

        String text = "";
        text = line.substring(line.indexOf(")") + 2, line.indexOf("}"));  // gets name and state of name

        if (line.contains("} 1-0") || line.contains("} 0-1")) {


            if (line.indexOf(" resigns} ") > 0) {  // make translation friendly
                text = text.replace("resigns", getString(R.string.state_resigned));

            } else if (line.indexOf("checkmated") > 0) {
                text = text.replace("checkmated", getString(R.string.state_mate));

            } else if (line.indexOf("forfeits on time") > 0) {
                text = text.replace("forfeits on time", getString(R.string.state_time));

            } else {
                text = getString(R.string.ics_game_over);
            }
        } else if (line.contains("} 1/2-1/2")) {  // draw
            gameToast(String.format(getString(R.string.ics_game_over_format), getString(R.string.state_draw)), false);

            return;
        }

        gameToast(String.format(getString(R.string.ics_game_over_format), text), true);

    }

    public void copyToClipBoard() {
        try {
            @SuppressWarnings("deprecation")
            ClipboardManager cm = (ClipboardManager) getSystemService(CLIPBOARD_SERVICE);
            cm.setText(PGN.toString());
            doToast(getString(R.string.ics_copy_clipboard));
        } catch (Exception e) {
            doToast(getString(R.string.err_copy_clipboard));
            Log.e("ex", e.toString());
        }
    }


    // @TODO move to ChessBoardActivity
    public void SendToApp() {

        try {
            Intent sendIntent = new Intent(Intent.ACTION_SEND);
            sendIntent.putExtra(Intent.EXTRA_SUBJECT, "chess pgn");
            sendIntent.putExtra(Intent.EXTRA_STREAM, Uri.parse("file://" + _sFile));
            sendIntent.setType("application/x-chess-pgn");

            startActivity(sendIntent);

        } catch (Exception e) {

            doToast(getString(R.string.err_send_email));
            Log.e("ex", e.toString());
        }
    }


    private void confirmShow(String title, String text, String sendstring) {
        _dlgConfirm.setSendString(sendstring);
        _dlgConfirm.setText(title, text);
        _dlgConfirm.show();
    }

    public void gameToast(final String text, final boolean stop) {
        if (stop) {
//            get_view().stopGame();
        }
        Toast t = Toast.makeText(this, text, Toast.LENGTH_LONG);
        t.setGravity(Gravity.CENTER, 0, 0);
        t.show();
    }


    public void globalToast(final String text) {
        Toast t = Toast.makeText(this, text, Toast.LENGTH_LONG);
        t.setGravity(Gravity.BOTTOM, 0, 0);
        t.show();
    }

    @Override
    public boolean requestMove(int from, int to) {
        String sMove = Pos.toString(from) + "-" + Pos.toString(to);
        sendString(sMove);
        return true;
    }

    @Override
    protected void onResume() {
        Log.i(TAG, "onResume");

        invalidateOptionsMenu(); // update OptionsMenu

        SharedPreferences prefs = this.getPrefs();


        _ficsHandle = prefs.getString("ics_handle", null);
        _ficsPwd = prefs.getString("ics_password", null);

        _iConsoleCharacterSize = Integer.parseInt(prefs.getString("ICSConsoleCharacterSize", "10"));

        _bAutoSought = prefs.getBoolean("ICSAutoSought", true);

        _bTimeWarning = prefs.getBoolean("ICSTimeWarning", true);
        _TimeWarning = Integer.parseInt(prefs.getString("ICSTimeWarningsecs", "10"));

        _bEndGameDialog = prefs.getBoolean("ICSEndGameDialog", true);

        _bShowClockPGN = prefs.getBoolean("ICSClockPGN", true);

        _bICSVolume = prefs.getBoolean("ICSVolume", true);


        // get rid of notification for tap to play
        NotificationManager notificationManager = (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);
        notificationManager.cancel(0); // 0 is notification id
        _ICSNotifyLifeCycle = false;

        _gameStartSound = Integer.parseInt(prefs.getString("ICSGameStartSound", "1"));

        _notifyON = prefs.getBoolean("ICSGameStartBringToFront", true);

        /////////////////////////////////////////////
//        _adapterHandles = new ArrayAdapter<String>(this, android.R.layout.simple_list_item_1);
//        _arrayPasswords = new ArrayList<String>();
//
//        if (_ficsHandle != null) {
//            _adapterHandles.add(_ficsHandle);
//            _arrayPasswords.add(_ficsPwd);
//        }
//
//        try {
//            JSONArray jArray = new JSONArray(prefs.getString("ics_handle_array", "guest"));
//            JSONArray jArrayPasswords = new JSONArray(prefs.getString("ics_password_array", ""));
//            for (int i = 0; i < jArray.length(); i++) {
//                _adapterHandles.add(jArray.getString(i));
//                _arrayPasswords.add(jArrayPasswords.getString(i));
//            }
//        } catch (JSONException e) {
//            // TODO Auto-generated catch block
//            e.printStackTrace();
//        }
//
//        int t = 0;
//        boolean g = false;
//        for (int i = 0; i < _adapterHandles.getCount(); i++) {
//            String x = _adapterHandles.getItem(i).toString();
//            if (x.equals(_ficsHandle)) {
//                t++;
//            }
//            if (x.equals("guest")) {
//                g = true;  // flag to check if guest is in the spinner
//            }
//        }
//        if (t > 1) {  // work around for remove - delete every occurrence and add latest - this will add latest password
//            while (t > 0) {
//                _adapterHandles.remove(_ficsHandle);
//                _arrayPasswords.remove(_adapterHandles.getPosition(_ficsHandle) + 1);
//                t--;
//            }
//            _adapterHandles.add(_ficsHandle);
//            _arrayPasswords.add(_ficsPwd);
//        }
//
//        if (g == false) {
//            _adapterHandles.add("guest"); // if no guest then add guest
//            _arrayPasswords.add(" ");
//        }
//
//        _spinnerHandles.setAdapter(_adapterHandles);
//
//        _spinnerHandles.setSelection(_adapterHandles.getPosition(_ficsHandle));
//
//
//        /////////////////////////////////////////////////////////////////
//
//        if (_ficsHandle == null) {
//            _ficsHandle = "guest";
//            _ficsPwd = "";
//        }


        String sTmp = prefs.getString("NotificationUri", null);
        if (sTmp == null) {
            _ringNotification = null;
        } else {
            Uri tmpUri = Uri.parse(sTmp);
            _ringNotification = RingtoneManager.getRingtone(this, tmpUri);
        }

        if (icsServer != null) {
            icsServer.addListener(this);
        }

        openLoginDialogOfNotConnected();

        super.onResume();
    }

    public void openLoginDialogOfNotConnected() {
        if (icsServer != null && _dlgLogin != null && !icsServer.isConnected()) {
            _dlgLogin.show();
        }
    }

    public String get_ficsHandle() {
        return _ficsHandle;
    }

    public boolean is_bTimeWarning() {
        return _bTimeWarning;
    }

    public int get_TimeWarning() {
        return _TimeWarning;
    }

    public int get_gameStartSound() {
        return _gameStartSound;
    }

    public String get_whiteHandle() {
        if (_whiteHandle != null) {
            return _whiteHandle;
        }  // _whiteHandle is mat1 and mat2 match
        try {
            return _matgame.group(1);
        } catch (Exception e) { // return _matgame match
            return "";
        }
    }

    public String get_whiteRating() {
        if (_whiteRating != null) {
            return _whiteRating;
        }
        try {
            return _matgame.group(2);
        } catch (Exception e) {
            return "";
        }
    }

    public String get_blackHandle() {
        if (_blackHandle != null) {
            return _blackHandle;
        }
        try {
            return _matgame.group(3);
        } catch (Exception e) {
            return "";
        }
    }

    public String get_blackRating() {
        if (_blackRating != null) {
            return _blackRating;
        }
        try {
            return _matgame.group(4);
        } catch (Exception e) {
            return "";
        }
    }

    public boolean isConnected() {
        if (this.icsServer != null) {
            return this.icsServer.isConnected();
        }
        return false;
    }

    public void notificationAPP() {

        if (_notifyON && _ICSNotifyLifeCycle) {

            Intent intent = new Intent(this, ICSClient.class);
            PendingIntent pendingIntent = PendingIntent.getActivity(this, 0, intent, 0);

            NotificationManager notificationManager = (NotificationManager) this.getSystemService(Context.NOTIFICATION_SERVICE);

            Notification.Builder builder = new Notification.Builder(this);
            builder.setContentIntent(pendingIntent)
                    .setSmallIcon(R.drawable.chess)
                    .setWhen(System.currentTimeMillis())
                    .setAutoCancel(true)
                    .setLights(Color.CYAN, 100, 100)
                    .setContentTitle(getString(R.string.ics_notification_title))
                    .setContentText(getString(R.string.ics_notification_text));

            Notification notification = builder.getNotification();

            notificationManager.notify(0, notification);

        }
    }

    @Override
    protected void onPause() {
        Log.i(TAG, "onPause");

        if (icsServer != null) {
            icsServer.removeListener(this);
        }

        SharedPreferences.Editor editor = this.getPrefs().edit();

        editor.putString("ics_handle", _ficsHandle);
        editor.putString("ics_password", _ficsPwd);

//        JSONArray jArray = new JSONArray();
//        JSONArray jArrayPasswords = new JSONArray();
//        for (int i = 0; i < _adapterHandles.getCount(); i++) {
//            jArray.put(_adapterHandles.getItem(i));
//            jArrayPasswords.put(_arrayPasswords.get(i));
//        }
//        editor.putString("ics_handle_array", jArray.toString());
//        editor.putString("ics_password_array", jArrayPasswords.toString());

        editor.putBoolean("ICSVolume", _bICSVolume);

        editor.commit();

        _ICSNotifyLifeCycle = true;

        super.onPause();
    }

    @Override
    protected void onRestart() {
        Log.i(TAG, "onRestart");
        super.onRestart();
    }

    @Override
    protected void onStart() {
        Log.i(TAG, "onStart");
        super.onStart();

        if (bindService(new Intent(ICSClient.this, ICSServer.class), mConnection, Context.BIND_AUTO_CREATE)) {
            Log.i(TAG, "Bind to ICSServer");
        } else {
            globalToast("Could not init remote chess process");
            Log.e(TAG, "Error: The requested service doesn't exist, or this client isn't allowed access to it.");
        }
    }

    @Override
    protected void onStop() {
        Log.i(TAG, "onStop");
        super.onStop();

        unbindService(mConnection);
    }

    @Override
    protected void onDestroy() {
        Log.i(TAG, "onDestroy");

        if (icsServer != null) {
            icsServer.removeListener(this);
        }
        super.onDestroy();
    }

    public void sendString(String s) {
        if (icsServer != null && !icsServer.sendString(s)) {
            try {
                new AlertDialog.Builder(ICSClient.this)
                        .setTitle(R.string.title_error)
                        .setMessage(getString(R.string.ics_lost_connection))
                        .setPositiveButton(getString(R.string.alert_ok),
                                new DialogInterface.OnClickListener() {
                                    public void onClick(DialogInterface dialog, int whichButton) {
                                        dialog.dismiss();
                                        finish();
                                    }
                                })
                        .show();
            } catch (Exception ex) {
                Log.e(TAG, ex.toString());
            }
        }
    }

    public void onItemClick(AdapterView<?> arg0, View arg1, int arg2, long arg3) {
        if (arg0 == _listChallenges) {
            if (_mapChallenges.size() > arg2) {
                HashMap<String, String> m = _mapChallenges.get(arg2);
                Log.i("onItemClick", "item " + m.get("play"));
                sendString("play " + m.get("play"));
            }
        } else if (arg0 == _listPlayers) {
            if (_mapPlayers.size() > arg2) {
                HashMap<String, String> m = _mapPlayers.get(arg2);
                Log.i("onItemClick", "item " + m.get("text_name"));
                _dlgPlayer.opponentName(m.get("text_name"));
                _dlgPlayer._tvPlayerListConsole.setText("");  // clear TextView
                _dlgPlayer.show();

            }
        } else if (arg0 == _listGames) {
            if (_mapGames.size() > arg2) {
                HashMap<String, String> m = _mapGames.get(arg2);
                Log.i("onItemClick", "item " + m.get("text_name1"));
                sendString("observe " + m.get("text_name1"));
                sendString("refresh");
//                get_view().setViewMode(ICSChessView.VIEW_WATCH);
//                get_view().setGameNum(Integer.parseInt((String) m.get("nr")));
//                switchToBoardView();
            }
        } else if (arg0 == _listStored) {
            if (_mapStored.size() > arg2) {
                HashMap<String, String> m = _mapStored.get(arg2);
                Log.i("onItemClick", "item " + m.get("text_name_stored"));
                sendString("match " + m.get("text_name_stored"));
//                switchToBoardView();
            }
        } else if (arg0 == _listWelcome) {
            String selected = _adapterWelcome.getItem(arg2);
            if (selected.equals(getString(R.string.ics_menu_games))) {
//                switchToGamesView();
            } else if (selected.equals(getString(R.string.ics_menu_players))) {
//                switchToPlayersView();
            } else if (selected.equals(getString(R.string.ics_menu_challenges))) {
                switchToChallengeView();
            }  else if (selected.equals(getString(R.string.ics_menu_seek))) {
                 _dlgMatch._rbSeek.setChecked(true);
                 _dlgMatch._rbSeek.performClick();
                 _dlgMatch.show();
            } else if (selected.equals(getString(R.string.ics_menu_top_blitz))) {
                sendString("obs /b");
//                get_view().setViewMode(ICSChessView.VIEW_WATCH);
            } else if (selected.equals(getString(R.string.ics_menu_top_standard))) {
                sendString("obs /s");
//                get_view().setViewMode(ICSChessView.VIEW_WATCH);
            } else if (selected.equals(getString(R.string.ics_menu_puzzlebot_mate))) {
                sendString("tell puzzlebot getmate");
            } else if (selected.equals(getString(R.string.ics_menu_puzzlebot_tactics))) {
                sendString("tell puzzlebot gettactics");
            } else if (selected.equals(getString(R.string.ics_menu_puzzlebot_study))) {
                sendString("tell puzzlebot getstudy");
            } else if (selected.equals(getString(R.string.ics_menu_console))) {
//                switchToConsoleView();
            } else if (selected.equals(getString(R.string.menu_prefs))) {
                Intent i = new Intent();
                i.setClass(ICSClient.this, ICSPrefs.class);
                startActivity(i);
            } else if (selected.equals(getString(R.string.menu_help))) {
                Intent i = new Intent();
                i.setClass(ICSClient.this, HtmlActivity.class);
                i.putExtra(HtmlActivity.HELP_MODE, "help_online");
                startActivity(i);
            }
        }
    }


    public void switchToChallengeView() {
        sendString("sought");
        _tvHeader.setText(R.string.ics_menu_challenges);
    }


    public void soundNotification() {
        if (_ringNotification != null) {
            _ringNotification.play();
        }
    }

    // @TODO move to BaseActivity
    public void vibration(int seq) {
        try {
            int v1, v2;
            if (seq == 1) {
                v1 = 200;    // increase
                v2 = 500;
            } else {
                v1 = 500;    // decrease
                v2 = 200;
            }
            long[] pattern = {500, v1, 100, v2};
            Vibrator v = (Vibrator) getSystemService(Context.VIBRATOR_SERVICE);
            v.vibrate(pattern, -1);
        } catch (Exception e) {
            Log.e(TAG, "vibrator process error", e);
        }

    }

    @Override
    public void OnDialogResult(int requestCode, Bundle data) {
        switch (requestCode) {
            case REQUEST_LOGIN:
                icsServer.startSession("freechess.org", 23, data.getString("handle"), data.getString("pwd"), "fics% ");
                break;
        }
    }

    public class ComparatorHashName implements java.util.Comparator<HashMap<String, String>> {
        public int compare(HashMap<String, String> a, HashMap<String, String> b) {
            String sA = a.get("text_name"), sB = b.get("text_name");
            if (sA != null && sB != null) {
                return sA.compareToIgnoreCase(sB);
            }
            return 0;
        }
    }

    public class ComparatorHashRating implements java.util.Comparator<HashMap<String, String>> {
        public int compare(HashMap<String, String> a, HashMap<String, String> b) {
            String sA = a.get("text_rating"), sB = b.get("text_rating");
            if (sA != null && sB != null) {
                return -1 * sA.compareToIgnoreCase(sB);
            }
            return 0;
        }
    }

    @Override
    public void OnLoginSuccess() {
        sendString("style 12");
        sendString("-channel 4"); // guest
        sendString("-channel 53"); // guest chat
        sendString("set kibitz 1"); // for puzzlebot
        sendString("set gin 0"); // current server game results - turn off - some clients turn it on
        sendString("set tzone " + tz.getDisplayName(false, TimeZone.SHORT));  // sets timezone

//        _adapterWelcome.add(getString(R.string.ics_menu_games));
//        _adapterWelcome.add(getString(R.string.ics_menu_players));
//        _adapterWelcome.add(getString(R.string.ics_menu_challenges));
//        _adapterWelcome.add(getString(R.string.ics_menu_seek));
//        _adapterWelcome.add(getString(R.string.ics_menu_top_blitz));
//        _adapterWelcome.add(getString(R.string.ics_menu_top_standard));
//        _adapterWelcome.add(getString(R.string.ics_menu_puzzlebot_mate));
//        _adapterWelcome.add(getString(R.string.ics_menu_puzzlebot_tactics));
//        _adapterWelcome.add(getString(R.string.ics_menu_puzzlebot_study));
//        _adapterWelcome.add(getString(R.string.ics_menu_console));
//        _adapterWelcome.add(getString(R.string.menu_prefs));
//        _adapterWelcome.add(getString(R.string.menu_help));
//
//        _adapterWelcome.notifyDataSetChanged();
    }

    @Override
    public void OnLoginFailed() {
        doToast("Could not log you in");
    }

    @Override
    public void OnLoggingIn() {
        doToast("Logging you in");
    }

    @Override
    public void OnSessionEnded() {
        doToast("ICS service ended unexpectedly");
        finish();
    }

    @Override
    public void OnError() {
        Log.i(TAG, "OnError");
        new AlertDialog.Builder(ICSClient.this)
            .setTitle(ICSClient.this.getString(R.string.ics_error))
            .setPositiveButton(getString(R.string.alert_ok),
                    new DialogInterface.OnClickListener() {
                        public void onClick(DialogInterface dialog, int whichButton) {
                            dialog.dismiss();
                            finish();
                        }
                    })
            .show();
    }

    @Override
    public void OnPlayerList(ArrayList<HashMap<String, String>> playerList) {
//        _mapPlayers.clear();
//        for (int i = 0; i < playerList.size(); i++) {
//            _mapPlayers.add(playerList.get(i));
//        }
//        Collections.sort(_mapPlayers, new ComparatorHashRating());
//        _adapterPlayers.notifyDataSetChanged();
    }

    @Override
    public void OnBoardUpdated(String gameLine, String handle) {
//        if (get_view().parseGame(gameLine, handle)) {
//            switchToBoardView();
//        } else {
//            Log.i(TAG, "Could not parse game line " + gameLine);
//        }
    }

    @Override
    public void OnChallenged(HashMap<String, String> challenge) {
        new AlertDialog.Builder(ICSClient.this)
            .setTitle(ICSClient.this.getString(R.string.title_challenge))
            .setMessage(challenge.get("opponent") +
                    " [" + challenge.get("rating") + "]\n " +
                    " challenges you for a " +
                    challenge.get("minutes") + " min.+" +
                    challenge.get("seconds") + " s " +
                    challenge.get("type") + " " +
                    challenge.get("num") + ".\n" +
                    "Do you wish to accept?")
            .setPositiveButton(getString(R.string.alert_yes),
                    new DialogInterface.OnClickListener() {
                        public void onClick(DialogInterface dialog, int whichButton) {
                            sendString("accept");
                            dialog.dismiss();
                        }
                    })
            .setNegativeButton(getString(R.string.alert_no), new DialogInterface.OnClickListener() {
                public void onClick(DialogInterface dialog, int whichButton) {
                    dialog.dismiss();
                }
            })
            .show();
    }

    @Override
    public void OnIllegalMove() {

    }

    @Override
    public void OnSeekNotAvailable() {
        globalToast("That seek is not available");
    }

    @Override
    public void OnPlayGameStarted(String whiteHandle, String blackHandle, String whiteRating, String blackRating) {
        globalToast("Game initialized");
    }

    @Override
    public void OnGameNumberUpdated(int number) {
        if (number > 0) {
//            get_view().setGameNum(number);
        }
    }

    @Override
    public void OnOpponentRequestsAbort() {
        confirmShow(getString(R.string.title_offers_abort), getString(R.string.ics_offers_abort), "abort");
    }

    @Override
    public void OnOpponentRequestsAdjourn() {
        confirmShow(getString(R.string.title_offers_adjourn), getString(R.string.ics_offers_adjourn), "adjourn");
    }

    @Override
    public void OnOpponentOffersDraw() {
        confirmShow(getString(R.string.title_offers_draw), getString(R.string.ics_offers_draw), "draw");
    }

    @Override
    public void OnOpponentRequestsTakeBack() {
        confirmShow(getString(R.string.title_offers_takeback), getString(R.string.ics_offers_takeback), "accept");
    }

    @Override
    public void OnAbortConfirmed() {
        gameToast("Game aborted by mutual agreement", true);
//        get_view().setStopPlaying();
    }

    @Override
    public void OnPlayGameResult(String message) {
        gameToast(message, true);
//        get_view().setStopPlaying();
    }

    @Override
    public void OnPlayGameStopped() {
//        get_view().setStopPlaying();
    }

    @Override
    public void OnYourRequestSended() {
        gameToast(getString(R.string.ics_request_sent), false);
    }

    @Override
    public void OnChatReceived() {

    }

    @Override
    public void OnResumingAdjournedGame() {
        gameToast("Resuming adjourned game", false);
    }

    @Override
    public void OnAbortedOrAdjourned() {
        gameToast("Game stopped (aborted or adjourned)", false);
    }

    @Override
    public void OnObservingGameStarted() {
        globalToast("Observing a game");
    }

    @Override
    public void OnObservingGameStopped() {
        globalToast("No longer observing the game");
    }

    @Override
    public void OnPuzzleStarted() {
        globalToast("Puzzle started");
    }

    @Override
    public void OnPuzzleStopped() {
        globalToast("Puzzle stopped");
    }

    @Override
    public void OnExaminingGameStarted() {
        globalToast("Examining a game");
    }

    @Override
    public void OnExaminingGameStopped() {
        globalToast("No longer examining the game");
    }

    @Override
    public void OnSoughtResult(ArrayList<HashMap<String, String>> soughtList) {
//        _mapChallenges.clear();
//        for (int i = 0; i < soughtList.size(); i++) {
//            _mapChallenges.add(soughtList.get(i));
//        }
//        _adapterChallenges.notifyDataSetChanged();
    }

    @Override
    public void OnGameListResult(ArrayList<HashMap<String, String>> games) {
//        _mapGames.clear();
//        for (int i = 0; i < games.size(); i++) {
//            _mapGames.add(games.get(i));
//        }
//
//        _adapterGames.notifyDataSetChanged();
    }

    @Override
    public void OnStoredListResult(ArrayList<HashMap<String, String>> games) {
//        _mapStored.clear();
//        for (int i = 0; i < games.size(); i++) {
//            _mapStored.add(games.get(i));
//        }
//        _adapterStored.notifyDataSetChanged();
    }

    @Override
    public void OnEndGameResult(int state) {
        int res = UI.chessStateToR(state);
        gameToast(getString(res), true);
//        this.get_view().setStopPlaying();
    }

    @Override
    public void OnConsoleOutput(String buffer) {
//        addConsoleText(buffer);
    }

    private class AlternatingRowColorAdapter extends SimpleAdapter {

        public AlternatingRowColorAdapter(Context context, List<HashMap<String, String>> data, int resource, String[] from, int[] to) {
            super(context, data, resource, from, to);
        }

        @Override
        public View getView(int position, View convertView, ViewGroup parent) {
            View view = super.getView(position, convertView, parent);
            view.setBackgroundColor(position % 2 == 0 ? 0x55888888 : 0x55666666);
            return view;
        }
    }
}

