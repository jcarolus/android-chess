package jwtc.android.chess;

import android.app.AlertDialog;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.SharedPreferences;
import android.media.RingtoneManager;
import android.net.Uri;
import android.os.Bundle;
import android.preference.Preference;
import android.preference.PreferenceManager;
import android.preference.Preference.OnPreferenceClickListener;
import android.util.Log;

import jwtc.android.chess.activities.BasePreferenceActivity;


public class ChessPreferences extends BasePreferenceActivity implements SharedPreferences.OnSharedPreferenceChangeListener {

    private static int REQUEST_SOUND = 1;
    private static String TAG = "ChessPreferences";
    private Uri _uriNotification;
    private int _colorScheme;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        PreferenceManager pm = getPreferenceManager();
        pm.setSharedPreferencesName("ChessPlayer");

        final SharedPreferences prefs = pm.getSharedPreferences();
        final SharedPreferences.Editor editor = prefs.edit();
        _colorScheme = prefs.getInt("ColorScheme", 0);
        _uriNotification = Uri.parse(prefs.getString("NotificationUri", ""));

        addPreferencesFromResource(R.xml.globalprefs);

        prefs.registerOnSharedPreferenceChangeListener(this);

        Preference prefSound = (Preference) findPreference("soundHandle");
        prefSound.setOnPreferenceClickListener(new OnPreferenceClickListener() {
            public boolean onPreferenceClick(Preference preference) {
                Intent intent = new Intent(RingtoneManager.ACTION_RINGTONE_PICKER);
                intent.putExtra(RingtoneManager.EXTRA_RINGTONE_TYPE, RingtoneManager.TYPE_NOTIFICATION);
                intent.putExtra(RingtoneManager.EXTRA_RINGTONE_TITLE, "Notification tone");
                if (_uriNotification == null) {
                    intent.putExtra(RingtoneManager.EXTRA_RINGTONE_EXISTING_URI, (Uri) null);
                } else {
                    intent.putExtra(RingtoneManager.EXTRA_RINGTONE_EXISTING_URI, _uriNotification);
                }
                startActivityForResult(intent, REQUEST_SOUND);
                return true;
            }
        });

        setResult(0);
    }

    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        if (requestCode == REQUEST_SOUND) {
            if (resultCode == RESULT_OK) {
                _uriNotification = data.getParcelableExtra(RingtoneManager.EXTRA_RINGTONE_PICKED_URI);

                SharedPreferences.Editor editor = getSharedPreferences("ChessPlayer", MODE_PRIVATE).edit();
                if (_uriNotification == null) {
                    editor.putString("NotificationUri", null);
                } else {
                    editor.putString("NotificationUri", _uriNotification.toString());
                }
                editor.commit();
                Log.i(TAG, "Sound " + _uriNotification);
            }
        }
    }

    @Override
    public void onSharedPreferenceChanged(SharedPreferences sharedPreferences, String s) {

        if (s.equals("localelanguage")) {
            Log.i(TAG, s);
            setResult(1);
        }
    }
}

