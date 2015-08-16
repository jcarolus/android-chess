package jwtc.android.chess.ics;

import java.util.StringTokenizer;
import java.util.Timer;
import java.util.TimerTask;

import jwtc.chess.*;
import jwtc.chess.board.BoardConstants;
import jwtc.chess.board.BoardMembers;

import jwtc.android.chess.*;
import jwtc.chess.board.ChessBoard;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.DialogInterface;
import android.os.Bundle;
import android.os.Handler;
import android.os.Message;
import android.util.Log;

import android.view.View;
import android.view.View.*;
import android.widget.*;

/**
 *
 */
public class ICSChessView extends ChessViewBase {

    public static final String TAG = "ICSChessView";

    private JNI _jni;
    //private Button _butAction;
    private TextView _tvPlayerTop, _tvPlayerBottom, _tvClockTop, _tvClockBottom, _tvBoardNum, _tvLastMove;

    //private EditText _editChat;
    private Button _butConfirmMove, _butCancelMove;
    private ViewSwitcher _viewSwitchConfirm;
    private String _opponent, _whitePlayer, _blackPlayer;
    private int m_iFrom, _iWhiteRemaining, _iBlackRemaining, _iGameNum, _iTurn, m_iTo;
    private ICSClient _parent;
    private boolean _bHandleClick, _bOngoingGame, _bForceFlipBoard, _bConfirmMove, _bCanPreMove;
    private Timer _timer;
    private static final int MSG_TOP_TIME = 1, MSG_BOTTOM_TIME = 2;
    public static final int VIEW_NONE = 0, VIEW_PLAY = 1, VIEW_WATCH = 2, VIEW_EXAMINE = 3, VIEW_PUZZLE = 4, VIEW_ENDGAME = 5;
    protected int _viewMode;

//	private Vibrator _vibrator;

    protected Handler m_timerHandler = new Handler() {
        /** Gets called on every message that is received */
        // @Override
        public void handleMessage(Message msg) {
            if (msg.what == MSG_TOP_TIME) {
                _tvClockTop.setText(parseTime(msg.getData().getInt("ticks")));
            } else {
                _tvClockBottom.setText(parseTime(msg.getData().getInt("ticks")));

                if (_parent.is_bTimeWarning() && (msg.getData().getInt("ticks") <= _parent.get_TimeWarning()) && (msg.getData().getInt("ticks") > 0)){
                    try {
                        _parent.soundTickTock();
                    } catch (Exception e) {
                        Log.e(TAG, "Died", e);
                    }
                }
            }
        }
    };

