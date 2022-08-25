package jwtc.android.chess.tools;

import java.io.File;

import jwtc.android.chess.activities.BaseActivity;
import jwtc.android.chess.helpers.MyPGNProvider;
import jwtc.android.chess.R;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.SharedPreferences;
import android.net.Uri;
import android.os.Bundle;

import android.util.Log;
import android.view.*;
import android.widget.AdapterView;
import android.widget.ListView;
import android.widget.Toast;
import android.widget.AdapterView.OnItemClickListener;

public class AdvancedActivity extends BaseActivity {

    public static final String TAG = "AdvancedActivity";
    protected static final int MY_PERMISSIONS_REQUEST_WRITE_EXTERNAL_STORAGE = 1;
    protected static final int REQUEST_CREATE_PGN_FILE = 0;
    private ListView _lvStart;

    /**
     * Called when the activity is first created.
     */
    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        setContentView(R.layout.pgntool);

        _lvStart = (ListView) findViewById(R.id.ListPgn);

        final CharSequence[] arrString;

        arrString = getResources().getTextArray(R.array.pgn_tool_menu);

        _lvStart.setOnItemClickListener(new OnItemClickListener() {

            @Override
            public void onItemClick(AdapterView<?> arg0, View arg1, int arg2, long arg3) {

                if (arrString[arg2].equals(getString(R.string.pgntool_export_explanation))) {
                    Intent intent = new Intent(Intent.ACTION_CREATE_DOCUMENT);
                    intent.addCategory(Intent.CATEGORY_OPENABLE);
                    intent.setType("application/x-chess-pgn");
                    intent.putExtra(Intent.EXTRA_TITLE, "chess.pgn");

                    startActivityForResult(intent, ImportService.EXPORT_GAME_DATABASE);

                } else if (arrString[arg2].equals(getString(R.string.pgntool_import_explanation))) {
                    Intent i = new Intent(Intent.ACTION_OPEN_DOCUMENT);
                    i.addCategory(Intent.CATEGORY_OPENABLE);
                    i.setType("*/*");
                    startActivityForResult(i, ImportService.IMPORT_GAMES);
                } else if (arrString[arg2].equals(getString(R.string.pgntool_create_db_explanation))) {
                    Intent i = new Intent(Intent.ACTION_OPEN_DOCUMENT);
                    i.addCategory(Intent.CATEGORY_OPENABLE);
                    i.setType("*/*");
                    startActivityForResult(i, ImportService.IMPORT_DATABASE);

                } else if (arrString[arg2].equals(getString(R.string.pgntool_point_db_explanation))) {
                    Intent i = new Intent(Intent.ACTION_OPEN_DOCUMENT);
                    i.addCategory(Intent.CATEGORY_OPENABLE);
                    i.setType("*/*");
                    startActivityForResult(i, ImportService.DB_POINT);

                } else if (arrString[arg2].equals(getString(R.string.pgntool_delete_explanation))) {
                    AlertDialog.Builder builder = new AlertDialog.Builder(AdvancedActivity.this);
                    builder.setTitle(getString(R.string.pgntool_confirm_delete));

                    builder.setPositiveButton(getString(R.string.button_ok), new DialogInterface.OnClickListener() {
                        public void onClick(DialogInterface dialog, int which) {
                            dialog.dismiss();
                            AdvancedActivity.this.getContentResolver().delete(MyPGNProvider.CONTENT_URI, "1=1", null);
                            doToast(getString(R.string.pgntool_deleted));
                        }
                    });
                    builder.setNegativeButton(getString(R.string.button_cancel), new DialogInterface.OnClickListener() {
                        public void onClick(DialogInterface dialog, int which) {
                            dialog.dismiss();
                        }
                    });

                    AlertDialog alert = builder.create();
                    alert.show();

                } else if (arrString[arg2].equals(getString(R.string.pgntool_help))) {
                    showHelp(R.string.advanced_help);
                } else if (arrString[arg2].equals(getString(R.string.pgntool_unset_uci_engine))) {
                    SharedPreferences prefs = getSharedPreferences("ChessPlayer", MODE_PRIVATE);
                    String sEngine = prefs.getString("UCIEngine", null);
                    if (sEngine != null) {
                        File f = new File("/data/data/jwtc.android.chess/" + sEngine);
                        if (f.delete()) {
                            Log.i("engines", sEngine + " deleted");
                        } else {
                            Log.w("engines", sEngine + " NOT deleted");
                        }
                        SharedPreferences.Editor editor = prefs.edit();
                        editor.putString("UCIEngine", null);
                        editor.commit();
                        doToast("Engine " + sEngine + " uninstalled");
                    } else {
                        doToast("No engine installed");
                    }
                } else if (arrString[arg2].equals(getString(R.string.pgntool_unset_uci_database))) {
                    SharedPreferences prefs = getSharedPreferences("ChessPlayer", MODE_PRIVATE);
                    String sDatabase = prefs.getString("UCIDatabase", null);
                    if (sDatabase != null) {
                        File f = new File("/data/data/jwtc.android.chess/" + sDatabase);
                        if (f.delete()) {
                            Log.i("database", sDatabase + " deleted");
                        } else {
                            Log.w("database", sDatabase + " NOT deleted");
                        }
                        SharedPreferences.Editor editor = prefs.edit();
                        editor.putString("UCIDatabase", null);
                        editor.commit();
                        doToast("Database " + sDatabase + " uninstalled");
                    } else {
                        doToast("No database installed");
                    }
                } else if (arrString[arg2].equals(getString(R.string.pgntool_reset_practice))) {

                    AlertDialog.Builder builder = new AlertDialog.Builder(AdvancedActivity.this);
                    builder.setTitle(getString(R.string.pgntool_confirm_practice_reset));

                    builder.setPositiveButton(getString(R.string.button_ok), new DialogInterface.OnClickListener() {
                        public void onClick(DialogInterface dialog, int which) {
                            dialog.dismiss();

                            SharedPreferences prefs = getSharedPreferences("ChessPlayer", MODE_PRIVATE);
                            SharedPreferences.Editor editor = prefs.edit();
                            editor.putInt("practicePos", 0);
                            editor.putInt("practiceTicks", 0);
                            editor.commit();

                            doToast(getString(R.string.practice_set_reset));

                        }
                    });
                    builder.setNegativeButton(getString(R.string.button_cancel), new DialogInterface.OnClickListener() {
                        public void onClick(DialogInterface dialog, int which) {
                            dialog.dismiss();
                        }
                    });

                    AlertDialog alert = builder.create();
                    alert.show();


                } else if (arrString[arg2].equals(getString(R.string.pgntool_import_practice))) {
                    Intent i = new Intent(Intent.ACTION_OPEN_DOCUMENT);
                    i.addCategory(Intent.CATEGORY_OPENABLE);
                    i.setType("*/*");
                    startActivityForResult(i, ImportService.IMPORT_PRACTICE);

                } else if (arrString[arg2].equals(getString(R.string.pgntool_import_puzzle))) {
                    Intent i = new Intent(Intent.ACTION_OPEN_DOCUMENT);
                    i.addCategory(Intent.CATEGORY_OPENABLE);
                    i.setType("*/*");
                    startActivityForResult(i, ImportService.IMPORT_PUZZLES);

                } else if (arrString[arg2].equals(getString(R.string.pgntool_import_opening))) {
//                    Intent i = new Intent(Intent.ACTION_OPEN_DOCUMENT);
//                    i.addCategory(Intent.CATEGORY_OPENABLE);
//                    i.setType("*/*");
//                    startActivityForResult(i, ImportService.);
                }
                //
            }
        });
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        // Handle presses on the action bar items
        switch (item.getItemId()) {
            case android.R.id.home:
                // API 5+ solution
                onBackPressed();
                return true;
        }
        return super.onOptionsItemSelected(item);
    }

    @Override
    public void onActivityResult(int requestCode, int resultCode, Intent resultData) {
        Log.d(TAG, "result" + requestCode + "  " + resultCode);
        if (resultCode == Activity.RESULT_OK) {
            Uri uri = null;
            if (resultData != null) {
                uri = resultData.getData();

                if (uri != null) {
                    Intent myIntent = new Intent();
                    myIntent.putExtra("mode", requestCode);
                    myIntent.setClass(AdvancedActivity.this, ImportActivity.class);
                    myIntent.setData(uri);

                    startActivity(myIntent);
                    finish();
                }
            }
            Log.d(TAG, "res url" + uri);
        }
    }

    public void doToast(String s) {
        Toast t = Toast.makeText(this, s, Toast.LENGTH_LONG);
        t.setGravity(Gravity.CENTER, 0, 0);
        t.show();
    }


}