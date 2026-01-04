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
import jwtc.android.chess.play.PlayMode;
import jwtc.android.chess.play.PlayModeAdapter;
import jwtc.android.chess.puzzle.PuzzleActivity;

/**
 * PracticeFragment
 *
 * Practice and training hub of the application.
 *
 * Responsibilities:
 * - Displays available practice modes in a grid layout
 * - Allows navigation to training-related activities
 * - Visually lists future features as unavailable ("Coming Soon")
 *
 * This fragment intentionally reuses the same layout and adapter
 * as {@link jwtc.android.chess.play.PlayFragment} for UI consistency.
 */
public class PracticeFragment extends Fragment {

    /**
     * Inflates the Practice screen and initializes the practice mode grid.
     *
     * @param inflater           Layout inflater
     * @param container          Parent view group
     * @param savedInstanceState Previously saved state, if any
     * @return Root view of the fragment
     */
    @Nullable
    @Override
    public View onCreateView(
            @NonNull LayoutInflater inflater,
            @Nullable ViewGroup container,
            @Nullable Bundle savedInstanceState) {

        // Reuse the play fragment layout for consistent UI
        View root = inflater.inflate(R.layout.fragment_play, container, false);

        // --- Practice mode grid setup ---
        RecyclerView grid = root.findViewById(R.id.playGrid);
        grid.setLayoutManager(new GridLayoutManager(getContext(), 2));

        // --- Available practice modes ---
        // The boolean flag indicates whether the feature is currently enabled
        List<PlayMode> modes = Arrays.asList(
                new PlayMode("Puzzles", R.drawable.ic_pieces_chessnut_wp, true),
                new PlayMode("Endgame Trainer", R.drawable.ic_pieces_alpha_wp, false), // Future feature
                new PlayMode("Versus AI", R.drawable.lichess, false),                 // Future feature
                new PlayMode("Game Explorer", R.drawable.ic_pieces_elisa_wn, false)   // Future feature
        );

        // Attach adapter with click callback
        grid.setAdapter(new PlayModeAdapter(modes, this::onModeSelected));

        return root;
    }

    /**
     * Called when a practice mode tile is selected.
     *
     * Currently implemented:
     * - Puzzles
     *
     * Other modes are intentionally disabled until implemented.
     *
     * @param mode Selected practice mode
     */
    private void onModeSelected(PlayMode mode) {
        Intent intent = new Intent();

        if (mode.title.equals("Puzzles")) {
            intent.setClass(requireContext(), PuzzleActivity.class);
            startMode(intent);
        }
    }

    /**
     * Starts the selected practice activity.
     *
     * Uses {@link Intent#FLAG_ACTIVITY_REORDER_TO_FRONT}
     * to avoid creating duplicate activity instances.
     *
     * @param intent Activity intent to launch
     */
    private void startMode(Intent intent) {
        intent.setFlags(Intent.FLAG_ACTIVITY_REORDER_TO_FRONT);
        startActivity(intent);
    }
}
