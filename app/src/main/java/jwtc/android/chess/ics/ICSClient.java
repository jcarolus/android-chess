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
import java.util.Timer;
import java.util.TimerTask;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.json.JSONArray;
import org.json.JSONException;

import jwtc.android.chess.*;

public class ICSClient extends MyBaseActivity implements OnItemClickListener, ICSListener {

    private ICSServer icsServer;
    protected String _sConsoleEditText;
    private String _server, _handle, _pwd, _ficsHandle, _ficsPwd, _sFile, _FEN = "", _whiteRating, _blackRating, _whiteHandle, _blackHandle;
    private int _port, _serverType, _TimeWarning, _gameStartSound, _iConsoleCharacterSize;
    private boolean _bAutoSought, _bTimeWarning, _bEndGameDialog, _bShowClockPGN,
            _notifyON, _bConsoleText, _bICSVolume, _ICSNotifyLifeCycle;
    private Button _butLogin;
    private TextView _tvHeader, _tvConsole, _tvPlayConsole;
//	public ICSChatDlg _dlgChat;

    private EditText _editHandle, _editPwd, _editConsole, _editBoard;

    private Spinner _spinnerHandles;
    private ArrayAdapter<String> _adapterHandles;
    private ArrayList<String> _arrayPasswords;

    //private EditText _editPrompt;
    private ListView _listChallenges, _listPlayers, _listGames, _listStored;
    private ICSChessView _view;
    private ChessViewBase _viewbase;
    protected ICSMatchDlg _dlgMatch;
    private ICSPlayerDlg _dlgPlayer;
    private ICSConfirmDlg _dlgConfirm;
    private ICSChatDlg _dlgChat;
    private ICSGameOverDlg _dlgOver;
    private StringBuilder PGN;
    private ViewAnimator _viewAnimatorMain, _viewAnimatorLobby;
    private ScrollView _scrollConsole, _scrollPlayConsole;

    private Ringtone _ringNotification;

    private TimeZone tz = TimeZone.getDefault();

    private Matcher _matgame;

    private ArrayList<HashMap<String, String>> _mapChallenges = new ArrayList<HashMap<String, String>>();
    private ArrayList<HashMap<String, String>> _mapPlayers = new ArrayList<HashMap<String, String>>();
    private ArrayList<HashMap<String, String>> _mapGames = new ArrayList<HashMap<String, String>>();
    private ArrayList<HashMap<String, String>> _mapStored = new ArrayList<HashMap<String, String>>();

    private AlternatingRowColorAdapter _adapterGames, _adapterPlayers, _adapterChallenges, _adapterStored;

    public static final String TAG = "ICSClient";

    protected static final int SERVER_FICS = 1;

    protected static final int VIEW_MAIN_BOARD = 0;
    protected static final int VIEW_MAIN_LOBBY = 1;
    protected static final int VIEW_MAIN_NOT_CONNECTED = 2;

    protected static final int VIEW_SUB_PLAYERS = 0;
    protected static final int VIEW_SUB_GAMES = 1;
    protected static final int VIEW_SUB_WELCOME = 2;
    protected static final int VIEW_SUB_CHALLENGES = 3;
    protected static final int VIEW_SUB_PROGRESS = 4;
    protected static final int VIEW_SUB_LOGIN = 5;
    protected static final int VIEW_SUB_CONSOLE = 6;
    protected static final int VIEW_SUB_STORED = 7;

    protected static final int DECREASE = 0;

    protected static int[] whiteClk = new int[200]; // PGN time clock
    protected static int[] blackClk = new int[200];

    private ImageButton butQuickSoundOn, butQuickSoundOff;

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
            icsServer.startSession("freechess.org", 23, _handle, _pwd, "fics% ");
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

        this.makeActionOverflowMenuShown();

        // needs to be called first because of chess statics init
        _view = new ICSChessView(this);
        _view.init();

        _dlgMatch = new ICSMatchDlg(this);
        _dlgPlayer = new ICSPlayerDlg(this);
        _dlgConfirm = new ICSConfirmDlg(this);
        _dlgChat = new ICSChatDlg(this);
        _dlgOver = new ICSGameOverDlg(this);

        _tvHeader = (TextView) findViewById(R.id.TextViewHeader);
        _tvHeader.setGravity(Gravity.CENTER);

        //_dlgChat = new ICSChatDlg(this);

        _serverType = SERVER_FICS;
        _iConsoleCharacterSize = 10;
        _bAutoSought = true;
        _bTimeWarning = true;
        _bEndGameDialog = true;
        _bShowClockPGN = true;

        _adapterChallenges = new AlternatingRowColorAdapter(ICSClient.this, _mapChallenges, R.layout.ics_seek_row,
                new String[]{"text_game", "text_name", "text_rating"}, new int[]{R.id.text_game, R.id.text_name, R.id.text_rating});

        _listChallenges = (ListView) findViewById(R.id.ICSChallenges);
        _listChallenges.setAdapter(_adapterChallenges);
        _listChallenges.setOnItemClickListener(this);

        _adapterPlayers = new AlternatingRowColorAdapter(ICSClient.this, _mapPlayers, R.layout.ics_player_row,
                new String[]{"text_name", "text_rating"}, new int[]{R.id.text_name, R.id.text_rating});

        _listPlayers = (ListView) findViewById(R.id.ICSPlayers);
        _listPlayers.setAdapter(_adapterPlayers);
        _listPlayers.setOnItemClickListener(this);

