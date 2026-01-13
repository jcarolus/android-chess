package jwtc.android.chess;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Date;
import java.util.List;

import android.app.DatePickerDialog;
import android.content.ContentUris;
import android.content.Intent;
import android.content.SharedPreferences;
import android.database.Cursor;
import android.net.Uri;
import android.os.Bundle;
import android.text.Editable;
import android.text.TextUtils;
import android.text.TextWatcher;
import android.util.Log;
import android.view.View;
import android.widget.ArrayAdapter;
import android.widget.AutoCompleteTextView;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ImageButton;
import android.widget.SeekBar;
import android.widget.TextView;
import android.widget.ViewAnimator;

import com.google.android.material.switchmaterial.SwitchMaterial;

import jwtc.android.chess.activities.ChessBoardActivity;
import jwtc.android.chess.helpers.ActivityHelper;
import jwtc.android.chess.helpers.MyPGNProvider;
import jwtc.android.chess.helpers.PGNHelper;
import jwtc.android.chess.play.PlayActivity;
import jwtc.android.chess.services.GameApi;
import jwtc.android.chess.views.ChessSquareView;
import jwtc.chess.JNI;
import jwtc.chess.PGNColumns;

public class GamesListActivity extends ChessBoardActivity {
    private static final String TAG = "GamesListActivity";

    private EditText editTextSearch, editTextPlayerWhite, editTextPlayerBlack;
    private String sortOrder, sortBy;
    private View _viewSortRating, _viewSortWhite, _viewSortBlack, _viewSortId, _viewSortDate, _viewSortEvent;
    private TextView textViewResult, textViewTotal, textViewFilterInfo;
    private Button buttonFilterDateAfter, buttonDate;
    private SwitchMaterial switchDateAfter;
    private AutoCompleteTextView autoCompleteResult;
    private SeekBar seekBarGames;
    private Cursor cursor;

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        setContentView(R.layout.gameslist);

        ActivityHelper.fixPaddings(this, findViewById(R.id.root_layout));

        gameApi = new GameApi();

        sortBy = PGNColumns.DATE;
        sortOrder = "ASC";

//        editTextSearch = (EditText) findViewById(R.id.EditTextGamesList);
//        editTextSearch.addTextChangedListener(new TextWatcher() {
//
//            public void afterTextChanged(Editable s) {
//                doFilterSort();
//            }
//
//            public void beforeTextChanged(CharSequence s, int start, int count, int after) {
//            }
//
//            public void onTextChanged(CharSequence s, int start, int before, int count) {
//            }
//        });
        final ViewAnimator viewAnimator = findViewById(R.id.root_layout);

        editTextPlayerWhite = findViewById(R.id.EditTextPlayerWhite);
        editTextPlayerBlack = findViewById(R.id.EditTextPlayerBlack);
        textViewResult = findViewById(R.id.TextViewResult);
        textViewTotal = findViewById(R.id.TextViewTotal);
        textViewFilterInfo = findViewById(R.id.TextViewFilterInfo);

