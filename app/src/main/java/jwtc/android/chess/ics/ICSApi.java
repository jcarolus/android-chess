package jwtc.android.chess.ics;

import android.util.Log;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.StringTokenizer;

import jwtc.android.chess.services.GameApi;
import jwtc.chess.Pos;
import jwtc.chess.board.BoardConstants;

public class ICSApi extends GameApi implements ICSListener {
    public static final String TAG = "GameApi";
    public static final int VIEW_NONE = 0, VIEW_PLAY = 1, VIEW_WATCH = 2, VIEW_EXAMINE = 3, VIEW_PUZZLE = 4, VIEW_ENDGAME = 5;

    protected int _iGameNum;
    protected String _whitePlayer;
    protected String _blackPlayer;
    protected boolean _flippedBoard = true;
    protected String _playerMe;
    protected String _opponent;
    protected int _viewMode;
    protected boolean _bCanPreMove;
    protected int _iWhiteRemaining;
    protected int _iBlackRemaining;

    @Override
    public void OnLoginSuccess() {

    }

    @Override
    public void OnLoginFailed() {

    }

    @Override
    public void OnLoggingIn() {

    }

    @Override
    public void OnSessionEnded() {

    }

    @Override
    public void OnError() {

    }

    @Override
    public void OnPlayerList(ArrayList<HashMap<String, String>> playerList) {

    }

    @Override
    public void OnBoardUpdated(String gameLine, String handle) {
        parseGame(gameLine, handle);
    }

    @Override
    public void OnChallenged(HashMap<String, String> challenge) {

    }

    @Override
    public void OnIllegalMove() {

    }

    @Override
    public void OnSeekNotAvailable() {

    }

    @Override
    public void OnPlayGameStarted(String whiteHandle, String blackHandle, String whiteRating, String blackRating) {

    }

    @Override
    public void OnGameNumberUpdated(int number) {

    }

    @Override
    public void OnOpponentRequestsAbort() {

    }

    @Override
    public void OnOpponentRequestsAdjourn() {

    }

    @Override
    public void OnOpponentOffersDraw() {

    }

    @Override
    public void OnOpponentRequestsTakeBack() {

    }

    @Override
    public void OnAbortConfirmed() {

    }

    @Override
    public void OnPlayGameResult(String message) {

    }

    @Override
    public void OnPlayGameStopped() {

    }

    @Override
    public void OnYourRequestSended() {

    }

    @Override
    public void OnChatReceived() {

    }

    @Override
    public void OnResumingAdjournedGame() {

    }

    @Override
    public void OnAbortedOrAdjourned() {

    }

    @Override
    public void OnObservingGameStarted() {

    }

    @Override
    public void OnObservingGameStopped() {

    }

    @Override
    public void OnPuzzleStarted() {

    }

    @Override
    public void OnPuzzleStopped() {

    }

    @Override
    public void OnExaminingGameStarted() {

    }

    @Override
    public void OnExaminingGameStopped() {

    }

    @Override
    public void OnSoughtResult(ArrayList<HashMap<String, String>> soughtList) {

    }

    @Override
    public void OnGameListResult(ArrayList<HashMap<String, String>> games) {

    }

    @Override
    public void OnStoredListResult(ArrayList<HashMap<String, String>> games) {

    }

    @Override
    public void OnEndGameResult(int state) {

    }

    @Override
    public void OnConsoleOutput(String buffer) {

    }

    public synchronized boolean parseGame(String line, String sMe) {
        //Log.i(TAG, "parseGame" + line);

        String _sNumberOfMove;

        try {
            //<12> rnbqkb-r pppppppp -----n-- -------- ----P--- -------- PPPPKPPP RNBQ-BNR B -1 0 0 1 1 0 7 Newton Einstein 1 2 12 39 39 119 122 2 K/e1-e2 (0:06) Ke2 0
            //<12> ----k--r -npn-ppp -p---r-- ---Pp--- BPP-P-Pb -----R-- ---BN-P- -R-K---- B -1 0 0 1 0 2 1227 Lingo DoctorYona 0 3 0 25 25 81 80 29 B/c2-a4 (0:02) Ba4 0

            jni.reset();

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
                    jni.putPiece(i, p, t);
                }
            } // loop 64
            index++;

