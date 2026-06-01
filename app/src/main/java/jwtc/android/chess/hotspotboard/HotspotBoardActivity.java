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
import android.view.View;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import com.google.android.material.button.MaterialButton;
import com.google.android.material.button.MaterialButtonToggleGroup;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.android.material.switchmaterial.SwitchMaterial;

import jwtc.android.chess.R;
import jwtc.android.chess.activities.ChessBoardActivity;
import jwtc.android.chess.services.NetworkAddressHelper;
import jwtc.chess.Move;
import jwtc.chess.board.BoardConstants;

import jwtc.android.chess.helpers.ActivityHelper;

public class HotspotBoardActivity extends ChessBoardActivity {
    private final Messenger messengerToService = new Messenger(new IncomingHandler());
    private final String TAG = "HotspotBoardActivity";
    private Messenger messengerFromService;
    private SwitchMaterial switchHost;
    private MaterialButtonToggleGroup colorToggleGroup;
    private MaterialButtonToggleGroup networkToggleGroup;
    private MaterialButton buttonConnect;
    private LinearLayout layoutConnect;
    private EditText editName, editHostIp;
    private boolean isHost = true, isPlayAsWhite = true;
    private int connectionMode = HotspotBoardService.CONNECTION_MODE_HOTSPOT;
    private MaterialButton buttonResign, buttonDraw, buttonNew;
    private LinearLayout layoutGameButtons, layoutNewGameButtons;
    private TextView textPlayer, textOpponent;
    private TextView textStatus, textConnectionHelp, textLocalIp;
    private View layoutHostIp;
    private ImageView imageBottomTurn, imageTopTurn, imageTurnWhite, imageTurnBlack;
    private Handler statusHandler = new Handler(Looper.getMainLooper());
    private int overrideGameState = 0;

    private ServiceConnection connection = new ServiceConnection() {
        @Override
        public void onServiceConnected(ComponentName name, IBinder service) {
            Log.d(TAG, "onServiceConnected");
            messengerFromService = new Messenger(service);
            // Send our messenger so service can talk to us
            Message msg = Message.obtain(null, HotspotBoardService.MSG_ACTIVITY_CONNECTED);
            msg.replyTo = messengerToService;
            try {
                messengerFromService.send(msg);
            } catch (RemoteException e) {
                e.printStackTrace();
            }
        }

        @Override
        public void onServiceDisconnected(ComponentName name) {
            Log.d(TAG, "onServiceDisconnected");
            messengerFromService = null;
        }
    };

