package jwtc.android.chess.ics;

import android.app.AlertDialog;
import android.content.pm.ActivityInfo;
import android.content.res.Configuration;
import android.media.Ringtone;
import android.media.RingtoneManager;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.Message;
import android.os.PowerManager;
import android.preference.PreferenceActivity;
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

import java.lang.ref.WeakReference;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Timer;
import java.util.TimerTask;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.json.JSONArray;
import org.json.JSONException;

import jwtc.android.chess.*;


public class ICSClient extends MyBaseActivity implements OnItemClickListener {

    private TelnetSocket _socket;
    private Thread _workerTelnet;
    private String _server, _handle, _pwd, _prompt, _waitFor, _buffer, _ficsHandle, _ficsPwd;
    private int _port, _serverType;
    private boolean _bIsGuest, _bInICS, _bAutoSought;
    private Button _butLogin;
    private TextView _tvHeader, _tvConsole, _tvPlayConsole;
//	public ICSChatDlg _dlgChat;

    private EditText _editHandle, _editPwd, _editConsole;
    //private EditText _editPrompt;
    private ListView _listChallenges, _listPlayers, _listGames, _listStored;
    private ICSChessView _view;
    private ICSMatchDlg _dlgMatch;
    private ICSConfirmDlg _dlgConfirm;
    private ICSChatDlg _dlgChat;
    private ViewAnimator _viewAnimatorMain, _viewAnimatorLobby;
    private ScrollView _scrollConsole;

    private Ringtone _ringNotification;

    // FICS
    // Challenge: withca (----) GuestFHYH (----) unrated blitz 10 0
    private Pattern _pattChallenge = Pattern.compile("Challenge\\: (\\w+) \\((.+)\\) (\\w+) \\((.+)\\) (rated |unrated )(standard |blitz |wild )(\\d+) (\\d+)( \\(adjourned\\))?.*");

    // @TODO ===========================================================================================
    //    C Opponent       On Type          Str  M    ECO Date
    // 1: W jwtc            N [ sr 20   0] 39-39 W3   C44 Thu Nov  5, 12:41 PST 2009
    // 1: B jwtc            Y [ sr  7  12] 39-39 B3   B07 Sun Jun  2, 02:59 PDT 2013
    private Pattern _pattStoredRow = Pattern.compile("[\\s]*(\\d+)\\: (W|B) (\\w+)[\\s]*(Y|N).+");
    // =================================================================================================

    // relay
    // :262 GMTopalov         GMCaruana         *       C78

    // GuestNJVN (++++) seeking 5 0 unrated blitz ("play 104" to respond)
    // GuestFXXP (++++) seeking 7 0 unrated blitz f ("play 27" to respond)
    // Suffocate (++++) seeking 30 30 unrated standard [black] m ("play 29" to respond)
    //Pattern _pattSeeking = Pattern.compile("(\\w+) \\((.+)\\) seeking (\\d+) (\\d+) (rated |unrated ?)(standard |blitz |lightning )(\\[white\\] |\\[black\\] )?(f |m )?\\(\"play (\\d+)\" to respond\\)");

    private Pattern _pattSought;
    private Pattern _pattGameRow;
    private Pattern _pattChat = Pattern.compile("(\\w+)(\\(\\w+\\))? tells you\\: (.+)");

    //1269.allko                    ++++.kaspalesweb(U)
    private Pattern _pattPlayerRow = Pattern.compile("(\\s+)?(.{4})([\\.\\:\\^\\ ])(\\w+)(\\(\\w+\\))?");

    private ArrayList<HashMap<String, String>> _mapChallenges = new ArrayList<HashMap<String, String>>();
    private ArrayList<HashMap<String, String>> _mapPlayers = new ArrayList<HashMap<String, String>>();
    private ArrayList<HashMap<String, String>> _mapGames = new ArrayList<HashMap<String, String>>();
    private ArrayList<HashMap<String, String>> _mapStored = new ArrayList<HashMap<String, String>>();

    private AlternatingRowColorAdapter _adapterGames, _adapterPlayers, _adapterChallenges, _adapterStored;

    public static final String TAG = "ICSClient";

    protected static final int MSG_PARSE = 1;
    protected static final int MSG_STOP_SESSION = 2;
    protected static final int MSG_START_SESSION = 3;
    protected static final int MSG_ERROR = 4;

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

    static class InnerThreadHandler extends Handler {
        WeakReference<ICSClient> _client;

        InnerThreadHandler(ICSClient client) {
            this._client = new WeakReference<ICSClient>(client);
        }

        @Override
        public void handleMessage(Message msg) {
            ICSClient client = _client.get();
            if (client != null) {
                switch (msg.what) {
                    case MSG_PARSE:
                        //parseBuffer(msg.getData().getString("buffer"));
                        client.parseBuffer();
                        break;
                    case MSG_STOP_SESSION:
                        client.stopSession(msg.getData().getString("buffer"));
                        break;
                    case MSG_START_SESSION:
                        client.switchToBoardView();
                        break;
                }

                super.handleMessage(msg);
            }
        }
    }

