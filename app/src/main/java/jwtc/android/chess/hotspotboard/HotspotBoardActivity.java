package jwtc.android.chess.hotspotboard;

import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.ServiceConnection;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.os.Message;
import android.os.Messenger;
import android.os.RemoteException;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.appcompat.app.AlertDialog;

import com.google.android.material.button.MaterialButton;
import com.google.android.material.button.MaterialButtonToggleGroup;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.android.material.textfield.TextInputEditText;
import com.google.android.material.textfield.TextInputLayout;
import com.google.android.material.switchmaterial.SwitchMaterial;

import jwtc.android.chess.R;
import jwtc.android.chess.activities.ChessBoardActivity;
import jwtc.android.chess.helpers.Clipboard;
import jwtc.android.chess.services.NetworkAddressHelper;
import jwtc.chess.Move;
import jwtc.chess.board.BoardConstants;

import jwtc.android.chess.helpers.ActivityHelper;

public class HotspotBoardActivity extends ChessBoardActivity {
    private final Messenger messengerToService = new Messenger(new IncomingHandler());
    private final String TAG = "HotspotBoardActivity";
    private Messenger messengerFromService;
    private MaterialButtonToggleGroup colorToggleGroup;
    private MaterialButton buttonConnect, buttonDisconnect, buttonCopyIp;
    private LinearLayout layoutConnect;
    private LinearLayout layoutSessionSummary;
    private boolean isHost = true, isPlayAsWhite = true;
    private boolean isShareMode = false;
    private boolean isObserving = false;
    private boolean isListening = false;
    private boolean hasReceivedGameUpdate = false;
    private int connectionMode = HotspotBoardService.CONNECTION_MODE_HOTSPOT;
    private MaterialButton buttonResign, buttonDraw, buttonNew;
    private LinearLayout layoutGameButtons, layoutNewGameButtons;
    private TextView textPlayer, textOpponent;
    private TextView textStatus, textSessionSummary;
    private ImageView imageBottomTurn, imageTopTurn, imageTurnWhite, imageTurnBlack;
    private final Handler statusHandler = new Handler(Looper.getMainLooper());
    private int overrideGameState = 0; // @TODO
    private boolean isServiceBound = false;
    private boolean hasActiveSession = false;
    private String configuredName = "";
    private String configuredHostIp = "";

    private ServiceConnection connection = new ServiceConnection() {
        @Override
        public void onServiceConnected(ComponentName name, IBinder service) {
            Log.d(TAG, "onServiceConnected");
            messengerFromService = new Messenger(service);
            isServiceBound = true;
            // Send our messenger so service can talk to us
            Message msg = Message.obtain(null, HotspotBoardService.MSG_ACTIVITY_CONNECTED);
            msg.replyTo = messengerToService;
            try {
                messengerFromService.send(msg);
                syncRestoredSessionUi();
            } catch (RemoteException e) {
                e.printStackTrace();
            }
        }

        @Override
        public void onServiceDisconnected(ComponentName name) {
            Log.d(TAG, "onServiceDisconnected");
            messengerFromService = null;
            isServiceBound = false;
        }
    };

    @Override
    public void onMoveApplied(int move) {
        super.onMoveApplied(move);

        Log.d(TAG, "OnMove " + move);

        String lastMovePgn = getLastMoveDescription(false);

        Message msg = Message.obtain(null, HotspotBoardService.MSG_SEND_GAME_UPDATE);
        Bundle bundle = new Bundle();
        try {
            GameMessage message = new GameMessage(
                gameApi.getFEN(),
                ((HotspotBoardApi) gameApi).getWhite(),
                ((HotspotBoardApi) gameApi).getBlack(),
                move,
                lastMovePgn
            );
            bundle.putString("data", message.toJsonString());
            msg.setData(bundle);

            messengerFromService.send(msg);
        } catch (Exception e) {
            Log.d(TAG, "Could net send game message");
            e.printStackTrace();
        }
    }

