package jwtc.chess;

public class PGNEntry {

    public String _sMove, _sAnnotation;
    public int _move, _duckMove;

    public PGNEntry(String sM, String sA, int move, int duckMove) {
        _sMove = sM;
        _sAnnotation = sA;
        _move = move;
        _duckMove = duckMove;
    }
}