package jwtc.android.chess;


import android.app.AlertDialog;
import android.app.PendingIntent;
import android.content.ComponentName;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.ServiceConnection;
import android.os.Bundle;
import android.os.IBinder;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.Spinner;

import com.android.vending.billing.IInAppBillingService;

import org.json.JSONObject;

import java.math.BigInteger;
import java.security.SecureRandom;

public class Donate extends MyBaseActivity {

    public static final String TAG = "Donate";
    private String _payLoad;

    IInAppBillingService mService;

    ServiceConnection mServiceConn = new ServiceConnection() {
        @Override
        public void onServiceDisconnected(ComponentName name) {
            mService = null;
        }

        @Override
        public void onServiceConnected(ComponentName name,
                                       IBinder service) {
            mService = IInAppBillingService.Stub.asInterface(service);
        }
    };

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        setContentView(R.layout.donate);

        this.makeActionOverflowMenuShown();

        SecureRandom sr = new SecureRandom();
        _payLoad = new BigInteger(130, sr).toString(32);
        Log.i(TAG, "Payload " + _payLoad);

        Intent serviceIntent = new Intent("com.android.vending.billing.InAppBillingService.BIND");
        serviceIntent.setPackage("com.android.vending");
        bindService(serviceIntent, mServiceConn, Context.BIND_AUTO_CREATE);

        final Spinner spinAmount = (Spinner)findViewById(R.id.SpinnerDonate);

        Button butDonate = (Button)findViewById(R.id.ButtonDonate);
        butDonate.setOnClickListener(new View.OnClickListener() {
            public void onClick(View arg0) {
                int index = spinAmount.getSelectedItemPosition();
                String[] arrSKU = new String[]{"donate1euro", "donate2euro", "donate5euro", "donate10euro", "donate20euro"};
                try {
                    Bundle buyIntentBundle = mService.getBuyIntent(3, getPackageName(), arrSKU[index], "inapp", _payLoad);
                    PendingIntent pendingIntent = buyIntentBundle.getParcelable("BUY_INTENT");

                    startIntentSenderForResult(pendingIntent.getIntentSender(),
                            1001, new Intent(), Integer.valueOf(0), Integer.valueOf(0),
                            Integer.valueOf(0));

                } catch (Exception e) {
                    e.printStackTrace();
                }
            }
        });
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        if (requestCode == 1001) {

            AlertDialog.Builder builder = new AlertDialog.Builder(this);
            builder.setPositiveButton(R.string.alert_ok, new DialogInterface.OnClickListener() {
                public void onClick(DialogInterface dialog, int item) {
                    dialog.dismiss();
                }});

            int responseCode = data.getIntExtra("RESPONSE_CODE", 0);
            String purchaseData = data.getStringExtra("INAPP_PURCHASE_DATA");
            String dataSignature = data.getStringExtra("INAPP_DATA_SIGNATURE");

            Log.i(TAG, "response code " + responseCode);

            if (resultCode == RESULT_OK) {
                builder.setTitle(getString(R.string.donate_success));

                try {
                    JSONObject jo = new JSONObject(purchaseData);
                    String token = jo.getString("purchaseToken");
                    String payLoad = jo.getString("developerPayload");

                    if(_payLoad.equals(payLoad)) {

                        int response = mService.consumePurchase(3, getPackageName(), token);
                        Log.i(TAG, "consume response " + response);
                    } else {
                        Log.w(TAG, "payLoads do not match " + _payLoad + " != " + payLoad);
                    }
                }
                catch (Exception e) {
                    //alert("Failed to parse purchase data.");
                    e.printStackTrace();
                }
            } else {
                builder.setTitle(getString(R.string.donate_error));
            }
            AlertDialog alert = builder.create();
            alert.show();
        }
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        if (mService != null) {
            unbindService(mServiceConn);
        }
    }


}
