package jwtc.android.chess;

import jwtc.android.chess.puzzle.practice;
import jwtc.android.chess.puzzle.puzzle;
import jwtc.android.chess.tools.pgntool;
import jwtc.android.chess.ics.*;
import android.app.ListActivity;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.res.Configuration;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.Bundle;
import android.util.Log;
import android.view.Gravity;
import android.view.View;
import android.view.View.OnClickListener;
import android.widget.Button;
import android.widget.ListView;
import android.widget.Toast;

import java.util.Locale;

public class start extends ListActivity {

	//private ListView _lvStart;
	public static final String TAG = "start";

	/**
	 * Called when the activity is first created.
	 */
	@Override
	public void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);

		SharedPreferences getData = getSharedPreferences("ChessPlayer", Context.MODE_PRIVATE);
		String myLanguage  	= getData.getString("localelanguage", "");

		Locale current = getResources().getConfiguration().locale;
		String language = current.getLanguage();
		if(myLanguage.equals("")){    // localelanguage not used yet? then use device default locale
			myLanguage = language;
		}

		Locale locale = new Locale(myLanguage);    // myLanguage is current language
		Locale.setDefault(locale);
		Configuration config = new Configuration();
		config.locale = locale;
		getBaseContext().getResources().updateConfiguration(config,
				getBaseContext().getResources().getDisplayMetrics());

		setContentView(R.layout.start);

		if (getIntent().getBooleanExtra("RESTART", false)) {
			finish();
			Intent intent = new Intent(this, start.class);
			startActivity(intent);
		}
	}

	@Override
	protected void onListItemClick(ListView l, View v, int position, long id){
		String s = getListView().getItemAtPosition(position).toString();
		try {
			Intent i = new Intent();
			Log.i("start", s);
			if (s.equals(getString(R.string.start_play))) {
				i.setClass(start.this, main.class);
				i.setFlags(Intent.FLAG_ACTIVITY_REORDER_TO_FRONT);
				startActivity(i);
			} else if (s.equals(getString(R.string.start_practice))) {
				i.setClass(start.this, practice.class);
				i.setFlags(Intent.FLAG_ACTIVITY_REORDER_TO_FRONT);
				startActivity(i);
			} else if (s.equals(getString(R.string.start_puzzles))) {
				i.setClass(start.this, puzzle.class);
				i.setFlags(Intent.FLAG_ACTIVITY_REORDER_TO_FRONT);
				startActivity(i);
			} else if (s.equals(getString(R.string.start_about))) {
				i.setClass(start.this, HtmlActivity.class);
				i.putExtra(HtmlActivity.HELP_MODE, "about");
				startActivity(i);
			} else if (s.equals(getString(R.string.start_ics))) {
				i.setClass(start.this, ICSClient.class);
				startActivity(i);
			} else if (s.equals(getString(R.string.start_pgn))) {
				i.setClass(start.this, pgntool.class);
				i.setFlags(Intent.FLAG_ACTIVITY_REORDER_TO_FRONT);
				startActivity(i);
			} else if (s.equals(getString(R.string.start_globalpreferences))) {
				i.setClass(start.this, ChessPreferences.class);
				startActivityForResult(i, 0);
			} else if (s.equals(getString(R.string.menu_help))) {
				i.setClass(start.this, HtmlActivity.class);
				i.putExtra(HtmlActivity.HELP_MODE, "help");
				startActivity(i);
			} else if (s.equals(getString(R.string.start_playhub))) {
				String playHubPackageName = "com.playhub";
				if (isAppInstalled(playHubPackageName)) {
					i = getPackageManager().getLaunchIntentForPackage(playHubPackageName);
					//i.putExtra("gameEngine", getPackageName());
					startActivity(i);
				} else {
					try {
						startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse("market://details?id=" + playHubPackageName)));
					} catch (android.content.ActivityNotFoundException activityNotFoundException) {
						startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse("http://play.google.com/store/apps/details?id=" + playHubPackageName)));
					}
				}
			}

		} catch (Exception ex) {
			Toast t = Toast.makeText(start.this, R.string.toast_could_not_start_activity, Toast.LENGTH_LONG);
			t.setGravity(Gravity.BOTTOM, 0, 0);
			t.show();
		}
	}

	protected void onActivityResult(int requestCode, int resultCode, Intent data) {
		if(resultCode == 1){
			Log.i(TAG, "finish and restart");

			Intent intent = new Intent(this, start.class);
			//intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
			intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP);
			intent.putExtra("RESTART", true);
			startActivity(intent);

		}
	}
   
	private boolean isAppInstalled(String uri) {
		PackageManager pm = getPackageManager();
		boolean app_installed;
		try {
			pm.getPackageInfo(uri, PackageManager.GET_ACTIVITIES);
			app_installed = true;
		}
		catch (PackageManager.NameNotFoundException e) {
			app_installed = false;
		}
		return app_installed;
	}

}
