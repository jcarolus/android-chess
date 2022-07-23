package jwtc.android.chess.views;

public class HistoryView {

    /*
    View v = _inflater.inflate(R.layout.pgn_item, null, false);
        v.setId(ply);
        _arrPGNView.add(new PGNView(this, v, ply, sMove, sAnnotation.length() > 0));

        if (_layoutHistory != null) {
            while (ply >= 0 && _layoutHistory.getChildCount() >= ply)
                _layoutHistory.removeViewAt(_layoutHistory.getChildCount() - 1);


            RelativeLayout.LayoutParams lp = new RelativeLayout.LayoutParams(RelativeLayout.LayoutParams.WRAP_CONTENT, RelativeLayout.LayoutParams.WRAP_CONTENT);
            if (_layoutHistory.getChildCount() > 0) {
                if (_hScrollHistory != null) {
                    lp.addRule(RelativeLayout.RIGHT_OF, _layoutHistory.getChildAt(_layoutHistory.getChildCount() - 1).getId());
                }
            }
            _layoutHistory.addView(v, lp);

        }
     */


    /*
     public PGNView(ChessView parent, View view, int num, String sMove, boolean bAnno) {
        _parent = parent;
        _view = view;
        _bAnnotated = bAnno;

        TextView tvItemNum = (TextView) _view.findViewById(R.id.TextViewNumMove);
        if (num % 2 == 1) {
            int i = ((int) num / 2 + 1);
            String s = "";
            tvItemNum.setText(s + i + ". ");
            //tvItemNum.setVisibility(View.VISIBLE);

        } else {
            //tvItemNum.setVisibility(View.INVISIBLE);
            tvItemNum.setWidth(0);
        }
        _tvItem = (TextView) _view.findViewById(R.id.TextViewMove);
        _tvItem.setText(sMove);
        _sMove = sMove;

        _view.setOnClickListener(new OnClickListener() {
            public void onClick(View arg0) {
                _parent.onClickPGNView(PGNView.this);
            }
        });

        _view.setOnLongClickListener(new OnLongClickListener() {

            public boolean onLongClick(View v) {
                _parent.onLongClickPGNView(PGNView.this);
                return false;
            }
        });
    }

    public void setAnnotated(boolean b) {
        _bAnnotated = b;
    }

    public void setSelected(boolean b) {

        if (b) {
            _tvItem.setPaintFlags(_tvItem.getPaintFlags() | Paint.UNDERLINE_TEXT_FLAG);
        } else {
            _tvItem.setPaintFlags(_tvItem.getPaintFlags() & ~Paint.UNDERLINE_TEXT_FLAG);
        }
        if (_bAnnotated) {
            _tvItem.setTypeface(null, Typeface.BOLD);
        } else {
            _tvItem.setTypeface(null, Typeface.NORMAL);
        }
    }
}
     */

}
