package jwtc.android.chess.tools;

import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Date;
import java.util.Iterator;
import java.util.TreeSet;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import jwtc.android.chess.MyBaseActivity;
import jwtc.android.chess.R;
import jwtc.android.chess.puzzle.MyPuzzleProvider;
import jwtc.android.chess.puzzle.practice;
import jwtc.chess.GameControl;
import jwtc.chess.JNI;
import jwtc.chess.PGNColumns;
import jwtc.chess.PGNEntry;
import jwtc.chess.algorithm.UCIWrapper;
import jwtc.chess.board.ChessBoard;
import android.app.Activity;
import android.app.AlertDialog;
import android.content.ContentResolver;
import android.content.ContentValues;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.SharedPreferences;
import android.database.Cursor;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.Message;
import android.text.ClipboardManager;
import android.util.Log;
import android.view.Gravity;
import android.view.MenuItem;
import android.view.View;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

public class importactivity extends MyBaseActivity {
	
	private int _cnt, _untilPly, _cntFail;
	private GameControl _gameControl;
	private JNI _jni;
	private TextView _tvWork, _tvWorkCnt, _tvWorkCntFail;
	private ProgressBar _progress;
	private PGNProcessor _processor;
	private String _mode = null;
	
	protected TreeSet<Long> _arrKeys;
	protected String _outFile;
	protected boolean _processing;
	
	private final String TAG = "importactivity";
	
    /** Called when the activity is first created. */
    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        
        setContentView(R.layout.doimport);

		this.makeActionOverflowMenuShown();

        _processing = false;
        _gameControl = new GameControl();
        
        _jni = _gameControl.getJNI();
		_untilPly = 17;
		
		_arrKeys = new TreeSet<Long>();
		
        _tvWork = (TextView)findViewById(R.id.TextViewDoImport);
        _tvWorkCnt = (TextView)findViewById(R.id.TextViewDoImportCnt);
        _tvWorkCntFail = (TextView)findViewById(R.id.TextViewDoImportCntFail);
        _progress = (ProgressBar)findViewById(R.id.ProgressDoImport);
        
        _progress.setVisibility(View.INVISIBLE);
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

    @Override
    protected void onPause() {
        super.onPause();
        
    }
    
