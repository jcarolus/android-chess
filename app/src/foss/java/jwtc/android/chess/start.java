package jwtc.android.chess;

import android.os.Bundle;

import jwtc.android.chess.activities.StartBaseActivity;

/**
 * start
 *
 * Application launch activity.
 *
 * This class exists primarily as a lightweight entry point that:
 * - Specifies which layout resource should be used on launch
 * - Delegates all initialization logic to {@link StartBaseActivity}
 *
 * Keeping this class thin allows:
 * - Easy swapping of launch layouts in the future
 * - A clean separation between app entry and shared activity logic
 *
 * Note:
 * Most functionality (theme handling, navigation, fragment management)
 * is implemented in {@link StartBaseActivity}.
 */
public class start extends StartBaseActivity {

    /**
     * Called when the activity is first created.
     *
     * Sets the layout resource used by {@link StartBaseActivity}
     * before delegating to the base implementation.
     *
     * @param savedInstanceState Previously saved state, if any
     */
    @Override
    public void onCreate(Bundle savedInstanceState) {

        // Specify the layout for the start activity
        // This allows StartBaseActivity to remain reusable
        layoutResource = R.layout.start;

        // Delegate full setup to the base activity
        super.onCreate(savedInstanceState);
    }
}