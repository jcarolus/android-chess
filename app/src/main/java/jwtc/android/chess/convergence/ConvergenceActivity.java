package jwtc.android.chess.convergence;

import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;
import android.os.Handler;
import android.os.Message;
import android.util.Log;
import android.view.KeyEvent;
import android.view.View;
import android.view.Window;
import android.view.View.OnClickListener;
import android.widget.AdapterView;
import android.widget.Button;
import android.widget.ListView;
import android.widget.SimpleAdapter;
import android.widget.TextView;
import android.net.ConnectivityManager;
import android.net.NetworkInfo;
import android.widget.ViewSwitcher;

import java.lang.ref.WeakReference;
import java.lang.Boolean;
import android.net.wifi.WifiManager;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import com.samsung.multiscreen.device.*;
import com.samsung.multiscreen.application.*;
import com.samsung.multiscreen.channel.*;


import jwtc.android.chess.*;

public class ConvergenceActivity extends Activity implements AdapterView.OnItemClickListener {

    //private ListView _lvStart;
    private JSONServer _JSONServer;
    private DIAL _dial;

    public static final String TAG = "convergence.Activity";
    public static final int MSG_SEARCH_DEVICE = 1;
    public static final int MSG_DIAL_DESCRIPTION = 2;
    public static final int MSG_DIAL_APP = 3;
    public static final int MSG_SERVER_STARTED = 4;
    public static final int MSG_SERVER_STOPPED = 5;

    protected TextView _tvIpPort;
    protected Button _butStartStop;
    protected SimpleAdapter _adapterDevices;
    protected ViewSwitcher _viewSwitchWifi;
    protected ListView _listDevices;

    private ArrayList<HashMap<String, String>> _mapDevices = new ArrayList<HashMap<String, String>>();

    /**
     * provide an inner thread handler to any results can be safely used by
     * an instance of the Connection class from an Android Activity
     */
    static class InnerThreadHandler extends Handler {
        WeakReference<ConvergenceActivity> _activity;

        InnerThreadHandler(ConvergenceActivity activity) {
            _activity = new WeakReference<ConvergenceActivity>(activity);
        }

        @Override
        public void handleMessage(Message msg) {
            ConvergenceActivity activity = _activity.get();
            if (activity != null) {
                Bundle bun = msg.getData();
                switch (msg.what) {
                    case MSG_SEARCH_DEVICE:
                        if (bun.getBoolean("status")) {
                            Log.i(TAG, "ThreadHandler found device, trying to dial for description");
                            try {
                                //activity.dialDescription();

                                HashMap<String, String> item = new HashMap<String, String>();
                                item.put("text_name", "TV");
                                item.put("text_ip", bun.getString("buffer"));
                                activity._mapDevices.add(item);

                                activity._adapterDevices.notifyDataSetChanged();
                            } catch (Exception ex) {
                                Log.e(TAG, "Could get dial description: " + ex.toString());
                            }
                        } else {
                            Log.e(TAG, bun.getString("buffer"));
                        }
                        break;
                    case MSG_DIAL_DESCRIPTION:
                        if (bun.getBoolean("status")) {
                            try {
                                // test with youtube
                                activity._dial.dialApp(bun.getString("dialUrl") + "nl.jwtc.chess", "LANCode=0");
                            } catch (Exception ex) {
                                Log.e(TAG, "Could get dial application: " + ex.toString());
                            }
                        } else {
                            Log.e(TAG, bun.getString("buffer"));
                        }
                        break;
                    case MSG_DIAL_APP:
                        if (bun.getBoolean("status")) {

                        } else {
                            Log.e(TAG, bun.getString("buffer"));
                        }
                        break;
                }

                super.handleMessage(msg);
            }
        }
    }

    protected InnerThreadHandler _threadHandler = new InnerThreadHandler(this);

    private SSDP _ssdp = new SSDP((Handler) _threadHandler);


    private Handler _handlerTimer = new Handler();

    private Runnable _runnableTimer = new Runnable() {
        @Override
        public void run() {
            // do what you need to do
            _mapDevices.clear();
            _ssdp.searchDevices();
            // and here comes the "trick"
            _handlerTimer.postDelayed(this, 20000);
        }
    };


    /**
     * Called when the activity is first created.
     */
    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        requestWindowFeature(Window.FEATURE_NO_TITLE);

        setContentView(R.layout.convergence);

        Log.i(TAG, "onCreate");
        _JSONServer = null;

        _tvIpPort = (TextView) findViewById(R.id.TextViewServerIpPort);

