package jwtc.android.chess.ics;

import jwtc.android.chess.*;
import android.app.Dialog;
import android.content.Context;
import android.content.SharedPreferences;
import android.text.InputType;
import android.util.Log;
import android.view.View;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.RadioButton;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.Toast;

/**
 * 
 */
public class ICSMatchDlg extends Dialog {

	public static final String TAG = "ICSMatchDlg";

	private TextView _tvPlayerName, _tvRatingRangeMIN, _tvRatingRangeMAX, _tvManual, _tvFormula;
	protected RadioButton _rbSeek, _rbChallenge;
	private Spinner _spinTime, _spinIncrement, _spinVariant, _spinColor;
	private EditText _editPlayer, _editRatingRangeMIN, _editRatingRangeMAX;
	private ArrayAdapter<CharSequence> _adapterTime, _adapterIncrement, _adapterVariant, _adapterColor;
	private Button _butOk, _butCancel;
	private CheckBox _checkRated, _checkManual, _checkFormula;
	private ICSClient _parent;
	
	public ICSMatchDlg(Context context) {
		super(context);

		_parent = (ICSClient)context;

		setContentView(R.layout.ics_match);
		
		setTitle("Seek or Challenge");

		_rbSeek = (RadioButton) findViewById(R.id.RadioButtonSeek);
		_rbSeek.setOnClickListener(new View.OnClickListener() {
			@Override
			public void onClick(View view) {
				_editPlayer.setVisibility(View.GONE);
				_tvPlayerName.setVisibility(View.GONE);
				_editRatingRangeMIN.setVisibility(View.VISIBLE);
				_tvRatingRangeMIN.setVisibility(View.VISIBLE);
				_editRatingRangeMAX.setVisibility(View.VISIBLE);
				_tvRatingRangeMAX.setVisibility(View.VISIBLE);
				_checkManual.setVisibility(View.VISIBLE);
				_tvManual.setVisibility(View.VISIBLE);
				_checkFormula.setVisibility(View.GONE);  // if formula is valuable enough then
				_tvFormula.setVisibility(View.GONE);     // change these to VISIBLE

				SharedPreferences myPrefs = getContext().getSharedPreferences(_parent.get_ficsHandle().toLowerCase(), getContext().MODE_PRIVATE);
				String spinTime = myPrefs.getString("spinTime", "5");
				String spinIncrement = myPrefs.getString("spinIncrement", "4");
				String spinVariant = myPrefs.getString("spinVariant", "0");
				String spinColor = myPrefs.getString("spinColor", "0");
				String editRatingRangeMIN = myPrefs.getString("editRatingRangeMIN", "0");
				String editRatingRangeMAX = myPrefs.getString("editRatingRangeMAX", "9999");
				Boolean checkRated = myPrefs.getBoolean("checkRated", false);
				Boolean checkManual = myPrefs.getBoolean("checkManual", false);

				_spinTime.setSelection(Integer.parseInt(spinTime));
				_spinIncrement.setSelection(Integer.parseInt(spinIncrement));
				_spinVariant.setSelection(Integer.parseInt(spinVariant));
				_spinColor.setSelection(Integer.parseInt(spinColor));
				_editRatingRangeMIN.setText(editRatingRangeMIN);
				_editRatingRangeMAX.setText(editRatingRangeMAX);
				_checkRated.setChecked(checkRated);
				_checkManual.setChecked(checkManual);

				_parent._dlgMatch.show();
			}
		});
		_rbChallenge = (RadioButton) findViewById(R.id.RadioButtonChallenge);
		_rbChallenge.setOnClickListener(new View.OnClickListener(){

			@Override
			public void onClick(View view) {
				_editPlayer.setVisibility(View.VISIBLE);
				_tvPlayerName.setVisibility(View.VISIBLE);
				_editRatingRangeMIN.setVisibility(View.GONE);
				_tvRatingRangeMIN.setVisibility(View.GONE);
				_editRatingRangeMAX.setVisibility(View.GONE);
				_tvRatingRangeMAX.setVisibility(View.GONE);
				_checkManual.setVisibility(View.GONE);
				_tvManual.setVisibility(View.GONE);
				_checkFormula.setVisibility(View.GONE);
				_tvFormula.setVisibility(View.GONE);
				_parent._dlgMatch.show();
			}
		});
		
		_editPlayer = (EditText)findViewById(R.id.EditTextMatchOpponent);
		_tvPlayerName = (TextView) findViewById(R.id.tvMatchPlayerName);

	    _spinTime = (Spinner) findViewById(R.id.SpinnerMatchTime);
	    _adapterTime = ArrayAdapter.createFromResource(context, R.array.match_time_minutes, android.R.layout.simple_spinner_item);
	    _adapterTime.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
	    _spinTime.setAdapter(_adapterTime);
	    
	    _spinIncrement = (Spinner) findViewById(R.id.SpinnerMatchTimeIncrement);
	    _adapterIncrement = ArrayAdapter.createFromResource(context, R.array.match_time_increments, android.R.layout.simple_spinner_item);
	    _adapterIncrement.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
	    _spinIncrement.setAdapter(_adapterIncrement);
	    
	    _spinVariant = (Spinner) findViewById(R.id.SpinnerMatchVariant);
	    _adapterVariant = ArrayAdapter.createFromResource(context, R.array.match_variant, android.R.layout.simple_spinner_item);
	    _adapterVariant.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
	    _spinVariant.setAdapter(_adapterVariant);

		_spinColor = (Spinner) findViewById(R.id.SpinnerMatchColor);
		_adapterColor = ArrayAdapter.createFromResource(context, R.array.match_color, android.R.layout.simple_spinner_item);
		_adapterColor.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
		_spinColor.setAdapter(_adapterColor);

		_editRatingRangeMIN = (EditText) findViewById(R.id.EditTextMatchRatingRangeMIN);
		_tvRatingRangeMIN = (TextView) findViewById(R.id.tvMatchRatingMIN);
		_editRatingRangeMIN.setInputType(InputType.TYPE_CLASS_NUMBER);

		_editRatingRangeMAX = (EditText) findViewById(R.id.EditTextMatchRatingRangeMAX);
		_tvRatingRangeMAX = (TextView) findViewById(R.id.tvMatchRatingMAX);
		_editRatingRangeMAX.setInputType(InputType.TYPE_CLASS_NUMBER);

	    _checkRated = (CheckBox)findViewById(R.id.CheckBoxSeekRated);

		_checkManual = (CheckBox) findViewById(R.id.CheckBoxSeekManual);
		_tvManual = (TextView) findViewById(R.id.tvMatchManual);

		_checkFormula = (CheckBox) findViewById(R.id.CheckBoxSeekFormula);
		_tvFormula = (TextView) findViewById(R.id.tvMatchFormula);

	    _butOk = (Button)findViewById(R.id.ButtonMatchOk);
	    _butOk.setOnClickListener(new View.OnClickListener() {
			public void onClick(View v) {
				ICSMatchDlg.this.dismiss();
				String s = "", sP = _editPlayer.getText().toString();

				if(_rbSeek.isChecked()){
					s = "seek " + (_checkManual.isChecked() ? "m ": "a ") + (_checkFormula.isChecked() ? "f ": "") + _editRatingRangeMIN.getText().toString()
							+ "-" + _editRatingRangeMAX.getText().toString() + " ";

				} else {
					s = "match " + sP + " ";
				}

				s += (_checkRated.isChecked() ? "rated " : "unrated ") +
						(String)_spinTime.getSelectedItem() + " " +
						(String)_spinIncrement.getSelectedItem() + " ";

				// Color
				if (((String) _spinColor.getSelectedItem()).equals((String)_spinColor.getItemAtPosition(1))){
					s += "w ";
				} else if(((String) _spinColor.getSelectedItem()).equals((String)_spinColor.getItemAtPosition(2))){
					s += "b ";
				}

				// Chess960 - wild fr
				if(((String)_spinVariant.getSelectedItem()).equals ((String)_spinVariant.getItemAtPosition(1))){
					s += "wild fr ";
				}


				SharedPreferences mPrefs = getContext().getSharedPreferences(_parent.get_ficsHandle().toLowerCase(), Context.MODE_PRIVATE);
				SharedPreferences.Editor editor = mPrefs.edit();
				editor.putString("spinTime", String.valueOf(_spinTime.getSelectedItemPosition()));
				editor.putString("spinIncrement", String.valueOf(_spinIncrement.getSelectedItemPosition()));
				editor.putString("spinVariant", String.valueOf(_spinVariant.getSelectedItemPosition()));
				editor.putString("spinColor", String.valueOf(_spinColor.getSelectedItemPosition()));
				editor.putString("editRatingRangeMIN", _editRatingRangeMIN.getText().toString());
				editor.putString("editRatingRangeMAX", _editRatingRangeMAX.getText().toString());
				editor.putBoolean("checkRated", _checkRated.isChecked());
				editor.putBoolean("checkManual", _checkManual.isChecked());
				editor.apply();

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