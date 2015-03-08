package jwtc.android.chess;

import jwtc.android.chess.puzzle.practice;
import jwtc.android.chess.puzzle.puzzle;
import jwtc.android.chess.tools.pgntool;
import jwtc.android.chess.convergence.ConvergenceActivity;
import jwtc.android.chess.ics.*;
import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.util.Log;
import android.view.Gravity;
import android.view.View;
import android.view.Window;
import android.view.View.OnClickListener;
import android.widget.Button;
import android.widget.Toast;

public class start extends Activity/*ListActivity*/  {
	
	//private ListView _lvStart;
	public static final String TAG = "start";
	
	/** Called when the activity is first created. */
    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        
        requestWindowFeature(Window.FEATURE_NO_TITLE);
        
        setContentView(R.layout.start);        
        
        //final CharSequence[] arrString;
        //arrString = getResources().getTextArray(R.array.start_menu); 
        
        OnClickListener ocl = new OnClickListener() {
        	public void onClick(View arg0) {
        		Button b = (Button)arg0;
        		try{
					Intent i = new Intent();
					String s = b.getText().toString();
					Log.i("start", s);
					if(s.equals(getString(R.string.start_play))){
						i.setClass(start.this, main.class); 
						i.setFlags(Intent.FLAG_ACTIVITY_REORDER_TO_FRONT);
						startActivity(i); 
					} else if(s.equals(getString(R.string.start_practice))){
						i.setClass(start.this, practice.class); 
						i.setFlags(Intent.FLAG_ACTIVITY_REORDER_TO_FRONT);
						startActivity(i); 
					} else if(s.equals(getString(R.string.start_puzzles))){
						i.setClass(start.this, puzzle.class); 
						i.setFlags(Intent.FLAG_ACTIVITY_REORDER_TO_FRONT);
						startActivity(i); 
					} else if(s.equals(getString(R.string.start_about))){
						i.setClass(start.this, HtmlActivity.class);
						i.putExtra(HtmlActivity.HELP_MODE, "about");
						startActivity(i); 
					} else if(s.equals(getString(R.string.start_ics))){
						i.setClass(start.this, ICSClient.class);
						startActivity(i); 
					} else if(s.equals(getString(R.string.start_pgn))){
						i.setClass(start.this, pgntool.class); 
						i.setFlags(Intent.FLAG_ACTIVITY_REORDER_TO_FRONT);
						startActivity(i); 
					} else if (s.equals(getString(R.string.start_globalpreferences))){
						i.setClass(start.this, ChessPreferences.class);
						startActivity(i); 
					} else if (s.equals(getString(R.string.menu_help))){
						i.setClass(start.this, HtmlActivity.class);
						i.putExtra(HtmlActivity.HELP_MODE, "help");
						startActivity(i);
					} else if(s.equals(getString(R.string.start_convergence))){
						i.setClass(start.this, ConvergenceActivity.class);
						i.setFlags(Intent.FLAG_ACTIVITY_REORDER_TO_FRONT);
						startActivity(i); 
					} 
					
				} catch(Exception ex){
					Toast t = Toast.makeText(start.this, "Could not start the activity", Toast.LENGTH_LONG);
					t.setGravity(Gravity.BOTTOM, 0, 0);
					t.show();
				}
        	}
        };
        
        ((Button)findViewById(R.id.StartButtonPlay)).setOnClickListener(ocl);
        ((Button)findViewById(R.id.StartButtonPractice)).setOnClickListener(ocl);
        ((Button)findViewById(R.id.StartButtonPuzzles)).setOnClickListener(ocl);
        ((Button)findViewById(R.id.StartButtonICS)).setOnClickListener(ocl);
        ((Button)findViewById(R.id.StartButtonPreferences)).setOnClickListener(ocl);
        //((Button)findViewById(R.id.StartButtonAbout)).setOnClickListener(ocl);
        ((Button)findViewById(R.id.StartButtonAdvanced)).setOnClickListener(ocl);
        ((Button)findViewById(R.id.StartButtonHelp)).setOnClickListener(ocl);
        ((Button)findViewById(R.id.StartButtonConvergence)).setOnClickListener(ocl);

       
        //Connection con = new Connection();
       
        /*
        _lvStart =(ListView)findViewById(android.R.id.list);
        
        _lvStart.setOnItemClickListener(new OnItemClickListener(){

			@Override
			public void onItemClick(AdapterView<?> arg0, View arg1, int arg2, long arg3) {
				try{
					Intent i = new Intent();
					if(arrString[arg2].equals(getString(R.string.start_play))){
						i.setClass(start.this, main.class); startActivity(i); 
					} else if(arrString[arg2].equals(getString(R.string.start_practice))){
						i.setClass(start.this, practice.class); startActivity(i); 
					} else if(arrString[arg2].equals(getString(R.string.start_puzzles))){
						i.setClass(start.this, puzzle.class); startActivity(i); 
					} else if(arrString[arg2].equals(getString(R.string.start_about))){
						i.setClass(start.this, HtmlActivity.class);
						i.putExtra(HtmlActivity.HELP_MODE, "about");
						startActivity(i); 
					} else if(arrString[arg2].equals(getString(R.string.start_ics))){
						i.setClass(start.this, ICSClient.class);
						startActivity(i); 
					} else if(arrString[arg2].equals(getString(R.string.start_pgn))){
						i.setClass(start.this, pgntool.class); startActivity(i); 
					} else if (arrString[arg2].equals(getString(R.string.start_globalpreferences))){
						i.setClass(start.this, ChessPreferences.class);
						startActivity(i); 
					} else if (arrString[arg2].equals(getString(R.string.start_exit))){
						finish();
					}
				} catch(Exception ex){
					Toast t = Toast.makeText(start.this, "Could not start the activity", Toast.LENGTH_LONG);
					t.setGravity(Gravity.BOTTOM, 0, 0);
					t.show();
				}
			}
        });
        */
    }
    
    
   
}
