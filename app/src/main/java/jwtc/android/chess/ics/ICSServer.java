package jwtc.android.chess.ics;

import android.os.Bundle;
import android.os.Message;
import android.util.Log;

import java.util.ArrayList;
import java.util.HashMap;

public class ICSServer {
    protected static final String TAG = "ICSServer";
    protected static final int EXPECT_LOGIN_PROMPT = 1;
    protected static final int EXPECT_LOGIN_RESPONSE = 2;
    protected static final int EXPECT_PASSWORD_RESPONSE = 3;
    protected static final int EXPECT_PROMPT = 4;

    private TelnetSocket _socket;
    private Thread _workerTelnet;
    private ICSListener listener;
    protected ICSThreadMessageHandler threadHandler = new ICSThreadMessageHandler(this);
    protected int expectingState;
    protected String currentBuffer;
    protected String prompt;
    protected String handle;
    protected String password;
    protected String opponent;
    protected ICSPatterns icsPatterns = new ICSPatterns();

    ICSServer(ICSListener listener) {
        this.listener = listener;
    }

    public void initialize() {

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
                    message.what = ICSThreadMessageHandler.MSG_CONNECTION_ERROR;
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

    public void handleThreadMessage(Message msg) {
        switch (msg.what) {
            case ICSThreadMessageHandler.MSG_PARSE:
                String buffer = msg.getData().getString("buffer");
                handleBufferMessage(buffer);
                break;
            case ICSThreadMessageHandler.MSG_CONNECTION_ERROR:
                Log.i(TAG, "MSG_CONNECTION_ERROR");
                listener.OnError();
                break;
            case ICSThreadMessageHandler.MSG_ERROR:
                Log.i(TAG, "MSG_ERROR");
                listener.OnError();
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

        listener.OnConsoleOutput(buffer);

        if (expectingState == EXPECT_LOGIN_PROMPT) {
            if (buffer.endsWith("login: ")) {
                if (sendString(handle)) {
                    expectingState = EXPECT_LOGIN_RESPONSE;
                    listener.OnLoggingIn();
                    return;
                } else {
                    Log.i(TAG, "Could net send handle");
                    return;
                }
            }
            Log.i(TAG, "Unexpected buffer when expecting login prompt");
            return;
        }

        if (expectingState == EXPECT_LOGIN_RESPONSE) {
            if (handle.equals("guest")) {
                if (buffer.contains("Press return to enter the server as")) {
                    String guestHandle = icsPatterns.parseGuestHandle(buffer);
                    if (guestHandle != null) {
                        if (sendString("")) {
                            handle = guestHandle;
                            expectingState = EXPECT_PASSWORD_RESPONSE;
                            return;
                        } else {
                            Log.i(TAG, "Could net send handle");
                            return;
                        }
                    } else {
                        Log.i(TAG, "Could not get guest handle from response");
                        return;
                    }
                }
                Log.i(TAG, "Unexpected buffer on guest login response");
                return;
            }
            if (buffer.contains("password: ")) {
                if (sendString(password)) {
                    expectingState = EXPECT_PASSWORD_RESPONSE;
                    return;
                } else {
                    Log.i(TAG, "Could net send handle");
                    return;
                }
            }
            Log.i(TAG, "Unexpected buffer on guest login response");
            return;
        }

        if (expectingState == EXPECT_PASSWORD_RESPONSE) {
            if (handle.startsWith("Guest")) {
                if (icsPatterns.isSessionStarting(buffer)) {
                    expectingState = EXPECT_PROMPT;
                    listener.OnLoginSuccess();
                    return;
                }
                Log.i(TAG, "Unexpected buffer on guest password response: " + buffer);
                return;
            }
            if (icsPatterns.isInvalidPassword(buffer)) {
                listener.OnLoginFailed();
                return;
            }
            if (icsPatterns.isSessionStarting(buffer)) {
                expectingState = EXPECT_PROMPT;
                listener.OnLoginSuccess();
                return;
            }
            Log.i(TAG, "Unexpected buffer on password response: " + buffer);
            return;
        }

        if (expectingState != EXPECT_PROMPT) {
            Log.i(TAG, "Unvalid expect state");
            return;
        }

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
                this.listener.OnGameListResult(games);
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
                listener.OnSoughtResult(soughtList);
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
                listener.OnPlayerList(playerList);
            }
            return;
        }

        // single line parsing
        for (int i = 0; i < lines.length; i++) {
            String line = lines[i].replace(prompt, "");

            HashMap<String, String> gameInfo = icsPatterns.parseGameInfo(line);
            if (gameInfo != null) {
                listener.OnPlayGameStarted(gameInfo.get("whiteHandle"), gameInfo.get("blackHandle"), gameInfo.get("whiteRating"), gameInfo.get("blackRating"));
                continue;
            }

            HashMap<String, String> board = icsPatterns.parseBoard(line);
            if (board != null) {
                listener.OnBoardUpdated(board.get("board"), handle);
                continue;
            }

            HashMap<String, String> challenge = icsPatterns.parseChallenge(line, handle);
            if (challenge != null) {
                listener.OnChallenged(challenge.get("opponent"), challenge.get("rating"), "@TODO");
                continue;
            }

            if (icsPatterns.isSeekNotAvailable(line)) {
                listener.OnSeekNotAvailable();
                continue;
            }

            int gameNum = icsPatterns.getCreatingOrContinuingGameNumber(line);
            if (gameNum > 0) {
                listener.OnGameNumberUpdated(gameNum);
                continue;
            }

            if (icsPatterns.isResumingAdjournedGame(line)) {
                listener.OnResumingAdjournedGame();
                continue;
            }

            if (icsPatterns.isIllegalMove(line)) {
                listener.OnIllegalMove();
                continue;
            }

            if (icsPatterns.isAbortRequest(line, opponent)) {
                listener.OnOpponentRequestsAbort();
                continue;
            }

            if (icsPatterns.isAbortedConfirmed(line)) {
                listener.OnAbortConfirmed();
                continue;
            }

            if (icsPatterns.isTakeBackRequest(line, opponent)) {
                listener.OnOpponentRequestsTakeBack();
                continue;
            }

            if (icsPatterns.isAdjournRequest(line, opponent)) {
                listener.OnOpponentRequestsAdjourn();
                continue;
            }

            if (icsPatterns.isAbortedOrAdourned(line)) {
                listener.OnAbortedOrAdjourned();
                continue;
            }

            if (icsPatterns.isAbortOrDrawOrAdjourneRequestSent(line)) {
                listener.OnYourRequestSended();
                continue;
            }

            if (icsPatterns.isDrawRequest(line, opponent)) {
                listener.OnOpponentOffersDraw();
                continue;
            }

            if (icsPatterns.isNowOservingGame(line)) {
                listener.OnObservingGameStarted();
                continue;
            }

            if (icsPatterns.isStopObservingGame(line)) {
                listener.OnObservingGameStopped();
                continue;
            }

            if (icsPatterns.isStopExaminingGame(line)) {
                listener.OnExaminingGameStopped();
                continue;
            }

            if (icsPatterns.isPuzzleStarted(line)) {
                listener.OnPuzzleStarted();
                continue;
            }

            if (icsPatterns.isPuzzleStopped(line)) {
                listener.OnPuzzleStopped();
                continue;
            }
        }

    }
}