    public ICSChessView(Activity activity) {
        super(activity);

        _jni = new JNI();
        _jni.reset();

        _parent = (ICSClient) activity;

        m_iFrom = -1;
        m_iTo = -1;

        _bHandleClick = false;
        _viewMode = VIEW_NONE;
        _bOngoingGame = false;
        _bForceFlipBoard = false;
        _bCanPreMove = false;
        _opponent = "";
        _iTurn = BoardConstants.WHITE;
        _iWhiteRemaining = _iBlackRemaining = 0;
        _bConfirmMove = false;

        _tvPlayerTop = (TextView) _activity.findViewById(R.id.TextViewTop);
        _tvPlayerBottom = (TextView) _activity.findViewById(R.id.TextViewBottom);

        _tvClockTop = (TextView) _activity.findViewById(R.id.TextViewClockTop);
        _tvClockBottom = (TextView) _activity.findViewById(R.id.TextViewClockBottom);

        _tvBoardNum = (TextView) _activity.findViewById(R.id.TextViewICSBoardNum);
        //_tvViewMode = (TextView)_activity.findViewById(R.id.TextViewICSBoardViewMode);
        _tvLastMove = (TextView) _activity.findViewById(R.id.TextViewICSBoardLastMove);

        _butCancelMove = (Button) _activity.findViewById(R.id.ButtonCancelMove);
        _butCancelMove.setOnClickListener(new OnClickListener() {
            public void onClick(View arg0) {
                m_iFrom = -1;
                m_iTo = -1;
                _bHandleClick = true;
                _jni.undo();
                paint();
                // switch back
                _viewSwitchConfirm.setDisplayedChild(0);
            }
        });
        _butConfirmMove = (Button) _activity.findViewById(R.id.ButtonConfirmMove);
        _butConfirmMove.setOnClickListener(new OnClickListener() {
            public void onClick(View arg0) {

                _tvLastMove.setText("...");
                String sMove = Pos.toString(m_iFrom) + "-" + Pos.toString(m_iTo);
                _parent.sendString(sMove);
                m_iFrom = -1;
                // switch back
                _viewSwitchConfirm.setDisplayedChild(0);
            }
        });

        _viewSwitchConfirm = (ViewSwitcher) _activity.findViewById(R.id.ViewSitcherConfirmAndText);
         
         /*
         ImageButton butPrev = (ImageButton)_activity.findViewById((R.id.ButtonICSExamineRew));
 		butPrev.setOnClickListener(new OnClickListener() {
        	public void onClick(View arg0) {
        		_parent.sendString("backward");
			}
		});
 		
 		ImageButton butNext = (ImageButton)_activity.findViewById((R.id.ButtonICSExamineFf));
 		butNext.setOnClickListener(new OnClickListener() {
        	public void onClick(View arg0) {
        		_parent.sendString("forward");
			}
		});
		*/

        _timer = new Timer(true);
        _timer.schedule(new TimerTask() {
            @Override
            public void run() {
                int ticks = 0;
                // do not use keep alive
                //if(_clockTicks % 120 == 119){
                //	_parent.sendString("style 12");
                //}

                if (false == _bOngoingGame)
                    return;
                if (_iTurn == BoardConstants.WHITE) {
                    _iWhiteRemaining--;
                    ticks = _iWhiteRemaining;
                } else {
                    _iBlackRemaining--;
                    ticks = _iBlackRemaining;
                }
                //Log.i("TimerTask", "ticks=" + ticks + " turn " + _iTurn + " w" + _iWhiteRemaining + " b" + _iBlackRemaining);
                if (ticks >= 0) {
                    Message msg = new Message();

                    if (_flippedBoard) {
                        msg.what = _iTurn == BoardConstants.WHITE ? MSG_TOP_TIME : MSG_BOTTOM_TIME;
                    } else {
                        msg.what = _iTurn == BoardConstants.WHITE ? MSG_BOTTOM_TIME : MSG_TOP_TIME;
                    }

                    Bundle bun = new Bundle();
                    bun.putInt("ticks", ticks);
                    msg.setData(bun);
                    m_timerHandler.sendMessage(msg);
                }
            }
        }, 1000, 1000);
  
         /*
         ImageButton butChat = (ImageButton)_parent.findViewById(R.id.ButtonBoardChat);
         butChat.setOnClickListener(new View.OnClickListener() {
         	public void onClick(View arg0) {
         		if(_bInTheGame){
         			_parent._dlgChat.setTitle("Chat");	
         		}
         		else {
         			_parent._dlgChat.setTitle("Whisper");
         		}
         		_parent._dlgChat.show();
         	}
     	});
        */
        OnClickListener ocl = new OnClickListener() {
            public void onClick(View arg0) {

                handleClick(getFieldIndex(getIndexOfButton(arg0)));
            }
        };

        init(ocl);
    }

    public void init() {

        Log.i("init", "=========");

        m_iFrom = -1;
        m_iTo = -1;

        _bHandleClick = false;
        _bOngoingGame = false;
        _opponent = "";
        _flippedBoard = false;

        paint();
    }

    public void setViewMode(final int iMode) {
        _viewMode = iMode;
        updateViewMode();
    }

    public void updateViewMode() {
        switch (_viewMode) {
            case VIEW_NONE:
                Log.i("ICSChessView", "Idle");
                break;
            case VIEW_PLAY:
                Log.i("ICSChessView", "Play");
                break;
            case VIEW_WATCH:
                Log.i("ICSChessView", "Watch");
                break;
            case VIEW_EXAMINE:
                Log.i("ICSChessView", "Examine");
                break;
            case VIEW_PUZZLE:
                Log.i("ICSChessView", "Puzzle");
                break;
            case VIEW_ENDGAME:
                Log.i("ICSChessView", "Endgame");
                break;
            default:
                Log.i("ICSChessView", "X");
        }
    }

