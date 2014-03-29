package jwtc.android.chess;

import java.util.Calendar;
import android.app.DatePickerDialog;
import android.app.Dialog;
import android.content.ContentValues;
import android.view.View;
import android.widget.Button;
import android.widget.DatePicker;
import android.widget.EditText;
import android.widget.RatingBar;
import jwtc.chess.PGNColumns;
/**
 * 
 */
public class SaveGameDlg extends Dialog {

	private EditText _editWhite, _editBlack, _editEvent;
	
	private RatingBar _rateRating;
	private Button _butSave, _butCancel, _butSaveCopy, _butDate;
	private main _parent;
	private String _sPGN;
	private int _year, _month, _day;
	private DatePickerDialog _dlgDate;
	
	public SaveGameDlg(main context) {
		super(context);

		_parent = context;		
		setContentView(R.layout.savegame);
		
		setTitle(R.string.title_save_game);
		
		_rateRating = (RatingBar)findViewById(R.id.RatingBarSave);
		
		_editEvent = (EditText)findViewById(R.id.EditTextSaveEvent);
		_editWhite = (EditText)findViewById(R.id.EditTextSaveWhite);
        _editBlack = (EditText)findViewById(R.id.EditTextSaveBlack);
                
        _butDate = (Button)findViewById(R.id.ButtonSaveDate);
        _butDate.setOnClickListener(new View.OnClickListener() {
        	public void onClick(View arg0) {
        		_dlgDate.show();
        	}
    	});
        
        _butSave = (Button)findViewById(R.id.ButtonSaveSave);
        _butSave.setOnClickListener(new View.OnClickListener() {
        	public void onClick(View arg0) {
        		dismiss();
        		save(false);
        	}
    	});
        
        _butSaveCopy = (Button)findViewById(R.id.ButtonSaveCopy);
        _butSaveCopy.setOnClickListener(new View.OnClickListener() {
        	public void onClick(View arg0) {
        		dismiss();
        		save(true);
        	}
    	});
        
        _butCancel = (Button)findViewById(R.id.ButtonSaveCancel);
        _butCancel.setOnClickListener(new View.OnClickListener() {
        	public void onClick(View arg0) {
        		dismiss();
        	}
        });
	}
	
	public void save(boolean bCopy){
		ContentValues values = new ContentValues();
		
		Calendar c = Calendar.getInstance();
		c.set(_year, _month-1, _day, 0, 0);
        values.put(PGNColumns.DATE, c.getTimeInMillis());
        values.put(PGNColumns.WHITE, _editWhite.getText().toString());
        values.put(PGNColumns.BLACK, _editBlack.getText().toString());
        values.put(PGNColumns.PGN, _sPGN);
        values.put(PGNColumns.RATING, _rateRating.getRating());
        values.put(PGNColumns.EVENT, _editEvent.getText().toString());

        _parent.saveGame(values, bCopy);
	}

	public void setItems(String sEvent, String sWhite, String sBlack, Calendar cal, String sPGN, float fRating, boolean bCopy){
		
		_rateRating.setRating(fRating);
		_editEvent.setText(sEvent);
		_editWhite.setText(sWhite);
        _editBlack.setText(sBlack);
        
        _year = cal.get(Calendar.YEAR);
        _month = cal.get(Calendar.MONTH) + 1;
        _day = cal.get(Calendar.DAY_OF_MONTH);
        
        _butDate.setText(_year + "." + _month + "." + _day);
        
        _dlgDate = new DatePickerDialog(_parent, new DatePickerDialog.OnDateSetListener() {

			public void onDateSet(DatePicker view, int year, int monthOfYear, int dayOfMonth) {
				_year = year;
				_month = monthOfYear + 1;
				_day = dayOfMonth;
				_butDate.setText(_year + "." + _month + "." + _day);
				
			}}, _year, _month-1, _day);
        
        _sPGN = sPGN;
        
       	_butSaveCopy.setEnabled(bCopy);
        
	}

}