package jwtc.android.chess;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.List;

import android.app.DatePickerDialog;
import android.app.Dialog;
import android.content.ContentUris;
import android.content.ContentValues;
import android.content.Context;
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
import android.widget.ImageButton;
import android.widget.SeekBar;
import android.widget.TextView;
import android.widget.ViewAnimator;

import androidx.annotation.NonNull;

import com.google.android.material.switchmaterial.SwitchMaterial;
import com.google.android.material.textfield.TextInputEditText;

import jwtc.android.chess.activities.ChessBoardActivity;
import jwtc.android.chess.helpers.ActivityHelper;
import jwtc.android.chess.helpers.MyPGNProvider;
import jwtc.android.chess.helpers.PGNHelper;
import jwtc.android.chess.helpers.ResultDialogListener;
import jwtc.android.chess.helpers.Utils;
import jwtc.android.chess.play.PlayActivity;
import jwtc.android.chess.play.SaveGameDialog;
import jwtc.android.chess.services.GameApi;
import jwtc.android.chess.views.ChessSquareView;
import jwtc.android.chess.views.FixedDropdownView;
import jwtc.chess.JNI;
import jwtc.chess.PGNColumns;

public class GamesListActivity extends ChessBoardActivity implements ResultDialogListener<Bundle> {
    private static final String TAG = "GamesListActivity";
    protected static final int REQUEST_SAVE_GAME = 1;

    private String sortOrder, sortBy;
    private TextView textViewResult, textViewTotal, textViewFilterInf, textViewPlayerWhite, textViewPlayerBlack, textViewEvent, textViewDate, textViewFilterInfo;
    private TextInputEditText editTextFilterWhite, editTextFilterBlack, editTextFilterEvent;
    private TextInputEditText editTextFilterDateAfter, editTextFilterDateBefore;
    private SwitchMaterial switchFilterWhite, switchFilterBlack, switchFilterDateAfter, switchFilterDateBefore, switchFilterEvent, switchFilterResult;
    private AutoCompleteTextView autoCompleteResult, autoCompleteOrderBy, autoCompleteOrderDirection;
    private FixedDropdownView dropdownView;

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

        final ViewAnimator viewAnimator = findViewById(R.id.root_layout);

        textViewPlayerWhite = findViewById(R.id.TextViewPlayerWhite);
        textViewPlayerBlack = findViewById(R.id.TextViewPlayerBlack);
        textViewResult = findViewById(R.id.TextViewResult);
        textViewTotal = findViewById(R.id.TextViewTotal);

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

        Button buttonEdit = findViewById(R.id.ButtonEdit);
        buttonEdit.setOnClickListener(v -> editGame());

        Button buttonDelete = findViewById(R.id.ButtonDelete);
        buttonDelete.setOnClickListener(v -> deleteGame());

        textViewDate = findViewById(R.id.TextViewDate);
        textViewEvent = findViewById(R.id.TextViewEvent);

        FilterDialog filterDialog = new FilterDialog(this);

        ImageButton buttonOpenFilters = findViewById(R.id.ButtonOpenFilters);
        buttonOpenFilters.setOnClickListener(v -> filterDialog.show());


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