    @Override
    public boolean requestMove(final int from, final int to) {
        Log.d(TAG, "requestMove");
        if (isObserving || isShareMode) {
            rebuildBoard();
            Log.d(TAG, "requestMove observing or sharing");
            return false;
        }
        if (((HotspotBoardApi) gameApi).isMyTurn()) {
            boolean res = super.requestMove(from, to);
            if (!res) {
                rebuildBoard();
            }
            return res;
        }
        rebuildBoard();
        Log.d(TAG, "requestMove not my turn");
        return false;
    }

    public void startSession() {
        Log.d(TAG, "startSession called " + isHost);
        isObserving = false;
        hasActiveSession = true;
        isListening = isHost;
        updateConnectedState(false);
        updateStatus(isHost
            ? getString(isShareMode ? R.string.hotspot_status_waiting_observer : R.string.hotspot_status_waiting)
            : getString(R.string.hotspot_status_connecting));
        try {
            if (messengerFromService != null) {
                Message startMsg = Message.obtain(null, HotspotBoardService.MSG_START_SESSION);
                Log.d(TAG, "startMsg " + (startMsg == null ? "null" : "object"));
                startMsg.arg1 = isHost ? 1 : 0; // boolean isHost
                Bundle sessionData = new Bundle();
                sessionData.putInt(HotspotBoardService.KEY_CONNECTION_MODE, connectionMode);
                sessionData.putString(HotspotBoardService.KEY_HOST_IP, configuredHostIp);
                sessionData.putInt(HotspotBoardService.KEY_HOST_MODE, isShareMode
                    ? HotspotBoardService.HOST_MODE_SHARE
                    : HotspotBoardService.HOST_MODE_PLAY);
                startMsg.setData(sessionData);
                messengerFromService.send(startMsg);

            } else {
                Log.d(TAG, "messengerFromService is null");
            }
        } catch (RemoteException e) {
            Log.d(TAG, "startSession failed");
            e.printStackTrace();
        }
    }

    private void sendGameMessage(int type, int lastMove, String lastMovePgn) {
        try {
            GameMessage message = new GameMessage(
                type,
                gameApi.getFEN(),
                ((HotspotBoardApi) gameApi).getWhite(),
                ((HotspotBoardApi) gameApi).getBlack(),
                lastMove,
                lastMovePgn
            );
            Message msg = Message.obtain(null, HotspotBoardService.MSG_SEND_GAME_UPDATE);
            Bundle bundle = new Bundle();
            bundle.putString("data", message.toJsonString());
            msg.setData(bundle);
            messengerFromService.send(msg);
        } catch (Exception e) {
            Log.e(TAG, "sendGameMessage failed", e);
        }
    }

    private class IncomingHandler extends Handler {
        public IncomingHandler() {
            super(Looper.getMainLooper());
        }