    public boolean isUserPlaying() {
        return _viewMode == VIEW_PLAY;
    }

    public String getOpponent() {
        return _opponent;
    }

    public int getGameNum() {
        return _iGameNum;
    }

    public void setGameNum(int num) {
        Log.i("setGameNum", "num = " + num);
        _iGameNum = num;
    }

    public void setConfirmMove(boolean b) {
        _bConfirmMove = b;
    }

    public void stopGame() {
        Log.i("stopGame", "=========");
        _bOngoingGame = false;
        _viewMode = VIEW_NONE;
        _flippedBoard = false;
        _iGameNum = 0;
        m_iTo = 0;
        m_iFrom = -1;

        _jni.reset();

        //resetImageCache();
        //paint();
    }

    public void forceFlipBoard() {
        _bForceFlipBoard = _bForceFlipBoard ? false : true;
        _flippedBoard = _bForceFlipBoard;
        paint();
    }

    public synchronized boolean preParseGame(final String fLine) {
        try {
            // 64 fields + 8 spaces = 72
            if (fLine.length() > 72) {
                String line = fLine.substring(72);

                //_flippedBoard = false;
                //B 0 0 1 1 0 7 Newton Einstein 1 2 12 39 39 119 122 2 K/e1-e2 (0:06) Ke2 0
                StringTokenizer st = new StringTokenizer(line);

                st.nextToken(); // W or B
                st.nextToken();
                st.nextToken();
                st.nextToken();
                st.nextToken();
                st.nextToken();
                st.nextToken();

                // skip the check for gamenum?
                int iTmp = Integer.parseInt(st.nextToken());

                if (_iGameNum == iTmp) {

                    return true;
                }

                Log.i("preParseGame", "Gamenum " + _iGameNum + " <> " + iTmp);

            } // > 64

        } catch (Exception ex) {
            Log.e("preParseGame", ex.toString());
        }
        return false;
    }

