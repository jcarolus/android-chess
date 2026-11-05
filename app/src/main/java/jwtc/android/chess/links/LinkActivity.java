package jwtc.android.chess.links;

import android.content.Intent;
import android.os.Bundle;
import android.widget.Toast;
import jwtc.android.chess.R;
import jwtc.android.chess.activities.BaseActivity;
import jwtc.android.chess.analyze.AnalyzeActivity;
import jwtc.android.chess.hotspotboard.HotspotBoardActivity;
import jwtc.android.chess.lichess.LichessLobbyActivity;

/** Validates external URLs before forwarding only the expected data to an app screen. */
public class LinkActivity extends BaseActivity {
    public static final String EXTRA_HOST = "linkHost";
    public static final String EXTRA_USER = "linkUser";
    public static final String EXTRA_PATH = "linkMovePath";

    @Override
    public void onCreate(Bundle state) {
        super.onCreate(state);
        try {
            AppLink link = AppLink.parse(getIntent().getDataString());
            Intent target;
            switch (link.route) {
                case "position":
                case "game":
                    target = new Intent(this, AnalyzeActivity.class);
                    target.putExtra(
                            "game".equals(link.route)
                                    ? AnalyzeActivity.EXTRA_PGN
                                    : AnalyzeActivity.EXTRA_FEN,
                            link.content);
                    target.putExtra(EXTRA_PATH, link.path);
                    break;
                case "join":
                    target = new Intent(this, HotspotBoardActivity.class);
                    target.putExtra(EXTRA_HOST, link.content);
                    break;
                case "online":
                    target = new Intent(this, LichessLobbyActivity.class);
                    target.putExtra(EXTRA_USER, link.content);
                    break;
                default:
                    target = new Intent(this, jwtc.android.chess.start.class);
            }
            target.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_SINGLE_TOP);
            startActivity(target);
        } catch (IllegalArgumentException e) {
            Toast.makeText(this, R.string.app_link_invalid, Toast.LENGTH_LONG).show();
        }
        finish();
    }
}
