package jwtc.chess.algorithm;

import java.io.BufferedReader;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.io.PrintWriter;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import android.util.Log;

import jwtc.chess.GameControl;
import jwtc.chess.Pos;

public class UCIWrapper {

    protected GameControl m_control;
    private BufferedReader _reader;//, _readerError;
    private PrintWriter _writer;
    private Process _process;

    private Pattern _pattMove = Pattern.compile("([a-h]{1}[1-8]{1})([a-h]{1}[1-8]{1})(q|r|b|n)?");

    private static final String TAG = "UCIWrapper";

    public UCIWrapper(GameControl gc) {
        m_control = gc;
        _process = null;
    }

    public boolean isReady() {
        return (_process != null);
    }

    public void init(String sEnginePath) {


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
                                        m_control.sendMessageFromThread(tmp);
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
                                        m_control.sendUCIMoveMessageFromThread(from, to, 0);

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

    public void play(int msecs, int ply) {
        sendCommand("ucinewgame");

        sendCommand("position fen " + m_control.getJNI().toFEN());

        if (msecs > 0) {
            sendCommand("go movetime " + msecs);
        } else {
            sendCommand("go depth " + ply);
        }
    }

    public void quit() {
        sendCommand("quit");
        if (_process != null) {
            _process.destroy();
            _process = null;
        }
    }

    public void sendCommand(final String cmd) {
        if (_process != null) {
            _writer.println(cmd);
            _writer.flush();
            Log.i(TAG, "sendCommand: " + cmd);
        }
    }

    public void stop() {
        sendCommand("stop");
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
