package jwtc.android.chess.tools;

import java.io.File;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import android.app.ListActivity;
import android.content.Intent;
import android.graphics.drawable.Drawable;
import android.net.Uri;
import android.os.Bundle;
import android.view.Gravity;
import android.view.MenuItem;
import android.view.View;
import android.widget.ListView;
import android.widget.Toast;

import jwtc.android.chess.MyBaseActivity;
import jwtc.android.chess.R;
import jwtc.android.chess.iconifiedlist.*;

public class FileListView extends ListActivity { 
		
	private File currentDirectory = new File("/");
	private List<IconifiedText> directoryEntries = new ArrayList<IconifiedText>();
	
	private String _mode = null;
	
    /** Called when the activity is first created. */
    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

		MyBaseActivity.prepareWindowSettings(this);

		MyBaseActivity.makeActionOverflowMenuShown(this);
     }
    
    private void browseToRoot() {
		browseTo(new File("/"));
    }
    
    @Override
    protected void onResume() {
        super.onResume();

        final Intent intent = getIntent();
        Bundle extras =intent.getExtras(); 
        if(extras != null){
        	String type = extras.getString(pgntool.EXTRA_MODE); 
        	if(type != null){
        		_mode = type;
        	}
        }
        if(_mode == null)
        {
        	doToast("Action not supported");
        	finish();
        }
		browseToRoot();
    }

	@Override
	public boolean onOptionsItemSelected(MenuItem item) {
		// Handle presses on the action bar items
		switch (item.getItemId()) {
			case android.R.id.home:
				// API 5+ solution
				onBackPressed();
				return true;
		}
		return super.onOptionsItemSelected(item);
	}
    
    private void upOneLevel(){
		if(this.currentDirectory.getParent() != null)
			this.browseTo(this.currentDirectory.getParentFile());
	}
	
	private void browseTo(final File aDirectory){
		// On relative we display the full path in the title.
		this.setTitle(aDirectory.getName() + " :: " + getFolderTitleForMode());
		if (aDirectory.isDirectory()){
			this.currentDirectory = aDirectory;
			fill(aDirectory.listFiles());
		}else{

			openFile(aDirectory);
			
		}
	}
	
	protected String getFolderTitleForMode(){
		if(_mode.equals(pgntool.MODE_IMPORT) || _mode.equals(pgntool.MODE_CREATE_PRACTICE) || _mode.equals(pgntool.MODE_IMPORT_PUZZLE) || _mode.equals(pgntool.MODE_IMPORT_OPENINGDATABASE)){
			return "*.pgn;*.zip";
		}
		else if(_mode.equals(pgntool.MODE_DB_IMPORT)){
			return "*.bin";
		}
		else if(_mode.equals(pgntool.MODE_UCI_INSTALL)){
			return "uci engine";
		}
		else if(_mode.equals(pgntool.MODE_IMPORT_PRACTICE)){
			return "*.txt";
		}
		return null;
	}
	
	protected boolean fileNameFilterByMode(String sName){
		if(_mode.equals(pgntool.MODE_IMPORT) || _mode.equals(pgntool.MODE_DB_IMPORT) || _mode.equals(pgntool.MODE_CREATE_PRACTICE) || _mode.equals(pgntool.MODE_IMPORT_PUZZLE) || _mode.equals(pgntool.MODE_IMPORT_OPENINGDATABASE)){
			if(sName.toLowerCase().endsWith(".pgn") || sName.endsWith(".zip")){
				return true;
			}
		} else if(_mode.equals(pgntool.MODE_DB_POINT)){
			if(sName.toLowerCase().endsWith(".bin")){
				return true;
			}
		} else if(_mode.equals(pgntool.MODE_UCI_INSTALL)){
			return true;
		} else if(_mode.equals(pgntool.MODE_IMPORT_PRACTICE)){
			if(sName.toLowerCase().endsWith(".txt")){
				return true;
			}
		}
		return false;
	}
	
	private void openFile(final File aFile){
		
		try {

			if(fileNameFilterByMode(aFile.getName()))
			{
				//
				Intent myIntent = new Intent();
				myIntent.putExtra(pgntool.EXTRA_MODE, _mode);
				myIntent.setClass(FileListView.this, importactivity.class);
				myIntent.setData(Uri.parse("file://" + aFile.getAbsolutePath()));
				
				startActivity(myIntent);
				
			}
		} catch (Exception e) {
			doToast("Could not open file, no program association...");
			e.printStackTrace();
		}

	}

	 public void doToast(final String text){
		Toast t = Toast.makeText(this, text, Toast.LENGTH_LONG);
		t.setGravity(Gravity.BOTTOM, 0, 0);
		t.show();
    }
	
	private void fill(File[] files) {
		this.directoryEntries.clear();
		
		// Add the "." == "current directory"
		this.directoryEntries.add(new IconifiedText(".", getResources().getDrawable(R.drawable.collections_collection)));		
		// and the ".." == 'Up one level'
		if(this.currentDirectory.getParent() != null){
			this.directoryEntries.add(new IconifiedText("..", 
					getResources().getDrawable(R.drawable.navigation_previous_item)));
		}
		Drawable currentIcon = null;
		for (File currentFile : files){
			if (currentFile.canRead() /*&& (_mode == MODE_IMPORT  || _mode == MODE_EXPORT && currentFile.canWrite())*/){
				if (currentFile.isDirectory()) {
					currentIcon = getResources().getDrawable(R.drawable.collections_collection);
					this.directoryEntries.add(new IconifiedText(currentFile.getPath(), currentIcon));
				}else { 
					String fileName = currentFile.getName();
		
					if(fileNameFilterByMode(fileName))
					{
						currentIcon = getResources().getDrawable(R.drawable.navigation_next_item);
						this.directoryEntries.add(new IconifiedText(currentFile.getPath(), currentIcon));
					}				
				}
			}
		}
		Collections.sort(this.directoryEntries);
		
		IconifiedTextListAdapter itla = new IconifiedTextListAdapter(this);
		itla.setListItems(this.directoryEntries);		
		this.getListView().setAdapter(itla);
	}

	protected void onListItemClick(ListView l, View v, int position, long id) {
		super.onListItemClick(l, v, position, id);
		
		if(this.directoryEntries.size() > position){
			String selectedFileString = this.directoryEntries.get(position).getText();
			if (selectedFileString.equals(".")) {
				// Refresh
				this.browseTo(this.currentDirectory);
			} else if(selectedFileString.equals("..")){
				this.upOneLevel();
			} else {
				File clickedFile = new File(this.directoryEntries.get(position).getText());
				if(clickedFile != null)
					this.browseTo(clickedFile);
			}
		}
	}	
}