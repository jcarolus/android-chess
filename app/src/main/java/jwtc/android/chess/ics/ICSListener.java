package jwtc.android.chess.ics;

import java.util.ArrayList;
import java.util.HashMap;

public interface ICSListener {
    void OnLoginSuccess();
    void OnLoginFailed(String error);
    void OnLoggingIn();
    void OnSessionEnded();
    void OnError();
    void OnPlayerList(ArrayList<HashMap<String,String>> playerList);
    void OnBoardUpdated(String gameLine, String handle);
    void OnChallenged(HashMap<String, String> challenge);
    void OnIllegalMove();
    void OnSeekNotAvailable();
    void OnPlayGameStarted(String whiteHandle, String blackHandle, String whiteRating, String blackRating);
    void OnGameNumberUpdated(int number);
    void OnOpponentRequestsAbort();
    void OnOpponentRequestsAdjourn();
    void OnOpponentOffersDraw();
    void OnOpponentRequestsTakeBack();
    void OnAbortConfirmed();
    void OnPlayGameResult(String message);
    void OnPlayGameStopped();
    void OnYourRequestSended();
    void OnChatReceived();
    void OnResumingAdjournedGame();
    void OnAbortedOrAdjourned();
    void OnObservingGameStarted();
    void OnObservingGameStopped();
    void OnPuzzleStarted();
    void OnPuzzleStopped();
    void OnExaminingGameStarted();
    void OnExaminingGameStopped();
    void OnSoughtResult(ArrayList<HashMap<String, String>> soughtList);
    void OnGameListResult(ArrayList<HashMap<String, String>> games);
    void OnStoredListResult(ArrayList<HashMap<String, String>> games);
    void OnEndGameResult(int state);
    void OnConsoleOutput(String buffer);

}