    // passes the incoming data from the socket worker thread for parsing
    protected InnerThreadHandler m_threadHandler = new InnerThreadHandler(this);

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
                    if (client._socket != null && client._workerTelnet != null && client._workerTelnet.isAlive() && client._socket.isConnected() &&
                            client._bInICS && client.get_view().isUserPlaying() == false) {
                        while (client._mapChallenges.size() > 0) {
                            client._mapChallenges.remove(0);
                        }
                        client._adapterChallenges.notifyDataSetChanged();
                        client.sendString("sought");
                    }
                }
            }
        }
    }

    private Timer _timer = null;
    protected InnerTimerHandler m_timerHandler = new InnerTimerHandler(this);

    /**
     * Called when the activity is first created.
     */
    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        int configOrientation = this.getResources().getConfiguration().orientation;
        if(configOrientation == Configuration.ORIENTATION_LANDSCAPE) {
            this.setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE);
        } else {
            this.setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_PORTRAIT);
        }

        setContentView(R.layout.icsclient);

        this.makeActionOverflowMenuShown();

        // needs to be called first because of chess statics init
        _view = new ICSChessView(this);
        _view.init();

        _dlgMatch = new ICSMatchDlg(this);
        _dlgConfirm = new ICSConfirmDlg(this);
        _dlgChat = new ICSChatDlg(this);

        _handle = null;
        _pwd = null;
        _workerTelnet = null;
        _socket = null;

        _tvHeader = (TextView) findViewById(R.id.TextViewHeader);

        //_dlgChat = new ICSChatDlg(this);

        _bIsGuest = true;
        _serverType = SERVER_FICS;
        _bInICS = false;
        _bAutoSought = true;

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
                new String[]{"text_name1", "text_name2", "text_rating1", "text_rating2"}, new int[]{R.id.text_name1, R.id.text_name2, R.id.text_rating1, R.id.text_rating2});

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

        /*
        ImageButton butClose = (ImageButton)findViewById(R.id.ButtonBoardClose);
        butClose.setOnClickListener(new OnClickListener() {
        	public void onClick(View arg0) {
        		// TODO
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
                    _dlgChat.prepare();
                }
            });
        }

        _editHandle = (EditText) findViewById(R.id.EditICSHandle);
        _editPwd = (EditText) findViewById(R.id.EditICSPwd);

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
                    String s = et.getText().toString();
                    sendString(s + "\n");
                    et.setText("");
                    return true;
                }
                return false;
            }
        };

        _editConsole = (EditText) findViewById(R.id.EditICSConsole);
        if (_editConsole != null) {
            _editConsole.setOnKeyListener(okl);
        }
        EditText editBoard = (EditText) findViewById(R.id.EditICSBoard);
        if (editBoard != null) {
            editBoard.setOnKeyListener(okl);
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
                    } catch(Exception ex){

                        doToast("Could not go to registration page");
                    }
                }
            });
        }
        _ringNotification = null;

        switchToLoginView();

        Log.i("ICSClient", "onCreate");
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {

        menu.add(Menu.NONE, R.string.menu_prefs, Menu.NONE, R.string.menu_prefs);
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
        menu.add(Menu.NONE, R.string.ics_menu_stop_puzzle, Menu.NONE, R.string.ics_menu_stop_puzzle);

        menu.add(Menu.NONE, R.string.ics_menu_console, Menu.NONE, R.string.ics_menu_console);

        menu.add("tell puzzlebot hint");
        menu.add("forward");
        menu.add("backward");
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
        boolean isNotGuest = isConnected && (false == _handle.equals("guest"));
        int viewMode = this.get_view()._viewMode;

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
            } else if (title.equals("forward")) {
                item.setVisible(isConnected && viewMode == ICSChessView.VIEW_EXAMINE);
            } else if (title.equals("backward")) {
                item.setVisible(isConnected && viewMode == ICSChessView.VIEW_EXAMINE);
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
                get_view().stopGame();
                return true;
            case R.string.ics_menu_adjourn:
                sendString("adjourn");
                return true;
            case R.string.ics_menu_draw:
                sendString("draw");
                return true;
            case R.string.ics_menu_flag:
                sendString("flag");
            case R.string.ics_menu_resign:
                sendString("resign");
                return true;
            case R.string.menu_flip:
                _view.forceFlipBoard();
                sendString("refresh");
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
                _dlgMatch.setPlayer("*");
                _dlgMatch.show();
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

        sendString(item.getTitle().toString());
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

    public void stopSession(String sReason) {
        Log.e("stopSession", sReason);
        if (isFinishing()) {
            return;
        }
        new AlertDialog.Builder(ICSClient.this)
                .setTitle(R.string.title_error)
                .setMessage(sReason)
                .setPositiveButton(R.string.alert_ok,
                        new DialogInterface.OnClickListener() {
                            public void onClick(DialogInterface dialog, int whichButton) {
                                dialog.dismiss();
                                finish();
                            }
                        })
                .show();
    }

    public void startSession(final String h, final String p) {

        if (h == "") {
            globalToast(getString(R.string.msg_ics_enter_handle));
            return;
        }

        Log.i("ICSClient", "Setting servertype to FICS");
        _serverType = SERVER_FICS;
        _server = "freechess.org";
        _port = 23;

        _prompt = "fics% ";
        _ficsHandle = h;
        _ficsPwd = p;
        _handle = _ficsHandle;
        _pwd = _ficsPwd;
        if (_handle != "guest" && _pwd == "") {
            globalToast(getString(R.string.msg_ics_enter_password));
            return;
        }

        // FICS
        //209 1739 rahulso            15  10 unrated standard   [white]     0-9999 m
        //101 ++++ GuestYYLN          16   0 unrated standard               0-9999 mf
        //   6 ++++ sdhisfh             2   0 unrated crazyhouse             0-9999
        //  11 ++++ GuestFGMX          20  10 unrated standard               0-9999 f
        //   7 ++++ Amhztb             10  90 unrated standard               0-9999 m
        //  26 ++++ GuestFFHQ           7   0 unrated wild/3     [white]     0-9999
        _pattSought = Pattern.compile("[\\s]*(\\d+)[\\s]+(\\d+|\\++|-+)[\\s]+([\\w\\(\\)]+)[\\s]+(\\d+)[\\s]+(\\d+)[\\s]+(rated|unrated?)[\\s]+([\\w/\\d]+?)[\\s]*(\\[white\\]|\\[black\\])?[\\s]*(\\d+)\\-(\\d+)[\\s]*([fm]+)?");
        // 							  "        09          09 | +++            handle()              09          09                              wild/3
        // FICS
        //  93 2036 WFMKierzek  2229 FMKarl     [ su120   0]  26:13 -  3:49 (22-22) W: 28
        //  1  2    3           4    5            678     9
        _pattGameRow = Pattern.compile("[\\s]*(\\d+) (\\d+) (\\w+)[\\s]+(\\d+) (\\w+)[\\s]+\\[ (s|b|l)(r|u)[\\s]*(\\d+)[\\s]*(\\d+)\\][\\s]*(\\d+):(\\d+)[\\s]*-[\\s]*(\\d+):(\\d+).+");

        _butLogin.setEnabled(false);
        switchToLoadingView();

        _workerTelnet = new Thread(new Runnable() {

            public void run() {
                Message msg, msgStop;
                Bundle bun;

                msgStop = new Message();
                msgStop.what = MSG_STOP_SESSION;

                _bIsGuest = _handle.equals("guest");

                try {
                    _socket = new TelnetSocket(_server, _port);
                } catch (Exception ex) {
                    _socket = null;

                    bun = new Bundle();
                    bun.putString("buffer", getString(R.string.ics_error_connection));
                    msgStop.setData(bun);
                    m_threadHandler.sendMessage(msgStop);

                    return;
                }

                try {

                    // do login
                    String data = "";
                    String buf = _socket.readString();
                    while (buf != null && !isFinishing()) {
                        data += buf;
                        if (data.indexOf("login: ") > 0)
                            break;
                        //Log.i("startSession 0", "Fetching until 'login:'");
                        buf = _socket.readString();
                    }
                    if (data.length() == 0) {

                        bun = new Bundle();
                        bun.putString("buffer", "No response from server after connection...");
                        msgStop.setData(bun);
                        m_threadHandler.sendMessage(msgStop);
                        return;
                    }
                    //Log.i("startSession 1", "First response: " + data);
                    if (data.indexOf("login: ") == -1) {

                        bun = new Bundle();
                        bun.putString("buffer", "Unexpected response from server after connection...");
                        msgStop.setData(bun);
                        m_threadHandler.sendMessage(msgStop);

                        return;
                    }

                    Log.i("startSession 1", "Logging in with " + _handle);
                    sendString(_handle);

                    int iPos, iPos2;

                    data = "";
                    buf = _socket.readString();
                    while (buf != null) {
                        data += buf;
                        if (data.indexOf("\":") > 0 &&
                                (data.indexOf("Press return to enter the server as") > 0 ||
                                        data.indexOf("Logging you in as") > 0) ||
                                data.indexOf("password: ") > 0 || data.indexOf("login:") > 0
                                )
                            break;
                        Log.i("startSession debug", "wait: " + data);
                        buf = _socket.readString();
                    }
                    if (data.length() == 0) {

                        bun = new Bundle();
                        bun.putString("buffer", "No response from server after setting login handle...");
                        msgStop.setData(bun);
                        m_threadHandler.sendMessage(msgStop);

                        return;
                    }
                    // just remove all newlines from the string for better regex matching
                    data = data.replaceAll("[\r\n\0]", "");
                    Log.i("startSession 2", "After handle: " + data);
                    iPos = data.indexOf("Press return to enter the server as \"");
                    iPos2 = data.indexOf("Logging you in as \"");
                    //iPos3 = data.indexOf("If it is yours, type the password");

                    if (iPos >= 0) {
                        data = data.trim();
                        Log.i("startSession 2.1", "Guest log in v1");
                        // 							    Press return to enter the server as
                        Pattern patt = Pattern.compile("Press return to enter the server as \"(\\w+)\":");
                        Matcher match = patt.matcher(data);
                        if (match.find()) {
                            _handle = match.group(1);
                            sendString("");
                        } else {

                            bun = new Bundle();
                            bun.putString("buffer", "Could not process response after setting login handle...(1)");
                            msgStop.setData(bun);
                            m_threadHandler.sendMessage(msgStop);

                            return;
                        }
                    } else if (iPos2 >= 0) {
                        Log.i("startSession 2.1", "Guest log in v2");
                        Pattern patt = Pattern.compile("Logging you in as \"(\\w+)\"");
                        Matcher match = patt.matcher(data);
                        if (match.find()) {
                            _handle = match.group(1);
                        } else {

                            bun = new Bundle();
                            bun.putString("buffer", "Could not process response after setting login handle...(2)");
                            msgStop.setData(bun);
                            m_threadHandler.sendMessage(msgStop);

                            return;
                        }
                    } else if (data.indexOf("password: ") > 0) {
                        sendString(_pwd);
                    } else {

                        bun = new Bundle();
                        bun.putString("buffer", "username error:  " + data);
                        msgStop.setData(bun);
                        m_threadHandler.sendMessage(msgStop);

                        return;
                    }
                    data = "";

                    buf = _socket.readString();
                    while (buf != null) {
                        data += buf;
                        if (data.length() > 20)
                            break;
                        buf = _socket.readString();
                    }

                    Log.i("startSession 3", "Response after password: " + data);
                    if (data == null || data.length() == 0) {

                        bun = new Bundle();
                        bun.putString("buffer", "No response from server while logging in...");
                        msgStop.setData(bun);
                        m_threadHandler.sendMessage(msgStop);

                        return;
                    }
                    if (data.indexOf("**** Starting ") == -1 && data.indexOf("****************") == -1) {

                        bun = new Bundle();
                        bun.putString("buffer", "password error:  " + data);
                        msgStop.setData(bun);
                        m_threadHandler.sendMessage(msgStop);

                        return;
                    }
                    _bInICS = true;
                    sendString("style 12");
                    sendString("-channel 4"); // guest
                    sendString("-channel 53"); // guest chat
                    sendString("set kibitz 1"); // for puzzlebot

                    // sendMessage("set interface "+ getPreferences().getString(APP_NAME));
                    Log.i("ICSClient", " == HANDLE " + _handle);
                    // _handle

                    msg = new Message();
                    msg.what = MSG_START_SESSION;
                    m_threadHandler.sendMessage(msg);
                    //

                    String buffer = "";
                    _waitFor = _prompt;
                    buffer = "";

                    while (_socket != null && _socket.isConnected()) {
                        data = _socket.readString();
                        if (data != null && data.length() > 0) {
                            //Log.i("WorkerTelnet = ", data);
                            buffer += data;

                            if (buffer.endsWith(_waitFor)) {

                                _buffer = buffer;

                                msg = new Message();
                                msg.what = MSG_PARSE;
                                bun = new Bundle();
                                //bun.putString("buffer", buffer);
                                //msg.setData(bun);
                                m_threadHandler.sendMessage(msg);

                                buffer = "";
                            }
                        }
                    }
                } catch (Exception ex) {

                    bun = new Bundle();
                    bun.putString("buffer", getString(R.string.ics_error_connection));
                    msgStop.setData(bun);
                    m_threadHandler.sendMessage(msgStop);

                    Log.e("WorkerTelnet", ex.toString());
                }
                _bInICS = false;
                Log.i("WorkerTelnet", "stopped");
                finish();
            }

        });
        _workerTelnet.start();

    }

    private void parseBuffer() {
        try {

            //Log.i("parseBuffer", "[" + buffer + "]");
            String sRaw = "";
            Matcher match;

            //////////////////////////////////////////////////////////////////////////////////////////////
            String[] lines;
            if (_serverType == SERVER_FICS) {
                lines = _buffer.split("\n\r");
            } else {
                lines = _buffer.split("\r\n");
            }

            //Log.i("WorkerTelnet", "looking within STATUS_ONLINE");

            // 680 players displayed (of 680). (*) indicates system administrator.
            // result for who

            if (lines.length > 3 && _buffer.indexOf("players displayed (of ") > 0 && _buffer.indexOf("indicates system administrator.") > 0) {
                _mapPlayers.clear();
                for (int i = 0; i < lines.length - 2; i++) {
                    match = _pattPlayerRow.matcher(lines[i]);
                    while (match.find()) {
                        String name = match.group(4);
                        if (name != null && match.group(2) != null) {

                            String code = match.group(5);

                            if (code == null) {
                                HashMap<String, String> item = new HashMap<String, String>();
                                item.put("text_name", name);
                                item.put("text_rating", match.group(2));
                                _mapPlayers.add(item);
                            } else {

                                if (code == "(U)" || code == "(FM)" || code == "(GM)" || code == "(IM)" || code == "(WIM)" || code == "(WGM)") {
                                    name += code;
                                    HashMap<String, String> item = new HashMap<String, String>();
                                    item.put("text_name", name);
                                    item.put("text_rating", match.group(2));
                                    _mapPlayers.add(item);
                                }
                            }

                        }
                    }
                }
                switchToPlayersView();

                Collections.sort(_mapPlayers, new ComparatorHashRating());

                _adapterPlayers.notifyDataSetChanged();
            }

            //////////////////////////////////////////////////////////////////////////////////////////////
            // result for stored
 			/*
 			else if(lines.length > 3 && _buffer.indexOf("Stored games for " + _handle + ":") > 0){
 				//_pattStoredRow
 			}*/
            //////////////////////////////////////////////////////////////////////////////////////////////
            // single line stuff
            else {
                String line;
                for (int i = 0; i < lines.length; i++) {
                    line = lines[i].replace(_prompt, "");
                    // lines can still contain prompt!
                    //   _prompt
                    //////////////////////////////////////////////////////////////
                    // board representation
                    if (line.indexOf("<12> ") >= 0) {
                        // this can be multiple lines!
                        String[] gameLines = line.split("<12> ");

                        for (int j = 0; j < gameLines.length; j++) {
                            // at least 65 chars
                            if (gameLines[j].length() > 65) {
                                //if(get_view().preParseGame(gameLines[j])){
                                if (get_view().parseGame(gameLines[j], _handle)) {
                                    switchToBoardView();
                                } else {
                                    //gameToast("There was a problem parsing the response of the server.", false);
                                    Log.w("parseBuffer", "Could not parse game response");
                                    addConsoleText("Could not parse game response");
                                }
                                //}
                            }
                        }
                    }

                    ///////////////////////////////////////////////////////////////
                    // Challenge: jwtc (++++) jewithca (++++) unrated blitz 5 0.
                    else if (line.indexOf("Challenge:") >= 0) {
                        Log.i("parseBuffer", "parsing challenge " + line);
                        match = _pattChallenge.matcher(line);
                        if (match.matches()) {

                            String opponent, rating;
                            if (match.group(1).equals(_handle)) {
                                opponent = match.group(3);
                                rating = match.group(4);
                            } else {
                                opponent = match.group(1);
                                rating = match.group(2);
                            }

                            //Log.i("parseBuffer", "matched challenge");
                            // ("adjourned", match.group(9) != null);

                            new AlertDialog.Builder(ICSClient.this)
                                    .setTitle(ICSClient.this.getString(R.string.title_challenge))
                                    .setMessage(opponent +
                                            " [" + rating +
                                            "]\nchallenges you for a " + match.group(7) + " min.+" + match.group(8) + "s " + match.group(5) + " " + match.group(6) + ".\nDo you wish to accept?")
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
                    }
                    //////////////////////////////////////////////////////////////
                    // seek no longer available
                    else if (line.equals("That seek is not available.")) {
                        gameToast("That seek is not available", false);
                    }

                    /////////////////////////////////////////////////////////////////
                    // game created
                    else if (line.indexOf("{Game ") >= 0 && (line.indexOf(" Creating ") > 0 || line.indexOf(" Continuing ") > 0)) {
                        Pattern p = Pattern.compile("\\{Game (\\d+) .*");
                        Matcher m = p.matcher(line);
                        if (m.matches()) {
                            get_view().setGameNum(Integer.parseInt(m.group(1)));
                            get_view().setViewMode(ICSChessView.VIEW_PLAY);
                            switchToBoardView();
                        }
                    } else if (line.indexOf("Creating: ") >= 0 && line.indexOf("(adjourned)") >= 0) {
                        //Creating: jcarolus (----) jwtc (----) unrated blitz 5 0 (adjourned)
                        get_view().setViewMode(ICSChessView.VIEW_PLAY);
                        switchToBoardView();
                        gameToast("Resuming adjourned game", false);
                    }
                    //////////////////////////////////////////////////////////////
                    else if (line.indexOf("Illegal move (") == 0) {
                        gameToast("Illegal move", false);
                    }
                    //////////////////////////////////////////////////////////////
                    // abort
                    else if (get_view().isUserPlaying() && line.indexOf(get_view().getOpponent() + " would like to abort the game") != -1) { // todo add opponent in match
                        confirmShow(getString(R.string.title_offers_abort), getString(R.string.ics_offers_abort), "abort");
                    } else if (get_view().isUserPlaying() && line.indexOf("Game aborted by mutual agreement}") >= 0) {
                        gameToast("Game aborted by mutual agreement", true);
                        get_view().setViewMode(ICSChessView.VIEW_NONE);
                    }
                    //////////////////////////////////////////////////////////////
                    // take back
                    else if (get_view().isUserPlaying() && line.indexOf(get_view().getOpponent() + " would like to take back ") != -1) { //
                        confirmShow(getString(R.string.title_offers_takeback), getString(R.string.ics_offers_takeback), "accept");
                    }
                    //////////////////////////////////////////////////////////////
                    // game over
                    else if (line.indexOf("{Game " /*+ get_view().getGameNum()*/) >= 0 && (line.indexOf("} 1-0") > 0 || line.indexOf("} 0-1") > 0)) {
                        String text = "";
                        if (line.indexOf(" resigns} ") > 0) { // todo add opponent in match
                            text = String.format(getString(R.string.ics_game_over_format), getString(R.string.state_resigned));
                        } else if (line.indexOf("checkmated") > 0) {
                            text = String.format(getString(R.string.ics_game_over_format), getString(R.string.state_mate));
                        } else if (line.indexOf("forfeits on time") > 0) {
                            text = String.format(getString(R.string.ics_game_over_format), getString(R.string.state_time));
                        } else {
                            text = getString(R.string.ics_game_over);
                        }
                        gameToast(text, true);

                        get_view().setViewMode(ICSChessView.VIEW_NONE);
                    }
                    // draw
                    else if (line.indexOf("{Game " /*+ get_view().getGameNum()*/) >= 0 && line.indexOf("} 1/2-1/2") > 0) {

                        gameToast(String.format(getString(R.string.ics_game_over_format), getString(R.string.state_draw)), true);

                        get_view().setViewMode(ICSChessView.VIEW_NONE);

                    }
                    //////////////////////////////////////////////////////////////
                    // aborted/adjouned
                    else if (line.indexOf("{Game " /*+ get_view().getGameNum()*/) >= 0 && line.indexOf("} *") > 0) {
                        String text = getString(R.string.ics_game_over);
                        gameToast(text, true);
                        get_view().setViewMode(ICSChessView.VIEW_NONE);
                    }
                    //////////////////////////////////////////////////////////////
                    // draw / abort / todo:adjourn request sent
                    else if (line.equals("Draw request sent.") || line.equals("Abort request sent.") || line.equals("Takeback request sent.")) {

                        gameToast(getString(R.string.ics_request_sent), false);

                    } else if (get_view().isUserPlaying() && line.indexOf(get_view().getOpponent() + " offers you a draw.") >= 0) {

                        confirmShow(getString(R.string.title_offers_draw), getString(R.string.ics_offers_draw), "draw");
                    } else if (get_view().isUserPlaying() && line.indexOf(get_view().getOpponent() + " would like to adjourn the game; type \"adjourn\" to accept.") >= 0) {

                        confirmShow(getString(R.string.title_offers_adjourn), getString(R.string.ics_offers_adjourn), "adjourn");
                    }
                    /////////////////////////////////////////////////////////////
                    // chat
                    else if (line.indexOf(" tells you: ") > 0) {
                        match = _pattChat.matcher(line);
                        if (match.matches()) {
                            //globalToast(String.format(getString(R.string.ics_tells_you), match.group(1), match.group(3)));
                            String s = String.format(getString(R.string.ics_tells_you), match.group(1), match.group(3));
                            while (i + 2 < lines.length && lines[i + 1].startsWith("\\")) {
                                i++;
                                s += line.replace("\\", "");
                            }
                            addConsoleText(s);
                        }

                    }
                    // observe status
                    else if (line.indexOf("You are now observing game") >= 0) {
                        get_view().setViewMode(ICSChessView.VIEW_WATCH);
                        //gameToast("Observing a game", false);
                    }
                    // stop observing
                    else if (line.indexOf("Removing game") >= 0 && line.indexOf("from observation list") > 0 || line.indexOf("You are no longer examining game") >= 0) {
                        //gameToast("No longer observing the game", true);
                        get_view().setViewMode(ICSChessView.VIEW_NONE);
                    }
                    // examine
                    else if (line.indexOf("puzzlebot has made you an examiner of game") >= 0) {
                        get_view().setViewMode(ICSChessView.VIEW_PUZZLE);
                        gameToast("Puzzle started", false);
                    }
                    // stop problem
                    else if (line.indexOf("Your current problem has been stopped") >= 0) {
                        get_view().setViewMode(ICSChessView.VIEW_NONE);
                        gameToast("Puzzle stopped", true);
                    }
                    /////////////////////////////////////////////////////////////
                    // game talk
                    else if (line.indexOf("[" + get_view().getGameNum() + "] says: ") > 0) {
                        String s = line;
                        while (i + 2 < lines.length && lines[i + 1].startsWith("\\")) {
                            i++;
                            s += line.replace("\\", "");
                        }
                        addConsoleText(s);
                    }

                    //////////////////////////////////////////////////////////////
                    // TODO what is this
                    else if (line.indexOf("-->") == 0) {
                        // globalToast(line);
                    } else if (line.indexOf("kibitzes:") > 0) {
                        String s = line.replace("kibitzes:", "");
                        while (i + 2 < lines.length && lines[i + 1].startsWith("\\")) {
                            i++;
                            s += line.replace("\\", "");
                        }
                        addConsoleText(s);
                    }
                    /////////////////////////////////////////////////////////////////
                    // result of sought
                    else if ((false == get_view().isUserPlaying()) && _pattSought.matcher(line).matches()) {
                        match = _pattSought.matcher(line);
                        if (match.matches()) {

                            //Log.i("PATSOUGHT", "groupCount " + match.groupCount());
                            if (match.groupCount() > 7) {

                                String s, type, rated;
                                if (_serverType == SERVER_FICS) {
                                    // 1   2    3                  4   5  6       7          8
                                    // 209 1739 rahulso            15  10 unrated standard   [white]     0-9999 m
                                    s = String.format("%2dm+%2ds", Integer.parseInt(match.group(4)), Integer.parseInt(match.group(5)));
                                    type = match.group(7);
                                    rated = match.group(6);
                                } else {
                                    //  1 2    3                   4         5   6    7      8
                                    //  5 1111 SlowFlo(C)          standard  30  30   rated  white        0-1700 mf
                                    s = String.format("%2dm+%2ds", Integer.parseInt(match.group(5)), Integer.parseInt(match.group(6)));
                                    type = match.group(4);
                                    rated = match.group(7);
                                }
                                //_adapter.insert(s + " " + b.getString("type") + " " + b.getString("opponent") + "[" + b.getString("rating") + "]", 0);
                                HashMap<String, String> item = new HashMap<String, String>();

                                if (type.indexOf("blitz") >= 0 || type.indexOf("standard") >= 0) {

                                    if (type.indexOf("standard") >= 0)
                                        type = "";
                                    item.put("text_game", s + " " + rated + " " + type);
                                    item.put("play", match.group(1));
                                    item.put("text_name", match.group(3));
                                    item.put("text_rating", match.group(2));
                                    _mapChallenges.add(0, item);

                                    _adapterChallenges.notifyDataSetChanged();
                                }
                                //switchToChallengeView();
                            } else {
                                Log.w("ICSClient", "pattSought match, but groupcount = " + match.groupCount());
                            }
                        }
                    }
                    // skip stuff
                    else if (line.indexOf("seeking") > 0 && line.indexOf("to respond") > 0) {
                        // skip seeking stuff, is handled via 'sought' command
                        //Log.i("ICSClient", "Skip seeking");
                    } else if (line.indexOf("ads displayed.") > 0) {
                        //Log.i("ICSClient", "Skip ads displayed");
                    }
                    //////////////////////////////////////////////////////////////////////////////////////////////
                    // result for games
                    else if ((false == get_view().isUserPlaying()) && _pattGameRow.matcher(line).matches()) {
                        //Log.i("ICSClient", "GAMEROW match");
                        match = _pattGameRow.matcher(line);
                        if (match.matches()) {
                            HashMap<String, String> item = new HashMap<String, String>();
                            //  93 2036 WFMKierzek  2229 FMKarl     [ su120   0]  26:13 -  3:49 (22-22) W: 28
                            item.put("nr", match.group(1));
                            item.put("text_rating1", match.group(2));
                            item.put("text_name1", match.group(3));
                            item.put("text_rating2", match.group(4));
                            item.put("text_name2", match.group(5));
                            item.put("text_type", match.group(6).toUpperCase() + match.group(7).toUpperCase());
                            item.put("text_time1", match.group(10) + ":" + match.group(11));
                            item.put("text_time2", match.group(12) + ":" + match.group(13));

                            _mapGames.add(0, item);
                            _adapterGames.notifyDataSetChanged();
                        }
                    }
                    //////////////////////////////////////////////////////////////////////////////////////////////
                    // result for stored games
                    else if ((false == get_view().isUserPlaying()) && _pattStoredRow.matcher(line).matches()) {
                        //Log.i(TAG, "stored row match");
                        match = _pattStoredRow.matcher(line);
                        if (match.matches()) {
                            HashMap<String, String> item = new HashMap<String, String>();
                            item.put("nr_stored", match.group(1));
                            item.put("color_stored", match.group(2));
                            item.put("text_name_stored", match.group(3));
                            item.put("available_stored", match.group(4).equals("Y") ? "*" : "");
                            _mapStored.add(item);
                            _adapterStored.notifyDataSetChanged();
                        }
                    }
                    // skip
                    else if (line.indexOf("Seek ads filtered by") >= 0) {

                    }
                    // skip
                    // Game 1540: etturN moves: Kxg1
                    else if (line.indexOf("Game") >= 0 && line.indexOf("moves:") > 0) {

                    }
                    //////////////////////////////////////////////////////////////
                    // shouts, tshouts etc...
                    // any other data we haven't matched, put it on prompt
                    else if (line.length() > 0) {
                        Log.i("ICSClient", "lines[" + i + "] " + line);
                        //Log.i("ICSClient", "lines[" + i + "][last] " + (int)(line.charAt(line.length()-1)));
                        sRaw += "\n" + line;
                    }

                } // for line
                //////////////////////////////////////////////////////////////

                if (sRaw.length() > 0) {
                    sRaw = sRaw.replace(new Character((char) 7).toString(), "").replace("\\", "").replace("\t", "").replace(_prompt, "\n").trim();
                    sRaw = sRaw.replaceAll("[\n]{2,}", "\n");
                    if (sRaw.length() > 0) {
                        addConsoleText(sRaw);
                    }
                }
            } // sinle line stuff

        } catch (Exception ex) {

            Log.e("WorkerTelnet", ex.toString());

        }
    }

    public void addConsoleText(final String s) {

        final String s2 = _tvConsole.getText() + "\n\n" + s;
        if (s2.length() > 2048) {
            _tvConsole.append(s2.substring(s2.length() - 1024));
        } else {
            _tvConsole.append(s);
        }
        _tvPlayConsole.setText(s);

    }

    private void confirmShow(String title, String text, String sendstring) {
        _dlgConfirm.setSendString(sendstring);
        _dlgConfirm.setText(title, text);
        _dlgConfirm.show();
    }

    private void gameToast(final String text, final boolean stop) {
        if (stop) {
            get_view().stopGame();
        }
        Toast t = Toast.makeText(this, text, Toast.LENGTH_LONG);
        t.setGravity(Gravity.CENTER, 0, 0);
        t.show();
    }


    private void globalToast(final String text) {
        Toast t = Toast.makeText(this, text, Toast.LENGTH_LONG);
        t.setGravity(Gravity.BOTTOM, 0, 0);
        t.show();
    }

    @Override
    protected void onResume() {

        SharedPreferences prefs = this.getPrefs();

        ChessImageView._colorScheme = prefs.getInt("ColorScheme", 0);

        _view.setConfirmMove(prefs.getBoolean("ICSConfirmMove", true));

        _ficsHandle = prefs.getString("ics_handle", null);
        _ficsPwd = prefs.getString("ics_password", null);

        _bAutoSought = prefs.getBoolean("ICSAutoSought", true);
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
            Log.i("onResume", "socket " + (_socket == null) + " worker " + (_workerTelnet == null));

            _editHandle.setText(_ficsHandle);
            _editPwd.setText(_ficsPwd);

            /////////////////////////////////////////////////////
            // DEBUG
            //switchToBoardView();
            /////////////////////////////////////////////////////

            // real
            switchToLoginView();
        }

        //rescheduleTimer();

        super.onResume();
    }

    public boolean isConnected() {
        if (_socket == null || _workerTelnet == null || (false == _workerTelnet.isAlive()) || (false == _bInICS) || (false == _socket.isConnected())) {
            return false;
        }
        return true;
    }

    @Override
    protected void onPause() {

        // lock screen orientation?
        //setRequestedOrientation(this.getResources().getConfiguration().orientation);

        ////////////////////////////////////////////////////////////

        SharedPreferences.Editor editor = this.getPrefs().edit();

        if (_bIsGuest) {
            _handle = "guest";
        }
        editor.putString("ics_handle", _ficsHandle);
        editor.putString("ics_password", _ficsPwd);

        editor.commit();

        super.onPause();
    }

    @Override
    protected void onStart() {
        Log.i("ICSClient", "onStart");
        super.onStart();
    }

    @Override
    protected void onStop() {
        Log.i("ICSClient", "onStop");
        super.onStop();
    }

    @Override
    protected void onDestroy() {
        Log.i("ICSClient", "onDestroy");

        _workerTelnet = null;
        disconnect();

        super.onDestroy();
    }

    public void rescheduleTimer() {
        _timer = new Timer(true);
        _timer.schedule(new TimerTask() {
            @Override
            public void run() {

                m_timerHandler.sendMessage(new Message());
            }
        }, 200, 5000);
    }

    public void cancelTimer() {
        if (_timer != null) {
            _timer.cancel();
        }
    }

    public void disconnect() {
        if (_socket != null) {
            try {
                _socket.close();
            } catch (Exception ex) {
            }
            _socket = null;
        }
    }

    public void sendString(String s) {
        if (_socket == null || _socket.sendString(s + "\n") == false) {
            new AlertDialog.Builder(ICSClient.this)
                    .setTitle(R.string.title_error)
                    .setMessage("Connection to server is broken.")
                    .setPositiveButton(getString(R.string.alert_ok),
                            new DialogInterface.OnClickListener() {
                                public void onClick(DialogInterface dialog, int whichButton) {
                                    dialog.dismiss();
                                    finish();
                                }
                            })
                    .show();
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
                _dlgMatch.setPlayer(m.get("text_name").toString());
                _dlgMatch.show();
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

        cancelTimer();

        if (_viewAnimatorMain.getDisplayedChild() != VIEW_MAIN_BOARD)
            _viewAnimatorMain.setDisplayedChild(VIEW_MAIN_BOARD);
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

        rescheduleTimer();

        _tvHeader.setText(R.string.ics_menu_challenges);
        if (_viewAnimatorMain.getDisplayedChild() != VIEW_MAIN_LOBBY)
            _viewAnimatorMain.setDisplayedChild(VIEW_MAIN_LOBBY);
        _viewAnimatorLobby.setDisplayedChild(VIEW_SUB_CHALLENGES);
    }

    public void switchToPlayersView() {

        cancelTimer();

        _tvHeader.setText(R.string.ics_menu_players);
        if (_viewAnimatorMain.getDisplayedChild() != VIEW_MAIN_LOBBY)
            _viewAnimatorMain.setDisplayedChild(VIEW_MAIN_LOBBY);
        _viewAnimatorLobby.setDisplayedChild(VIEW_SUB_PLAYERS);
    }

    public void switchToGamesView() {

        cancelTimer();

        _mapGames.clear();
        _adapterGames.notifyDataSetChanged();

        _tvHeader.setText(R.string.ics_menu_games);
        if (_viewAnimatorMain.getDisplayedChild() != VIEW_MAIN_LOBBY)
            _viewAnimatorMain.setDisplayedChild(VIEW_MAIN_LOBBY);
        _viewAnimatorLobby.setDisplayedChild(VIEW_SUB_GAMES);
    }

    public void switchToStoredView() {

        cancelTimer();

        _mapStored.clear();
        _adapterStored.notifyDataSetChanged();

        _tvHeader.setText(R.string.ics_menu_stored);
        if (_viewAnimatorMain.getDisplayedChild() != VIEW_MAIN_LOBBY)
            _viewAnimatorMain.setDisplayedChild(VIEW_MAIN_LOBBY);
        _viewAnimatorLobby.setDisplayedChild(VIEW_SUB_STORED);
    }

    public void switchToLoadingView() {

        cancelTimer();

        _tvHeader.setText(R.string.title_loading);
        if (_viewAnimatorMain.getDisplayedChild() != VIEW_MAIN_LOBBY) {
            _viewAnimatorMain.setDisplayedChild(VIEW_MAIN_LOBBY);
        }
        _viewAnimatorLobby.setDisplayedChild(VIEW_SUB_PROGRESS);
    }

    public void switchToLoginView() {

        cancelTimer();

        _butLogin.setEnabled(true);
        _tvHeader.setText("Login");
        if (_viewAnimatorMain.getDisplayedChild() != VIEW_MAIN_LOBBY) {
            _viewAnimatorMain.setDisplayedChild(VIEW_MAIN_LOBBY);
        }
        _viewAnimatorLobby.setDisplayedChild(VIEW_SUB_LOGIN);
    }

    public void switchToConsoleView() {

        cancelTimer();

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

    public class ComparatorHashName implements java.util.Comparator<HashMap<String, String>> {
        public int compare(HashMap<String, String> a, HashMap<String, String> b) {
            return ((String) ((HashMap<String, String>) a).get("text_name")).compareToIgnoreCase(((String) ((HashMap<String, String>) b).get("text_name")));
        }
    }

    public class ComparatorHashRating implements java.util.Comparator<HashMap<String, String>> {
        public int compare(HashMap<String, String> a, HashMap<String, String> b) {
            return -1 * ((String) ((HashMap<String, String>) a).get("text_rating")).compareToIgnoreCase(((String) ((HashMap<String, String>) b).get("text_rating")));
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

