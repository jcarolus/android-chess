package jwtc.android.chess.gamedatabase;

import android.content.Intent;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;
import android.view.View;
import android.widget.TextView;
import androidx.annotation.NonNull;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import jwtc.android.chess.GamesListActivity;
import jwtc.android.chess.R;
import jwtc.android.chess.activities.BaseActivity;
import jwtc.android.chess.helpers.ActivityHelper;
import jwtc.android.chess.helpers.GameStore;
import jwtc.chess.GameSelection;

/** Selection screen shared by the Game Database and Study entry points. */
public class GameSelectionActivity extends BaseActivity {
    public static final String EXTRA_RETURN_SELECTION = "returnGameSelection";
    private final ExecutorService executor = Executors.newSingleThreadExecutor();
    private final Handler handler = new Handler(Looper.getMainLooper());
    private final Runnable countQuery = this::queryCount;
    private GameSelectionForm form;
    private View open, surprise;
    private TextView count, error;
    private int generation;
    protected int matchingCount;
    private boolean ready;
    private boolean busy;

    protected String preferencePrefix() {
        return GameSelectionPreferences.DATABASE;
    }

    protected boolean isStudySelection() {
        return false;
    }

    @Override
    public void onCreate(Bundle state) {
        super.onCreate(state);
        setContentView(R.layout.game_selection);
        View root = findViewById(R.id.SelectionRoot);
        ActivityHelper.fixPaddings(this, root);
        open = findViewById(R.id.SelectionOpen);
        surprise = findViewById(R.id.SelectionSurprise);
        count = findViewById(R.id.TextViewFilterInfo);
        error = findViewById(R.id.TextViewFilterError);
        surprise.setVisibility(isStudySelection() ? View.VISIBLE : View.GONE);
        ((TextView) open)
                .setText(
                        isStudySelection()
                                ? R.string.gameslist_open
                                : R.string.gameslist_show_games);
        open.setEnabled(false);
        surprise.setEnabled(false);
        GameSelection selection =
                state == null ? null : (GameSelection) state.getSerializable(GameSelection.EXTRA);
        if (selection == null) {
            selection = (GameSelection) getIntent().getSerializableExtra(GameSelection.EXTRA);
        }
        if (selection == null) {
            selection = GameSelectionPreferences.load(getPrefs(), preferencePrefix());
        }
        form = new GameSelectionForm(root, selection, this::refreshCount);
        open.setOnClickListener(v -> submit(false));
        surprise.setOnClickListener(v -> submit(true));
        configureMenu();
    }

    @Override
    protected void onResume() {
        super.onResume();
        refreshCount();
    }

    @Override
    protected void onPause() {
        GameSelectionPreferences.save(getPrefs(), preferencePrefix(), currentSelection());
        super.onPause();
    }

    @Override
    protected void onSaveInstanceState(@NonNull Bundle state) {
        state.putSerializable(GameSelection.EXTRA, currentSelection());
        super.onSaveInstanceState(state);
    }

    protected final GameSelection currentSelection() {
        return form.read();
    }

    protected final boolean canSubmit() {
        return ready && !busy;
    }

    protected final void setBusy(boolean value) {
        busy = value;
        refreshCount();
    }

    protected final void refreshCount() {
        ++generation;
        handler.removeCallbacks(countQuery);
        ready = false;
        open.setEnabled(false);
        surprise.setEnabled(false);
        GameSelection selection = currentSelection();
        if (!selection.hasValidDates()) {
            error.setText(R.string.gameslist_invalid_dates);
            error.setVisibility(View.VISIBLE);
            count.setText("");
            return;
        }
        error.setVisibility(View.GONE);
        count.setText(R.string.gameslist_loading);
        if (!busy) {
            handler.postDelayed(countQuery, 200);
        }
    }

    private void queryCount() {
        int request = generation;
        GameSelection selection = currentSelection();
        executor.execute(
                () -> {
                    try {
                        int matches = new GameStore(getContentResolver()).count(selection.filter());
                        handler.post(
                                () -> {
                                    if (request != generation || isDestroyed() || isFinishing()) {
                                        return;
                                    }
                                    matchingCount = matches;
                                    ready = !isStudySelection() || matches > 0;
                                    count.setText(
                                            getString(R.string.gameslist_result_count, matches));
                                    open.setEnabled(canSubmit());
                                    surprise.setEnabled(canSubmit());
                                });
                    } catch (Exception failure) {
                        Log.w("GameSelection", "Could not count matching games", failure);
                        handler.post(
                                () -> {
                                    if (request == generation && !isDestroyed() && !isFinishing()) {
                                        count.setText(R.string.game_selection_load_failed);
                                    }
                                });
                    }
                });
    }

    private void submit(boolean random) {
        if (!canSubmit()) {
            return;
        }
        GameSelection selection = currentSelection();
        GameSelectionPreferences.save(getPrefs(), preferencePrefix(), selection);
        openSelection(selection, random);
    }

    protected void openSelection(GameSelection selection, boolean random) {
        if (getIntent().getBooleanExtra(EXTRA_RETURN_SELECTION, false)) {
            setResult(RESULT_OK, new Intent().putExtra(GameSelection.EXTRA, selection));
            finish();
            return;
        }
        startActivity(
                new Intent(this, GamesListActivity.class).putExtra(GameSelection.EXTRA, selection));
    }

    protected void configureMenu() {
        findViewById(R.id.ButtonMenu).setVisibility(View.GONE);
    }

    @Override
    protected void onDestroy() {
        ++generation;
        handler.removeCallbacksAndMessages(null);
        executor.shutdownNow();
        super.onDestroy();
    }
}
