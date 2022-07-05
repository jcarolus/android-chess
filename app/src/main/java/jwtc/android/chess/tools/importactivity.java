package jwtc.android.chess.tools;

import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Date;
import java.util.Iterator;
import java.util.TreeSet;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import androidx.appcompat.app.AppCompatActivity;
import jwtc.android.chess.R;
import jwtc.android.chess.puzzle.MyPuzzleProvider;
import jwtc.chess.GameControl;
import jwtc.chess.JNI;
import jwtc.chess.PGNColumns;
import jwtc.chess.PGNEntry;
import jwtc.chess.algorithm.UCIWrapper;
import jwtc.chess.board.ChessBoard;

import android.app.AlertDialog;
import android.content.ContentResolver;
import android.content.ContentValues;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.SharedPreferences;
import android.database.Cursor;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.Message;
import android.text.ClipboardManager;
import android.util.Log;
import android.view.Gravity;
import android.view.MenuItem;
import android.view.View;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

public class importactivity extends AppCompatActivity {

    private int _cnt, _untilPly, _cntFail;
    private GameControl _gameControl;
    private JNI _jni;
    private TextView _tvWork, _tvWorkCnt, _tvWorkCntFail;
    private ProgressBar _progress;
    private PGNProcessor _processor;
    private int _mode = 0;

    protected TreeSet<Long> _arrKeys;
    protected String _outFile;
    protected boolean _processing;

    private final String TAG = "importactivity";

    /**
     * Called when the activity is first created.
     */
    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        setContentView(R.layout.doimport);

        _processing = false;
        _gameControl = new GameControl();

        _jni = _gameControl.getJNI();
        _untilPly = 17;

        _arrKeys = new TreeSet<Long>();

        _tvWork = (TextView) findViewById(R.id.TextViewDoImport);
        _tvWorkCnt = (TextView) findViewById(R.id.TextViewDoImportCnt);
        _tvWorkCntFail = (TextView) findViewById(R.id.TextViewDoImportCntFail);
        _progress = (ProgressBar) findViewById(R.id.ProgressDoImport);

