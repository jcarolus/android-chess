package jwtc.android.chess.play;

import android.app.DatePickerDialog;
import android.content.Context;
import android.os.Bundle;
import android.view.View;
import android.widget.Button;
import android.widget.DatePicker;
import android.widget.EditText;
import android.widget.RatingBar;

import java.util.Calendar;
import java.util.Date;

import androidx.annotation.NonNull;
import jwtc.android.chess.R;
import jwtc.android.chess.helpers.ResultDialog;
import jwtc.android.chess.helpers.ResultDialogListener;
import jwtc.chess.PGNColumns;

public class SaveGameDialog extends ResultDialog<Bundle> {

    private EditText editTextWhite, editTextBlack, editTextEvent;
    private RatingBar ratingBarRating;
    private DatePickerDialog datePickerDialog;
    private String _sPGN;
    private int _year, _month, _day;

    public SaveGameDialog(@NonNull Context context, ResultDialogListener<Bundle> listener, int requestCode, String sEvent, String sWhite, String sBlack, Date date, String sPGN, boolean bCopy) {
        super(context, listener, requestCode);

        setContentView(R.layout.savegame);

        setTitle(R.string.title_save_game);

        ratingBarRating = findViewById(R.id.RatingBarSave);

        editTextEvent = findViewById(R.id.EditTextSaveEvent);
        editTextWhite = findViewById(R.id.EditTextSaveWhite);
        editTextBlack = findViewById(R.id.EditTextSaveBlack);

        final Button _butDate = findViewById(R.id.ButtonSaveDate);
        _butDate.setOnClickListener(new View.OnClickListener() {
            public void onClick(View arg0) {
                datePickerDialog.show();
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

        ratingBarRating.setRating(3.0F);
        editTextEvent.setText(sEvent);
        editTextWhite.setText(sWhite);
        editTextBlack.setText(sBlack);

        Calendar calendar = Calendar.getInstance();
        calendar.setTime(date);
        _year = calendar.get(Calendar.YEAR);
        _month = calendar.get(Calendar.MONTH) + 1;
        _day = calendar.get(Calendar.DAY_OF_MONTH);

        _butDate.setText(_year + "." + _month + "." + _day);

        datePickerDialog = new DatePickerDialog(context, new DatePickerDialog.OnDateSetListener() {

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
        data.putCharSequence(PGNColumns.WHITE, editTextWhite.getText().toString());
        data.putCharSequence(PGNColumns.BLACK, editTextBlack.getText().toString());
        data.putCharSequence(PGNColumns.PGN, _sPGN);
        data.putFloat(PGNColumns.RATING, ratingBarRating.getRating());
        data.putCharSequence(PGNColumns.EVENT, editTextEvent.getText().toString());
        data.putBoolean("copy", bCopy);

        setResult(data);
    }
}
