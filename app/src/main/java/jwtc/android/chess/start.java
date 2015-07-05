package jwtc.android.chess;

import jwtc.android.chess.puzzle.practice;
import jwtc.android.chess.puzzle.puzzle;
import jwtc.android.chess.tools.pgntool;
import jwtc.android.chess.ics.*;
import android.app.ListActivity;
import android.content.Intent;
import android.os.Bundle;
import android.util.Log;
import android.view.Gravity;
import android.view.View;
import android.view.View.OnClickListener;
import android.widget.Button;
import android.widget.ListView;
import android.widget.Toast;

public class start extends ListActivity {

	//private ListView _lvStart;
	public static final String TAG = "start";

	/**
	 * Called when the activity is first created.
	 */
	@Override
	public void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);

		setContentView(R.layout.start);
	}

	@Override
	protected void onListItemClick(ListView l, View v, int position, long id){
		String s = getListView().getItemAtPosition(position).toString();
		try {
			Intent i = new Intent();
			Log.i("start", s);
			if (s.equals(getString(R.string.start_play))) {
				i.setClass(start.this, main.class);
				i.setFlags(Intent.FLAG_ACTIVITY_REORDER_TO_FRONT);
				startActivity(i);
			} else if (s.equals(getString(R.string.start_practice))) {
				i.setClass(start.this, practice.class);
				i.setFlags(Intent.FLAG_ACTIVITY_REORDER_TO_FRONT);
				startActivity(i);
			} else if (s.equals(getString(R.string.start_puzzles))) {
				i.setClass(start.this, puzzle.class);
				i.setFlags(Intent.FLAG_ACTIVITY_REORDER_TO_FRONT);
				startActivity(i);
			} else if (s.equals(getString(R.string.start_about))) {
				i.setClass(start.this, HtmlActivity.class);
				i.putExtra(HtmlActivity.HELP_MODE, "about");
				startActivity(i);
			} else if (s.equals(getString(R.string.start_ics))) {
				i.setClass(start.this, ICSClient.class);
				startActivity(i);
			} else if (s.equals(getString(R.string.start_pgn))) {
				i.setClass(start.this, pgntool.class);
				i.setFlags(Intent.FLAG_ACTIVITY_REORDER_TO_FRONT);
				startActivity(i);
			} else if (s.equals(getString(R.string.start_globalpreferences))) {
				i.setClass(start.this, ChessPreferences.class);
				startActivity(i);
			} else if (s.equals(getString(R.string.menu_help))) {
				i.setClass(start.this, HtmlActivity.class);
				i.putExtra(HtmlActivity.HELP_MODE, "help");
				startActivity(i);
			}

		} catch (Exception ex) {
			Toast t = Toast.makeText(start.this, R.string.toast_could_not_start_activity, Toast.LENGTH_LONG);
			t.setGravity(Gravity.BOTTOM, 0, 0);
			t.show();
		}
	}

   
}
