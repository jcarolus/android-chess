package jwtc.android.chess.engine;

import android.util.Log;

import java.io.BufferedReader;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.io.PrintWriter;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import jwtc.chess.JNI;
import jwtc.chess.Move;
import jwtc.chess.Pos;

public class UCIEngine extends EngineApi {
    private static final String TAG = "UCIEngine";

    private BufferedReader _reader;//, _readerError;
    private PrintWriter _writer;
    private Process _process;
    private String databaseName;
    private final Pattern _pattMove = Pattern.compile("([a-h]{1}[1-8]{1})([a-h]{1}[1-8]{1})(q|r|b|n)?");


    @Override
    public void play(int msecs, int ply) {
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

    public void initEngine(String sEnginePath) {


        try {
            Log.i(TAG, "intitializing " + sEnginePath);
            // Executes the command.
            _process = Runtime.getRuntime().exec(sEnginePath);

            // Reads stdout.
            _reader = new BufferedReader(new InputStreamReader(_process.getInputStream()));
            //_readerError = new BufferedReader(new InputStreamReader(_process.getErrorStream()));
            _writer = new PrintWriter(new OutputStreamWriter(_process.getOutputStream()));

            new Thread(new Runnable() {
                public void run() {

                    Matcher match;
                    String tmp;
                    int from, to, iPos, iLine = 0;
                    try {

                        //while ((read = _reader.read(buffer)) > 0) {
                        //    output.append(buffer, 0, read);
                        //tmp = output.toString();

                        int i = 0;

                        while (true) {
                            tmp = _reader.readLine();

                            Log.i(TAG, iLine + ">>" + tmp);
                            //m_control.sendMessageFromThread();

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
                        if (_process != null) {
                            _process.destroy();
                            _process = null;
                        }
                        Log.e(TAG, "run error: " + ex);
                    }
                }
            }).start();

            sendCommand("uci");

        } catch (Exception e) {
            Log.e(TAG, "init error: " + e);
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
                // TODO

                try {
                    OutputStream out = new FileOutputStream("/data/data/jwtc.android.chess/" + sEngine);
                    byte[] buf = new byte[1024];
                    int len;
                    while ((len = in.read(buf)) > 0) {
                        out.write(buf, 0, len);
                    }

                    out.close();
                    in.close();

                    runConsole("/system/bin/ls /data/data/jwtc.android.chess/");
                    runConsole("/system/bin/chmod 744 /data/data/jwtc.android.chess/" + sEngine);

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

                    runConsole("/system/bin/chmod 744 /data/data/jwtc.android.chess/" + sDatabase);
                    runConsole("/system/bin/ls /data/data/jwtc.android.chess/");

                    Log.i(TAG, "install completed");
                } catch (Exception ex) {

                    Log.e(TAG, "install error: " + ex.toString());
                }
            }
        }).start();
    }

    public static String runConsole(String sCmd) {
        try {
            // Executes the command.
            Process process = Runtime.getRuntime().exec(sCmd);
            BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream()));
            int read;
            char[] buffer = new char[4096];
            StringBuffer output = new StringBuffer();
            while ((read = reader.read(buffer)) > 0) {
                output.append(buffer, 0, read);
            }
            reader.close();

            // Waits for the command to finish.
            process.waitFor();

            return output.toString();

        } catch (Exception e) {
            return null;
        }
    }
}
