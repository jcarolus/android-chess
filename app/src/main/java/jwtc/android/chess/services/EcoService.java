package jwtc.android.chess.services;

import android.content.res.AssetManager;
import android.util.Log;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;
import jwtc.android.chess.engine.RustBoardNative;
import jwtc.android.chess.engine.RustBoardResult;
import jwtc.android.chess.engine.RustSearchRequest;
import jwtc.chess.board.EcoHash;
import org.json.JSONArray;
import org.json.JSONObject;

public class EcoService {
    private static final String TAG = "EcoService";
    private HMap hashMap = null;
    private final GameApi gameApi;

    public EcoService(GameApi gameApi) {
        this.gameApi = gameApi;
    }

    public void load(final AssetManager assetManager) {
        if (hashMap == null) {
            (new Thread(
                            new Runnable() {
                                public void run() {
                                    try {
                                        Thread.sleep(1000);
                                        long start = System.currentTimeMillis();

                                        hashMap = HMap.read(assetManager, "hashmap.bin");
                                        Log.i(
                                                TAG,
                                                "ECO size: "
                                                        + hashMap.getSize()
                                                        + " load ms:"
                                                        + (System.currentTimeMillis() - start));
                                    } catch (Exception e) {
                                        Log.d(TAG, "Could not read the opening database");
                                    }
                                }
                            }))
                    .start();
        }
    }

    public String getEcoNameByHash(long hash) {
        if (hashMap != null) {
            return hashMap.get(hash);
        } else {
            Log.d(TAG, "getEcoNameByHash - no hash map ");
        }
        Log.d(TAG, "getEcoNameByHash - no hashMap for " + hash);
        return null;
    }

    public ArrayList<Integer> getAvailableMoves() {
        ArrayList<Integer> moves = new ArrayList<>();
        for (OpeningMove opening : probeMoves()) {
            moves.add(opening.move);
        }
        return moves;
    }

    public JSONArray getAvailable() {
        JSONArray result = new JSONArray();
        for (OpeningMove opening : probeMoves()) {
            try {
                JSONObject entry = new JSONObject();
                entry.put("name", opening.name);
                entry.put("move", opening.san);
                result.put(entry);
            } catch (Exception ignored) {
            }
        }
        return result;
    }

    private ArrayList<OpeningMove> probeMoves() {
        ArrayList<OpeningMove> openings = new ArrayList<>();
        if (hashMap == null || gameApi.hasPendingDuckMove() || gameApi.isPositionEnded()) {
            return openings;
        }
        String fen = gameApi.getFEN();
        long hash = gameApi.getHashKey();
        RustSearchRequest.Variant variant = gameApi.rustVariant();
        int[] files =
                variant == RustSearchRequest.Variant.CHESS_960
                        ? RustSearchRequest.chess960Files(fen, fen.split(" ")[1].equals("w"))
                        : null;
        String[] history = new String[0];
        Map<String, String> duckTurns = new HashMap<>();
        if (variant == RustSearchRequest.Variant.DUCK) {
            for (String turn :
                    RustBoardResult.words(
                            RustBoardNative.legalMoves(variant, fen, history).moves)) {
                String pieceMove = turn.split("@")[0];
                // ECO hashes ignore the duck square, so one legal placement per piece move
                // suffices.
                duckTurns.putIfAbsent(pieceMove, turn);
            }
        }
        int count = gameApi.getMoveArraySize();
        for (int i = 0; i < count; i++) {
            int move = gameApi.getMoveArrayAt(i);
            String coordinate = RustSearchRequest.toRustCoordinate(move, files);
            String turn =
                    variant == RustSearchRequest.Variant.DUCK
                            ? duckTurns.get(coordinate)
                            : coordinate;
            if (turn == null) {
                continue;
            }
            RustBoardResult applied = RustBoardNative.applyMove(variant, fen, history, turn);
            long childHash = EcoHash.afterMove(hash, fen, applied.fen, move);
            String name = hashMap.get(childHash);
            if (name != null) {
                String san = RustBoardNative.san(variant, fen, history, coordinate).san;
                openings.add(new OpeningMove(move, name, san));
            }
        }
        return openings;
    }

    private static final class OpeningMove {
        final int move;
        final String name;
        final String san;

        OpeningMove(int move, String name, String san) {
            this.move = move;
            this.name = name;
            this.san = san;
        }
    }
}
