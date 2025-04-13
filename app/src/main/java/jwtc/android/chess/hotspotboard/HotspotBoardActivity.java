package jwtc.android.chess.hotspotboard;

import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.ServiceConnection;
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

public class HotspotBoardActivity extends ChessBoardActivity {
    private final Messenger messengerToService = new Messenger(new IncomingHandler());
    private final String TAG = "HotspotBoardActivity";
    private Messenger messengerFromService;
    private SwitchMaterial switchHost;
    private Button buttonConnect;
    private TableLayout layoutConnect;
    private EditText editName;
    private boolean isHost = true;

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
    public void OnMove(int move) {
        super.OnMove(move);

        Log.d(TAG, "OnMove");
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

    public void startSession(boolean isHost) {
        try {
            if (messengerFromService != null) {
                Message startMsg = Message.obtain(null, HotspotBoardService.MSG_START_SESSION);
                Log.d(TAG, "startMsg " + startMsg == null ? "null" : "object");
                startMsg.arg1 = isHost ? 1 : 0; // boolean isHost
                messengerFromService.send(startMsg);

                gameApi.newGame(); // @TODO
            } else {
                Log.d(TAG, "messengerFromService is null");
            }
        } catch (RemoteException e) {
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

                    } catch (Exception ex) {
                        Log.d(TAG, "Could not parse game message: " + ex.toString());
                    }
                }
            } else if (msg.what == HotspotBoardService.MSG_SOCKET_CONNECTED) {
                layoutConnect.setVisibility(View.GONE);

            } else if (msg.what == HotspotBoardService.MSG_SOCKET_DISCONNECTED) {
                layoutConnect.setVisibility(View.VISIBLE);
            }
        }
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
                if (name.length() > 0) {
                    ((HotspotBoardApi)gameApi).setMyName(name);
                    startSession(isHost);
                }
            }
        });

        layoutConnect = findViewById(R.id.LayoutConnect);

        editName = findViewById(R.id.EditName);
    }
}
