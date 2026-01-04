package jwtc.android.chess.settings;

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
import jwtc.android.chess.activities.BoardPreferencesActivity;
import jwtc.android.chess.play.PlayMode;
import jwtc.android.chess.play.PlayModeAdapter;
import jwtc.android.chess.tools.AdvancedActivity;

public class SettingsFragment extends Fragment {

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
                new PlayMode("Board Preference", R.drawable.outline_settings_24, true),
                new PlayMode("Advanced", R.drawable.ic_exclamation_triangle, true)
        );

        grid.setAdapter(new PlayModeAdapter(modes, this::onModeSelected));

        return root;
    }

    private void onModeSelected(PlayMode mode) {
        Intent intent = new Intent();
        if (mode.title.equals("Board Preference")) {
            intent.setClass(requireContext(), BoardPreferencesActivity.class);
            startMode(intent);
        } else if (mode.title.equals("Advanced")) {
            intent.setClass(requireContext(), AdvancedActivity.class);
            startMode(intent);
        }
    }
    private void startMode(Intent intent) {
        intent.setFlags(Intent.FLAG_ACTIVITY_REORDER_TO_FRONT);
        startActivity(intent);
    }
}

