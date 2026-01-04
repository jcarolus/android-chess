package jwtc.android.chess.helpers;

import android.content.Context;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.NonNull;

import jwtc.android.chess.R;

public class StartMenuAdapter extends ArrayAdapter<String> {

    private final LayoutInflater inflater;

    public StartMenuAdapter(Context context, String[] items) {
        super(context, 0, items);
        inflater = LayoutInflater.from(context);
    }

    @NonNull
    @Override
    public View getView(int position, View convertView, @NonNull ViewGroup parent) {

        if (convertView == null) {
            convertView = inflater.inflate(R.layout.start_list_single, parent, false);
        }

        TextView title = convertView.findViewById(R.id.txt);
        ImageView icon = convertView.findViewById(R.id.img);

        String item = getItem(position);
        title.setText(item);

        // Simple icon mapping example
        switch (item) {
            case "Play":
                icon.setImageResource(R.drawable.ic_logo);
                break;
            case "Practice":
                icon.setImageResource(R.drawable.ic_logo_2);
                break;
            case "Puzzles":
                icon.setImageResource(R.drawable.ic_pieces_alpha_bp);
                break;
            default:
                icon.setImageResource(R.drawable.ic_icon_cpu);
                break;
        }

        return convertView;
    }
}
