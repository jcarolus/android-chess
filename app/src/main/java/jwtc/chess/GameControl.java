package jwtc.chess;

import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Date;
import java.util.HashMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import android.content.res.Resources;
import android.util.Log;
import jwtc.chess.algorithm.*;
import jwtc.chess.board.BoardConstants;


// Controls the game
// Superclass for user interface (UI) and ChessProblemSolver 
// searchtread can be run with selected searchalgorithm by calling play()
// contains two search algorithm objects, not nessacarely different. In PC_PC mode, the returned
// search algorithm objects will alternate between the two. This way, two different algorithms
// can be tested with self play against each other

public class GameControl
{
	// active - is control allowed
	protected boolean m_bActive;
	protected int m_iLevelMode;
	protected int _selectedLevel, _selectedLevelPly;
	
	// java native interface
	protected JNI _jni;
	
	// thread to run the searchalgorithm in
	protected Thread m_searchThread;
	
	protected UCIWrapper _uci;
	
	// The modes of play
	// human against human (only game control)
	public static final int HUMAN_HUMAN = 1;
	// human against PC
	public static final int HUMAN_PC = 0;
	// PC against PC - self play or auto play
	// public static final int PC_PC = 2;
	
	public static final int LEVEL_TIME = 1;
	public static final int LEVEL_PLY = 2;
	
	protected static Pattern _patNum;
	protected static Pattern _patAnnot;
	protected static Pattern _patMove;
	protected static Pattern _patCastling;
	static {
		
try{
			
			_patNum = Pattern.compile("(\\d+)\\.");
	    	_patAnnot = Pattern.compile("\\{([^\\{]*)\\}");
			_patMove = Pattern.compile("(K|Q|R|B|N)?(a|b|c|d|e|f|g|h)?(1|2|3|4|5|6|7|8)?(x)?(a|b|c|d|e|f|g|h)(1|2|3|4|5|6|7|8)(=Q|=R|=B|=N)?(\\+|#)?([\\?\\!]*)?[\\s]*");
			_patCastling = Pattern.compile("(O\\-O|O\\-O\\-O)(\\+|#)?([\\?\\!]*)?");
} catch(Exception e){
	
}
		
	}
	
	protected HashMap<String,String> _mapPGNHead; //
	protected ArrayList<PGNEntry> _arrPGN;
	
	
	// start timer at, and total time for me and opponent
	protected long _lClockStartWhite, _lClockStartBlack, _lClockWhite, _lClockBlack;
	public long _lClockTotal; // given time
	protected long _lClockIncrement; // given time

	
	// openingdatabase filename
	protected String _openingDbFileName;
	
	///////////////////////////////////////////////////////////
	public GameControl()
	{
		m_iLevelMode = LEVEL_TIME;
		
		_jni = new JNI();
		_jni.newGame();
		
		m_bActive = true;
		m_searchThread = null;
		
		_uci = new UCIWrapper(this);
		
		
		_mapPGNHead = new HashMap<String, String>();
		_arrPGN = new ArrayList<PGNEntry>();
		
		_openingDbFileName = null;
	}
	
	public void loadDB(final InputStream in, final String outFilename, final int depth){
		
		
		_openingDbFileName = outFilename;
		new Thread(new Runnable(){
        	public void run(){
				// TODO
				
				 try {
					 //File f = new File(outFilename);
					 // always copy now!
					 //if(f.exists() == false){
						 
						 OutputStream out = new FileOutputStream(outFilename);
						byte[] buf = new byte[1024];
						int len;
						while ((len = in.read(buf)) > 0) {
							out.write(buf, 0, len);
						}
						
						out.close();
						in.close();
					 //}
					_jni.loadDB(_openingDbFileName, depth);
					
				 } catch(Exception ex){}
        	}
        	}).start();
		
	}
	
	public void setOpeningDb(String sFileName){
		_jni.loadDB(sFileName, 30); // todo - number of plies
	}
	
	public String getOpeningDbFileName(){
		return _openingDbFileName;
	}
	
