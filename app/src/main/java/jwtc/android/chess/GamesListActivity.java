package jwtc.android.chess;

import java.util.ArrayList;
import java.util.Calendar;
import java.util.Date;
import java.util.List;

import android.app.Dialog;
import android.content.ContentUris;
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
import android.widget.GridView;
import android.widget.ListView;
import android.widget.RatingBar;
import android.widget.ScrollView;
import android.widget.SeekBar;
import android.widget.SimpleCursorAdapter;
import android.widget.TextView;

import androidx.annotation.NonNull;

import com.google.android.material.button.MaterialButton;
import com.google.android.material.button.MaterialButtonToggleGroup;
import com.google.android.material.switchmaterial.SwitchMaterial;
import com.google.android.material.textfield.TextInputEditText;

import jwtc.android.chess.activities.ChessBoardActivity;
import jwtc.android.chess.helpers.ActivityHelper;
import jwtc.android.chess.helpers.MyPGNProvider;
import jwtc.android.chess.helpers.Utils;
import jwtc.android.chess.play.MoveItem;
import jwtc.android.chess.play.MoveItemAdapter;
import jwtc.android.chess.play.PlayActivity;
import jwtc.android.chess.play.SaveGameDialog;
import jwtc.android.chess.services.GameApi;
import jwtc.android.chess.views.ChessSquareView;
import jwtc.android.chess.views.FixedDropdownView;
import jwtc.android.chess.views.PGNDateView;
import jwtc.chess.JNI;
import jwtc.chess.PGNColumns;
import jwtc.chess.PGNEntry;
import jwtc.chess.Pos;
import jwtc.chess.board.BoardConstants;

public class GamesListActivity extends ChessBoardActivity {
    private static final String TAG = "GamesListActivity";
    private static final int VIEW_BOARD = 1, VIEW_PGN = 2, VIEW_LIST = 3;

    private String sortOrder, sortBy;
    private TextView textViewResult, textViewTotal, textViewPlayerWhite, textViewPlayerBlack, textViewEvent, textViewDate, textViewFilterInfo, textViewRating, textViewWhitePieces, textViewBlackPieces;
    private TextInputEditText editTextFilterWhite, editTextFilterBlack, editTextFilterEvent;
    private MaterialButton buttonFilters;
    private PGNDateView pgnDateAfter, pgnDateBefore;
    private SwitchMaterial switchFilterWhite, switchFilterBlack, switchFilterDateAfter, switchFilterDateBefore, switchFilterEvent, switchFilterResult;
    private FixedDropdownView dropDownResult, dropDownOrderBy, dropDownOrderDirection;
    private MaterialButtonToggleGroup buttonToggleGroup;
    private GridView gridLayoutPgn;
    private ListView listView;
    private ScrollView scrollView;
    private final ArrayList<MoveItem> mapMoves = new ArrayList<MoveItem>();
    private MoveItemAdapter adapterMoves;
    private SimpleCursorAdapter adapterGames;
    private FilterDialog filterDialog;
    private SeekBar seekBarGames;
    private Cursor cursor;
    private long currentGameId;

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        setContentView(R.layout.gameslist);

        ActivityHelper.fixPaddings(this, findViewById(R.id.root_layout));

        gameApi = new GameApi();

        sortBy = PGNColumns.DATE;
        sortOrder = "ASC";

        textViewPlayerWhite = findViewById(R.id.TextViewPlayerWhite);
        textViewPlayerBlack = findViewById(R.id.TextViewPlayerBlack);
        textViewRating = findViewById(R.id.TextViewRating);
        textViewResult = findViewById(R.id.TextViewResult);
        textViewTotal = findViewById(R.id.TextViewTotal);
        textViewWhitePieces = findViewById(R.id.TextViewWhitePieces);
        textViewBlackPieces = findViewById(R.id.TextViewBlackPieces);

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

        MaterialButton butPrev = findViewById(R.id.ButtonPrevious);
        butPrev.setOnClickListener(v -> {
            int progress = seekBarGames.getProgress();
            if (progress > 0) {
                seekBarGames.setProgress(progress - 1);
            }
        });

        MaterialButton butNext = findViewById(R.id.ButtonNext);
        butNext.setOnClickListener(v -> {
            int progress = seekBarGames.getProgress();
            if (progress + 1 < cursor.getCount()) {
                seekBarGames.setProgress(progress + 1);
            }
        });

        MaterialButton buttonOpen = findViewById(R.id.ButtonOpen);
        buttonOpen.setOnClickListener(v -> openGame());

        MaterialButton buttonEdit = findViewById(R.id.ButtonEdit);
        buttonEdit.setOnClickListener(v -> editGame());

