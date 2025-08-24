package jwtc.android.chess.activities;

import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.content.res.Configuration;
import android.content.res.Resources;
import android.os.Build;
import android.os.Bundle;
import android.util.DisplayMetrics;
import android.util.Log;
import android.view.Gravity;
import android.view.View;
import android.widget.AdapterView;
import android.widget.ListView;
import android.widget.TextView;
import android.widget.Toast;

import java.util.Locale;

import androidx.appcompat.app.ActionBar;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.app.AppCompatDelegate;
import jwtc.android.chess.R;
import jwtc.android.chess.helpers.ActivityHelper;
import jwtc.android.chess.hotspotboard.HotspotBoardActivity;
import jwtc.android.chess.ics.ICSClient;
import jwtc.android.chess.play.PlayActivity;
import jwtc.android.chess.practice.PracticeActivity;
import jwtc.android.chess.puzzle.PuzzleActivity;
import jwtc.android.chess.tools.AdvancedActivity;


public class StartBaseActivity  extends AppCompatActivity {
    public static final String TAG = "StartBaseActivity";
    protected ListView _list;
    protected int layoutResource = R.layout.start;

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        SharedPreferences prefs = getSharedPreferences("ChessPlayer", Context.MODE_PRIVATE);

        Resources resources = getResources();
        Configuration configuration = resources.getConfiguration();
        DisplayMetrics displayMetrics = resources.getDisplayMetrics();

        if (Build.VERSION.SDK_INT > Build.VERSION_CODES.N){
            getApplicationContext().createConfigurationContext(configuration);
        } else {
            resources.updateConfiguration(configuration, displayMetrics);
        }

        if (prefs.getBoolean("nightMode", false)) {
            AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_YES);
        } else {
            AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM);
        }

        ActionBar actionBar = getSupportActionBar();
        if (actionBar != null) {
            try {
                PackageInfo pInfo = getPackageManager().getPackageInfo(getPackageName(), 0);
                actionBar.setSubtitle(pInfo.versionName);
            } catch (PackageManager.NameNotFoundException e) {}
        }

        setContentView(layoutResource);

        ActivityHelper.fixPaddings(this, findViewById(R.id.root_layout));

        _list = findViewById(R.id.ListStart);
        _list.setOnItemClickListener(new AdapterView.OnItemClickListener() {
            @Override
            public void onItemClick(AdapterView<?> parent, View view, int position, long id) {
                String requestedItem = parent.getItemAtPosition(position).toString();
                try {
                    Intent i = new Intent();
                    Log.i(TAG, requestedItem);
                    if (requestedItem.equals(getString(R.string.start_play))) {
                        i.setClass(StartBaseActivity.this, PlayActivity.class);
                        i.setFlags(Intent.FLAG_ACTIVITY_REORDER_TO_FRONT);
                        startActivity(i);
                    } else if (requestedItem.equals(getString(R.string.start_practice))) {
                        i.setClass(StartBaseActivity.this, PracticeActivity.class);
                        i.setFlags(Intent.FLAG_ACTIVITY_REORDER_TO_FRONT);
                        startActivity(i);
                    } else if (requestedItem.equals(getString(R.string.start_puzzles))) {
                        i.setClass(StartBaseActivity.this, PuzzleActivity.class);
                        i.setFlags(Intent.FLAG_ACTIVITY_REORDER_TO_FRONT);
                        startActivity(i);
                    } else if (requestedItem.equals(getString(R.string.start_hotspotboard))) {
                        i.setClass(StartBaseActivity.this, HotspotBoardActivity.class);
                        i.setFlags(Intent.FLAG_ACTIVITY_REORDER_TO_FRONT);
                        startActivity(i);
                    } else if (requestedItem.equals(getString(R.string.start_ics))) {
                        i.setClass(StartBaseActivity.this, ICSClient.class);
                        startActivity(i);
                    } else if (requestedItem.equals(getString(R.string.start_pgn))) {
                        i.setClass(StartBaseActivity.this, AdvancedActivity.class);
                        i.setFlags(Intent.FLAG_ACTIVITY_REORDER_TO_FRONT);
                        startActivity(i);
                    } else if (requestedItem.equals(getString(R.string.start_boardpreferences))) {
                        i.setClass(StartBaseActivity.this, BoardPreferencesActivity.class);
                        startActivity(i);
                    } else {
                        Log.d(TAG, "Nothing to start");
                    }
                } catch (Exception ex) {
                    Log.d(TAG, "Exception " + (ex != null ? ex.getMessage() : " no ex"));
                    Toast t = Toast.makeText(StartBaseActivity.this, R.string.toast_could_not_start_activity, Toast.LENGTH_LONG);
                    t.setGravity(Gravity.BOTTOM, 0, 0);
                    t.show();
                }
            }
        });

        _list.requestFocus();
    }
}
