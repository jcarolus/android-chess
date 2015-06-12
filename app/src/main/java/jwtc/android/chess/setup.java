package jwtc.android.chess;

import java.io.InputStream;

import jwtc.chess.*;
import jwtc.chess.board.BoardConstants;
import jwtc.chess.board.ChessBoard;
import android.app.AlertDialog;
import android.app.Dialog;
import android.content.pm.ActivityInfo;
import android.graphics.Rect;
import android.net.Uri;
import android.os.Bundle;
import android.util.DisplayMetrics;
import android.util.Log;
import android.view.*;
import android.view.View.OnClickListener;
import android.widget.*;
import android.widget.TableRow.LayoutParams;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.SharedPreferences;

public class setup extends MyBaseActivity {
	private ChessViewBase _view;
	private JNI _jni;
	private int _selectedColor;
	private int _selectedPiece;
	private int _selectedPosition;
	
	private ChessImageView[] _arrSelImages;
	//private static Bitmap[] _arrBmp;
	private ImageButton _butDel;
	private ImageButton _butColor;
	private Button _butOk, _butCancel;
	//private TextView _tvMsg;
	//private ImageView _butOk;
	//private ImageButton _butMenu;
		
	private int _iTurn, _iEPFile;
	private boolean _bWhiteCastleShort, _bWhiteCastleLong, _bBlackCastleShort, _bBlackCastleLong;
	private SetupOptionsDlg _dlg;
	
	private Uri _uri;
	
