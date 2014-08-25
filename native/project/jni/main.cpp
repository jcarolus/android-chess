#include "common.h"

#include <pthread.h>
#include <unistd.h>


#include "ChessBoard.h"
#include "Game.h"

void miniTest();
void unitTest();
void startThread();
void testSpecial();
void testGame();
void testSpeed();
void testSetupMate();
void testSetupCastle();
void testSetupQuiesce();
void testHouse();
void newGame();
void initStuff();

static Game *g;

void *search_thread(void* arg){

    g->search();
}

int main(int argc, char **argv) {

	ChessBoard::initStatics();

        g = new Game();
        testSpecial();

        //miniTest();
       //unitTest();

        /*
        g = new Game();

        g->loadDB("/home/jeroen/db.bin", 3);

        newGame();

        g->searchDB();
         */

        //testSpeed();

        //testGame();

        //sleep(5);
        DEBUG_PRINT("\n\n=== DONE ===\n", 0);

	//delete g;
}

void miniTest()
{
/*
    g = new Game();
    testSetupCastle();

    int m, from, to;
    char buf[1024] = "";
    ChessBoard *board = g->getBoard();
    
    board->toFEN(buf);
    DEBUG_PRINT("\nFEN\n%s\n", buf);

    board->getMoves();

    //DEBUG_PRINT("COL HROOK after getMoves() = %d\n", ChessBoard::COL_HROOK);

    while(board->hasMoreMoves())
    {
       m = board->getNextMove();
       from = Move_getFrom(m);
       to = Move_getTo(m);
       if(Move_isOO(m)){
            DEBUG_PRINT("\n[%d-%d]\n", from, to);
       }
    }
    

    m = Move_makeMoveOO(ChessBoard::f1, ChessBoard::g1);

    DEBUG_PRINT("COL HROOK before # %d\n", ChessBoard::COL_HROOK);
    g->move(m);

    board = g->getBoard();
    board->toFEN(buf);
    DEBUG_PRINT("\nFEN\n%s\n", buf);

    board->printB(buf);
    DEBUG_PRINT("\nB\n%s\n", buf);

    g->reset();

    delete g;

    return;
*/
    g = new Game();
    testSetupQuiesce();
    g->searchLimited(2);
    delete g;
 return;
    g = new Game();
    testSetupMate();
    g->searchLimited(2);

    delete g;

}
void unitTest()
{
    g = new Game();
    testSpecial();
    delete g;
    g = new Game();
    testSetupMate();
    delete g;
    g = new Game();
    testSetupQuiesce();
    delete g;
    g = new Game();
    //testSpeed();
    delete g;
}

void startThread(){
    pthread_t tid;

   DEBUG_PRINT("Creating thread\n", 0);
   pthread_create(&tid, NULL, search_thread, NULL);
   DEBUG_PRINT("Done creatingthread\n", 0);

}

void testSpecial(){

     DEBUG_PRINT("Testing Special position\n", 0);

	char buf[1024] = "", s[255];
	ChessBoard *board = g->getBoard();


        board->put(ChessBoard::f8, ChessBoard::KING, ChessBoard::BLACK);

        board->put(ChessBoard::f5, ChessBoard::KING, ChessBoard::WHITE);
        board->put(ChessBoard::a1, ChessBoard::ROOK, ChessBoard::WHITE);


/*
        board->put(ChessBoard::f8, ChessBoard::ROOK, ChessBoard::BLACK);
	board->put(ChessBoard::h8, ChessBoard::KING, ChessBoard::BLACK);
        board->put(ChessBoard::e7, ChessBoard::QUEEN, ChessBoard::WHITE);
        board->put(ChessBoard::f7, ChessBoard::PAWN, ChessBoard::BLACK);
        board->put(ChessBoard::g7, ChessBoard::QUEEN, ChessBoard::BLACK);

	board->put(ChessBoard::e6, ChessBoard::PAWN, ChessBoard::BLACK);

        board->put(ChessBoard::b5, ChessBoard::PAWN, ChessBoard::BLACK);
        board->put(ChessBoard::d5, ChessBoard::PAWN, ChessBoard::BLACK);
        board->put(ChessBoard::g5, ChessBoard::PAWN, ChessBoard::WHITE);

        board->put(ChessBoard::c4, ChessBoard::PAWN, ChessBoard::BLACK);
        board->put(ChessBoard::f4, ChessBoard::PAWN, ChessBoard::WHITE);

        board->put(ChessBoard::b3, ChessBoard::PAWN, ChessBoard::BLACK);
        board->put(ChessBoard::e3, ChessBoard::PAWN, ChessBoard::WHITE);

        board->put(ChessBoard::d2, ChessBoard::PAWN, ChessBoard::WHITE);

	board->put(ChessBoard::a1, ChessBoard::BISHOP, ChessBoard::WHITE);
        board->put(ChessBoard::b1, ChessBoard::KING, ChessBoard::WHITE);
 */
	

	board->setCastlingsEPAnd50(0, 0, 0, 0, -1, 0);
	//board->setTurn(0);
	g->commitBoard();

	board->toFEN(buf);
	DEBUG_PRINT(buf, 0);

        g->setSearchTime(2);
        g->search(); 


        while(g->m_bSearching){
            DEBUG_PRINT("Main thread sleeping\n", 0);
            sleep(1);
        }

        int m = g->getBestMove();
        g->move(m);


        //g->setSearchTime(1);
        //g->search();

	DEBUG_PRINT("\ndone\n", 0);

    //5r1k/4Qpq1/4p3/1p1p2P1/2p2P2/1p2P3/3P4/BK6 b - -

}