        _progress.setVisibility(View.INVISIBLE);
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        // Handle presses on the action bar items
        Intent intent;
        switch (item.getItemId()) {
            case android.R.id.home:
                // API 5+ solution
                onBackPressed();
                return true;
            default:
                return super.onOptionsItemSelected(item);
        }
    }

    @Override
    protected void onPause() {
        super.onPause();

    }

    @Override
    protected void onResume() {
        super.onResume();

        if (_processing == false) {
            final Intent intent = getIntent();
            final Uri uri = intent.getData();

            Bundle extras = intent.getExtras();
            if (extras != null) {
                int type = extras.getInt(pgntool.EXTRA_MODE);
                if (type != 0) {
                    _mode = type;
                }
            }
            if (_mode == 0) {
                if (uri == null) {
                    finish();
                    return;
                }
                _mode = pgntool.MODE_IMPORT; // by default
            }
            _cnt = 0;
            _cntFail = 0;


            if (_mode == pgntool.MODE_IMPORT) {
                // @TODO
//                _processor = new PGNImportProcessor();
//                _processor.m_threadUpdateHandler = new Handler() {
//                    /** Gets called on every message that is received */
//                    // @Override
//                    public void handleMessage(Message msg) {
//
//                        if (msg.what == PGNProcessor.MSG_PROCESSED_PGN) {
//                            _cnt++;
//                            _tvWorkCnt.setText("Processed " + _cnt);
//                        } else if (msg.what == PGNProcessor.MSG_FAILED_PGN) {
//                            _cntFail++;
//                            _tvWorkCntFail.setText("Failed " + _cntFail);
//                        } else if (msg.what == PGNProcessor.MSG_FINISHED) {
//                            _progress.setVisibility(View.INVISIBLE);
//                            _tvWorkCnt.setText("Imported " + _cnt + " games");
//                        } else if (msg.what == PGNProcessor.MSG_FATAL_ERROR) {
//                            _progress.setVisibility(View.INVISIBLE);
//                            _tvWorkCnt.setText("An error occured, import failed");
//                        }
//                    }
//                };
            } else if (_mode == pgntool.MODE_DB_IMPORT) {

                // @TODO
//                _arrKeys.clear();
//
//                _processor = new PGNDbProcessor();
//                _processor.m_threadUpdateHandler = new Handler() {
//                    /** Gets called on every message that is received */
//                    // @Override
//                    public void handleMessage(Message msg) {
//
//                        if (msg.what == PGNProcessor.MSG_PROCESSED_PGN) {
//                            _cnt++;
//                            _tvWorkCnt.setText("Processed " + _cnt);
//                        } else if (msg.what == PGNProcessor.MSG_FAILED_PGN) {
//                            _cntFail++;
//                            _tvWorkCntFail.setText("Failed " + _cntFail);
//                        } else if (msg.what == PGNProcessor.MSG_FINISHED) {
//                            writeHashKeysToFile();
//                            _progress.setVisibility(View.INVISIBLE);
//                            _tvWorkCnt.setText("Imported " + _cnt + " games; " + _arrKeys.size() + " positions.");
//                            _processing = false;
//                        } else if (msg.what == PGNProcessor.MSG_FATAL_ERROR) {
//                            _progress.setVisibility(View.INVISIBLE);
//                            _tvWorkCnt.setText("An error occured, import failed");
//                            _processing = false;
//                        }
//                    }
//                };

            } else if (_mode == pgntool.MODE_CREATE_PRACTICE) {

                // @TODO
//                _arrKeys.clear();
//
//                Log.i(TAG, "Create practice set");
//
//                getContentResolver().delete(MyPuzzleProvider.CONTENT_URI_PRACTICES, "1=1", null);
//                Log.i(TAG, "Deleted practices");
//
//                _processor = new PracticeImportProcessor();
//                _processor.m_threadUpdateHandler = new Handler() {
//                    /** Gets called on every message that is received */
//                    // @Override
//                    public void handleMessage(Message msg) {
//
//                        if (msg.what == PGNProcessor.MSG_PROCESSED_PGN) {
//                            _cnt++;
//                            _tvWorkCnt.setText("Processed " + _cnt);
//                        } else if (msg.what == PGNProcessor.MSG_FAILED_PGN) {
//                            _cntFail++;
//                            _tvWorkCntFail.setText("Failed " + _cntFail);
//                        } else if (msg.what == PGNProcessor.MSG_FINISHED) {
//
//                            _progress.setVisibility(View.INVISIBLE);
//                            _tvWorkCnt.setText("Imported " + _cnt + " practice positions");
//                            _processing = false;
//                        } else if (msg.what == PGNProcessor.MSG_FATAL_ERROR) {
//                            _progress.setVisibility(View.INVISIBLE);
//                            _tvWorkCnt.setText("An error occured, import failed");
//                            _processing = false;
//                        }
//                    }
//                };

            } else if (_mode == pgntool.MODE_DB_POINT) {


                try {

                    if (uri != null) {

                        SharedPreferences.Editor editor = getSharedPreferences("ChessPlayer", MODE_PRIVATE).edit();

                        editor.putString("OpeningDb", uri.toString());

                        editor.commit();


                        doToast("Openingdatabase: " + uri.toString());
                        //String outFilename = "/data/data/" + getPackageName() + "/db.bin";
                        //_gameControl.loadDB(getAssets().open("db.bin"), outFilename, 17);

                    }
                } catch (Exception e) {

                }

                finish();
                return;
            } else if (_mode == pgntool.MODE_UCI_INSTALL) {

                String sPath = uri.getPath();
                String sEngine = uri.getLastPathSegment(); //"robbolito-android"; //bikjump2.1    stockfish2.0

                Log.i(TAG, "Install UCI " + sPath + " as " + sEngine);

                try {
                    FileInputStream fis = new FileInputStream(sPath);
                    UCIWrapper.install(fis, sEngine);

                    SharedPreferences.Editor editor = getSharedPreferences("ChessPlayer", MODE_PRIVATE).edit();
                    editor.putString("UCIEngine", sEngine);
                    editor.commit();

                    doToast(String.format(getString(R.string.pgntool_uci_engine_success), sEngine));

                } catch (IOException e) {

                    doToast(getString(R.string.pgntool_uci_engine_error));
                    // TODO Auto-generated catch block
                    e.printStackTrace();
                }


                finish();
                return;
            } else if (_mode == pgntool.MODE_UCI_DB_INSTALL) {

                String sPath = uri.getPath();
                String sDatabase = uri.getLastPathSegment(); //"goi.bin"

                Log.i(TAG, "Install UCI database " + sPath + " as " + sDatabase);

                try {
                    FileInputStream fis = new FileInputStream(sPath);
                    UCIWrapper.installDb(fis, sDatabase);

                    SharedPreferences.Editor editor = getSharedPreferences("ChessPlayer", MODE_PRIVATE).edit();
                    editor.putString("UCIDatabase", sDatabase);
                    editor.commit();

                    doToast(String.format(getString(R.string.pgntool_uci_engine_success), sDatabase));

                } catch (IOException e) {

                    doToast(getString(R.string.pgntool_uci_engine_error));
                    // TODO Auto-generated catch block
                    e.printStackTrace();
                }


                finish();
                return;
            } else if (_mode == pgntool.MODE_IMPORT_PRACTICE) {

                String sPath = uri.getPath();
                Log.i(TAG, "Import practice " + sPath);

                try {
                    SharedPreferences prefs = getSharedPreferences("ChessPlayer", MODE_PRIVATE);
                    SharedPreferences.Editor editor = prefs.edit();
                    editor.putInt("practicePos", 0);
                    editor.putInt("practiceTicks", 0);
                    editor.commit();

                    getContentResolver().delete(MyPuzzleProvider.CONTENT_URI_PRACTICES, "1=1", null);

                    Log.i(TAG, "Deleted practices");

                } catch (Exception e) {

                    doToast("An error occured, could not copy practice set");
                    // TODO Auto-generated catch block
                    e.printStackTrace();
                }

                finish();
                return;
            } else if (_mode == pgntool.MODE_IMPORT_PUZZLE) {

                SharedPreferences prefs = getSharedPreferences("ChessPlayer", MODE_PRIVATE);
                SharedPreferences.Editor editor = prefs.edit();
                editor.putInt("puzzlePos", 0);
                editor.commit();

                // @TODO
//                _processor = new PuzzleImportProcessor();
//                _processor.m_threadUpdateHandler = new Handler() {
//                    /** Gets called on every message that is received */
//                    // @Override
//                    public void handleMessage(Message msg) {
//
//                        if (msg.what == PGNProcessor.MSG_PROCESSED_PGN) {
//                            _cnt++;
//                            _tvWorkCnt.setText("Processed " + _cnt);
//                        } else if (msg.what == PGNProcessor.MSG_FAILED_PGN) {
//                            _cntFail++;
//                            _tvWorkCntFail.setText("Failed " + _cntFail);
//                        } else if (msg.what == PGNProcessor.MSG_FINISHED) {
//
//                            _progress.setVisibility(View.INVISIBLE);
//                            _tvWorkCnt.setText("Imported " + _cnt + " puzzle positions");
//                            _processing = false;
//                        } else if (msg.what == PGNProcessor.MSG_FATAL_ERROR) {
//                            _progress.setVisibility(View.INVISIBLE);
//                            _tvWorkCnt.setText("An error occured, import failed");
//                            _processing = false;
//                        }
//                    }
//                };
            } else if (_mode == pgntool.MODE_IMPORT_OPENINGDATABASE) {
                /// @TODO
//                _processor = new OpeningImportProcessor();
//                _processor.m_threadUpdateHandler = new Handler() {
//                    /** Gets called on every message that is received */
//                    // @Override
//                    public void handleMessage(Message msg) {
//
//                        if (msg.what == PGNProcessor.MSG_PROCESSED_PGN) {
//                            _cnt++;
//                            _tvWorkCnt.setText("Processed " + _cnt);
//                        } else if (msg.what == PGNProcessor.MSG_FAILED_PGN) {
//                            _cntFail++;
//                            _tvWorkCntFail.setText("Failed " + _cntFail);
//                        } else if (msg.what == PGNProcessor.MSG_FINISHED) {
//
//                            _progress.setVisibility(View.INVISIBLE);
//                            _tvWorkCnt.setText("Imported " + _cnt + " openings");
//                            _processing = false;
//
//                            ClipboardManager cm = (ClipboardManager) getSystemService(CLIPBOARD_SERVICE);
//                            cm.setText(_processor.getString());
//                            //Log.i("DONE_OPENING", _processor.getString());
//
//                        } else if (msg.what == PGNProcessor.MSG_FATAL_ERROR) {
//                            _progress.setVisibility(View.INVISIBLE);
//                            _tvWorkCnt.setText("An error occured, import failed");
//                            _processing = false;
//                        }
//                    }
//                };
            } else {

                finish();
                return;
            }

            if (uri != null) {

                AlertDialog.Builder builder = new AlertDialog.Builder(this);
                builder.setTitle("Import " + uri.getLastPathSegment() + "?");

                builder.setPositiveButton(getString(R.string.alert_yes), new DialogInterface.OnClickListener() {

                    public void onClick(DialogInterface dialog, int which) {

                        dialog.dismiss();

                        _progress.setVisibility(View.VISIBLE);
                        _tvWork.setText("Importing " + uri.toString());

                        try {

                            InputStream is = getContentResolver().openInputStream(uri);

                            if (uri.getPath().lastIndexOf(".zip") > 0) {

                                _outFile = uri.getPath().replace(".zip", ".bin");
                                _processor.processZipFile(is);
                            } else {
                                _outFile = uri.getPath().replace(".pgn", ".bin");
                                _processor.processPGNFile(is);
                            }

                            _processing = true;


                        } catch (Exception ex) {

                            Log.e("import", ex.toString());
                        }

                    }
                });

                builder.setNegativeButton(getString(R.string.alert_no), new DialogInterface.OnClickListener() {
                    public void onClick(DialogInterface dialog, int which) {
                        dialog.dismiss();
                        finish();
                    }
                });

                AlertDialog alert = builder.create();
                alert.show();
            }
        }
    }

    public void doToast(String s) {
        Toast t = Toast.makeText(this, s, Toast.LENGTH_LONG);
        t.setGravity(Gravity.CENTER, 0, 0);
        t.show();
    }

    public void readDB(InputStream isDB) {
        Log.i("import", "readDB executing");
        _arrKeys.clear();
        long l;
        int len;
        byte[] bytes = new byte[8];
        try {
            while ((len = isDB.read(bytes, 0, bytes.length)) != -1) {
                l = 0L;
                l |= (long) bytes[0] << 56;
                l |= (long) bytes[1] << 48;
                l |= (long) bytes[2] << 40;
                l |= (long) bytes[3] << 32;
                l |= (long) bytes[4] << 24;
                l |= (long) bytes[5] << 16;
                l |= (long) bytes[6] << 8;
                l |= (long) bytes[7];

                // assume file keys are allready unique

                _arrKeys.add(l);
            }
        } catch (IOException e) {
            Log.e("import", "readDB: " + e.toString());
            // TODO Auto-generated catch block
            e.printStackTrace();
        }

    }

    public void writeHashKeysToFile() {
        FileOutputStream fos;
        try {
            fos = new FileOutputStream(_outFile);
            long l;
            byte[] bytes = new byte[8];
			
			/*
			Collections.sort(_arrKeys,  new Comparator<Long>() {
		        public int compare(Long arg0, Long arg1) {
		        	long x = (long) arg0;
			    	long y = (long) arg1;
			    	if(x > y) {
			    		return 1;
			    	} else if(x == y) {
			    		return 0;
			    	} else {
			    		return -1;
			    	}
		        	
		        }
		    });
			*/

            Iterator<Long> it = _arrKeys.iterator();
            while (it.hasNext()) {

                l = it.next();

                if (l == 0)
                    break;

                bytes[0] = (byte) (l >>> 56);
                bytes[1] = (byte) (l >>> 48);
                bytes[2] = (byte) (l >>> 40);
                bytes[3] = (byte) (l >>> 32);
                bytes[4] = (byte) (l >>> 24);
                bytes[5] = (byte) (l >>> 16);
                bytes[6] = (byte) (l >>> 8);
                bytes[7] = (byte) (l);

                fos.write(bytes);

                //co.pl("" + l + "{" + bytes[0] + ", " + bytes[1] + ", " + bytes[2] + ", " + bytes[3] + ", " + bytes[4] + ", " + bytes[5] + ", " + bytes[6] + ", " + bytes[7] + "}");

                //Log.i("writeHashKeys", "long " + l);
                //break;
            }

            fos.flush();
            fos.close();
            Log.i("import", "wrote hash keys to " + _outFile);

        } catch (Exception e) {
            // TODO Auto-generated catch block
            Log.e("import", "writeHashkeys: " + e.toString());
            e.printStackTrace();
        }
    }
}