    @Override
    protected void onResume() {
        super.onResume();
        
        if(_processing == false){
	        final Intent intent = getIntent();
	        final Uri uri = intent.getData();
	        
	        Bundle extras =intent.getExtras(); 
	        if(extras != null){
	        	String type = extras.getString(pgntool.EXTRA_MODE); 
	        	if(type != null){
	        		_mode = type;
	        	}
	        }
	        if(_mode == null)
	        {
	        	if(uri == null){
	        		finish();
	        		return;
	        	}
	        	_mode = pgntool.MODE_IMPORT; // by default
	        }
	        _cnt = 0;
	        _cntFail = 0;
	        
	        
	        if(_mode.equals(pgntool.MODE_IMPORT)){
	        	_processor = new PGNImportProcessor();
	            _processor.m_threadUpdateHandler = new Handler(){
	                /** Gets called on every message that is received */
	                // @Override
	                public void handleMessage(Message msg) {
	                	
	                	if(msg.what == PGNProcessor.MSG_PROCESSED_PGN){
	                		_cnt++;
	                		_tvWorkCnt.setText("Processed " + _cnt);
	                	} else if(msg.what == PGNProcessor.MSG_FAILED_PGN){
	                		_cntFail++;
	                		_tvWorkCntFail.setText("Failed " + _cntFail);
	                	} else if(msg.what == PGNProcessor.MSG_FINISHED){
	                		_progress.setVisibility(View.INVISIBLE);
	                		_tvWorkCnt.setText("Imported " + _cnt + " games");
	                	} else if(msg.what == PGNProcessor.MSG_FATAL_ERROR){
	                		_progress.setVisibility(View.INVISIBLE);
	                		_tvWorkCnt.setText("An error occured, import failed");
	                	}
	                }
	           }; 
	        } else if(_mode.equals(pgntool.MODE_DB_IMPORT)){
	        	
	        	_arrKeys.clear();
	        	
	        	_processor = new PGNDbProcessor();
	            _processor.m_threadUpdateHandler = new Handler(){
	                /** Gets called on every message that is received */
	                // @Override
	                public void handleMessage(Message msg) {
	                	
	                	if(msg.what == PGNProcessor.MSG_PROCESSED_PGN){
	                		_cnt++;
	                		_tvWorkCnt.setText("Processed " + _cnt);
	                	} else if(msg.what == PGNProcessor.MSG_FAILED_PGN){
	                		_cntFail++;
	                		_tvWorkCntFail.setText("Failed " + _cntFail);
	                	} else if(msg.what == PGNProcessor.MSG_FINISHED){
	                		writeHashKeysToFile();
	                		_progress.setVisibility(View.INVISIBLE);
	                		_tvWorkCnt.setText("Imported " + _cnt + " games; " + _arrKeys.size() + " positions.");
	                		_processing = false;
	                	} else if(msg.what == PGNProcessor.MSG_FATAL_ERROR){
	                		_progress.setVisibility(View.INVISIBLE);
	                		_tvWorkCnt.setText("An error occured, import failed");
	                		_processing = false;
	                	}
	                }
	           }; 
	        	
	        } else if(_mode.equals(pgntool.MODE_CREATE_PRACTICE)){ 
	        	_arrKeys.clear();
	        	
	        	Log.i(TAG, "Create practice set");
	        	
	        	getContentResolver().delete(MyPuzzleProvider.CONTENT_URI_PRACTICES, "1=1", null);
	        	Log.i(TAG, "Deleted practices");
	        	
	        	_processor = new PracticeImportProcessor();
	        	_processor.m_threadUpdateHandler = new Handler(){
	                /** Gets called on every message that is received */
	                // @Override
	                public void handleMessage(Message msg) {
	                	
	                	if(msg.what == PGNProcessor.MSG_PROCESSED_PGN){
	                		_cnt++;
	                		_tvWorkCnt.setText("Processed " + _cnt);
	                	} else if(msg.what == PGNProcessor.MSG_FAILED_PGN){
	                		_cntFail++;
	                		_tvWorkCntFail.setText("Failed " + _cntFail);
	                	} else if(msg.what == PGNProcessor.MSG_FINISHED){
	                		
	                		_progress.setVisibility(View.INVISIBLE);
	                		_tvWorkCnt.setText("Imported " + _cnt + " practice positions");
	                		_processing = false;
	                	} else if(msg.what == PGNProcessor.MSG_FATAL_ERROR){
	                		_progress.setVisibility(View.INVISIBLE);
	                		_tvWorkCnt.setText("An error occured, import failed");
	                		_processing = false;
	                	}
	                }
	           }; 
	        
	        } else if(_mode.equals(pgntool.MODE_DB_POINT)){
	        	
	        	
	            try {
	            	
	            	if(uri != null){
	            	
	            		SharedPreferences.Editor editor = getSharedPreferences("ChessPlayer", MODE_PRIVATE).edit();
	            		
	            		editor.putString("OpeningDb", uri.toString());
	            		
	            		editor.commit();
	            		
	            		
	            		doToast("Openingdatabase: " + uri.toString());
	            	//String outFilename = "/data/data/" + getPackageName() + "/db.bin";
	            	//_gameControl.loadDB(getAssets().open("db.bin"), outFilename, 17);
	            	
	            	}
	    		} catch (Exception e) {
	    			
	    		}
	            
	        	finish();
	        	return;
	        }
	        else if(_mode.equals(pgntool.MODE_UCI_INSTALL)){
	        	
	        	String sPath = uri.getPath();
				String sEngine = uri.getLastPathSegment(); //"robbolito-android"; //bikjump2.1    stockfish2.0
				
				Log.i(TAG, "Install UCI " + sPath + " as " + sEngine);
				
				try {
					FileInputStream fis = new FileInputStream(sPath);
					UCIWrapper.install(fis, sEngine);

					SharedPreferences.Editor editor = getSharedPreferences("ChessPlayer", MODE_PRIVATE).edit();
					editor.putString("UCIEngine", sEngine);
					editor.commit();

					doToast(String.format(getString(R.string.pgntool_uci_engine_success), sEngine));

				} catch (IOException e) {

					doToast(getString(R.string.pgntool_uci_engine_error));
					// TODO Auto-generated catch block
					e.printStackTrace();
				}
	        	
	        	
	        	finish();
	        	return;
	        }
	        else if(_mode.equals(pgntool.MODE_IMPORT_PRACTICE)){
	        	
	        	String sPath = uri.getPath();
	        	Log.i(TAG, "Import practice " + sPath);
				
				try {
					SharedPreferences prefs = getSharedPreferences("ChessPlayer", MODE_PRIVATE);
					SharedPreferences.Editor editor = prefs.edit();
					editor.putInt("practicePos", 0);
					editor.putInt("practiceTicks", 0);
					editor.commit();
					
					getContentResolver().delete(MyPuzzleProvider.CONTENT_URI_PRACTICES, "1=1", null);
					
					Log.i(TAG, "Deleted practices");
					
					Intent practiceIntent = new Intent();
					practiceIntent.setClass(this, practice.class);
					practiceIntent.setData(uri);
					
					doToast("Practice set was copied");
					
					startActivity(practiceIntent);
					
				} catch (Exception e) {
					
					doToast("An error occured, could not copy practice set");
					// TODO Auto-generated catch block
					e.printStackTrace();
				}
	        	
	        	finish();
	        	return;
	        }
	        else if(_mode.equals(pgntool.MODE_IMPORT_PUZZLE)){
	        	
	        	SharedPreferences prefs = getSharedPreferences("ChessPlayer", MODE_PRIVATE);
				SharedPreferences.Editor editor = prefs.edit();
				editor.putInt("puzzlePos", 0);
				editor.commit();
	        	
	        	_processor = new PuzzleImportProcessor();
	        	_processor.m_threadUpdateHandler = new Handler(){
	                /** Gets called on every message that is received */
	                // @Override
	                public void handleMessage(Message msg) {
	                	
	                	if(msg.what == PGNProcessor.MSG_PROCESSED_PGN){
	                		_cnt++;
	                		_tvWorkCnt.setText("Processed " + _cnt);
	                	} else if(msg.what == PGNProcessor.MSG_FAILED_PGN){
	                		_cntFail++;
	                		_tvWorkCntFail.setText("Failed " + _cntFail);
	                	} else if(msg.what == PGNProcessor.MSG_FINISHED){
	                		
	                		_progress.setVisibility(View.INVISIBLE);
	                		_tvWorkCnt.setText("Imported " + _cnt + " puzzle positions");
	                		_processing = false;
	                	} else if(msg.what == PGNProcessor.MSG_FATAL_ERROR){
	                		_progress.setVisibility(View.INVISIBLE);
	                		_tvWorkCnt.setText("An error occured, import failed");
	                		_processing = false;
	                	}
	                }
	           }; 
	        } else if(_mode.equals(pgntool.MODE_IMPORT_OPENINGDATABASE)){
	        	_processor = new OpeningImportProcessor();
	        	_processor.m_threadUpdateHandler = new Handler(){
	                /** Gets called on every message that is received */
	                // @Override
	                public void handleMessage(Message msg) {
	                	
	                	if(msg.what == PGNProcessor.MSG_PROCESSED_PGN){
	                		_cnt++;
	                		_tvWorkCnt.setText("Processed " + _cnt);
	                	} else if(msg.what == PGNProcessor.MSG_FAILED_PGN){
	                		_cntFail++;
	                		_tvWorkCntFail.setText("Failed " + _cntFail);
	                	} else if(msg.what == PGNProcessor.MSG_FINISHED){
	                		
	                		_progress.setVisibility(View.INVISIBLE);
	                		_tvWorkCnt.setText("Imported " + _cnt + " openings");
	                		_processing = false;
	                		
	                		ClipboardManager cm = (ClipboardManager) getSystemService(CLIPBOARD_SERVICE);
	                    	cm.setText(_processor.getString());
	                		//Log.i("DONE_OPENING", _processor.getString());
	                		
	                	} else if(msg.what == PGNProcessor.MSG_FATAL_ERROR){
	                		_progress.setVisibility(View.INVISIBLE);
	                		_tvWorkCnt.setText("An error occured, import failed");
	                		_processing = false;
	                	}
	                }
	           };
	        }
	        else  {

	        	finish();
	        	return;
	        }
	
	        if(uri != null){
	        	
	        	AlertDialog.Builder builder = new AlertDialog.Builder(this);
				builder.setTitle("Import " + uri.getLastPathSegment() + "?");
				
				builder.setPositiveButton(getString(R.string.alert_yes), new DialogInterface.OnClickListener(){
	
					public void onClick(DialogInterface dialog, int which) {
						
						dialog.dismiss();
		        	
						_progress.setVisibility(View.VISIBLE);
			        	_tvWork.setText("Importing " + uri.toString());
			        	
						try {
							
							InputStream is = getContentResolver().openInputStream(uri);
							
							if(uri.getPath().lastIndexOf(".zip") > 0){
	
								_outFile = uri.getPath().replace(".zip", ".bin"); 
								_processor.processZipFile(is);
							} else {
								_outFile = uri.getPath().replace(".pgn", ".bin");
								_processor.processPGNFile(is);
							}
		
							_processing = true;
							
							
						} catch(Exception ex){
							
							Log.e("import", ex.toString());
						}
						
					}
				});
				
				builder.setNegativeButton(getString(R.string.alert_no), new DialogInterface.OnClickListener(){
					public void onClick(DialogInterface dialog, int which) {
						dialog.dismiss();
						finish();
					}
				});
				
				AlertDialog alert = builder.create();
				alert.show();
	        }
        }
    }
    
