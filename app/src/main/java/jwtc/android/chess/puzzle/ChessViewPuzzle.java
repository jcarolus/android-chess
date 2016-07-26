package jwtc.android.chess.puzzle;

import jwtc.android.chess.*;

import java.io.InputStream;

import jwtc.chess.*;
import jwtc.chess.board.BoardConstants;
import jwtc.chess.board.BoardMembers;

import android.app.Activity;
import android.app.AlertDialog;
import android.app.ProgressDialog;
import android.os.Handler;
import android.os.Message;
import android.text.InputType;
import android.util.Log;
import android.view.View;
import android.view.View.*;
import android.content.ContentResolver;
import android.content.ContentValues;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.SharedPreferences;
import android.database.Cursor;
import android.widget.*;
import android.widget.SeekBar.OnSeekBarChangeListener;

/**
 *
 */
public class ChessViewPuzzle extends UI {
    private ChessViewBase _view;
    private TextView _tvPuzzleText;
    private Button _butPuzzle, _butJump;
    private ImageView _imgTurn;
    private ImageButton _butPrev, _butNext, _butHelp;
    private ImageView _imgStatus;
    private puzzle _parent;
    private int _iPos;

    private Cursor _cursor = null;
    private int _cnt, _num;
    private SeekBar _seekBar;

    protected ContentResolver _cr;

    protected ProgressDialog _progressDlg;

    protected Thread _thread;
    protected Handler m_threadHandler = new Handler() {
        /** Gets called on every message that is received */
        // @Override
        public void handleMessage(Message msg) {
            if (msg.what == 1) {
                _progressDlg.hide();

                getNumPuzzles();
                if (_seekBar != null) {
                    _seekBar.setMax(_num);
                }

                play();
            } else if (msg.what == 2) {
                _progressDlg.setMessage(_parent.getString(R.string.msg_progress) + String.format(" %d", (_cnt * 100) / _num) + " %");
            } else if (msg.what == 3) {
                _progressDlg.hide();
                _tvPuzzleText.setText("An error occured during install");
            }
        }

    };


    public void getNumPuzzles() {

        _cursor = _parent.managedQuery(MyPuzzleProvider.CONTENT_URI_PUZZLES, MyPuzzleProvider.COLUMNS, null, null, "");

        if (_cursor != null) {
            _num = _cursor.getCount();
        } else {
            _num = 0;
        }

        //Cursor countCursor = _cr.query(MyPuzzleProvider.CONTENT_URI_PUZZLES, new String[] {"count(*) AS count"}, null, null, null);
        //countCursor.moveToFirst();
        //_num = countCursor.getInt(0);
    }

    public ChessViewPuzzle(final Activity activity) {
        super();
        _parent = (puzzle) activity;
        _view = new ChessViewBase(activity);

        _cr = activity.getContentResolver();

        _tvPuzzleText = (TextView) _parent.findViewById(R.id.TextViewPuzzleText);
        _imgTurn = (ImageView) _parent.findViewById(R.id.ImageTurn);

        _imgStatus = (ImageView) _parent.findViewById(R.id.ImageStatus);

        _cnt = 0;
        //_num = 500;
        getNumPuzzles();

        _seekBar = (SeekBar) _parent.findViewById(R.id.SeekBarPuzzle);
        if (_seekBar != null) {
            _seekBar.setOnSeekBarChangeListener(new OnSeekBarChangeListener() {

                @Override
                public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                    if (fromUser) {
                        _iPos = progress - 1;
                        play();
                    }
                }

                @Override
                public void onStartTrackingTouch(SeekBar seekBar) {
                    // TODO Auto-generated method stub

                }

                @Override
                public void onStopTrackingTouch(SeekBar seekBar) {
                    // TODO Auto-generated method stub

                }

            });

            _seekBar.setMax(_num);
        }

        _iPos = 0;


