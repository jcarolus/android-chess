package jwtc.android.chess.play;

import android.content.Context;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.BaseAdapter;
import android.widget.ImageView;
import android.widget.TextView;
import java.util.List;
import jwtc.android.chess.R;

public class MoveItemAdapter extends BaseAdapter {
    private Context context;
    private List<MoveItem> moves;

    public MoveItemAdapter(Context context, List<MoveItem> moves) {
        this.context = context;
        this.moves = moves;
    }

    @Override
    public int getCount() {
        return moves.size();
    }

    @Override
    public Object getItem(int position) {
        return moves.get(position);
    }

    @Override
    public long getItemId(int position) {
        return position;
    }

    @Override
    public View getView(int position, View convertView, ViewGroup parent) {
        MoveItem move = moves.get(position);

        if (convertView == null) {
            convertView = LayoutInflater.from(context).inflate(R.layout.pgn_item, parent, false);
        }

        ImageView imageTurn = convertView.findViewById(R.id.ImageTurn);
        TextView textNr = convertView.findViewById(R.id.TextViewNumMove);
        TextView textMove = convertView.findViewById(R.id.TextViewMove);
        TextView textAnnotation = convertView.findViewById(R.id.TextViewAnnotation);

        if (move.turn != 0) {
            imageTurn.setImageResource(move.turn);
            imageTurn.setVisibility(View.VISIBLE);
        } else {
            imageTurn.setVisibility(View.GONE);
        }

        textNr.setText(move.nr);
        textMove.setText(move.move);
        textAnnotation.setText(move.annotation);

        return convertView;
    }
}