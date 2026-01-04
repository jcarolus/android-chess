package jwtc.android.chess.play;

import android.content.Intent;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.recyclerview.widget.GridLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import java.util.Arrays;
import java.util.List;

import jwtc.android.chess.R;

public class PlayFragment extends Fragment {

    @Nullable
    @Override
    public View onCreateView(
            @NonNull LayoutInflater inflater,
            @Nullable ViewGroup container,
            @Nullable Bundle savedInstanceState) {

        View root = inflater.inflate(R.layout.fragment_play, container, false);

        RecyclerView grid = root.findViewById(R.id.playGrid);
        grid.setLayoutManager(new GridLayoutManager(getContext(), 2));

        List<PlayMode> modes = Arrays.asList(
                new PlayMode("Play Solo", R.drawable.ic_pieces_chessnut_wp),
                new PlayMode("FreeChess", R.drawable.ic_pieces_alpha_wp),
                new PlayMode("Lichess", R.drawable.lichess),
                new PlayMode("Hotspot", R.drawable.ic_pieces_elisa_wn)
        );

        grid.setAdapter(new PlayModeAdapter(modes, this::onModeSelected));

        return root;
    }

    private void onModeSelected(PlayMode mode) {
        Intent intent = new Intent();
        if (mode.title.equals("Play Solo")) {
            startMode(intent);
        }
    }
    private void startMode(Intent intent) {
                    intent.setClass(requireContext(), PlayActivity.class);
                    intent.setFlags(Intent.FLAG_ACTIVITY_REORDER_TO_FRONT);
                    startActivity(intent);
    }
}

