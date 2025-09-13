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
import android.widget.Button;
import android.widget.CompoundButton;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.ViewSwitcher;

import androidx.appcompat.app.AlertDialog;

import com.google.android.material.button.MaterialButtonToggleGroup;
import com.google.android.material.card.MaterialCardView;
import com.google.android.material.switchmaterial.SwitchMaterial;

import jwtc.android.chess.R;
import jwtc.android.chess.activities.ChessBoardActivity;
import jwtc.android.chess.constants.ColorSchemes;
import jwtc.chess.Move;
import jwtc.chess.board.BoardConstants;

import jwtc.android.chess.helpers.ActivityHelper;

public class HotspotBoardActivity extends ChessBoardActivity {
    private final Messenger messengerToService = new Messenger(new IncomingHandler());
    private final String TAG = "HotspotBoardActivity";
    private Messenger messengerFromService;
    private SwitchMaterial switchHost;
    private MaterialButtonToggleGroup colorToggleGroup;
    private Button buttonConnect;
    private LinearLayout layoutConnect;
    private EditText editName;
    private boolean isHost = true, isPlayAsWhite = true;
    private Button buttonResign, buttonDraw, buttonNew;
    private LinearLayout layoutGameButtons, layoutNewGameButtons;
    private TextView textPlayer, textOpponent;
    private TextView textStatus;
    private ViewSwitcher switchTurnMe, switchTurnOpp;
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
                    ((HotspotBoardApi)gameApi).getWhite(),
                    ((HotspotBoardApi)gameApi).getBlack(),
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
        if (((HotspotBoardApi)gameApi).isMyTurn()) {
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
        updateStatus(isHost ? "Waiting for opponent to connect" : "Trying to connect");
        try {
            if (messengerFromService != null) {
                Message startMsg = Message.obtain(null, HotspotBoardService.MSG_START_SESSION);
                Log.d(TAG, "startMsg " + (startMsg == null ? "null" : "object"));
                startMsg.arg1 = isHost ? 1 : 0; // boolean isHost
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
                    ((HotspotBoardApi)gameApi).getWhite(),
                    ((HotspotBoardApi)gameApi).getBlack(),
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
                        ((HotspotBoardApi)gameApi).onGameUpdate(message);

                        switch(message.type) {
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
                                new AlertDialog.Builder(HotspotBoardActivity.this)
                                        .setTitle("Draw Offer")
                                        .setMessage(((HotspotBoardApi) gameApi).getOpponentName() + " offers a draw. Do you accept?")
                                        .setPositiveButton("Accept", (dialog, which) -> {
                                            sendGameMessage(GameMessage.TYPE_DRAW_ACCEPT, 0);
                                            showGameResult("Game Over", "The game is a draw.");
                                        })
                                        .setNegativeButton("Decline", (dialog, which) -> sendGameMessage(GameMessage.TYPE_DRAW_DECLINE, 0))
                                        .show();
                                break;
                            case GameMessage.TYPE_DRAW_ACCEPT:
                                overrideGameState = BoardConstants.DRAW_AGREEMENT;
                                showGameResult("Game Over", "The game is a draw.");
                                break;
                            case GameMessage.TYPE_DRAW_DECLINE:
                                updateStatus("Draw offer declined.");
                                break;
                        }

                    } catch (Exception ex) {
                        Log.d(TAG, "Could not parse game message: " + ex.toString());
                    }
                }
            } else if (msg.what == HotspotBoardService.MSG_SOCKET_CONNECTED) {
                updateConnectedState(true);

                updateStatus("Your opponent is connected. " + (isHost ? " Start a new game" : "Wait for your opponent to start a new game"));
            } else if (msg.what == HotspotBoardService.MSG_SOCKET_DISCONNECTED) {
                updateConnectedState(false);

                updateStatus("Your opponent is no longer connected");
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
        if (messengerFromService != null && ((HotspotBoardApi)gameApi).getOpponentName().length() > 0) {
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

        colorToggleGroup = findViewById(R.id.colorToggleGroup);

        // Set default selection to White
        colorToggleGroup.check(R.id.buttonWhite);
        colorToggleGroup.addOnButtonCheckedListener(new MaterialButtonToggleGroup.OnButtonCheckedListener() {
            @Override
            public void onButtonChecked(MaterialButtonToggleGroup group, int checkedId, boolean isChecked) {
                if (isChecked) {
                    isPlayAsWhite = checkedId == R.id.buttonWhite;
                }
            }
        });

        switchTurnMe = findViewById(R.id.ImageTurnMe);
        switchTurnOpp = findViewById(R.id.ImageTurnOpp);
        textPlayer = findViewById(R.id.TextPlayer);
        textOpponent = findViewById(R.id.TextOpponent);
        layoutConnect = findViewById(R.id.LayoutConnect);
        layoutGameButtons = findViewById(R.id.LayoutGameButtons);
        layoutNewGameButtons = findViewById(R.id.LayoutNewGame);
        buttonResign = findViewById(R.id.ButtonResign);
        buttonDraw = findViewById(R.id.ButtonDraw);
        buttonNew = findViewById(R.id.ButtonNew);
        textStatus = findViewById(R.id.TextStatus);

        buttonNew.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                newGame();
            }
        });

        switchHost = findViewById(R.id.SwitchHost);
        switchHost.setChecked(true);
        switchHost.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
            public void onCheckedChanged(CompoundButton buttonView, boolean isChecked) {
                isHost = switchHost.isChecked();
            }
        });

        buttonConnect = findViewById(R.id.ButtonConnect);
        buttonConnect.setOnClickListener(new View.OnClickListener() {
            public void onClick(View arg0) {
                String name = editName.getText().toString();
                Log.d(TAG, "buttonConnect " + name);
                if (!name.isEmpty()) {
                    ((HotspotBoardApi)gameApi).setMyName(name);
                    textPlayer.setText(name);
                    startSession();
                }
            }
        });

        buttonResign.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                new AlertDialog.Builder(HotspotBoardActivity.this)
                        .setTitle("Resign")
                        .setMessage("Are you sure you want to resign?")
                        .setPositiveButton("Yes", (dialog, which) -> {
                            sendGameMessage(GameMessage.TYPE_RESIGN, 0);
                            if (((HotspotBoardApi) gameApi).isPlayingAsWhite()) {
                                overrideGameState = BoardConstants.WHITE_RESIGNED;
                            } else {
                                overrideGameState = BoardConstants.BLACK_RESIGNED;
                            }
                            showGameResult("Defeat", "You resigned.");
                        })
                        .setNegativeButton("No", null)
                        .show();
            }
        });

        buttonDraw.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                sendGameMessage(GameMessage.TYPE_DRAW_OFFER, 0);
                updateStatus("Draw offer sent.");
                buttonDraw.setEnabled(false);
            }
        });

        editName = findViewById(R.id.EditName);
    }

    @Override
    public void onResume() {
        super.onResume();
        SharedPreferences prefs = getPrefs();

        Log.d(TAG, "messengerFromService " + (messengerFromService == null));

        String sName = prefs.getString("hotspotboardName", "");
        editName.setText(sName);

        switchHost.setChecked(prefs.getBoolean("hostpotboardIsHost", true));

        updateConnectedState(false);
        updateGameButtonsVisibility(false);
    }

    @Override
    protected void onPause() {
        SharedPreferences.Editor editor = this.getPrefs().edit();

        editor.putString("hotspotboardName", ((HotspotBoardApi)gameApi).getMyName());
        editor.putBoolean("hostpotboardIsHost", isHost);

        editor.commit();

        super.onPause();
    }

    private void newGame() {
        gameApi.newGame();
        overrideGameState = 0;
        ((HotspotBoardApi)gameApi).setPlayingAsWhite(isPlayAsWhite);
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

    protected void updateTurnSwitchers() {
        final int currentTurn = jni.getTurn();
        boolean amIWhite = ((HotspotBoardApi)gameApi).isPlayingAsWhite();

        switchTurnOpp.setVisibility(currentTurn == BoardConstants.BLACK && amIWhite || currentTurn == BoardConstants.WHITE && !amIWhite ?  View.VISIBLE : View.INVISIBLE);
        switchTurnOpp.setDisplayedChild(currentTurn == BoardConstants.BLACK ? 0 : 1);

        switchTurnMe.setVisibility(currentTurn == BoardConstants.WHITE && amIWhite || currentTurn == BoardConstants.BLACK && !amIWhite ?  View.VISIBLE : View.INVISIBLE);
        switchTurnMe.setDisplayedChild(currentTurn == BoardConstants.BLACK ? 0 : 1);
    }

    private void showGameResult(String title, String message) {
        if (isFinishing() || isDestroyed()) {
            return;
        }

        new AlertDialog.Builder(this)
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

        final int state = jni.getState();
        int turn = jni.getTurn();
        boolean amIWhite = ((HotspotBoardApi)gameApi).isPlayingAsWhite();
        chessBoardView.setRotated(!amIWhite);

        updateTurnSwitchers();

        if (((HotspotBoardApi) gameApi).getOpponentName().length() > 0) {
            textPlayer.setText(((HotspotBoardApi)gameApi).getMyName());
            textOpponent.setText(((HotspotBoardApi)gameApi).getOpponentName());
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