void testGame(){

    g = new Game();
    ChessBoard *board, *tmp = new ChessBoard();

    newGame();
    board = g->getBoard();

    int m, i = 0; boolean bMoved;
    while(!board->isEnded()){
        DEBUG_PRINT("\nEntering loop\n", 0);

        g->setSearchTime(3);
        startThread();
        while(g->m_bSearching){
            DEBUG_PRINT("Main thread sleeping\n", 0);
            sleep(1);
        }

        m = g->getBestMove();

        bMoved = g->move(m);
        board = g->getBoard();

        if(!bMoved){
            DEBUG_PRINT("\nBAILING OUT - not moved\n", 0);
            break;
        }
        #if DEBUG_LEVEL == 1
            char buf[512];
            board->toFEN(buf);
            DEBUG_PRINT("\nFEN\n%s\n", buf);
       

            if(board->getNumBoard() >= 119){
                char buf[2048];
                board->printB(buf);
		DEBUG_PRINT(buf, 0);
            }
        #endif
        DEBUG_PRINT("\n=====> %d, %d\n", board->getNumBoard(), board->getState());

        //if(i++ > 70)
        //    break;
    }
    char buf[512];
    board->toFEN(buf);
    DEBUG_PRINT("\nFEN\n%s\n", buf);

}
void testSpeed(){
	char buf[1024] = "", s[255];

        g = new Game();
        
	ChessBoard *board = g->getBoard();
	
	newGame();
	
	board->toFEN(buf);
	DEBUG_PRINT(buf, 0);
	
	//sprintf(s, "State %d = %d = %d\n", g->getBoard()->getState(), g->getBoard()->isEnded(), g->getBoard()->getNumMoves());
	//DEBUG_PRINT(s);
	
        g->setSearchTime(30);
        g->search();
}

void testSetupMate()
{
       DEBUG_PRINT("Testing mate position\n", 0);
       
	char buf[1024] = "", s[255];
	ChessBoard *board = g->getBoard();
	
	board->put(ChessBoard::h7, ChessBoard::PAWN, ChessBoard::BLACK);
	board->put(ChessBoard::h8, ChessBoard::KING, ChessBoard::BLACK);
	board->put(ChessBoard::g7, ChessBoard::PAWN, ChessBoard::BLACK);
	board->put(ChessBoard::a8, ChessBoard::ROOK, ChessBoard::BLACK);
	//board->put(ChessBoard::b8, ChessBoard::ROOK, ChessBoard::BLACK);
	
	board->put(ChessBoard::b1, ChessBoard::ROOK, ChessBoard::WHITE);
	board->put(ChessBoard::b2, ChessBoard::ROOK, ChessBoard::WHITE);
	board->put(ChessBoard::c1, ChessBoard::KING, ChessBoard::WHITE);
	
	board->setCastlingsEPAnd50(0, 0, 0, 0, -1, 0);
	//board->setTurn(0);
	g->commitBoard();
	
	board->toFEN(buf);
	DEBUG_PRINT(buf, 0);

       
}

void testSetupCastle()
{
    DEBUG_PRINT("Testing castling position\n", 0);
    char buf[1024] = "", s[255];
	ChessBoard *board = g->getBoard();

	board->put(ChessBoard::c8, ChessBoard::KING, ChessBoard::BLACK);
	board->put(ChessBoard::a8, ChessBoard::ROOK, ChessBoard::BLACK);
	//board->put(ChessBoard::b8, ChessBoard::ROOK, ChessBoard::BLACK);

	board->put(ChessBoard::g1, ChessBoard::ROOK, ChessBoard::WHITE);
	board->put(ChessBoard::f1, ChessBoard::KING, ChessBoard::WHITE);

	board->setCastlingsEPAnd50(1, 1, 1, 1, -1, 0);
	//board->setTurn(0);
	g->commitBoard();

        DEBUG_PRINT("COL HROOK after commit = %d\n", ChessBoard::COL_HROOK);

	board->toFEN(buf);
	DEBUG_PRINT(buf, 0);
}