        _adapterGames = new AlternatingRowColorAdapter(ICSClient.this, _mapGames, R.layout.ics_game_row,
                new String[]{"nr", "text_type", "text_name1", "text_name2", "text_rating1", "text_rating2", "text_time1", "text_time2"},
                new int[]{R.id.nr, R.id.text_type, R.id.text_name1, R.id.text_name2, R.id.text_rating1, R.id.text_rating2, R.id.text_time1, R.id.text_time2});

        _listGames = (ListView) findViewById(R.id.ICSGames);
        _listGames.setAdapter(_adapterGames);
        _listGames.setOnItemClickListener(this);

        _adapterStored = new AlternatingRowColorAdapter(ICSClient.this, _mapStored, R.layout.ics_stored_row,
                new String[]{"nr_stored", "color_stored", "text_name_stored", "available_stored"}, new int[]{R.id.nr_stored, R.id.color_stored, R.id.text_name_stored, R.id.available_stored});

        _listStored = (ListView) findViewById(R.id.ICSStored);
        _listStored.setAdapter(_adapterStored);
        _listStored.setOnItemClickListener(this);

        _viewAnimatorMain = (ViewAnimator) findViewById(R.id.ViewAnimatorMain);
        _viewAnimatorMain.setOutAnimation(this, R.anim.slide_left);
        _viewAnimatorMain.setInAnimation(this, R.anim.slide_right);

        _viewAnimatorLobby = (ViewAnimator) findViewById(R.id.ViewAnimatorLobby);
        _viewAnimatorLobby.setOutAnimation(this, R.anim.slide_left);
        _viewAnimatorLobby.setInAnimation(this, R.anim.slide_right);

        _scrollConsole = (ScrollView) findViewById(R.id.ScrollICSConsole);
        _scrollPlayConsole = (ScrollView) findViewById(R.id.ScrollPlayConsole);

        /*
        ImageButton butClose = (ImageButton)findViewById(R.id.ButtonBoardClose);
        butClose.setOnClickListener(new OnClickListener() {
        	public void onClick(View arg0) {
        		unsetBoard();
        		switchToWelcomeView();
        	}
        });
        */

        ImageButton butQuick = (ImageButton) findViewById(R.id.ButtonICSQuickCmd);
        if (butQuick != null) {
            butQuick.setOnClickListener(new OnClickListener() {
                public void onClick(View arg0) {
                    //showMenu();
                    openOptionsMenu();
                }
            });
        }

        ImageButton butQuick2 = (ImageButton) findViewById(R.id.ButtonICSConsoleQuickCmd);
        if (butQuick2 != null) { // crashes reported on this being null
            butQuick2.setOnClickListener(new OnClickListener() {
                public void onClick(View arg0) {
                    //showMenu();
                    openOptionsMenu();
                }
            });
        }

