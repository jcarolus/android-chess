package jwtc.android.chess;

/**
 * A superclass to be used as the activity reference in both normal and PlayHub modes.
 * Both main and PlayHubActivity inherit this class, and this allows chessView to assume the
 * existence of the methods described here, whether its _parent is main and whether it
 * is PlayHubActivity.
 */
public class ChessActivity extends MyBaseActivity {

    public void showSubViewMenu() {
        // intentionally empty
    }

    public void saveGame() {
        // intentionally empty
    }

    public void soundNotification(String sSpeech) {
        // intentionally empty
    }
}
