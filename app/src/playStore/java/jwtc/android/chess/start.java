package jwtc.android.chess;

import jwtc.android.chess.activities.StartBaseActivity;
import jwtc.chess.JNI;

import android.content.ActivityNotFoundException;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;

import androidx.core.view.MenuItemCompat;

import android.util.Log;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.widget.Button;

import java.io.IOException;
import java.util.Timer;
import java.util.TimerTask;

import androidx.mediarouter.app.MediaRouteActionProvider;
import androidx.mediarouter.media.MediaRouteSelector;
import androidx.mediarouter.media.MediaRouter;
import androidx.mediarouter.media.MediaRouter.RouteInfo;

import com.google.android.gms.cast.ApplicationMetadata;
import com.google.android.gms.cast.Cast;
import com.google.android.gms.cast.CastDevice;
import com.google.android.gms.cast.CastMediaControlIntent;
import com.google.android.gms.common.ConnectionResult;
import com.google.android.gms.common.api.GoogleApiClient;

import com.google.android.gms.common.api.ResultCallback;
import com.google.android.gms.common.api.Status;

public class start extends StartBaseActivity {
    public static final String TAG = "start";

    private MediaRouter mMediaRouter;
    private MediaRouteSelector mMediaRouteSelector;
    private MediaRouter.Callback mMediaRouterCallback;
    private CastDevice mSelectedDevice;
    private GoogleApiClient mApiClient;
    private Cast.Listener mCastListener;
    private ConnectionCallbacks mConnectionCallbacks;
    private ConnectionFailedListener mConnectionFailedListener;
    private ChessChannel mChessChannel;
    private boolean mApplicationStarted;
    private boolean mWaitingForReconnect;
    private String mSessionId;

    private JNI _jni;
    private Timer _timer;
    private String _lastMessage;

    /**
     * Called when the activity is first created.
     */
    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        _jni = JNI.getInstance();

        _lastMessage = "";

        _timer = new Timer(true);
        _timer.schedule(new TimerTask() {
            @Override
            public void run() {
                sendMessage(_jni.toFEN());
            }
        }, 1000, 500);


        // Configure Cast device discovery
        mMediaRouter = MediaRouter.getInstance(getApplicationContext());
        mMediaRouteSelector = new MediaRouteSelector.Builder()
                .addControlCategory(CastMediaControlIntent.categoryForCast("05EB93C6")).build();
        mMediaRouterCallback = new MyMediaRouterCallback();

        final String packageName = "jwtc.android.chess";