    public synchronized boolean parseGame(String line, String sMe) {
        //Log.i("parseGame", line);

        try {
            //<12> rnbqkb-r pppppppp -----n-- -------- ----P--- -------- PPPPKPPP RNBQ-BNR B -1 0 0 1 1 0 7 Newton Einstein 1 2 12 39 39 119 122 2 K/e1-e2 (0:06) Ke2 0
            //<12> ----k--r -npn-ppp -p---r-- ---Pp--- BPP-P-Pb -----R-- ---BN-P- -R-K---- B -1 0 0 1 0 2 1227 Lingo DoctorYona 0 3 0 25 25 81 80 29 B/c2-a4 (0:02) Ba4 0

            _jni.reset();

            resetImageCache();

            int p = 0, t = 0, index = -1; // !!

            for (int i = 0; i < 64; i++) {
                if (i % 8 == 0)
                    index++;
                char c = line.charAt(index++);
                if (c != '-') {
                    if (c == 'k' || c == 'K') {
                        p = BoardConstants.KING;
                        t = (c == 'k' ? BoardConstants.BLACK : BoardConstants.WHITE);
                    } else if (c == 'q' || c == 'Q') {
                        p = BoardConstants.QUEEN;
                        t = (c == 'q' ? BoardConstants.BLACK : BoardConstants.WHITE);
                    } else if (c == 'r' || c == 'R') {
                        p = BoardConstants.ROOK;
                        t = (c == 'r' ? BoardConstants.BLACK : BoardConstants.WHITE);
                    } else if (c == 'n' || c == 'N') {
                        p = BoardConstants.KNIGHT;
                        t = (c == 'n' ? BoardConstants.BLACK : BoardConstants.WHITE);
                    } else if (c == 'b' || c == 'B') {
                        p = BoardConstants.BISHOP;
                        t = (c == 'b' ? BoardConstants.BLACK : BoardConstants.WHITE);
                    } else if (c == 'p' || c == 'P') {
                        p = BoardConstants.PAWN;
                        t = (c == 'p' ? BoardConstants.BLACK : BoardConstants.WHITE);
                    } else
                        continue;
                    _jni.putPiece(i, p, t);
                }
            } // loop 64
            index++;

            line = line.substring(index);
            //_flippedBoard = false;
            //B 0 0 1 1 0 7 Newton Einstein 1 2 12 39 39 119 122 2 K/e1-e2 (0:06) Ke2 0
            StringTokenizer st = new StringTokenizer(line);
            _iTurn = BoardConstants.WHITE;
            if (st.nextToken().equals("B")) {
                _jni.setTurn(BoardConstants.BLACK);
                _iTurn = BoardConstants.BLACK;
            }
            // -1 none, or 0-7 for column indicates double pawn push
            int iEPColumn = Integer.parseInt(st.nextToken());
            int wccs = Integer.parseInt(st.nextToken());
            int wccl = Integer.parseInt(st.nextToken());
            int bccs = Integer.parseInt(st.nextToken());
            int bccl = Integer.parseInt(st.nextToken());
            int r50 = Integer.parseInt(st.nextToken());
            int ep = -1;
            if (iEPColumn >= 0) {
                // calc from previous turn!
                if (_iTurn == BoardConstants.WHITE) {
                    ep = iEPColumn + 16;
                } else {
                    ep = iEPColumn + 40;
                }
                Log.i("parseGame", "EP: " + ep);
            }

            // skip the check for gamenum?
            int iTmp = Integer.parseInt(st.nextToken());
            _iGameNum = iTmp;
            _tvBoardNum.setText("" + _iGameNum);
            /*
            if(_iGameNum != iTmp){
    			Log.i("parseGame", "Gamenum " + _iGameNum + " <> " + iTmp);
    			return false;
    		}
    		*/
            _whitePlayer = st.nextToken();
            _blackPlayer = st.nextToken();

            if (_blackPlayer.equalsIgnoreCase(sMe)) {
                _flippedBoard = true;
            } else if (_whitePlayer.equalsIgnoreCase(sMe)) {
                _flippedBoard = false;
            } else {
                _flippedBoard = _bForceFlipBoard;
            }

            int iMe = Integer.parseInt(st.nextToken());
            //_bHandleClick = (iMe == 1);
            _bHandleClick = true;
            //Log.i("parseGame", "setting handleclick " + iMe + ":" + _whitePlayer + ":" + _blackPlayer);

            //_bInTheGame = iMe == 1 || iMe == -1;
            _bCanPreMove = false;
            if (_viewMode == VIEW_PLAY) {
                _opponent = _blackPlayer.equals(sMe) ? _whitePlayer : _blackPlayer;
                if (iMe == 1) {

                    if (m_iFrom != -1 && m_iTo != -1) {
                        _tvLastMove.setText("...");
                        String sMove = Pos.toString(m_iFrom) + "-" + Pos.toString(m_iTo);
                        _parent.sendString(sMove);
                        m_iFrom = -1;

                    } else {
                        Log.i("ICSChessView", "Sound notification!");
                        _parent.soundNotification();
                    }
                } else if (false == _bConfirmMove) {
                    _bCanPreMove = true;
                }
            }
            _bOngoingGame = true;

            int iTime = Integer.parseInt(st.nextToken());
            int iIncrement = Integer.parseInt(st.nextToken());
            st.nextToken();
            st.nextToken();
            _iWhiteRemaining = Integer.parseInt(st.nextToken());
            _iBlackRemaining = Integer.parseInt(st.nextToken());

            if (_flippedBoard) {
                _tvPlayerTop.setText(_whitePlayer);
                _tvPlayerBottom.setText(_blackPlayer);
                _tvClockTop.setText(parseTime(_iWhiteRemaining));
                _tvClockBottom.setText(parseTime(_iBlackRemaining));
            } else {
                _tvPlayerTop.setText(_blackPlayer);
                _tvPlayerBottom.setText(_whitePlayer);
                _tvClockTop.setText(parseTime(_iBlackRemaining));
                _tvClockBottom.setText(parseTime(_iWhiteRemaining));
            }

            // the last move
            st.nextToken();
            String sMove = st.nextToken();

            //int iFrom = -1;
            if (false == sMove.equals("none") && sMove.length() > 2) {

                _tvLastMove.setText(sMove);

                if (sMove.equals("o-o")) {
                    if (_iTurn == BoardConstants.WHITE)
                        m_iTo = Pos.fromString("g8");
                    else
                        m_iTo = Pos.fromString("g1");
                } else if (sMove.equals("o-o-o")) {
                    if (_iTurn == BoardConstants.WHITE)
                        m_iTo = Pos.fromString("c8");
                    else
                        m_iTo = Pos.fromString("c1");
                } else {
                    // gxh8=R
                    try {
                        //K/e1-e2
                        m_iTo = Pos.fromString(sMove.substring(sMove.length() - 2));
                        //iFrom = Pos.fromString(sMove.substring(sMove.length()-5, 2));
                    } catch (Exception ex2) {
                        m_iTo = -1;
                        Log.i("parseGame", "Could not parse move: " + sMove + " in " + sMove.substring(sMove.length() - 2));
                    }
                }
            } else {
                _tvLastMove.setText("");
            }

            //
            _jni.setCastlingsEPAnd50(wccl, wccs, bccl, bccs, ep, r50);

            _jni.commitBoard();

            // _board
            paint();

            //Log.i("parseGame", "Done..." + _bHandleClick);
            return true;

        } catch (Exception ex) {
            Log.e("parseGame", ex.toString());
            return false;
        }

    }

