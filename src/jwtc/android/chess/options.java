package jwtc.android.chess;

import jwtc.chess.GameControl;
import android.app.Activity;
import android.app.AlertDialog;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.text.InputType;
import android.view.Gravity;
import android.view.View;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.CompoundButton;
import android.widget.EditText;
import android.widget.RadioButton;
import android.widget.Spinner;
import android.widget.Toast;
import android.widget.CompoundButton.OnCheckedChangeListener;
import android.widget.TableRow;

public class options extends Activity {

	public static final int RESULT_960 = 1;
	
	private CheckBox _checkPlay, _checkAutoFlip, _checkMoves, _check960;
	private Spinner _spinLevel, _spinLevelPly;
	private Button _butCancel, _butOk;
	private RadioButton _radioTime, _radioPly;
	private TableRow _tableRowOption960;
	
	@Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
     
        setContentView(R.layout.options);
     
        setTitle(R.string.title_options);

		_checkPlay = (CheckBox)findViewById(R.id.CheckBoxOptionsPlayAndroid);
		_checkPlay.setOnCheckedChangeListener(new OnCheckedChangeListener(){
			public void onCheckedChanged(CompoundButton buttonView, boolean isChecked) {
				_checkAutoFlip.setEnabled(false == isChecked);
			}		
		});

		_checkAutoFlip = (CheckBox)findViewById(R.id.CheckBoxOptionsAutoFlip);
		_checkMoves = (CheckBox)findViewById(R.id.CheckBoxOptionsShowMoves);
		
		_tableRowOption960 = (TableRow)findViewById(R.id.TableRowOptions960);
		_check960 = (CheckBox)findViewById(R.id.CheckBoxOptions960);
		
