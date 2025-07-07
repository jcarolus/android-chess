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
import android.widget.TableLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AlertDialog;
import androidx.core.content.ContextCompat;

import com.google.android.material.switchmaterial.SwitchMaterial;

import jwtc.android.chess.R;
import jwtc.android.chess.activities.ChessBoardActivity;
import jwtc.chess.board.BoardConstants;

import jwtc.android.chess.helpers.ActivityHelper;

public class HotspotBoardActivity extends ChessBoardActivity {
    private final Messenger messengerToService = new Messenger(new IncomingHandler());
    private final String TAG = "HotspotBoardActivity";
    private Messenger messengerFromService;
    private SwitchMaterial switchHost;
    private Button buttonConnect;
    private Button white;
    private Button black;
    private TableLayout layoutConnect, layoutButtons;
    private EditText editName;
    private boolean isHost = true;
    private Button buttonResign, buttonDraw, buttonNew;
    private LinearLayout layoutGameButtons;
    private TextView textPlayer, textOpponent;
    private TextView textStatus;
    private Handler statusHandler = new Handler(Looper.getMainLooper());
    private boolean isGameOver = false;

    private String startFEN = null;

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
                if (((HotspotBoardApi)gameApi).getOpponentName().length() > 0) {
                    layoutConnect.setVisibility(View.GONE);
                    layoutGameButtons.setVisibility(View.VISIBLE);
                }
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
            GameMessage message = new GameMessage(gameApi.getFEN(), ((HotspotBoardApi)gameApi).getMyName());
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

    private void updateColorButtons(boolean whiteSelected) {
        if (whiteSelected) {
            white.setBackgroundColor(ContextCompat.getColor(this, R.color.button_selection_blue));
            white.setTextColor(ContextCompat.getColor(this, android.R.color.white));
            black.setBackgroundColor(ContextCompat.getColor(this, android.R.color.transparent));
            black.setTextColor(ContextCompat.getColor(this, R.color.surfaceTextColor));
        } else {
            black.setBackgroundColor(ContextCompat.getColor(this, R.color.button_selection_blue));
            black.setTextColor(ContextCompat.getColor(this, android.R.color.white));
            white.setBackgroundColor(ContextCompat.getColor(this, android.R.color.transparent));
            white.setTextColor(ContextCompat.getColor(this, R.color.surfaceTextColor));
        }
    }

