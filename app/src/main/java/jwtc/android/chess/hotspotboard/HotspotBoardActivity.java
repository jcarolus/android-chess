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
import android.widget.TableLayout;

import com.google.android.material.switchmaterial.SwitchMaterial;

import jwtc.android.chess.R;
import jwtc.android.chess.activities.ChessBoardActivity;

import static android.view.View.INVISIBLE;

public class HotspotBoardActivity extends ChessBoardActivity {
    private final Messenger messengerToService = new Messenger(new IncomingHandler());
    private final String TAG = "HotspotBoardActivity";
    private Messenger messengerFromService;
    private SwitchMaterial switchHost, switchPlayAsWhite;
    private Button buttonConnect;
    private TableLayout layoutConnect, layoutButtons;
    private EditText editName;
    private boolean isHost = true, playAsWhite = true;

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
        this.sendGameUpdate();

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

    public void startSession(boolean isHost) {
        Log.d(TAG, "startSession called " + isHost);
        try {
            if (messengerFromService != null) {
                this.setConnectedState(true);
                Message startMsg = Message.obtain(null, HotspotBoardService.MSG_START_SESSION);
                Log.d(TAG, "startMsg " + startMsg == null ? "null" : "object");
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

                    } catch (Exception ex) {
                        Log.d(TAG, "Could not parse game message: " + ex.toString());
                    }
                }
            } else if (msg.what == HotspotBoardService.MSG_SOCKET_CONNECTED) {
                HotspotBoardActivity.this.setConnectedState(true);
                if (HotspotBoardActivity.this.isHost) {
                    HotspotBoardActivity.this.sendGameUpdate();
                }
            } else if (msg.what == HotspotBoardService.MSG_SOCKET_DISCONNECTED) {
                HotspotBoardActivity.this.setConnectedState(false);
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
        unbindService(connection);
    }

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        Log.d(TAG, "onCreate");
        gameApi = new HotspotBoardApi();
        setContentView(R.layout.hotspotboard);

        afterCreate();

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
                    if (isHost) {
                        ((HotspotBoardApi) gameApi).setPlayingAsWhite(playAsWhite);
                    }
                    chessBoardView.setRotated(!((HotspotBoardApi)gameApi).isPlayingAsWhite());
                    startSession(isHost);
                }
            }
        });

        Button buttonNew = findViewById(R.id.ButtonNew);
        buttonNew.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                gameApi.newGame();

                ((HotspotBoardApi)gameApi).setPlayingAsWhite(playAsWhite);
                chessBoardView.setRotated(!((HotspotBoardApi)gameApi).isPlayingAsWhite());
                HotspotBoardActivity.this.sendGameUpdate();
            }
        });

        layoutConnect = findViewById(R.id.LayoutConnect);
        layoutButtons = findViewById(R.id.LayoutButtons);

        this.setConnectedState(false);

        editName = findViewById(R.id.EditName);

        switchPlayAsWhite = findViewById(R.id.SwitchPlayAsWhite);
        switchPlayAsWhite.setChecked(isHost);
        switchPlayAsWhite.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
            public void onCheckedChanged(CompoundButton buttonView, boolean isChecked) {
                playAsWhite = switchPlayAsWhite.isChecked();
            }
        });

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
        switchPlayAsWhite.setChecked(prefs.getBoolean("hostpotboardIsHost", true));
    }

    @Override
    protected void onPause() {
        SharedPreferences.Editor editor = this.getPrefs().edit();

        String sFEN = gameApi.getFEN();
        editor.putString("hotspotboardName", ((HotspotBoardApi)gameApi).getMyName());
        editor.putBoolean("hostpotboardIsHost", ((HotspotBoardApi)gameApi).isPlayingAsWhite());

        editor.commit();

        super.onPause();
    }

    protected void sendGameUpdate() {
        Log.d(TAG, "sendGameUpdate");
        Message msg = Message.obtain(null, HotspotBoardService.MSG_SEND_GAME_UPDATE);
        Bundle bundle = new Bundle();
        try {
            GameMessage message = new GameMessage(gameApi.getFEN(), gameApi.getWhite(), gameApi.getBlack());
            bundle.putString("data", message.toJsonString());
            msg.setData(bundle);

            messengerFromService.send(msg);
        } catch (Exception e) {
            Log.d(TAG, "Could net send game message");
            e.printStackTrace();
        }
    }

    protected void setConnectedState(boolean isConnected) {
        if (isConnected) {
            layoutConnect.setVisibility(View.GONE);
            layoutButtons.setVisibility(View.VISIBLE);
        } else {
            layoutConnect.setVisibility(View.VISIBLE);
            layoutButtons.setVisibility(View.GONE);
        }
    }
}
