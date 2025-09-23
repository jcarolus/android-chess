package jwtc.android.chess.helpers;


import android.content.Context;
import android.graphics.Paint;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.core.content.ContextCompat;
import androidx.recyclerview.widget.DiffUtil;
import androidx.recyclerview.widget.RecyclerView;

import java.util.ArrayList;
import java.util.HashMap;

import jwtc.android.chess.R;
import jwtc.android.chess.services.GameApi;
import jwtc.chess.JNI;
import jwtc.chess.PGNEntry;
import jwtc.chess.Pos;

public class MoveRecyclerAdapter extends RecyclerView.Adapter<MoveRecyclerAdapter.ViewHolder> {
    private final ArrayList<HashMap<String, String>> mapMoves = new ArrayList<HashMap<String, String>>();
    private final OnItemClickListener listener;
    private final GameApi gameApi;
    private final Context context;


    public interface OnItemClickListener {
        void onMoveItemClick(int position);
    }

    public MoveRecyclerAdapter(Context context, GameApi gameApi, OnItemClickListener listener) {
        this.context = context;
        this.gameApi = gameApi;
        this.listener = listener;
    }

    public void update() {
        final JNI jni = JNI.getInstance();
        ArrayList<HashMap<String, String>> newMapMoves = new ArrayList<HashMap<String, String>>();
        ArrayList<PGNEntry> pgnEntries = gameApi.getPGNEntries();

        for (int i = 0; i < pgnEntries.size(); i++) {
            String sMove =  pgnEntries.get(i)._sMove;
            if (pgnEntries.get(i)._duckMove != -1) {
                sMove += "@" + Pos.toString(pgnEntries.get(i)._duckMove);
            }
            HashMap<String, String> item = new HashMap<String, String>();
            item.put("nr", i % 2 == 0 ? ((i/2 + 1) + ". ") : " ");
            item.put("move", sMove);
            item.put("turn", jni.getNumBoard() - 1 == i ? "yes" : "no");

            newMapMoves.add(item);
        }

        DiffUtil.DiffResult diffResult = DiffUtil.calculateDiff(new DiffUtil.Callback() {
            @Override
            public int getOldListSize() { return mapMoves.size(); }
            @Override
            public int getNewListSize() { return newMapMoves.size(); }
            @Override
            public boolean areItemsTheSame(int oldPos, int newPos) {
                HashMap<String, String> item = mapMoves.get(oldPos);
                HashMap<String, String> newItem = newMapMoves.get(newPos);
                return MoveRecyclerAdapter.this.equalsItem(item, newItem);
            }
            @Override
            public boolean areContentsTheSame(int oldPos, int newPos) {
                HashMap<String, String> item = mapMoves.get(oldPos);
                HashMap<String, String> newItem = newMapMoves.get(newPos);
                return MoveRecyclerAdapter.this.equalsItem(item, newItem);
            }
        });
        mapMoves.clear();
        mapMoves.addAll(newMapMoves);
        diffResult.dispatchUpdatesTo(this);
    }

    public static class ViewHolder extends RecyclerView.ViewHolder {
        TextView textViewNumMove;
        TextView textViewMove;

        public ViewHolder(View itemView, OnItemClickListener listener) {
            super(itemView);
            textViewNumMove = itemView.findViewById(R.id.TextViewNumMove);
            textViewMove = itemView.findViewById(R.id.TextViewMove);

            itemView.setOnClickListener(v -> {
                int pos = getAdapterPosition();
                if (pos != RecyclerView.NO_POSITION && listener != null) {
                    listener.onMoveItemClick(pos);
                }
            });
        }
    }

    @NonNull
    @Override
    public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.pgn_item_view, parent, false);
        return new ViewHolder(view, listener);
    }

    @Override
    public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
        HashMap<String, String> item = mapMoves.get(position);

        if ("yes".equals(item.get("turn"))) {
            // holder.textViewMove.setPaintFlags(holder.textViewMove.getPaintFlags() | Paint.UNDERLINE_TEXT_FLAG);
            holder.textViewMove.setTextColor(ContextCompat.getColor(context, R.color.primaryColor));
        } else {
            holder.textViewMove.setTextColor(ContextCompat.getColor(context, R.color.surfaceTextColor));
        }
        holder.textViewNumMove.setText(item.get("nr"));
        holder.textViewMove.setText(item.get("move"));
    }

    @Override
    public int getItemCount() {
        return mapMoves.size();
    }

    protected boolean equalsItem(HashMap<String, String> item, HashMap<String, String> newItem) {
        if (!item.get("nr").equals(newItem.get("nr"))) {
            return false;
        }
        if (!item.get("turn").equals(newItem.get("turn"))) {
            return false;
        }
        if (!item.get("move").equals(newItem.get("move"))) {
            return false;
        }
        return true;
    }
}