	public void enableControl()
	{
		m_bActive = true;	
	}
	public void disableControl()
	{
		m_bActive = false;	
	}
	// returns true when GUI input is allowed or is active
	public boolean isActive()
	{
		return m_bActive;	
	}	
	
	public void resetTimer(){
		Log.i("GameControl", "resetTimer ########################### ");
		_lClockIncrement = 0;	
		_lClockWhite = 0;
		_lClockBlack = 0;
		_lClockStartWhite = 0;
		_lClockStartBlack = 0;
		
		continueTimer();
	}
	
	protected void switchTimer(){
		final long lEnd = System.currentTimeMillis();
		if(_lClockStartWhite > 0 && _jni.getTurn() == BoardConstants.BLACK){
			_lClockWhite += (lEnd - _lClockStartWhite);
			_lClockStartWhite = 0;
			_lClockStartBlack = lEnd;
		} else if(_lClockStartBlack > 0 && _jni.getTurn() == BoardConstants.WHITE){
			_lClockBlack += (lEnd - _lClockStartBlack);
			_lClockStartBlack = 0;
			_lClockStartWhite = lEnd;
		}
	}
	public void setClockTotal(long millies){
		_lClockTotal = millies;
	}
	protected void pauzeTimer(){
		final long lEnd = System.currentTimeMillis();
		if(_lClockStartWhite > 0 && _jni.getTurn() == BoardConstants.WHITE){
			_lClockWhite += (lEnd - _lClockStartWhite);
		} else if(_lClockStartBlack > 0 && _jni.getTurn() == BoardConstants.BLACK){
			_lClockBlack += (lEnd - _lClockStartBlack);
		}
		_lClockStartWhite = 0;
		_lClockStartBlack = 0;
	}
	protected void continueTimer(){
		if(_lClockTotal > 0){
			if(_jni.getTurn() == BoardConstants.WHITE)
				_lClockStartWhite = System.currentTimeMillis();
			else
				_lClockStartBlack = System.currentTimeMillis();
		}
	}
	protected boolean pauseOrContinueTimer(){
		if(_lClockStartWhite > 0 || _lClockStartBlack > 0){
			pauzeTimer();
			return true;
		}
		else {
			continueTimer();
			return false;
		}
	}
	protected long getWhiteRemainClock(){
		final long lDiff = _lClockStartWhite > 0 ? (System.currentTimeMillis() - _lClockStartWhite) : 0;
    	return (_lClockTotal - (_lClockWhite + lDiff));
	}
	protected long getBlackRemainClock(){
		final long lDiff = _lClockStartBlack > 0 ? (System.currentTimeMillis() - _lClockStartBlack) : 0;
    	return (_lClockTotal - (_lClockBlack + lDiff));
	}

	protected boolean move(int move, String sAnnotation, boolean bUpdate)
	{
		//Log.i("requestMove debug", m_game.getBoard().getPGNMoves(new ChessBoard()));
		if(_jni.move(move) == 0)
		{
			return false;
		}		
		addPGNEntry(_jni.getNumBoard()-1, _jni.getMyMoveToString(), sAnnotation, _jni.getMyMove(), bUpdate);
		if(bUpdate)
			updateState();
		
		return true;
	}
	
	public void newGame()
	{
		Date d = Calendar.getInstance().getTime();
		SimpleDateFormat formatter = new SimpleDateFormat("yyyy.MM.dd");
		
		_mapPGNHead.clear();
		_mapPGNHead.put("Event", "?");
		_mapPGNHead.put("Site", "?");
		_mapPGNHead.put("Round", "?");
		_mapPGNHead.put("White", Resources.getSystem().getString(android.R.string.unknownName));
		_mapPGNHead.put("Black", Resources.getSystem().getString(android.R.string.unknownName));
		_mapPGNHead.put("Date", formatter.format(d));
		
		_arrPGN.clear();
		
		_jni.newGame();
	}
	
	
	public final boolean initFEN(final String sFEN, boolean resetHead){
		if(_jni.initFEN(sFEN)){
			
			if(resetHead){
				_mapPGNHead.clear();
				_mapPGNHead.put("Event", "?");
				_mapPGNHead.put("Site", "?");
				_mapPGNHead.put("Round", "?");
				_mapPGNHead.put("White", Resources.getSystem().getString(android.R.string.unknownName));
				_mapPGNHead.put("Black", Resources.getSystem().getString(android.R.string.unknownName));
			}
			_mapPGNHead.put("Setup", "1");
			_mapPGNHead.put("FEN", sFEN);
			
			_arrPGN.clear();
					
			return true;
		}
		return false;
	}

	
	