        Button buttonShare = findViewById(R.id.ButtonShare);
        if (buttonShare != null) {
            buttonShare.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    Intent i = new Intent(Intent.ACTION_SEND);
                    i.setType("text/plain");
                    i.putExtra(Intent.EXTRA_SUBJECT, getString(R.string.app_name));
                    i.putExtra(Intent.EXTRA_TEXT, "https://play.google.com/store/apps/details?id=" + packageName);
                    startActivity(Intent.createChooser(i, "Share"));
                }
            });
        }
    }

    @Override
    protected void onStart() {
        super.onStart();
        // Start media router discovery
        mMediaRouter.addCallback(mMediaRouteSelector, mMediaRouterCallback,
                MediaRouter.CALLBACK_FLAG_REQUEST_DISCOVERY);
    }

    @Override
    protected void onStop() {
        // End media router discovery
        mMediaRouter.removeCallback(mMediaRouterCallback);
        super.onStop();
    }

    @Override
    public void onDestroy() {
        Log.d(TAG, "onDestroy");
        teardown(true);
        super.onDestroy();
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        super.onCreateOptionsMenu(menu);
        getMenuInflater().inflate(R.menu.start_topmenu, menu);
        MenuItem mediaRouteMenuItem = menu.findItem(R.id.media_route_menu_item);
        MediaRouteActionProvider mediaRouteActionProvider
                = (MediaRouteActionProvider) MenuItemCompat
                .getActionProvider(mediaRouteMenuItem);
        // Set the MediaRouteActionProvider selector for device discovery.
        mediaRouteActionProvider.setRouteSelector(mMediaRouteSelector);
        return true;
    }

    /**
     * Send a text message to the receiver
     */
    private void sendMessage(final String message) {
        if (mApiClient != null && mChessChannel != null && message != null) {
            try {
                if (!_lastMessage.equals(message)) {
                    //Log.i(TAG, "Try to send " + message);
                    Cast.CastApi.sendMessage(mApiClient,
                            mChessChannel.getNamespace(), message).setResultCallback(
                            new ResultCallback<Status>() {
                                @Override
                                public void onResult(Status result) {
                                    if (result.isSuccess()) {
                                        _lastMessage = message;
                                    } else {
                                        Log.e(TAG, "Sending message failed");
                                    }
                                }
                            });
                }
            } catch (Exception e) {
                Log.e(TAG, "Exception while sending message", e);
            }
        }
    }

    /**
     * Callback for MediaRouter events
     */
    private class MyMediaRouterCallback extends MediaRouter.Callback {

        @Override
        public void onRouteSelected(MediaRouter router, RouteInfo info) {
            Log.d(TAG, "onRouteSelected");
            // Handle the user route selection.
            mSelectedDevice = CastDevice.getFromBundle(info.getExtras());

            launchReceiver();
        }

        @Override
        public void onRouteUnselected(MediaRouter router, RouteInfo info) {
            Log.d(TAG, "onRouteUnselected: info=" + info);
            teardown(false);
            mSelectedDevice = null;
        }
    }

    /**
     * Google Play services callbacks
     */
    private class ConnectionCallbacks implements
            GoogleApiClient.ConnectionCallbacks {

        @Override
        public void onConnected(Bundle connectionHint) {
            Log.d(TAG, "onConnected");

            if (mApiClient == null) {
                // We got disconnected while this runnable was pending
                // execution.
                return;
            }

            try {
                if (mWaitingForReconnect) {
                    mWaitingForReconnect = false;

                    // Check if the receiver app is still running
                    if ((connectionHint != null)
                            && connectionHint.getBoolean(Cast.EXTRA_APP_NO_LONGER_RUNNING)) {
                        Log.d(TAG, "App  is no longer running");
                        teardown(true);
                    } else {
                        // Re-create the custom message channel
                        try {
                            Cast.CastApi.setMessageReceivedCallbacks(
                                    mApiClient,
                                    mChessChannel.getNamespace(),
                                    mChessChannel);
                        } catch (IOException e) {
                            Log.e(TAG, "Exception while creating channel", e);
                        }
                    }
                } else {
                    // Launch the receiver app
                    Cast.CastApi.launchApplication(mApiClient, "05EB93C6", false)
                            .setResultCallback(
                                    new ResultCallback<Cast.ApplicationConnectionResult>() {
                                        @Override
                                        public void onResult(
                                                Cast.ApplicationConnectionResult result) {
                                            Status status = result.getStatus();
                                            Log.d(TAG,
                                                    "ApplicationConnectionResultCallback.onResult:"
                                                            + status.getStatusCode());
                                            if (status.isSuccess()) {
                                                ApplicationMetadata applicationMetadata = result
                                                        .getApplicationMetadata();
                                                mSessionId = result.getSessionId();
                                                String applicationStatus = result
                                                        .getApplicationStatus();
                                                boolean wasLaunched = result.getWasLaunched();
                                                Log.d(TAG, "application name: "
                                                        + applicationMetadata.getName()
                                                        + ", status: " + applicationStatus
                                                        + ", sessionId: " + mSessionId
                                                        + ", wasLaunched: " + wasLaunched);
                                                mApplicationStarted = true;

                                                // Create the custom message
                                                // channel
                                                mChessChannel = new ChessChannel();
                                                try {
                                                    Cast.CastApi.setMessageReceivedCallbacks(
                                                            mApiClient,
                                                            mChessChannel.getNamespace(),
                                                            mChessChannel);
                                                } catch (IOException e) {
                                                    Log.e(TAG, "Exception while creating channel",
                                                            e);
                                                }

                                            } else {
                                                Log.e(TAG, "application could not launch");
                                                teardown(true);
                                            }
                                        }
                                    });
                }
            } catch (Exception e) {
                Log.e(TAG, "Failed to launch application", e);
            }
        }

        @Override
        public void onConnectionSuspended(int cause) {
            Log.d(TAG, "onConnectionSuspended");
            mWaitingForReconnect = true;
        }
    }

    /**
     * Google Play services callbacks
     */
    private class ConnectionFailedListener implements
            GoogleApiClient.OnConnectionFailedListener {

        @Override
        public void onConnectionFailed(ConnectionResult result) {
            Log.e(TAG, "onConnectionFailed ");

            teardown(false);
        }
    }

    /**
     * Start the receiver app
     */
    private void launchReceiver() {
        try {
            mCastListener = new Cast.Listener() {

                @Override
                public void onApplicationDisconnected(int errorCode) {
                    Log.d(TAG, "application has stopped");
                    teardown(true);
                }

            };
            // Connect to Google Play services
            mConnectionCallbacks = new ConnectionCallbacks();
            mConnectionFailedListener = new ConnectionFailedListener();
            Cast.CastOptions.Builder apiOptionsBuilder = Cast.CastOptions
                    .builder(mSelectedDevice, mCastListener);
            mApiClient = new GoogleApiClient.Builder(this)
                    .addApi(Cast.API, apiOptionsBuilder.build())
                    .addConnectionCallbacks(mConnectionCallbacks)
                    .addOnConnectionFailedListener(mConnectionFailedListener)
                    .build();

            mApiClient.connect();
        } catch (Exception e) {
            Log.e(TAG, "Failed launchReceiver", e);
        }
    }

    /**
     * Custom message channel
     */
    class ChessChannel implements Cast.MessageReceivedCallback {

        /**
         * @return custom namespace
         */
        public String getNamespace() {
            return "urn:x-cast:nl.jwtc.chess.channel";
        }

        /*
         * Receive message from the receiver app
         */
        @Override
        public void onMessageReceived(CastDevice castDevice, String namespace,
                                      String message) {
            //Log.d(TAG, "onMessageReceived: " + message);
        }

    }

    /**
     * Tear down the connection to the receiver
     */
    private void teardown(boolean selectDefaultRoute) {
        Log.d(TAG, "teardown");
        if (mApiClient != null) {
            if (mApplicationStarted) {
                if (mApiClient.isConnected() || mApiClient.isConnecting()) {
                    try {
                        Cast.CastApi.stopApplication(mApiClient, mSessionId);
                        if (mChessChannel != null) {
                            Cast.CastApi.removeMessageReceivedCallbacks(
                                    mApiClient,
                                    mChessChannel.getNamespace());
                            mChessChannel = null;
                        }
                    } catch (IOException e) {
                        Log.e(TAG, "Exception while removing channel", e);
                    }
                    mApiClient.disconnect();
                }
                mApplicationStarted = false;
            }
            mApiClient = null;
        }
        if (selectDefaultRoute) {
            mMediaRouter.selectRoute(mMediaRouter.getDefaultRoute());
        }
        mSelectedDevice = null;
        mWaitingForReconnect = false;
        mSessionId = null;
    }
}
