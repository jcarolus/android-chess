package jwtc.android.chess.tools;

import java.io.File;
import java.io.FileOutputStream;

import jwtc.android.chess.HtmlActivity;
import jwtc.android.chess.MyPGNProvider;
import jwtc.android.chess.R;
import jwtc.chess.PGNColumns;
import android.app.AlertDialog;
import android.app.ListActivity;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.SharedPreferences;
import android.database.Cursor;
import android.os.Bundle;
import android.os.Environment;
import android.util.Log;
import android.view.*;
import android.widget.AdapterView;
import android.widget.ListView;
import android.widget.Toast;
import android.widget.AdapterView.OnItemClickListener;

public class pgntool extends ListActivity {
	
	protected static final String MODE_IMPORT = "import";
	protected static final String MODE_DB_IMPORT = "db_import";
	protected static final String MODE_DB_POINT = "db_point";
	protected static final String MODE_UCI_INSTALL = "uci_install";
	protected static final String MODE_CREATE_PRACTICE = "create_practice";
	protected static final String MODE_IMPORT_PRACTICE = "import_practice";
	protected static final String MODE_IMPORT_PUZZLE = "import_puzzle";
	protected static final String MODE_IMPORT_OPENINGDATABASE = "import_openingdatabase";
	
	protected static final String EXTRA_MODE = "jwtc.android.chess.tools.Mode";
	
	private ListView _lvStart;
	
    /** Called when the activity is first created. */
    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        setContentView(R.layout.pgntool);
        
		_lvStart =(ListView)findViewById(android.R.id.list);
		        
		final CharSequence[] arrString;
		
		arrString = getResources().getTextArray(R.array.pgn_tool_menu); 

