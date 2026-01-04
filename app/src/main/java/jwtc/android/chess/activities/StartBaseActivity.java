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

/**
 * StartBaseActivity
 *
 * Main entry activity for the app.
 * - Applies theme configuration (night mode)
 * - Displays app version in the ActionBar
 * - Hosts and switches between main fragments using BottomNavigationView
 *
 * This activity acts as a lightweight fragment container and UI coordinator.
 */
public class StartBaseActivity extends AppCompatActivity {

    /** Logging tag */
    public static final String TAG = "StartBaseActivity";

    /** Optional legacy list reference (not actively used here) */
    protected ListView _list;

    /** Layout used by this activity */
    protected int layoutResource = R.layout.start;

    /** Bottom navigation menu */
    BottomNavigationView bottomMenuNavigation;

    // --- Fragment cache ---
    // Fragments are created once and reused to preserve state and reduce overhead
    private final Fragment playFragment = new PlayFragment();
    private final Fragment practiceFragment = new PracticeFragment();
    private final Fragment academyFragment = new AcademyFragment();
    private final Fragment settingsFragment = new SettingsFragment();

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        // Load player preferences
        SharedPreferences prefs =
                getSharedPreferences("ChessPlayer", Context.MODE_PRIVATE);

        // --- Resource & configuration handling ---
        Resources resources = getResources();
        Configuration configuration = resources.getConfiguration();
        DisplayMetrics displayMetrics = resources.getDisplayMetrics();

        // Handle configuration update depending on Android version
        // (Older APIs require manual update)
        if (Build.VERSION.SDK_INT > Build.VERSION_CODES.N) {
            getApplicationContext().createConfigurationContext(configuration);
        } else {
            resources.updateConfiguration(configuration, displayMetrics);
        }

        // --- Night mode handling ---
        // User preference overrides system setting
        if (prefs.getBoolean("nightMode", false)) {
            AppCompatDelegate.setDefaultNightMode(
                    AppCompatDelegate.MODE_NIGHT_YES
            );
        } else {
            AppCompatDelegate.setDefaultNightMode(
                    AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM
            );
        }

        // --- ActionBar subtitle: app version ---
        ActionBar actionBar = getSupportActionBar();
        if (actionBar != null) {
            try {
                PackageInfo pInfo =
                        getPackageManager().getPackageInfo(getPackageName(), 0);
                actionBar.setSubtitle(pInfo.versionName);
            } catch (PackageManager.NameNotFoundException ignored) {
                // Version info not critical; fail silently
            }
        }

        // Inflate activity layout
        setContentView(layoutResource);

        // --- Bottom navigation setup ---
        bottomMenuNavigation = findViewById(R.id.bottom_nav);
        bottomMenuNavigation.setOnItemSelectedListener(item -> {
            int id = item.getItemId();

            if (id == R.id.nav_play) {
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

        // --- Default fragment on first launch ---
        // Prevents fragment duplication on configuration changes
        if (savedInstanceState == null) {
            showFragment(playFragment);
            bottomMenuNavigation.setSelectedItemId(R.id.nav_play);
        }
    }

    /**
     * Helper method to configure a clickable row UI element.
     *
     * @param row      Root view of the row
     * @param title    Display text
     * @param iconRes  Drawable resource ID
     * @param action   Action to run when clicked
     */
    private void setupRow(View row, String title, int iconRes, Runnable action) {
        TextView txt = row.findViewById(R.id.txt);
        ImageView img = row.findViewById(R.id.img);

        txt.setText(title);
        img.setImageResource(iconRes);

        row.setClickable(true);
        row.setOnClickListener(v -> action.run());
    }

    /**
     * Replaces the current fragment inside the content container.
     * Uses fade animations for a smoother UI transition.
     *
     * @param fragment Fragment to display
     */
    private void showFragment(Fragment fragment) {
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
