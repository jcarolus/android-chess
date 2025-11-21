package jwtc.android.chess.lichess;

import android.content.Context;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.text.InputType;
import android.util.Log;
import android.view.View;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.Spinner;
import android.widget.TextView;

import jwtc.android.chess.R;
import jwtc.android.chess.helpers.ResultDialog;
import jwtc.android.chess.helpers.ResultDialogListener;

public class ChallengeDialog extends ResultDialog {

    public static final String TAG = "Lichess.ChallengeDialog";

    public ChallengeDialog(Context context, ResultDialogListener listener, int requestCode, final SharedPreferences prefs) {
        super(context, listener, requestCode);

        setContentView(R.layout.lichess_challenge);

        setTitle("Seek or Challenge");

        final EditText editTextPlayer = (EditText) findViewById(R.id.EditTextMatchOpponent);
        final TextView textViewPlayerName = (TextView) findViewById(R.id.tvMatchPlayerName);

        final Spinner spinnerTime = (Spinner) findViewById(R.id.SpinnerMatchTime);
        final ArrayAdapter<CharSequence> adapterTime = ArrayAdapter.createFromResource(context, R.array.match_time_minutes, android.R.layout.simple_spinner_item);
        adapterTime.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        spinnerTime.setAdapter(adapterTime);

        final Spinner spinIncrement = (Spinner) findViewById(R.id.SpinnerMatchTimeIncrement);
        final ArrayAdapter<CharSequence> adapterIncrement = ArrayAdapter.createFromResource(context, R.array.match_time_increments, android.R.layout.simple_spinner_item);
        adapterIncrement.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        spinIncrement.setAdapter(adapterIncrement);

        final Spinner spinnerVariant = (Spinner) findViewById(R.id.SpinnerMatchVariant);
        final ArrayAdapter<CharSequence> adapterVariant = ArrayAdapter.createFromResource(context, R.array.match_variant, android.R.layout.simple_spinner_item);
        adapterVariant.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        spinnerVariant.setAdapter(adapterVariant);

        final Spinner spinnerColor = (Spinner) findViewById(R.id.SpinnerMatchColor);
        final ArrayAdapter<CharSequence> adapterColor = ArrayAdapter.createFromResource(context, R.array.match_color, android.R.layout.simple_spinner_item);
        adapterColor.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        spinnerColor.setAdapter(adapterColor);

        final EditText editRatingRangeMIN = (EditText) findViewById(R.id.EditTextMatchRatingRangeMIN);
        final TextView textViewRatingRangeMIN = (TextView) findViewById(R.id.tvMatchRatingMIN);
        editRatingRangeMIN.setInputType(InputType.TYPE_CLASS_NUMBER);

        final EditText editTextRatingRangeMAX = (EditText) findViewById(R.id.EditTextMatchRatingRangeMAX);
        final TextView textViewRatingRangeMAX = (TextView) findViewById(R.id.tvMatchRatingMAX);
        editTextRatingRangeMAX.setInputType(InputType.TYPE_CLASS_NUMBER);

        final CheckBox checkBoxRated = (CheckBox) findViewById(R.id.CheckBoxSeekRated);

        final Button bututonOk = (Button) findViewById(R.id.ButtonChallengeOk);
        bututonOk.setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) {
                ChallengeDialog.this.dismiss();

                SharedPreferences.Editor editor = prefs.edit();
//                editor.putString("spinTime", String.valueOf(_spinTime.getSelectedItemPosition()));
//                editor.putString("spinIncrement", String.valueOf(_spinIncrement.getSelectedItemPosition()));
//                editor.putString("spinVariant", String.valueOf(_spinVariant.getSelectedItemPosition()));
//                editor.putString("spinColor", String.valueOf(_spinColor.getSelectedItemPosition()));
//                editor.putString("editRatingRangeMIN", _editRatingRangeMIN.getText().toString());
//                editor.putString("editRatingRangeMAX", _editRatingRangeMAX.getText().toString());
//                editor.putBoolean("checkRated", _checkRated.isChecked());
                editor.apply();
//                Bundle data = new Bundle();
//                data.putCharSequence("challenge", s);
//
//                setResult(data);

            }
        });
        final Button buttonCancel = (Button) findViewById(R.id.ButtonChallengeCancel);
        buttonCancel.setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) {
                ChallengeDialog.this.dismiss();
            }
        });
    }
}