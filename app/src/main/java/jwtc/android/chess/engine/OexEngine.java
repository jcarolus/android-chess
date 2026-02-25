package jwtc.android.chess.engine;

import android.content.Context;
import android.util.Log;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.io.PrintWriter;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import jwtc.android.chess.engine.oex.OexEngineDescriptor;
import jwtc.android.chess.engine.oex.OexEngineResolver;
import jwtc.android.chess.services.GameApi;
import jwtc.chess.JNI;
import jwtc.chess.Move;
import jwtc.chess.Pos;
import jwtc.chess.board.BoardConstants;

public class OexEngine extends EngineApi {
    private static final String TAG = "OexEngine";
    private static final Pattern MOVE_PATTERN = Pattern.compile("^([a-h][1-8])([a-h][1-8])([qrbn])?$");
    private static final Pattern SCORE_CP_PATTERN = Pattern.compile(".*\\bscore\\s+cp\\s+(-?\\d+).*");
    private static final Pattern SCORE_MATE_PATTERN = Pattern.compile(".*\\bscore\\s+mate\\s+(-?\\d+).*");

    private final Context context;
    private final GameApi gameApi;
    private final OexEngineResolver resolver;
    private final JNI jni = JNI.getInstance();

    private String preferredEngineId;
    private Process process;
    private BufferedReader reader;
    private PrintWriter writer;
    private Thread readThread;
    private volatile boolean searching = false;
    private volatile boolean destroyed = false;
    private volatile int latestValue = 0;

    public OexEngine(Context context, GameApi gameApi, String preferredEngineId) {
        this.context = context.getApplicationContext();
        this.gameApi = gameApi;
        this.resolver = new OexEngineResolver(this.context);
        this.preferredEngineId = preferredEngineId;
    }

    public static boolean hasAvailableEngines(Context context) {
        return !new OexEngineResolver(context).resolveEngines().isEmpty();
    }

    public void setPreferredEngineId(String preferredEngineId) {
        this.preferredEngineId = preferredEngineId;
    }

    @Override
    public synchronized void play() {
        Log.d(TAG, "play " + msecs + ", " + ply);
        if (gameApi.isEnded() || searching) {
            return;
        }

        for (EngineListener listener : listeners) {
            listener.OnEngineStarted();
        }

        if (!ensureProcess()) {
            sendErrorMessageFromThread();
            return;
        }

        searching = true;
        sendCommand("ucinewgame");
        sendCommand("position fen " + jni.toFEN());
        if (ply > 0) {
            sendCommand("go depth " + ply);
        } else if (msecs > 0) {
            sendCommand("go movetime " + msecs);
        } else {
            searching = false;
            sendErrorMessageFromThread();
        }
    }

    @Override
    public synchronized boolean isReady() {
        return !searching;
    }

    @Override
    public synchronized void abort(Runnable onDone) {
        boolean wasBusy = searching;
        searching = false;
        sendCommand("stop");

        if (wasBusy) {
            for (EngineListener listener : listeners) {
                listener.OnEngineAborted();
            }
        }

        if (onDone != null) {
            updateHandler.post(onDone);
        }
    }

    @Override
    public synchronized void destroy() {
        destroyed = true;
        searching = false;
        sendCommand("quit");

        if (readThread != null) {
            readThread.interrupt();
            readThread = null;
        }

        if (process != null) {
            process.destroy();
            process = null;
        }

        closeIo();
    }

    private synchronized boolean ensureProcess() {
        if (process != null) {
            return true;
        }
        try {
            List<OexEngineDescriptor> engines = resolver.resolveEngines();
            OexEngineDescriptor engine = resolver.selectEngine(engines, preferredEngineId);
            if (engine == null) {
                Log.w(TAG, "No OEX engines found");
                return false;
            }
            preferredEngineId = engine.getId();
            process = Runtime.getRuntime().exec(resolver.ensureLocalCopy(engine).getAbsolutePath());
            reader = new BufferedReader(new InputStreamReader(process.getInputStream()));
            writer = new PrintWriter(new OutputStreamWriter(process.getOutputStream()));
            destroyed = false;
            startReaderThread();
            sendCommand("uci");
            sendCommand("isready");
            return true;
        } catch (Exception ex) {
            Log.e(TAG, "Failed to start OEX engine", ex);
            process = null;
            closeIo();
            return false;
        }
    }

