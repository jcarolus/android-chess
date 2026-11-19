package jwtc.android.chess;

import android.content.Intent;
import android.content.SharedPreferences;
import android.database.Cursor;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;
import android.view.View;
import android.view.ViewGroup;
import android.widget.GridView;
import android.widget.ListView;
import android.widget.RatingBar;
import android.widget.ScrollView;
import android.widget.SeekBar;
import android.widget.SimpleCursorAdapter;
import android.widget.TextView;
import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.core.view.ViewCompat;
import androidx.core.view.accessibility.AccessibilityNodeInfoCompat;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.button.MaterialButtonToggleGroup;
import java.util.ArrayList;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import jwtc.android.chess.activities.ChessBoardActivity;
import jwtc.android.chess.analyze.AnalyzeActivity;
import jwtc.android.chess.gamedatabase.GameSelectionActivity;
import jwtc.android.chess.gamedatabase.GameSelectionPreferences;
import jwtc.android.chess.helpers.ActivityHelper;
import jwtc.android.chess.helpers.GameStore;
import jwtc.android.chess.helpers.Utils;
import jwtc.android.chess.play.MenuDialog;
import jwtc.android.chess.play.MoveItem;
import jwtc.android.chess.play.MoveItemAdapter;
import jwtc.android.chess.play.PlayActivity;
import jwtc.android.chess.play.SaveGameDialog;
import jwtc.android.chess.services.GameApi;
import jwtc.android.chess.tools.ImportActivity;
import jwtc.android.chess.tools.ImportService;
import jwtc.android.chess.views.ChessSquareView;
import jwtc.chess.GameFilter;
import jwtc.chess.GameRecord;
import jwtc.chess.GameSelection;
import jwtc.chess.PGNColumns;
import jwtc.chess.PGNEntry;
import jwtc.chess.PgnDate;
import jwtc.chess.Pos;
import jwtc.chess.board.BoardConstants;

public class GamesListActivity extends ChessBoardActivity {
    private static final String TAG = "GamesListActivity";
    private static final int VIEW_BOARD = 1, VIEW_PGN = 2, VIEW_LIST = 3;
    private static final String EMPTY_BOARD_FEN = "8/8/8/8/8/8/8/8 w - - 0 1";
    private ActivityResultLauncher<String[]> importGames;
    private ActivityResultLauncher<String> exportGames;
    private GameSelection selection;
    private TextView textViewResult,
            textViewTotal,
            textViewPlayerWhite,
            textViewPlayerBlack,
            textViewEvent,
            textViewDate,
            textViewRating;
    private MaterialButton buttonFilters, buttonOpen, buttonAnalyze, buttonEdit, buttonDelete;
    private MaterialButtonToggleGroup buttonToggleGroup;
    private GridView gridLayoutPgn;
    private ListView listView;
    private ScrollView scrollView;
    private final ArrayList<MoveItem> mapMoves = new ArrayList<MoveItem>();
    private MoveItemAdapter adapterMoves;
    private SimpleCursorAdapter adapterGames;
    private SeekBar seekBarGames;
    private Cursor cursor;
    private long currentGameId;
    private final ExecutorService queryExecutor = Executors.newSingleThreadExecutor();
    private final Handler mainHandler = new Handler(Looper.getMainLooper());
    private int queryGeneration = 0;
    private boolean updatingSelection;

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        importGames =
                registerForActivityResult(
                        new ActivityResultContracts.OpenDocument(),
                        uri -> {
                            if (uri != null) {
                                startDatabaseTransfer(uri, ImportService.IMPORT_GAMES);
                            }
                        });
        exportGames =
                registerForActivityResult(
                        new ActivityResultContracts.CreateDocument("application/x-chess-pgn"),
                        uri -> {
                            if (uri != null) {
                                startDatabaseTransfer(uri, ImportService.EXPORT_GAME_DATABASE);
                            }
                        });

        setContentView(R.layout.gameslist);

        ActivityHelper.fixPaddings(this, findViewById(R.id.root_layout));

