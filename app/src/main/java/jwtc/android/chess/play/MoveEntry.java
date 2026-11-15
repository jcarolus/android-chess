package jwtc.android.chess.play;

import android.content.res.Configuration;
import android.content.res.Resources;
import android.view.View;
import android.view.ViewGroup;
import android.view.inputmethod.InputMethodManager;
import android.view.inputmethod.InputMethodSubtype;
import android.widget.LinearLayout;
import java.util.Arrays;
import java.util.Locale;
import java.util.Objects;
import java.util.function.BooleanSupplier;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.IntConsumer;
import jwtc.android.chess.R;
import jwtc.android.chess.activities.ChessBoardActivity;
import jwtc.android.chess.helpers.ChessAlertDialogBuilder;
import jwtc.android.chess.services.GameApi;
import jwtc.android.chess.services.GameApi.PositionMove;
import jwtc.chess.GameTree.Node;
import jwtc.chess.Move;
import jwtc.chess.board.BoardConstants;

public final class MoveEntry {
    public static final String PREF_MOVE_ENTRY = "play_move_entry";
    public static final String PREF_CONFIRM_MOVE_ENTRY = "play_confirm_move_entry";

    private final ChessBoardActivity activity;
    private final Function<IntConsumer, ChessAlertDialogBuilder> promotionDialog;
    private final BooleanSupplier requireReview;
    private final Consumer<PositionMove> playMove;
    private final MoveEntryField field;
    private GameApi gameApi;
    private Node currentNode;
    private Object position;
    private boolean playerTurn;
    private int positionRevision;
    private boolean reviewingMove;

