package jwtc.android.chess;

import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
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
import android.widget.Toast;

import java.util.Locale;

import androidx.appcompat.app.AppCompatActivity;
import jwtc.android.chess.ics.ICSClient;
import jwtc.android.chess.play.PlayActivity;
import jwtc.android.chess.practice.PracticeActivity;
import jwtc.android.chess.puzzle.PuzzleActivity;
import jwtc.android.chess.tools.AdvancedActivity;


public class start extends AppCompatActivity {

    public static final String TAG = "start";
    private static String _ssActivity = "";
    private ListView _list;

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        SharedPreferences getData = getSharedPreferences("ChessPlayer", Context.MODE_PRIVATE);
        String myLanguage = getData.getString("localelanguage", "");

        Locale current = getResources().getConfiguration().locale;
        String language = current.getLanguage();
        if (myLanguage.equals("")) {    // localelanguage not used yet? then use device default locale
            myLanguage = language;
        }

        Locale locale = new Locale(myLanguage);    // myLanguage is current language
        Locale.setDefault(locale);

        Resources resources = getResources();
        Configuration configuration = resources.getConfiguration();
        DisplayMetrics displayMetrics = resources.getDisplayMetrics();
        configuration.setLocale(locale);

        if (Build.VERSION.SDK_INT > Build.VERSION_CODES.N){
            getApplicationContext().createConfigurationContext(configuration);
        } else {
            resources.updateConfiguration(configuration, displayMetrics);
        }

        setContentView(R.layout.start);

        String[] title = getResources().getStringArray(R.array.start_menu);

        _list = (ListView) findViewById(R.id.ListStart);
        _list.setOnItemClickListener(new AdapterView.OnItemClickListener() {
            @Override
            public void onItemClick(AdapterView<?> parent, View view, int position, long id) {
                _ssActivity = parent.getItemAtPosition(position).toString();
                try {
                    Intent i = new Intent();
                    Log.i("start", _ssActivity);
                    if (_ssActivity.equals(getString(R.string.start_play))) {
                        i.setClass(start.this, PlayActivity.class);
                        i.setFlags(Intent.FLAG_ACTIVITY_REORDER_TO_FRONT);
                        startActivity(i);
                    } else if (_ssActivity.equals(getString(R.string.start_practice))) {
                        i.setClass(start.this, PracticeActivity.class);
                        i.setFlags(Intent.FLAG_ACTIVITY_REORDER_TO_FRONT);
                        startActivity(i);
                    } else if (_ssActivity.equals(getString(R.string.start_puzzles))) {
                        i.setClass(start.this, PuzzleActivity.class);
                        i.setFlags(Intent.FLAG_ACTIVITY_REORDER_TO_FRONT);
                        startActivity(i);
                    } else if (_ssActivity.equals(getString(R.string.start_about))) {
                        i.setClass(start.this, HtmlActivity.class);
                        i.putExtra(HtmlActivity.HELP_STRING_RESOURCE, R.string.about_help);
                        startActivity(i);
                    } else if (_ssActivity.equals(getString(R.string.start_ics))) {
                        i.setClass(start.this, ICSClient.class);
                        startActivity(i);
                    } else if (_ssActivity.equals(getString(R.string.start_pgn))) {
                        i.setClass(start.this, AdvancedActivity.class);
                        i.setFlags(Intent.FLAG_ACTIVITY_REORDER_TO_FRONT);
                        startActivity(i);
                    } else if (_ssActivity.equals(getString(R.string.start_globalpreferences))) {
                        i.setClass(start.this, ChessPreferences.class);
                        startActivityForResult(i, 0);
                    } else {
                        Log.d(TAG, "Nothing to start");
                    }
                } catch (Exception ex) {
                    Log.d(TAG, "Exception " + (ex != null ? ex.getMessage() : " no ex"));
                    Toast t = Toast.makeText(start.this, R.string.toast_could_not_start_activity, Toast.LENGTH_LONG);
                    t.setGravity(Gravity.BOTTOM, 0, 0);
                    t.show();
                }
            }
        });
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        if (resultCode == RESULT_OK) {
            Log.i(TAG, "recreate");
            recreate();
        }
    }
}
