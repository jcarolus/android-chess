package jwtc.android.chess;

import android.app.Activity;
import android.content.SharedPreferences;
import android.content.res.Configuration;
import android.media.AudioManager;
import android.media.SoundPool;
import android.os.Bundle;
import android.os.PowerManager;
import android.preference.PreferenceActivity;
import android.util.Log;
import android.view.Gravity;
import android.view.MenuItem;
import android.view.ViewConfiguration;
import android.view.Window;
import android.view.WindowManager;
import android.widget.Toast;

import com.google.android.gms.analytics.HitBuilders;
import com.google.android.gms.analytics.Tracker;

import java.lang.reflect.Field;

public class MyBaseActivity extends android.app.Activity{

	protected PowerManager.WakeLock _wakeLock;
	protected Tracker _tracker;
	public static final String TAG = "MyBaseActivity";
	private long _onResumeTimeMillies = 0;
	public SoundPool spSound;
	public int _itickTock, _ihorseNeigh, _ismallNeigh, _ihorseSnort, _ihorseRunAway,
			   _imove, _icapture;
	public float _fVolume = 1.0f;

	@Override
    public void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);

		this.prepareWindowSettings();

		_wakeLock = this.getWakeLock();

		MyApplication application = (MyApplication) getApplication();
		_tracker = application .getDefaultTracker();
//		_tracker.setScreenName(TAG);
//		_tracker.send(new HitBuilders.ScreenViewBuilder().build());
		spSound = new SoundPool(7, AudioManager.STREAM_MUSIC, 0);
		_itickTock = spSound.load(this, R.raw.ticktock, 1);
		_ihorseNeigh = spSound.load(this, R.raw.horseneigh, 1);
		_ismallNeigh = spSound.load(this, R.raw.smallneigh, 2);
		_ihorseSnort = spSound.load(this, R.raw.horsesnort, 1);
		_ihorseRunAway = spSound.load(this, R.raw.horserunaway, 1);
		_imove = spSound.load(this, R.raw.move, 1);
		_icapture = spSound.load(this, R.raw.capture, 1);

	}

	@Override
	protected void onResume() {

		SharedPreferences prefs = this.getPrefs();

		if(prefs.getBoolean("wakeLock", true)) {
			_wakeLock.acquire();
		}

		_onResumeTimeMillies = System.currentTimeMillis();

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

	public void trackEvent(String category, String action){
		_tracker.send(new HitBuilders.EventBuilder()
				.setCategory(category)
				.setAction(action)
				.build());
	}

	public void trackEvent(String category, String action, String label){
		_tracker.send(new HitBuilders.EventBuilder()
				.setCategory(category)
				.setAction(action)
				.setLabel(label)
				.build());
	}

	public void set_fVolume(float vol){
		_fVolume = vol;
	}

	public float get_fVolume(){
		return _fVolume;
	}

	public void soundTickTock(){
		spSound.play(_itickTock, _fVolume, _fVolume, 1, 0, 1);
	}

	public void soundHorseNeigh(){
		spSound.play(_ihorseNeigh, _fVolume, _fVolume, 1, 0, 1);
	}

	public void soundSmallNeigh(){
		spSound.play(_ismallNeigh, _fVolume, _fVolume, 2, 0, 1);
	}

	public void soundHorseSnort(){
		spSound.play(_ihorseSnort, _fVolume, _fVolume, 1, 0, 1);
	}

	public void soundHorseRunAway(){
		spSound.play(_ihorseRunAway, _fVolume, _fVolume, 1, 0, 1);
	}

	public void soundMove(){
		spSound.play(_imove, _fVolume, _fVolume, 1, 0, 1);
	}

	public void soundCapture(){
		spSound.play(_icapture, _fVolume, _fVolume, 1, 0 ,1);
	}
}
