package jwtc.android.chess.play;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import java.util.List;

import jwtc.android.chess.R;

public class PlayModeAdapter extends RecyclerView.Adapter<PlayModeAdapter.VH> {

    public interface Listener {
        void onClick(PlayMode mode);
    }

    private final List<PlayMode> items;
    private final Listener listener;

    public PlayModeAdapter(List<PlayMode> items, Listener listener) {
        this.items = items;
        this.listener = listener;
    }

    @NonNull
    @Override
    public VH onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View v = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.item_play_tile, parent, false);
        return new VH(v);
    }

    @Override
    public void onBindViewHolder(@NonNull VH h, int pos) {
        PlayMode m = items.get(pos);
        h.title.setText(m.title);
        h.icon.setImageResource(m.icon);
        h.itemView.setOnClickListener(v -> listener.onClick(m));
    }

    @Override
    public int getItemCount() {
        return items.size();
    }

    static class VH extends RecyclerView.ViewHolder {
        ImageView icon;
        TextView title;

        VH(View v) {
            super(v);
            icon = v.findViewById(R.id.icon);
            title = v.findViewById(R.id.title);
        }
    }
}

