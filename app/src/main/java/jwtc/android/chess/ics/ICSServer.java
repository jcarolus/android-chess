package jwtc.android.chess.ics;

import android.app.Service;
import android.content.Intent;
import android.os.Binder;
import android.os.Bundle;
import android.os.Handler;
import android.os.IBinder;
import android.os.Message;
import android.util.Log;

import java.lang.ref.WeakReference;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Timer;
import java.util.TimerTask;

import androidx.annotation.Nullable;
import jwtc.chess.board.ChessBoard;

public class ICSServer extends Service {
    protected static final String TAG = "ICSServer";
    protected static final int EXPECT_LOGIN_PROMPT = 1;
    protected static final int EXPECT_LOGIN_RESPONSE = 2;
    protected static final int EXPECT_PASSWORD_RESPONSE = 3;
    protected static final int EXPECT_PROMPT = 4;

    private final IBinder mBinder = new LocalBinder();
    private Handler keepAliveTimerHandler = new KeepAliveHandler();
    private TelnetSocket _socket;
    private Thread _workerTelnet;
    private Timer keepAlivetimer = null;
    private ArrayList<ICSListener> listeners = new ArrayList<>();
    protected ICSThreadMessageHandler threadHandler = new ICSThreadMessageHandler(this);
    protected int expectingState;
    protected String currentBuffer;
    protected String prompt;
    protected String handle;
    protected String password;
    protected String opponent;
    protected ICSPatterns icsPatterns = new ICSPatterns();

