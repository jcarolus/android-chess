package jwtc.android.chess.helpers;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import java.util.ArrayList;
import java.util.List;

import jwtc.android.chess.R;

public class StartItemAdapter extends RecyclerView.Adapter<StartItemAdapter.TileVH> {
    final List<StartItem> items = new ArrayList<>();

    public interface OnTileClickListener {
        void onTileClick(StartItem tile, int position);
    }

    private final OnTileClickListener listener;

    public StartItemAdapter(List<StartItem> initialItems, OnTileClickListener listener) {
        if (initialItems != null) {
            items.addAll(initialItems);
        }
        this.listener = listener;
    }

    @NonNull
    @Override
    public TileVH onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View v = LayoutInflater.from(parent.getContext()).inflate(R.layout.start_item, parent, false);
        return new TileVH(v);
    }

    @Override
    public void onBindViewHolder(@NonNull TileVH holder, int position) {
        StartItem tile = items.get(position);
        holder.bind(tile, position, listener);
    }

    @Override
    public long getItemId(int position) {
        return position;
    }

    @Override
    public int getItemCount() {
        return items.size();
    }

    public static class TileVH extends RecyclerView.ViewHolder {
        final TextView title;
        final ImageView icon;

        TileVH(@NonNull View itemView) {
            super(itemView);
            title = itemView.findViewById(R.id.start_item_title);
            icon = itemView.findViewById(R.id.start_item_icon);
        }

        void bind(StartItem startItem, int position, OnTileClickListener listener) {
            title.setText(startItem.textResourceId);
            icon.setImageResource(startItem.iconResourceId);

            itemView.setOnClickListener(v -> {
                if (listener != null) listener.onTileClick(startItem, position);
            });
        }
    }
}