            line = line.substring(index);
            //_flippedBoard = false;
            //B 0 0 1 1 0 7 Newton Einstein 1 2 12 39 39 119 122 2 K/e1-e2 (0:06) Ke2 0
            StringTokenizer st = new StringTokenizer(line);
            String _sTurn = st.nextToken();  // _sTurn is "W" or "B"
            int _iTurn = BoardConstants.WHITE;  //  _iTurn is  1  or  0
            if (_sTurn.equals("B")) {
                jni.setTurn(BoardConstants.BLACK);
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
            int _iGameNum = iTmp;

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
                _playerMe = _blackPlayer;
            } else if (_whitePlayer.equalsIgnoreCase(sMe)) {
                _flippedBoard = false;
                _playerMe = _whitePlayer;
            }

            int _iMe = Integer.parseInt(st.nextToken()); // my relation number to this game
            /*
            -3 isolated position, such as for "ref 3" or the "sposition" command
            -2 I am observing game being examined
             2 I am the examiner of this game
            -1 I am playing, it is my opponent's move
             1 I am playing and it is my move
             0 I am observing a game being played
             */
            if ((_iMe == 2 || _iMe == -2)) {  //  I am the examiner or observer of this game
                //initiate textviews in examine mode
                //_tvMoveNumber.setText("1");
                _viewMode = VIEW_EXAMINE;
            }
            //_bHandleClick = (iMe == 1);


            //_bInTheGame = iMe == 1 || iMe == -1;
            _bCanPreMove = false;
            if (_viewMode == VIEW_PLAY) {
                _opponent = _blackPlayer.equals(sMe) ? _whitePlayer : _blackPlayer;
                if (_iMe == 1) {

//                    if (m_iFrom != -1 && m_iTo != -1) {
//                        _tvLastMove.setText("...");
//                        String sMove = Pos.toString(m_iFrom) + "-" + Pos.toString(m_iTo);
//                        _parent.sendString(sMove);
//                        m_iFrom = -1;
//
//                    } else {
//                        Log.i("ICSChessView", "Sound notification!");
//                        _parent.soundNotification();
//                    }
                } else {
                    _bCanPreMove = true;
                }
            }

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
            if (sLastMoveDisplay.contains("+")) {
//                _parent.soundCheck();
            } else if (sLastMoveDisplay.contains("x")) {
//                _parent.soundCapture();
            } else {
//                _parent.soundMove();
            }
            int iFlipBoardOrientation = Integer.parseInt(st.nextToken()); //0 = White on Bottom / 1 = Black on bottom


            //int iFrom = -1;
            if (false == sMove.equals("none") && sMove.length() > 2) {

//                _tvLastMove.setText(_iTurn == 1 ? "." + sLastMoveDisplay : sLastMoveDisplay);  // display last move
//                _tvTimePerMove.setText(_sTimePerMove);
//                // The about to be move is converted to the current move
//                _tvMoveNumber.setText(_iTurn == 0 ? _sNumberOfMove : Integer.toString(Integer.parseInt(_sNumberOfMove) - 1));
//
//                if (sMove.equals("o-o")) {
//                    if (_iTurn == BoardConstants.WHITE)
//                        m_iTo = Pos.fromString("g8");
//                    else
//                        m_iTo = Pos.fromString("g1");
//                } else if (sMove.equals("o-o-o")) {
//                    if (_iTurn == BoardConstants.WHITE)
//                        m_iTo = Pos.fromString("c8");
//                    else
//                        m_iTo = Pos.fromString("c1");
//                } else {
//                    // gxh8=R
//                    try {
//                        //K/e1-e2
//                        m_iTo = Pos.fromString(sMove.substring(sMove.length() - 2));
//                        //iFrom = Pos.fromString(sMove.substring(sMove.length()-5, 2));
//                    } catch (Exception ex2) {
//                        m_iTo = -1;
//                        Log.i("parseGame", "Could not parse move: " + sMove + " in " + sMove.substring(sMove.length() - 2));
//                    }
//                }
            } else {
//                _tvLastMove.setText("");
            }

            //
            jni.setCastlingsEPAnd50(wccl, wccs, bccl, bccs, ep, r50);

            jni.commitBoard();

            // _board
            dispatchState();

            //Log.i("parseGame", "Done..." + _bHandleClick);
            return true;

        } catch (Exception ex) {
            Log.e("parseGame", ex.toString());
            return false;
        }

    }
}
