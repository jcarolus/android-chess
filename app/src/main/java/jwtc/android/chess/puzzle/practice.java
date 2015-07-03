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

        ActivityHelper.prepareWindowSettings(this);

        _wakeLock = ActivityHelper.getWakeLock(this);
        
        setContentView(R.layout.practice);

        ActivityHelper.makeActionOverflowMenuShown(this);

        _chessView = new ChessViewPractice(this);

    }


    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        MenuInflater inflater = getMenuInflater();
        inflater.inflate(R.menu.practice_topmenu, menu);
        return super.onCreateOptionsMenu(menu);
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        // Handle presses on the action bar items
        Intent intent;
        switch (item.getItemId()) {
            case android.R.id.home:
                // API 5+ solution
                onBackPressed();
                return true;
            case R.id.action_help:
                Intent i = new Intent();
                i.setClass(practice.this, HtmlActivity.class);
                i.putExtra(HtmlActivity.HELP_MODE, "help_practice");
                startActivity(i);
                return true;
            default:
                return super.onOptionsItemSelected(item);
        }
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