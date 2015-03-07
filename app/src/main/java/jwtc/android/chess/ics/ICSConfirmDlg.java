package jwtc.android.chess.ics;

import jwtc.android.chess.*;
import android.app.Dialog;
import android.content.Context;
import android.view.View;
import android.widget.Button;
import android.widget.TextView;

/**
 * 
 */
public class ICSConfirmDlg extends Dialog {

	private ICSClient _parent;
	private String _sendString;
	private TextView _tvText;
	
	public ICSConfirmDlg(Context context) {
		super(context);

		_parent = (ICSClient)context;
		
		setContentView(R.layout.icsconfirm);
		
		setCanceledOnTouchOutside(true);
		
		_tvText = (TextView)findViewById(R.id.TextViewConfirm);
		
		Button butYes = (Button)findViewById(R.id.ButtonConfirmYes);
		butYes.setOnClickListener(new View.OnClickListener() {
        	public void onClick(View arg0) {
        		_parent.sendString(_sendString);
        		dismiss();
        	}
    	}); 
		Button butNo = (Button)findViewById(R.id.ButtonConfirmNo);
		butNo.setOnClickListener(new View.OnClickListener() {
        	public void onClick(View arg0) {
        		dismiss();
        	}
    	});
	}
	
	public void setText(String sTitle, String sText){
		setTitle(sTitle);
		_tvText.setText(sText);
	}
	public void setSendString(String s){
		_sendString = s;
	}
}