    public void addListener(ICSListener listener) {
        this.listeners.add(listener);
    }
    public void removeListener(ICSListener listener) {this.listeners.remove(listener);}

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        Log.i(TAG, "Received start id " + startId + ": " + intent);
        return START_STICKY;
    }

    @Override
    public void onDestroy() {
        Log.i(TAG, "onDestroy");
        tearDown();
    }

    public void tearDown() {
        _workerTelnet = null;
        try {
            _socket.close();
        } catch (Exception ex) { }
        _socket = null;
    }

    public void startSession(final String server, final int port, final String handle, final String password, final String prompt) {
        Log.i(TAG, "startSession " + server + " " + port + " " + handle);
        currentBuffer = "";
        expectingState = EXPECT_LOGIN_PROMPT;
        this.prompt = prompt;
        this.handle = handle;
        this.password = password;
        opponent = "";

        _workerTelnet = new Thread(new Runnable() {
            public void run() {
            try {
                _socket = new TelnetSocket(server, port);
            } catch (Exception ex) {
                Message message = new Message();
                message.what = ICSThreadMessageHandler.MSG_CONNECTION_CLOSED;
                threadHandler.sendMessage(message);
                return;
            }

            try {
                while (_socket != null && _socket.isConnected()) {
                    String buffer = _socket.readString();

                    // Log.i(TAG, "Buffer " + buffer == null ? "NULL" : buffer);

                    if (buffer != null && buffer.length() > 0) {
                        Message message = new Message();
                        message.what = ICSThreadMessageHandler.MSG_PARSE;
                        Bundle bundle = new Bundle();
                        bundle.putString("buffer", buffer);
                        message.setData(bundle);
                        threadHandler.sendMessage(message);
                    }
                }
                Log.i(TAG, "End of workerTelnet");
                cancelKeepAliveTimer();

                Message message = new Message();
                message.what = ICSThreadMessageHandler.MSG_CONNECTION_CLOSED;
                threadHandler.sendMessage(message);
                //
            } catch (Exception ex) {
                Message message = new Message();
                message.what = ICSThreadMessageHandler.MSG_ERROR;
                threadHandler.sendMessage(message);
            }
            }
        });
        _workerTelnet.start();
    }

    public boolean sendString(String s) {
        Log.i(TAG, "sendString: " + s);
        return _socket != null && _socket.sendString(s + "\n");
    }

    public boolean isUserPlaying() {
        return false;
    }

    public boolean isConnected() {
        if (_socket != null && _socket.isConnected()) {
            return expectingState != EXPECT_LOGIN_PROMPT;
        }
        return false;
    }

    public String getHandle() {
        return handle;
    }

    public boolean isGuest() {
        return handle != null && handle.startsWith("Guest");
    }

    public void handleThreadMessage(Message msg) {
        switch (msg.what) {
            case ICSThreadMessageHandler.MSG_PARSE:
                String buffer = msg.getData().getString("buffer");
                handleBufferMessage(buffer);
                break;
            case ICSThreadMessageHandler.MSG_CONNECTION_CLOSED:
                Log.i(TAG, "MSG_CONNECTION_CLOSED");
                for (ICSListener listener: listeners) {listener.OnSessionEnded();}
                break;
            case ICSThreadMessageHandler.MSG_ERROR:
                Log.i(TAG, "MSG_ERROR");
                for (ICSListener listener: listeners) {listener.OnError();}
                break;
            default:
                Log.e(TAG, "Unecpected msg.what");
                break;
        }
    }

    public void handleBufferMessage(String buffer) {
//        Log.i(TAG, "handleBufferMessage: " + buffer);
        currentBuffer += buffer;
        String waitingFor = prompt;
        String contains = "**** ";

        switch (expectingState) {
            case EXPECT_LOGIN_PROMPT:
                waitingFor = "login: ";
                break;
            case EXPECT_LOGIN_RESPONSE:
                if (handle.equals("guest")) {
                    waitingFor = "\":";
                } else {
                    waitingFor = ": ";
                }
                break;
            case EXPECT_PASSWORD_RESPONSE:
                waitingFor = null;
                break;
        }
        if (
            waitingFor != null && currentBuffer.endsWith(waitingFor) ||
            waitingFor == null && currentBuffer.contains(contains) && buffer.length() > 20
        ) {
            this.parse(currentBuffer);
            currentBuffer = "";
        }
    }

    private void parse(String buffer) {
        //Log.i(TAG, expectingState + "; parse: " + buffer);
        String[] lines = buffer.split("\n\r");
        int lineCount = lines.length;

        if (expectingState == EXPECT_LOGIN_PROMPT) {
            if (buffer.endsWith("login: ")) {
                if (sendString(handle)) {
                    expectingState = EXPECT_LOGIN_RESPONSE;
                    for (ICSListener listener: listeners) {listener.OnLoggingIn();}
                    return;
                } else {
                    dispatchLoginerror("Could net send handle");
                }
            } else {
                dispatchLoginerror("Unexpected response while expecting login prompt");
            }

            return;
        }

        if (expectingState == EXPECT_LOGIN_RESPONSE) {
            buffer = ICSPatterns.replaceChars(buffer, ICSPatterns.loginChars, "");
            if (handle.equals("guest")) {
                if (buffer.contains("Press return to enter the server as")) {
                    String guestHandle = icsPatterns.parseGuestHandle(buffer);
                    if (guestHandle != null) {
                        if (sendString("")) {
                            handle = guestHandle;
                            expectingState = EXPECT_PASSWORD_RESPONSE;
                            return;
                        } else {
                            dispatchLoginerror("Could net send handle");
                            return;
                        }
                    } else {
                        dispatchLoginerror("Could not get guest handle from response");
                        return;
                    }
                }
                dispatchLoginerror("Unexpected response on guest login");
                return;
            }
            if (buffer.contains("password: ")) {
                if (sendString(password)) {
                    expectingState = EXPECT_PASSWORD_RESPONSE;
                    return;
                } else {
                    dispatchLoginerror("Could net send password");
                    return;
                }
            }
            dispatchLoginerror("Unexpected response on guest login");
            return;
        }

        if (expectingState == EXPECT_PASSWORD_RESPONSE) {
            buffer = ICSPatterns.replaceChars(buffer, ICSPatterns.loginChars, "");
            if (handle.startsWith("Guest")) {
                if (icsPatterns.isSessionStarting(buffer)) {
                    expectingState = EXPECT_PROMPT;
                    for (ICSListener listener: listeners) {listener.OnLoginSuccess();}
                    return;
                }
                dispatchLoginerror("Unexpected buffer on guest password response: " + buffer);
                return;
            }
            if (icsPatterns.isInvalidPassword(buffer)) {
                dispatchLoginerror("Invalid password");
                return;
            }
            if (icsPatterns.isSessionStarting(buffer)) {
                expectingState = EXPECT_PROMPT;
                for (ICSListener listener: listeners) {listener.OnLoginSuccess();}
                return;
            }
            dispatchLoginerror("Unexpected buffer on password response: " + buffer);
            return;
        }

        if (expectingState != EXPECT_PROMPT) {
            Log.i(TAG, "Invalid expect state");
            return;
        }

        startKeepAliveTimer();

        // check multiline responses
        if (icsPatterns.containsGamesDisplayed(buffer, lineCount)) {
            ArrayList<HashMap<String, String>> games = new ArrayList<HashMap<String, String>>();
            for (int i = 0; i < lineCount - 2; i++) {
                HashMap<String, String> gameMap = icsPatterns.parseGameLine(lines[i]);
                if (gameMap != null) {
                    games.add(gameMap);
                }
            }
            if (games.size() > 0) {
                for (ICSListener listener: listeners) {listener.OnGameListResult(games);}
            }
            return;
        }

        if (icsPatterns.containsAdsDisplayed(buffer, lineCount)) {
            ArrayList<HashMap<String, String>> soughtList = new ArrayList<HashMap<String, String>>();
            for (int i = 0; i < lineCount - 2; i++) {
                HashMap<String, String> sought = icsPatterns.parseSought(lines[i]);
                if (sought != null) {
                    soughtList.add(sought);
                }
            }
            if (soughtList.size() > 0) {
                for (ICSListener listener: listeners) {listener.OnSoughtResult(soughtList);}
            }
            return;
        }

        if (icsPatterns.containsPlayersDisplayed(buffer, lineCount)) {
            ArrayList<HashMap<String, String>> playerList = new ArrayList<HashMap<String, String>>();
            for (int i = 0; i < lineCount - 2; i++) {
                HashMap<String, String> player = icsPatterns.parsePlayerLine(lines[i]);
                if (player != null) {
                    playerList.add(player);
                }
            }
            if (playerList.size() > 0) {
                for (ICSListener listener: listeners) {listener.OnPlayerList(playerList);}
            }
            return;
        }

        // single line parsing
        for (int i = 0; i < lines.length; i++) {
            String line = lines[i].replace(prompt, "");

            HashMap<String, String> gameInfo = icsPatterns.parseGameInfo(line);
            if (gameInfo != null) {
                for (ICSListener listener: listeners) {listener.OnPlayGameStarted(gameInfo.get("whiteHandle"), gameInfo.get("blackHandle"), gameInfo.get("whiteRating"), gameInfo.get("blackRating"));}
                continue;
            }

            HashMap<String, String> board = icsPatterns.parseBoard(line);
            if (board != null) {
                for (ICSListener listener: listeners) {listener.OnBoardUpdated(board.get("board"), handle);}
                continue;
            }

            HashMap<String, String> challenge = icsPatterns.parseChallenge(line, handle);
            if (challenge != null) {
                for (ICSListener listener: listeners) {listener.OnChallenged(challenge);}
                continue;
            }

            if (icsPatterns.isSeekNotAvailable(line)) {
                for (ICSListener listener: listeners) {listener.OnSeekNotAvailable();}
                continue;
            }

            int gameNum = icsPatterns.getCreatingOrContinuingGameNumber(line);
            if (gameNum > 0) {
                for (ICSListener listener: listeners) {listener.OnGameNumberUpdated(gameNum);}
                continue;
            }

            if (icsPatterns.isResumingAdjournedGame(line)) {
                for (ICSListener listener: listeners) {listener.OnResumingAdjournedGame();}
                continue;
            }

            if (icsPatterns.isIllegalMove(line)) {
                for (ICSListener listener: listeners) {listener.OnIllegalMove();}
                continue;
            }

            if (icsPatterns.isAbortRequest(line, opponent)) {
                for (ICSListener listener: listeners) {listener.OnOpponentRequestsAbort();}
                continue;
            }

            if (icsPatterns.isAbortedConfirmed(line)) {
                for (ICSListener listener: listeners) {listener.OnAbortConfirmed();}
                continue;
            }

            if (icsPatterns.isTakeBackRequest(line, opponent)) {
                for (ICSListener listener: listeners) {listener.OnOpponentRequestsTakeBack();}
                continue;
            }

            if (icsPatterns.isAdjournRequest(line, opponent)) {
                for (ICSListener listener: listeners) {listener.OnOpponentRequestsAdjourn();}
                continue;
            }

            if (icsPatterns.isAbortedOrAdourned(line)) {
                for (ICSListener listener: listeners) {listener.OnAbortedOrAdjourned();}
                continue;
            }

            if (icsPatterns.isAbortOrDrawOrAdjourneRequestSent(line)) {
                for (ICSListener listener: listeners) {listener.OnYourRequestSended();}
                continue;
            }

            int result = icsPatterns.gameState(line);
            if (result != ChessBoard.PLAY) {
                for (ICSListener listener: listeners) {listener.OnEndGameResult(result);}
                continue;
            }

            if (icsPatterns.isDrawRequest(line, opponent)) {
                for (ICSListener listener: listeners) {listener.OnOpponentOffersDraw();}
                continue;
            }

            if (icsPatterns.isNowOservingGame(line)) {
                for (ICSListener listener: listeners) {listener.OnObservingGameStarted();}
                continue;
            }

            if (icsPatterns.isStopObservingGame(line)) {
                for (ICSListener listener: listeners) {listener.OnObservingGameStopped();}
                continue;
            }

            if (icsPatterns.isStopExaminingGame(line)) {
                for (ICSListener listener: listeners) {listener.OnExaminingGameStopped();}
                continue;
            }

            if (icsPatterns.isPuzzleStarted(line)) {
                for (ICSListener listener: listeners) {listener.OnPuzzleStarted();}
                continue;
            }

            if (icsPatterns.isPuzzleStopped(line)) {
                for (ICSListener listener: listeners) {listener.OnPuzzleStopped();}
                continue;
            }

            if (icsPatterns.filterOutput(line)) {
                continue;
            }

            Log.i(TAG, "[" + line + "]");

            for (ICSListener listener: listeners) {listener.OnConsoleOutput(line);}
        }
    }

    public void startKeepAliveTimer() {
        if (keepAlivetimer == null) {
            keepAlivetimer = new Timer(true);
            keepAlivetimer.schedule(new TimerTask() {
                @Override
                public void run() {
                keepAliveTimerHandler.sendEmptyMessage(0);
                }
            }, 30000, 30000);  // send every 30 seconds
        }
    }

    public void cancelKeepAliveTimer() {
        if (keepAlivetimer != null) {
            keepAlivetimer.cancel();
            keepAlivetimer = null;
        }
    }

    @Nullable
    @Override
    public IBinder onBind(Intent intent) {
        return mBinder;
    }


    public class LocalBinder extends Binder {
        ICSServer getService() {
            return ICSServer.this;
        }
    }

    private void dispatchLoginerror(String error) {
        for (ICSListener listener: listeners) {listener.OnLoginFailed(error);}
    }

    private class KeepAliveHandler extends Handler {
        WeakReference<ICSServer> icsServerReference;

        public KeepAliveHandler() {
            icsServerReference = new WeakReference<ICSServer>(ICSServer.this);
        }

        @Override
        public void handleMessage(Message msg) {
            ICSServer icsServer = this.icsServerReference.get();
            if (icsServer != null) {
                icsServer.sendString("sought");
            }
        }
    }
}