        doFilterSort();
    }

    @Override
    protected void onPause() {
        super.onPause();

        SharedPreferences.Editor editor = this.getPrefs().edit();

        editor.putString("gameslist_filter_white", Utils.getTrimmedOrDefault(editTextFilterWhite.getText(), ""));
        editor.putString("gameslist_filter_black", Utils.getTrimmedOrDefault(editTextFilterBlack.getText(), ""));

        editor.commit();
    }


    private void onQueryUpdated() {
        int count = cursor.getCount();
        seekBarGames.setProgress(0);
        seekBarGames.setMax(count > 0 ? count - 1 : 0);
        seekBarGames.setVisibility(count == 0 ? View.INVISIBLE : View.VISIBLE);
        textViewTotal.setText("" + (seekBarGames.getProgress() + (count == 0 ? 0 : 1)) + "/" + count);
        textViewFilterInfo.setText("Results: " + count);
    }

    private void loadGame() {
        int position = seekBarGames.getProgress();
        if (cursor == null || cursor.getCount() < position) {
            return;
        }
        cursor.moveToPosition(position);

        gameApi.loadPGN(Utils.getColumnString(cursor, PGNColumns.PGN));

        textViewPlayerWhite.setText(Utils.getColumnString(cursor, PGNColumns.WHITE));
        textViewPlayerBlack.setText(Utils.getColumnString(cursor, PGNColumns.BLACK));
        textViewDate.setText(Utils.formatDate(Utils.getColumnDate(cursor, PGNColumns.DATE)));
        textViewEvent.setText(Utils.getColumnString(cursor, PGNColumns.EVENT));

        String result = Utils.getColumnString(cursor, PGNColumns.RESULT);
        if (result.equals("1/2-1/2")) {
            result = "½-½";
        }
        textViewResult.setText(result);
    }

    private void openGame() {
        int index = cursor.getColumnIndex(PGNColumns._ID);
        long id = cursor.getLong(index);

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
        final long id = Utils.getColumnLong(cursor, PGNColumns._ID);

        openConfirmDialog(getString(R.string.title_delete_game), getString(R.string.button_ok), getString(R.string.button_cancel), () -> {
            Uri uri = ContentUris.withAppendedId(MyPGNProvider.CONTENT_URI, id);
            getContentResolver().delete(uri, null, null);
            doFilterSort();
        }, null);
    }

    private void editGame() {
        SaveGameDialog saveDialog = new SaveGameDialog(this, this, REQUEST_SAVE_GAME,
                Utils.getColumnString(cursor, PGNColumns.EVENT),
                Utils.getColumnString(cursor, PGNColumns.WHITE),
                Utils.getColumnString(cursor, PGNColumns.BLACK),
                Utils.getColumnDate(cursor, PGNColumns.DATE),
                gameApi.exportFullPGN(),
                true);
        saveDialog.show();
    }

    private void doFilterSort() {
        List<String> whereParts = new ArrayList<>();
        List<String> args = new ArrayList<>();

        String value =  Utils.getTrimmedOrNull(editTextFilterWhite.getText());
        if (value != null && switchFilterWhite.isChecked()) {
            whereParts.add(PGNColumns.WHITE + " LIKE ?");
            args.add("%" + value + "%");
        }

        value =  Utils.getTrimmedOrNull(editTextFilterBlack.getText());
        if (value != null && switchFilterBlack.isChecked()) {
            whereParts.add(PGNColumns.BLACK + " LIKE ?");
            args.add("%" + value + "%");
        }

        value =  Utils.getTrimmedOrNull(editTextFilterEvent.getText());
        if (value != null && switchFilterEvent.isChecked()) {
            whereParts.add(PGNColumns.EVENT + " LIKE ?");
            args.add("%" + value + "%");
        }

        String dateAfter = Utils.getTrimmedOrNull(editTextFilterDateAfter.getText());
        if (dateAfter != null && switchFilterDateAfter.isChecked()) {
            Date d = PGNHelper.getDate(dateAfter);
            Log.d(TAG, "dateAfter " + dateAfter + " => " + d);
            if (d != null) {
                whereParts.add(PGNColumns.DATE + " >= ? ");
                args.add(String.valueOf(d.getTime()));
            }
        }

        String result =  Utils.getTrimmedOrNull(autoCompleteResult.getText());
        if (result != null && switchFilterResult.isChecked()) {
            whereParts.add(PGNColumns.RESULT + " = ?");
            args.add(result);
        }

        String selection = whereParts.isEmpty() ? null : TextUtils.join(" AND ", whereParts);
        String[] selectionArgs = args.isEmpty() ? null : args.toArray(new String[0]);

        sortBy = Utils.getTrimmedOrDefault(autoCompleteOrderBy.getText(), PGNColumns.DATE);
        sortOrder = Utils.getTrimmedOrDefault(autoCompleteOrderDirection.getText(), "DESC");

        String dbgArgs = selectionArgs == null ? "" : TextUtils.join("|", selectionArgs);
        Log.i(TAG, "runQuery " + selection + " " + dbgArgs + " BY " + sortBy + " " + sortOrder);

        if (cursor != null && !cursor.isClosed()) {
            cursor.close();
        }
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



    @Override
    public void OnDialogResult(int requestCode, Bundle data) {
        ContentValues values = new ContentValues();
        boolean bCopy = data.getBoolean("copy");

        values.put(PGNColumns.DATE, data.getLong(PGNColumns.DATE));
        values.put(PGNColumns.WHITE, data.getString(PGNColumns.WHITE));
        values.put(PGNColumns.BLACK, data.getString(PGNColumns.BLACK));
        values.put(PGNColumns.PGN, data.getString(PGNColumns.PGN));
        values.put(PGNColumns.RATING, data.getFloat(PGNColumns.RATING));
        values.put(PGNColumns.EVENT, data.getString(PGNColumns.EVENT));

        HashMap<String, String> pgnTags = new HashMap<>();
        GameApi.loadPGNHead(data.getString(PGNColumns.PGN), pgnTags);

        values.put(PGNColumns.RESULT, pgnTags.get("Result"));


        /*
        Uri uri = ContentUris.withAppendedId(MyPGNProvider.CONTENT_URI, lGameID);
            getContentResolver().update(uri, values, null, null);
         */
    }

    private class FilterDialog extends Dialog {
        public FilterDialog(@NonNull Context context) {
            super(context, R.style.ChessDialogTheme);

            setContentView(R.layout.gameslist_filters);

            SharedPreferences prefs = getPrefs();

            final TextWatcher textWatcher = new TextWatcher() {
                @Override
                public void afterTextChanged(Editable s) {
                    doFilterSort();
                }

                @Override
                public void beforeTextChanged(CharSequence s, int start, int count, int after) {}

                @Override
                public void onTextChanged(CharSequence s, int start, int before, int count) {}
            };

            Button buttonFilterClose = findViewById(R.id.ButtonFilterClose);
            buttonFilterClose.setOnClickListener(v -> dismiss());

            textViewFilterInfo = findViewById(R.id.TextViewFilterInfo);

            editTextFilterWhite = findViewById(R.id.EditTextFilterWhite);
            editTextFilterWhite.setText(prefs.getString("gameslist_filter_white", ""));
            editTextFilterWhite.addTextChangedListener(textWatcher);

            editTextFilterBlack = findViewById(R.id.EditTextFilterBlack);
            editTextFilterBlack.setText(prefs.getString("gameslist_filter_black", ""));
            editTextFilterBlack.addTextChangedListener(textWatcher);

            editTextFilterEvent = findViewById(R.id.EditTextFilterEvent);
            editTextFilterEvent.addTextChangedListener(textWatcher);

            DatePickerDialog datePickerDialog = new DatePickerDialog(context);

            // PGNHelper.getDate(s);
            editTextFilterDateAfter = findViewById(R.id.ButtonFilterDateAfter);
            editTextFilterDateAfter.setOnClickListener(v -> {
                datePickerDialog.setOnDateSetListener((view, year, monthOfYear, dayOfMonth) -> {
                    monthOfYear++;
                    editTextFilterDateAfter.setText("" + year + "." + monthOfYear + "." + dayOfMonth);
                    doFilterSort();
                });

                datePickerDialog.show();
            });

            editTextFilterDateBefore = findViewById(R.id.ButtonFilterDateBefore);
            // @TODO

            switchFilterWhite = findViewById(R.id.SwitchFilterWhite);
            switchFilterWhite.setOnCheckedChangeListener((v, isChecked) -> doFilterSort());

            switchFilterBlack = findViewById(R.id.SwitchFilterBlack);
            switchFilterBlack.setOnCheckedChangeListener((v, isChecked) -> doFilterSort());

            switchFilterEvent = findViewById(R.id.SwitchFilterEvent);
            switchFilterEvent.setOnCheckedChangeListener((v, isChecked) -> doFilterSort());

            switchFilterDateAfter = findViewById(R.id.SwitchFilterDateAfter);
            switchFilterDateAfter.setOnCheckedChangeListener((v, isChecked) -> doFilterSort());

            switchFilterDateBefore = findViewById(R.id.SwitchFilterDateBefore);
            switchFilterDateBefore.setOnCheckedChangeListener((v, isChecked) -> doFilterSort());

            switchFilterResult = findViewById(R.id.SwitchFilterResult);
            switchFilterResult.setOnCheckedChangeListener((v, isChecked) -> doFilterSort());

            String[] resultOptions = {"1-0", "0-1", "1/2-1/2", "*"};
            ArrayAdapter<String> adapter = new ArrayAdapter<String>(context, android.R.layout.select_dialog_item, resultOptions);
            autoCompleteResult = findViewById(R.id.AutoCompleteResult);
            autoCompleteResult.setAdapter(adapter);
            autoCompleteResult.addTextChangedListener(textWatcher);

            String[] orderByOptions = {PGNColumns.DATE, PGNColumns.WHITE, PGNColumns.BLACK, PGNColumns.EVENT, PGNColumns.RESULT};
            ArrayAdapter<String> adapterOrderBy = new ArrayAdapter<String>(context, android.R.layout.select_dialog_item, orderByOptions);
            autoCompleteOrderBy = findViewById(R.id.AutoCompleteOrderBy);
            autoCompleteOrderBy.setAdapter(adapterOrderBy);
            autoCompleteOrderBy.setText(PGNColumns.DATE);
            autoCompleteOrderBy.addTextChangedListener(textWatcher);
            autoCompleteOrderBy.setOnClickListener(v -> {
                CharSequence current = autoCompleteOrderBy.getText();
                autoCompleteOrderBy.setText("", true);
                autoCompleteOrderBy.showDropDown();
                autoCompleteOrderBy.setText(current, false); // restore
            });

            String[] orderDirectionOptions = {"ASC", "DESC"};
            ArrayAdapter<String> adapterOrderDirection = new ArrayAdapter<String>(context, android.R.layout.select_dialog_item, orderDirectionOptions);
            autoCompleteOrderDirection = findViewById(R.id.AutoCompleteOrderDirection);
            autoCompleteOrderDirection.setAdapter(adapterOrderDirection);
            autoCompleteOrderDirection.setText("DESC");
            autoCompleteOrderDirection.addTextChangedListener(textWatcher);

            dropdownView = findViewById(R.id.ExposedDropdownView);
            dropdownView.setHint("Order");
            dropdownView.setSelectionText("ASC");
            dropdownView.setItems(orderDirectionOptions);
            dropdownView.setOnItemClickListener((parent, view, position, id) -> {
                 String selected = (String) parent.getItemAtPosition(position);
                 Log.d(TAG, "sel " + selected);
                // do something with selected
            });
        }
    }
}