	    ArrayAdapter<CharSequence> adapter = ArrayAdapter.createFromResource(this, R.array.levels_time, android.R.layout.simple_spinner_item);
	    adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);

	    _spinLevel = (Spinner)findViewById(R.id.SpinnerOptionsLevel);
	    _spinLevel.setPrompt(getString(R.string.title_pick_level));
	    _spinLevel.setAdapter(adapter);
	    
	    ArrayAdapter<CharSequence> adapterPly = ArrayAdapter.createFromResource(this, R.array.levels_ply, android.R.layout.simple_spinner_item);
	    adapterPly.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
	    _spinLevelPly = (Spinner)findViewById(R.id.SpinnerOptionsLevelPly);
	    _spinLevelPly.setPrompt(getString(R.string.title_pick_level));
	    _spinLevelPly.setAdapter(adapterPly);
	    
	    _radioTime = (RadioButton)findViewById(R.id.RadioOptionsTime);
	    _radioTime.setOnCheckedChangeListener(new OnCheckedChangeListener(){
			public void onCheckedChanged(CompoundButton buttonView, boolean isChecked) {
				_radioPly.setChecked(_radioTime.isChecked() ? false : true);
			}		
		});
	    _radioPly = (RadioButton)findViewById(R.id.RadioOptionsPly);
	    _radioPly.setOnCheckedChangeListener(new OnCheckedChangeListener(){
			public void onCheckedChanged(CompoundButton buttonView, boolean isChecked) {
				_radioTime.setChecked(_radioPly.isChecked() ? false : true);
			}		
		});
	    
	    _butCancel = (Button)findViewById(R.id.ButtonOptionsCancel);
		_butCancel.setOnClickListener(new View.OnClickListener() {
        	public void onClick(View arg0) {
        		setResult(RESULT_CANCELED);
        		finish();
        	}
		});
		
		_butOk = (Button)findViewById(R.id.ButtonOptionsOk);
		_butOk.setOnClickListener(new View.OnClickListener() {
        	public void onClick(View arg0) {
        		
        		SharedPreferences.Editor editor = getSharedPreferences("ChessPlayer", MODE_PRIVATE).edit();
        		
        		editor.putInt("levelMode", _radioTime.isChecked() ? GameControl.LEVEL_TIME : GameControl.LEVEL_PLY);
        		editor.putInt("level", _spinLevel.getSelectedItemPosition()+1);
        		editor.putInt("levelPly", _spinLevelPly.getSelectedItemPosition()+1);
        		editor.putInt("playMode", _checkPlay.isChecked() ? GameControl.HUMAN_PC : GameControl.HUMAN_HUMAN);
        		editor.putBoolean("autoflipBoard", _checkAutoFlip.isChecked());
        		editor.putBoolean("showMoves", _checkMoves.isChecked());
        		
        		editor.commit();
        		
        		if(_tableRowOption960.getVisibility() == View.VISIBLE && _check960.isChecked()){
        			
        			
        			AlertDialog.Builder builder = new AlertDialog.Builder(options.this);
        	    	builder.setTitle("Chess 960 :: Manual position nr or random?");
        	    	final EditText input = new EditText(options.this);
        	    	input.setInputType(InputType.TYPE_CLASS_PHONE);
        	    	builder.setView(input);
        	    	builder.setPositiveButton("Manual", new DialogInterface.OnClickListener() {  
        	    	    public void onClick(DialogInterface dialog, int whichButton) {
        	    	    	try{
        		    	    	int seed = Integer.parseInt(input.getText().toString());
        		    	    	
        		    	    	if(seed >= 0 && seed <= 960){
	        		    	    	//_chessView.newGameRandomFischer(seed);
	        		    	    	SharedPreferences.Editor editor = getSharedPreferences("ChessPlayer", MODE_PRIVATE).edit();
	        		    	        editor.putString("FEN", null);
	        		    	        editor.putInt("boardNum", 0);
	        		    	        editor.putInt("randomFischerSeed", seed % 960);
	        		    	        editor.commit();
	        		    	        
	        		    	        finish();
        		    	    	} else {
        		    	    		doToast("Invalid position, enter a number between 0 and 960");
        		    	    	}
        	    	    	} catch(Exception ex){
        	    	    		doToast("Invalid position, enter a number");
        	    	    	}
        	    	    }
        	    	});
        	    	builder.setNegativeButton("Random", new DialogInterface.OnClickListener() {

        	            public void onClick(DialogInterface dialog, int which) {
        	            	int seed = -1;
        	            	//seed = _chessView.newGameRandomFischer(seed);
        	            	
        	            	SharedPreferences.Editor editor = getSharedPreferences("ChessPlayer", MODE_PRIVATE).edit();
        	                editor.putString("FEN", null);
        	                editor.putInt("boardNum", -1);
        	                editor.putInt("randomFischerSeed", seed);
        	                editor.commit();
        	                
        	                finish();
        	            }
        	    	});
        	        
        	        AlertDialog alert = builder.create();
        			alert.show();
        			
        			setResult(RESULT_960);
        			
        		} else {
        			setResult(RESULT_OK);
        			finish();
        		}
        		
        		
        		
        	}
		});
	}
	
	
	 @Override
	 protected void onResume() {
		 
		 final Intent intent = getIntent();

		 if(intent.getExtras().getInt("requestCode") == main.REQUEST_NEWGAME){
			 setTitle(R.string.menu_new);
			 _tableRowOption960.setVisibility(View.VISIBLE);
		 } else {
			 _check960.setChecked(false);
			 _tableRowOption960.setVisibility(View.GONE);
		 }
		 
		 SharedPreferences prefs = getSharedPreferences("ChessPlayer", MODE_PRIVATE);
		 
		_checkPlay.setChecked(prefs.getInt("playMode", GameControl.HUMAN_PC) == GameControl.HUMAN_PC);
 		_checkAutoFlip.setChecked(prefs.getBoolean("autoflipBoard", false));
 		_checkAutoFlip.setEnabled(false == _checkPlay.isChecked());
 		_checkMoves.setChecked(prefs.getBoolean("showMoves", true));
 		
 		_radioTime.setChecked(prefs.getInt("levelMode", GameControl.LEVEL_TIME) == GameControl.LEVEL_TIME);
 	    _radioPly.setChecked(prefs.getInt("levelMode", GameControl.LEVEL_TIME) == GameControl.LEVEL_PLY);
 	    
 		_spinLevel.setSelection(prefs.getInt("level", 2)-1);
 		_spinLevelPly.setSelection(prefs.getInt("levelPly", 2)-1);
		 
		 super.onResume();
	 }
	 
	 @Override
	    protected void onPause() {
		 
		 super.onPause();
	 }
	 
	 public void doToast(final String text){
		Toast t = Toast.makeText(this, text, Toast.LENGTH_LONG);
		t.setGravity(Gravity.BOTTOM, 0, 0);
		t.show();
    }
}
