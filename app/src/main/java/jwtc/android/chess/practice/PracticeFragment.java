package jwtc.android.chess.practice;

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
import jwtc.android.chess.play.PlayActivity;
import jwtc.android.chess.play.PlayMode;
import jwtc.android.chess.play.PlayModeAdapter;
import jwtc.android.chess.puzzle.PuzzleActivity;

public class PracticeFragment extends Fragment {

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
                new PlayMode("Puzzles", R.drawable.ic_pieces_chessnut_wp, true),
                new PlayMode("Endgame Trainer", R.drawable.ic_pieces_alpha_wp, false), // Future feature
                new PlayMode("Versus AI", R.drawable.lichess, false), // Future feature
                new PlayMode("Game Explorer", R.drawable.ic_pieces_elisa_wn, false) // Future feature
        );

        grid.setAdapter(new PlayModeAdapter(modes, this::onModeSelected));

        return root;
    }

    private void onModeSelected(PlayMode mode) {
        Intent intent = new Intent();
        if (mode.title.equals("Puzzles")) {
            intent.setClass(requireContext(), PuzzleActivity.class);
            startMode(intent);
        }

    }
    private void startMode (Intent intent){
        intent.setFlags(Intent.FLAG_ACTIVITY_REORDER_TO_FRONT);
        startActivity(intent);
    }
}

