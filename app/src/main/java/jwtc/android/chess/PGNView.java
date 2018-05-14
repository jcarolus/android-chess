package jwtc.android.chess;

import android.graphics.Paint;
import android.graphics.Typeface;
import android.view.View;
import android.view.View.OnClickListener;
import android.view.View.OnLongClickListener;
import android.widget.TextView;

public class PGNView {

	public String _sMove;
	public boolean _bAnnotated;
	public View _view;
	public ChessView _parent;
	private TextView _tvItem;
	
	public PGNView(ChessView parent, View view, int num, String sMove, boolean bAnno){
		_parent = parent;
		_view = view;
		_bAnnotated = bAnno;
		
		TextView tvItemNum = (TextView)_view.findViewById(R.id.TextViewNumMove);
		if(num % 2 == 1){
			int i = ((int)num/2 + 1);
			String s = "";
			if(parent.hasVerticalScroll()){
				if(i < 100)
					s += "  ";
				if(i < 10)
					s += "  ";
			}
			tvItemNum.setText(s + i + ". ");
			//tvItemNum.setVisibility(View.VISIBLE);
			
		} else {
			//tvItemNum.setVisibility(View.INVISIBLE);
			tvItemNum.setWidth(0);
		}
		_tvItem = (TextView)_view.findViewById(R.id.TextViewMove);
		_tvItem.setText(sMove);
		_sMove = sMove;
		
		_view.setOnClickListener(new OnClickListener() {
        	public void onClick(View arg0) {
        		_parent.onClickPGNView(PGNView.this);
        	}
    	});
		
		_view.setOnLongClickListener(new OnLongClickListener(){

			public boolean onLongClick(View v) {
				_parent.onLongClickPGNView(PGNView.this);
				return false;
			}
		});
	}
	public void setAnnotated(boolean b){
		_bAnnotated = b;
	}
	public void setSelected(boolean b){

		if(b){
			_tvItem.setPaintFlags(_tvItem.getPaintFlags() | Paint.UNDERLINE_TEXT_FLAG);
		}
		else{
			_tvItem.setPaintFlags(_tvItem.getPaintFlags() & ~Paint.UNDERLINE_TEXT_FLAG);
		}
		if(_bAnnotated) {
			_tvItem.setTypeface(null, Typeface.BOLD);
		}
		else {
			_tvItem.setTypeface(null, Typeface.NORMAL);
		}
	}
}