void testSetupQuiesce()
{
	char buf[1024] = "", s[255];
	ChessBoard *board = g->getBoard();
	
	board->put(ChessBoard::d7, ChessBoard::PAWN, ChessBoard::BLACK);
	board->put(ChessBoard::f8, ChessBoard::KING, ChessBoard::BLACK);
	board->put(ChessBoard::c6, ChessBoard::PAWN, ChessBoard::BLACK);
	board->put(ChessBoard::e7, ChessBoard::QUEEN, ChessBoard::BLACK);
	
	
	board->put(ChessBoard::b4, ChessBoard::BISHOP, ChessBoard::WHITE);
	board->put(ChessBoard::c3, ChessBoard::PAWN, ChessBoard::WHITE);
	board->put(ChessBoard::d4, ChessBoard::PAWN, ChessBoard::WHITE);
	board->put(ChessBoard::e5, ChessBoard::PAWN, ChessBoard::WHITE);
	board->put(ChessBoard::d3, ChessBoard::KING, ChessBoard::WHITE);
	
	board->setCastlingsEPAnd50(0, 0, 0, 0, -1, 0);
	board->setTurn(0);
	g->commitBoard();
	
	board->toFEN(buf);
	DEBUG_PRINT(buf, 0);
	

}

void testHouse(){
	
	char buf[1024] = "", s[255];
	
	g->getBoard()->toFEN(buf);
	DEBUG_PRINT(buf, 0);
	
	if(g->putPieceHouse(ChessBoard::e2, ChessBoard::KNIGHT, false))
		DEBUG_PRINT("PUT HOUSE\n", 0);
		
	g->getBoard()->toFEN(buf);
	DEBUG_PRINT(buf, 0);
}

void newGame()
{
	ChessBoard *board = g->getBoard();
	
	board->put(ChessBoard::a8, ChessBoard::ROOK, ChessBoard::BLACK);
		board->put(ChessBoard::b8, ChessBoard::KNIGHT, ChessBoard::BLACK);
		board->put(ChessBoard::c8, ChessBoard::BISHOP, ChessBoard::BLACK);
		board->put(ChessBoard::d8, ChessBoard::QUEEN, ChessBoard::BLACK);
		board->put(ChessBoard::e8, ChessBoard::KING, ChessBoard::BLACK);
		board->put(ChessBoard::f8, ChessBoard::BISHOP, ChessBoard::BLACK);
		board->put(ChessBoard::g8, ChessBoard::KNIGHT, ChessBoard::BLACK);
		board->put(ChessBoard::h8, ChessBoard::ROOK, ChessBoard::BLACK);
		board->put(ChessBoard::a7, ChessBoard::PAWN, ChessBoard::BLACK);
		board->put(ChessBoard::b7, ChessBoard::PAWN, ChessBoard::BLACK);
		board->put(ChessBoard::c7, ChessBoard::PAWN, ChessBoard::BLACK);
		board->put(ChessBoard::d7, ChessBoard::PAWN, ChessBoard::BLACK);
		board->put(ChessBoard::e7, ChessBoard::PAWN, ChessBoard::BLACK);
		board->put(ChessBoard::f7, ChessBoard::PAWN, ChessBoard::BLACK);
		board->put(ChessBoard::g7, ChessBoard::PAWN, ChessBoard::BLACK);
		board->put(ChessBoard::h7, ChessBoard::PAWN, ChessBoard::BLACK);
		
		board->put(ChessBoard::a1, ChessBoard::ROOK, ChessBoard::WHITE);
		board->put(ChessBoard::b1, ChessBoard::KNIGHT, ChessBoard::WHITE);
		board->put(ChessBoard::c1, ChessBoard::BISHOP, ChessBoard::WHITE);
		board->put(ChessBoard::d1, ChessBoard::QUEEN, ChessBoard::WHITE);
		board->put(ChessBoard::e1, ChessBoard::KING, ChessBoard::WHITE);
		board->put(ChessBoard::f1, ChessBoard::BISHOP, ChessBoard::WHITE);
		board->put(ChessBoard::g1, ChessBoard::KNIGHT, ChessBoard::WHITE);
		board->put(ChessBoard::h1, ChessBoard::ROOK, ChessBoard::WHITE);
		board->put(ChessBoard::a2, ChessBoard::PAWN, ChessBoard::WHITE);
		board->put(ChessBoard::b2, ChessBoard::PAWN, ChessBoard::WHITE);
		board->put(ChessBoard::c2, ChessBoard::PAWN, ChessBoard::WHITE);
		board->put(ChessBoard::d2, ChessBoard::PAWN, ChessBoard::WHITE);
		board->put(ChessBoard::e2, ChessBoard::PAWN, ChessBoard::WHITE);
		board->put(ChessBoard::f2, ChessBoard::PAWN, ChessBoard::WHITE);
		board->put(ChessBoard::g2, ChessBoard::PAWN, ChessBoard::WHITE);
		board->put(ChessBoard::h2, ChessBoard::PAWN, ChessBoard::WHITE);
		
		board->setCastlingsEPAnd50(1, 1, 1, 1, -1, 0);
		
		g->commitBoard();
}

void initStuff()
{

        for(int i = 0; i < 64; i++)
        {

            DEBUG_PRINT("%lldLL, ", ChessBoard::ROOK_RANGE[i] | ChessBoard::BISHOP_RANGE[i]);
        }

}