package jwtc.android.chess.play;

import android.app.DatePickerDialog;
import android.content.ContentValues;
import android.content.Context;
import android.os.Bundle;
import android.view.View;
import android.widget.Button;
import android.widget.DatePicker;
import android.widget.EditText;
import android.widget.RatingBar;

import java.util.Calendar;

import androidx.annotation.NonNull;
import jwtc.android.chess.R;
import jwtc.android.chess.helpers.ResultDialog;
import jwtc.android.chess.helpers.ResultDialogListener;
import jwtc.chess.PGNColumns;

public class SaveGameDialog extends ResultDialog {

    private EditText _editWhite, _editBlack, _editEvent;
    private RatingBar _rateRating;
    private DatePickerDialog _dlgDate;
    private String _sPGN;
    private int _year, _month, _day;

    public SaveGameDialog(@NonNull Context context, ResultDialogListener listener, int requestCode, String sEvent, String sWhite, String sBlack, Calendar cal, String sPGN, boolean bCopy) {
        super(context, listener, requestCode);

        setContentView(R.layout.savegame);

        setTitle(R.string.title_save_game);

        _rateRating = findViewById(R.id.RatingBarSave);

        _editEvent = findViewById(R.id.EditTextSaveEvent);
        _editWhite = findViewById(R.id.EditTextSaveWhite);
        _editBlack = findViewById(R.id.EditTextSaveBlack);

        final Button _butDate = findViewById(R.id.ButtonSaveDate);
        _butDate.setOnClickListener(new View.OnClickListener() {
            public void onClick(View arg0) {
                _dlgDate.show();
            }
        });

        Button _butSave = findViewById(R.id.ButtonSaveSave);
        _butSave.setOnClickListener(new View.OnClickListener() {
            public void onClick(View arg0) {
                dismiss();
                save(false);
            }
        });

        Button _butSaveCopy = findViewById(R.id.ButtonSaveCopy);
        _butSaveCopy.setOnClickListener(new View.OnClickListener() {
            public void onClick(View arg0) {
                dismiss();
                save(true);
            }
        });

        Button _butCancel = findViewById(R.id.ButtonSaveCancel);
        _butCancel.setOnClickListener(new View.OnClickListener() {
            public void onClick(View arg0) {
                dismiss();
            }
        });

        _rateRating.setRating(3.0F);
        _editEvent.setText(sEvent);
        _editWhite.setText(sWhite);
        _editBlack.setText(sBlack);

        _year = cal.get(Calendar.YEAR);
        _month = cal.get(Calendar.MONTH) + 1;
        _day = cal.get(Calendar.DAY_OF_MONTH);

        _butDate.setText(_year + "." + _month + "." + _day);

        _dlgDate = new DatePickerDialog(context, new DatePickerDialog.OnDateSetListener() {

            public void onDateSet(DatePicker view, int year, int monthOfYear, int dayOfMonth) {
                _year = year;
                _month = monthOfYear + 1;
                _day = dayOfMonth;
                _butDate.setText(_year + "." + _month + "." + _day);

            }
        }, _year, _month - 1, _day);

        _sPGN = sPGN;

        _butSaveCopy.setEnabled(bCopy);
    }

    protected void save(boolean bCopy) {
        Bundle data = new Bundle();

        Calendar c = Calendar.getInstance();
        c.set(_year, _month - 1, _day, 0, 0);
        data.putLong(PGNColumns.DATE, c.getTimeInMillis());
        data.putCharSequence(PGNColumns.WHITE, _editWhite.getText().toString());
        data.putCharSequence(PGNColumns.BLACK, _editBlack.getText().toString());
        data.putCharSequence(PGNColumns.PGN, _sPGN);
        data.putFloat(PGNColumns.RATING, _rateRating.getRating());
        data.putCharSequence(PGNColumns.EVENT, _editEvent.getText().toString());
        data.putBoolean("copy", bCopy);

        setResult(data);
    }
}
