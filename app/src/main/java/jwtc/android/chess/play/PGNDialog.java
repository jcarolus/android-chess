package jwtc.android.chess.play;

import jwtc.android.chess.helpers.EinkMode;
import android.app.Dialog;
import android.content.Context;
import android.view.Window;
import android.widget.GridView;

import java.util.ArrayList;
import java.util.ArrayDeque;
import java.util.Deque;
import jwtc.chess.GameTree.Node;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;

import androidx.annotation.NonNull;

import com.google.android.material.button.MaterialButton;

import jwtc.android.chess.R;
import jwtc.android.chess.helpers.Clipboard;
import jwtc.android.chess.services.GameApi;
import jwtc.chess.PGNEntry;
import jwtc.chess.Pos;

public class PGNDialog extends Dialog {
    private static final String TAG = "PGNDialog";

    public PGNDialog(@NonNull final Context context, final GameApi gameApi) {
        super(context, EinkMode.isThemeEnabled() ? R.style.ChessDialogThemeEink : R.style.ChessDialogTheme);

        requestWindowFeature(Window.FEATURE_NO_TITLE);

        setContentView(R.layout.full_pgn);

        ArrayList<MoveItem> mapMoves = new ArrayList<MoveItem>();
        MoveItemAdapter adapterMoves = new MoveItemAdapter(context, mapMoves);

        GridView contentLayout = findViewById(R.id.LayoutContent);

        contentLayout.setAdapter(adapterMoves);

        // Show complete branches in one column; node identity distinguishes equal ply numbers.
        contentLayout.setNumColumns(1);
        ArrayList<Node> nodes = new ArrayList<>();
        Runnable refresh = () -> {
            nodes.clear();
            mapMoves.clear();
            Deque<Node> remaining = new ArrayDeque<>();
            for (int i = gameApi.getRootNode().getChildren().size() - 1; i >= 0; i--)
                remaining.push(gameApi.getRootNode().getChildren().get(i));
            while (!remaining.isEmpty()) {
                Node node = remaining.pop();
                nodes.add(node);
                PGNEntry entry = node.getEntry();
                String move = entry.sMove;
                if (entry.duckMove != -1) move += "@" + Pos.toString(entry.duckMove);
                StringBuilder prefix = new StringBuilder();
                for (Node ancestor = node; ancestor.getParent() != null; ancestor = ancestor.getParent()) {
                    if (ancestor.getParent().getNext() != ancestor) prefix.append("  ↳ ");
                }
                String annotation = entry.sAnnotation;
                if (!node.getLeadingComment().isEmpty()) annotation = node.getLeadingComment() + " " + annotation;
                for (int nag : node.getNags()) annotation += " $" + nag;
                mapMoves.add(new MoveItem(prefix + gameApi.getMoveNumber(node) + " ", move, entry.move,
                    annotation, node == gameApi.getCurrentNode() ? R.drawable.turnblack : 0));
                for (int i = node.getChildren().size() - 1; i >= 0; i--) remaining.push(node.getChildren().get(i));
            }
            adapterMoves.notifyDataSetChanged();
        };
        refresh.run();
        contentLayout.smoothScrollToPosition(Math.max(0, nodes.indexOf(gameApi.getCurrentNode())));
        contentLayout.setOnItemClickListener((parent, view, position, id) -> {
            if (gameApi.goTo(nodes.get(position))) dismiss();
        });
        contentLayout.setOnItemLongClickListener((parent, view, position, id) -> {
            Node node = nodes.get(position);
            boolean alternative = node.getParent().getNext() != node;
            ArrayList<String> actions = new ArrayList<>();
            actions.add(context.getString(R.string.pgn_return_main_line));
            if (alternative) {
                actions.add(context.getString(R.string.pgn_promote_variation));
                actions.add(context.getString(R.string.pgn_delete_variation));
            }
            new MaterialAlertDialogBuilder(context)
                .setItems(actions.toArray(new String[0]), (dialog, which) -> {
                    if (which == 0) gameApi.returnToMainLine();
                    else if (which == 1) gameApi.promoteVariation(node);
                    else gameApi.deleteVariation(node);
                    refresh.run();
                }).show();
            return true;
        });

        MaterialButton buttonClip = findViewById(R.id.ButtonClip);
        buttonClip.setOnClickListener(view -> {
            Clipboard.stringToClipboard(context, gameApi.exportFullPGN(), context.getString(R.string.copied_clipboard_success));
            dismiss();
        });

        MaterialButton buttonClose = findViewById(R.id.ButtonClose);
        buttonClose.setOnClickListener(view -> dismiss());
    }
}
