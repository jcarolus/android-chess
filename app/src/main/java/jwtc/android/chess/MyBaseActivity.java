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
import android.util.AttributeSet;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.LayoutInflater.Factory;
import android.view.ViewConfiguration;
import android.view.Window;
import android.view.WindowManager;
import android.widget.TextView;

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

	// @see http://stackoverflow.com/questions/9739498/android-action-bar-not-showing-overflow
	public void makeActionOverflowMenuShown() {
		//devices with hardware menu button (e.g. Samsung Note) don't show action overflow menu
		try {
			ViewConfiguration config = ViewConfiguration.get(this);
			Field menuKeyField = ViewConfiguration.class.getDeclaredField("sHasPermanentMenuKey");
			if (menuKeyField != null) {
				menuKeyField.setAccessible(true);
				menuKeyField.setBoolean(config, false);
			}
		} catch (Exception e) {
			Log.d("main", e.getLocalizedMessage());
		}
	}

	public SharedPreferences getPrefs(){
		return this.getSharedPreferences("ChessPlayer", Activity.MODE_PRIVATE);
	}

	public void prepareWindowSettings(){

		SharedPreferences prefs = getPrefs();
		if(prefs.getBoolean("fullScreen", true)){
			this.getWindow().setFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN, WindowManager.LayoutParams.FLAG_FULLSCREEN);

		}

		if(this.getResources().getBoolean(R.bool.portraitOnly)){
			this.setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_PORTRAIT);
		}

		int configOrientation = this.getResources().getConfiguration().orientation;
		if(configOrientation == Configuration.ORIENTATION_LANDSCAPE) {
			this.requestWindowFeature(Window.FEATURE_NO_TITLE);
		} else {
			this.getActionBar().setDisplayHomeAsUpEnabled(true);
		}
	}

	public PowerManager.WakeLock getWakeLock(){
		PowerManager pm = (PowerManager) this.getSystemService(Activity.POWER_SERVICE);
		return pm.newWakeLock(PowerManager.SCREEN_DIM_WAKE_LOCK, "DoNotDimScreen");

	}

}