        MaterialButton buttonDelete = findViewById(R.id.ButtonDelete);
        buttonDelete.setOnClickListener(v -> deleteGame());

        textViewDate = findViewById(R.id.TextViewDate);
        textViewEvent = findViewById(R.id.TextViewEvent);

        filterDialog = new FilterDialog(this);

        buttonFilters = findViewById(R.id.ButtonOpenFilters);
        buttonFilters.setOnClickListener(v -> filterDialog.show());


        buttonToggleGroup = findViewById(R.id.ButtonToggleGroupView);

        buttonToggleGroup.addOnButtonCheckedListener((group, checkedId, isChecked) -> {
            if (isChecked) {
                if (checkedId == R.id.ButtonToggleBoard) {
                    showBoardView();
                } else if (checkedId == R.id.ButtonTogglePgn) {
                    showPgnView();
                } else if (checkedId == R.id.ButtonToggleList) {
                    showListView();
                }
            }
        });

        gridLayoutPgn = findViewById(R.id.GridLayoutPgn);
        gridLayoutPgn.setOnItemClickListener(null);
        gridLayoutPgn.setOnItemSelectedListener(null);
        gridLayoutPgn.setClickable(false);
        gridLayoutPgn.setLongClickable(false);

        adapterMoves = new MoveItemAdapter(this, mapMoves);
        gridLayoutPgn.setAdapter(adapterMoves);

        adapterGames = new SimpleCursorAdapter(this, R.layout.game_row, null,
                new String[]{PGNColumns._ID, PGNColumns.WHITE, PGNColumns.BLACK, PGNColumns.DATE, PGNColumns.EVENT, PGNColumns.RATING, PGNColumns.RESULT},
                new int[]{R.id.text_id, R.id.text_name1, R.id.text_name2, R.id.text_date, R.id.text_event, R.id.rating, R.id.text_result});

        adapterGames.setViewBinder((view, cursor, columnIndex) -> {

            int nImageIndex = cursor.getColumnIndex(PGNColumns.RATING);
            if (nImageIndex == columnIndex) {
                float fR = Utils.getColumnFloat(cursor, PGNColumns.RATING);
                ((RatingBar) view).setRating(fR);
                //((RatingBar)view).setRating(0.4F);
                return true;
            }
            int nDateIndex = cursor.getColumnIndex(PGNColumns.DATE);
            if (nDateIndex == columnIndex) {
                ((TextView) view).setText(Utils.formatDate(Utils.getColumnDate(cursor, PGNColumns.DATE)));
                return true;
            }
            int nResultIndex = cursor.getColumnIndex(PGNColumns.RESULT);
            if (nResultIndex == columnIndex) {
                String result = Utils.getColumnString(cursor, PGNColumns.RESULT);
                ((TextView) view).setText(result.equals("1/2-1/2") ?  "½-½" : result);
                return true;
            }

            return false;
        });

        listView = findViewById(R.id.ListView);
        listView.setAdapter(adapterGames);
        listView.setOnItemClickListener((parent, view, position, id) -> {
            cursor.moveToPosition(position);
            openGame();
        });
        listView.setOnItemLongClickListener((parent, view, position, id) -> {
            cursor.moveToPosition(position);
            deleteGame();
            return true;
        });

        scrollView = findViewById(R.id.ScrollView);

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

        SharedPreferences prefs = getPrefs();
        currentGameId = prefs.getLong("gameslist_current_game_id", 0);

        filterDialog.init();

        int view = prefs.getInt("gameslist_current_view", VIEW_BOARD);
        Log.d(TAG, "View " + view);
        switch (view) {
            case VIEW_PGN:
                buttonToggleGroup.check(R.id.ButtonTogglePgn);
                showPgnView();
                break;
            case VIEW_LIST:
                buttonToggleGroup.check(R.id.ButtonToggleList);
                showListView();
                break;
            default:
                buttonToggleGroup.check(R.id.ButtonToggleBoard);
                showBoardView();
                break;
        }


