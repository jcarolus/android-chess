#include <jni.h>

//#include <JNIHelp.h>
//#include <android_runtime/AndroidRuntime.h>
#include <pthread.h>

#ifndef _Included_chessJNI
#define _Included_chessJNI

#include "common.h"


#include "ChessBoard.h"
#include "Game.h"

//static void search_thread(void* arg);

int jniRegisterNativeMethods(JNIEnv* env, const char* className,
        const JNINativeMethod* gMethods, int numMethods);

extern "C" {
	

JNIEXPORT void JNICALL Java_jwtc_chess_JNI_destroy( JNIEnv* env, jobject thiz);
JNIEXPORT int JNICALL Java_jwtc_chess_JNI_isInited( JNIEnv* env, jobject thiz);
JNIEXPORT int JNICALL Java_jwtc_chess_JNI_requestMove( JNIEnv* env, jobject thiz, jint from, jint to);
JNIEXPORT int JNICALL Java_jwtc_chess_JNI_move( JNIEnv* env, jobject thiz, jint move);
JNIEXPORT void JNICALL Java_jwtc_chess_JNI_undo( JNIEnv* env, jobject thiz);
JNIEXPORT void JNICALL Java_jwtc_chess_JNI_reset( JNIEnv* env, jobject thiz);
JNIEXPORT void JNICALL Java_jwtc_chess_JNI_putPiece( JNIEnv* env, jobject thiz, jint pos, jint piece, jint turn);
JNIEXPORT void JNICALL Java_jwtc_chess_JNI_searchMove( JNIEnv* env, jobject thiz, jint secs);
JNIEXPORT void JNICALL Java_jwtc_chess_JNI_searchDepth( JNIEnv* env, jobject thiz, jint depth);
JNIEXPORT int JNICALL Java_jwtc_chess_JNI_getMove( JNIEnv* env, jobject thiz);
JNIEXPORT int JNICALL Java_jwtc_chess_JNI_getBoardValue( JNIEnv* env, jobject thiz);
JNIEXPORT int JNICALL Java_jwtc_chess_JNI_peekSearchDone( JNIEnv* env, jobject thiz);
JNIEXPORT int JNICALL Java_jwtc_chess_JNI_peekSearchBestMove( JNIEnv* env, jobject thiz, jint ply);
JNIEXPORT int JNICALL Java_jwtc_chess_JNI_peekSearchBestValue( JNIEnv* env, jobject thiz);
JNIEXPORT int JNICALL Java_jwtc_chess_JNI_peekSearchDepth( JNIEnv* env, jobject thiz);
JNIEXPORT int JNICALL Java_jwtc_chess_JNI_getEvalCount( JNIEnv* env, jobject thiz);
JNIEXPORT void JNICALL Java_jwtc_chess_JNI_setPromo( JNIEnv* env, jobject thiz, jint piece);
JNIEXPORT int JNICALL Java_jwtc_chess_JNI_getState( JNIEnv* env, jobject thiz);
JNIEXPORT int JNICALL Java_jwtc_chess_JNI_isEnded( JNIEnv* env, jobject thiz);
JNIEXPORT void JNICALL Java_jwtc_chess_JNI_setCastlingsEPAnd50( JNIEnv* env, jobject thiz, jint wccl, jint wccs, jint bccl, jint bccs, jint ep, jint r50);
JNIEXPORT int JNICALL Java_jwtc_chess_JNI_getNumBoard( JNIEnv* env, jobject thiz);
JNIEXPORT void JNICALL Java_jwtc_chess_JNI_commitBoard( JNIEnv* env, jobject thiz);
JNIEXPORT void JNICALL Java_jwtc_chess_JNI_setTurn( JNIEnv* env, jobject thiz, jint turn);
//JNIEXPORT jintArray JNICALL Java_jwtc_chess_JNI_getMoveArray(JNIEnv *env, jobject thiz);
JNIEXPORT int JNICALL Java_jwtc_chess_JNI_getMoveArraySize( JNIEnv* env, jobject thiz);
JNIEXPORT int JNICALL Java_jwtc_chess_JNI_getMoveArrayAt( JNIEnv* env, jobject thiz, jint i);
JNIEXPORT int JNICALL Java_jwtc_chess_JNI_getTurn( JNIEnv* env, jobject thiz);
JNIEXPORT int JNICALL Java_jwtc_chess_JNI_pieceAt( JNIEnv* env, jobject thiz, jint turn, jint pos);
JNIEXPORT jstring JNICALL Java_jwtc_chess_JNI_getMyMoveToString( JNIEnv* env, jobject thiz);
JNIEXPORT int JNICALL Java_jwtc_chess_JNI_getMyMove( JNIEnv* env, jobject thiz);
JNIEXPORT int JNICALL Java_jwtc_chess_JNI_isLegalPosition( JNIEnv* env, jobject thiz);
JNIEXPORT int JNICALL Java_jwtc_chess_JNI_isAmbiguousCastle( JNIEnv* env, jobject thiz, jint from, jint to);
JNIEXPORT void JNICALL Java_jwtc_chess_JNI_doCastleMove( JNIEnv* env, jobject thiz, jint from, jint to);
JNIEXPORT jstring JNICALL Java_jwtc_chess_JNI_toFEN( JNIEnv* env, jobject thiz);
JNIEXPORT void JNICALL Java_jwtc_chess_JNI_removePiece( JNIEnv* env, jobject thiz, jint turn, jint pos);
JNIEXPORT BITBOARD JNICALL Java_jwtc_chess_JNI_getHashKey( JNIEnv* env, jobject thiz);
JNIEXPORT void JNICALL Java_jwtc_chess_JNI_loadDB( JNIEnv* env, jobject thiz, jstring sFile, jint depth);
JNIEXPORT void JNICALL Java_jwtc_chess_JNI_interrupt( JNIEnv* env, jobject thiz);
JNIEXPORT int JNICALL Java_jwtc_chess_JNI_getNumCaptured( JNIEnv* env, jobject thiz, jint turn, jint piece);
JNIEXPORT void JNICALL Java_jwtc_chess_JNI_resetHouse( JNIEnv* env, jobject thiz);
JNIEXPORT void JNICALL Java_jwtc_chess_JNI_putPieceHouse( JNIEnv* env, jobject thiz, jint pos, jint piece);
JNIEXPORT int JNICALL Java_jwtc_chess_JNI_getNumHouse( JNIEnv* env, jobject thiz, jint turn, jint piece);
JNIEXPORT void JNICALL Java_jwtc_chess_JNI_setNumHouse( JNIEnv* env, jobject thiz, jint turn, jint piece, jint num);
JNIEXPORT int JNICALL Java_jwtc_chess_JNI_searchMoveHouse( JNIEnv* env, jobject thiz, jint secs);

JNIEXPORT int JNICALL Java_jwtc_chess_JNI_getEvalPropertyCount( JNIEnv* env, jobject thiz);
JNIEXPORT jstring JNICALL Java_jwtc_chess_JNI_getEvalPropertyName( JNIEnv* env, jobject thiz, jint iProp);
JNIEXPORT int JNICALL Java_jwtc_chess_JNI_getEvalPropertyValue( JNIEnv* env, jobject thiz, jint iProp);
JNIEXPORT void JNICALL Java_jwtc_chess_JNI_setEvalPropertyValue( JNIEnv* env, jobject thiz, jint iProp, jint value);

}

#endif

