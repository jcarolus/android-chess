package jwtc.android.chess.play;

import static jwtc.android.chess.helpers.PGNHelper.cleanPgnString;
import static jwtc.android.chess.helpers.PGNHelper.regexPgnTag;

import android.app.Dialog;
import android.content.ContentValues;
import android.content.Context;
import android.widget.RatingBar;

import java.util.HashMap;

import androidx.annotation.NonNull;

import com.google.android.material.button.MaterialButton;
import com.google.android.material.textfield.TextInputEditText;

import jwtc.android.chess.R;
import jwtc.android.chess.helpers.PGNHelper;
import jwtc.android.chess.helpers.Utils;
import jwtc.android.chess.services.GameApi;
import jwtc.android.chess.views.PGNDateView;
import jwtc.chess.PGNColumns;

public class SaveGameDialog extends Dialog {

    private TextInputEditText editTextWhite, editTextBlack, editTextEvent;
    private RatingBar ratingBarRating;
    private PGNDateView dateView;
    private SaveGameResult result;
    private OnResultListener onResultListener;

    public SaveGameDialog(@NonNull Context context, String fullPGN, long lGameID, OnResultListener onResult) {
        super(context, R.style.ChessDialogTheme);

        result = new SaveGameResult();
        result.pgnTags = new HashMap<String, String>();
        GameApi.loadPGNHead(fullPGN, result.pgnTags);
        result.pgnMoves = cleanPgnString(fullPGN.replaceAll(regexPgnTag, ""));

        init(lGameID, onResult);
    }

    public SaveGameDialog(@NonNull Context context, String pgnMoves, HashMap<String, String> pgnTags, long lGameID, OnResultListener onResult) {
        super(context, R.style.ChessDialogTheme);

        result = new SaveGameResult();
        result.pgnTags = pgnTags;
        result.pgnMoves = pgnMoves;

        init(lGameID, onResult);
    }

    protected void init(long lGameID, OnResultListener onResult) {
        setContentView(R.layout.savegame);
        setTitle(R.string.title_save_game);

        result.lGameID = lGameID;
        this.onResultListener = onResult;
        ratingBarRating = findViewById(R.id.RatingBarSave);

        editTextEvent = findViewById(R.id.EditTextSaveEvent);
        editTextWhite = findViewById(R.id.EditTextSaveWhite);
        editTextBlack = findViewById(R.id.EditTextSaveBlack);

        dateView = findViewById(R.id.DateView);

        MaterialButton _butSave = findViewById(R.id.ButtonSaveSave);
        _butSave.setOnClickListener(arg0 -> {
            dismiss();
            save(false);
        });

        MaterialButton _butSaveCopy = findViewById(R.id.ButtonSaveCopy);
        _butSaveCopy.setOnClickListener(arg0 -> {
            dismiss();
            save(true);
        });

        MaterialButton _butCancel = findViewById(R.id.ButtonSaveCancel);
        _butCancel.setOnClickListener(arg0 -> dismiss());

        ratingBarRating.setRating(3.0F);
        editTextEvent.setText(result.pgnTags.get("Event"));
        editTextWhite.setText(result.pgnTags.get("White"));
        editTextBlack.setText(result.pgnTags.get("Black"));

        dateView.setDate(PGNHelper.getDate(result.pgnTags.get("Event")));

        _butSaveCopy.setEnabled(lGameID != 0);
    }


    protected void save(boolean bCopy) {
        result.pgnTags.put("Event", Utils.getTrimmedOrDefault(editTextEvent.getText(), "Event?"));
        result.pgnTags.put("White", Utils.getTrimmedOrDefault(editTextWhite.getText(), "White?"));
        result.pgnTags.put("Black", Utils.getTrimmedOrDefault(editTextBlack.getText(), "Black?"));
        result.pgnTags.put("Date", Utils.formatDate(dateView.getDate()));
        result.rating = ratingBarRating.getRating();
        result.createCopy = bCopy;

        this.onResultListener.onResult(result);
    }

    public class SaveGameResult {
        public HashMap<String, String> pgnTags;
        public String pgnMoves;
        public float rating = 0f;
        public long lGameID;
        public boolean createCopy = false;

        public ContentValues getContentValues() {

            ContentValues values = new ContentValues();

            values.put(PGNColumns.EVENT, result.pgnTags.get("Event"));
            values.put(PGNColumns.WHITE, result.pgnTags.get("White"));
            values.put(PGNColumns.BLACK, result.pgnTags.get("Black"));
            values.put(PGNColumns.DATE, PGNHelper.getDate(result.pgnTags.get("Date")).getTime());
            values.put(PGNColumns.RESULT, result.pgnTags.get("Result"));
            values.put(PGNColumns.RATING, result.rating);
            values.put(PGNColumns.PGN, result.pgnMoves);

            return values;
        }
    }

    public interface OnResultListener {
        public void onResult(SaveGameResult result);
    }
}