    public void doToast(String s){
    	Toast t = Toast.makeText(this, s, Toast.LENGTH_LONG);
		t.setGravity(Gravity.CENTER, 0, 0);
		t.show();
    }
   
    public void readDB(InputStream isDB){
    	Log.i("import", "readDB executing");
		_arrKeys.clear();
		long l;
		int len; byte[] bytes = new byte[8];
		try {
			while((len = isDB.read(bytes, 0, bytes.length)) != -1){
				l = 0L;
				l |= (long)bytes[0] << 56;
				l |= (long)bytes[1] << 48;
				l |= (long)bytes[2] << 40;
				l |= (long)bytes[3] << 32;
				l |= (long)bytes[4] << 24;
				l |= (long)bytes[5] << 16;
				l |= (long)bytes[6] << 8;
				l |= (long)bytes[7];
				
				// assume file keys are allready unique
				
				_arrKeys.add(l);
			}
		} catch (IOException e) {
			Log.e("import", "readDB: " + e.toString());
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
		
	}
    public void writeHashKeysToFile(){
		FileOutputStream fos;
		try {
			fos = new FileOutputStream(_outFile);
			long l; 
			byte[] bytes = new byte[8];
			
			/*
			Collections.sort(_arrKeys,  new Comparator<Long>() {
		        public int compare(Long arg0, Long arg1) {
		        	long x = (long) arg0;
			    	long y = (long) arg1;
			    	if(x > y) {
			    		return 1;
			    	} else if(x == y) {
			    		return 0;
			    	} else {
			    		return -1;
			    	}
		        	
		        }
		    });
			*/
			
			Iterator<Long> it = _arrKeys.iterator();
			while(it.hasNext()){
				
				l = it.next();
				
				if(l == 0)
					break;
			
				bytes[0] = (byte)(l >>> 56);
				bytes[1] = (byte)(l >>> 48);
				bytes[2] = (byte)(l >>> 40);
				bytes[3] = (byte)(l >>> 32);
				bytes[4] = (byte)(l >>> 24);
				bytes[5] = (byte)(l >>> 16);
				bytes[6] = (byte)(l >>> 8);
				bytes[7] = (byte)(l);
				
				fos.write(bytes);
				
				//co.pl("" + l + "{" + bytes[0] + ", " + bytes[1] + ", " + bytes[2] + ", " + bytes[3] + ", " + bytes[4] + ", " + bytes[5] + ", " + bytes[6] + ", " + bytes[7] + "}");
				
				//Log.i("writeHashKeys", "long " + l);
				//break;
			}
			
			fos.flush();
			fos.close();
			Log.i("import", "wrote hash keys to " + _outFile);
		
		} catch (Exception e) {
			// TODO Auto-generated catch block
			Log.e("import", "writeHashkeys: " + e.toString());
			e.printStackTrace();
		}
	}
    
    
    private class PGNImportProcessor extends PGNProcessor{

    	public PGNImportProcessor(){
    		
    	}
		@Override
		public synchronized boolean processPGN(final String sPGN) {
			
			//Log.i("processPGN", sPGN);
	    	if(_gameControl.loadPGN(sPGN)){
		    	
		    	ContentValues values = new ContentValues();
		    	values.put(PGNColumns.EVENT, _gameControl.getPGNHeadProperty("Event"));
		    	values.put(PGNColumns.WHITE, _gameControl.getWhite());
		    	values.put(PGNColumns.BLACK, _gameControl.getBlack());
		    	values.put(PGNColumns.PGN, _gameControl.exportFullPGN());
		    	values.put(PGNColumns.RATING, 2.5F);
		    	
		    	// todo date goes wrong #################################
		    	Date dd = _gameControl.getDate();
		    	if(dd == null)
		    		dd = Calendar.getInstance().getTime();
		    	
		    	values.put(PGNColumns.DATE, dd.getTime());
		    	
		    	Uri uri = Uri.parse("content://jwtc.android.chess.MyPGNProvider/games");
		     	Uri uriInsert = getContentResolver().insert(uri, values);
		     	return true;
	    	}
	    	return false;
		}
		@Override
		public String getString() {
			// TODO Auto-generated method stub
			return null;
		}
    	
    }
    
    private class PGNDbProcessor extends PGNProcessor{

		public PGNDbProcessor(){
			
		}
		
		@Override
		public synchronized boolean processPGN(final String sPGN) {

			long lKey;
			//Log.i("import", "processPGN:" + sPGN);
			if(_gameControl.loadPGN(sPGN)){
				int ply = 0, pgnSize = _gameControl.getArrPGNSize();
				int existingCnt = 0;
				//Log.i("import", "processPGN - gameSize:" + pgnSize);
				while(ply <= pgnSize && ply <= _untilPly){
					//_jni.getNumBoard();
					_gameControl.jumptoMove(ply);
					
					lKey = _jni.getHashKey();
					
					if(false == _arrKeys.contains(lKey)){
						_arrKeys.add(lKey);
					} else {
						existingCnt++;
					}
					ply++;
				}
				//Log.i("import", "processPGN - existing keys: " + existingCnt);
				return true;
			}
			return false;
		}

		@Override
		public String getString() {
			// TODO Auto-generated method stub
			return null;
		}
		
	}
    
    private class PracticeImportProcessor extends PGNProcessor{

    	public PracticeImportProcessor(){
    		
    	}
		@Override
		public synchronized boolean processPGN(final String sPGN) {
			
			//Log.i("processPGN", sPGN);
	    	if(_gameControl.loadPGN(sPGN)){
		    	
		    	if(_jni.getState() == ChessBoard.MATE){
		    	
		    		int startExport = _gameControl.getArrPGNSize();
		    				    		
		    		int plies = 2, undos = 0, moves = 0;
		    		String s = "";
		    		String[] arrMoves = {
		    				_gameControl.exportMovesPGNFromPly(startExport),
		    				"", // not interested in move from opponent
		    				_gameControl.exportMovesPGNFromPly(startExport - 3),
		    		};
		    		
		    		if(startExport >= 3){ // at least 3 half moves for a 2 move mate
		    		
			    		while(plies <= 4){
			    			undos = 0;
				    		while(undos <= moves){ // undo one extra
				    			//Log.i(TAG, "undo");
				    			_gameControl.undo();
				    			undos++;
				    		}
				    		String sFEN = _jni.toFEN();
			    			
				    		_jni.searchDepth(plies);
				    		
				    		int move = _jni.getMove();
				    		int value = _jni.peekSearchBestValue();
	
				    		//Log.i(TAG, moves + ", Move " + Move.toDbgString(move) + " val: " + value + " at plies " + plies);
				    		
				    		if(value == 100000 * (plies % 2 == 0 ? 1 : -1) && _jni.move(move) != 0)
				    		{
					    		_gameControl.addPGNEntry(_jni.getNumBoard()-1, _jni.getMyMoveToString(), "", _jni.getMyMove(), true);
					    		
					    		// save when it's our move
					    		if(plies % 2 == 0){
					    			if(plies == 4){
					    				Log.i(TAG, "YESS");
					    			}
					    			s = "[FEN \"" + sFEN + "\"]\n" + arrMoves[moves];
					    		}
					    		moves++;
					    		plies++; 
					    		
				    		} else {
				    			//Log.i(TAG, "Stop at plies " + plies);
				    			break;
				    		}
			    		}
			    		
			    		if(s.length() > 0){
				    		try{
					    		
					    		Cursor cursor = managedQuery(MyPuzzleProvider.CONTENT_URI_PRACTICES, MyPuzzleProvider.COLUMNS, PGNColumns.PGN + "=?", new String[]{s}, "");
					    		if(cursor != null){
					    			if(cursor.getCount() > 0){
					    				//Log.i(TAG, "DOUBLE!");
					    				return false;
					    			}
					    			cursor.close();
					    		}
					    		//Log.i(TAG, "SAVING: " + s);
					    	
					    		ContentValues values = new ContentValues();
					    		values.put(PGNColumns.PGN, s);
					    	
					    		Uri uri = MyPuzzleProvider.CONTENT_URI_PRACTICES;
					     		Uri uriInsert = getContentResolver().insert(uri, values);
					    		
					    		return true;
				    		} catch(Exception ex){
				    			Log.e(TAG, ex.toString());
				    			return false;
				    		}
			    		}
		    		}
		    	}
	    	}
	    	return false;
		}
		@Override
		public String getString() {
			// TODO Auto-generated method stub
			return null;
		}
    	
    }
    
    private class PuzzleImportProcessor extends PGNProcessor{

    	private ContentResolver _cr;
    	
    	public PuzzleImportProcessor(){
    		_cr = importactivity.this.getContentResolver();
    		_cr.delete(MyPuzzleProvider.CONTENT_URI_PUZZLES, "1=1", null);
    		
    		Log.i(TAG, "Created puzzle import instance, deleted puzzles");
    	}
		@Override
		public synchronized boolean processPGN(final String sPGN) {
			
			 ContentValues values;
			 values = new ContentValues();
			 values.put("PGN", sPGN);
			 _cr.insert(MyPuzzleProvider.CONTENT_URI_PUZZLES, values);
			
			return true;
		}
		@Override
		public String getString() {
			// TODO Auto-generated method stub
			return null;
		}
    }
    
    private class OpeningImportProcessor extends PGNProcessor{

    	public JSONArray _jArray;
    	ArrayList<PGNEntry> _arrMoves;
    	String _sECO 		= _gameControl.getPGNHeadProperty("Event");
		String _sName 		= _gameControl.getPGNHeadProperty("White");
		String _sVariation 	= _gameControl.getPGNHeadProperty("Black");
    	
    	public OpeningImportProcessor(){
    		_jArray = new JSONArray();
    	}
    	
		@Override
		public boolean processPGN(String sPGN) {
			if(_gameControl.loadPGN(sPGN)){
				_sECO 		= _gameControl.getPGNHeadProperty("Event");
				_sName 		= _gameControl.getPGNHeadProperty("White");
				_sVariation = _gameControl.getPGNHeadProperty("Black");
				if(_sVariation.equals("black ?")){
					_sVariation = "";
				}
				_arrMoves 	= _gameControl.getPGNEntries();
				
				findOrInsertEntry(_jArray, _arrMoves.remove(0));
				
				return true;
			}
			return false;
		}
    	
		
		// m = move
		// a = array
		// e = ECO
		// v = variation
		protected void findOrInsertEntry(JSONArray curArray, PGNEntry entry){
			boolean bFound = false;
			for(int i = 0; i < curArray.length(); i++){
				try {
					JSONObject jObj = (JSONObject)curArray.get(i);
					
					if(jObj.getString("m").equals(entry._sMove)){
						
						bFound = true;
						JSONArray newArray;
						if(jObj.has("a")){
							newArray = (JSONArray)jObj.get("a");
						} else {
							newArray = new JSONArray();
							jObj.put("a", newArray);
						}
						
						if(_arrMoves.size() == 0){
							jObj.put("e", _sECO);
							jObj.put("n", _sName);
							jObj.put("v", _sVariation);
						} else {
							findOrInsertEntry(newArray, _arrMoves.remove(0));
						}
						
					} 
					
				} catch (JSONException e) {
					
				}
			
			}
			if(false == bFound){
				JSONObject newObject = new JSONObject();
				try {
					JSONArray newArray = new JSONArray();
					
					newObject.put("m", entry._sMove);
					newObject.put("a", newArray);
					
					if(_arrMoves.size() == 0){
						newObject.put("e", _sECO);
						newObject.put("n", _sName);
						newObject.put("v", _sVariation);
					}
					
					curArray.put(newObject);
					
					if(_arrMoves.size() > 0){
						findOrInsertEntry(newArray, _arrMoves.remove(0));
					}
					
				} catch (JSONException e) {
				}
			}
		}

		@Override
		public String getString() {
			return _jArray.toString();
		}
    }
   
}