package jwtc.android.chess.puzzle;

import java.io.InputStream;

import jwtc.android.chess.*;
import android.app.Activity;
import android.net.Uri;
import android.os.Bundle;
import android.os.PowerManager;
import android.view.*;
import android.widget.*;
import android.content.Intent;
import android.content.SharedPreferences;

public class practice extends Activity {
	
    /** instances for the view and game of chess **/
	private ChessViewPractice _chessView;
	private PowerManager.WakeLock _wakeLock;
	
    /** Called when the activity is first created. */
    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

      //NOTE: Should be called before Activity.setContentView() or it will throw!
        SharedPreferences prefs = getSharedPreferences("ChessPlayer", MODE_PRIVATE);
        if(prefs.getBoolean("fullScreen", true)){
        	getWindow().setFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN, WindowManager.LayoutParams.FLAG_FULLSCREEN); 
        }
        requestWindowFeature(Window.FEATURE_NO_TITLE);
        
        PowerManager pm = (PowerManager) getSystemService(POWER_SERVICE);  
        _wakeLock = pm.newWakeLock(PowerManager.SCREEN_DIM_WAKE_LOCK, "DoNotDimScreen");
        
        setContentView(R.layout.practice);
        
        _chessView = new ChessViewPractice(this);

    }
  
	/**
	 * 
	 */
    @Override
    protected void onResume() {
        
		SharedPreferences prefs = getSharedPreferences("ChessPlayer", MODE_PRIVATE);
		if(prefs.getBoolean("wakeLock", true))
		{
			_wakeLock.acquire();	
		}
		 final Intent intent = getIntent();
	     Uri uri = intent.getData();
	     InputStream is = null;
	     if(uri != null){
			try {
				is = getContentResolver().openInputStream(uri);
			} catch(Exception ex){}
	     }
        _chessView.OnResume(prefs, is);
        _chessView.updateState();
		
        super.onResume();
    }


    @Override
    protected void onPause() {
        
    	if(_wakeLock.isHeld())
        {
        	_wakeLock.release();
        }
        
        SharedPreferences.Editor editor = getSharedPreferences("ChessPlayer", MODE_PRIVATE).edit();
        _chessView.OnPause(editor);

        editor.commit();
        
        super.onPause();
    }
    @Override
    protected void onDestroy(){
    	_chessView.OnDestroy();
    	super.onDestroy();
    }
        
    public void doToast(final String text){
		Toast t = Toast.makeText(this, text, Toast.LENGTH_LONG);
		t.setGravity(Gravity.CENTER, 0, 0);
		t.show();
    }

    public void flipBoard(){
    	_chessView.flipBoard();
    }
}