        OnClickListener ocl = new OnClickListener() {
            public void onClick(View arg0) {
                handleClick(_view.getIndexOfButton(arg0));
            }
        };

        OnLongClickListener olcl = new OnLongClickListener() {
            @Override
            public boolean onLongClick(View view) {
                handleClick(_view.getIndexOfButton(view));
                return true;
            }
        };

        _view.init(ocl, olcl);

        _butPuzzle = (Button) _parent.findViewById(R.id.ButtonPuzzle);

        _butPuzzle.setOnClickListener(new OnClickListener() {
            public void onClick(View arg0) {
                jumptoMove(_jni.getNumBoard());
                updateState();

                //if(_arrPGN.size() == m_game.getBoard().getNumBoard()-1)
                //   _butPuzzle.setText("Next");

            }
        });

        _butJump = (Button) _parent.findViewById(R.id.ButtonPuzzleJump);
        _butJump.setOnClickListener(new OnClickListener() {
            public void onClick(View arg0) {
                AlertDialog.Builder builder = new AlertDialog.Builder(_parent);
                builder.setTitle(_parent.getString(R.string.title_puzzle_jump));
                final EditText input = new EditText(_parent);
                input.setInputType(InputType.TYPE_CLASS_PHONE);
                builder.setView(input);
                builder.setPositiveButton(_parent.getString(R.string.button_ok), new DialogInterface.OnClickListener() {
                    public void onClick(DialogInterface dialog, int whichButton) {
                        try {
                            int num = Integer.parseInt(input.getText().toString());

                            if (num > 0 && num <= _num) {
                                _iPos = num - 1;
                                play();
                                return;
                            }
                        } catch (Exception ex) {

                        }
                        _parent.doToast(_parent.getString(R.string.err_puzzle_jump));
                    }
                });


                AlertDialog alert = builder.create();
                alert.show();
            }
        });

        _butPrev = (ImageButton) _parent.findViewById(R.id.ButtonPuzzlePrevious);
        _butPrev.setOnClickListener(new OnClickListener() {
            public void onClick(View arg0) {
                if (_iPos > 1)
                    _iPos -= 2;
                play();
            }
        });

        _butNext = (ImageButton) _parent.findViewById(R.id.ButtonPuzzleNext);
        _butNext.setOnClickListener(new OnClickListener() {
            public void onClick(View arg0) {
                play();
            }
        });

        _butHelp = (ImageButton) _parent.findViewById(R.id.ButtonPuzzleHelp);
        _butHelp.setOnClickListener(new OnClickListener() {
            public void onClick(View arg0) {
                Intent i = new Intent();
                i.setClass(_parent, HtmlActivity.class);
                i.putExtra(HtmlActivity.HELP_MODE, "help_puzzle");
                _parent.startActivity(i);
            }
        });

    }

    // install
    private void processPGN(String s) {

        if (_cnt % 100 == 0) {
            Message msg = new Message();
            msg.what = 2;
            m_threadHandler.sendMessage(msg);

        }

        ContentValues values;
        values = new ContentValues();
        values.put("PGN", s);
        _cr.insert(MyPuzzleProvider.CONTENT_URI_PUZZLES, values);

        _cnt++;
    }
    ///////////////////////////////////////////////////////////////
