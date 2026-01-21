package jwtc.android.chess.play;

import android.content.Context;
import android.os.Bundle;
import android.widget.Button;
import android.widget.RatingBar;

import java.util.Date;

import androidx.annotation.NonNull;

import com.google.android.material.textfield.TextInputEditText;

import jwtc.android.chess.R;
import jwtc.android.chess.helpers.ResultDialog;
import jwtc.android.chess.helpers.ResultDialogListener;
import jwtc.android.chess.views.PGNDateView;
import jwtc.chess.PGNColumns;

public class SaveGameDialog extends ResultDialog<Bundle> {

    private final TextInputEditText editTextWhite, editTextBlack, editTextEvent;
    private final RatingBar ratingBarRating;
    private final String _sPGN;
    private final PGNDateView dateView;

    public SaveGameDialog(@NonNull Context context, ResultDialogListener<Bundle> listener, int requestCode, String sEvent, String sWhite, String sBlack, Date date, String sPGN, boolean bCopy) {
        super(context, listener, requestCode);

        setContentView(R.layout.savegame);

        setTitle(R.string.title_save_game);

        ratingBarRating = findViewById(R.id.RatingBarSave);

        editTextEvent = findViewById(R.id.EditTextSaveEvent);
        editTextWhite = findViewById(R.id.EditTextSaveWhite);
        editTextBlack = findViewById(R.id.EditTextSaveBlack);

        dateView = findViewById(R.id.DateView);

        Button _butSave = findViewById(R.id.ButtonSaveSave);
        _butSave.setOnClickListener(arg0 -> {
            dismiss();
            save(false);
        });

        Button _butSaveCopy = findViewById(R.id.ButtonSaveCopy);
        _butSaveCopy.setOnClickListener(arg0 -> {
            dismiss();
            save(true);
        });

        Button _butCancel = findViewById(R.id.ButtonSaveCancel);
        _butCancel.setOnClickListener(arg0 -> dismiss());

        ratingBarRating.setRating(3.0F);
        editTextEvent.setText(sEvent);
        editTextWhite.setText(sWhite);
        editTextBlack.setText(sBlack);
        dateView.setDate(date);

        _sPGN = sPGN;

        _butSaveCopy.setEnabled(bCopy);
    }

    protected void save(boolean bCopy) {
        Bundle data = new Bundle();

        data.putLong(PGNColumns.DATE, dateView.getDate().getTime());
        data.putCharSequence(PGNColumns.WHITE, editTextWhite.getText().toString());
        data.putCharSequence(PGNColumns.BLACK, editTextBlack.getText().toString());
        data.putCharSequence(PGNColumns.PGN, _sPGN);
        data.putFloat(PGNColumns.RATING, ratingBarRating.getRating());
        data.putCharSequence(PGNColumns.EVENT, editTextEvent.getText().toString());
        data.putBoolean("copy", bCopy);

        setResult(data);
    }
}
