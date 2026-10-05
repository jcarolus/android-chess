package jwtc.android.chess.engine;

import android.content.Context;
import android.util.Log;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.io.PrintWriter;
import java.util.ArrayList;
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
    private static final long RESPONSE_TIMEOUT_MILLIS = 5000;
    private static final Pattern MOVE_PATTERN = Pattern.compile("^([a-h][1-8])([a-h][1-8])([qrbn])?$");
    private static final Pattern SCORE_CP_PATTERN = Pattern.compile(".*\\bscore\\s+cp\\s+(-?\\d+).*");
    private static final Pattern SCORE_MATE_PATTERN = Pattern.compile(".*\\bscore\\s+mate\\s+(-?\\d+).*");
    private static final Pattern MOBILE_OPTION_PATTERN = Pattern.compile(
            "option name (Threads|MultiPV) type spin default \\d+ min (\\d+) max (\\d+)");

    private final GameApi gameApi;
    private final OexEngineResolver resolver;
    private final JNI jni = JNI.getInstance();
    private final ArrayList<Runnable> completionCallbacks = new ArrayList<>();

    private String preferredEngineId;
    private String runningEngineId;
    private Process process;
    private PrintWriter writer;
    private boolean uciReady;
    private boolean engineReady;
    private boolean supportsThreads;
    private boolean supportsMultiPv;
    private Search search;
    private Runnable timeout;
    private int latestValue;

    private static class Search {
        final long id;
        final Mode mode;
        final String fen;
        final String command;
        final int timeMillis;
        boolean sent;
        boolean stopping;
        boolean cancelled;

        Search(long id, Mode mode, String fen, String command, int timeMillis) {
            this.id = id;
            this.mode = mode;
            this.fen = fen;
            this.command = command;
            this.timeMillis = timeMillis;
        }
    }

    public OexEngine(Context context, GameApi gameApi, String preferredEngineId) {
        this.gameApi = gameApi;
        this.resolver = new OexEngineResolver(context.getApplicationContext());
        this.preferredEngineId = preferredEngineId;
    }

    public static boolean hasAvailableEngines(Context context) {
        return !new OexEngineResolver(context).resolveEngines().isEmpty();
    }

    public synchronized void setPreferredEngineId(String preferredEngineId) {
        this.preferredEngineId = preferredEngineId;
    }

    @Override
    public boolean supportsAnalysis() {
        return true;
    }

    @Override
    public synchronized void play() {
        if (gameApi.isEnded() || search != null) {
            return;
        }
        if (ply <= 0 && msecs <= 0) {
            sendErrorMessageFromThread();
            return;
        }
        startSearch(Mode.PLAY, jni.toFEN(), ply > 0 ? "go depth " + ply : "go movetime " + msecs,
                ply > 0 ? 0 : msecs);
    }

    @Override
    public synchronized void analyze(String fen, int timeMillis) {
        validateAnalysisRequest(fen, timeMillis);
        if (search != null) {
            return;
        }
        startSearch(Mode.ANALYSIS, fen.trim(), "go movetime " + timeMillis, timeMillis);
    }

    private void startSearch(Mode mode, String fen, String command, int timeMillis) {
        Search requested = new Search(beginSearch(mode), mode, fen, command, timeMillis);
        search = requested;
        latestValue = 0;
        for (EngineListener listener : listeners) {
            listener.OnEngineStarted();
        }
        // A listener may have cancelled the request synchronously.
        if (search != requested) {
            return;
        }
        if (!ensureProcess()) {
            if (search != null) {
                failSearch("Unable to start engine");
            }
        } else if (engineReady) {
            sendSearch();
        } else {
            scheduleTimeout(() -> failSearch("UCI initialization timed out"), RESPONSE_TIMEOUT_MILLIS);
        }
    }

    @Override
    public synchronized boolean isReady() {
        return search == null;
    }

    @Override
    public synchronized void stop(Runnable onDone) {
        if (search == null) {
            if (onDone != null) {
                updateHandler.post(onDone);
            }
            return;
        }
        if (onDone != null) {
            completionCallbacks.add(onDone);
        }
        if (search.stopping) {
            return;
        }
        search.stopping = true;
        if (!search.sent) {
            // No go command was sent, so there will be no bestmove to wait for.
            closeProcess();
            finishSearch();
        } else if (sendCommand("stop")) {
            scheduleTimeout(() -> failSearch("Engine did not acknowledge stop"), RESPONSE_TIMEOUT_MILLIS);
        }
    }

    @Override
    public synchronized void abort(Runnable onDone) {
        discardSearchResults();
        boolean notify = search != null && !search.cancelled;
        if (search != null) {
            search.cancelled = true;
        }
        stop(onDone);
        if (notify) {
            for (EngineListener listener : listeners) {
                listener.OnEngineAborted();
            }
        }
    }

    @Override
    public synchronized void destroy() {
        discardSearchResults();
        closeProcess();
        finishSearch();
    }

    private boolean ensureProcess() {
        if (process != null && preferredEngineId != null && !preferredEngineId.equals(runningEngineId)) {
            closeProcess();
        }
        if (process != null) {
            return true;
        }
        try {
            List<OexEngineDescriptor> engines = resolver.resolveEngines();
            OexEngineDescriptor engine = resolver.selectEngine(engines, preferredEngineId);
            if (engine == null) {
                return false;
            }
            runningEngineId = engine.getId();
            // Drain stderr along with stdout so a verbose engine cannot block on a full pipe.
            process = new ProcessBuilder(resolver.ensureLocalCopy(engine).getAbsolutePath())
                    .redirectErrorStream(true).start();
            writer = new PrintWriter(new OutputStreamWriter(process.getOutputStream()));
            startReaderThread(process);
            return sendCommand("uci");
        } catch (Exception ex) {
            Log.e(TAG, "Failed to start OEX engine", ex);
            closeProcess();
            return false;
        }
    }

    private void closeProcess() {
        Process oldProcess = process;
        process = null;
        uciReady = false;
        engineReady = false;
        supportsThreads = false;
        supportsMultiPv = false;
        if (oldProcess != null) {
            oldProcess.destroy();
        }
        if (writer != null) {
            writer.close();
            writer = null;
        }
        // The reader owns and closes its stream outside this monitor. Closing a
        // BufferedReader here can deadlock against its blocking readLine().
    }

    private void startReaderThread(Process owner) {
        new Thread(() -> {
            try (BufferedReader input = new BufferedReader(new InputStreamReader(owner.getInputStream()))) {
                String line;
                while ((line = input.readLine()) != null) {
                    synchronized (OexEngine.this) {
                        if (process != owner) {
                            return;
                        }
                        parseLine(line.trim());
                    }
                }
            } catch (IOException ex) {
                Log.d(TAG, "Engine output closed", ex);
            } finally {
                synchronized (OexEngine.this) {
                    if (process == owner) {
                        failSearch("Engine exited unexpectedly");
                    }
                }
            }
        }, "OexEngine-output").start();
    }

    private void parseLine(String line) {
        if (!uciReady) {
            Matcher option = MOBILE_OPTION_PATTERN.matcher(line);
            if (option.matches()) {
                try {
                    if (Long.parseLong(option.group(2)) <= 1 && Long.parseLong(option.group(3)) >= 1) {
                        if ("Threads".equals(option.group(1))) {
                            supportsThreads = true;
                        } else {
                            supportsMultiPv = true;
                        }
                    }
                } catch (NumberFormatException ignored) {
                    // Ignore malformed or out-of-range advertised option limits.
                }
            } else if ("uciok".equals(line)) {
                uciReady = true;
                if (supportsThreads && !sendCommand("setoption name Threads value 1")) {
                    return;
                }
                if (supportsMultiPv && !sendCommand("setoption name MultiPV value 1")) {
                    return;
                }
                if (sendCommand("ucinewgame")) {
                    sendCommand("isready");
                }
            }
            return;
        }
        if ("readyok".equals(line) && !engineReady) {
            engineReady = true;
            sendSearch();
        } else if (search != null && search.sent) {
            if (line.startsWith("info ") && !search.cancelled) {
                parseInfo(line);
            } else if (line.startsWith("bestmove ")) {
                parseBestMove(line);
            }
        }
    }

    private void sendSearch() {
        if (search == null || search.sent) {
            return;
        }
        cancelTimeout();
        search.sent = true;
        if (!sendCommand("position fen " + search.fen) || !sendCommand(search.command)) {
            return;
        }
        if (search.timeMillis > 0) {
            // The engine enforces movetime. Also stop an engine that overruns its
            // budget, then terminate it if it fails to acknowledge that stop.
            scheduleTimeout(() -> stop(null), (long) search.timeMillis + 1000);
        }
    }

    private void parseInfo(String line) {
        try {
            Matcher cpMatch = SCORE_CP_PATTERN.matcher(line);
            if (cpMatch.matches()) {
                latestValue = Integer.parseInt(cpMatch.group(1));
            } else {
                Matcher mateMatch = SCORE_MATE_PATTERN.matcher(line);
                if (mateMatch.matches()) {
                    int mateIn = Integer.parseInt(mateMatch.group(1));
                    int distance = (int) Math.min(Math.abs((long) mateIn), BoardConstants.VALUATION_MATE - 1);
                    latestValue = (mateIn >= 0 ? 1 : -1) * (BoardConstants.VALUATION_MATE - distance);
                }
            }
            sendMessageFromThread(search.id, line, (float) latestValue / 100.0F);
        } catch (NumberFormatException ex) {
            Log.w(TAG, "Ignoring malformed engine score: " + line);
        }
    }

    private void parseBestMove(String line) {
        Search completed = search;
        int value = latestValue;
        if (!completed.cancelled && completed.mode == Mode.PLAY) {
            String[] parts = line.split("\\s+");
            String uciMove = parts.length > 1 ? parts[1] : "";
            // Resolve against the live board on the main thread, only if it still
            // matches this PLAY search. ANALYSIS never touches JNI move resolution.
            updateHandler.post(() -> {
                if (!isCurrentSearch(completed.id) || !completed.fen.equals(jni.toFEN())) {
                    return;
                }
                int move = resolveMove(uciMove);
                for (EngineListener listener : listeners) {
                    if (move != 0) {
                        listener.OnEngineMove(move, -1, value);
                    } else {
                        listener.OnEngineError();
                    }
                }
            });
        }
        // In analysis even bestmove 0000/(none) simply completes the search.
        finishSearch();
    }

    private void finishSearch() {
        cancelTimeout();
        search = null;
        for (Runnable callback : completionCallbacks) {
            updateHandler.post(callback);
        }
        completionCallbacks.clear();
    }

    private void failSearch(String reason) {
        Log.w(TAG, reason);
        closeProcess();
        sendErrorMessageFromThread();
        finishSearch();
    }

    private void scheduleTimeout(Runnable action, long delayMillis) {
        cancelTimeout();
        timeout = new Runnable() {
            @Override
            public void run() {
                synchronized (OexEngine.this) {
                    if (timeout != this) {
                        return;
                    }
                    timeout = null;
                    action.run();
                }
            }
        };
        updateHandler.postDelayed(timeout, delayMillis);
    }

    private void cancelTimeout() {
        if (timeout != null) {
            updateHandler.removeCallbacks(timeout);
            timeout = null;
        }
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

    private boolean sendCommand(String command) {
        if (writer == null) {
            return false;
        }
        writer.println(command);
        writer.flush();
        if (writer.checkError()) {
            failSearch("Failed to send engine command");
            return false;
        }
        return true;
    }
}
