package jwtc.android.chess;

import java.text.SimpleDateFormat;
import java.util.Calendar;

import android.content.Intent;
import android.content.SharedPreferences;
import android.database.Cursor;
import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ImageButton;
import android.widget.SeekBar;
import android.widget.TextView;

import jwtc.android.chess.activities.ChessBoardActivity;
import jwtc.android.chess.helpers.ActivityHelper;
import jwtc.android.chess.helpers.MyPGNProvider;
import jwtc.android.chess.play.PlayActivity;
import jwtc.android.chess.services.GameApi;
import jwtc.chess.PGNColumns;

public class GamesListActivity extends ChessBoardActivity {
    private static final String TAG = "GamesListActivity";

    private EditText editTextSearch, editTextPlayerWhite, editTextPlayerBlack;
    private String sortOrder, sortBy;
    private View _viewSortRating, _viewSortWhite, _viewSortBlack, _viewSortId, _viewSortDate, _viewSortEvent;
    private TextView textViewResult, textViewTotal;
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

        _viewSortRating = (View) findViewById(R.id.sort_rating);
        _viewSortRating.setOnClickListener(new View.OnClickListener() {
            public void onClick(View arg0) {

                if (sortBy.equals(PGNColumns.RATING))
                    flipSortOrder();

                sortBy = PGNColumns.RATING;
                doFilterSort();
            }
        });
        _viewSortWhite = (View) findViewById(R.id.sort_text_name1);
        _viewSortWhite.setOnClickListener(new View.OnClickListener() {
            public void onClick(View arg0) {
                if (sortBy.equals(PGNColumns.WHITE))
                    flipSortOrder();

                sortBy = PGNColumns.WHITE;
                doFilterSort();
            }
        });
        _viewSortBlack = (View) findViewById(R.id.sort_text_name2);
        _viewSortBlack.setOnClickListener(new View.OnClickListener() {
            public void onClick(View arg0) {
                if (sortBy.equals(PGNColumns.BLACK))
                    flipSortOrder();

                sortBy = PGNColumns.BLACK;
                doFilterSort();
            }
        });
        _viewSortId = (View) findViewById(R.id.sort_text_id);
        _viewSortId.setOnClickListener(new View.OnClickListener() {
            public void onClick(View arg0) {
                if (sortBy.equals(PGNColumns._ID))
                    flipSortOrder();

                sortBy = PGNColumns._ID;
                doFilterSort();
            }
        });
        _viewSortEvent = (View) findViewById(R.id.sort_text_event);
        _viewSortEvent.setOnClickListener(new View.OnClickListener() {
            public void onClick(View arg0) {
                if (sortBy.equals(PGNColumns.EVENT))
                    flipSortOrder();

                sortBy = PGNColumns.EVENT;
                doFilterSort();
            }
        });
        _viewSortDate = (View) findViewById(R.id.sort_text_date);
        _viewSortDate.setOnClickListener(new View.OnClickListener() {
            public void onClick(View arg0) {
                if (sortBy.equals(PGNColumns.DATE))
                    flipSortOrder();

                sortBy = PGNColumns.DATE;
                doFilterSort();
            }
        });

        editTextSearch = (EditText) findViewById(R.id.EditTextGamesList);
        editTextSearch.addTextChangedListener(new TextWatcher() {

            public void afterTextChanged(Editable s) {
                doFilterSort();
            }

            public void beforeTextChanged(CharSequence s, int start, int count, int after) {
            }

            public void onTextChanged(CharSequence s, int start, int before, int count) {
            }
        });

        editTextPlayerWhite = findViewById(R.id.EditTextPlayerWhite);
        editTextPlayerBlack = findViewById(R.id.EditTextPlayerBlack);
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

        // DEBUG
        //getContentResolver().delete(MyPGNProvider.CONTENT_URI, "1=1", null);
        /////////////////////////////////////////////////////////////////////////////////////


		 /*
		 new FilterQueryProvider(){

			public Cursor runQuery(CharSequence constraint) {
				String s = constraint.toString().replace("'", "''");
				String sWhere = PGNColumns.WHITE + " LIKE('%" + s + "%') OR " +
								PGNColumns.BLACK + " LIKE('%" + s + "%') OR " +
								PGNColumns.EVENT + " LIKE('%" + s + "%')";
				Log.i("runQuery", sWhere + " BY " + _sortOrder);
				return managedQuery(MyPGNProvider.CONTENT_URI, PGNColumns.COLUMNS, sWhere, null, _sortOrder);
			}
			 
		 });
        */

        afterCreate();
    }

    @Override
    protected void onResume() {
        super.onResume();

        cursor = getContentResolver().query(MyPGNProvider.CONTENT_URI, PGNColumns.COLUMNS, null, null, sortBy + " " + sortOrder);

        onQueryUpdated();
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

    private void updateProgressTotal() {
        int count =  cursor.getCount();
        textViewTotal.setText("" + (seekBarGames.getProgress() + (count == 0 ? 0 : 1)) + "/" + count);
    }

    private void doFilterSort() {
        String s = "%" + editTextSearch.getText().toString() + "%";
        String sWhere = PGNColumns.WHITE + " LIKE ? OR " +
                PGNColumns.BLACK + " LIKE ? OR " +
                PGNColumns.EVENT + " LIKE ?";
        String[] selectionArgs = new String[] {
            s, s, s
        };
        Log.i("runQuery", sWhere + " BY " + sortOrder);

        cursor = getContentResolver().query(MyPGNProvider.CONTENT_URI, PGNColumns.COLUMNS, sWhere, selectionArgs, sortBy + " " + sortOrder);
        onQueryUpdated();
    }

    private void flipSortOrder() {
        sortOrder = sortOrder.equals("DESC") ? "ASC" : "DESC";
    }

    private String convText(TextView v, String text) {
        if (v.getId() == R.id.text_date) {
            SimpleDateFormat formatter = new SimpleDateFormat("yyyy.MM.dd");
            Calendar c = Calendar.getInstance();
            c.setTimeInMillis(Long.parseLong(text));
            return formatter.format(c.getTime());
        }
        return text;
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
}
