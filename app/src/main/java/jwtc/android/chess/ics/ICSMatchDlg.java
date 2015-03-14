package jwtc.android.chess.ics;

import jwtc.android.chess.*;
import android.app.Dialog;
import android.content.Context;
import android.util.Log;
import android.view.View;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.Spinner;
import android.widget.Toast;

/**
 * 
 */
public class ICSMatchDlg extends Dialog {

	private Spinner _spinTime, _spinIncrement, _spinVariant;
	private EditText _editPlayer;
	private ArrayAdapter<CharSequence> _adapterTime, _adapterIncrement, _adapterVariant;
	private Button _butOk, _butCancel;
	private CheckBox _checkRated;
	private ICSClient _parent;
	
	public ICSMatchDlg(Context context) {
		super(context);

		_parent = (ICSClient)context;

		setContentView(R.layout.ics_match);
		
		setTitle("Seek or Challenge");
		
		_editPlayer = (EditText)findViewById(R.id.EditTextMatchOpponent);

	    _spinTime = (Spinner) findViewById(R.id.SpinnerMatchTime);
	    _adapterTime = ArrayAdapter.createFromResource(context, R.array.match_time_minutes, android.R.layout.simple_spinner_item);
	    _adapterTime.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
	    _spinTime.setAdapter(_adapterTime);
	    
	    _spinTime.setSelection(5);
	    
	    _spinIncrement = (Spinner) findViewById(R.id.SpinnerMatchTimeIncrement);
	    _adapterIncrement = ArrayAdapter.createFromResource(context, R.array.match_time_increments, android.R.layout.simple_spinner_item);
	    _adapterIncrement.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
	    _spinIncrement.setAdapter(_adapterIncrement);
	    
	    _spinVariant = (Spinner) findViewById(R.id.SpinnerMatchVariant);
	    _adapterVariant = ArrayAdapter.createFromResource(context, R.array.match_variant, android.R.layout.simple_spinner_item);
	    _adapterVariant.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
	    _spinVariant.setAdapter(_adapterVariant);
	    
	    _checkRated = (CheckBox)findViewById(R.id.CheckBoxSeekRated);
   /*
	    _spinRated = (Spinner) findViewById(R.id.SpinnerMatchRate);
	    _adapterRated = ArrayAdapter.createFromResource(context, R.array.match_rated, android.R.layout.simple_spinner_item);
	    _adapterRated.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
	    _spinRated.setAdapter(_adapterRated);
*/
	    _butOk = (Button)findViewById(R.id.ButtonMatchOk);
	    _butOk.setOnClickListener(new View.OnClickListener() {
			public void onClick(View v) {
				ICSMatchDlg.this.dismiss();
				String s = "", sP = _editPlayer.getText().toString();
				if(sP.equals("*")){
					s = "seek " + (_checkRated.isChecked() ? "rated" : "unrated") + " " +
					(String)_spinTime.getSelectedItem() + " " +
					(String)_spinIncrement.getSelectedItem()
					;
					
				} else {
					s = "match " + 
					sP + " " + (_checkRated.isChecked() ? "rated" : "unrated") + " " +  
					(String)_spinTime.getSelectedItem() + " " + 
					(String)_spinIncrement.getSelectedItem();
				}
				// wild fr
				if(((String)_spinVariant.getSelectedItem()).equals("Standard")){
					
				} else {
					s += " wild fr";
				}
				Log.i("ICSMatchDlg", s);
				_parent.sendString(s);
				Toast.makeText(_parent, R.string.toast_challenge_posted, Toast.LENGTH_SHORT).show();
			}
        });
	    _butCancel = (Button)findViewById(R.id.ButtonMatchCancel);
	    _butCancel.setOnClickListener(new View.OnClickListener() {
			public void onClick(View v) {
				ICSMatchDlg.this.dismiss();
			}
        });
	}

	public void setPlayer(String s){
		_editPlayer.setText(s);
	}
	
}