    private void sendGameMessage(int type) {
        try {
            GameMessage message = new GameMessage(type, gameApi.getFEN(), ((HotspotBoardApi)gameApi).getMyName());
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
                        chessBoardView.setRotated(!((HotspotBoardApi)gameApi).isPlayingAsWhite());

                        if (((HotspotBoardApi) gameApi).getOpponentName().length() > 0) {
                            textPlayer.setText(((HotspotBoardApi)gameApi).getMyName());
                            textOpponent.setText(((HotspotBoardApi)gameApi).getOpponentName());
                        }

                        switch(message.type) {
                            case GameMessage.TYPE_NEW_GAME:
                                gameApi.initFEN(message.FEN, true);
                                rebuildBoard();
                                buttonDraw.setEnabled(true);
                                break;
                            case GameMessage.TYPE_RESIGN:
                                showGameResult("Victory!", ((HotspotBoardApi) gameApi).getOpponentName() + " has resigned.");
                                break;
                            case GameMessage.TYPE_DRAW_OFFER:
                                new AlertDialog.Builder(HotspotBoardActivity.this)
                                        .setTitle("Draw Offer")
                                        .setMessage(((HotspotBoardApi) gameApi).getOpponentName() + " offers a draw. Do you accept?")
                                        .setPositiveButton("Accept", (dialog, which) -> {
                                            sendGameMessage(GameMessage.TYPE_DRAW_ACCEPT);
                                            showGameResult("Game Over", "The game is a draw.");
                                        })
                                        .setNegativeButton("Decline", (dialog, which) -> sendGameMessage(GameMessage.TYPE_DRAW_DECLINE))
                                        .show();
                                break;
                            case GameMessage.TYPE_DRAW_ACCEPT:
                                showGameResult("Game Over", "The game is a draw.");
                                break;
                            case GameMessage.TYPE_DRAW_DECLINE:
                                updateStatus("Draw offer declined.");
                                buttonDraw.setEnabled(true);
                                break;
                        }

                    } catch (Exception ex) {
                        Log.d(TAG, "Could not parse game message: " + ex.toString());
                    }
                }
            } else if (msg.what == HotspotBoardService.MSG_SOCKET_CONNECTED) {
                layoutConnect.setVisibility(View.GONE);
                buttonNew.setVisibility(View.GONE);
                layoutGameButtons.setVisibility(View.VISIBLE);
                if (isHost) {
                    white.setClickable(false);
                    black.setClickable(false);
                    sendGameMessage(GameMessage.TYPE_MOVE); // send initial state
                } else {
                    sendGameMessage(GameMessage.TYPE_MOVE);
                }

            } else if (msg.what == HotspotBoardService.MSG_SOCKET_DISCONNECTED) {
                if (isGameOver) {
                    return;
                }
                layoutConnect.setVisibility(View.VISIBLE);
                buttonNew.setVisibility(View.VISIBLE);
                layoutGameButtons.setVisibility(View.GONE);
                if (isHost) {
                    white.setClickable(true);
                    black.setClickable(true);
                }
                // a player disconnected during the game
                if (((HotspotBoardApi)gameApi).getOpponentName().length() > 0) {
                    showGameResult("Victory!", "Your opponent disconnected.");
                }

            } else if (msg.what == HotspotBoardService.MSG_SET_PLAYER_COLOR) {
                boolean amWhite = msg.arg1 == 1;
                Log.d(TAG, "Received color from service. I am playing as " + (amWhite ? "White" : "Black"));
                ((HotspotBoardApi) gameApi).setPlayingAsWhite(amWhite);
                chessBoardView.setRotated(!amWhite);
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

        white = findViewById(R.id.PlayAsWhite);
        black = findViewById(R.id.PlayAsBlack);
        textPlayer = findViewById(R.id.TextPlayer);
        textOpponent = findViewById(R.id.TextOpponent);
        layoutGameButtons = findViewById(R.id.LayoutGameButtons);
        buttonResign = findViewById(R.id.ButtonResign);
        buttonDraw = findViewById(R.id.ButtonDraw);
        buttonNew = findViewById(R.id.ButtonNew);
        textStatus = findViewById(R.id.TextStatus);

        buttonNew.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                gameApi.newGame();
                rebuildBoard();
            }
        });

        switchHost = findViewById(R.id.SwitchHost);
        switchHost.setChecked(true);
        switchHost.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
            public void onCheckedChanged(CompoundButton buttonView, boolean isChecked) {
                isHost = switchHost.isChecked();
                if (!isHost) {
                    white.setVisibility(View.INVISIBLE);
                    black.setVisibility(View.INVISIBLE);
                }
                if (isHost) {
                    white.setVisibility(View.VISIBLE);
                    black.setVisibility(View.VISIBLE);
                }
            }
        });

        white.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                try {
                    Message msg = Message.obtain(null, HotspotBoardService.MSG_SET_HOST_COLOR);
                    msg.arg1 = 1;
                    messengerFromService.send(msg);
                    updateColorButtons(true);
                } catch (RemoteException e) {
                    e.printStackTrace();
                }
            }
        });

        black.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                try {
                    Message msg = Message.obtain(null, HotspotBoardService.MSG_SET_HOST_COLOR);
                    msg.arg1 = 0;
                    messengerFromService.send(msg);
                    updateColorButtons(false);
                } catch (RemoteException e) {
                    e.printStackTrace();
                }
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
                            sendGameMessage(GameMessage.TYPE_RESIGN);
                            showGameResult("Defeat", "You resigned.");
                        })
                        .setNegativeButton("No", null)
                        .show();
            }
        });

        buttonDraw.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                sendGameMessage(GameMessage.TYPE_DRAW_OFFER);
                updateStatus("Draw offer sent.");
                buttonDraw.setEnabled(false);
            }
        });

        layoutConnect = findViewById(R.id.LayoutConnect);
        layoutButtons = findViewById(R.id.LayoutButtons);

        editName = findViewById(R.id.EditName);
    }

    @Override
    public void onResume() {
        super.onResume();
        SharedPreferences prefs = getPrefs();

        Log.d(TAG, "messengerFromService " + (messengerFromService == null));

        gameApi.newGame();

        String sName = prefs.getString("hotspotboardName", "");
        editName.setText(sName);

        switchHost.setChecked(prefs.getBoolean("hostpotboardIsHost", true));
        updateColorButtons(true);

        Log.d(TAG, "onResume " + sName + " :: " + startFEN);
    }

    @Override
    protected void onPause() {
        SharedPreferences.Editor editor = this.getPrefs().edit();

        editor.putString("hotspotboardName", ((HotspotBoardApi)gameApi).getMyName());
        editor.putBoolean("hostpotboardIsHost", isHost);

        editor.commit();

        super.onPause();
    }

    private void updateStatus(String status) {
        textStatus.setText(status);
        textStatus.setVisibility(View.VISIBLE);
        statusHandler.removeCallbacksAndMessages(null);
        statusHandler.postDelayed(() -> textStatus.setVisibility(View.GONE), 3000);
    }

    private void showGameResult(String title, String message) {
        if (isFinishing() || isDestroyed()) {
            return;
        }
        isGameOver = true;
        new AlertDialog.Builder(this)
                .setTitle(title)
                .setMessage(message)
                .setPositiveButton(android.R.string.ok, (dialog, which) -> {
                    if (messengerFromService != null) {
                        try {
                            Message disconnectMsg = Message.obtain(null, HotspotBoardService.MSG_DISCONNECT_SOCKET);
                            messengerFromService.send(disconnectMsg);
                        } catch (RemoteException e) {
                            e.printStackTrace();
                        }
                    }

                    isGameOver = false; // ready for a new game
                    if (gameApi instanceof HotspotBoardApi) {
                        ((HotspotBoardApi) gameApi).setOpponentName(""); // clear opponent
                    }
                    textOpponent.setText("Opponent");

                    layoutConnect.setVisibility(View.VISIBLE);
                    buttonNew.setVisibility(View.VISIBLE);
                    layoutGameButtons.setVisibility(View.GONE);
                    if (isHost) {
                        white.setClickable(true);
                        black.setClickable(true);
                    }
                    gameApi.newGame();
                    rebuildBoard();
                    buttonDraw.setEnabled(true);
                })
                .setCancelable(false)
                .setIcon(android.R.drawable.ic_dialog_info)
                .show();
    }

    @Override
    public void rebuildBoard() {
        super.rebuildBoard();

        final int state = jni.getState();
        if (state == BoardConstants.MATE) {
            boolean amIWhite = ((HotspotBoardApi)gameApi).isPlayingAsWhite();
            int turn = jni.getTurn();

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
    }
}
