package jwtc.android.chess.play;

import android.content.Intent;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageButton;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.recyclerview.widget.GridLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import java.util.Arrays;
import java.util.List;

import jwtc.android.chess.R;
import jwtc.android.chess.hotspotboard.HotspotBoardActivity;
import jwtc.android.chess.ics.ICSClient;
import jwtc.android.chess.lichess.LichessActivity;

/**
 * PlayFragment
 *
 * Main "Play" hub of the application.
 *
 * Responsibilities:
 * - Displays available play modes in a grid layout
 * - Handles navigation to the selected game mode
 * - Provides a share button to invite others to play
 *
 * This fragment acts purely as a UI selector and does not
 * contain any game logic itself.
 */
public class PlayFragment extends Fragment {

    /**
     * Inflates the Play screen UI and initializes its components.
     *
     * @param inflater           Layout inflater
     * @param container          Parent container
     * @param savedInstanceState Previously saved state, if any
     * @return Root view of the fragment
     */
    @Nullable
    @Override
    public View onCreateView(
            @NonNull LayoutInflater inflater,
            @Nullable ViewGroup container,
            @Nullable Bundle savedInstanceState) {

        // Inflate fragment layout
        View root = inflater.inflate(R.layout.fragment_play, container, false);

        // --- Play mode grid setup ---
        RecyclerView grid = root.findViewById(R.id.playGrid);
        grid.setLayoutManager(new GridLayoutManager(getContext(), 2));

        // --- Share button ---
        // Allows users to share an invite message using any supported app
        ImageButton shareBtn = root.findViewById(R.id.btnShare);
        shareBtn.setOnClickListener(v -> {
            Intent shareIntent = new Intent(Intent.ACTION_SEND);
            shareIntent.setType("text/plain");
            shareIntent.putExtra(
                    Intent.EXTRA_TEXT,
                    "Come play chess with me!"
            );
            startActivity(
                    Intent.createChooser(shareIntent, "Share with...")
            );
        });

        // --- Available play modes ---
        // Each mode defines:
        // - Display title
        // - Icon resource
        // - Availability flag
        List<PlayMode> modes = Arrays.asList(
                new PlayMode("Play Solo", R.drawable.ic_pieces_alpha_wq, true),
                new PlayMode("FreeChess", R.drawable.ic_pieces_alpha_wk, true),
                new PlayMode("Lichess", R.drawable.lichess, true),
                new PlayMode("Hotspot", R.drawable.ic_pieces_elisa_wn, true)
        );

        // Attach adapter with click callback
        grid.setAdapter(new PlayModeAdapter(modes, this::onModeSelected));

        return root;
    }

    /**
     * Called when a play mode tile is selected.
     *
     * Routes the user to the appropriate activity based
     * on the selected mode.
     *
     * @param mode Selected play mode
     */
    private void onModeSelected(PlayMode mode) {
        Intent intent = new Intent();

        if (mode.title.equals("Play Solo")) {
            intent.setClass(requireContext(), PlayActivity.class);
            startMode(intent);

        } else if (mode.title.equals("FreeChess")) {
            intent.setClass(requireContext(), ICSClient.class);
            startMode(intent);

        } else if (mode.title.equals("Lichess")) {
            intent.setClass(requireContext(), LichessActivity.class);
            startMode(intent);

        } else if (mode.title.equals("Hotspot")) {
            intent.setClass(requireContext(), HotspotBoardActivity.class);
            startMode(intent);
        }
    }

    /**
     * Starts a selected play mode activity.
     *
     * Uses {@link Intent#FLAG_ACTIVITY_REORDER_TO_FRONT}
     * to reuse an existing instance if it already exists
     * in the activity stack.
     *
     * @param intent Activity intent to launch
     */
    private void startMode(Intent intent) {
        intent.setFlags(Intent.FLAG_ACTIVITY_REORDER_TO_FRONT);
        startActivity(intent);
    }
}