    private synchronized void closeIo() {
        try {
            if (reader != null) {
                reader.close();
            }
        } catch (IOException ignored) {
        }
        reader = null;
        if (writer != null) {
            writer.close();
        }
        writer = null;
    }

    private void startReaderThread() {
        readThread = new Thread(() -> {
            try {
                String line;
                while (!Thread.currentThread().isInterrupted() && reader != null && (line = reader.readLine()) != null) {
                    parseLine(line.trim());
                }
            } catch (Exception ex) {
                if (!destroyed) {
                    Log.e(TAG, "Error while reading from OEX engine", ex);
                    sendErrorMessageFromThread();
                }
            } finally {
                synchronized (OexEngine.this) {
                    searching = false;
                    if (process != null) {
                        process.destroy();
                        process = null;
                    }
                    closeIo();
                }
            }
        });
        readThread.start();
    }

    private void parseLine(String line) {
        if (line.startsWith("info ")) {
            parseInfo(line);
            return;
        }
        if (line.startsWith("bestmove ")) {
            parseBestMove(line);
        }
    }

    private void parseInfo(String line) {
        Matcher cpMatch = SCORE_CP_PATTERN.matcher(line);
        if (cpMatch.matches()) {
            latestValue = Integer.parseInt(cpMatch.group(1));
        } else {
            Matcher mateMatch = SCORE_MATE_PATTERN.matcher(line);
            if (mateMatch.matches()) {
                int mateIn = Integer.parseInt(mateMatch.group(1));
                int sign = mateIn >= 0 ? 1 : -1;
                latestValue = sign * (BoardConstants.VALUATION_MATE - Math.abs(mateIn));
            }
        }
        sendMessageFromThread(line, (float) latestValue / 100.0F);
    }

    private void parseBestMove(String line) {
        searching = false;
        String[] parts = line.split("\\s+");
        if (parts.length < 2) {
            sendErrorMessageFromThread();
            return;
        }

        int move = resolveMove(parts[1]);
        if (move == 0) {
            Log.w(TAG, "Could not resolve bestmove: " + parts[1]);
            sendErrorMessageFromThread();
            return;
        }
        sendMoveMessageFromThread(move, -1, latestValue);
    }

    private int resolveMove(String uciMove) {
        Matcher matcher = MOVE_PATTERN.matcher(uciMove);
        if (!matcher.matches()) {
            return 0;
        }

        try {
            int from = Pos.fromString(matcher.group(1));
            int to = Pos.fromString(matcher.group(2));
            int promotionPiece = promotionPieceFromUci(matcher.group(3));

            int size = jni.getMoveArraySize();
            for (int i = 0; i < size; i++) {
                int move = jni.getMoveArrayAt(i);
                if (Move.getFrom(move) != from || Move.getTo(move) != to) {
                    continue;
                }
                if (promotionPiece != -1) {
                    if (!Move.isPromotionMove(move) || Move.getPromotionPiece(move) != promotionPiece) {
                        continue;
                    }
                }
                return move;
            }
        } catch (Exception ex) {
            Log.w(TAG, "Failed to resolve move " + uciMove, ex);
        }
        return 0;
    }

    private int promotionPieceFromUci(String promotion) {
        if (promotion == null || promotion.isEmpty()) {
            return -1;
        }
        switch (promotion.toLowerCase()) {
            case "q":
                return BoardConstants.QUEEN;
            case "r":
                return BoardConstants.ROOK;
            case "b":
                return BoardConstants.BISHOP;
            case "n":
                return BoardConstants.KNIGHT;
            default:
                return -1;
        }
    }

    private synchronized void sendCommand(String command) {
        if (writer == null) {
            return;
        }
        writer.println(command);
        writer.flush();
    }
}
