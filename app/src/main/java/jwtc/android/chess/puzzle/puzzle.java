package jwtc.android.chess.puzzle;

import jwtc.android.chess.*;
import android.app.Activity;
import android.content.Intent;
import android.content.pm.ActivityInfo;
import android.os.Bundle;
import android.os.PowerManager;
import android.util.Log;
import android.view.*;
import android.widget.*;
import android.content.SharedPreferences;

public class puzzle extends MyBaseActivity {
	
    /** instances for the view and game of chess **/
	private ChessViewPuzzle _chessView;

    /** Called when the activity is first created. */
    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        setContentView(R.layout.puzzle);

        this.makeActionOverflowMenuShown();

        _chessView = new ChessViewPuzzle(this);
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
            default:
                return super.onOptionsItemSelected(item);
        }
    }
	/**
	 * 
	 */
    @Override
    protected void onResume() {
        
    	Log.i("puzzle", "onResume");
    	
		SharedPreferences prefs = this.getPrefs();

        _chessView.OnResume(prefs);
        _chessView.updateState();
	
        super.onResume();
        //
    }


    @Override
    protected void onPause() {
        
        SharedPreferences.Editor editor = this.getPrefs().edit();
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