        @Override
        public void handleMessage(Message msg) {
            if (msg.what == HotspotBoardService.MSG_RECEIVED_GAME_UPDATE) {
                String data = msg.getData().getString("data");
                Log.d(TAG, "Received from service: " + data);
                // Update UI here
                if (data != null) {
                    try {

                        GameMessage message = GameMessage.fromJson(data);

                        if (!hasReceivedGameUpdate && message.type != GameMessage.TYPE_SHARE_SNAPSHOT) {
                            hasReceivedGameUpdate = true;
                        }

                        ((HotspotBoardApi) gameApi).onGameUpdate(message);

                        switch (message.type) {
                            case GameMessage.TYPE_SHARE_SNAPSHOT:
                                isObserving = true;
                                updateObservingState(true);
                                break;
                            case GameMessage.TYPE_MOVE:
                                isObserving = false;
                                updateObservingState(false);
                                if (message.lastMove > 0) {
                                    moveToPositions.clear();
                                    highlightedPositions.clear();
                                    highlightedPositions.add(Move.getFrom(message.lastMove));
                                    highlightedPositions.add(Move.getTo(message.lastMove));
                                    updateSelectedSquares();

                                    updateTextViewOrSpeech(textStatus, message.lastMovePgn);
                                }
                                buttonDraw.setEnabled(true);
                                break;
                            case GameMessage.TYPE_RESIGN:
                                if (((HotspotBoardApi) gameApi).isPlayingAsWhite()) {
                                    overrideGameState = BoardConstants.BLACK_RESIGNED;
                                } else {
                                    overrideGameState = BoardConstants.WHITE_RESIGNED;
                                }
                                showGameResult("Victory!", ((HotspotBoardApi) gameApi).getOpponentName() + " has resigned.");
                                break;
                            case GameMessage.TYPE_DRAW_OFFER:
                                openConfirmDialog("Draw Offer", "Accept", "Decline", () -> {
                                    sendGameMessage(GameMessage.TYPE_DRAW_ACCEPT, 0, "");
                                    showGameResult("Game Over", "The game is a draw.");
                                }, () -> {
                                    sendGameMessage(GameMessage.TYPE_DRAW_DECLINE, 0, "");
                                });
                                break;
                            case GameMessage.TYPE_DRAW_ACCEPT:
                                overrideGameState = BoardConstants.DRAW_AGREEMENT;
                                showGameResult("Game Over", "The game is a draw.");
                                break;
                            case GameMessage.TYPE_DRAW_DECLINE:
                                updateStatus(getString(R.string.hotspot_status_draw_declined));
                                break;
                        }

                    } catch (Exception ex) {
                        Log.d(TAG, "Could not parse game message: " + ex.toString());
                    }
                }
            } else if (msg.what == HotspotBoardService.MSG_SOCKET_LISTENING) {
                hasActiveSession = true;
                isListening = true;
                updateConnectedState(false);
                updateStatus(getString(isShareMode
                    ? R.string.hotspot_status_waiting_observer
                    : R.string.hotspot_status_waiting));
            } else if (msg.what == HotspotBoardService.MSG_SOCKET_CONNECTED) {
                hasActiveSession = true;
                isListening = false;
                updateConnectedState(true);

                updateStatus(getString(isHost
                    ? (isShareMode ? R.string.hotspot_status_observer_connected_host : R.string.hotspot_status_opponent_connected_host)
                    : R.string.hotspot_status_opponent_connected_client));
            } else if (msg.what == HotspotBoardService.MSG_SOCKET_DISCONNECTED) {
                hasActiveSession = false;
                isListening = false;
                isObserving = false;
                hasReceivedGameUpdate = false;
                updateObservingState(false);
                updateConnectedState(false);

                updateStatus(getString(R.string.hotspot_status_disconnected));
            }
        }
    }

    @Override
    protected void onStart() {
        super.onStart();
        Log.d(TAG, "onStart, call bindService");
        bindHotspotBoardService();
    }

    @Override
    protected void onStop() {
        super.onStop();
        Log.d(TAG, "onStop");
        // if game is on, opponent wins
        if (messengerFromService != null && ((HotspotBoardApi) gameApi).getOpponentName().length() > 0) {
            // This might not be sent if the service is already disconnected.
            // The opponent will see a socket disconnection message.
        }
        if (isServiceBound) {
            unbindService(connection);
            isServiceBound = false;
        }
        messengerFromService = null;
    }

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        Log.d(TAG, "onCreate");
        SharedPreferences prefs = getPrefs();
        isHost = prefs.getBoolean("hostpotboardIsHost", true);
        isShareMode = prefs.getBoolean("hotspotboardShareMode", false);
        connectionMode = prefs.getInt("hotspotboardConnectionMode", HotspotBoardService.CONNECTION_MODE_HOTSPOT);
        configuredName = prefs.getString("hotspotboardName", "");
        configuredHostIp = prefs.getString("hotspotboardHostIp", "");
        gameApi = new HotspotBoardApi();
        setContentView(R.layout.hotspotboard);

