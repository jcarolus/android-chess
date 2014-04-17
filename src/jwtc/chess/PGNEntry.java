package jwtc.chess;

public class PGNEntry {

	public String _sMove, _sAnnotation;
	public int _move;
	
	public PGNEntry(String sM, String sA, int m){
		_sMove = sM;
		_sAnnotation = sA;
		_move = m;
	}
}