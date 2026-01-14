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

import androidx.appcompat.app.ActionBar;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.app.AppCompatDelegate;
import androidx.recyclerview.widget.GridLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import java.util.List;

import jwtc.android.chess.GamesListActivity;
import jwtc.android.chess.R;
import jwtc.android.chess.helpers.StartItem;
import jwtc.android.chess.helpers.StartItemAdapter;
import jwtc.android.chess.helpers.ActivityHelper;
import jwtc.android.chess.hotspotboard.HotspotBoardActivity;
import jwtc.android.chess.ics.ICSClient;
import jwtc.android.chess.lichess.LichessActivity;
import jwtc.android.chess.play.PlayActivity;
import jwtc.android.chess.practice.PracticeActivity;
import jwtc.android.chess.puzzle.PuzzleActivity;
import jwtc.android.chess.tools.AdvancedActivity;


public class StartBaseActivity  extends AppCompatActivity {
    public static final String TAG = "StartBaseActivity";
    protected RecyclerView list;
    protected StartItemAdapter startItemAdapter;
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

        List<StartItem> startItemList = List.of(
                new StartItem(R.drawable.start_play, R.string.start_play, PlayActivity.class),
                new StartItem(R.drawable.lichess, R.string.start_lichess, LichessActivity.class),
                new StartItem(R.drawable.fics, R.string.start_ics, ICSClient.class),
                new StartItem(R.drawable.percent, R.string.start_practice, PracticeActivity.class),
                new StartItem(R.drawable.puzzle, R.string.start_puzzles, PuzzleActivity.class),
                new StartItem(R.drawable.wifi, R.string.start_hotspotboard, HotspotBoardActivity.class),
                new StartItem(R.drawable.sliders, R.string.start_boardpreferences, BoardPreferencesActivity.class),
                new StartItem(R.drawable.tools, R.string.start_pgn, AdvancedActivity.class),
                new StartItem(R.drawable.database, R.string.start_database, GamesListActivity.class)
        );
        startItemAdapter = new StartItemAdapter(startItemList, (item, pos) -> {
            Intent i = new Intent();
            i.setClass(StartBaseActivity.this, item.cls);
            i.setFlags(Intent.FLAG_ACTIVITY_REORDER_TO_FRONT);
            startActivity(i);
        });

        int spanCount = getResources().getInteger(R.integer.start_span_count);
        list = findViewById(R.id.startItemsRecycler);
        list.setLayoutManager(new GridLayoutManager(this, spanCount));
        list.setAdapter(startItemAdapter);
        list.requestFocus();
    }
}
