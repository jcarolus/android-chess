package jwtc.android.chess;

import java.text.SimpleDateFormat;
import java.util.Calendar;

import android.database.Cursor;
import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
import android.util.Log;
import android.view.View;
import android.widget.EditText;
import android.widget.SeekBar;
import android.widget.TextView;

import jwtc.android.chess.activities.ChessBoardActivity;
import jwtc.android.chess.helpers.ActivityHelper;
import jwtc.android.chess.helpers.MyPGNProvider;
import jwtc.android.chess.puzzle.MyPuzzleProvider;
import jwtc.android.chess.services.GameApi;
import jwtc.chess.PGNColumns;
import jwtc.chess.PGNProvider;

public class GamesListActivity extends ChessBoardActivity {
    private static final String TAG = "GamesListActivity";

    //LayoutInflater _inflater;
    private EditText editTextSearch;
    private String sortOrder, sortBy;
    private View _viewSortRating, _viewSortWhite, _viewSortBlack, _viewSortId, _viewSortDate, _viewSortEvent;
    private TextView textViewPlayerWhite, textViewPlayerBlack, textViewResult;
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

        textViewPlayerWhite = findViewById(R.id.TextViewPlayerWhite);
        textViewPlayerBlack = findViewById(R.id.TextViewPlayerBlack);
        textViewResult = findViewById(R.id.TextViewResult);

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
        //_inflater = (LayoutInflater)getSystemService(Activity.LAYOUT_INFLATER_SERVICE);

        // DEBUG
        //getContentResolver().delete(MyPGNProvider.CONTENT_URI, "1=1", null);
        /////////////////////////////////////////////////////////////////////////////////////

        // cursor = managedQuery(MyPGNProvider.CONTENT_URI, PGNColumns.COLUMNS, null, null, _sortBy + " " + _sortOrder);
//        _adapter = new AlternatingSimpleCursorAdapter(this, R.layout.game_row, cursor,
//                new String[]{PGNColumns._ID, PGNColumns.WHITE, PGNColumns.BLACK, PGNColumns.DATE, PGNColumns.EVENT, PGNColumns.RATING, PGNColumns.RESULT},
//                new int[]{R.id.text_id, R.id.text_name1, R.id.text_name2, R.id.text_date, R.id.text_event, R.id.rating, R.id.text_result}) {
//            @Override
//            public void setViewText(TextView v, String text) {
//                super.setViewText(v, convText(v, text));
//            }
//
//            @Override
//            public View getView(int position, View convertView, ViewGroup parent) {
//                View view = super.getView(position, convertView, parent);
//                //view.setBackgroundColor(position % 2 == 0 ? 0x55888888 : 0x55666666);
//                return view;
//            }
//        };

		 /*
		 _adapter.setFilterQueryProvider(new FilterQueryProvider(){

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
//        _adapter.setViewBinder((view, cursor1, columnIndex) -> {
//
//            int nImageIndex = cursor1.getColumnIndex(PGNColumns.RATING);
//            if (nImageIndex == columnIndex) {
//                int floatIndex = cursor1.getColumnIndex(PGNColumns.RATING);
//                if (floatIndex >= 0) {
//                    float fR = cursor1.getFloat(floatIndex);
//                    ((RatingBar) view).setRating(fR);
//                }
//                //((RatingBar)view).setRating(0.4F);
//                return true;
//            }
//            return false;
//        });

        // ListViewGamesList

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
        seekBarGames.setMax(cursor.getCount());
    }

    private void loadGame() {
        int position = seekBarGames.getProgress();
        if (cursor == null || cursor.getCount() < position) {
            return;
        }
        cursor.moveToPosition(position);
        int index = cursor.getColumnIndex(PGNColumns.PGN);
        if (index < 0) {
            Log.d(TAG, "Could not load without PGN index " + index);
            return;
        }
        String sPGN = cursor.getString(index);
        gameApi.loadPGN(sPGN);

        index = cursor.getColumnIndex(PGNColumns.WHITE);
        textViewPlayerWhite.setText(cursor.getString(index));

        index = cursor.getColumnIndex(PGNColumns.BLACK);
        textViewPlayerBlack.setText(cursor.getString(index));

        index = cursor.getColumnIndex(PGNColumns.RESULT);
        textViewResult.setText(cursor.getString(index));
    }

    private void doFilterSort() {
        //_adapter.getFilter().filter(_editFilter.getText().toString());
        //_listGames.invalidate();
        String s = "%" + editTextSearch.getText().toString() + "%";
        String sWhere = PGNColumns.WHITE + " LIKE ? OR " +
                PGNColumns.BLACK + " LIKE ? OR " +
                PGNColumns.EVENT + " LIKE ?";
        String[] selectionArgs = new String[] {
            s, s, s
        };
        Log.i("runQuery", sWhere + " BY " + sortOrder);

        cursor = managedQuery(MyPGNProvider.CONTENT_URI, PGNColumns.COLUMNS, sWhere, selectionArgs, sortBy + " " + sortOrder);

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


}