    private String parseTime(int sec) {

        return String.format("%d:%02d", (int) (Math.floor(sec / 60)), sec % 60);
    }

    private void paint() {
        paintBoard(_jni, new int[]{m_iFrom, m_iTo}, null);
    }

    private void handleClick(int index) {
        // _board != null
        Log.i("handleClick", "Clicked " + index + " handling " + _bHandleClick);

        if (_bHandleClick) {
            m_iTo = -1;

            if (m_iFrom == -1) {
                // when a pre move is possible, check if the selected position is a field
                if (_bCanPreMove) {
                    if (_jni.pieceAt(_jni.getTurn() == ChessBoard.WHITE ? ChessBoard.BLACK : ChessBoard.WHITE, index) == BoardConstants.FIELD) {
                        return;
                    }
                }
                // same for a regular move, but then with the actual turn
                else if (_jni.pieceAt(_jni.getTurn(), index) == BoardConstants.FIELD) {
                    return;
                }

                m_iFrom = index;
                paint();
            } else {

                if (_bCanPreMove) {
                    m_iTo = index;
                    Log.i("ICSChessView", "pre move:" + m_iFrom + "-" + m_iTo);
                    paint();
                    return;
                }

                boolean isCastle = false;

                if (_jni.isAmbiguousCastle(m_iFrom, index) != 0) { // in case of Fischer

                    isCastle = true;

                } else if (index == m_iFrom) {
                    m_iFrom = -1;
                    return;
                }
                // if valid move
                // collect legal moves if pref is set
                boolean isValid = false;
                int move = -1;
                try {
                    // via try catch because of empty or mem error results in exception

                    if (_jni.isEnded() == 0) {
                        synchronized (this) {
                            int size = _jni.getMoveArraySize();
                            //Log.i("paintBoard", "# " + size);

                            boolean isPromotion = false;

                            for (int i = 0; i < size; i++) {
                                move = _jni.getMoveArrayAt(i);
                                if (Move.getFrom(move) == m_iFrom) {
                                    if (Move.getTo(move) == index) {
                                        isValid = true;

                                        // check if it is promotion
                                        if (_jni.pieceAt(BoardConstants.WHITE, m_iFrom) == BoardConstants.PAWN &&
                                                BoardMembers.ROW_TURN[BoardConstants.WHITE][m_iFrom] == 6 &&
                                                BoardMembers.ROW_TURN[BoardConstants.WHITE][index] == 7
                                                ||
                                                _jni.pieceAt(BoardConstants.BLACK, m_iFrom) == BoardConstants.PAWN &&
                                                        BoardMembers.ROW_TURN[BoardConstants.BLACK][m_iFrom] == 6 &&
                                                        BoardMembers.ROW_TURN[BoardConstants.BLACK][index] == 7) {

                                            isPromotion = true;

                                        }

                                        break;
                                    }
                                }
                            }

                            if (isPromotion) {
                                final String[] items = _parent.getResources().getStringArray(R.array.promotionpieces);
                                final int finalIndex = index;

                                AlertDialog.Builder builder = new AlertDialog.Builder(_parent);
                                builder.setTitle(R.string.title_pick_promo);
                                builder.setSingleChoiceItems(items, 0, new DialogInterface.OnClickListener() {
                                    public void onClick(DialogInterface dialog, int item) {
                                        dialog.dismiss();
                                        _jni.setPromo(4 - item);
                                        String[] arrPromos = {"q", "r", "b", "n"};
                                        _parent.sendString("promote " + arrPromos[item]);
                                        int move, size = _jni.getMoveArraySize();
                                        for (int i = 0; i < size; i++) {
                                            move = _jni.getMoveArrayAt(i);
                                            if (Move.getFrom(move) == m_iFrom) {
                                                if (Move.getTo(move) == finalIndex && Move.getPromotionPiece(move) == (4 - item)) {

                                                    continueMove(finalIndex, true, move);
                                                    return;
                                                }
                                            }
                                        }
                                    }
                                });
                                AlertDialog alert = builder.create();
                                alert.show();

                                return;
                            }

                            if (isCastle) {

                                final int finalIndex = index;

                                AlertDialog.Builder builder = new AlertDialog.Builder(_parent);
                                builder.setTitle(R.string.title_castle);
                                builder.setPositiveButton(R.string.alert_yes, new DialogInterface.OnClickListener() {
                                    public void onClick(DialogInterface dialog, int item) {
                                        dialog.dismiss();

                                        int move, size = _jni.getMoveArraySize();
                                        for (int i = 0; i < size; i++) {
                                            move = _jni.getMoveArrayAt(i);
                                            if (Move.getFrom(move) == m_iFrom) {
                                                if (Move.getTo(move) == finalIndex && (Move.isOO(move) || Move.isOOO(move))) {
                                                    continueMove(finalIndex, true, move);
                                                    return;
                                                }
                                            }
                                        }
                                    }
                                });
                                builder.setNegativeButton(R.string.alert_no, new DialogInterface.OnClickListener() {
                                    public void onClick(DialogInterface dialog, int item) {
                                        dialog.dismiss();
                                        if (m_iFrom != finalIndex) {
                                            int move, size = _jni.getMoveArraySize();
                                            for (int i = 0; i < size; i++) {
                                                move = _jni.getMoveArrayAt(i);
                                                if (Move.getTo(move) == finalIndex && (false == Move.isOO(move)) && (false == Move.isOOO(move))) {
                                                    continueMove(finalIndex, true, move);
                                                    return;
                                                }
                                            }
                                        } else {
                                            m_iFrom = -1;
                                        }
                                    }
                                });
                                AlertDialog alert = builder.create();
                                alert.show();

                                return;
                            }

                        }
                    }
                } catch (Exception e) {
                    System.gc();
                }

                continueMove(index, isValid, move);
            }
        }
    }

    private void continueMove(int index, boolean isValid, int move) {
        if (isValid) {

            _bHandleClick = false;
            // if confirm and is playing, first let user confirm
            if (_bConfirmMove && isUserPlaying()) {

                _tvLastMove.setText("");
                //
                m_iTo = index;
                _viewSwitchConfirm.setDisplayedChild(1);

                _jni.move(move);
                paint();

            } else {
                _tvLastMove.setText("...");
                // test and make move if valid move
                //
                String sMove = "";
                if (Move.isOO(move)) {
                    sMove = "0-0";
                } else if (Move.isOOO(move)) {
                    sMove = "0-0-0";
                } else {
                    sMove = Pos.toString(m_iFrom) + "-" + Pos.toString(index);
                }
                _parent.sendString(sMove);
                m_iTo = index;
                paint();
                m_iFrom = -1;
            }
        } else {
            m_iFrom = -1;
            // show that move is invalid
            _tvLastMove.setText("invalid");
        }
    }
}