package jwtc.android.chess.engine;

import android.util.Log;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.io.PrintWriter;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import jwtc.chess.JNI;
import jwtc.chess.Move;
import jwtc.chess.Pos;

public class UCIEngine extends EngineApi {
    private static final String TAG = "UCIEngine";

    private BufferedReader _reader, _readerError;
    private PrintWriter _writer;
    private Process _process;
    private String databaseName;
    private final Pattern _pattMove = Pattern.compile("([a-h]{1}[1-8]{1})([a-h]{1}[1-8]{1})(q|r|b|n)?");


    @Override
    public void play() {
        if (databaseName != null) {
            sendCommand("setoption name Book File value /data/data/jwtc.android.chess/" + databaseName);
        }

        sendCommand("ucinewgame");
        sendCommand("position fen " + JNI.getInstance().toFEN());

        if (msecs > 0) {
            sendCommand("go movetime " + msecs);
        } else {
            sendCommand("go depth " + ply);
        }
    }

    @Override
    public void destroy() {
        sendCommand("quit");
        if (_process != null) {
            _process.destroy();
            _process = null;
        }
    }

    @Override
    public void abort() {
        sendCommand("stop");
    }

    @Override
    public boolean isReady() {
        return (_process != null);
    }

    public void initDatabase(String databaseName) {
        this.databaseName = databaseName;
    }

    public boolean initEngine(String sEnginePath) {


        try {
            Log.i(TAG, "intitializing " + sEnginePath);
            // Executes the command.
            List<String> args = new ArrayList<String>();
            args.add(sEnginePath);
            ProcessBuilder pb = new ProcessBuilder(args);
            pb.directory(new File("/data/data/jwtc.android.chess/"));
            pb.redirectErrorStream(true);
            _process = pb.start();

//            _readerError = new BufferedReader(new InputStreamReader(_process.getErrorStream()));
            _writer = new PrintWriter(new OutputStreamWriter(_process.getOutputStream()));

            sendCommand("uci");
            // Reads stdout.
            _reader = new BufferedReader(new InputStreamReader(_process.getInputStream()));

            new Thread(new Runnable() {
                public void run() {

                    Matcher match;
                    String tmp;
                    int from, to, iPos, iLine = 0;
                    try {
                        int i = 0;

                        while (true) {
                            tmp = _reader.readLine();

                            Log.i(TAG, iLine + ">>" + tmp);
                            if (tmp == null) {
                                sendErrorMessageFromThread();
                                break;
                            }

                            // parse response
                            iPos = tmp.indexOf("info");

                            if (iPos >= 0) {
                                if (i % 10 == 0) {
                                    Log.i(TAG, tmp);
                                    String[] arr = tmp.split(" ");
                                    tmp = "";
                                    for (int j = 0; j < arr.length; j++) {

                                        if (arr[j].equals("depth")) {
                                            tmp += arr[j] + " " + arr[j + 1] + "\t";
                                        } else if (arr[j].equals("nodes")) {
                                            tmp += arr[j] + " " + arr[j + 1] + "\t";
                                        } else if (arr[j].equals("nps")) {
                                            tmp += arr[j] + " " + arr[j + 1] + "\t";
                                        }
                                        if (arr[j].equals("depth")) {

                                            //TODO
                                        }

                                    }
                                    if (tmp.length() > 0) {
                                        sendMessageFromThread(tmp);
                                    }
                                }
                                i++;
                            } else {
                                iPos = tmp.indexOf("bestmove");
                                //Log.i(TAG, "bestmove = " + iPos);
                                if (iPos >= 0) {
                                    //
                                    tmp = tmp.substring(iPos + 9);
                                    //Log.i(TAG, tmp);
                                    iPos = tmp.indexOf(" ");
                                    if (iPos > 0) {
                                        tmp = tmp.substring(0, iPos);
                                    }
                                    Log.i(TAG, tmp);
                                    match = _pattMove.matcher(tmp);
                                    if (match.matches()) {

                                        from = Pos.fromString(match.group(1));
                                        to = Pos.fromString(match.group(2));
                                        Log.i(TAG, match.group(1) + "-" + match.group(2) + " move " + from + ", " + to);

                                        int move = Move.makeMove(from, to);
                                        sendMoveMessageFromThread(move);

                                        i = 0;
                                    }
                                }
                            }

                            iLine++;
                        }
                    } catch (Exception ex) {
                        sendErrorMessageFromThread();

                        if (_process != null) {
                            _process.destroy();
                            _process = null;
                        }
                        Log.e(TAG, "run error: " + ex);
                    }
                }
            }).start();

//            new Thread(new Runnable() {
//                public void run() {
//                    try {
//                        String s = _readerError.readLine();
//                        Log.d(TAG, "stderr " + s);
//                        sendErrorMessageFromThread();
//                    } catch (Exception ex) {
//                        sendErrorMessageFromThread();
//                    }
//                }
//            }).start();

            return true;

        } catch (Exception e) {
            Log.e(TAG, "init error: " + e);
            return false;
        }
    }

