package jwtc.android.chess.puzzle;

import jwtc.android.chess.*;
import android.app.Activity;
import android.os.Bundle;
import android.os.PowerManager;
import android.util.Log;
import android.view.*;
import android.widget.*;
import android.content.SharedPreferences;

public class puzzle extends Activity {
	
    /** instances for the view and game of chess **/
	private ChessViewPuzzle _chessView;
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
        
        setContentView(R.layout.puzzle);
        
        _chessView = new ChessViewPuzzle(this);

    }
    
   
	/**
	 * 
	 */
    @Override
    protected void onResume() {
        
    	Log.i("puzzle", "onResume");
    	
		SharedPreferences prefs = getSharedPreferences("ChessPlayer", MODE_PRIVATE);
		if(prefs.getBoolean("wakeLock", true))
		{
			_wakeLock.acquire();	
		}
        
        _chessView.OnResume(prefs);
        _chessView.updateState();
	
        super.onResume();
        //
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