        _lvStart.setOnItemClickListener(new OnItemClickListener(){

			@Override
			public void onItemClick(AdapterView<?> arg0, View arg1, int arg2, long arg3) {
				if(arrString[arg2].equals(getString(R.string.pgntool_export_explanation))){
					doExport();
					
				} else if(arrString[arg2].equals(getString(R.string.pgntool_import_explanation))){
					Intent i = new Intent();
					i.setClass(pgntool.this, FileListView.class);
					i.putExtra(EXTRA_MODE, MODE_IMPORT);
					pgntool.this.startActivity(i);
					
				} else if(arrString[arg2].equals(getString(R.string.pgntool_create_db_explanation))){
					Intent i = new Intent();
					i.setClass(pgntool.this, FileListView.class);
					i.putExtra(EXTRA_MODE, MODE_DB_IMPORT);
					pgntool.this.startActivity(i);
					
				} else if(arrString[arg2].equals(getString(R.string.pgntool_point_db_explanation))){ 
				
					Intent i = new Intent();
					i.setClass(pgntool.this, FileListView.class);
					i.putExtra(EXTRA_MODE, MODE_DB_POINT);
					pgntool.this.startActivity(i);
				} /*else if(arrString[arg2].equals(getString(R.string.pgntool_engine_test))){
					
					Intent i = new Intent();
					i.setClass(pgntool.this, EngineTester.class);
					pgntool.this.startActivity(i);
					
				}*/ else if(arrString[arg2].equals(getString(R.string.pgntool_delete_explanation))){
					AlertDialog.Builder builder = new AlertDialog.Builder(pgntool.this);
					builder.setTitle(getString(R.string.pgntool_confirm_delete));
					
					builder.setPositiveButton(getString(R.string.button_ok), new DialogInterface.OnClickListener(){
						public void onClick(DialogInterface dialog, int which) {
							dialog.dismiss();
							pgntool.this.getContentResolver().delete(MyPGNProvider.CONTENT_URI, "1=1", null);
							doToast(getString(R.string.pgntool_deleted));
						}
					});
					builder.setNegativeButton(getString(R.string.button_cancel), new DialogInterface.OnClickListener(){
						public void onClick(DialogInterface dialog, int which) {
							dialog.dismiss();
						}
					});
					
					AlertDialog alert = builder.create();
					alert.show();
					
				} else if(arrString[arg2].equals(getString(R.string.pgntool_help))){
					Intent i = new Intent();
		    		 i.setClass(pgntool.this, HtmlActivity.class);
		    		 i.putExtra(HtmlActivity.HELP_MODE, "help_pgntool");
		    		 startActivity(i); 
				} else if(arrString[arg2].equals(getString(R.string.pgntool_point_uci_engine))){
					
					Intent i = new Intent();
					i.setClass(pgntool.this, FileListView.class);
					i.putExtra(EXTRA_MODE, MODE_UCI_INSTALL);
					pgntool.this.startActivity(i);
					
				}else if(arrString[arg2].equals(getString(R.string.pgntool_unset_uci_engine))){
					SharedPreferences prefs = getSharedPreferences("ChessPlayer", MODE_PRIVATE);
					String sEngine = prefs.getString("UCIEngine", null);
					if(sEngine != null){
						File f = new File("/data/data/jwtc.android.chess/" + sEngine);
						f.delete();
						SharedPreferences.Editor editor = prefs.edit();
						editor.putString("UCIEngine", null);
						editor.commit();
						doToast("Engine " + sEngine + " uninstalled");
					} else {
						doToast("No engine installed");
					}
				}else if(arrString[arg2].equals(getString(R.string.pgntool_reset_practice))){
					
					AlertDialog.Builder builder = new AlertDialog.Builder(pgntool.this);
					builder.setTitle(getString(R.string.pgntool_confirm_practice_reset));
					
					builder.setPositiveButton(getString(R.string.button_ok), new DialogInterface.OnClickListener(){
						public void onClick(DialogInterface dialog, int which) {
							dialog.dismiss();
							
							SharedPreferences prefs = getSharedPreferences("ChessPlayer", MODE_PRIVATE);
							SharedPreferences.Editor editor = prefs.edit();
							editor.putInt("practicePos", 0);
							editor.putInt("practiceTicks", 0);
							editor.commit();
							
							doToast("Practice set has been reset");
							
						}
					});
					builder.setNegativeButton(getString(R.string.button_cancel), new DialogInterface.OnClickListener(){
						public void onClick(DialogInterface dialog, int which) {
							dialog.dismiss();
						}
					});
					
					AlertDialog alert = builder.create();
					alert.show();
					
					
        		} else if(arrString[arg2].equals(getString(R.string.pgntool_import_practice))){
        			Intent i = new Intent();
					i.setClass(pgntool.this, FileListView.class);
					i.putExtra(EXTRA_MODE, MODE_IMPORT_PRACTICE);
					pgntool.this.startActivity(i);
        		} else if(arrString[arg2].equals(getString(R.string.pgntool_create_practice_explanation))){
        			Intent i = new Intent();
					i.setClass(pgntool.this, FileListView.class);
					i.putExtra(EXTRA_MODE, MODE_CREATE_PRACTICE);
					pgntool.this.startActivity(i);
        		} else if(arrString[arg2].equals(getString(R.string.pgntool_import_puzzle))){
        			Intent i = new Intent();
					i.setClass(pgntool.this, FileListView.class);
					i.putExtra(EXTRA_MODE, MODE_IMPORT_PUZZLE);
					pgntool.this.startActivity(i);
        		}
        		 else if(arrString[arg2].equals(getString(R.string.pgntool_import_opening))){
         			Intent i = new Intent();
 					i.setClass(pgntool.this, FileListView.class);
 					i.putExtra(EXTRA_MODE, MODE_IMPORT_OPENINGDATABASE);
 					pgntool.this.startActivity(i);
         		}
				// 
        	}
		});
    }
    
    public void doExport(){
    	try {
    		if(Environment.getExternalStorageState().equals(Environment.MEDIA_MOUNTED)){
	    		
				String sFile = Environment.getExternalStorageDirectory() + "/chess.pgn";
				int i = 0;
				Cursor cursor = managedQuery(MyPGNProvider.CONTENT_URI, PGNColumns.COLUMNS, null, null, PGNColumns.DEFAULT_SORT_ORDER);
				if(cursor != null){
					
					if(cursor.getCount() > 0){
		
						cursor.moveToFirst();
						String s = "";
						
						while(cursor.isAfterLast() == false){
							
							s += cursor.getString(cursor.getColumnIndex(PGNColumns.PGN)) + "\n\n\n";
							cursor.moveToNext();
							i++;
						}
						
						FileOutputStream fos;
						fos = new FileOutputStream(sFile);
						fos.write(s.getBytes());
						fos.flush();
						fos.close();
					}
				}
				doToast(String.format(getString(R.string.pgntool_numexport), i));

	    	} else {
	    		doToast(getString(R.string.err_sd_not_mounted));
	    	}
    	} catch (Exception e) {
			
			//doToast(getString(R.string.err_send_email));
			Log.e("ex", e.toString());
			return;
		} 
    }
    public void doToast(String s){
    	Toast t = Toast.makeText(this, s, Toast.LENGTH_LONG);
		t.setGravity(Gravity.CENTER, 0, 0);
		t.show();
    }
}