        ActivityHelper.fixPaddings(this, findViewById(R.id.root_layout));

        // init shared views before aftercreate
        switchSound = findViewById(R.id.SwitchSound);
        switchMoveToSpeech = findViewById(R.id.SwitchSpeech);
        switchAccessibilityDrag = findViewById(R.id.SwitchAccessibilityDrag);

        afterCreate();
        View boardAreaLayout = findViewById(R.id.board_area);
        if (boardAreaLayout == null) {
            boardAreaLayout = findViewById(R.id.includeboard);
        }
        initBoardLayoutSizing(findViewById(R.id.root_layout), boardAreaLayout, findViewById(R.id.play_controls), null, null);

        colorToggleGroup = findViewById(R.id.colorToggleGroup);

        // Set default selection to White
        colorToggleGroup.check(R.id.buttonWhite);
        colorToggleGroup.addOnButtonCheckedListener((group, checkedId, isChecked) -> {
            if (isChecked) {
                isPlayAsWhite = checkedId == R.id.buttonWhite;
            }
        });

        imageBottomTurn = findViewById(R.id.ImageBottomTurn);
        imageTopTurn = findViewById(R.id.ImageTopTurn);
        textPlayer = findViewById(R.id.TextPlayer);
        textOpponent = findViewById(R.id.TextOpponent);
        layoutConnect = findViewById(R.id.LayoutConnect);
        layoutSessionSummary = findViewById(R.id.LayoutSessionSummary);
        layoutGameButtons = findViewById(R.id.LayoutGameButtons);
        layoutNewGameButtons = findViewById(R.id.LayoutNewGame);
        buttonResign = findViewById(R.id.ButtonResign);
        buttonDraw = findViewById(R.id.ButtonDraw);
        buttonNew = findViewById(R.id.ButtonNew);
        buttonDisconnect = findViewById(R.id.ButtonDisconnect);
        buttonCopyIp = findViewById(R.id.ButtonCopyIp);
        textStatus = findViewById(R.id.TextStatus);
        textSessionSummary = findViewById(R.id.TextSessionSummary);

        // default rotation
        imageTurnWhite = imageBottomTurn;
        imageTurnBlack = imageTopTurn;

        buttonNew.setOnClickListener(v -> newGame());

        buttonConnect = findViewById(R.id.ButtonConnect);
        buttonConnect.setOnClickListener(arg0 -> showConnectionDialog());
        buttonDisconnect.setOnClickListener(v -> stopSharing());
        buttonCopyIp.setOnClickListener(v -> copyLocalIp());

        buttonResign.setOnClickListener(v -> {
            openConfirmDialog("Are you sure you want to resign?", "Yes", "No", () -> {
                sendGameMessage(GameMessage.TYPE_RESIGN, 0, "");
                if (((HotspotBoardApi) gameApi).isPlayingAsWhite()) {
                    overrideGameState = BoardConstants.WHITE_RESIGNED;
                } else {
                    overrideGameState = BoardConstants.BLACK_RESIGNED;
                }
                showGameResult("Defeat", "You resigned.");
            }, null);
        });

        buttonDraw.setOnClickListener(v -> {
            sendGameMessage(GameMessage.TYPE_DRAW_OFFER, 0, "");
            updateStatus(getString(R.string.hotspot_status_draw_sent));
            buttonDraw.setEnabled(false);
        });