    protected void sendCommand(final String cmd) {
        if (_process != null) {
            _writer.println(cmd);
            _writer.flush();
            Log.i(TAG, "sendCommand: " + cmd);
        }
    }

    public static void install(final InputStream in, final String sEngine) {
        new Thread(new Runnable() {
            public void run() {
            try {
                OutputStream out = new FileOutputStream("/data/data/jwtc.android.chess/" + sEngine);
                byte[] buf = new byte[1024];
                int len;
                while ((len = in.read(buf)) > 0) {
                    out.write(buf, 0, len);
                }

                out.close();
                in.close();

                List<String> args = new ArrayList<String>();
                args.add("chmod");
                args.add("744");
                args.add(sEngine);
                runConsole(args, "/data/data/jwtc.android.chess/");

                Log.i(TAG, "install completed");
            } catch (Exception ex) {

                Log.e(TAG, "install error: " + ex.toString());
            }
            }
        }).start();
    }

    public static void installDb(final InputStream in, final String sDatabase) {
        new Thread(new Runnable() {
            public void run() {
            // TODO
            try {
                OutputStream out = new FileOutputStream("/data/data/jwtc.android.chess/" + sDatabase);
                byte[] buf = new byte[1024];
                int len;
                while ((len = in.read(buf)) > 0) {
                    out.write(buf, 0, len);
                }

                out.close();
                in.close();

                List<String> args = new ArrayList<String>();
                args.add("chmod");
                args.add("744");
                args.add(sDatabase);
                runConsole(args, "/data/data/jwtc.android.chess/");
                args.clear();
                args.add("ls");
                args.add("-la");
                runConsole(args, "/data/data/jwtc.android.chess/");

            } catch (Exception ex) {

                Log.e(TAG, "install error: " + ex.toString());
            }
            }
        }).start();
    }

    public static void runConsole(final List<String> args, final String workingDir) {
        Log.d(TAG, "runConsole");
        try {

            ProcessBuilder pb = new ProcessBuilder(args);
            pb.directory(new File(workingDir));
            pb.redirectErrorStream(true);
            Process process = pb.start();

            String result = "";
            InputStream in = process.getInputStream();
            byte[] re = new byte[1024];
            while (in.read(re) != -1) {
                result = result + new String(re);
            }
            Log.d(TAG, result);
//            Process process = Runtime.getRuntime().exec("sh");
//            final BufferedReader readerStdout = new BufferedReader(new InputStreamReader(process.getInputStream()));
//            final BufferedReader readerStderr = new BufferedReader(new InputStreamReader(process.getErrorStream()));
//            final PrintWriter processWriter = new PrintWriter(new OutputStreamWriter(process.getOutputStream()));
//
//            new Thread(new Runnable() {
//                public void run() {
//                    try {
//                        processWriter.println(sCmd);
//                        processWriter.flush();
//                        String sOut = "";
//                        while(true) {
//                            String tmp = readerStdout.readLine();
//                            if (tmp == null) {
//                                break;
//                            }
//                            sOut += tmp;
//                        }
//
//                        Log.d(TAG, "=> "  + sOut);
//                        processWriter.println("exit");
//                        processWriter.flush();
//
//                    } catch (Exception e) {
//                        Log.d(TAG, "out thread" + e.getMessage());
//                    }
//                }
//            }).start();
//
//            new Thread(new Runnable() {
//                public void run() {
//                    try {
//                        String sOut = "";
//                        while(true) {
//                            String tmp = readerStderr.readLine();
//                            if (tmp == null) {
//                                break;
//                            }
//                            sOut += tmp;
//                        }
//
//                        Log.d(TAG, "!! "  + sOut);
//
//                    } catch (Exception e) {
//                        Log.d(TAG, e.getMessage());
//                    }
//                }
//            }).start();
//
//            // Waits for the command to finish.
//            Log.d(TAG, "waiting...");
//            process.waitFor();
//            readerStdout.close();
//            readerStderr.close();
//            Log.d(TAG, "runConsole done...");

        } catch (Exception e) {
            Log.d(TAG, "Outer error " + e.getMessage());
        }

    }
}