	public int newGameRandomFischer(int seed){
		
		int ret = _jni.initRandomFisher(seed);
		
		_mapPGNHead.clear();
		_mapPGNHead.put("Event", "?");
		_mapPGNHead.put("Site", "?");
		_mapPGNHead.put("Round", "?");
		_mapPGNHead.put("White", Resources.getSystem().getString(android.R.string.unknownName));
		_mapPGNHead.put("Black", Resources.getSystem().getString(android.R.string.unknownName));
		
		_mapPGNHead.put("Variant", "Fischerandom");
		_mapPGNHead.put("Setup", "1");
		_mapPGNHead.put("FEN", _jni.toFEN());
		
		_arrPGN.clear();

		return ret;
	}
	public void undo()
	{
		_jni.undo();
	}
	public void stopThreadAndUndo(){
		try {
			synchronized(this){
				//Log.i("GameControl", "stopThread");
				m_searchThread.interrupt();
				_jni.interrupt();
			}
			m_searchThread.join();
			undo();
			//undo();
		} catch (InterruptedException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
		enableControl();
	}
	
	public synchronized void play()
	{
		//Log.i("GameControl", "play() called");
		if(_uci.isReady()){
			if(getLevelMode() == LEVEL_PLY){
				_uci.play(0, getLevelPly());
			} else {
				int secs[] = {1, 1, 2, 4, 8, 10, 20, 30, 60, 300, 900, 1800}; // 1 offset, so 3 extra 1 unused secs
				_uci.play(secs[getLevel()] * 1000, 0);
			}
		} else {
			m_searchThread = new Thread(new SearchAlgorithmRunner(this));
			m_searchThread.start();
		}
	}
	
	public JNI getJNI()
	{
		return _jni;
	}
	
	public UCIWrapper getUCI(){
		return _uci;
	}
	
	public int getArrPGNSize(){
		return _arrPGN.size();
	}
	
	public void jumptoMove(int ply){
		// must be within history
		if(ply <= _arrPGN.size() && ply >= 0){
			int boardPly = _jni.getNumBoard();
			if(ply >= boardPly){
				while(ply >= boardPly){
					_jni.move(_arrPGN.get(boardPly-1)._move);
					boardPly++;
				}
			} else {
				while(ply < boardPly){
					_jni.undo();
					boardPly--;
				}
			}
		}
	}

	
	public void loadPGNHead(String s){
		
		s = s.replaceAll("[\\r\\n]+", " ");
		s = s.replaceAll("  ", " ");
		s = s.trim();
		s = s + " "; // for last token
		
		Matcher matchToken;
		String token;
		Pattern patTag = Pattern.compile("\\[(\\w+) \"(.*)\"\\]");
		
		int i = 0, pos;
		while(i < s.length()){
			pos = s.indexOf(" ", i);
			
			if(pos > 0){
				
				if(s.charAt(i) == '['){
					pos = s.indexOf("]", i);
					if(pos == -1)
						break;
					pos++;
				}
			} else {
				break;
			}
			
			token = s.substring(i, pos);
			
			i = pos + 1;
			
			matchToken = patTag.matcher(token);
			if(matchToken.matches()){
				_mapPGNHead.put(matchToken.group(1), matchToken.group(2));
				if(matchToken.group(1).equals("FEN")){
					initFEN(matchToken.group(2), false);
				}
			}
		}
	}
	
	protected boolean removeComment(StringBuffer s) throws Exception{
		int iOpen = s.indexOf("("), iClose = s.indexOf(")"), iNextOpen;
		if(iOpen >= 0 && iClose >= 0){
		
			iNextOpen = s.indexOf("(", iOpen+1);
			while(iNextOpen >= 0 && iNextOpen < iClose){
				iOpen = iNextOpen;
				iNextOpen = s.indexOf("(", iNextOpen+1);
			}
			if(iOpen > iClose){
				throw new Exception("Open bracket after closing bracket: " + iOpen + ", " + iClose);
			}
			
			s.delete(iOpen, iClose+1);
			return true;
		}
		if(iOpen >= 0){
			throw new Exception("No closing bracket for comment");
		}
		if(iClose >= 0){
			throw new Exception("No opening bracket for comment");
		}
		return false;
	}
	
	protected void removeDoubleSpaces(StringBuffer sb){
		int iSpace = sb.indexOf("  ");
		while(iSpace >= 0){
			sb.delete(iSpace, iSpace+1);
			iSpace = sb.indexOf("  ");
		}
	}
	
	public boolean loadPGNMoves(String s){
		
		_arrPGN.clear();
		
		s = s.replaceAll("[\\r\\n\\t]+", " ");
		//s = s.replaceAll("\\{[^\\}]*\\}", ""); // remove comments
		
		StringBuffer sb = new StringBuffer(s);
		
		removeDoubleSpaces(sb);
		
		//Log.i("loadPGNMoves", sb.toString());
		
		try{
			while(removeComment(sb)){
				;
			}
		} catch(Exception e){
			Log.w("loadPGNMoves", "Exception: " + e);
			Log.i("loadPGNMoves", sb.toString());
			return false;
		}
		
		removeDoubleSpaces(sb);
		
		s = sb.toString();
		
		//Log.i("loadPGNMoves", s);
		
		/*
		// the ( alternative ( move ) )
		Pattern pat = Pattern.compile("(\\([^\\)\\(]*\\))?");
		int i = 0, cnt = 0;
		while(s.indexOf("(") >= 0 && cnt < 200){
			cnt++;
			Matcher m = pat.matcher(s);
			if(m.find())
				s = m.replaceAll("");
			else {
				Log.w("PGN Moves", "Could not find parentheses");
				return false;
			}
		}
		if(s.indexOf(")") >= 0){
			Log.w("PGN Moves", "Still parentheses");
			return false;
		}
		*/
		
		int i;
		s = s.replaceAll("\\$\\d+", ""); // the $x
		//s = s.replaceAll("  ", " ");
		s = s.trim();
		s = s + " "; // for last token
		try{
			
			int pos, numMove = 1, tmp, posDot;
			i = 0;
			
			Matcher matchToken;
			String token, sAnnotation;
			sAnnotation = "";
			while(i < s.length()){
				pos = s.indexOf(" ", i);
				posDot = s.indexOf(".", i);

				token = "";
				if(pos > 0){
					if(s.charAt(i) == '['){
						pos = s.indexOf("]", i);
						if(pos == -1)
							break;
						pos++;
						token = s.substring(i, pos);
						
						i = pos + 1;
					} else if(s.charAt(i) == '{'){
						pos = s.indexOf("}", i);
						if(pos == -1)
							break;
						pos++;
						token = s.substring(i, pos);
						
						i = pos + 1;
					} else if(posDot > 0 && posDot < pos){
						if(s.length() > posDot+3 && s.substring(posDot, posDot + 3).equals("...")){
							i = posDot + 3;
							continue;
						} else {
							posDot++;
							token = s.substring(i, posDot);
							i = posDot;
						}
					} else {
						token = s.substring(i, pos);
						i = pos + 1;
					}
				} else {
					break;
				}
				if(token.equals(".."))
					continue;
				
				matchToken = _patNum.matcher(token);
				if(matchToken.matches()){
					tmp = Integer.parseInt(matchToken.group(1));
					if(tmp == numMove)
						numMove++;
					else
						break;
				} else {
					matchToken = _patAnnot.matcher(token);
					if(matchToken.matches()){
						sAnnotation = matchToken.group(1);
					} else {
						
						matchToken = _patMove.matcher(token);
						if(matchToken.matches()){
							
							if(requestMove(token, matchToken, null, sAnnotation)){
								sAnnotation = "";
							}
							else {
								break;
							}
						} else {
							
							matchToken = _patCastling.matcher(token);
							if(matchToken.matches()){
								
								if(requestMove(token, null, matchToken.group(1), sAnnotation)){
									sAnnotation = "";
								}
								else {
									break;
								}
							} else {
								continue;
							}
						}
					}
				}
			}

			if(sAnnotation.length() != 0){
				setAnnotation(_jni.getNumBoard()-2,  sAnnotation);
			}
			
		} catch(Exception e){
			//System.out.println("@" + e);
			return false;
		}
		
		return true;
	}
	
	public synchronized boolean checkIsLegalMove(int from, int to)
	{
		int checkMove = Move.makeMove(from, to);
		int size = _jni.getMoveArraySize(); int move;
		for(int i = 0; i < size; i++)
		{
			move = _jni.getMoveArrayAt(i);
			if(Move.equalPositions(checkMove, move))
			{
				return true;
			}
		}
		return false;
	}
	
	public synchronized boolean requestMove(String sMove){
		
		int size = _jni.getMoveArraySize(); int move;
		
		String sTmp, sCand = "";
		for(int i = 0; i < size; i++)
		{
			move = _jni.getMoveArrayAt(i);
			_jni.move(move);
			
			sTmp = _jni.getMyMoveToString();
			if(sTmp.equals(sMove))
				return true;

			sCand += sTmp + ",";
			_jni.undo();
		}
		sCand += " != " + sMove;

		return false;
	}
	
	
	protected synchronized final boolean requestMove(final String token, final Matcher matchToken, final String sCastle, final String sAnnotation){
		
		boolean bMatch = false;
		int size = _jni.getMoveArraySize(); int move;
		
		if(sCastle != null){
			for(int i = 0; i < size; i++)
			{
				bMatch = false;
				move = _jni.getMoveArrayAt(i);
				if(Move.isOO(move) && sCastle.equals("O-O")){
					bMatch = true;
				}
				if(Move.isOOO(move) && sCastle.equals("O-O-O")){
					bMatch = true;
				}
				if(bMatch){
					if(move(move, "", false)){
						int numBoard = _jni.getNumBoard()-3;
						if(numBoard >= 0)
							setAnnotation(numBoard,  sAnnotation);
						return true;
					} else {
						return false;
					}
				}
			}
		} else {
			
			
			String sPiece = matchToken.group(1);
			String sDistFile = matchToken.group(2);
			String sDistRank = matchToken.group(3);
			String sTakes = matchToken.group(4);
			String sFile = matchToken.group(5);
			String sRank = matchToken.group(6);
			String sPromote = matchToken.group(7);
			String sCheck = matchToken.group(8);
			String sMark =  matchToken.group(9);
	
			
			
			if(sFile == null){
    			return false;
			}
			if(sRank == null){
				return false;
			}
			int movePiece = BoardConstants.PAWN;
			if(sPiece != null){
				if(sPiece.equals("K"))
					movePiece = BoardConstants.KING;
				else if(sPiece.equals("Q"))
					movePiece = BoardConstants.QUEEN;
				else if(sPiece.equals("R"))
					movePiece = BoardConstants.ROOK;
				else if(sPiece.equals("B"))
					movePiece = BoardConstants.BISHOP;
				else if(sPiece.equals("N"))
					movePiece = BoardConstants.KNIGHT;
				else{
					
					return false;
				}
			}
			
			int moveTo = Pos.fromString(sFile + sRank);
			int from, to, piece, t = _jni.getTurn();
			
			for(int i = 0; i < size; i++)
			{
				bMatch = false;
				move = _jni.getMoveArrayAt(i);
				from = Move.getFrom(move);
				to = Move.getTo(move);
				
				if(sPromote != null){
					piece = Move.getPromotionPiece(move);
					if(false == (sPromote.equals("=Q") && piece == BoardConstants.QUEEN ||
						sPromote.equals("=R") && piece == BoardConstants.ROOK ||
						sPromote.equals("=B") && piece == BoardConstants.BISHOP ||
						sPromote.equals("=N") && piece == BoardConstants.KNIGHT))
						continue;
				}
					
				piece = _jni.pieceAt(t, from);
				
				//if(Move.toDbgString(move).equals("[h5-f5]"))
				//	System.out.println("#");
				
				
				if(piece == movePiece && to == moveTo){
					if(sDistFile != null){
						//System.out.println("#" + sDistFile + " - " + Move.toDbgString(move));
						if(Pos.colToString(from).equals(sDistFile))
							bMatch = true;
					} else if(sDistRank != null){
						if(Pos.rowToString(from).equals(sDistRank))
							bMatch = true;
					} else {
						bMatch = true;
					}
				}
				if(bMatch){

					if(move(move, "", false)){
						int numBoard = _jni.getNumBoard()-3;
						if(numBoard >= 0)
							setAnnotation(numBoard,  sAnnotation);
						return true;
					} else {
						return false;
					}
				}
				
			}
		}	
		return false;
	}
	
	
	// 
	public boolean loadPGN(String s)
	{
		newGame();
		
		loadPGNHead(s);
							
		return loadPGNMoves(s);
	}

	// superclass method, implemented by sub-classes
	public void addPGNEntry(int ply, String sMove, String sAnnotation, int move, boolean bScroll){
		while(ply >= 0 && _arrPGN.size() >= ply)
			_arrPGN.remove(_arrPGN.size()-1);
		
		_arrPGN.add(new PGNEntry(sMove, sAnnotation, move));
	}
	
	public boolean wasMovePlayed(int from, int to){
		int ply = _jni.getNumBoard();
		if(_arrPGN.size() >= ply && ply > 0){
			PGNEntry p = _arrPGN.get(ply-1);
			if(Move.getFrom(p._move) == from && Move.getTo(p._move) == to){
				return true;
			}
		}
		
		return false;
	}
	
	public int getFromOfNextMove(){
		int ply = _jni.getNumBoard();
		if(_arrPGN.size() >= ply && ply > 0){
			PGNEntry p = _arrPGN.get(ply-1);
			return Move.getFrom(p._move);
		}
		return -1;
	}
	
	public void setAnnotation(int i, String sAnno){
		if(_arrPGN.size() > i)
			_arrPGN.get(i)._sAnnotation = sAnno;
	}
	
	public String exportFullPGN(){
		String[] arrHead = {"Event", "Site", "Date", "Round", "White", "Black", "Result", "EventDate", 
				"Variant", "Setup",	"FEN", "PlyCount"};
		
		String s = "", key;
		for(int i = 0; i < arrHead.length;i++){
			key = arrHead[i];
			if(_mapPGNHead.containsKey(key))
				s +=  "[" + key + " \"" + _mapPGNHead.get(key) + "\"]\n";
		}
		
		s += exportMovesPGN();
		s += "\n";
		return s;
	}
	protected String exportMovesPGN(){
		return exportMovesPGNFromPly(1);
	}
	public String exportMovesPGNFromPly(int iPly){
		String s = "";
		if(iPly > 0){
			iPly--;
		}
		if(iPly < 0){
			iPly = 0;
		}
		
		for(int i = iPly; i < _arrPGN.size(); i++){
			if((i-iPly) % 2 == 0)
				s += ((i-iPly)/2 + 1) + ". ";
			s += _arrPGN.get(i)._sMove + " ";
			
			// TODO this was commented? bug?
			if(_arrPGN.get(i)._sAnnotation.length() > 0)
				s += " {" +_arrPGN.get(i)._sAnnotation + "}\n "; 
		}
		
		return s;
	}
	public ArrayList<PGNEntry> getPGNEntries(){
		return _arrPGN;
	}
	////////////////////////////////////////////////////////////////////////////////////
	// superclass methods
	public int getLevel()
	{
		// return arbitrary value, since only overridden methods of sub-classes call this member
		return _selectedLevel;
	}
	public int getLevelMode(){
		return m_iLevelMode;
	}
	public int getLevelPly(){
		return _selectedLevelPly;
	}
	public void setLevelMode(int iLevelMode){
		m_iLevelMode = iLevelMode;
	}
	public void setLevel(int level){
		_selectedLevel = level;
	}
	public void setLevelPly(int iLevelPly){
		_selectedLevelPly = iLevelPly;
	}
	// superclass method, implemented by sub-classes
	public int getPlayMode()
	{
		// return arbitrary value, since only overridden methods of sub-classes call this member
		return HUMAN_PC;
	}
	public void updateState()
	{
		switchTimer();
	}
	public void setMessage(String sText)
	{
	}
	public void setEngineMessage(String sText)
	{
	}
	public void sendMessageFromThread(String sText)
	{
	}
	public void sendMoveMessageFromThread(int move){
	}
	public void sendUCIMoveMessageFromThread(int from, int to, int promo){
	}
	//////////////////////////////////////////////////////////////////////////////////////////
	public void OnDestroy(){
		m_searchThread = null;
		_jni.destroy();
	}
	public void OnResume(){
		/*
		if(_jni.isInited() == 0)
			_jni.init();
		*/
	}
	
	
	public void setPGNHeadProperty(String sProp, String sValue){
		_mapPGNHead.put(sProp, sValue);
	}
	public String getPGNHeadProperty(String sProp){
		return _mapPGNHead.get(sProp);
	}
	public String getWhite(){
		return getPGNHeadProperty("White");
	}
	public String getBlack(){
		return getPGNHeadProperty("Black");
	}
	public void setDateLong(long lTime){
		Calendar cal = Calendar.getInstance();
		cal.setTimeInMillis(lTime);
		Date d = cal.getTime();
		SimpleDateFormat formatter = new SimpleDateFormat("yyyy.MM.dd");
		setPGNHeadProperty("Date", formatter.format(d));
	}
	public Date getDate(){
		String s = getPGNHeadProperty("Date");
		if(s != null){
			Pattern patTag = Pattern.compile("(\\d+)\\.(\\d+)\\.(\\d+)");
			Matcher match = patTag.matcher(s);
			if(match.matches()){
				try{
					SimpleDateFormat formatter = new SimpleDateFormat("yyyy.MM.dd");
					Date utilDate = formatter.parse(s);
					return utilDate;
				} catch(Exception ex){}
			} else {
				
				// in case it's a YYYY.mm.?? format, we make it fst of the month
				patTag = Pattern.compile("(\\d+)\\.(\\d+)\\.(\\?\\?)");
				match = patTag.matcher(s);
				if(match.matches()){
					try{
						s = match.group(1) + "." + match.group(2) + ".01";
						SimpleDateFormat formatter = new SimpleDateFormat("yyyy.MM.dd");
						Date utilDate = formatter.parse(s);
						return utilDate;
					} catch(Exception ex){}
				} else {
					// in case it's a YYYY.??.?? format, we make it January 1
					patTag = Pattern.compile("(\\d+)\\.(\\?\\?)\\.(\\?\\?)");
					match = patTag.matcher(s);
					if(match.matches()){
						try{
							s = match.group(1) + ".01.01";
							SimpleDateFormat formatter = new SimpleDateFormat("yyyy.MM.dd");
							Date utilDate = formatter.parse(s);
							return utilDate;
						} catch(Exception ex){}
					}
				}
			}
		}
		return null;
	}
	/*
	public String getPGNFEN(){
		return getPGNHeadProperty("FEN");
	}*/
}