        gameApi = new GameApi();

        selection = (GameSelection) getIntent().getSerializableExtra(GameSelection.EXTRA);
        if (selection == null) {
            selection =
                    GameSelectionPreferences.load(getPrefs(), GameSelectionPreferences.DATABASE);
        }

        textViewPlayerWhite = findViewById(R.id.TextViewPlayerWhite);
        textViewPlayerBlack = findViewById(R.id.TextViewPlayerBlack);
        textViewRating = findViewById(R.id.TextViewRating);
        textViewResult = findViewById(R.id.TextViewResult);
        textViewTotal = findViewById(R.id.TextViewTotal);
        textViewWhitePieces = findViewById(R.id.TextViewWhitePieces);
        textViewBlackPieces = findViewById(R.id.TextViewBlackPieces);

        seekBarGames = findViewById(R.id.SeekBarGames);
        seekBarGames.setOnSeekBarChangeListener(
                new SeekBar.OnSeekBarChangeListener() {
                    @Override
                    public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                        if (!updatingSelection) {
                            loadGame();
                        }
                    }

                    @Override
                    public void onStartTrackingTouch(SeekBar seekBar) {}

                    @Override
                    public void onStopTrackingTouch(SeekBar seekBar) {}
                });

        MaterialButton butPrev = findViewById(R.id.ButtonPrevious);
        butPrev.setOnClickListener(
                v -> {
                    int progress = seekBarGames.getProgress();
                    if (progress > 0) {
                        seekBarGames.setProgress(progress - 1);
                    }
                });

        MaterialButton butNext = findViewById(R.id.ButtonNext);
        butNext.setOnClickListener(
                v -> {
                    int progress = seekBarGames.getProgress();
                    if (cursor != null && progress + 1 < cursor.getCount()) {
                        seekBarGames.setProgress(progress + 1);
                    }
                });

        buttonOpen = findViewById(R.id.ButtonOpen);
        buttonOpen.setOnClickListener(v -> openGame());

        buttonAnalyze = findViewById(R.id.ButtonAnalyze);
        buttonAnalyze.setOnClickListener(v -> analyzeGame());

        buttonEdit = findViewById(R.id.ButtonEdit);
        buttonEdit.setOnClickListener(v -> editGame());

        buttonDelete = findViewById(R.id.ButtonDelete);
        buttonDelete.setOnClickListener(v -> deleteGame());

        textViewDate = findViewById(R.id.TextViewDate);
        textViewEvent = findViewById(R.id.TextViewEvent);

        buttonFilters = findViewById(R.id.ButtonOpenFilters);
        buttonFilters.setOnClickListener(
                v ->
                        startActivity(
                                new Intent(this, GameSelectionActivity.class)
                                        .addFlags(
                                                Intent.FLAG_ACTIVITY_CLEAR_TOP
                                                        | Intent.FLAG_ACTIVITY_SINGLE_TOP)));

        final MenuDialog menuDialog =
                new MenuDialog(
                        this,
                        (requestCode, data) -> {
                            String item = data.getString("item");
                            if (getString(R.string.pgntool_import_explanation).equals(item)) {
                                importGames.launch(new String[] {"*/*"});
                            } else if (getString(R.string.pgntool_export_explanation)
                                    .equals(item)) {
                                exportGames.launch("chess.pgn");
                            } else if (getString(R.string.menu_delete_game_database).equals(item)) {
                                confirmDeleteDatabase();
                            }
                        },
                        0,
                        R.array.gameslist_menu);
        findViewById(R.id.ButtonMenu).setOnClickListener(v -> menuDialog.show());

        buttonToggleGroup = findViewById(R.id.ButtonToggleGroupView);

        buttonToggleGroup.addOnButtonCheckedListener(
                (group, checkedId, isChecked) -> {
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

        adapterGames =
                new SimpleCursorAdapter(
                        this,
                        R.layout.game_row,
                        null,
                        new String[] {
                            PGNColumns._ID,
                            PGNColumns.WHITE,
                            PGNColumns.BLACK,
                            PGNColumns.DATE,
                            PGNColumns.EVENT,
                            PGNColumns.RATING,
                            PGNColumns.RESULT
                        },
                        new int[] {
                            R.id.text_id,
                            R.id.text_name1,
                            R.id.text_name2,
                            R.id.text_date,
                            R.id.text_event,
                            R.id.rating,
                            R.id.text_result
                        }) {
                    @Override
                    public View getView(int position, View convertView, ViewGroup parent) {
                        View row = super.getView(position, convertView, parent);
                        // Accessibility services announce what a tap and a long press do on a
                        // row, so the long-press delete is discoverable without sight.
                        ViewCompat.replaceAccessibilityAction(
                                row,
                                AccessibilityNodeInfoCompat.AccessibilityActionCompat.ACTION_CLICK,
                                getString(R.string.gameslist_open),
                                (view, arguments) -> {
                                    cursor.moveToPosition(position);
                                    openGame();
                                    return true;
                                });
                        ViewCompat.replaceAccessibilityAction(
                                row,
                                AccessibilityNodeInfoCompat.AccessibilityActionCompat
                                        .ACTION_LONG_CLICK,
                                getString(R.string.choice_delete),
                                (view, arguments) -> {
                                    cursor.moveToPosition(position);
                                    deleteGame();
                                    return true;
                                });
                        return row;
                    }
                };

        adapterGames.setViewBinder(
                (view, cursor, columnIndex) -> {
                    int nImageIndex = cursor.getColumnIndex(PGNColumns.RATING);
                    if (nImageIndex == columnIndex) {
                        float fR = Utils.getColumnFloat(cursor, PGNColumns.RATING);
                        ((RatingBar) view).setRating(fR);
                        // ((RatingBar)view).setRating(0.4F);
                        return true;
                    }
                    int nDateIndex = cursor.getColumnIndex(PGNColumns.DATE);
                    if (nDateIndex == columnIndex) {
                        ((TextView) view)
                                .setText(
                                        PgnDate.format(
                                                Utils.getColumnDate(cursor, PGNColumns.DATE)));
                        return true;
                    }
                    int nResultIndex = cursor.getColumnIndex(PGNColumns.RESULT);
                    if (nResultIndex == columnIndex) {
                        String result = Utils.getColumnString(cursor, PGNColumns.RESULT);
                        ((TextView) view).setText(result.equals("1/2-1/2") ? "½-½" : result);
                        return true;
                    }

                    return false;
                });

        listView = findViewById(R.id.ListView);
        listView.setAdapter(adapterGames);
        listView.setOnItemClickListener(
                (parent, view, position, id) -> {
                    cursor.moveToPosition(position);
                    openGame();
                });
        listView.setOnItemLongClickListener(
                (parent, view, position, id) -> {
                    cursor.moveToPosition(position);
                    deleteGame();
                    return true;
                });

        scrollView = findViewById(R.id.ScrollView);

        afterCreate();
        View boardAreaLayout = findViewById(R.id.board_area);
        if (boardAreaLayout == null) {
            boardAreaLayout = findViewById(R.id.includeboard);
        }
        // The ScrollView's height is defined by its board-to-menu constraints. Passing it as the
        // controls view would also assign a fixed runtime height that does not reserve LayoutMenu,
        // over-constraining it and shifting its top edge over the board in portrait mode.
        initBoardLayoutSizing(findViewById(R.id.root_layout), boardAreaLayout, null, null, null);
    }

    @Override
    public void afterCreate() {
        Log.d(TAG, " afterCreate");

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
    protected void onDestroy() {
        super.onDestroy();
        ++queryGeneration;
        queryExecutor.shutdownNow();
        adapterGames.swapCursor(null);
        if (cursor != null && !cursor.isClosed()) {
            cursor.close();
        }
    }

    @Override
    protected void saveGameFromDialog(SaveGameDialog.SaveGameResult result) {
        super.saveGameFromDialog(result);
        doFilterSort();
    }

    private void onQueryUpdated() {
        int count = cursor == null ? 0 : cursor.getCount();
        adapterGames.swapCursor(cursor);
        if (count == 0) {
            seekBarGames.setVisibility(View.INVISIBLE);
            clearPreview();
            return;
        }

        int position = findPositionById(cursor, currentGameId);
        updatingSelection = true;
        seekBarGames.setMax(count - 1);
        seekBarGames.setProgress(position);
        updatingSelection = false;
        seekBarGames.setVisibility(View.VISIBLE);
        loadGame();
    }

    private void loadGame() {
        int position = seekBarGames.getProgress();
        if (cursor == null || cursor.isClosed() || !cursor.moveToPosition(position)) {
            clearPreview();
            return;
        }
        int count = cursor.getCount();

        String totalText = getString(R.string.gameslist_game_of_total, position + 1, count);
        textViewTotal.setText(totalText);

        GameRecord record = GameStore.fromCursor(cursor);
        currentGameId = record.id;
        boolean loaded = gameApi.loadGame(record);
        if (!loaded) {
            // Never leave the previous game's board under this record's header.
            gameApi.initFEN(EMPTY_BOARD_FEN, true);
            doToast(getString(R.string.gameslist_load_failed));
        }
        buttonOpen.setEnabled(loaded);
        buttonAnalyze.setEnabled(loaded);
        buttonEdit.setEnabled(loaded);
        buttonDelete.setEnabled(true);

        String rating = Float.toString(record.rating == null ? 0f : record.rating) + "★";

        textViewEvent.setText(record.event);
        textViewPlayerWhite.setText(record.white);
        textViewPlayerBlack.setText(record.black);
        textViewDate.setText(PgnDate.format(record.date));
        textViewResult.setText(record.result.equals("1/2-1/2") ? "½-½" : record.result);
        textViewRating.setText(rating);

        if (loaded) {
            gameApi.pgnTags.put("Result", record.result);
        }

        textViewWhitePieces.setText(getPiecesDescription(BoardConstants.WHITE));
        textViewBlackPieces.setText(getPiecesDescription(BoardConstants.BLACK));

        // pgn view

        ArrayList<PGNEntry> pgnEntries = gameApi.getPGNEntries();

        mapMoves.clear();
        for (int i = 0; i < pgnEntries.size(); i++) {
            String sMove = pgnEntries.get(i).sMove;
            if (pgnEntries.get(i).duckMove != -1) {
                sMove += "@" + Pos.toString(pgnEntries.get(i).duckMove);
            }
            String nr = i % 2 == 0 ? ((i / 2 + 1) + ". ") : " ";
            String annotation = pgnEntries.get(i).sAnnotation;
            int turn = (gameApi.getNumBoard() - 1 == i ? R.drawable.turnblack : 0);

            mapMoves.add(new MoveItem(nr, sMove, pgnEntries.get(i).move, annotation, turn));
        }

        adapterMoves.notifyDataSetChanged();
        gridLayoutPgn.smoothScrollToPosition(adapterMoves.getCount());
    }

    private void clearPreview() {
        gameApi.initFEN(EMPTY_BOARD_FEN, true);
        textViewTotal.setText(getString(R.string.gameslist_game_of_total, 0, 0));
        textViewPlayerWhite.setText("");
        textViewPlayerBlack.setText("");
        textViewEvent.setText("");
        textViewDate.setText("");
        textViewResult.setText("");
        textViewRating.setText("");
        textViewWhitePieces.setText("");
        textViewBlackPieces.setText("");
        mapMoves.clear();
        adapterMoves.notifyDataSetChanged();
        buttonOpen.setEnabled(false);
        buttonAnalyze.setEnabled(false);
        buttonEdit.setEnabled(false);
        buttonDelete.setEnabled(false);
    }

    private Long selectedGameId() {
        if (cursor == null
                || cursor.isClosed()
                || cursor.getPosition() < 0
                || cursor.getPosition() >= cursor.getCount()) {
            return null;
        }
        return Utils.getColumnLong(cursor, PGNColumns._ID);
    }

    private void openGame() {
        Long id = selectedGameId();
        if (id == null) {
            return;
        }

        Log.d(TAG, "openGame " + id);
        SharedPreferences.Editor editor = getPrefs().edit();
        editor.putLong("game_id", id);
        editor.putBoolean("opponent", false);
        editor.commit();

        Intent i = new Intent();
        i.setClass(this, PlayActivity.class);
        i.setFlags(Intent.FLAG_ACTIVITY_REORDER_TO_FRONT);

        startActivity(i);
    }

    private void analyzeGame() {
        Long id = selectedGameId();
        if (id == null) {
            return;
        }

        Log.d(TAG, "openGame " + id);
        Intent i = new Intent();
        i.setClass(this, AnalyzeActivity.class);
        i.putExtra(AnalyzeActivity.EXTRA_GAME_ID, id);
        i.putExtra(AnalyzeActivity.EXTRA_SESSION_ID, java.util.UUID.randomUUID().toString());
        i.setFlags(Intent.FLAG_ACTIVITY_REORDER_TO_FRONT);

        startActivity(i);
    }

    private void startDatabaseTransfer(Uri uri, int mode) {
        Intent intent = new Intent(this, ImportActivity.class);
        intent.setData(uri);
        intent.addFlags(
                Intent.FLAG_GRANT_READ_URI_PERMISSION | Intent.FLAG_GRANT_WRITE_URI_PERMISSION);
        intent.putExtra("mode", mode);
        startActivity(intent);
    }

    private void confirmDeleteDatabase() {
        GameStore games = new GameStore(getContentResolver());
        openConfirmDialog(
                getString(R.string.pgntool_confirm_delete_games)
                        + "\n\n"
                        + getString(R.string.pgntool_delete_item_count, games.count()),
                getString(R.string.choice_delete),
                getString(R.string.button_cancel),
                () -> {
                    games.deleteAll();
                    currentGameId = 0;
                    getPrefs().edit().putLong("game_id", 0).apply();
                    doFilterSort();
                    doToast(getString(R.string.pgntool_games_deleted));
                },
                null);
    }

    private void deleteGame() {
        Long id = selectedGameId();
        if (id == null || id < 0) {
            return;
        }

        openConfirmDialog(
                getString(R.string.title_delete_game),
                getString(R.string.button_ok),
                getString(R.string.button_cancel),
                () -> {
                    new GameStore(getContentResolver()).delete(id);
                    doFilterSort();
                },
                null);
    }

    private void editGame() {
        Long id = selectedGameId();
        if (id == null) {
            return;
        }
        // the game is loaded via the GameApi
        SaveGameDialog saveDialog = new SaveGameDialog(this, gameApi, id, this::saveGameFromDialog);
        saveDialog.show();
    }

    private void doFilterSort() {
        GameFilter filter = selection.filter();

        Log.i(TAG, "runQuery " + filter.selection() + " BY " + filter.orderBy());

        buttonFilters.setChecked(!filter.isEmpty());

        final int generation = ++queryGeneration;
        final GameStore store = new GameStore(getContentResolver());
        queryExecutor.execute(
                () -> {
                    Cursor newCursor = store.query(filter);
                    mainHandler.post(
                            () -> {
                                if (generation != queryGeneration) {
                                    if (newCursor != null && !newCursor.isClosed()) {
                                        newCursor.close();
                                    }
                                    return;
                                }
                                if (cursor != null && !cursor.isClosed()) {
                                    cursor.close();
                                }
                                cursor = newCursor;
                                onQueryUpdated();
                            });
                });
    }

    private int findPositionById(Cursor c, long id) {
        if (!c.moveToFirst()) {
            return -1;
        }
        do {
            if (Utils.getColumnLong(c, PGNColumns._ID) == id) {
                return c.getPosition();
            }
        } while (c.moveToNext());
        c.moveToFirst();
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
}