        butQuickSoundOn = (ImageButton) findViewById(R.id.ButtonICSSoundOn);
        butQuickSoundOff = (ImageButton) findViewById(R.id.ButtonICSSoundOff);
        if (butQuickSoundOn != null && butQuickSoundOff != null) {
            butQuickSoundOn.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View view) {
                    _bICSVolume = false;
                    set_fVolume(0.0f);
                    butQuickSoundOn.setVisibility(View.GONE);
                    butQuickSoundOff.setVisibility(View.VISIBLE);
                }
            });
            butQuickSoundOff.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View view) {
                    _bICSVolume = true;
                    set_fVolume(1.0f);
                    butQuickSoundOff.setVisibility(View.GONE);
                    butQuickSoundOn.setVisibility(View.VISIBLE);
                }
            });
        }

        ImageButton butCloseConsole = (ImageButton) findViewById(R.id.ICSCloseConsole);
        if (butCloseConsole != null) {
            butCloseConsole.setOnClickListener(new OnClickListener() {
                public void onClick(View arg0) {
                    if (isConnected()) {
                        switchToBoardView();
                    } else {
                        finish();
                    }
                }
            });
        }
        //

        ImageButton butChat = (ImageButton) findViewById(R.id.ButtonICSChat);
        if (butChat != null) {
            butChat.setOnClickListener(new OnClickListener() {
                public void onClick(View arg0) {
                    //ICSChatDlg
                    _dlgChat.show();
                    _bConsoleText = true;
                    _dlgChat.prepare();
                }
            });
        }

        _editHandle = (EditText) findViewById(R.id.EditICSHandle);
        _editHandle.setOnEditorActionListener(new EditText.OnEditorActionListener() {
            @Override
            public boolean onEditorAction(TextView v, int actionId, KeyEvent event) {
                if (actionId == EditorInfo.IME_ACTION_NEXT) {
                    _editPwd.setText(""); // clear password box for new handle
                    return false;  // this allows focus to the password box
                }
                return false;
            }
        });

        _editPwd = (EditText) findViewById(R.id.EditICSPwd);

        _spinnerHandles = (Spinner) findViewById(R.id.SpinnerLoginPresets);

        _spinnerHandles.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {

            @Override
            public void onItemSelected(AdapterView<?> parentView, View selectedItemView, int position, long id) {
                _editHandle.setText(_spinnerHandles.getSelectedItem().toString());
                _editPwd.setText(_arrayPasswords.get(position));
                if (_arrayPasswords.get(position).length() < 2) {
                    _editPwd.setText("");
                }
                Log.d(TAG, _spinnerHandles.getSelectedItem().toString());
            }

            @Override
            public void onNothingSelected(AdapterView<?> parentView) {
                Log.d(TAG, "nothing selected in spinner");
            }
        });

        /////////////////////////
        final Handler actionHandler = new Handler();
        final Runnable runnable = new Runnable() {
            @Override
            public void run() {
                new AlertDialog.Builder(ICSClient.this)
                        .setTitle("Delete entry")
                        .setMessage("Are you sure you want to delete " + _spinnerHandles.getSelectedItem().toString() + "?")
                        .setPositiveButton(android.R.string.yes, new DialogInterface.OnClickListener() {
                            public void onClick(DialogInterface dialog, int which) {
                                String newData = _spinnerHandles.getSelectedItem().toString();
                                _adapterHandles.remove(newData);
                                _adapterHandles.notifyDataSetChanged();
                                _arrayPasswords.remove(_spinnerHandles.getSelectedItemPosition());
                                _editHandle.setText("");
                                _editPwd.setText("");
                            }
                        })
                        .setNegativeButton(android.R.string.no, new DialogInterface.OnClickListener() {
                            public void onClick(DialogInterface dialog, int which) {
                                // do nothing
                            }
                        })
                        .setIcon(android.R.drawable.ic_dialog_alert)
                        .show();
            }
        };

        _spinnerHandles.setOnTouchListener(new View.OnTouchListener() { // simulate long press on spinner
            @Override
            public boolean onTouch(View v, MotionEvent event) {
                if (event.getAction() == MotionEvent.ACTION_DOWN) {
                    actionHandler.postDelayed(runnable, 650);
                } else if (event.getAction() == MotionEvent.ACTION_UP) {
                    actionHandler.removeCallbacks(runnable);
                }
                return false;
            }
        });
        /////////////////////

        _butLogin = (Button) findViewById(R.id.ButICSLogin);
        if (_butLogin != null) {
            _butLogin.setOnClickListener(new View.OnClickListener() {
                public void onClick(View arg0) {
                    startSession(_editHandle.getText().toString(), _editPwd.getText().toString());
                }
            });
        }
        _tvConsole = (TextView) findViewById(R.id.TextViewICSBoardConsole);
        _tvPlayConsole = (TextView) findViewById(R.id.TextViewICSPlayConsole);

        OnKeyListener okl = new OnKeyListener() {
            public boolean onKey(View v, int keyCode, KeyEvent event) {
                if (((event.getAction() == KeyEvent.ACTION_DOWN) && (keyCode == KeyEvent.KEYCODE_ENTER)) ||
                        event.getAction() == EditorInfo.IME_ACTION_DONE
                ) {
                    // Perform action on key press
                    EditText et = (EditText) v;
                    _sConsoleEditText = et.getText().toString();

                    _bConsoleText = true;  // show text when user types to ICS
                    sendString(_sConsoleEditText);
                    et.setText("");

                    return true;
                }
                return false;
            }
        };

        _editConsole = (EditText) findViewById(R.id.EditICSConsole);
        if (_editConsole != null) {
            _editConsole.setTextColor(getResources().getColor(android.R.color.white));
            _editConsole.setSingleLine(true);
            _editConsole.setOnKeyListener(okl);
        }
        _editBoard = (EditText) findViewById(R.id.EditICSBoard);
        if (_editBoard != null) {
            _editBoard.setSingleLine(true);
            _editBoard.setOnKeyListener(okl);
        }

        Button butReg = (Button) findViewById(R.id.ButICSRegister);
        if (butReg != null) {
            butReg.setOnClickListener(new View.OnClickListener() {
                public void onClick(View arg0) {
                    try {
                        Intent i = new Intent();
                        i.setAction(Intent.ACTION_VIEW);
                        i.setData(Uri.parse("http://www.freechess.org/Register/index.html"));
                        startActivity(i);
                    } catch (Exception ex) {

                        doToast("Could not go to registration page");
                    }
                }
            });
        }

        final int REQUEST_CODE_ASK_PERMISSIONS = 123;  // Ask permission to write to external storage for android 6 and above
        int hasWritePermission =
                ContextCompat.checkSelfPermission(ICSClient.this, Manifest.permission.WRITE_EXTERNAL_STORAGE);
        if (hasWritePermission != PackageManager.PERMISSION_GRANTED) {

            if (!ActivityCompat.shouldShowRequestPermissionRationale(ICSClient.this, Manifest.permission.WRITE_EXTERNAL_STORAGE)) {
                ActivityCompat.requestPermissions(ICSClient.this, new String[]{Manifest.permission.WRITE_EXTERNAL_STORAGE},
                        REQUEST_CODE_ASK_PERMISSIONS);
            } else {
                globalToast("NEED PERMISSION TO WRITE GAMES TO SD CARD"); // Show toast if user denied permission
                ActivityCompat.requestPermissions(ICSClient.this, new String[]{Manifest.permission.WRITE_EXTERNAL_STORAGE},
                        REQUEST_CODE_ASK_PERMISSIONS);
            }
        }


        _ringNotification = null;

        switchToLoginView();

        Log.i("ICSClient", "onCreate");
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {

        menu.add(Menu.NONE, R.string.menu_prefs, Menu.NONE, R.string.menu_prefs);
        menu.add(Menu.NONE, R.string.menu_flip, Menu.NONE, R.string.menu_flip);
        menu.add(Menu.NONE, R.string.ics_menu_takeback, Menu.NONE, R.string.ics_menu_takeback);
        menu.add(Menu.NONE, R.string.ics_menu_adjourn, Menu.NONE, R.string.ics_menu_adjourn);
        menu.add(Menu.NONE, R.string.ics_menu_draw, Menu.NONE, R.string.ics_menu_draw);
        menu.add(Menu.NONE, R.string.ics_menu_resign, Menu.NONE, R.string.ics_menu_resign);
        menu.add(Menu.NONE, R.string.ics_menu_abort, Menu.NONE, R.string.ics_menu_abort);
        menu.add(Menu.NONE, R.string.ics_menu_flag, Menu.NONE, R.string.ics_menu_flag);
        menu.add(Menu.NONE, R.string.ics_menu_refresh, Menu.NONE, R.string.ics_menu_refresh);
        menu.add(Menu.NONE, R.string.ics_menu_challenges, Menu.NONE, R.string.ics_menu_challenges);
        menu.add(Menu.NONE, R.string.ics_menu_games, Menu.NONE, R.string.ics_menu_games);
        menu.add(Menu.NONE, R.string.ics_menu_stored, Menu.NONE, R.string.ics_menu_stored);
        menu.add(Menu.NONE, R.string.ics_menu_seek, Menu.NONE, R.string.ics_menu_seek);
        menu.add(Menu.NONE, R.string.ics_menu_players, Menu.NONE, R.string.ics_menu_players);
        menu.add(Menu.NONE, R.string.ics_menu_top_blitz, Menu.NONE, R.string.ics_menu_top_blitz);
        menu.add(Menu.NONE, R.string.ics_menu_top_standard, Menu.NONE, R.string.ics_menu_top_standard);
        menu.add(Menu.NONE, R.string.ics_menu_stop_puzzle, Menu.NONE, R.string.ics_menu_stop_puzzle);

        menu.add(Menu.NONE, R.string.ics_menu_console, Menu.NONE, R.string.ics_menu_console);

        menu.add("tell puzzlebot hint");
        menu.add("unexamine");
        menu.add("tell endgamebot hint");
        menu.add("tell endgamebot move");
        menu.add("tell endgamebot stop");

        menu.add(Menu.NONE, R.string.ics_menu_unobserve, Menu.NONE, R.string.ics_menu_unobserve);
        menu.add(Menu.NONE, R.string.menu_help, Menu.NONE, R.string.menu_help);

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

        boolean isConnected = isConnected();
        boolean isUserPlaying = get_view().isUserPlaying();
        boolean isNotGuest = isConnected && icsServer.isGuest();
        int viewMode = this.get_view()._viewMode;

        menu.findItem(R.string.menu_flip).setVisible(isConnected && viewMode != ICSChessView.VIEW_NONE);
        menu.findItem(R.string.ics_menu_takeback).setVisible(isConnected && isUserPlaying);

        menu.findItem(R.string.ics_menu_adjourn).setVisible(isConnected && isUserPlaying && isNotGuest);
        menu.findItem(R.string.ics_menu_draw).setVisible(isConnected && isUserPlaying);
        menu.findItem(R.string.ics_menu_resign).setVisible(isConnected && isUserPlaying);
        menu.findItem(R.string.ics_menu_abort).setVisible(isConnected && isUserPlaying);
        menu.findItem(R.string.ics_menu_flag).setVisible(isConnected && isUserPlaying);
        menu.findItem(R.string.ics_menu_refresh).setVisible(isConnected && isUserPlaying);

        menu.findItem(R.string.ics_menu_challenges).setVisible(isConnected && viewMode == ICSChessView.VIEW_NONE);
        menu.findItem(R.string.ics_menu_games).setVisible(isConnected && viewMode == ICSChessView.VIEW_NONE);
        menu.findItem(R.string.ics_menu_stored).setVisible(isConnected && viewMode == ICSChessView.VIEW_NONE && isNotGuest);
        menu.findItem(R.string.ics_menu_seek).setVisible(isConnected && viewMode == ICSChessView.VIEW_NONE);
        menu.findItem(R.string.ics_menu_players).setVisible(isConnected && viewMode == ICSChessView.VIEW_NONE);

        menu.findItem(R.string.ics_menu_stop_puzzle).setVisible(isConnected && viewMode == ICSChessView.VIEW_PUZZLE);

        menu.findItem(R.string.ics_menu_unobserve).setVisible(isConnected && viewMode == ICSChessView.VIEW_WATCH);

        menu.findItem(R.string.ics_menu_console).setVisible(isConnected);

        for (int i = 0; i < menu.size(); i++) {
            MenuItem item = menu.getItem(i);
            String title = item.getTitle().toString();
            if (title.equals("tell puzzlebot hint")) {
                item.setVisible(isConnected && viewMode == ICSChessView.VIEW_PUZZLE);
            } else if (title.equals("unexamine")) {
                item.setVisible(isConnected && viewMode == ICSChessView.VIEW_EXAMINE);
            } else if (title.equals("tell endgamebot hint")) {
                item.setVisible(isConnected && viewMode == ICSChessView.VIEW_ENDGAME);
            } else if (title.equals("tell endgamebot move")) {
                item.setVisible(isConnected && viewMode == ICSChessView.VIEW_ENDGAME);
            } else if (title.equals("tell endgamebot stop")) {
                item.setVisible(isConnected && viewMode == ICSChessView.VIEW_ENDGAME);
            }
        }

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
                // switchToLoadingView();
                switchToGamesView();
                sendString("games");
                return true;
            case R.string.ics_menu_stored:
                switchToStoredView();
                sendString("stored");
                return true;
            case R.string.ics_menu_challenges:
                switchToChallengeView();
                return true;
            case R.string.ics_menu_seek:
                _dlgMatch._rbSeek.setChecked(true);
                _dlgMatch._rbSeek.performClick();
                return true;
            case R.string.menu_help:
                i = new Intent();
                i.setClass(ICSClient.this, HtmlActivity.class);
                i.putExtra(HtmlActivity.HELP_MODE, "help_online");
                startActivity(i);
                return true;
            case R.string.ics_menu_console:
                switchToConsoleView();
                return true;
            case R.string.ics_menu_players:
                sendString("who a");
                switchToLoadingView();
                return true;
            case R.string.ics_menu_top_blitz:
                sendString("obs /b");
                return true;
            case R.string.ics_menu_top_standard:
                sendString("obs /s");
                return true;
            case R.string.ics_menu_quit:
                finish();
                return true;
            case R.string.ics_menu_stop_puzzle:
                sendString("tell puzzlebot stop");
                get_view().stopGame();
                return true;
            case R.string.ics_menu_unobserve:
                sendString("unobserve");
                get_view().stopGame();
                return true;
            case android.R.id.home:
                confirmAbort();
                return true;
        }

        // check menu for ending tag of <NR>, then delete tag and allow a command with no return
        String itemTitle = item.getTitle().toString();
        if (itemTitle.length() > 4 && itemTitle.substring(itemTitle.length() - 4).equals("<NR>")) {
            if (_viewAnimatorLobby.getDisplayedChild() == VIEW_SUB_CONSOLE) {
                _editConsole.setText(itemTitle.substring(0, itemTitle.length() - 4));
                _editConsole.requestFocus();
                _editConsole.setSelection(_editConsole.getText().length());
            } else {
                _editBoard.setText(itemTitle.substring(0, itemTitle.length() - 4));
                _editBoard.requestFocus();
                _editBoard.setSelection(_editBoard.getText().length());
            }
        } else {
            sendString(item.getTitle().toString());
        }
        return true;
    }


    public void confirmAbort() {
        Log.i("ICSClient", "confirmAbort");
        if (isConnected()) {
            if (_viewAnimatorMain.getDisplayedChild() == VIEW_MAIN_BOARD && get_view().isUserPlaying()) {
                new AlertDialog.Builder(ICSClient.this)
                        .setTitle(ICSClient.this.getString(R.string.ics_menu_abort) + "?")
                        .setPositiveButton(getString(R.string.alert_yes),
                                new DialogInterface.OnClickListener() {
                                    public void onClick(DialogInterface dialog, int whichButton) {
                                        dialog.dismiss();
                                        sendString("abort");
                                        sendString("quit");
                                        finish();
                                    }
                                })
                        .setNegativeButton(getString(R.string.alert_no), new DialogInterface.OnClickListener() {
                            public void onClick(DialogInterface dialog, int whichButton) {
                                dialog.dismiss();
                            }
                        })
                        .show();
                return;
            }
        }
        finish();
    }

    public boolean onKeyDown(int keyCode, KeyEvent event) {
        if (keyCode == KeyEvent.KEYCODE_BACK && event.getRepeatCount() == 0) {
            confirmAbort();
            return true;
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

    public void startSession(final String h, final String p) {
        if (h == "") {
            globalToast(getString(R.string.msg_ics_enter_handle));
            return;
        }
        if (h != "guest" && p == "") {
            globalToast(getString(R.string.msg_ics_enter_password));
            return;
        }

        if (bindService(new Intent(ICSClient.this, ICSServer.class), mConnection, Context.BIND_AUTO_CREATE)) {
            Log.i(TAG, "Bind to ICSServer");

            _handle = h;
            _pwd = p;
            switchToLoadingView();

        } else {
            globalToast("Could not init remote chess process");
            Log.e(TAG, "Error: The requested service doesn't exist, or this client isn't allowed access to it.");
        }
    }

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

        saveGameSDCard();

        _dlgOver.updateGameResultText(_matgame.group(11)); // game result message sent to dialog

        _dlgOver.setWasPlaying(get_view().getOpponent().length() > 0);
        _dlgOver.show();
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
            gameToast(String.format(getString(R.string.ics_game_over_format), getString(R.string.state_draw)), true);
            get_view().setViewMode(ICSChessView.VIEW_NONE);
            return;
        }

        gameToast(String.format(getString(R.string.ics_game_over_format), text), true);
        soundHorseSnort();

        get_view().setViewMode(ICSChessView.VIEW_NONE);
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

    public void saveGameSDCard() {
        try {
            if (Environment.getExternalStorageState().equals(Environment.MEDIA_MOUNTED)) {

                _sFile = Environment.getExternalStorageDirectory() + "/chessgamesonline.pgn";

                FileOutputStream fos;

                fos = new FileOutputStream(_sFile, true);
                fos.write(PGN.toString().getBytes());
                fos.flush();
                fos.close();

                doToast(getString(R.string.ics_save_game));

            } else {
                doToast(getString(R.string.err_sd_not_mounted));
            }
        } catch (Exception e) {

            doToast(getString(R.string.err_saving_game));
            Log.e("ex", e.toString());
        }

    }

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

    public void addConsoleText(final String s) {

        _tvConsole.setTypeface(Typeface.MONOSPACE);  // Monospace gives each character the same width
        _tvPlayConsole.setTypeface(Typeface.MONOSPACE);
        _dlgPlayer._tvPlayerListConsole.setTypeface(Typeface.MONOSPACE);

        _tvConsole.setTextSize(_iConsoleCharacterSize); // sets console text size
        _tvPlayConsole.setTextSize(_iConsoleCharacterSize);
        _dlgPlayer._tvPlayerListConsole.setTextSize(_iConsoleCharacterSize);

        if (_dlgPlayer.isShowing()) {
            _dlgPlayer._tvPlayerListConsole.append(s);
            _dlgPlayer._scrollPlayerListConsole.post(new Runnable() {
                public void run() {
                    _dlgPlayer._scrollPlayerListConsole.fullScroll(HorizontalScrollView.FOCUS_DOWN);
                }
            });
            return;
        }


        final String s2 = _tvConsole.getText() + "\n\n" + s;
        if (s2.length() > 8192) {
            _tvConsole.setText(s2.substring(s2.length() - 4096));
        } else {
            _tvConsole.append("\n\n" + s);
        }

        _scrollConsole.post(new Runnable() {//
            public void run() {
                _scrollConsole.fullScroll(HorizontalScrollView.FOCUS_DOWN);
            }
        });


        final String s3 = _tvPlayConsole.getText() + "\n\n" + s;
        if (s3.length() > 1024) {
            _tvPlayConsole.setText(s3.substring(s3.length() - 512));
        } else {
            _tvPlayConsole.append("\n\n" + s);
        }
        _scrollPlayConsole.post(new Runnable() {
            public void run() {
                _scrollPlayConsole.fullScroll(HorizontalScrollView.FOCUS_DOWN);
            }
        });
    }

    private void confirmShow(String title, String text, String sendstring) {
        _dlgConfirm.setSendString(sendstring);
        _dlgConfirm.setText(title, text);
        _dlgConfirm.show();
    }

    public void gameToast(final String text, final boolean stop) {
        if (stop) {
            get_view().stopGame();
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
    protected void onResume() {
        Log.i(TAG, "onResume");

        if (icsServer != null) {
            icsServer.addListener(this);
        }

        invalidateOptionsMenu(); // update OptionsMenu

        SharedPreferences prefs = this.getPrefs();

        _viewbase._showCoords = prefs.getBoolean("showCoords", false);

        ChessImageView._colorScheme = prefs.getInt("ColorScheme", 0);

        _view.setConfirmMove(prefs.getBoolean("ICSConfirmMove", false));

        _ficsHandle = prefs.getString("ics_handle", null);
        _ficsPwd = prefs.getString("ics_password", null);

        _iConsoleCharacterSize = Integer.parseInt(prefs.getString("ICSConsoleCharacterSize", "10"));

        _bAutoSought = prefs.getBoolean("ICSAutoSought", true);

        _bTimeWarning = prefs.getBoolean("ICSTimeWarning", true);
        _TimeWarning = Integer.parseInt(prefs.getString("ICSTimeWarningsecs", "10"));

        _bEndGameDialog = prefs.getBoolean("ICSEndGameDialog", true);

        _bShowClockPGN = prefs.getBoolean("ICSClockPGN", true);

        _bICSVolume = prefs.getBoolean("ICSVolume", true);
        if (_bICSVolume) {
            butQuickSoundOff.setVisibility(View.GONE);
            butQuickSoundOn.setVisibility(View.VISIBLE);
            set_fVolume(1.0f);
        } else {
            butQuickSoundOn.setVisibility(View.GONE);
            butQuickSoundOff.setVisibility(View.VISIBLE);
            set_fVolume(0.0f);
        }

        // get rid of notification for tap to play
        NotificationManager notificationManager = (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);
        notificationManager.cancel(0); // 0 is notification id
        _ICSNotifyLifeCycle = false;

        _gameStartSound = Integer.parseInt(prefs.getString("ICSGameStartSound", "1"));

        _notifyON = prefs.getBoolean("ICSGameStartBringToFront", true);

        /////////////////////////////////////////////
        _adapterHandles = new ArrayAdapter<String>(this, android.R.layout.simple_list_item_1);
        _arrayPasswords = new ArrayList<String>();

        if (_ficsHandle != null) {
            _adapterHandles.add(_ficsHandle);
            _arrayPasswords.add(_ficsPwd);
        }

        try {
            JSONArray jArray = new JSONArray(prefs.getString("ics_handle_array", "guest"));
            JSONArray jArrayPasswords = new JSONArray(prefs.getString("ics_password_array", ""));
            for (int i = 0; i < jArray.length(); i++) {
                _adapterHandles.add(jArray.getString(i));
                _arrayPasswords.add(jArrayPasswords.getString(i));
            }
        } catch (JSONException e) {
            // TODO Auto-generated catch block
            e.printStackTrace();
        }

        int t = 0;
        boolean g = false;
        for (int i = 0; i < _adapterHandles.getCount(); i++) {
            String x = _adapterHandles.getItem(i).toString();
            if (x.equals(_ficsHandle)) {
                t++;
            }
            if (x.equals("guest")) {
                g = true;  // flag to check if guest is in the spinner
            }
        }
        if (t > 1) {  // work around for remove - delete every occurrence and add latest - this will add latest password
            while (t > 0) {
                _adapterHandles.remove(_ficsHandle);
                _arrayPasswords.remove(_adapterHandles.getPosition(_ficsHandle) + 1);
                t--;
            }
            _adapterHandles.add(_ficsHandle);
            _arrayPasswords.add(_ficsPwd);
        }

        if (g == false) {
            _adapterHandles.add("guest"); // if no guest then add guest
            _arrayPasswords.add(" ");
        }

        _spinnerHandles.setAdapter(_adapterHandles);

        _spinnerHandles.setSelection(_adapterHandles.getPosition(_ficsHandle));


        /////////////////////////////////////////////////////////////////

        if (_ficsHandle == null) {
            _ficsHandle = "guest";
            _ficsPwd = "";
        }


        String sTmp = prefs.getString("NotificationUri", null);
        if (sTmp == null) {
            _ringNotification = null;
        } else {
            Uri tmpUri = Uri.parse(sTmp);
            _ringNotification = RingtoneManager.getRingtone(this, tmpUri);
        }

        //if(true)return;

        if (isConnected()) {
            switchToBoardView();
        } else {

            _editHandle.setText(_ficsHandle);
            _editPwd.setText(_ficsPwd);
            if (_ficsPwd.length() < 2) {
                _editPwd.setText("");
            }

            /////////////////////////////////////////////////////
            // DEBUG
            //switchToBoardView();
            /////////////////////////////////////////////////////

            // real
            switchToLoginView();
        }
        super.onResume();
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

        JSONArray jArray = new JSONArray();
        JSONArray jArrayPasswords = new JSONArray();
        for (int i = 0; i < _adapterHandles.getCount(); i++) {
            jArray.put(_adapterHandles.getItem(i));
            jArrayPasswords.put(_arrayPasswords.get(i));
        }
        editor.putString("ics_handle_array", jArray.toString());
        editor.putString("ics_password_array", jArrayPasswords.toString());

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
    }

    @Override
    protected void onStop() {
        Log.i(TAG, "onStop");
        super.onStop();
    }

    @Override
    protected void onDestroy() {
        Log.i(TAG, "onDestroy");

        icsServer.removeListener(this);
        unbindService(mConnection);

        super.onDestroy();
    }

    public void sendString(String s) {

        if (_bConsoleText) {        // allows user ICS console text only
            addConsoleText(s);
            _bConsoleText = false;
        }

        if (!icsServer.sendString(s)) {
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
                get_view().setViewMode(ICSChessView.VIEW_WATCH);
                get_view().setGameNum(Integer.parseInt((String) m.get("nr")));
                switchToBoardView();
            }
        } else if (arg0 == _listStored) {
            if (_mapStored.size() > arg2) {
                HashMap<String, String> m = _mapStored.get(arg2);
                Log.i("onItemClick", "item " + m.get("text_name_stored"));
                sendString("match " + m.get("text_name_stored"));
                switchToBoardView();
            }
        }
    }

    public void switchToBoardView() {
        if (_viewAnimatorMain.getDisplayedChild() != VIEW_MAIN_BOARD) {
            _viewAnimatorMain.setDisplayedChild(VIEW_MAIN_BOARD);
        }
        _viewAnimatorLobby.setDisplayedChild(VIEW_SUB_WELCOME);  // used for lobby default
    }

    /*

    public void switchToLobbyView(){
        if(_viewAnimatorMain.getDisplayedChild() != 0)
            _viewAnimatorMain.setDisplayedChild(0);
    }
    public void switchToWelcomeView(){

        _tvHeader.setText(String.format(getString(R.string.ics_welcome_format), _handle));
        if(_viewAnimatorMain.getDisplayedChild() != 0)
            _viewAnimatorMain.setDisplayedChild(0);
        _viewAnimatorLobby.setDisplayedChild(2);
    }
    */
    public void switchToChallengeView() {
        _tvHeader.setText(R.string.ics_menu_challenges);
        if (_viewAnimatorMain.getDisplayedChild() != VIEW_MAIN_LOBBY)
            _viewAnimatorMain.setDisplayedChild(VIEW_MAIN_LOBBY);
        _viewAnimatorLobby.setDisplayedChild(VIEW_SUB_CHALLENGES);
    }

    public void switchToPlayersView() {
        _tvHeader.setText(Integer.toString(_adapterPlayers.getCount()) + " " + getString(R.string.ics_available_players));
        if (_viewAnimatorMain.getDisplayedChild() != VIEW_MAIN_LOBBY)
            _viewAnimatorMain.setDisplayedChild(VIEW_MAIN_LOBBY);
        _viewAnimatorLobby.setDisplayedChild(VIEW_SUB_PLAYERS);
    }

    public void switchToGamesView() {
        _mapGames.clear();
        _adapterGames.notifyDataSetChanged();

        _tvHeader.setText(R.string.ics_menu_games);
        if (_viewAnimatorMain.getDisplayedChild() != VIEW_MAIN_LOBBY)
            _viewAnimatorMain.setDisplayedChild(VIEW_MAIN_LOBBY);
        _viewAnimatorLobby.setDisplayedChild(VIEW_SUB_GAMES);
    }

    public void switchToStoredView() {
        _mapStored.clear();
        _adapterStored.notifyDataSetChanged();

        _tvHeader.setText(R.string.ics_menu_stored);
        if (_viewAnimatorMain.getDisplayedChild() != VIEW_MAIN_LOBBY)
            _viewAnimatorMain.setDisplayedChild(VIEW_MAIN_LOBBY);
        _viewAnimatorLobby.setDisplayedChild(VIEW_SUB_STORED);
    }

    public void switchToLoadingView() {
        _tvHeader.setText(R.string.title_loading);
        if (_viewAnimatorMain.getDisplayedChild() != VIEW_MAIN_LOBBY) {
            _viewAnimatorMain.setDisplayedChild(VIEW_MAIN_LOBBY);
        }
        _viewAnimatorLobby.setDisplayedChild(VIEW_SUB_PROGRESS);
    }

    public void switchToLoginView() {
        _butLogin.setEnabled(true);
        _tvHeader.setText("Login");
        if (_viewAnimatorMain.getDisplayedChild() != VIEW_MAIN_LOBBY) {
            _viewAnimatorMain.setDisplayedChild(VIEW_MAIN_LOBBY);
        }
        _viewAnimatorLobby.setDisplayedChild(VIEW_SUB_LOGIN);
    }

    public void switchToConsoleView() {
        _tvHeader.setText("Console");
        if (_viewAnimatorMain.getDisplayedChild() != VIEW_MAIN_LOBBY) {
            _viewAnimatorMain.setDisplayedChild(VIEW_MAIN_LOBBY);
        }
        _viewAnimatorLobby.setDisplayedChild(VIEW_SUB_CONSOLE);

        _scrollConsole.post(new Runnable() {
            public void run() {
                _scrollConsole.fullScroll(HorizontalScrollView.FOCUS_DOWN);
            }
        });
    }

    public ICSChessView get_view() {
        return _view;
    }

    public void soundNotification() {
        if (_ringNotification != null) {
            _ringNotification.play();
        }
    }

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

        switchToConsoleView();
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
    public void OnSessionStarted() {
        switchToBoardView();
    }

    @Override
    public void OnSessionEnded() { switchToLoginView(); }

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
        _mapPlayers.clear();
        for (int i = 0; i < playerList.size(); i++) {
            _mapPlayers.add(playerList.get(i));
        }
        Collections.sort(_mapPlayers, new ComparatorHashRating());
        _adapterPlayers.notifyDataSetChanged();

        switchToPlayersView();
    }

    @Override
    public void OnBoardUpdated(String gameLine, String handle) {
        if (get_view().parseGame(gameLine, handle)) {
            switchToBoardView();
        } else {
            Log.i(TAG, "Could not parse game line " + gameLine);
        }
    }

    @Override
    public void OnChallenged(String opponent, String rating, String message) {

    }

    @Override
    public void OnIllegalMove() {

    }

    @Override
    public void OnSeekNotAvailable() {

    }

    @Override
    public void OnPlayGameStarted(String whiteHandle, String blackHandle, String whiteRating, String blackRating) {

    }

    @Override
    public void OnGameNumberUpdated(int number) {
        if (number > 0) {
            get_view().setGameNum(number);
            get_view().setViewMode(ICSChessView.VIEW_PLAY);
        }
    }

    @Override
    public void OnOpponentRequestsAbort() {

    }

    @Override
    public void OnOpponentRequestsAdjourn() {

    }

    @Override
    public void OnOpponentOffersDraw() {

    }

    @Override
    public void OnOpponentRequestsTakeBack() {

    }

    @Override
    public void OnAbortConfirmed() {

    }

    @Override
    public void OnPlayGameResult(String message) {

    }

    @Override
    public void OnPlayGameStopped() {

    }

    @Override
    public void OnYourRequestSended() {

    }

    @Override
    public void OnChatReceived() {

    }

    @Override
    public void OnResumingAdjournedGame() {

    }

    @Override
    public void OnAbortedOrAdjourned() {

    }

    @Override
    public void OnObservingGameStarted() {

    }

    @Override
    public void OnObservingGameStopped() {

    }

    @Override
    public void OnPuzzleStarted() {

    }

    @Override
    public void OnPuzzleStopped() {

    }

    @Override
    public void OnExaminingGameStarted() {

    }

    @Override
    public void OnExaminingGameStopped() {

    }

    @Override
    public void OnSoughtResult(ArrayList<HashMap<String, String>> soughtList) {

    }

    @Override
    public void OnChallengedResult(ArrayList<HashMap<String, String>> challenges) {

    }

    @Override
    public void OnGameListResult(ArrayList<HashMap<String, String>> games) {

    }

    @Override
    public void OnStoredListResult(ArrayList<HashMap<String, String>> games) {

    }

    @Override
    public void OnEndGameResult() {

    }

    @Override
    public void OnConsoleOutput(String buffer) {
        addConsoleText(buffer);
    }

    static class InnerTimerHandler extends Handler {
        WeakReference<ICSClient> _client;

        InnerTimerHandler(ICSClient client) {
            this._client = new WeakReference<ICSClient>(client);
        }

        @Override
        public void handleMessage(Message msg) {
            ICSClient client = _client.get();
            if (client != null) {
                if (client._bAutoSought) {
//                    if (client._socket != null && client._workerTelnet != null && client._workerTelnet.isAlive() && client._socket.isConnected() &&
//                            client._bInICS && client.get_view().isUserPlaying() == false) {
//                        while (client._mapChallenges.size() > 0) {
//                            client._mapChallenges.remove(0);
//                        }
//                        client._adapterChallenges.notifyDataSetChanged();
//                        client.sendString("sought");
//                    }
                }
            }
        }
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

