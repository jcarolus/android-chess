package jwtc.android.chess.services;

import android.content.res.Resources;
import android.util.Log;

import java.util.Locale;
import java.util.regex.Matcher;

import jwtc.android.chess.R;
import jwtc.chess.Move;
import jwtc.chess.Pgn;
import jwtc.chess.Pos;

/** Spells out a SAN move for text to speech and content descriptions, e.g. "Knight F 3" or "castle short". */
public final class MoveSpeech {
    private static final String TAG = "MoveSpeech";

    private MoveSpeech() {}

    /**
     * @param move the encoded move, used for the from-square and en passant; sMove supplies the rest
     * @param resources localized strings; the system resources are used when null
     */
    public static String describe(Resources resources, String sMove, int move, boolean useLongMove) {
        if (resources == null) {
            resources = Resources.getSystem();
        }
        StringBuilder sMoveSpeech = new StringBuilder();

        // check regular move
        Matcher matchToken = Pgn.MOVE.matcher(sMove);
        if (matchToken.matches()) {
            // 1            2                 3                 4   5                6                7             8             9       10
            // (K|Q|R|B|N)?(a|b|c|d|e|f|g|h)?(1|2|3|4|5|6|7|8)?(x)?(a|b|c|d|e|f|g|h)(1|2|3|4|5|6|7|8)(=Q|=R|=B|=N)?(@[a-h][1-8])?(\\+|#)?([\\?\\!]*)?[\\s]*")
            String piece = matchToken.group(1);
            if (piece != null) {
                piece = getPieceName(resources, piece);
            } else {
                piece = getString(resources, R.string.piece_pawn, "Pawn");
            }
            sMoveSpeech.append(piece).append(" ");
            String sFile = matchToken.group(2);
            String sRow = matchToken.group(3);

            if (useLongMove) {
                sMoveSpeech.append(Pos.toString(Move.getFrom(move)).toUpperCase()).append(" ");
            } else {

                if (sFile != null) {
                    sMoveSpeech.append(sFile.toUpperCase(Locale.ROOT)).append(" ");
                }

                if (sRow != null) {
                    sMoveSpeech.append(sRow).append(" ");
                }
            }

            if (matchToken.group(4) != null) {
                sMoveSpeech.append(getString(resources, R.string.tts_move_takes, "takes")).append(" ");
            }
            sFile = matchToken.group(5);
            sRow = matchToken.group(6);
            if (sFile != null && sRow != null) {
                sMoveSpeech.append(getString(
                    resources,
                    R.string.tts_move_square,
                    "%1$s%2$s",
                    sFile.toUpperCase(Locale.ROOT),
                    sRow
                )).append(" ");
            }

            if (Move.isEP(move)) {
                sMoveSpeech.append(getString(resources, R.string.tts_move_en_passant, "en passant")).append(" ");
            }

            String sPromote = matchToken.group(7);
            if (sPromote != null && sPromote.length() > 1) {
                sMoveSpeech.append(getString(
                    resources,
                    R.string.tts_move_promotes_to,
                    "promotes to %1$s",
                    getPieceName(resources, sPromote.substring(1))
                )).append(" ");
            }
            // ignore Duck for now

            String sSpecial = matchToken.group(9);
            if (sSpecial != null) {
                if (sSpecial.equals("+")) {
                    sMoveSpeech.append(getString(resources, R.string.state_check, "check")).append(" ");
                } else if (sSpecial.equals("#")) {
                    sMoveSpeech.append(getString(resources, R.string.tts_move_checkmate, "checkmate")).append(" ");
                }
            }
        } else if (sMove.contains("O-O-O")) {
            sMoveSpeech.append(getString(resources, R.string.tts_move_castle_long, "castle long")).append(" ");
            if (sMove.contains("+")) {
                sMoveSpeech.append(getString(resources, R.string.state_check, "check")).append(" ");
            }
            if (sMove.contains("#")) {
                sMoveSpeech.append(getString(resources, R.string.tts_move_checkmate, "checkmate")).append(" ");
            }
        } else if (sMove.contains("O-O")) {
            sMoveSpeech.append(getString(resources, R.string.tts_move_castle_short, "castle short")).append(" ");
            if (sMove.contains("+")) {
                sMoveSpeech.append(getString(resources, R.string.state_check, "check")).append(" ");
            }
            if (sMove.contains("#")) {
                sMoveSpeech.append(getString(resources, R.string.tts_move_checkmate, "checkmate")).append(" ");
            }
        } else {
            Log.d(TAG, "Did not parse move " + sMove);
        }

        String spoken = sMoveSpeech.toString().trim();
        Log.d(TAG, "TTS " + sMove + " => " + spoken);

        return spoken;
    }

    private static String getPieceName(Resources resources, String piece) {
        switch (piece) {
            case "K":
                return getString(resources, R.string.piece_king, "King");
            case "Q":
                return getString(resources, R.string.piece_queen, "Queen");
            case "R":
                return getString(resources, R.string.piece_rook, "Rook");
            case "B":
                return getString(resources, R.string.piece_bishop, "Bishop");
            case "N":
                return getString(resources, R.string.piece_knight, "Knight");
            default:
                return "";
        }
    }

    private static String getString(Resources resources, int resourceId, String fallback) {
        if (resources != null) {
            try {
                return resources.getString(resourceId);
            } catch (Resources.NotFoundException ignored) {
            }
        }
        return fallback;
    }

    private static String getString(Resources resources, int resourceId, String fallback, Object... args) {
        if (resources != null) {
            try {
                return resources.getString(resourceId, args);
            } catch (Resources.NotFoundException ignored) {
            }
        }
        return String.format(Locale.getDefault(), fallback, args);
    }
}
