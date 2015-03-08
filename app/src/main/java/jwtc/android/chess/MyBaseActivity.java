package jwtc.android.chess;

import android.content.Context;
import android.graphics.Color;
import android.os.Bundle;
import android.os.Handler;
import android.util.AttributeSet;
import android.view.LayoutInflater;
import android.view.View;
import android.view.LayoutInflater.Factory;
import android.widget.TextView;

public class MyBaseActivity extends android.app.Activity{

	
	@Override
    public void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		
		 getLayoutInflater().setFactory(new Factory() {
	    	@Override
	    	public View onCreateView(String name, Context context, AttributeSet attrs) {

	    		if (name.equalsIgnoreCase("com.android.internal.view.menu.IconMenuItemView")) {
	    			try {
	    				LayoutInflater f = getLayoutInflater();
	    				final View view = f.createView(name, null, attrs);

	    				new Handler().post(new Runnable() {
	    					public void run() {

	    						// set the background 
	    						view.setBackgroundColor(Color.BLACK);

	    						// set the text color
	    						((TextView) view).setTextColor(Color.WHITE);
	    					}
	    				});
	    				return view;
	    			} catch (Exception e) {
	    			} 
	    		}
	    		return null;
	    	}

			
	    });
	}
}