        doFilterSort();
    }

    @Override
    protected void onPause() {
        super.onPause();

        SharedPreferences.Editor editor = this.getPrefs().edit();

        editor.putString("gameslist_filter_event", Utils.getTrimmedOrDefault(editTextFilterEvent.getText(), ""));
        editor.putString("gameslist_filter_white", Utils.getTrimmedOrDefault(editTextFilterWhite.getText(), ""));
        editor.putString("gameslist_filter_black", Utils.getTrimmedOrDefault(editTextFilterBlack.getText(), ""));
        editor.putString("gameslist_filter_result", Utils.getTrimmedOrDefault(dropDownResult.getSelectionText(), ""));
        editor.putLong("gameslist_filter_date_after", pgnDateAfter.getDate().getTime());
        editor.putLong("gameslist_filter_date_before", pgnDateBefore.getDate().getTime());

        editor.putBoolean("gameslist_filter_switch_event", switchFilterEvent.isChecked());
        editor.putBoolean("gameslist_filter_switch_white", switchFilterWhite.isChecked());
        editor.putBoolean("gameslist_filter_switch_black", switchFilterBlack.isChecked());
        editor.putBoolean("gameslist_filter_switch_date_before", switchFilterDateBefore.isChecked());
        editor.putBoolean("gameslist_filter_switch_date_after", switchFilterDateAfter.isChecked());
        editor.putBoolean("gameslist_filter_switch_result", switchFilterResult.isChecked());

        editor.putString("gameslist_filter_order_by", Utils.getTrimmedOrDefault(dropDownOrderBy.getSelectionText(), ""));
        editor.putString("gameslist_filter_order_direction", Utils.getTrimmedOrDefault(dropDownOrderDirection.getSelectionText(), ""));

        editor.putLong("gameslist_current_game_id", currentGameId);

        int currentView = VIEW_BOARD;
        int buttonToggleId = buttonToggleGroup.getCheckedButtonId();
        if (buttonToggleId == R.id.ButtonTogglePgn) {
            currentView = VIEW_PGN;
        } else if (buttonToggleId == R.id.ButtonToggleList) {
            currentView = VIEW_LIST;
        }
        editor.putInt("gameslist_current_view", currentView);

        editor.commit();
    }

    @Override
    protected void saveGameFromDialog(SaveGameDialog.SaveGameResult result) {
        super.saveGameFromDialog(result);
        loadGame();
    }

    private void onQueryUpdated() {
        int count = cursor.getCount();

        int position = findPositionById(cursor, currentGameId);

        if (position >= 0) {
            seekBarGames.setProgress(position);
            seekBarGames.setMax(count > 0 ? count - 1 : 0);
            seekBarGames.setVisibility(count == 0 ? View.INVISIBLE : View.VISIBLE);
            textViewFilterInfo.setText("Results: " + count);
        } else {
            // @TODO
        }

        adapterGames.swapCursor(cursor);
    }

    private void loadGame() {
        int position = seekBarGames.getProgress();
        if (cursor == null) {
            return; // @TODO
        }
        int count = cursor.getCount();
        if (count <= position) {
            return; // @TODO
        }

        textViewTotal.setText("" + (position + (count == 0 ? 0 : 1)) + "/" + count);
        cursor.moveToPosition(position);

        currentGameId = Utils.getColumnLong(cursor, PGNColumns._ID);
        gameApi.loadPGN(Utils.getColumnString(cursor, PGNColumns.PGN));

        String event = Utils.getColumnString(cursor, PGNColumns.EVENT);
        String white = Utils.getColumnString(cursor, PGNColumns.WHITE);
        String black = Utils.getColumnString(cursor, PGNColumns.BLACK);
        String date = Utils.formatDate(Utils.getColumnDate(cursor, PGNColumns.DATE));
        String result = Utils.getColumnString(cursor, PGNColumns.RESULT);
        String rating = Float.toString(Utils.getColumnFloat(cursor, PGNColumns.RATING));

        textViewEvent.setText(event);
        textViewPlayerWhite.setText(white);
        textViewPlayerBlack.setText(black);
        textViewDate.setText(date);
        textViewResult.setText(result.equals("1/2-1/2") ?  "½-½" : result);
        textViewRating.setText(rating);

        gameApi.pgnTags.put("Event", event);
        gameApi.pgnTags.put("White", white);
        gameApi.pgnTags.put("Black", black);
        gameApi.pgnTags.put("Date", date);
        gameApi.pgnTags.put("Result", result);


        textViewWhitePieces.setText(getPiecesDescription(BoardConstants.WHITE));
        textViewBlackPieces.setText(getPiecesDescription(BoardConstants.BLACK));

        // pgn view

        ArrayList<PGNEntry> pgnEntries = gameApi.getPGNEntries();

        mapMoves.clear();
        for (int i = 0; i < pgnEntries.size(); i++) {
            String sMove =  pgnEntries.get(i).sMove;
            if (pgnEntries.get(i).duckMove != -1) {
                sMove += "@" + Pos.toString(pgnEntries.get(i).duckMove);
            }
            String nr = i % 2 == 0 ? ((i/2+1) + ". ") : " ";
            String annotation = pgnEntries.get(i).sAnnotation;
            int turn = (jni.getNumBoard() - 1 == i ? R.drawable.turnblack : 0);

            mapMoves.add(new MoveItem(nr, sMove, pgnEntries.get(i).move, annotation, turn));

        }

        adapterMoves.notifyDataSetChanged();
        gridLayoutPgn.smoothScrollToPosition(adapterMoves.getCount());
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
        final long id = Utils.getColumnLong(cursor, PGNColumns._ID);
        // the game is loaded via the GameApi
        SaveGameDialog saveDialog = new SaveGameDialog(this, gameApi.exportMovesPGN(), gameApi.pgnTags, id, this::saveGameFromDialog);
        saveDialog.show();
    }

    private void updateParams() {
        doFilterSort();
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

        Date dateAfter = pgnDateAfter.getDate();
        if (dateAfter != null && switchFilterDateAfter.isChecked()) {
            Log.d(TAG, "dateAfter " + dateAfter);
            whereParts.add(PGNColumns.DATE + " >= ? ");
            args.add(String.valueOf(dateAfter.getTime()));
        }

        Date dateBefore = pgnDateBefore.getDate();
        if (dateBefore != null && switchFilterDateBefore.isChecked()) {
            Log.d(TAG, "dateBefore " + dateBefore);
            whereParts.add(PGNColumns.DATE + " <= ? ");
            args.add(String.valueOf(dateBefore.getTime()));
        }

        String result =  Utils.getTrimmedOrNull(dropDownResult.getSelectionText());
        if (result != null && switchFilterResult.isChecked()) {
            whereParts.add(PGNColumns.RESULT + " = ?");
            args.add(result);
        }

        String selection = whereParts.isEmpty() ? null : TextUtils.join(" AND ", whereParts);
        String[] selectionArgs = args.isEmpty() ? null : args.toArray(new String[0]);

        sortBy = Utils.getTrimmedOrDefault(dropDownOrderBy.getSelectionText(), PGNColumns.DATE);
        sortOrder = Utils.getTrimmedOrDefault(dropDownOrderDirection.getSelectionText(), "DESC");

        String dbgArgs = selectionArgs == null ? "" : TextUtils.join("|", selectionArgs);
        Log.i(TAG, "runQuery " + selection + " " + dbgArgs + " BY " + sortBy + " " + sortOrder);

        //buttonFilters.setText("Filters" + (selectionArgs == null ? "" : " (" + selectionArgs.length + ")"));
        buttonFilters.setChecked(selectionArgs != null && selectionArgs.length > 0);

        if (cursor != null && !cursor.isClosed()) {
            cursor.close();
        }
        cursor = getContentResolver().query(MyPGNProvider.CONTENT_URI, PGNColumns.COLUMNS, selection, selectionArgs, sortBy + " " + sortOrder);
        onQueryUpdated();
    }

    private int findPositionById(Cursor c, long id) {
        if (c == null) return -1;
        int idCol = c.getColumnIndex(PGNColumns._ID);
        if (idCol < 0) return -1;

        for (c.moveToFirst(); !c.isAfterLast(); c.moveToNext()) {
            if (c.getLong(idCol) == id) {
                return c.getPosition();
            }
        }
        return 0;
    }

    private void showBoardView() {
        chessBoardView.setVisibility(View.VISIBLE);
        gridLayoutPgn.setVisibility(View.INVISIBLE);
        listView.setVisibility(View.GONE);
        scrollView.setVisibility(View.VISIBLE);
    }

    private void showPgnView() {
        chessBoardView.setVisibility(View.INVISIBLE);
        gridLayoutPgn.setVisibility(View.VISIBLE);
        listView.setVisibility(View.GONE);
        scrollView.setVisibility(View.VISIBLE);
    }

    private void showListView() {
        chessBoardView.setVisibility(View.INVISIBLE);
        gridLayoutPgn.setVisibility(View.INVISIBLE);
        listView.setVisibility(View.VISIBLE);
        scrollView.setVisibility(View.GONE);
    }

    private class FilterDialog extends Dialog {
        public FilterDialog(@NonNull Context context) {
            super(context, R.style.ChessDialogTheme);

            setContentView(R.layout.gameslist_filters);
        }

        public void init() {
            SharedPreferences prefs = getPrefs();

            final TextWatcher textWatcher = new TextWatcher() {
                @Override
                public void afterTextChanged(Editable s) {
                    updateParams();
                }

                @Override
                public void beforeTextChanged(CharSequence s, int start, int count, int after) {}

                @Override
                public void onTextChanged(CharSequence s, int start, int before, int count) {}
            };

            MaterialButton buttonFilterClose = findViewById(R.id.ButtonFilterClose);
            buttonFilterClose.setOnClickListener(v -> dismiss());

            textViewFilterInfo = findViewById(R.id.TextViewFilterInfo);

            editTextFilterWhite = findViewById(R.id.EditTextFilterWhite);
            editTextFilterWhite.setText(prefs.getString("gameslist_filter_white", ""));
            editTextFilterWhite.addTextChangedListener(textWatcher);

            editTextFilterBlack = findViewById(R.id.EditTextFilterBlack);
            editTextFilterBlack.setText(prefs.getString("gameslist_filter_black", ""));
            editTextFilterBlack.addTextChangedListener(textWatcher);

            editTextFilterEvent = findViewById(R.id.EditTextFilterEvent);
            editTextFilterEvent.setText(prefs.getString("gameslist_filter_event", ""));
            editTextFilterEvent.addTextChangedListener(textWatcher);

            pgnDateAfter = findViewById(R.id.PGNDateAfter);
            pgnDateAfter.setDate(new Date(prefs.getLong("gameslist_filter_date_after", Calendar.getInstance().getTime().getTime())));
            pgnDateAfter.addOnTextChangedListener(textWatcher);

            pgnDateBefore = findViewById(R.id.PGNDateBefore);
            pgnDateBefore.setDate(new Date(prefs.getLong("gameslist_filter_date_before", Calendar.getInstance().getTime().getTime())));
            pgnDateBefore.addOnTextChangedListener(textWatcher);

            switchFilterWhite = findViewById(R.id.SwitchFilterWhite);
            switchFilterWhite.setChecked(prefs.getBoolean("gameslist_filter_switch_white", false));
            switchFilterWhite.setOnCheckedChangeListener((v, isChecked) -> updateParams());

            switchFilterBlack = findViewById(R.id.SwitchFilterBlack);
            switchFilterBlack.setChecked(prefs.getBoolean("gameslist_filter_switch_black", false));
            switchFilterBlack.setOnCheckedChangeListener((v, isChecked) -> updateParams());

            switchFilterEvent = findViewById(R.id.SwitchFilterEvent);
            switchFilterEvent.setChecked(prefs.getBoolean("gameslist_filter_switch_event", false));
            switchFilterEvent.setOnCheckedChangeListener((v, isChecked) -> updateParams());

            switchFilterDateAfter = findViewById(R.id.SwitchFilterDateAfter);
            switchFilterDateAfter.setChecked(prefs.getBoolean("gameslist_filter_switch_date_after", false));
            switchFilterDateAfter.setOnCheckedChangeListener((v, isChecked) -> updateParams());

            switchFilterDateBefore = findViewById(R.id.SwitchFilterDateBefore);
            switchFilterDateBefore.setChecked(prefs.getBoolean("gameslist_filter_switch_date_before", false));
            switchFilterDateBefore.setOnCheckedChangeListener((v, isChecked) -> updateParams());

            switchFilterResult = findViewById(R.id.SwitchFilterResult);
            switchFilterResult.setChecked(prefs.getBoolean("gameslist_filter_switch_result", false));
            switchFilterResult.setOnCheckedChangeListener((v, isChecked) -> updateParams());

            String[] resultOptions = {"1-0", "0-1", "1/2-1/2", "*"};
            dropDownResult = findViewById(R.id.DropdownResult);
            dropDownResult.setSelectionText(prefs.getString("gameslist_filter_result", "1-0"));
            dropDownResult.setItems(resultOptions);
            dropDownResult.addOnTextChangedListener(textWatcher);

            String[] orderByOptions = {PGNColumns.DATE, PGNColumns.WHITE, PGNColumns.BLACK, PGNColumns.EVENT, PGNColumns.RESULT};
            dropDownOrderBy = findViewById(R.id.DropdownOrderBy);
            dropDownOrderBy.setSelectionText(PGNColumns.DATE);
            dropDownOrderBy.setItems(orderByOptions);
            dropDownOrderBy.setSelectionText(prefs.getString("gameslist_filter_order_by", PGNColumns.DATE));
            dropDownOrderBy.addOnTextChangedListener(textWatcher);

            String[] orderDirectionOptions = {"ASC", "DESC"};
            dropDownOrderDirection = findViewById(R.id.DropdownOrderDirection);
            dropDownOrderDirection.setItems(orderDirectionOptions);
            dropDownOrderDirection.setSelectionText(prefs.getString("gameslist_filter_order_direction", "DESC"));
            dropDownOrderDirection.addOnTextChangedListener(textWatcher);
        }
    }
}
