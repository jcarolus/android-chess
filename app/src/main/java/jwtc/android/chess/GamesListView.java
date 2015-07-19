package jwtc.android.chess;

import java.text.SimpleDateFormat;
import java.util.Calendar;
import android.app.AlertDialog;
import android.app.ListActivity;
import android.content.ContentUris;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.database.Cursor;
import android.graphics.Color;
import android.net.Uri;
import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
import android.util.Log;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.widget.AdapterView;
import android.widget.EditText;
import android.widget.ListView;
import android.widget.RatingBar;
import android.widget.SimpleCursorAdapter;
import android.widget.TextView;
import android.widget.AdapterView.OnItemClickListener;
import android.widget.AdapterView.OnItemLongClickListener;

import jwtc.chess.PGNColumns;

public class GamesListView extends ListActivity implements OnItemClickListener, OnItemLongClickListener {

	ListView _listGames;
	//LayoutInflater _inflater;
	EditText _editFilter;
	AlternatingSimpleCursorAdapter _adapter;
	String _sortOrder, _sortBy;
	View _viewSortRating,_viewSortWhite,_viewSortBlack,_viewSortId,_viewSortDate,_viewSortEvent;      
	
	@Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

		MyBaseActivity.prepareWindowSettings(this);

		setContentView(R.layout.gameslist);

		MyBaseActivity.makeActionOverflowMenuShown(this);
		
		_sortBy = PGNColumns.DATE;
		_sortOrder = "ASC";
		
		_viewSortRating = (View)findViewById(R.id.sort_rating);
		_viewSortRating.setOnClickListener(new View.OnClickListener() {
        	public void onClick(View arg0) {
        		
        		if(_sortBy.equals(PGNColumns.RATING))
        			flipSortOrder();
        		
        		resetSortBackgrounds();
        		setSortBackground(_viewSortRating);
        		_sortBy = PGNColumns.RATING;
        		doFilterSort();
        	}
        });
		_viewSortWhite = (View)findViewById(R.id.sort_text_name1);
		_viewSortWhite.setOnClickListener(new View.OnClickListener() {
        	public void onClick(View arg0) {
        		if(_sortBy.equals(PGNColumns.WHITE))
        			flipSortOrder();
        		
        		resetSortBackgrounds();
        		setSortBackground(_viewSortWhite);
        		_sortBy = PGNColumns.WHITE;
        		doFilterSort();
        	}
        });
		_viewSortBlack = (View)findViewById(R.id.sort_text_name2);
		_viewSortBlack.setOnClickListener(new View.OnClickListener() {
        	public void onClick(View arg0) {
        		if(_sortBy.equals(PGNColumns.BLACK))
        			flipSortOrder();
        		
        		resetSortBackgrounds();
        		setSortBackground(_viewSortBlack);
        		_sortBy = PGNColumns.BLACK;
        		doFilterSort();
        	}
        });
		_viewSortId = (View)findViewById(R.id.sort_text_id);
		_viewSortId.setOnClickListener(new View.OnClickListener() {
        	public void onClick(View arg0) {
        		if(_sortBy.equals(PGNColumns._ID))
        			flipSortOrder();
        		
        		resetSortBackgrounds();
        		setSortBackground(_viewSortId);
        		_sortBy = PGNColumns._ID;
        		doFilterSort();
        	}
        });
		_viewSortEvent = (View)findViewById(R.id.sort_text_event);
		_viewSortEvent.setOnClickListener(new View.OnClickListener() {
        	public void onClick(View arg0) {
        		if(_sortBy.equals(PGNColumns.EVENT))
        			flipSortOrder();
        		
        		resetSortBackgrounds();
        		setSortBackground(_viewSortEvent);
        		_sortBy = PGNColumns.EVENT;
        		doFilterSort();
        	}
        });
		_viewSortDate = (View)findViewById(R.id.sort_text_date);
		_viewSortDate.setOnClickListener(new View.OnClickListener() {
        	public void onClick(View arg0) {
        		if(_sortBy.equals(PGNColumns.DATE))
        			flipSortOrder();
        		
        		resetSortBackgrounds();
        		setSortBackground(_viewSortDate);
        		_sortBy = PGNColumns.DATE;
        		doFilterSort();
        	}
        });
		setSortBackground(_viewSortDate);
		
		_editFilter = (EditText)findViewById(R.id.EditTextGamesList);
		_editFilter.addTextChangedListener(new TextWatcher() {

			public void afterTextChanged (Editable s){
				doFilterSort();
			}

			public void beforeTextChanged(CharSequence s, int start, int count, int after) {}

			public void onTextChanged(CharSequence s, int start, int before, int count) {}
		});
		
		
		//_inflater = (LayoutInflater)getSystemService(Activity.LAYOUT_INFLATER_SERVICE);
		
		// DEBUG
		//getContentResolver().delete(MyPGNProvider.CONTENT_URI, "1=1", null);
		/////////////////////////////////////////////////////////////////////////////////////
		