    public MoveEntry(
            ChessBoardActivity activity,
            Function<IntConsumer, ChessAlertDialogBuilder> promotionDialog,
            BooleanSupplier requireReview,
            Consumer<PositionMove> playMove) {
        this.activity = activity;
        this.promotionDialog = promotionDialog;
        this.requireReview = requireReview;
        this.playMove = playMove;
        field = new MoveEntryField(activity, this::submitMoveEntry);
        field.setVisibility(View.GONE);
        if (activity.getResources().getConfiguration().orientation
                == Configuration.ORIENTATION_LANDSCAPE) {
            LinearLayout controls = activity.findViewById(R.id.play_controls_content);
            controls.addView(
                    field,
                    0,
                    new LinearLayout.LayoutParams(
                            ViewGroup.LayoutParams.MATCH_PARENT,
                            ViewGroup.LayoutParams.WRAP_CONTENT));
        } else {
            ViewGroup boardBottom = activity.findViewById(R.id.play_board_bottom);
            boardBottom.addView(
                    field, ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        }
    }

    public void update(GameApi game, Object position, boolean playerTurn) {
        boolean available =
                game != null
                        && activity.getPrefs().getBoolean(PREF_MOVE_ENTRY, false)
                        && !game.isDuckVariant();
        boolean canPlay = available && playerTurn && !game.isEnded();
        if (game != gameApi
                || !Objects.equals(position, this.position)
                || canPlay != this.playerTurn) {
            reset();
        }
        gameApi = game;
        currentNode = game == null ? null : game.getCurrentNode();
        this.position = position;
        this.playerTurn = canPlay;
        field.setVisibility(available ? View.VISIBLE : View.GONE);
    }

    public void reset() {
        positionRevision++;
        playerTurn = false;
        field.resetConnection(true);
    }

    private void submitMoveEntry(String text) {
        if (reviewingMove || field.getVisibility() != View.VISIBLE) {
            return;
        }
        if (!canApplyMoveEntry()) {
            field.setError(activity.getString(R.string.move_entry_wrong_turn));
            return;
        }
        DictatedMove coordinates = parseMoveEntry(text);
        if (coordinates == null) {
            field.setError(activity.getString(R.string.move_entry_format));
            return;
        }
        PositionMove move =
                coordinates.san == null
                        ? gameApi.resolveTextMove(
                                coordinates.from, coordinates.to, coordinates.promotion)
                        : gameApi.resolveSanMove(coordinates::matchesSan);
        PositionMove promotion = null;
        if (move == null) {
            if (coordinates.san != null) {
                promotion = gameApi.resolveSanPromotion(coordinates::matchesSanWithoutPromotion);
            } else if (coordinates.promotion < 0) {
                promotion =
                        gameApi.resolveTextMove(
                                coordinates.from, coordinates.to, BoardConstants.QUEEN);
            }
        }
        if (promotion != null) {
            int from = Move.getFrom(promotion.move);
            int to = Move.getTo(promotion.move);
            int revision = positionRevision;
            int textRevision = field.textRevision();
            field.setError(null);
            reviewingMove = true;
            boolean[] selectedPiece = {false};
            promotionDialog
                    .apply(
                            piece -> {
                                selectedPiece[0] = true;
                                reviewingMove = false;
                                field.resetConnection(false);
                                if (revision == positionRevision
                                        && canApplyMoveEntry()
                                        && textRevision == field.textRevision()) {
                                    PositionMove selected =
                                            gameApi.resolveTextMove(from, to, piece);
                                    if (selected != null
                                            && (coordinates.san == null
                                                    || coordinates.matchesSanWithoutPromotion(
                                                            selected.san))) {
                                        confirmMoveEntry(coordinates, selected);
                                    } else {
                                        field.setError(
                                                activity.getString(R.string.move_entry_illegal));
                                    }
                                }
                            })
                    .setOnDismissListener(
                            dialog -> {
                                if (!selectedPiece[0]) {
                                    reviewingMove = false;
                                    field.resetConnection(false);
                                }
                            })
                    .show();
            return;
        }
        if (move == null) {
            field.setError(activity.getString(R.string.move_entry_illegal));
            return;
        }
        confirmMoveEntry(coordinates, move);
    }

    private void confirmMoveEntry(DictatedMove coordinates, PositionMove move) {
        field.setError(null);
        int revision = positionRevision;
        int textRevision = field.textRevision();
        Runnable apply =
                () -> {
                    if (revision == positionRevision
                            && canApplyMoveEntry()
                            && textRevision == field.textRevision()) {
                        field.resetConnection(true);
                        playMove.accept(move);
                    }
                };
        boolean review =
                activity.getPrefs().getBoolean(PREF_CONFIRM_MOVE_ENTRY, false)
                        || requireReview.getAsBoolean();
        boolean overwrite = gameApi.hasContinuationToOverwrite();
        if (!review && !overwrite) {
            apply.run();
            return;
        }
        String description = coordinates.san == null ? coordinates.describe() : move.san;
        if (review && Move.isPromotionMove(move.move)) {
            description +=
                    " "
                            + activity.getResources()
                                    .getStringArray(R.array.promotionpieces)[
                                    BoardConstants.QUEEN - Move.getPromotionPiece(move.move)];
        }
        String message =
                review
                        ? activity.getString(R.string.move_entry_review, description, move.san)
                        : activity.getString(R.string.title_overwrite_main_line);
        if (review && overwrite) {
            message += "\n" + activity.getString(R.string.title_overwrite_main_line);
        }
        reviewingMove = true;
        new ChessAlertDialogBuilder(activity)
                .setMessage(message)
                .setPositiveButton(R.string.alert_yes, (dialog, which) -> apply.run())
                .setNegativeButton(R.string.alert_no, null)
                .setOnDismissListener(
                        dialog -> {
                            reviewingMove = false;
                            field.resetConnection(false);
                        })
                .show();
    }

    private DictatedMove parseMoveEntry(String text) {
        InputMethodSubtype keyboard =
                activity.getSystemService(InputMethodManager.class).getCurrentInputMethodSubtype();
        String languageTag = keyboard == null ? "" : keyboard.getLanguageTag();
        if (keyboard != null && languageTag.isEmpty()) {
            languageTag = keyboard.getLocale().replace('_', '-');
        }
        Locale locale = Locale.forLanguageTag(languageTag);
        if (!locale.getLanguage().isEmpty()) {
            return DictatedMove.parse(text, moveEntryVocabulary(locale));
        }

        // Some keyboards expose no subtype language in handwriting mode.
        return DictatedMove.parseUnambiguous(
                text,
                Arrays.stream(activity.getResources().getStringArray(R.array.move_input_languages))
                        .map(Locale::forLanguageTag)
                        .map(this::moveEntryVocabulary)
                        .iterator());
    }

    private DictatedMove.Vocabulary moveEntryVocabulary(Locale locale) {
        Configuration configuration = new Configuration(activity.getResources().getConfiguration());
        configuration.setLocale(locale);
        Resources vocabulary = activity.createConfigurationContext(configuration).getResources();
        return new DictatedMove.Vocabulary(
                vocabulary.getStringArray(R.array.move_input_ranks),
                vocabulary.getStringArray(R.array.move_input_promotions),
                new String[] {
                    vocabulary.getString(R.string.piece_pawn),
                    vocabulary.getString(R.string.piece_knight),
                    vocabulary.getString(R.string.piece_bishop),
                    vocabulary.getString(R.string.piece_rook),
                    vocabulary.getString(R.string.piece_queen),
                    vocabulary.getString(R.string.piece_king)
                },
                vocabulary.getString(R.string.move_input_captures),
                new String[] {
                    vocabulary.getString(R.string.move_input_castle),
                    vocabulary.getString(R.string.tts_move_castle_short),
                    vocabulary.getString(R.string.tts_move_castle_long)
                });
    }

    private boolean canApplyMoveEntry() {
        return playerTurn && currentNode == gameApi.getCurrentNode() && !gameApi.isEnded();
    }
}