        seekBarGames = findViewById(R.id.SeekBarGames);
        seekBarGames.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override
            public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                loadGame();
            }

            @Override
            public void onStartTrackingTouch(SeekBar seekBar) {}

            @Override
            public void onStopTrackingTouch(SeekBar seekBar) {}
        });

        ImageButton butPrev = findViewById(R.id.ButtonPrevious);
        butPrev.setOnClickListener(v -> {
            int progress = seekBarGames.getProgress();
            if (progress > 0) {
                seekBarGames.setProgress(progress - 1);
            }
        });

        ImageButton butNext = findViewById(R.id.ButtonNext);
        butNext.setOnClickListener(v -> {
            int progress = seekBarGames.getProgress();
            if (progress + 1 < cursor.getCount()) {
                seekBarGames.setProgress(progress + 1);
            }
        });

        Button buttonOpen = findViewById(R.id.ButtonOpen);
        buttonOpen.setOnClickListener(v -> openGame());

        Button buttonDelete = findViewById(R.id.ButtonDelete);
        buttonDelete.setOnClickListener(v -> deleteGame());

        buttonDate = findViewById(R.id.ButtonDate);

        ImageButton buttonOpenFilters = findViewById(R.id.ButtonOpenFilters);
        buttonOpenFilters.setOnClickListener(v -> viewAnimator.setDisplayedChild(1));

        Button buttonFilterClose = findViewById(R.id.ButtonFilterClose);
        buttonFilterClose.setOnClickListener(v -> viewAnimator.setDisplayedChild(0));

        DatePickerDialog datePickerDialog = new DatePickerDialog(this);

        switchDateAfter = findViewById(R.id.SwitchFilterDateAfter);
        switchDateAfter.setOnCheckedChangeListener((v, isChecked) -> doFilterSort());
        // PGNHelper.getDate(s);
        buttonFilterDateAfter = findViewById(R.id.ButtonFilterDateAfter);
        buttonFilterDateAfter.setOnClickListener(v -> {
            datePickerDialog.setOnDateSetListener((view, year, monthOfYear, dayOfMonth) -> {
                monthOfYear++;
                buttonFilterDateAfter.setText("" + year + "." + monthOfYear + "." + dayOfMonth);
                doFilterSort();
            });

            datePickerDialog.show();
        });
        //

        String[] resultOptions = {"1-0", "0-1", "1/2-1/2", "*", ""};
        ArrayAdapter<String> adapter = new ArrayAdapter<String>(this, android.R.layout.select_dialog_item, resultOptions);
        autoCompleteResult = findViewById(R.id.AutoCompleteResult);
        autoCompleteResult.setAdapter(adapter);

        autoCompleteResult.addTextChangedListener(new TextWatcher() {
            @Override
            public void afterTextChanged(Editable s) {
                doFilterSort();
            }

            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {

            }

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {

            }
        });
        // DEBUG
        //getContentResolver().delete(MyPGNProvider.CONTENT_URI, "1=1", null);
        /////////////////////////////////////////////////////////////////////////////////////

        afterCreate();
    }

    @Override
    public void afterCreate() {
        Log.d(TAG, " afterCreate");

        jni = JNI.getInstance();
        chessBoardView = findViewById(R.id.includeboard);
        chessBoardView.setFocusable(false);

        for (int i = 0; i < 64; i++) {
            ChessSquareView csv = new ChessSquareView(this, i);
            chessBoardView.addView(csv);
        }

        gameApi.addListener(this);
    }

    @Override
    protected void onResume() {
        super.onResume();

        cursor = getContentResolver().query(MyPGNProvider.CONTENT_URI, PGNColumns.COLUMNS, null, null, sortBy + " " + sortOrder);

        doFilterSort();
    }

    private void onQueryUpdated() {
        seekBarGames.setProgress(0);
        int count = cursor.getCount();
        seekBarGames.setMax(count > 0 ? count - 1 : 0);
        seekBarGames.setVisibility(count == 0 ? View.INVISIBLE : View.VISIBLE);
    }

    private void loadGame() {
        updateProgressTotal();
        int position = seekBarGames.getProgress();
        if (cursor == null || cursor.getCount() < position) {
            return;
        }
        cursor.moveToPosition(position);

        gameApi.loadPGN(getColumnString(PGNColumns.PGN));

        editTextPlayerWhite.setText(getColumnString(PGNColumns.WHITE));
        editTextPlayerBlack.setText(getColumnString(PGNColumns.BLACK));
        buttonDate.setText(formatDate(getColumnDate(PGNColumns.DATE)));

        String result = getColumnString(PGNColumns.RESULT);
        if (result.equals("1/2-1/2")) {
            result = "½-½";
        }
        textViewResult.setText(result);
    }

    private void openGame() {
        long id = cursor.getLong(cursor.getColumnIndex(PGNColumns._ID));

        Log.d(TAG, "openGame " + id);
        SharedPreferences.Editor editor = getPrefs().edit();
        editor.putLong("game_id", id);
        editor.commit();

        Intent i = new Intent();
        i.setClass(this, PlayActivity.class);
        i.setFlags(Intent.FLAG_ACTIVITY_REORDER_TO_FRONT);

        startActivity(i);
    }

    private void deleteGame() {
        final long id = cursor.getLong(cursor.getColumnIndex(PGNColumns._ID));

        openConfirmDialog(getString(R.string.title_delete_game), getString(R.string.button_ok), getString(R.string.button_cancel), () -> {
            Uri uri = ContentUris.withAppendedId(MyPGNProvider.CONTENT_URI, id);
            getContentResolver().delete(uri, null, null);
            doFilterSort();
        }, null);
    }

    private void updateProgressTotal() {
        int count =  cursor.getCount();
        textViewTotal.setText("" + (seekBarGames.getProgress() + (count == 0 ? 0 : 1)) + "/" + count);
        textViewFilterInfo.setText("Results: " + count);
    }

    private void doFilterSort() {
        List<String> whereParts = new ArrayList<>();
        List<String> args = new ArrayList<>();

        String result =  getTrimmedOrNull(autoCompleteResult.getText());
        if (result != null) {
            whereParts.add(PGNColumns.RESULT + " = ?");
            args.add(result);
        }
        String dateAfter = getTrimmedOrNull(buttonFilterDateAfter.getText());
        if (dateAfter != null && switchDateAfter.isChecked()) {
            Date d = PGNHelper.getDate(dateAfter);
            Log.d(TAG, "dateAfter " + dateAfter + " => " + d);
            if (d != null) {
                whereParts.add(PGNColumns.DATE + " >= ? ");
                args.add(String.valueOf(d.getTime()));
            }
        }

        String selection = whereParts.isEmpty() ? null : TextUtils.join(" AND ", whereParts);
        String[] selectionArgs = args.isEmpty() ? null : args.toArray(new String[0]);

        String dbgArgs = selectionArgs == null ? "" : TextUtils.join("|", selectionArgs);
        Log.i(TAG, "runQuery " + selection + " " + dbgArgs + " BY " + sortOrder);

        cursor = getContentResolver().query(MyPGNProvider.CONTENT_URI, PGNColumns.COLUMNS, selection, selectionArgs, sortBy + " " + sortOrder);
        onQueryUpdated();
    }

    private void flipSortOrder() {
        sortOrder = sortOrder.equals("DESC") ? "ASC" : "DESC";
    }

    private Date parseTextToDate(String text) {
        if (text.equals("YYYY.mm.dd")) {
            return null;
        }
        SimpleDateFormat formatter = new SimpleDateFormat("yyyy.MM.dd");
        try {
            return formatter.parse(text);
        } catch (Exception ex) {
            return null;
        }
    }

    private String formatDate(Date d) {
        SimpleDateFormat formatter = new SimpleDateFormat("yyyy.MM.dd");
        return formatter.format(d);
    }

    private String getColumnString(String column) {
        int index = cursor.getColumnIndex(column);
        if (index >= 0 && index < cursor.getColumnCount()) {
            try {
                String value = cursor.getString(index);
                return value == null ? "" : value;
            } catch (Exception ex) {
                Log.d(TAG, "Caught getString exception for " + column);
                return "";
            }
        }
        Log.d(TAG, "invalid index " + index + " for " + column);
        return "";
    }

    private Date getColumnDate(String column) {
        int index = cursor.getColumnIndex(column);
        if (index >= 0 && index < cursor.getColumnCount()) {
            try {
                return new Date(cursor.getLong(index));
            } catch (Exception ex) {
                Log.d(TAG, "Caught exception for " + column + " " + ex.getMessage());
                return null;
            }
        }
        Log.d(TAG, "invalid index " + index + " for " + column);
        return null;
    }

    private static String getTrimmedOrNull(CharSequence cs) {
        if (cs == null) return null;
        String s = cs.toString().trim();
        return s.isEmpty() ? null : s;
    }
}
