package jwtc.android.chess.activities;

import android.content.Context;
import android.content.SharedPreferences;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.content.res.Configuration;
import android.content.res.Resources;
import android.os.Build;
import android.os.Bundle;
import android.util.DisplayMetrics;
import android.view.View;
import android.widget.ImageView;
import android.widget.ListView;
import android.widget.TextView;

import androidx.appcompat.app.ActionBar;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.app.AppCompatDelegate;
import androidx.fragment.app.Fragment;

import com.google.android.material.bottomnavigation.BottomNavigationView;

import jwtc.android.chess.R;
import jwtc.android.chess.academy.AcademyFragment;
import jwtc.android.chess.play.PlayFragment;
import jwtc.android.chess.practice.PracticeFragment;
import jwtc.android.chess.settings.SettingsFragment;


public class StartBaseActivity  extends AppCompatActivity {
    public static final String TAG = "StartBaseActivity";
    protected ListView _list;
    protected int layoutResource = R.layout.start;
    BottomNavigationView bottomMenuNavigation;

    // Fragment creation cache
    private final Fragment playFragment = new PlayFragment();
    private final Fragment practiceFragment = new PracticeFragment();
    private final Fragment academyFragment = new AcademyFragment();
    private final Fragment settingsFragment = new SettingsFragment();

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

        // Handling of bottom navigation
        bottomMenuNavigation = findViewById(R.id.bottom_nav);
        bottomMenuNavigation.setOnItemSelectedListener(item -> {
            int id = item.getItemId();
            if( id == R.id.nav_play) {
                showFragment(playFragment);
                return true;
            } else if (id == R.id.nav_practice) {
                showFragment(practiceFragment);
                return true;
            } else if (id == R.id.nav_academy) {
                showFragment(academyFragment);
                return true;
            } else if (id == R.id.nav_settings) {
                showFragment(settingsFragment);
                return true;
            }
            return false;
        });

        // Load default play fragment on launch
        if (savedInstanceState == null){
            showFragment(playFragment);
            bottomMenuNavigation.setSelectedItemId(R.id.nav_play);
        }
    }
    private void setupRow(View row, String title, int iconRes, Runnable action) {
        TextView txt = row.findViewById(R.id.txt);
        ImageView img = row.findViewById(R.id.img);

        txt.setText(title);
        img.setImageResource(iconRes);

        row.setClickable(true);
        row.setOnClickListener(v -> action.run());
    }
    private void showFragment(Fragment fragment){
        getSupportFragmentManager()
                .beginTransaction()
                .setCustomAnimations(
                        android.R.anim.fade_in,
                        android.R.anim.fade_out
                )
                .replace(R.id.content_container, fragment)
                .commit();
    }

}