        refreshConnectionControls();
    }

    @Override
    public void onResume() {
        super.onResume();

        Log.d(TAG, "messengerFromService " + (messengerFromService == null));

        SharedPreferences prefs = getPrefs();
        configuredName = prefs.getString("hotspotboardName", configuredName);
        configuredHostIp = prefs.getString("hotspotboardHostIp", configuredHostIp);
        refreshConnectionControls();
        syncRestoredSessionUi();
    }

    @Override
    protected void onPause() {
        SharedPreferences.Editor editor = this.getPrefs().edit();

        editor.putString("hotspotboardName", configuredName);
        editor.putString("hotspotboardHostIp", configuredHostIp);
        editor.putBoolean("hostpotboardIsHost", isHost);
        editor.putBoolean("hotspotboardShareMode", isShareMode);
        editor.putInt("hotspotboardConnectionMode", connectionMode);

        editor.commit();

        super.onPause();
    }

    private void newGame() {
        gameApi.newGame();
        overrideGameState = 0;
        ((HotspotBoardApi) gameApi).setPlayingAsWhite(isPlayAsWhite);
        sendGameMessage(GameMessage.TYPE_MOVE, 0, ""); // send initial state

        rebuildBoard();
        updateNewGameButtonVisibility(false);
        updateGameButtonsVisibility(true);
    }

    private void updateStatus(String status) {
        textStatus.setText(status);
        textStatus.setVisibility(View.VISIBLE);
        statusHandler.removeCallbacksAndMessages(null);
        statusHandler.postDelayed(() -> textStatus.setVisibility(View.INVISIBLE), 3000);
    }

    private void updateConnectedState(boolean isConnected) {
        boolean effectiveListening = isListeningSessionRestored();
        boolean effectiveConnected = isConnected || isShareHostSessionRestored() || isObservingSessionRestored();
        boolean showSessionSummary = hasActiveSession || effectiveConnected || effectiveListening;
        layoutConnect.setVisibility(showSessionSummary ? View.GONE : View.VISIBLE);
        layoutSessionSummary.setVisibility(showSessionSummary ? View.VISIBLE : View.GONE);
        updateSessionSummary(showSessionSummary);

        updateNewGameButtonVisibility(effectiveConnected);

        if (!effectiveConnected) {
            textOpponent.setText("Opponent");
            updateGameButtonsVisibility(false);
        }
    }

    private void updateNewGameButtonVisibility(boolean isVisible) {
        layoutNewGameButtons.setVisibility(isHost && !isShareMode && isVisible ? View.VISIBLE : View.GONE);
    }

    private void updateGameButtonsVisibility(boolean isVisible) {
        layoutGameButtons.setVisibility(isVisible ? View.VISIBLE : View.GONE);
    }

    private void refreshConnectionControls() {
        updateConnectedState(false);
        updateObservingState(false);
    }

    private void updateSessionSummary(boolean showSessionSummary) {
        if (!showSessionSummary) {
            textSessionSummary.setText("");
            buttonCopyIp.setVisibility(View.GONE);
            return;
        }

        StringBuilder summary = new StringBuilder();
        appendSummaryPart(summary, isHost ? getString(R.string.hotspot_host) : getString(R.string.hotspot_client));
        if (isHost && isShareMode) {
            appendSummaryPart(summary, getString(R.string.hotspot_share));
        }
        appendSummaryPart(summary, connectionMode == HotspotBoardService.CONNECTION_MODE_LOCAL_WIFI
            ? getString(R.string.hotspot_mode_local_wifi)
            : getString(R.string.hotspot_mode_hotspot));
        appendSummaryPart(summary, getConfiguredPlayerName());

        String localIp = getCurrentLocalIp();
        boolean showCopyIp = isHost
            && connectionMode == HotspotBoardService.CONNECTION_MODE_LOCAL_WIFI
            && localIp != null;
        if (showCopyIp) {
            appendSummaryPart(summary, localIp);
        }

        textSessionSummary.setText(summary.toString());
        buttonCopyIp.setVisibility(showCopyIp ? View.VISIBLE : View.GONE);
    }

    private void appendSummaryPart(StringBuilder summary, String value) {
        if (value == null || value.trim().isEmpty()) {
            return;
        }
        if (summary.length() > 0) {
            summary.append(" \u2022 ");
        }
        summary.append(value.trim());
    }

    private void showConnectionDialog() {
        View dialogView = LayoutInflater.from(this).inflate(R.layout.hotspotboard_connection_dialog, null);
        SwitchMaterial dialogSwitchHost = dialogView.findViewById(R.id.SwitchHost);
        SwitchMaterial dialogSwitchShare = dialogView.findViewById(R.id.SwitchShare);
        MaterialButtonToggleGroup dialogNetworkToggleGroup = dialogView.findViewById(R.id.networkToggleGroup);
        TextView dialogConnectionHelp = dialogView.findViewById(R.id.TextConnectionHelp);
        TextView dialogLocalIp = dialogView.findViewById(R.id.TextLocalIp);
        TextInputLayout inputName = dialogView.findViewById(R.id.InputName);
        TextInputLayout inputHostIp = dialogView.findViewById(R.id.InputHostIp);
        TextInputEditText dialogEditName = dialogView.findViewById(R.id.EditName);
        TextInputEditText dialogEditHostIp = dialogView.findViewById(R.id.EditHostIp);

        dialogSwitchHost.setChecked(isHost);
        dialogSwitchShare.setChecked(isShareMode);
        dialogEditName.setText(configuredName);
        dialogEditHostIp.setText(configuredHostIp);
        dialogNetworkToggleGroup.check(connectionMode == HotspotBoardService.CONNECTION_MODE_LOCAL_WIFI
            ? R.id.buttonModeLocalWifi
            : R.id.buttonModeHotspot);

        final AlertDialog[] dialogHolder = new AlertDialog[1];
        Runnable syncDialogControls = () -> {
            boolean dialogIsHost = dialogSwitchHost.isChecked();
            boolean dialogIsShareMode = dialogIsHost && dialogSwitchShare.isChecked();
            int dialogConnectionMode = dialogNetworkToggleGroup.getCheckedButtonId() == R.id.buttonModeLocalWifi
                ? HotspotBoardService.CONNECTION_MODE_LOCAL_WIFI
                : HotspotBoardService.CONNECTION_MODE_HOTSPOT;

            dialogSwitchShare.setVisibility(dialogIsHost ? View.VISIBLE : View.GONE);

            if (dialogConnectionMode == HotspotBoardService.CONNECTION_MODE_LOCAL_WIFI) {
                dialogConnectionHelp.setText(dialogIsHost
                    ? (dialogIsShareMode ? R.string.hotspot_local_wifi_share_host_help : R.string.hotspot_local_wifi_host_help)
                    : R.string.hotspot_local_wifi_client_help);
            } else {
                dialogConnectionHelp.setText(dialogIsHost
                    ? (dialogIsShareMode ? R.string.hotspot_share_host_help : R.string.hotspot_host_help)
                    : R.string.hotspot_client_help);
            }

            boolean showLocalIp = dialogIsHost && dialogConnectionMode == HotspotBoardService.CONNECTION_MODE_LOCAL_WIFI;
            dialogLocalIp.setVisibility(showLocalIp ? View.VISIBLE : View.GONE);
            if (showLocalIp) {
                String localIp = getCurrentLocalIp();
                dialogLocalIp.setText(localIp == null
                    ? getString(R.string.hotspot_local_ip_unavailable)
                    : getString(R.string.hotspot_local_ip_value, localIp));
            }

            inputHostIp.setVisibility(!dialogIsHost && dialogConnectionMode == HotspotBoardService.CONNECTION_MODE_LOCAL_WIFI
                ? View.VISIBLE
                : View.GONE);

            AlertDialog alertDialog = dialogHolder[0];
            if (alertDialog != null) {
                alertDialog.getButton(AlertDialog.BUTTON_POSITIVE).setText(dialogIsHost
                    ? R.string.hotspot_start_hosting
                    : R.string.hotspot_connect);
            }
        };

        dialogSwitchHost.setOnCheckedChangeListener((buttonView, isChecked) -> syncDialogControls.run());
        dialogSwitchShare.setOnCheckedChangeListener((buttonView, isChecked) -> syncDialogControls.run());
        dialogNetworkToggleGroup.addOnButtonCheckedListener((group, checkedId, isChecked) -> {
            if (isChecked) {
                syncDialogControls.run();
            }
        });

        AlertDialog dialog = new MaterialAlertDialogBuilder(this)
            .setTitle(R.string.hotspot_connection_settings)
            .setView(dialogView)
            .setNegativeButton(R.string.button_cancel, null)
            .setPositiveButton(isHost ? R.string.hotspot_start_hosting : R.string.hotspot_connect, null)
            .show();
        dialogHolder[0] = dialog;
        syncDialogControls.run();
        dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(v -> {
            inputName.setError(null);
            inputHostIp.setError(null);

            String name = getTextValue(dialogEditName);
            boolean dialogIsHost = dialogSwitchHost.isChecked();
            int dialogConnectionMode = dialogNetworkToggleGroup.getCheckedButtonId() == R.id.buttonModeLocalWifi
                ? HotspotBoardService.CONNECTION_MODE_LOCAL_WIFI
                : HotspotBoardService.CONNECTION_MODE_HOTSPOT;
            String hostIp = getTextValue(dialogEditHostIp);

            boolean hasError = false;
            if (name.isEmpty()) {
                inputName.setError(getString(R.string.hotspot_status_name_required));
                hasError = true;
            }
            if (!dialogIsHost
                && dialogConnectionMode == HotspotBoardService.CONNECTION_MODE_LOCAL_WIFI
                && hostIp.isEmpty()) {
                inputHostIp.setError(getString(R.string.hotspot_status_host_ip_required));
                hasError = true;
            }
            if (hasError) {
                return;
            }

            configuredName = name;
            configuredHostIp = hostIp;
            isHost = dialogIsHost;
            isShareMode = dialogIsHost && dialogSwitchShare.isChecked();
            connectionMode = dialogConnectionMode;

            ((HotspotBoardApi) gameApi).setMyName(configuredName);
            textPlayer.setText(configuredName);
            dialog.dismiss();
            refreshConnectionControls();
            startSession();
        });
    }

    private String getTextValue(TextInputEditText editText) {
        return editText.getText() == null ? "" : editText.getText().toString().trim();
    }

    private String getConfiguredPlayerName() {
        String currentName = ((HotspotBoardApi) gameApi).getMyName();
        return currentName == null || currentName.trim().isEmpty() ? configuredName : currentName.trim();
    }

    private String getCurrentLocalIp() {
        return NetworkAddressHelper.getLikelyWifiIpv4Address(this);
    }

    private void copyLocalIp() {
        String localIp = getCurrentLocalIp();
        if (localIp == null) {
            return;
        }
        Clipboard.stringToClipboard(this, localIp, getString(R.string.hotspot_copy_ip_success));
        doToast(getString(R.string.hotspot_copy_ip_success));
    }

    private void updateObservingState(boolean observing) {
        boolean effectiveObserving = observing || isObservingSessionRestored();
        if (effectiveObserving) {
            statusHandler.removeCallbacksAndMessages(null);
            textStatus.setText(R.string.hotspot_status_observing);
            textStatus.setVisibility(View.VISIBLE);
            updateGameButtonsVisibility(false);
            updateNewGameButtonVisibility(false);
        } else if (textStatus.getVisibility() == View.VISIBLE
            && getString(R.string.hotspot_status_observing).contentEquals(textStatus.getText())) {
            textStatus.setVisibility(View.INVISIBLE);
        }
    }

    private void bindHotspotBoardService() {
        if (isServiceBound) {
            return;
        }

        Intent serviceIntent = new Intent(this, HotspotBoardService.class);
        startService(serviceIntent);
        isServiceBound = bindService(serviceIntent, connection, Context.BIND_AUTO_CREATE);
    }

    private void syncRestoredSessionUi() {
        if (isShareHostSessionRestored()) {
            updateConnectedState(true);
            updateObservingState(false);
            return;
        }

        if (isListeningSessionRestored()) {
            updateConnectedState(false);
            updateObservingState(false);
            return;
        }

        if (isObservingSessionRestored()) {
            updateConnectedState(true);
            updateObservingState(true);
            return;
        }

        updateConnectedState(false);
        updateObservingState(false);
        updateGameButtonsVisibility(false);
    }

    private boolean isShareHostSessionRestored() {
        return hasActiveSession && !isListening && isHost && isShareMode;
    }

    private boolean isListeningSessionRestored() {
        return hasActiveSession && isListening && isHost;
    }

    private boolean isObservingSessionRestored() {
        return !isHost && isObserving;
    }

    private void stopSharing() {
        hasActiveSession = false;
        isListening = false;
        isObserving = false;
        try {
            if (messengerFromService != null) {
                messengerFromService.send(Message.obtain(null, HotspotBoardService.MSG_STOP_SESSION));
            }
        } catch (RemoteException e) {
            Log.e(TAG, "stopSharing failed", e);
        }
        updateObservingState(false);
        updateConnectedState(false);
    }

    protected void updateTurnSwitchers() {
        final int currentTurn = jni.getTurn();
        boolean amIWhite = ((HotspotBoardApi) gameApi).isPlayingAsWhite();

        imageTurnWhite.setImageResource(currentTurn == BoardConstants.WHITE
            ? R.drawable.turnwhite
            : R.drawable.turnempty
        );

        imageTurnBlack.setImageResource(currentTurn == BoardConstants.BLACK
            ? R.drawable.turnblack
            : R.drawable.turnempty
        );
    }

    private void showGameResult(String title, String message) {
        if (isFinishing() || isDestroyed()) {
            return;
        }

        new MaterialAlertDialogBuilder(this)
            .setTitle(title)
            .setMessage(message)
            .setPositiveButton(android.R.string.ok, (dialog, which) -> {
                updateGameButtonsVisibility(false);
                updateNewGameButtonVisibility(true);
            })
            .setCancelable(false)
            .setIcon(android.R.drawable.ic_dialog_info)
            .show();
    }

    @Override
    public void rebuildBoard() {
        super.rebuildBoard();

        final int state = gameApi.getState();
        int turn = jni.getTurn();
        boolean amIWhite = ((HotspotBoardApi) gameApi).isPlayingAsWhite();
        chessBoardView.setRotated(!amIWhite);

        imageTurnWhite = !amIWhite ? imageTopTurn : imageBottomTurn;
        imageTurnBlack = !amIWhite ? imageBottomTurn : imageTopTurn;

        updateTurnSwitchers();

        if (((HotspotBoardApi) gameApi).getOpponentName().length() > 0) {
            textPlayer.setText(((HotspotBoardApi) gameApi).getMyName());
            textOpponent.setText(((HotspotBoardApi) gameApi).getOpponentName());
        }

        if (!isObserving && !isShareMode && hasReceivedGameUpdate) {
            if (state == BoardConstants.MATE) {
                // if it's white's turn, white is mated (and loses)
                if ((turn == BoardConstants.WHITE && amIWhite) || (turn == BoardConstants.BLACK && !amIWhite)) {
                    showGameResult("Defeat", "You lost by checkmate.");
                } else {
                    showGameResult("Victory!", "You won by checkmate.");
                }
            } else if (state == BoardConstants.STALEMATE) {
                showGameResult("Game Over", "The game is a draw by stalemate.");
            } else if (state == BoardConstants.DRAW_REPEAT) {
                showGameResult("Game Over", "The game is a draw by 3-fold repetition.");
            } else if (state == BoardConstants.DRAW_50) {
                showGameResult("Game Over", "The game is a draw by the 50-move rule.");
            } else if (state == BoardConstants.DRAW_MATERIAL) {
                showGameResult("Game Over", "The game is a draw by insufficient material.");
            }

            updateGameButtonsVisibility(state == BoardConstants.PLAY || state == BoardConstants.CHECK);
        } else {
            updateGameButtonsVisibility(false);
        }
    }
}