/*
    private String formatTime(int sec){
    	
    	return String.format("%d:%02d", (int)(Math.floor(sec/60)), sec % 60);
    } */

    @Override
    public void paintBoard() {


        int[] arrSelPositions;

        int lastMove = _jni.getMyMove();
        if (lastMove != 0) {
            arrSelPositions = new int[3];
            arrSelPositions[0] = m_iFrom;
            arrSelPositions[1] = Move.getTo(lastMove);
            arrSelPositions[2] = Move.getFrom(lastMove);
        } else {
            arrSelPositions = new int[1];
            arrSelPositions[0] = m_iFrom;
        }


        _view.paintBoard(_jni, arrSelPositions, null);

    }

    public int getPlayMode() {
        return HUMAN_HUMAN;
    }

    public void flipBoard() {
        _view.flipBoard();
        updateState();
    }


    @Override
    protected boolean requestMove(int from, int to) {
        if (_arrPGN.size() <= _jni.getNumBoard() - 1) {
            setMessage("(position allready solved)");
            return super.requestMove(from, to);
        }
        int move = _arrPGN.get(_jni.getNumBoard() - 1)._move;
        int theMove = Move.makeMove(from, to);

        if (Move.equalPositions(move, theMove)) {
            jumptoMove(_jni.getNumBoard());

            updateState();

            setMessage("Correct!");
            _imgStatus.setImageResource(R.drawable.indicator_ok);
			/*
			if(_arrPGN.size() == m_game.getBoard().getNumBoard()-1)
				play();
			else {
				jumptoMove(m_game.getBoard().getNumBoard());
				updateState();
			}*/
            _parent.trackEvent(_parent.TAG, "solved");

            return true;
        } else {
            // check for illegal move
            setMessage("Move " + Move.toDbgString(theMove) + (checkIsLegalMove(from, to) ? " is not the expected move" : " is an invalid move"));
            _imgStatus.setImageResource(R.drawable.indicator_error);
            m_iFrom = -1;

        }

        updateState();

        return true;
    }

    @Override
    public void play() {
        m_iFrom = -1;
        Log.i("ChessViewPuzzle", "Numboard = " + _jni.getNumBoard());

        _imgStatus.setImageResource(R.drawable.indicator_none);

        String sPGN;

        _iPos++;
        if (_iPos < 1)
            _iPos = 1;
        if (_iPos > _num) {
            setMessage("You completed all puzzles!!!");
            return;
        }

        if (_seekBar != null) {
            _seekBar.setProgress(_iPos);
        }
        if (_cursor != null) {
            _cursor.moveToPosition(_iPos - 1);
            sPGN = _cursor.getString(_cursor.getColumnIndex(MyPuzzleProvider.COL_PGN));
            Log.i("ChessViewPuzzle", "init: " + sPGN);
            loadPGN(sPGN);

            jumptoMove(0);

            int turn = _jni.getTurn();
            if (turn == BoardConstants.BLACK && false == _view.getFlippedBoard() ||
                    turn == BoardConstants.WHITE && _view.getFlippedBoard())
                _view.flipBoard();

            String sWhite = _mapPGNHead.get("White");
            if (sWhite == null) {
                sWhite = "";
            } else {
                sWhite = sWhite.replace("?", "");
            }
            String sDate = _mapPGNHead.get("Date");
            if (sDate == null) {
                sDate = "";
            } else {
                sDate = sDate.replace("????", "");
                sDate = sDate.replace(".??.??", "");
            }

            if (sWhite.length() > 0 && sDate.length() > 0) {
                sWhite += ", ";
            }

            _tvPuzzleText.setText("# " + _iPos + " - " + sWhite + sDate); // + "\n\n" + _mapPGNHead.get("Event") + ", " + _mapPGNHead.get("Date").replace(".??.??", ""));
            //_tvPuzzle.setText(");

            _imgTurn.setImageResource((turn == BoardConstants.WHITE ? R.drawable.turnwhite : R.drawable.turnblack));

            updateState();

            _parent.trackEvent(_parent.TAG, "play");
        }
    }


    @Override
    public boolean handleClick(int index) {

        final int iTo = _view.getFieldIndex(index);
        if (m_iFrom != -1) {

            if (_jni.pieceAt(BoardConstants.WHITE, m_iFrom) == BoardConstants.PAWN &&
                    BoardMembers.ROW_TURN[BoardConstants.WHITE][m_iFrom] == 6 &&
                    BoardMembers.ROW_TURN[BoardConstants.WHITE][iTo] == 7
                    ||
                    _jni.pieceAt(BoardConstants.BLACK, m_iFrom) == BoardConstants.PAWN &&
                            BoardMembers.ROW_TURN[BoardConstants.BLACK][m_iFrom] == 6 &&
                            BoardMembers.ROW_TURN[BoardConstants.BLACK][iTo] == 7) {

                final String[] items = _parent.getResources().getStringArray(R.array.promotionpieces);

                AlertDialog.Builder builder = new AlertDialog.Builder(_parent);
                builder.setTitle("Pick promotion piece");
                builder.setSingleChoiceItems(items, 0, new DialogInterface.OnClickListener() {
                    public void onClick(DialogInterface dialog, int item) {
                        dialog.dismiss();
                        _jni.setPromo(4 - item);
                        boolean bValid = requestMove(m_iFrom, iTo);
                        m_iFrom = -1;
                        if (false == bValid)
                            paintBoard();
                    }
                });
                AlertDialog alert = builder.create();
                alert.show();
                return true;
            }
        }
        return super.handleClick(iTo);
    }

    @Override
    public void setMessage(String sMsg) {
        _tvPuzzleText.setText(sMsg);
        //_parent.doToast(sMsg);
        //_tvMessage.setText(sMsg);
        //m_textMessage.setText(sMsg);
    }

    @Override
    public void setMessage(int res) {
        _tvPuzzleText.setText(res);
        //_parent.doToast(_parent.getString(res));
    }

    public void OnPause(SharedPreferences.Editor editor) {

        editor.putBoolean("flippedBoard", _view.getFlippedBoard());
        if (_iPos > 0)
            editor.putInt("puzzlePos", _iPos - 1);
    }

    public void OnResume(final SharedPreferences prefs) {
        super.OnResume();

        if (_seekBar != null) {
            if (prefs.getBoolean("PuzzleShowSeekBar", true)) {
                _seekBar.setVisibility(View.VISIBLE);

            } else {
                _seekBar.setVisibility(View.GONE);
            }
        }

        ChessImageView._colorScheme = prefs.getInt("ColorScheme", 0);

        _view.setFlippedBoard(prefs.getBoolean("flippedBoard", false));

        _iPos = prefs.getInt("puzzlePos", 0);

        getNumPuzzles();

        if (_num == 0) {

            _num = 500; // first puzzle set has fixed amount
            _progressDlg = ProgressDialog.show(_parent, _parent.getString(R.string.title_installing), _parent.getString(R.string.msg_wait), false, false);

            //if(iTmp > 0)
            //	_cr.delete(MyPuzzleProvider.CONTENT_URI_PUZZLES, "1=1", null);

            _thread = new Thread(new Runnable() {
                public void run() {

                    try {
                        InputStream is = _parent.getAssets().open("puzzles.pgn");

                        StringBuffer sb = new StringBuffer();
                        String s = "", data;
                        int pos1 = 0, pos2 = 0, len;
                        byte[] buffer = new byte[2048];

                        while ((len = is.read(buffer, 0, buffer.length)) != -1) {
                            data = new String(buffer, 0, len);
                            sb.append(data);
                            pos1 = sb.indexOf("[Event \"");
                            while (pos1 >= 0) {
                                pos2 = sb.indexOf("[Event \"", pos1 + 10);
                                if (pos2 == -1)
                                    break;
                                s = sb.substring(pos1, pos2);

                                processPGN(s);

                                sb.delete(0, pos2);

                                pos1 = sb.indexOf("[Event \"");
                            }
                            //break;

                            //Log.i("run", "left: " + sb);
                            //break;
                        }
                        processPGN(sb.toString());

                        Log.i("run", "Count " + _cnt);

                        Message msg = new Message();
                        msg.what = 1;
                        m_threadHandler.sendMessage(msg);

                        is.close();

                    } catch (Exception ex) {
                        Log.e("Install", ex.toString());
                    }
                }
            });
            _thread.start();

            return;
        }

        play();

    }
}