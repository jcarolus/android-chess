package jwtc.android.chess.play;

import jwtc.android.chess.helpers.EinkMode;

import android.app.Dialog;
import android.content.Context;
import android.widget.RatingBar;

import java.util.Date;
import java.util.HashMap;

import androidx.annotation.NonNull;

import com.google.android.material.button.MaterialButton;
import com.google.android.material.textfield.TextInputEditText;

import jwtc.android.chess.R;
import jwtc.android.chess.helpers.Utils;
import jwtc.android.chess.services.GameApi;
import jwtc.android.chess.views.PGNDateView;
import jwtc.chess.GameRecord;
import jwtc.chess.PgnDate;

public class SaveGameDialog extends Dialog {

    private GameApi gameApi;
    private TextInputEditText editTextWhite, editTextBlack, editTextEvent;
    private RatingBar ratingBarRating;
    private PGNDateView dateView;
    private SaveGameResult result;
    private OnResultListener onResultListener;

    public SaveGameDialog(@NonNull Context context, GameApi gameApi, long lGameID, OnResultListener onResult) {
        super(context, EinkMode.isThemeEnabled() ? R.style.ChessDialogThemeEink : R.style.ChessDialogTheme);

        this.gameApi = gameApi;
        result = new SaveGameResult();

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
        editTextEvent.setText(gameApi.pgnTags.get("Event"));
        editTextWhite.setText(gameApi.pgnTags.get("White"));
        editTextBlack.setText(gameApi.pgnTags.get("Black"));

        dateView.setDate(PgnDate.parse(gameApi.pgnTags.get("Date")));

        _butSaveCopy.setEnabled(lGameID != 0);
    }


    protected void save(boolean bCopy) {
        gameApi.pgnTags.put("Event", Utils.getTrimmedOrDefault(editTextEvent.getText(), "Event?"));
        gameApi.pgnTags.put("White", Utils.getTrimmedOrDefault(editTextWhite.getText(), "White?"));
        gameApi.pgnTags.put("Black", Utils.getTrimmedOrDefault(editTextBlack.getText(), "Black?"));
        gameApi.pgnTags.put("Date", PgnDate.format(dateView.getDate()));
        result.rating = ratingBarRating.getRating();
        result.createCopy = bCopy;

        this.onResultListener.onResult(result);
    }

    public class SaveGameResult {
        public float rating = 0f;
        public long lGameID;
        public boolean createCopy = false;

        public GameRecord getRecord() {
            return GameRecord.of(gameApi.exportFullPGN(), gameApi.pgnTags).withRating(result.rating);
        }
    }

    public interface OnResultListener {
        public void onResult(SaveGameResult result);
    }
}
