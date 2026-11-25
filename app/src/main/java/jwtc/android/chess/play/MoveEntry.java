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
import jwtc.android.chess.R;
import jwtc.android.chess.activities.ChessBoardActivity;
import jwtc.android.chess.helpers.ChessAlertDialogBuilder;
import jwtc.android.chess.moveintake.MoveCandidate;
import jwtc.android.chess.moveintake.MoveOutcome;
import jwtc.android.chess.services.GameApi;
import jwtc.chess.GameTree.Node;
import jwtc.chess.LegalMove;
import jwtc.chess.Move;
import jwtc.chess.MoveResolver;
import jwtc.chess.board.BoardConstants;

/**
 * Text and dictated move entry. It parses what the user typed or said, resolves it against the
 * position, asks for a promotion piece the words left out and, when the user wants it, reads the
 * move back for review. The resolved move is offered to the Mode's Move intake like any other.
 */
public final class MoveEntry {
    public static final String PREF_MOVE_ENTRY = "play_move_entry";
    public static final String PREF_CONFIRM_MOVE_ENTRY = "play_confirm_move_entry";

    private final ChessBoardActivity activity;
    private final MoveEntryField field;
    private GameApi gameApi;
    private Node currentNode;
    private Object position;
    private boolean available;
    private int positionRevision;
    private boolean reviewingMove;

    public MoveEntry(ChessBoardActivity activity) {
        this.activity = activity;
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
                    field,
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT);
        }
    }

    /**
     * Shows the entry for the Game, or hides it for a null Game. A new Game or position clears what
     * was typed. Whether a move may be played is the Move intake's decision.
     */
    public void update(GameApi game, Object position) {
        boolean shown =
                game != null
                        && activity.getPrefs().getBoolean(PREF_MOVE_ENTRY, false)
                        && game.variant().supportsMoveEntry();
        if (game != gameApi || !Objects.equals(position, this.position) || shown != available) {
            reset();
        }
        gameApi = game;
        currentNode = game == null ? null : game.getCurrentNode();
        this.position = position;
        available = shown;
        field.setVisibility(shown ? View.VISIBLE : View.GONE);
    }

    public void reset() {
        positionRevision++;
        available = false;
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
        MoveResolver.Resolution resolution =
                coordinates.san == null
                        ? MoveResolver.coordinates(
                                gameApi.legalMoves(),
                                gameApi.variant(),
                                coordinates.from,
                                coordinates.to,
                                coordinates.promotion)
                        : MoveResolver.spokenSan(gameApi.legalMoves(), coordinates.san);
        switch (resolution.kind) {
            case MOVE:
                confirmMoveEntry(coordinates, resolution.move);
                break;
            case NEEDS_PROMOTION:
                askPromotion(coordinates, resolution.from, resolution.to);
                break;
            default:
                field.setError(activity.getString(R.string.move_entry_illegal));
        }
    }

    private void askPromotion(DictatedMove coordinates, int from, int to) {
        int revision = positionRevision;
        int textRevision = field.textRevision();
        field.setError(null);
        reviewingMove = true;
        boolean[] selectedPiece = {false};
        activity.promotionDialog(
                        piece -> {
                            selectedPiece[0] = true;
                            reviewingMove = false;
                            field.resetConnection(false);
                            if (revision == positionRevision
                                    && canApplyMoveEntry()
                                    && textRevision == field.textRevision()) {
                                MoveResolver.Resolution selected =
                                        MoveResolver.coordinates(
                                                gameApi.legalMoves(),
                                                gameApi.variant(),
                                                from,
                                                to,
                                                piece);
                                if (selected.kind == MoveResolver.Resolution.Kind.MOVE) {
                                    confirmMoveEntry(coordinates, selected.move);
                                } else {
                                    field.setError(activity.getString(R.string.move_entry_illegal));
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
    }

    private void confirmMoveEntry(DictatedMove coordinates, LegalMove move) {
        field.setError(null);
        MoveCandidate candidate = MoveCandidate.resolved(move.packed);
        if (!activity.getPrefs().getBoolean(PREF_CONFIRM_MOVE_ENTRY, false)) {
            offer(candidate);
            return;
        }
        int revision = positionRevision;
        int textRevision = field.textRevision();
        String description = coordinates.san == null ? coordinates.describe() : move.san;
        if (Move.isPromotionMove(move.packed)) {
            description +=
                    " "
                            + activity.getResources()
                                    .getStringArray(R.array.promotionpieces)[
                                    BoardConstants.QUEEN - Move.getPromotionPiece(move.packed)];
        }
        reviewingMove = true;
        new ChessAlertDialogBuilder(activity)
                .setMessage(activity.getString(R.string.move_entry_review, description, move.san))
                .setPositiveButton(
                        R.string.alert_yes,
                        (dialog, which) -> {
                            if (revision == positionRevision
                                    && canApplyMoveEntry()
                                    && textRevision == field.textRevision()) {
                                // The user has just read the move back, so a Mode that confirms
                                // moves does not ask again.
                                offer(candidate.withMoveConfirmed());
                            }
                        })
                .setNegativeButton(R.string.alert_no, null)
                .setOnDismissListener(
                        dialog -> {
                            reviewingMove = false;
                            field.resetConnection(false);
                        })
                .show();
    }

    private void offer(MoveCandidate candidate) {
        MoveOutcome outcome = activity.offerMove(candidate);
        if (outcome.kind() != MoveOutcome.Kind.REJECTED) {
            field.resetConnection(true);
            return;
        }
        field.setError(
                activity.getString(
                        outcome.reason() == MoveOutcome.Reason.ILLEGAL
                                ? R.string.move_entry_illegal
                                : R.string.move_entry_wrong_turn));
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

    /** Whether the position the text was entered for is still the one on the board. */
    private boolean canApplyMoveEntry() {
        return available && currentNode == gameApi.getCurrentNode();
    }
}