        _butStartStop = (Button) findViewById(R.id.ButtonStartStopServer);
        _butStartStop.setOnClickListener(new OnClickListener() {

            @Override
            public void onClick(View arg0) {
                toggleServer();
            }

        });

        _viewSwitchWifi = (ViewSwitcher) findViewById(R.id.ViewSwitcherWifi);

        ((Button) findViewById(R.id.ButtonHelpConvergence)).setOnClickListener(new OnClickListener() {

            @Override
            public void onClick(View v) {
                // TODO Auto-generated method stub
                Intent i = new Intent();
                i.setClass(ConvergenceActivity.this, HtmlActivity.class);
                i.putExtra(HtmlActivity.HELP_MODE, "convergence");
                startActivity(i);
            }

        });

        ((Button) findViewById(R.id.ConvergenceButtonWifi)).setOnClickListener(new OnClickListener() {

            @Override
            public void onClick(View v) {
                startActivity(new Intent(WifiManager.ACTION_PICK_WIFI_NETWORK));
            }

        });

        _adapterDevices = new SimpleAdapter(this, _mapDevices,	R.layout.convergence_device_row,
             new String[] { "text_name", "text_ip"}, new int[] { R.id.text_name, R.id.text_ip }  );


        _listDevices = (ListView)findViewById(R.id.ConvergenceList);
        _listDevices.setAdapter(_adapterDevices);
        _listDevices.setOnItemClickListener(this);

    }


    @Override
    public boolean onKeyDown(int keyCode, KeyEvent event) {
        if (keyCode == KeyEvent.KEYCODE_BACK && event.getRepeatCount() == 0) {
            if (isAlive()) {
                Intent intent = new Intent(this, start.class);
                intent.setFlags(Intent.FLAG_ACTIVITY_REORDER_TO_FRONT);
                startActivity(intent);
                return true;
            }
        }
        return super.onKeyDown(keyCode, event);
    }

    /*
    RFC1918 name	IP address range	number of addresses	largest CIDR block (subnet mask)	host id size	mask bits	classful description[Note 1]
24-bit block	10.0.0.0 - 10.255.255.255	16,777,216	10.0.0.0/8 (255.0.0.0)	24 bits	8 bits	single class A network
20-bit block	172.16.0.0 - 172.31.255.255	1,048,576	172.16.0.0/12 (255.240.0.0)	20 bits	12 bits	16 contiguous class B networks
16-bit block	192.168.0.0 - 192.168.255.255	65,536	192.168.0.0/16 (255.255.0.0)	16 bits	16 bits	256 contiguous class C networks

@TODO create shorter ip code based on above 3 types
     */
    protected String ipPart(String ip) {

        String[] arrTmp = ip.split("\\.");

        if (arrTmp.length > 0) {
            String res = "";
            for (int i = 0; i < arrTmp.length; i++) {
                if (arrTmp[i].length() == 1) {
                    res += "00" + arrTmp[i];
                } else if (arrTmp[i].length() == 2) {
                    res += "0" + arrTmp[i];
                } else {
                    res += arrTmp[i];
                }
            }
            return res;
        }
        return ip;
    }

    protected void toggleServer() {
        try {
            Log.i(TAG, "toggleServer");
            //SharedPreferences prefs = getSharedPreferences("ChessPlayer", MODE_PRIVATE);
            if (isAlive()) {
                _JSONServer.stop();
                _JSONServer = null;
                _tvIpPort.setText(getString(R.string.msg_server_stopped));
            } else {
                //String sPort = prefs.getString("restServerPort", "8092");
                //_JSONServer = new JSONServer(Integer.parseInt(sPort));
                int port = 8092;
                _JSONServer = new JSONServer(port, (Handler) _threadHandler);
                if (_JSONServer.start()) {
                    String sAddr = JSONServer.getIpAddress();
                    String sCode = "";
                    if (sAddr == null) {

                    } else {
                        port -= 8000; // port numbers starting from 8000, so short for 8092 -> 92
                        sCode = ipPart(sAddr) + port;
                    }
                    if (sCode.length() > 0) {
                        _tvIpPort.setText(getString(R.string.msg_your_code) + sCode + "\nIP: " + sAddr);
                    } else {
                        _tvIpPort.setText(getString(R.string.msg_not_valid_ip) + sAddr);
                        _JSONServer.stop();
                        _JSONServer = null;
                    }
                } else {
                    _JSONServer = null;
                    _tvIpPort.setText(getString(R.string.msg_server));
                }
            }

            isAlive();

        } catch (Exception ex) {
        }
    }

    protected boolean isAlive() {
        if (_JSONServer == null) {
            Log.i(TAG, "isAlive -> restServer = null");
            _butStartStop.setText(getString(R.string.menu_start));
            return false;
        }
        if (_JSONServer.isAlive()) {
            Log.i(TAG, "isAlive -> restServer.isAlive = true");
            _butStartStop.setText(getString(R.string.menu_stop));
            return true;
        } else {
            Log.i(TAG, "isAlive -> restServer.isAlive = false");
            _butStartStop.setText(getString(R.string.menu_start));
            return false;
        }
    }

    @Override
    protected void onResume() {

        Log.i(TAG, "onResume");
        isAlive();

        ConnectivityManager connManager = (ConnectivityManager) getSystemService(CONNECTIVITY_SERVICE);
        NetworkInfo mWifi = connManager.getNetworkInfo(ConnectivityManager.TYPE_WIFI);

        if (mWifi.isConnected()) {
            // Do whatever
            _viewSwitchWifi.setDisplayedChild(0);
        } else {
            _viewSwitchWifi.setDisplayedChild(1);
            //
        }

        _handlerTimer.postDelayed(_runnableTimer, 1000);

/*
        Log.i(TAG, "Device.search");
        Device.search(new DeviceAsyncResult<List<Device>>() {
            public void onResult(final List<Device> devices) {
                Log.i(TAG, "Device.onResult " + devices.size());
                if (devices.size() > 0) {
                    final Device device = devices.get(0);
                    onDeviceFound(device);
                }
            }

            public void onError(final DeviceError error) {
                Log.e(TAG, "Device search error " + error);
            }
        });
*/
/*
        String sCode = "566689";
        Log.i(TAG, "Device.search " + sCode);
        Device.getByCode(sCode, new DeviceAsyncResult<Device>() {
            @Override
            public void onResult(final Device device) {
                Log.d(TAG, "findDeviceByPinCode() onResult() device:\n" + device);
                onDeviceFound(device);
            }
            @Override
            public void onError(DeviceError error) {
                Log.d(TAG, "findDeviceByPinCode onError() error: " + error);
            }
        });
*/
        super.onResume();
    }


    private void onDeviceFound(final Device device){
        Log.i(TAG, "get application");
        device.getApplication("nl.jwtc.chess", new DeviceAsyncResult<Application>() {
            @Override
            public void onResult(Application app) {
                Log.i(TAG, "getApplication.onResult");

                String state;
                switch (app.getLastKnownStatus()) {
                    case INSTALLABLE:
                        state = "INSTALLABLE";
                        break;
                    case RUNNING:
                        state = "RUNNING";
                        break;
                    case STOPPED:
                    default:
                        state = "STOPPED";
                }
                Log.i(TAG, "Application state " + state);

                Map<String, String> parameters = new HashMap<String, String>();
                parameters.put("launcher", "android");
                app.launch(parameters, new ApplicationAsyncResult<Boolean>() {
                    @Override
                    public void onResult(final Boolean result) {
                        Log.i(TAG, "app.launch " + result);
                        if(result){
                            Map<String, String> clientAttributes = new HashMap<String, String>();
                            clientAttributes.put("name", "testname");

                            device.connectToChannel("nl.jwtc.chess.channel", clientAttributes, new DeviceAsyncResult<Channel>() {
                                @Override
                                public void onResult(final Channel channel) {
                                    Log.i(TAG, "sendToHost");
                                    channel.sendToHost("Hello TV");
                                }

                                @Override
                                public void onError(final DeviceError error) {
                                    Log.e(TAG, "connectToChannel error " + error);
                                }
                            });
                        }

                    }
                    @Override
                    public void onError(ApplicationError e) {
                        Log.e(TAG, e.toString());
                    }
                });
            }

            @Override
            public void onError(DeviceError e) {
                Log.e(TAG, e.toString());
            }
        });

    }

    @Override
    protected void onPause() {
        Log.i(TAG, "onPause");
        _handlerTimer.removeCallbacks(_runnableTimer);
        super.onPause();
    }

    @Override
    protected void onDestroy() {
        Log.i(TAG, "onDestroy");
        super.onDestroy();
    }

    public void onItemClick(AdapterView<?> arg0, View arg1, int arg2, long arg3) {
        if (arg0 == _listDevices) {
            if (_mapDevices.size() > arg2) {
                HashMap<String, String> m = _mapDevices.get(arg2);
                Log.i("onItemClick", "item " + m.get("text_ip"));
                _dial = new DIAL(m.get("text_ip"), _threadHandler);
                try {
                    _dial.dialDescription();
                } catch (Exception ex){

                }
            }
        }
    }
}
