package jwtc.android.chess;

import android.app.Activity;
import android.content.Context;
import android.content.SharedPreferences;
import android.content.pm.ActivityInfo;
import android.content.res.Configuration;
import android.graphics.Color;
import android.os.Bundle;
import android.os.Handler;
import android.os.PowerManager;
import android.preference.PreferenceActivity;
import android.util.AttributeSet;
import android.util.Log;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.LayoutInflater.Factory;
import android.view.ViewConfiguration;
import android.view.Window;
import android.view.WindowManager;
import android.widget.TextView;
import android.widget.Toast;

import java.lang.reflect.Field;

public class MyBaseActivity extends android.app.Activity{

	protected PowerManager.WakeLock _wakeLock;

	@Override
    public void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);

		this.prepareWindowSettings();

		_wakeLock = this.getWakeLock();
	}

	@Override
	protected void onResume() {

		SharedPreferences prefs = this.getPrefs();

		if(prefs.getBoolean("wakeLock", true)) {
			_wakeLock.acquire();
		}

		super.onResume();
	}

	@Override
	protected void onPause() {
		if (_wakeLock.isHeld()) {
			_wakeLock.release();
		}
		super.onPause();
	}

	@Override
	public boolean onOptionsItemSelected(MenuItem item) {
		switch (item.getItemId()) {
			case android.R.id.home:
				// API 5+ solution
				onBackPressed();
				return true;
		}
		return super.onOptionsItemSelected(item);
	}

	public static void makeActionOverflowMenuShown(Activity activity){
		//devices with hardware menu button (e.g. Samsung Note) don't show action overflow menu
		try {
			ViewConfiguration config = ViewConfiguration.get(activity);
			Field menuKeyField = ViewConfiguration.class.getDeclaredField("sHasPermanentMenuKey");
			if (menuKeyField != null) {
				menuKeyField.setAccessible(true);
				menuKeyField.setBoolean(config, false);
			}
		} catch (Exception e) {
			Log.d("main", e.getLocalizedMessage());
		}
	}
	// @see http://stackoverflow.com/questions/9739498/android-action-bar-not-showing-overflow
	public void makeActionOverflowMenuShown() {
		makeActionOverflowMenuShown(this);
	}

	public static SharedPreferences getPrefs(Activity activity){
		return activity.getSharedPreferences("ChessPlayer", Activity.MODE_PRIVATE);
	}

	public SharedPreferences getPrefs(){
		return getPrefs(this);
	}

	public static void prepareWindowSettings(Activity activity) {
		SharedPreferences prefs = getPrefs(activity);
		if(prefs.getBoolean("fullScreen", true)){
			activity.getWindow().setFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN, WindowManager.LayoutParams.FLAG_FULLSCREEN);
		}

		int configOrientation = activity.getResources().getConfiguration().orientation;
		if(configOrientation == Configuration.ORIENTATION_LANDSCAPE) {
			if(false == activity instanceof PreferenceActivity) {
				activity.requestWindowFeature(Window.FEATURE_NO_TITLE);
			}
		} else {
			try {
				activity.getActionBar().setDisplayHomeAsUpEnabled(true);
			} catch(Exception ex){

			}
		}
	}
	public void prepareWindowSettings(){
		prepareWindowSettings(this);
	}

	public PowerManager.WakeLock getWakeLock(){
		PowerManager pm = (PowerManager) this.getSystemService(Activity.POWER_SERVICE);
		return pm.newWakeLock(PowerManager.SCREEN_DIM_WAKE_LOCK, "DoNotDimScreen");
	}

	public void doToast(final String text){
		Toast t = Toast.makeText(this, text, Toast.LENGTH_LONG);
		t.setGravity(Gravity.BOTTOM, 0, 0);
		t.show();
	}
}
