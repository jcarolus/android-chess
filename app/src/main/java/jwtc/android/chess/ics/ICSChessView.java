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
import android.graphics.Color;
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
    private TextView _tvPlayerTop, _tvPlayerBottom, _tvPlayerTopRating, _tvPlayerBottomRating,
            _tvClockTop, _tvClockBottom, _tvBoardNum, _tvLastMove, _tvTimePerMove, _tvMoveNumber;

    //private EditText _editChat;
    protected ImageButton _butImageBackward, _butImageForward, _butImageRevert;
    private Button _butConfirmMove, _butCancelMove;
    private ViewSwitcher _viewSwitchConfirm;
    private String _opponent, _whitePlayer, _blackPlayer, _playerMe;
    private int m_iFrom, _iWhiteRemaining, _iBlackRemaining, _iGameNum, _iMe, _iTurn, m_iTo;
    private ICSClient _parent;
    private boolean _bHandleClick, _bOngoingGame, _bConfirmMove, _bConfirmMoveLongClick, _bCanPreMove, _bfirst;
    private Timer _timer;
    private static final int MSG_TOP_TIME = 1, MSG_BOTTOM_TIME = 2;
    public static final int VIEW_NONE = 0, VIEW_PLAY = 1, VIEW_WATCH = 2, VIEW_EXAMINE = 3, VIEW_PUZZLE = 4, VIEW_ENDGAME = 5;
    protected int _viewMode;

    protected static final int INCREASE = 1;

    protected Handler m_timerHandler = new Handler() {
        /** Gets called on every message that is received */
        // @Override
        public void handleMessage(Message msg) {
            if(_viewMode == VIEW_EXAMINE || _viewMode == VIEW_NONE){  // No ticks during EXAMINE mode or IDLE Mode
                return;
            }
            int countDown = msg.getData().getInt("ticks");

            if (msg.what == MSG_TOP_TIME) {
                _tvClockTop.setText(parseTime(countDown));
            } else {
                _tvClockBottom.setText(parseTime(countDown));
                _tvClockBottom.setBackgroundColor(Color.TRANSPARENT);

            }
            if((msg.what == MSG_TOP_TIME && (_tvPlayerTop.getText()).equals(_playerMe))  // Time Low Warning
                || (msg.what == MSG_BOTTOM_TIME && (_tvPlayerBottom.getText()).equals(_playerMe))){
                if (_parent.is_bTimeWarning() && (countDown <= _parent.get_TimeWarning()) && (msg.getData().getInt("ticks") > 0)) {
                    try {
                        _parent.soundTickTock();
                        if (countDown%2 == 0){ _tvClockBottom.setBackgroundColor(Color.RED);
                        } else {_tvClockBottom.setBackgroundColor(Color.BLACK);}
                    } catch (Exception e) {
                        Log.e(TAG, "sound process died", e);
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
        _bCanPreMove = false;
        _opponent = "";
        _iTurn = BoardConstants.WHITE;
        _iWhiteRemaining = _iBlackRemaining = 0;
        _bConfirmMove = false;
        _bConfirmMoveLongClick = false;

        _tvPlayerTop = (TextView) _activity.findViewById(R.id.TextViewTop);
        _tvPlayerBottom = (TextView) _activity.findViewById(R.id.TextViewBottom);

        _tvPlayerTopRating = (TextView) _activity.findViewById(R.id.TextViewICSTwoRating);
        _tvPlayerBottomRating = (TextView) _activity.findViewById(R.id.TextViewICSOneRating);

        _tvClockTop = (TextView) _activity.findViewById(R.id.TextViewClockTop);
        _tvClockBottom = (TextView) _activity.findViewById(R.id.TextViewClockBottom);

        _tvBoardNum = (TextView) _activity.findViewById(R.id.TextViewICSBoardNum);
        //_tvViewMode = (TextView)_activity.findViewById(R.id.TextViewICSBoardViewMode);
        _tvLastMove = (TextView) _activity.findViewById(R.id.TextViewICSBoardLastMove);
        _tvTimePerMove = (TextView) _activity.findViewById(R.id.TextViewICSTimePerMove);
        _tvMoveNumber = (TextView) _activity.findViewById(R.id.TextViewMoveNumber);

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
                if (_bConfirmMoveLongClick){_bConfirmMoveLongClick = false;}
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
         
        _butImageBackward = (ImageButton)_activity.findViewById(R.id.ButtonICSExamineBackward);
        _butImageBackward.setOnClickListener(new OnClickListener() {
        	public void onClick(View arg0) {
        		_parent.sendString("backward");
			}
		});
 		
 		_butImageForward = (ImageButton)_activity.findViewById(R.id.ButtonICSExamineForward);
        _butImageForward.setOnClickListener(new OnClickListener() {
        	public void onClick(View arg0) {
        		_parent.sendString("forward");
			}
		});

        _butImageRevert = (ImageButton)_activity.findViewById(R.id.ButtonICSRevert);
        _butImageRevert.setOnClickListener(new OnClickListener() {
            @Override
            public void onClick(View arg0) {
                _parent.sendString("revert");
            }
        });


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

        OnLongClickListener olcl = new OnLongClickListener() {
            @Override
            public boolean onLongClick(View view) {

                //Log.d(TAG, "OnLongClickListener m_iFrom ->" + m_iFrom + "   m_iTo ->" + m_iTo);
                if (m_iFrom == -1){return false;} // return false will let OnClickListener take care of it
                setConfirmMoveLongClick(true);
                handleClick(getFieldIndex(getIndexOfButton(view)));
                return true;
            }
        };

        init(ocl, olcl);
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
        _bfirst = true;    // reset first flipboard state
        updateViewMode();
    }

    public void updateViewMode() {
        switch (_viewMode) {
            case VIEW_NONE:
                setButtonExamineVisibility(false);
                Log.i(TAG, "Idle");
                break;
            case VIEW_PLAY:
                switch(_parent.get_gameStartSound()){
                    case 0: break;
                    case 1: _parent.soundHorseNeigh();
                            _parent.vibration(INCREASE);
                            break;
                    case 2: _parent.soundHorseNeigh();
                            break;
                    case 3: _parent.vibration(INCREASE);
                            break;
                    default: Log.e(TAG, "get_gameStartSound error");
                }
                _parent.notificationAPP();
                Log.i(TAG, "Play");
                break;
            case VIEW_WATCH:
                Log.i(TAG, "Watch");
                break;
            case VIEW_EXAMINE:
                setButtonExamineVisibility(true);
                Log.i(TAG, "Examine");
                break;
            case VIEW_PUZZLE:
                Log.i(TAG, "Puzzle");
                break;
            case VIEW_ENDGAME:
                Log.i(TAG, "Endgame");
                break;
            default:
                Log.i(TAG, "X");
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

    public void setConfirmMoveLongClick(boolean b){
        _bConfirmMoveLongClick = b;
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

    public void setButtonExamineVisibility(boolean status){
        _butImageBackward.setVisibility(status ? View.VISIBLE : View.GONE);
        _butImageForward.setVisibility(status ? View.VISIBLE : View.GONE);
        _butImageRevert.setVisibility(status ? View.VISIBLE : View.GONE);
        _tvTimePerMove.setVisibility(status ? View.VISIBLE : View.GONE);

        _tvPlayerTopRating.setVisibility(status ? View.GONE : View.VISIBLE);  // EXAMINE pattern doesn't have ratings plus
        _tvPlayerBottomRating.setVisibility(status ? View.GONE : View.VISIBLE); //it allows enough room for examine buttons in landscape mode
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
        //Log.i(TAG, "parseGame" + line);

        String _sNumberOfMove;

        try {
            //<12> rnbqkb-r pppppppp -----n-- -------- ----P--- -------- PPPPKPPP RNBQ-BNR B -1 0 0 1 1 0 7 Newton Einstein 1 2 12 39 39 119 122 2 K/e1-e2 (0:06) Ke2 0
            //<12> ----k--r -npn-ppp -p---r-- ---Pp--- BPP-P-Pb -----R-- ---BN-P- -R-K---- B -1 0 0 1 0 2 1227 Lingo DoctorYona 0 3 0 25 25 81 80 29 B/c2-a4 (0:02) Ba4 0

            _jni.reset();

            resetImageCache();

            int p = 0, t = 0, index = -1; // !!

            for (int i = 0; i < 64; i++) {
                if (i % 8 == 0) {
                    index++;
                }
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
            String _sTurn = st.nextToken();  // _sTurn is "W" or "B"
            _iTurn = BoardConstants.WHITE;  //  _iTurn is  1  or  0
            if (_sTurn.equals("B")) {
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
            if(_bfirst) {
                if (_blackPlayer.equalsIgnoreCase(sMe)) {
                    _flippedBoard = true;
                    _playerMe = _blackPlayer;
                } else if (_whitePlayer.equalsIgnoreCase(sMe)) {
                    _flippedBoard = false;
                    _playerMe = _whitePlayer;
                }
                _bfirst = false;
            }
            _iMe = Integer.parseInt(st.nextToken()); // my relation number to this game
            /*
            -3 isolated position, such as for "ref 3" or the "sposition" command
            -2 I am observing game being examined
             2 I am the examiner of this game
            -1 I am playing, it is my opponent's move
             1 I am playing and it is my move
             0 I am observing a game being played
             */
            if ((_iMe == 2 || _iMe == -2) && _viewMode != VIEW_EXAMINE){  //  I am the examiner or observer of this game
                //initiate textviews in examine mode
                _tvMoveNumber.setText("1");
                this.setViewMode(VIEW_EXAMINE);
            }
            //_bHandleClick = (iMe == 1);
            _bHandleClick = true;
            //Log.i(TAG, "parseGame setting handleclick " + iMe + ":" + _whitePlayer + ":" + _blackPlayer);

            //_bInTheGame = iMe == 1 || iMe == -1;
            _bCanPreMove = false;
            if (_viewMode == VIEW_PLAY) {
                _opponent = _blackPlayer.equals(sMe) ? _whitePlayer : _blackPlayer;
                if (_iMe == 1) {

                    if (m_iFrom != -1 && m_iTo != -1) {
                        _tvLastMove.setText("...");
                        String sMove = Pos.toString(m_iFrom) + "-" + Pos.toString(m_iTo);
                        _parent.sendString(sMove);
                        m_iFrom = -1;

                    } else {
                        Log.i("ICSChessView", "Sound notification!");
                        _parent.soundNotification();
                    }
                } else if (!_bConfirmMove || !_bConfirmMoveLongClick) {
                    _bCanPreMove = true;
                }
            }
            _bOngoingGame = true;

            int iTime = Integer.parseInt(st.nextToken());
            int iIncrement = Integer.parseInt(st.nextToken());
            int iWhiteMaterialStrength = Integer.parseInt(st.nextToken());
            int iBlackMaterialStrength = Integer.parseInt(st.nextToken());
            _iWhiteRemaining = Integer.parseInt(st.nextToken());
            _iBlackRemaining = Integer.parseInt(st.nextToken());
            _sNumberOfMove = st.nextToken(); // the number of the move about to be made
            String sMove = st.nextToken();  // machine notation move
            String _sTimePerMove = st.nextToken();  // time it took to make a move
            String sLastMoveDisplay = st.nextToken();  // algebraic notation move
            if(sLastMoveDisplay.contains("+")){
                _parent.soundSmallNeigh();
            } else if(sLastMoveDisplay.contains("x")){
                _parent.soundCapture();
            } else {
                _parent.soundMove();
            }
            int iFlipBoardOrientation = Integer.parseInt(st.nextToken()); //0 = White on Bottom / 1 = Black on bottom

            if (_flippedBoard) {
                _tvPlayerTop.setText(_whitePlayer);
                if(_tvPlayerTopRating != null) {
                    _tvPlayerTopRating.setText(_parent.get_whiteRating());
                }
                _tvPlayerBottom.setText(_blackPlayer);
                if(_tvPlayerBottomRating != null) {
                    _tvPlayerBottomRating.setText(_parent.get_blackRating());
                }
                _tvClockTop.setText(parseTime(_iWhiteRemaining));
                _tvClockBottom.setText(parseTime(_iBlackRemaining));
            } else {
                _tvPlayerTop.setText(_blackPlayer);
                if(_tvPlayerTopRating != null) {
                    _tvPlayerTopRating.setText(_parent.get_blackRating());
                }
                _tvPlayerBottom.setText(_whitePlayer);
                if(_tvPlayerBottomRating != null) {
                    _tvPlayerBottomRating.setText(_parent.get_whiteRating());
                }
                _tvClockTop.setText(parseTime(_iBlackRemaining));
                _tvClockBottom.setText(parseTime(_iWhiteRemaining));
            }

            //int iFrom = -1;
            if (false == sMove.equals("none") && sMove.length() > 2) {

                _tvLastMove.setText(_iTurn==1 ? "." + sLastMoveDisplay: sLastMoveDisplay);  // display last move
                _tvTimePerMove.setText(_sTimePerMove);
                // The about to be move is converted to the current move
                _tvMoveNumber.setText(_iTurn==0 ? _sNumberOfMove : Integer.toString(Integer.parseInt(_sNumberOfMove)-1));

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

    public void paint() {
        paintBoard(_jni, new int[]{m_iFrom, m_iTo}, null);
    }

    private void handleClick(int index) {
        // _board != null
        Log.i("handleClick", "Clicked " + index + " handling " + _bHandleClick);

        // _bHandleClick helps to determine if the click should be handled (will not if screen loading, etc.)
        if (_bHandleClick) {
            m_iTo = -1;

            if(_jni.pieceAt(_jni.getTurn(), index) != BoardConstants.FIELD){
                m_iFrom = -1; // If another piece is selected reset origin.
            }

            // if a piece is not selected, determine if it should be and select it for movement
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

                if (_bConfirmMoveLongClick){setConfirmMoveLongClick(false);}

                m_iFrom = index;
                paint();
            } else {

                if (_bCanPreMove) {
                    m_iTo = index;
                    Log.i(TAG, "pre move:" + m_iFrom + "-" + m_iTo);
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
            if (_bConfirmMove || _bConfirmMoveLongClick && isUserPlaying()) {

                _tvLastMove.setText("");
                //
                m_iTo = index;
                _viewSwitchConfirm.setDisplayedChild(1);

                _jni.move(move);
                paint();
                if(_bConfirmMoveLongClick){setConfirmMoveLongClick(false);}

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
            // invalid move
            m_iTo = -1;  // the destination is reset
            _tvLastMove.setText("invalid");
            if(_bConfirmMoveLongClick){setConfirmMoveLongClick(false);}
        }
    }
}