    @Override
    public void OnMove(int move) {
        super.OnMove(move);

        Log.d(TAG, "OnMove");

        Message msg = Message.obtain(null, HotspotBoardService.MSG_SEND_GAME_UPDATE);
        Bundle bundle = new Bundle();
        try {
            GameMessage message = new GameMessage(
                gameApi.getFEN(),
                ((HotspotBoardApi) gameApi).getWhite(),
                ((HotspotBoardApi) gameApi).getBlack(),
                move
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
        layoutConnect.setVisibility(View.GONE);
        updateStatus(isHost ? getString(R.string.hotspot_status_waiting) : getString(R.string.hotspot_status_connecting));
        try {
            if (messengerFromService != null) {
                Message startMsg = Message.obtain(null, HotspotBoardService.MSG_START_SESSION);
                Log.d(TAG, "startMsg " + (startMsg == null ? "null" : "object"));
                startMsg.arg1 = isHost ? 1 : 0; // boolean isHost
                Bundle sessionData = new Bundle();
                sessionData.putInt(HotspotBoardService.KEY_CONNECTION_MODE, connectionMode);
                sessionData.putString(HotspotBoardService.KEY_HOST_IP, editHostIp.getText().toString().trim());
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

    @Override
    public boolean needExitConfirmationDialog() {
        return true;
    }


    private void sendGameMessage(int type, int lastMove) {
        try {
            GameMessage message = new GameMessage(
                type,
                gameApi.getFEN(),
                ((HotspotBoardApi) gameApi).getWhite(),
                ((HotspotBoardApi) gameApi).getBlack(),
                lastMove
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
                        ((HotspotBoardApi) gameApi).onGameUpdate(message);

                        switch (message.type) {
                            case GameMessage.TYPE_MOVE:
                                if (message.lastMove > 0) {
                                    moveToPositions.clear();
                                    highlightedPositions.clear();
                                    highlightedPositions.add(Move.getFrom(message.lastMove));
                                    highlightedPositions.add(Move.getTo(message.lastMove));
                                    updateSelectedSquares();
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
                                    sendGameMessage(GameMessage.TYPE_DRAW_ACCEPT, 0);
                                    showGameResult("Game Over", "The game is a draw.");
                                }, () -> {
                                    sendGameMessage(GameMessage.TYPE_DRAW_DECLINE, 0);
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
            } else if (msg.what == HotspotBoardService.MSG_SOCKET_CONNECTED) {
                updateConnectedState(true);

                updateStatus(getString(isHost
                    ? R.string.hotspot_status_opponent_connected_host
                    : R.string.hotspot_status_opponent_connected_client));
            } else if (msg.what == HotspotBoardService.MSG_SOCKET_DISCONNECTED) {
                updateConnectedState(false);

                updateStatus(getString(R.string.hotspot_status_disconnected));
            }
        }
    }

    @Override
    protected void onStart() {
        super.onStart();
        Log.d(TAG, "onStart, call bindService");
        bindService(new Intent(this, HotspotBoardService.class), connection, Context.BIND_AUTO_CREATE);
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
        unbindService(connection);
    }

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        Log.d(TAG, "onCreate");
        gameApi = new HotspotBoardApi();
        setContentView(R.layout.hotspotboard);

        ActivityHelper.fixPaddings(this, findViewById(R.id.root_layout));

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
        layoutGameButtons = findViewById(R.id.LayoutGameButtons);
        layoutNewGameButtons = findViewById(R.id.LayoutNewGame);
        buttonResign = findViewById(R.id.ButtonResign);
        buttonDraw = findViewById(R.id.ButtonDraw);
        buttonNew = findViewById(R.id.ButtonNew);
        textStatus = findViewById(R.id.TextStatus);
        textConnectionHelp = findViewById(R.id.TextConnectionHelp);
        textLocalIp = findViewById(R.id.TextLocalIp);
        layoutHostIp = findViewById(R.id.LayoutHostIp);
        editName = findViewById(R.id.EditName);
        editHostIp = findViewById(R.id.EditHostIp);

        // default rotation
        imageTurnWhite = imageBottomTurn;
        imageTurnBlack = imageTopTurn;

        buttonNew.setOnClickListener(v -> newGame());

        switchHost = findViewById(R.id.SwitchHost);
        switchHost.setChecked(true);
        switchHost.setOnCheckedChangeListener((buttonView, isChecked) -> {
            isHost = switchHost.isChecked();
            refreshConnectionControls();
        });

        networkToggleGroup = findViewById(R.id.networkToggleGroup);
        networkToggleGroup.check(R.id.buttonModeHotspot);
        networkToggleGroup.addOnButtonCheckedListener((group, checkedId, isChecked) -> {
            if (!isChecked) {
                return;
            }
            connectionMode = checkedId == R.id.buttonModeLocalWifi
                ? HotspotBoardService.CONNECTION_MODE_LOCAL_WIFI
                : HotspotBoardService.CONNECTION_MODE_HOTSPOT;
            refreshConnectionControls();
        });

        buttonConnect = findViewById(R.id.ButtonConnect);
        buttonConnect.setOnClickListener(arg0 -> {
            String name = editName.getText().toString().trim();
            Log.d(TAG, "buttonConnect " + name);
            if (name.isEmpty()) {
                updateStatus(getString(R.string.hotspot_status_name_required));
                return;
            }
            if (!isHost && connectionMode == HotspotBoardService.CONNECTION_MODE_LOCAL_WIFI
                && editHostIp.getText().toString().trim().isEmpty()) {
                updateStatus(getString(R.string.hotspot_status_host_ip_required));
                return;
            }

            ((HotspotBoardApi) gameApi).setMyName(name);
            textPlayer.setText(name);
            startSession();
        });

        buttonResign.setOnClickListener(v -> {
            openConfirmDialog("Are you sure you want to resign?", "Yes", "No", () -> {
                sendGameMessage(GameMessage.TYPE_RESIGN, 0);
                if (((HotspotBoardApi) gameApi).isPlayingAsWhite()) {
                    overrideGameState = BoardConstants.WHITE_RESIGNED;
                } else {
                    overrideGameState = BoardConstants.BLACK_RESIGNED;
                }
                showGameResult("Defeat", "You resigned.");
            }, null);
        });

        buttonDraw.setOnClickListener(v -> {
            sendGameMessage(GameMessage.TYPE_DRAW_OFFER, 0);
            updateStatus(getString(R.string.hotspot_status_draw_sent));
            buttonDraw.setEnabled(false);
        });

        refreshConnectionControls();
    }

    @Override
    public void onResume() {
        super.onResume();
        SharedPreferences prefs = getPrefs();

        Log.d(TAG, "messengerFromService " + (messengerFromService == null));

        String sName = prefs.getString("hotspotboardName", "");
        editName.setText(sName);
        editHostIp.setText(prefs.getString("hotspotboardHostIp", ""));

        switchHost.setChecked(prefs.getBoolean("hostpotboardIsHost", true));
        connectionMode = prefs.getInt("hotspotboardConnectionMode", HotspotBoardService.CONNECTION_MODE_HOTSPOT);
        networkToggleGroup.check(connectionMode == HotspotBoardService.CONNECTION_MODE_LOCAL_WIFI
            ? R.id.buttonModeLocalWifi
            : R.id.buttonModeHotspot);
        refreshConnectionControls();

        updateConnectedState(false);
        updateGameButtonsVisibility(false);
    }

    @Override
    protected void onPause() {
        SharedPreferences.Editor editor = this.getPrefs().edit();

        editor.putString("hotspotboardName", ((HotspotBoardApi) gameApi).getMyName());
        editor.putString("hotspotboardHostIp", editHostIp.getText().toString().trim());
        editor.putBoolean("hostpotboardIsHost", isHost);
        editor.putInt("hotspotboardConnectionMode", connectionMode);

        editor.commit();

        super.onPause();
    }

    private void newGame() {
        gameApi.newGame();
        overrideGameState = 0;
        ((HotspotBoardApi) gameApi).setPlayingAsWhite(isPlayAsWhite);
        sendGameMessage(GameMessage.TYPE_MOVE, 0); // send initial state

        rebuildBoard();
        updateNewGameButtonVisibility(false);
        updateGameButtonsVisibility(true);
    }

    private void updateStatus(String status) {
        textStatus.setText(status);
        textStatus.setVisibility(View.VISIBLE);
        statusHandler.removeCallbacksAndMessages(null);
        statusHandler.postDelayed(() -> textStatus.setVisibility(View.GONE), 3000);
    }

    private void updateConnectedState(boolean isConnected) {
        layoutConnect.setVisibility(isConnected ? View.GONE : View.VISIBLE);

        updateNewGameButtonVisibility(isConnected);

        if (!isConnected) {
            textOpponent.setText("Opponent");
            updateGameButtonsVisibility(false);
        }
    }

    private void updateNewGameButtonVisibility(boolean isVisible) {
        layoutNewGameButtons.setVisibility(isHost && isVisible ? View.VISIBLE : View.GONE);
    }

    private void updateGameButtonsVisibility(boolean isVisible) {
        layoutGameButtons.setVisibility(isVisible ? View.VISIBLE : View.GONE);
    }

    private void refreshConnectionControls() {
        if (textConnectionHelp == null) {
            return;
        }

        if (connectionMode == HotspotBoardService.CONNECTION_MODE_LOCAL_WIFI) {
            textConnectionHelp.setText(isHost
                ? R.string.hotspot_local_wifi_host_help
                : R.string.hotspot_local_wifi_client_help);
        } else {
            textConnectionHelp.setText(isHost
                ? R.string.hotspot_host_help
                : R.string.hotspot_client_help);
        }

        boolean showLocalIp = isHost && connectionMode == HotspotBoardService.CONNECTION_MODE_LOCAL_WIFI;
        textLocalIp.setVisibility(showLocalIp ? View.VISIBLE : View.GONE);
        if (showLocalIp) {
            String localIp = NetworkAddressHelper.getLikelyWifiIpv4Address(this);
            textLocalIp.setText(localIp == null
                ? getString(R.string.hotspot_local_ip_unavailable)
                : getString(R.string.hotspot_local_ip_value, localIp));
        }

        layoutHostIp.setVisibility(!isHost && connectionMode == HotspotBoardService.CONNECTION_MODE_LOCAL_WIFI
            ? View.VISIBLE
            : View.GONE);
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
    }
}
