package jwtc.android.chess.ics;
import org.json.JSONArray;
import org.json.JSONException;

import jwtc.android.chess.MyBaseActivity;
import jwtc.android.chess.R;
import android.app.AlertDialog;
import android.content.DialogInterface;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.view.Gravity;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.ListView;
import android.widget.AdapterView.OnItemClickListener;

public class CustomCommands extends MyBaseActivity implements OnItemClickListener {

	public static final String TAG = "CustomCommands";
	public static final String DEFAULT_COMMANDS = "[" +
			"\"tell relay listgames\"," + 
			"\"tell puzzlebot getmate\"," +
			"\"tell puzzlebot gettactics\"," +
			"\"tell puzzlebot getstudy\"," +
			"\"exl\"," +
			"\"help commands\"" +
			"]";
	
	private ListView _listCommands;
	ArrayAdapter<String> _adapter;

	@Override
	public void onItemClick(AdapterView<?> arg0, View arg1, int arg2, long arg3) {
		// TODO Auto-generated method stub
		
		final int position = arg2;
		
		AlertDialog.Builder builder = new AlertDialog.Builder(this);
		builder.setTitle(getString(R.string.title_edit_or_delete));
		
		builder.setPositiveButton(getString(R.string.choice_edit), new DialogInterface.OnClickListener(){
			public void onClick(DialogInterface dialog, int which) {
				
				final FrameLayout fl = new FrameLayout(CustomCommands.this);
				final EditText input = new EditText(CustomCommands.this);
				input.setText(_adapter.getItem(position));
				input.setGravity(Gravity.CENTER);
				
				fl.addView(input, new FrameLayout.LayoutParams(FrameLayout.LayoutParams.FILL_PARENT, FrameLayout.LayoutParams.WRAP_CONTENT));
				
				AlertDialog.Builder builder = new AlertDialog.Builder(CustomCommands.this)
					.setView(fl)
					.setTitle(getString(R.string.title_edit_command))
					.setPositiveButton(R.string.button_ok, new DialogInterface.OnClickListener() {
					public void onClick(DialogInterface dialog, int which) {
						String s = input.getText().toString();
						
						_adapter.remove(_adapter.getItem(position));
						_adapter.insert(s, position);
						
						_listCommands.invalidateViews();
						
						dialog.dismiss();
					}
					
				});
				AlertDialog alert = builder.create();
				alert.show();
				
				dialog.dismiss();
			}
			
		});
		
		builder.setNegativeButton(getString(R.string.choice_delete), new DialogInterface.OnClickListener(){
			public void onClick(DialogInterface dialog, int which) {
				
				_adapter.remove(_adapter.getItem(position));				
				_listCommands.invalidateViews();
				dialog.dismiss();				
				
			}
		});
		
		AlertDialog alert = builder.create();
		alert.show();
		
	}

	
	@Override
	public void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		
		setContentView(R.layout.customcommands);

		this.makeActionOverflowMenuShown();
		
		_listCommands = (ListView) findViewById(R.id.ListCustomCommands);
		
		_listCommands.setOnItemClickListener(this);
	
	}
	
	@Override
	protected void onResume() {
		
		super.onResume();

		SharedPreferences prefs = this.getPrefs();
		
		_adapter = new ArrayAdapter<String>(this, android.R.layout.simple_list_item_1);
		
		try {
			JSONArray jArray = new JSONArray(prefs.getString("ics_custom_commands", DEFAULT_COMMANDS));
			for(int i = 0; i < jArray.length(); i++){
				_adapter.add(jArray.getString(i));
			}
		} catch (JSONException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}

		_listCommands.setAdapter(_adapter);
	}
	

	@Override
	protected void onPause() {

		SharedPreferences.Editor editor = this.getPrefs().edit();
		
		JSONArray jArray = new JSONArray();
		for(int i = 0; i < _adapter.getCount(); i++){
			jArray.put(_adapter.getItem(i));
		}
		editor.putString("ics_custom_commands", jArray.toString());

		editor.commit();
		
		super.onPause();
	}

	 public boolean onCreateOptionsMenu(Menu menu) {

		MenuItem item1;

		item1 = menu.add(getString(R.string.menu_new_command));
		item1.setIcon(R.drawable.content_new);

		return true;
	}

	public boolean onOptionsItemSelected(MenuItem item) {
		if(item.getTitle() == null){
			finish();
			return true;
		}
		if (item.getTitle().equals(getString(R.string.menu_new_command))) {

			final FrameLayout fl = new FrameLayout(this);
			final EditText input = new EditText(this);
			input.setGravity(Gravity.CENTER);
			input.setSingleLine();

			fl.addView(input, new FrameLayout.LayoutParams(FrameLayout.LayoutParams.FILL_PARENT, FrameLayout.LayoutParams.WRAP_CONTENT));
			
			AlertDialog.Builder builder = new AlertDialog.Builder(this)
				.setView(fl)
				.setTitle(R.string.menu_new_command)
				.setMessage(R.string.menu_new_command_message)
				.setPositiveButton(R.string.button_ok, new DialogInterface.OnClickListener() {
				public void onClick(DialogInterface dialog, int which) {
					String s = input.getText().toString();
					
					_adapter.add(s);
					_listCommands.invalidateViews();
					
					dialog.dismiss();
				}
				
			});
			AlertDialog alert = builder.create();
			alert.show();

			return true;
		}

		return super.onOptionsItemSelected(item);
	}

}
