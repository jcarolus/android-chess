package jwtc.android.chess.links;

import android.content.ClipData;
import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.widget.Toast;
import androidx.core.content.FileProvider;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import jwtc.android.chess.R;
import jwtc.android.chess.services.GameApi;
import jwtc.chess.GameTree.Node;

public final class LinkSharing {
    private LinkSharing() {}

    public static void share(Context context, String text) {
        send(context, text, "text/plain");
    }

    private static void send(Context context, String text, String type) {
        Intent intent = new Intent(Intent.ACTION_SEND);
        intent.setType(type);
        intent.putExtra(Intent.EXTRA_TEXT, text);
        context.startActivity(Intent.createChooser(intent, null));
    }

    private static void sharePgn(Context context, String pgn) {
        try {
            File directory = new File(context.getCacheDir(), "shared-games");
            if (!directory.isDirectory() && !directory.mkdirs()) {
                throw new IOException("Cannot create sharing directory");
            }
            File file = File.createTempFile("game-", ".pgn", directory);
            try (FileOutputStream output = new FileOutputStream(file)) {
                output.write(pgn.getBytes(StandardCharsets.UTF_8));
            }
            Uri uri =
                    FileProvider.getUriForFile(
                            context, context.getPackageName() + ".sharedgames", file);
            Intent intent = new Intent(Intent.ACTION_SEND);
            intent.setType("application/x-chess-pgn");
            intent.putExtra(Intent.EXTRA_STREAM, uri);
            intent.setClipData(ClipData.newRawUri("PGN", uri));
            intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
            context.startActivity(Intent.createChooser(intent, null));
        } catch (IOException e) {
            Toast.makeText(context, R.string.share_failed, Toast.LENGTH_LONG).show();
        }
    }

    public static void show(Context context, GameApi game) {
        new MaterialAlertDialogBuilder(context)
                .setItems(
                        new String[] {
                            context.getString(R.string.share_game_link),
                            context.getString(R.string.share_position_link),
                            context.getString(R.string.share_pgn)
                        },
                        (dialog, which) -> {
                            if (which == 2) {
                                sharePgn(context, game.exportFullPGN());
                                return;
                            }
                            try {
                                ArrayList<Integer> reversed = new ArrayList<>();
                                for (Node node = game.getCurrentNode();
                                        node.getParent() != null;
                                        node = node.getParent()) {
                                    reversed.add(
                                            game.getContinuations(node.getParent()).indexOf(node));
                                }
                                int[] path = new int[reversed.size()];
                                for (int i = 0; i < path.length; i++) {
                                    path[i] = reversed.get(path.length - i - 1);
                                }
                                share(
                                        context,
                                        which == 0
                                                ? AppLink.game(game.exportFullPGN(), path)
                                                : AppLink.position(game.getFEN()));
                            } catch (IllegalArgumentException e) {
                                new MaterialAlertDialogBuilder(context)
                                        .setMessage(R.string.app_link_too_large)
                                        .setPositiveButton(
                                                R.string.share_pgn,
                                                (d, w) -> sharePgn(context, game.exportFullPGN()))
                                        .setNegativeButton(R.string.button_cancel, null)
                                        .show();
                            }
                        })
                .show();
    }
}