	private final int SEL_COLOR = 5;
	//private final int SEL_DEL = 7;
	private final int SELBUTTONS_COUNT = 5; // 8
	
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
        if(getResources().getBoolean(R.bool.portraitOnly)){
            setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_PORTRAIT);
        }

        setContentView(R.layout.setup);
        _view = new ChessViewBase(this); 
        
        _jni = new JNI();

        final Intent intent = getIntent();
        _uri = intent.getData();
        
        _selectedPiece = BoardConstants.PAWN;
        _selectedColor = BoardConstants.WHITE;
        _selectedPosition = -1;
        
        _iTurn = ChessBoard.WHITE;
        _iEPFile = -1;
    	_bWhiteCastleShort = false;
    	_bWhiteCastleLong = false;
    	_bBlackCastleShort = false;
    	_bBlackCastleLong = false;
        
        resetBoard();
        
        OnClickListener ocl = new OnClickListener() {
        	public void onClick(View arg0) {
        		handleClick(_view.getIndexOfButton(arg0));
        	}
		};
		_view.init(ocl);
        
		//_tvMsg = (TextView)findViewById(R.id.TextViewSetupMsg);
		
		_butCancel = (Button)findViewById(R.id.ButtonSetupCancel);
		_butCancel.setOnClickListener(new OnClickListener() {
        	public void onClick(View arg0) {

        		SharedPreferences.Editor editor = getSharedPreferences("ChessPlayer", MODE_PRIVATE).edit();
        	    editor.putString("FEN", null);
        	    editor.commit();
        		
        		finish();
        	}
    	});
		
		
		_butOk = (Button)findViewById(R.id.ButtonSetupOk);
		_butOk.setOnClickListener(new OnClickListener() {
        	public void onClick(View arg0) {

        		saveAndFinish();
        	}
    	});
		
		_arrSelImages = new ChessImageView[SELBUTTONS_COUNT];
		// boardconstants range from 0-5, king is not included
		//_arrSelImages[BoardConstants.KING] = null;//new ChessImageView(this); 
		_arrSelImages[BoardConstants.QUEEN] = (ChessImageView)findViewById(R.id.selQueen);
		_arrSelImages[BoardConstants.ROOK] = (ChessImageView)findViewById(R.id.selRook);
		_arrSelImages[BoardConstants.BISHOP] = (ChessImageView)findViewById(R.id.selBishop);
		_arrSelImages[BoardConstants.KNIGHT] = (ChessImageView)findViewById(R.id.selKnight);
		_arrSelImages[BoardConstants.PAWN] = (ChessImageView)findViewById(R.id.selPawn);
		_butColor = (ImageButton)findViewById(R.id.selColor);
		
		ImageCacheObject tmpCache = new ImageCacheObject();
		tmpCache._color = ChessBoard.WHITE;
		tmpCache._piece = ChessBoard.QUEEN;
		tmpCache._fieldColor = ChessBoard.BLACK;
		tmpCache._bPiece = true;
		tmpCache._selected = false;
		tmpCache._selectedPos = false;
		_arrSelImages[BoardConstants.QUEEN].setICO(tmpCache);
		
		tmpCache = new ImageCacheObject();
		tmpCache._color = ChessBoard.WHITE;
		tmpCache._piece = ChessBoard.ROOK;
		tmpCache._fieldColor = ChessBoard.BLACK;
		tmpCache._bPiece = true;
		tmpCache._selected = false;
		tmpCache._selectedPos = false;
		_arrSelImages[BoardConstants.ROOK].setICO(tmpCache);
		
		tmpCache = new ImageCacheObject();
		tmpCache._color = ChessBoard.WHITE;
		tmpCache._piece = ChessBoard.BISHOP;
		tmpCache._fieldColor = ChessBoard.BLACK;
		tmpCache._bPiece = true;
		tmpCache._selected = false;
		tmpCache._selectedPos = false;
		_arrSelImages[BoardConstants.BISHOP].setICO(tmpCache);
		
		tmpCache = new ImageCacheObject();
		tmpCache._color = ChessBoard.WHITE;
		tmpCache._piece = ChessBoard.KNIGHT;
		tmpCache._fieldColor = ChessBoard.BLACK;
		tmpCache._bPiece = true;
		tmpCache._selected = false;
		tmpCache._selectedPos = false;
		_arrSelImages[BoardConstants.KNIGHT].setICO(tmpCache);
		
		tmpCache = new ImageCacheObject();
		tmpCache._color = ChessBoard.WHITE;
		tmpCache._piece = ChessBoard.PAWN;
		tmpCache._fieldColor = ChessBoard.BLACK;
		tmpCache._bPiece = true;
		tmpCache._selected = true; // pawn is selected by default
		tmpCache._selectedPos = false;
		_arrSelImages[BoardConstants.PAWN].setICO(tmpCache);
		
		_butDel = (ImageButton)findViewById(R.id.delPiece);
		_butDel.setOnClickListener(new OnClickListener() {
        	public void onClick(View arg0) {

        		if(_selectedPosition != -1){
	        		if(BoardConstants.FIELD != _jni.pieceAt(BoardConstants.WHITE, _selectedPosition) && 
            			BoardConstants.KING != _jni.pieceAt(BoardConstants.WHITE, _selectedPosition)){
            			_jni.removePiece(BoardConstants.WHITE, _selectedPosition);
            		}
            		if(BoardConstants.FIELD != _jni.pieceAt(BoardConstants.BLACK, _selectedPosition) && 
            			BoardConstants.KING != _jni.pieceAt(BoardConstants.BLACK, _selectedPosition)){
            			_jni.removePiece(BoardConstants.BLACK, _selectedPosition);
            		}
            		_selectedPosition = -1;
            		paintBoard();
        		}
        		
        	}
    	});
		
		_butColor.setOnClickListener(new OnClickListener(){

			@Override
			public void onClick(View v) {
				/*
				_butColor._fieldColor = _selectedColor;
				_butColor.invalidate();
	    		*/
				_selectedColor = _selectedColor == ChessBoard.WHITE ? ChessBoard.BLACK : ChessBoard.WHITE;
				
				ImageCacheObject tmpCache;
	    		for(int i = 0; i < 5; i++){
	    			tmpCache = _arrSelImages[i].getICO();
	    			tmpCache._color = _selectedColor;
	    			_arrSelImages[i].invalidate();
	    		}
			}
			
		});
		
		
		ocl = new OnClickListener() {
        	public void onClick(View arg0) {
        		for(int i = 0; i < SELBUTTONS_COUNT; i++){
        			if(_arrSelImages[i] == (ChessImageView)arg0)
        				handleSelectClick(i);
        		}
        		
        	}
		};
		for(int i = 0; i < SELBUTTONS_COUNT; i++){
			_arrSelImages[i].setOnClickListener(ocl);
		}
		
		_dlg = new SetupOptionsDlg();
		
		paintBoard();
				
		setResult(RESULT_OK);
    }
    
    
    public boolean onCreateOptionsMenu(Menu menu){
    	
    	MenuItem item1;
        
	    item1 = menu.add(getString(R.string.menu_options));
	    item1.setIcon(R.drawable.action_settings);

	    item1 = menu.add(getString(R.string.menu_help));
	    item1.setIcon(R.drawable.action_help);
	        	    
	    item1 = menu.add(getString(R.string.menu_clear));
	    item1.setIcon(R.drawable.navigation_cancel);
	    
    	return true;
    }
    
    public boolean onOptionsItemSelected(MenuItem  item){
    	 if(item.getTitle().equals(getString(R.string.menu_options))) {
    		_dlg.show();
    		 return true;
    	 } else if(item.getTitle().equals(getString(R.string.menu_help))) {
    		 Intent i = new Intent();
    		 i.setClass(setup.this, HtmlActivity.class);
    		 i.putExtra(HtmlActivity.HELP_MODE, "help_setup");
    		 startActivity(i); 
    		 return true;
    	 }else if(item.getTitle().equals(getString(R.string.menu_clear))) {
    		 resetBoard();
				paintBoard();
    		 return true;
    	 }	 
    	return false;
    }
    
    
    
    public void resetBoard(){
    	_jni.reset();
    	
         _jni.putPiece(BoardConstants.e1, BoardConstants.KING, BoardConstants.WHITE);
         _jni.putPiece(BoardConstants.e8, BoardConstants.KING, BoardConstants.BLACK);
    }
    public void paintBoard(){
    	int[] arrSel = new int[1];
    	arrSel[0] = _selectedPosition;
    	//TODO
    	_view.paintBoard(_jni, arrSel, null);

    	
    	
    	/*
    	_arrSelImages[BoardConstants.QUEEN].setImageResource(_selectedPiece == BoardConstants.QUEEN ? (_selectedColor == BoardConstants.WHITE ? R.drawable.wqs : R.drawable.bqs) : (_selectedColor == BoardConstants.WHITE ? R.drawable.wqw : R.drawable.bqw));
		_arrSelImages[BoardConstants.ROOK].setImageResource(_selectedPiece == BoardConstants.ROOK ? (_selectedColor == BoardConstants.WHITE ? R.drawable.wrs : R.drawable.brs) : (_selectedColor == BoardConstants.WHITE ? R.drawable.wrw : R.drawable.brw));
		_arrSelImages[BoardConstants.BISHOP].setImageResource(_selectedPiece == BoardConstants.BISHOP? (_selectedColor == BoardConstants.WHITE ? R.drawable.wbs : R.drawable.bbs) : (_selectedColor == BoardConstants.WHITE ? R.drawable.wbw : R.drawable.bbw));
		_arrSelImages[BoardConstants.KNIGHT].setImageResource(_selectedPiece == BoardConstants.KNIGHT ? (_selectedColor == BoardConstants.WHITE ? R.drawable.wns : R.drawable.bns) : (_selectedColor == BoardConstants.WHITE ? R.drawable.wnw : R.drawable.bnw));
		_arrSelImages[BoardConstants.PAWN].setImageResource(_selectedPiece == BoardConstants.PAWN ? (_selectedColor == BoardConstants.WHITE ? R.drawable.wps : R.drawable.bps) : (_selectedColor == BoardConstants.WHITE ? R.drawable.wpw : R.drawable.bpw));
		
		_arrSelImages[SEL_COLOR].setImageResource(_selectedColor == BoardConstants.WHITE ? R.drawable.b : R.drawable.w);
		*/
		//_arrSelImages[SEL_DEL].setSelected(_selectedPiece != -1);
		
		//_tvMsg.setText("v: " + _jni.getBoardValue());
    }
    
    public void handleClick(int index){
    	index = _view.getFieldIndex(index);
    	
    	if(_selectedPosition == -1 && _selectedPiece >= 0 && _selectedColor >= 0){
    		//ChessBoard board = _board.duplicate();
    		if(BoardConstants.FIELD != _jni.pieceAt(BoardConstants.WHITE, index)){
    			_selectedPosition = index;
    			paintBoard();
    			return;
    		}
    		if(BoardConstants.FIELD != _jni.pieceAt(BoardConstants.BLACK, index)){
    			_selectedPosition = index;
    			paintBoard();
    			return;
    		}
    		_jni.putPiece(index, _selectedPiece, _selectedColor);
    		/*_board.initHashKey();
    		_board.calcQuality();
    		_board.calcState(new ChessBoard());*/
    	}
    	else if(_selectedPosition >= 0 && // move a piece (field selected)
    		_jni.pieceAt(BoardConstants.WHITE, index) == BoardConstants.FIELD &&
    		_jni.pieceAt(BoardConstants.BLACK, index) == BoardConstants.FIELD){
    		
    		int t = BoardConstants.WHITE;
    		int p =_jni.pieceAt(t, _selectedPosition); 
    		if(p == BoardConstants.FIELD){
    			t = BoardConstants.BLACK;
    			p =_jni.pieceAt(t, _selectedPosition);
    		}
    		if(p != BoardConstants.FIELD){
    			_jni.removePiece(t, _selectedPosition);
    			_selectedPosition = -1;
    			_jni.putPiece(index, p, t);
    		}
    	}
    	
    	paintBoard();
    }
    
    public void handleSelectClick(int index){
    	
    	ImageCacheObject tmpCache;
    	
		_selectedPiece = index;
		
		for(int i = 0; i < 5; i++){
			tmpCache = _arrSelImages[i].getICO();
    		tmpCache._selected = false;	
		}
		tmpCache = _arrSelImages[_selectedPiece].getICO();
		tmpCache._selected = true;
		
		for(int i = 0; i < 5; i++){
			_arrSelImages[i].invalidate();
		}
    	paintBoard();
    }
    
    protected void saveAndFinish()
    {
    	_jni.setTurn(_iTurn);
        int iEP = -1;
        switch(_iEPFile){
        case 1: iEP = _iTurn == 1 ? 16 : 40; break;
        case 2: iEP = _iTurn == 1 ? 17 : 41; break;
        case 3: iEP = _iTurn == 1 ? 18 : 42; break;
        case 4: iEP = _iTurn == 1 ? 19 : 43; break;
        case 5: iEP = _iTurn == 1 ? 20 : 44; break;
        case 6: iEP = _iTurn == 1 ? 21 : 45; break;
        case 7: iEP = _iTurn == 1 ? 22 : 46; break;
        case 8: iEP = _iTurn == 1 ? 23 : 47; break;
        }
        
		///////////////////////////////////////////////////////////////////////////////////////
        
        _jni.setCastlingsEPAnd50(
        		_bWhiteCastleLong ? 1 : 0,
        		_bWhiteCastleShort ? 1 : 0, 
   				_bBlackCastleLong ? 1 : 0, 
        		_bBlackCastleShort ? 1 : 0,
        		iEP, 0);
        _jni.commitBoard();
        
        if(_jni.isLegalPosition() == 0)
        {
        	new AlertDialog.Builder(this)
			.setTitle("Use illegal position?")
			.setPositiveButton(getString(R.string.alert_yes),
				new DialogInterface.OnClickListener() {
					public void onClick(DialogInterface dialog,	int whichButton) {
						dialog.dismiss();

						commitFEN();
						setup.this.finish();
					}
				})
			.setNegativeButton(getString(R.string.alert_no), new DialogInterface.OnClickListener() {
				public void onClick(DialogInterface dialog, int whichButton) {
					dialog.dismiss();
				}
			})
			.show();	
        }
        else
        {
        	commitFEN();
        	finish();
        }
    }
    
    protected void commitFEN()
    {
    	SharedPreferences.Editor editor = getSharedPreferences("ChessPlayer", MODE_PRIVATE).edit();
	    editor.putString("FEN", _jni.toFEN());
	    editor.putString("game_pgn", "");
	    editor.putInt("boardNum", 0);
	    editor.putLong("game_id", 0);
	    editor.commit();
    }
    
    @Override
    protected void onResume() {
        super.onResume();

        _view.OnResume();
/////////////////////////////////////////////////////////////////////////////

        Window window = getWindow();
        final View v = window.getDecorView();
        v.post(new Runnable() {
            @Override
            public void run() {
                Rect rectangle= new Rect();
                v.getWindowVisibleDisplayFrame(rectangle);
                int availableHeight = rectangle.bottom - rectangle.top;
                int availableWidth = rectangle.right - rectangle.left;
                int length;

                if(availableHeight > availableWidth){
                    length = availableWidth / 8;
                } else {
                    length = availableHeight / 8;
                }
                //int contentViewTop = window.findViewById(Window.ID_ANDROID_CONTENT).getTop();
                //int titleBarHeight= contentViewTop - statusBarHeight;

                Log.i("ChessViewBase", "availableHeight 2 " + availableHeight);

                LayoutParams params = new LayoutParams(length, length);
                for(int i = 0; i < _arrSelImages.length; i++){
                    _arrSelImages[i].setLayoutParams(params);
                }
            }
        });


        /////////////////////////////////////////////////////////////////////////////

        String sFEN = null;
        if(_uri != null){
        
        	sFEN = "";
        	Log.i("setup", "opening " + _uri.toString());
        	InputStream is;
			try {
				is = getContentResolver().openInputStream(_uri);
				byte[] b = new byte[4096]; int len;
				
				while((len = is.read(b)) > 0)
				{
					sFEN += new String(b);
				}
				is.close();
				sFEN = sFEN.trim();
				
				Log.i("setup", "loaded " + sFEN);
			} catch (Exception e) {
				sFEN = null;
				Log.e("setup", "Failed " + e.toString());
			}
        }
        
        SharedPreferences prefs = getSharedPreferences("ChessPlayer", MODE_PRIVATE);
        
        if(sFEN == null || sFEN.length() == 0)
        {
        	sFEN = prefs.getString("FEN", null);
        }
       
        
        if(sFEN != null && sFEN.length() > 0){
        	_jni.initFEN(sFEN);
        }
        else{
        	resetBoard();
        }
        paintBoard();
    }

    class SetupOptionsDlg extends Dialog {


    	private CheckBox _checkWhiteCastleShort, _checkWhiteCastleLong, _checkBlackCastleShort, _checkBlackCastleLong;
    	private RadioButton _radioTurnWhite, _radioTurnBlack;
    	private Button _butOptionsCancel, _butOptionsOk;
    	private Spinner _spingEPFile;
    	
    	public SetupOptionsDlg() {
    		super(setup.this);
	
    		setContentView(R.layout.setup_options);
    		setTitle(R.string.title_options);

    		_radioTurnWhite = (RadioButton)findViewById(R.id.RadioSetupTurnWhite);
    		_radioTurnBlack = (RadioButton)findViewById(R.id.RadioSetupTurnBlack);
    		
    		_checkWhiteCastleShort = (CheckBox)findViewById(R.id.CheckBoxSetupWhiteCastleShort);
    		_checkWhiteCastleLong = (CheckBox)findViewById(R.id.CheckBoxSetupWhiteCastleLong);
    		_checkBlackCastleShort = (CheckBox)findViewById(R.id.CheckBoxSetupBlackCastleShort);
    		_checkBlackCastleLong = (CheckBox)findViewById(R.id.CheckBoxSetupBlackCastleLong);
    		
    		_butOptionsCancel = (Button)findViewById(R.id.ButtonSetupOptionsCancel);
    		_butOptionsCancel.setOnClickListener(new View.OnClickListener() {
	        	public void onClick(View arg0) {
	        		hide();
	        	}
			});
    		
    		_butOptionsOk = (Button)findViewById(R.id.ButtonSetupOptionsOk);
    		_butOptionsOk.setOnClickListener(new View.OnClickListener() {
	        	public void onClick(View arg0) {
	        		
	        		setup.this._bWhiteCastleShort = _checkWhiteCastleShort.isChecked();
	        		setup.this._bWhiteCastleLong = _checkWhiteCastleLong.isChecked();
	        		setup.this._bBlackCastleShort = _checkBlackCastleShort.isChecked();
	        		setup.this._bBlackCastleLong = _checkBlackCastleLong.isChecked();
	        		
	        		setup.this._iTurn = _radioTurnWhite.isChecked() ? 1 : 0;
	        		
	        		setup.this._iEPFile = _spingEPFile.getSelectedItemPosition();
	        		
	        		
	        		hide();
	        	}
			});
    		
    	    ArrayAdapter<CharSequence> adapter = ArrayAdapter.createFromResource(setup.this, R.array.field_files, android.R.layout.simple_spinner_item);
    	    adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);

    	    _spingEPFile = (Spinner)findViewById(R.id.SpinnerOptionsEnPassant);
    	    _spingEPFile.setPrompt(getString(R.string.title_pick_en_passant));
    	    _spingEPFile.setAdapter(adapter);
    	    
    	}

    	public void setItems(){
    		_radioTurnWhite.setChecked(setup.this._iTurn == ChessBoard.WHITE);
    		_radioTurnBlack.setChecked(setup.this._iTurn == ChessBoard.BLACK);
    		
    		_checkWhiteCastleShort.setChecked(setup.this._bWhiteCastleShort);
    		_checkWhiteCastleLong.setChecked(setup.this._bWhiteCastleLong);
    		
    		_checkBlackCastleShort.setChecked(setup.this._bBlackCastleShort);
    		_checkBlackCastleLong.setChecked(setup.this._bBlackCastleLong);
    		
    		_spingEPFile.setSelection(setup.this._iEPFile);
    	}

    }
}