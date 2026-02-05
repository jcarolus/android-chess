package jwtc.android.chess.play;

import android.content.Context;
import android.graphics.Paint;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.BaseAdapter;
import android.widget.TextView;

import androidx.core.content.ContextCompat;

import java.util.List;
import jwtc.android.chess.R;
import jwtc.android.chess.services.GameApi;

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

        TextView textNr = convertView.findViewById(R.id.TextViewNumMove);
        TextView textMove = convertView.findViewById(R.id.TextViewMove);
        TextView textAnnotation = convertView.findViewById(R.id.TextViewAnnotation);

        if (move.turn != 0) {
            textMove.setPaintFlags(textMove.getPaintFlags() | Paint.UNDERLINE_TEXT_FLAG);
            textMove.setTextColor(ContextCompat.getColor(context, R.color.primaryColor));
        } else {
            textMove.setTextColor(ContextCompat.getColor(context, R.color.surfaceTextColor));
            textMove.setPaintFlags(textMove.getPaintFlags() & (~ Paint.UNDERLINE_TEXT_FLAG));
        }

        textNr.setText(move.nr);
        textMove.setText(move.sMove);
        textMove.setContentDescription(GameApi.moveToSpeechString(move.sMove, move.move));
        textAnnotation.setText(move.annotation);

        return convertView;
    }
}