		Cursor cursor = managedQuery(MyPGNProvider.CONTENT_URI, PGNColumns.COLUMNS, null, null, _sortBy + " " + _sortOrder);
		_adapter = new AlternatingSimpleCursorAdapter(this, R.layout.game_row, cursor,
	        		new String[] 	{PGNColumns._ID,	PGNColumns.WHITE,	PGNColumns.BLACK,	PGNColumns.DATE,	PGNColumns.EVENT,	PGNColumns.RATING}, 
	                new int[] 		{ R.id.text_id, 	R.id.text_name1,	R.id.text_name2, 	R.id.text_date,		R.id.text_event, 	R.id.rating }){
			 @Override
		        public void setViewText(TextView v, String text) {
		            super.setViewText(v, convText(v, text));
		          }
			 @Override
				public View getView(int position, View convertView, ViewGroup parent) {
				  View view = super.getView(position, convertView, parent);
				  view.setBackgroundColor(position % 2 == 0 ? 0x55888888 : 0x55666666);
				  return view;
				}
		 };

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
		_adapter.setViewBinder(new SimpleCursorAdapter.ViewBinder(){

				public boolean setViewValue(View view, Cursor cursor, int columnIndex) {
					
					int nImageIndex = cursor.getColumnIndex(PGNColumns.RATING);
			         if(nImageIndex == columnIndex)
			         { 
			        	 float fR = cursor.getFloat(cursor.getColumnIndex(PGNColumns.RATING));
			        	 ((RatingBar)view).setRating(fR);
			        	 //((RatingBar)view).setRating(0.4F);
			        	 return true;
			         }
					return false;
				}
		 		
		 	});
		
		// ListViewGamesList
		_listGames = getListView();
		
		_listGames.setAdapter(_adapter);
		_listGames.setOnItemClickListener(this);
		_listGames.setOnItemLongClickListener(this);
	}


	private void doFilterSort(){
		//_adapter.getFilter().filter(_editFilter.getText().toString());
		//_listGames.invalidate();
		String s = _editFilter.getText().toString();
		String sWhere = PGNColumns.WHITE + " LIKE('%" + s + "%') OR " +
		PGNColumns.BLACK + " LIKE('%" + s + "%') OR " +
		PGNColumns.EVENT + " LIKE('%" + s + "%')";
		Log.i("runQuery", sWhere + " BY " + _sortOrder);
		_adapter.changeCursor(managedQuery(MyPGNProvider.CONTENT_URI, PGNColumns.COLUMNS, sWhere, null, _sortBy + " " + _sortOrder));
	
	}
	 
	private void resetSortBackgrounds(){
		_viewSortId.setBackgroundColor(Color.BLACK);
		_viewSortWhite.setBackgroundColor(Color.BLACK);
		_viewSortBlack.setBackgroundColor(Color.BLACK);
		_viewSortEvent.setBackgroundColor(Color.BLACK);
		_viewSortRating.setBackgroundColor(Color.BLACK);
		_viewSortDate.setBackgroundColor(Color.BLACK);
	}
	private void setSortBackground(View v){
		if(_sortOrder.equals("DESC"))
			v.setBackgroundResource(R.drawable.arrow_down);
		else
			v.setBackgroundResource(R.drawable.arrow_up);
	}

	private void flipSortOrder(){
		_sortOrder = _sortOrder.equals("DESC") ? "ASC" : "DESC";
	}
	
	private String convText(TextView v, String text) {
	    switch (v.getId()) {
	      case R.id.text_date:
	    	  SimpleDateFormat formatter = new SimpleDateFormat("yyyy.MM.dd");
	    	  Calendar c = Calendar.getInstance();
	    	  c.setTimeInMillis(Long.parseLong(text));
	        return formatter.format(c.getTime());
	    }
	      return text;
	    } 

	
	public void onItemClick(AdapterView<?> arg0, View arg1, int position, long arg3) {
		Cursor c = (Cursor) _listGames.getAdapter().getItem(position);
		long id = c.getLong(c.getColumnIndex(PGNColumns._ID));
		
		Uri uri = ContentUris.withAppendedId(MyPGNProvider.CONTENT_URI, id);
		Intent i = new Intent(Intent.ACTION_EDIT);
		i.setData(uri);
		
		//startActivity(i);
		
		setResult(RESULT_OK, i);
		finish();
	}

	public boolean onItemLongClick(AdapterView<?> arg0, View arg1, int position, long arg3) {
		// TODO Auto-generated method stub
		Cursor c = (Cursor) _listGames.getAdapter().getItem(position);
		final long id = c.getLong(c.getColumnIndex(PGNColumns._ID));
		
		AlertDialog.Builder builder = new AlertDialog.Builder(this);
		builder.setTitle(getString(R.string.title_delete_game));
		
		builder.setPositiveButton(getString(R.string.button_ok), new DialogInterface.OnClickListener(){

			public void onClick(DialogInterface dialog, int which) {
				Uri uri = ContentUris.withAppendedId(MyPGNProvider.CONTENT_URI, id);
				getContentResolver().delete(uri, null, null);
				dialog.dismiss();
			}
			
		});
		
		builder.setNegativeButton(getString(R.string.button_cancel), new DialogInterface.OnClickListener(){
			public void onClick(DialogInterface dialog, int which) {
				dialog.dismiss();
			}
		});
		
		AlertDialog alert = builder.create();
		alert.show();
		
		return true;
	}

	private class AlternatingSimpleCursorAdapter extends SimpleCursorAdapter{

		public AlternatingSimpleCursorAdapter(Context context, int layout,
				Cursor c, String[] from, int[] to) {
			super(context, layout, c, from, to);
			// TODO Auto-generated constructor stub
		}
		@Override
		public View getView(int position, View convertView, ViewGroup parent) {
		  View view = super.getView(position, convertView, parent);
		  
		  view.setBackgroundColor(position % 2 == 0 ? 0xFF333333 : 0xFF222222);

		  return view;
		}
	}

}
