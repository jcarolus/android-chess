package jwtc.android.chess;

import android.content.Intent;

import com.playhub.Consts;
import com.playhub.GameInfoFromPlayHub;
import com.playhub.GameInfoToReturnToPlayHub;

import java.io.UnsupportedEncodingException;
import java.util.HashMap;

import android.view.View;
import android.widget.*;
import android.widget.SeekBar.OnSeekBarChangeListener;

import jwtc.chess.GameControl;
import jwtc.chess.board.ChessBoard;
/**
 *
 */
public class PlayHubChessView extends ChessView {

    private PlayHubActivity playHubActivity;
    private boolean canViewerPlay;

    public PlayHubChessView(PlayHubActivity playHubActivity) {
        super(playHubActivity);
        this.playHubActivity = playHubActivity;

        setAutoFlip(false);
        setShowMoves(true);
        setPlayMode(GameControl.HUMAN_HUMAN);

        SeekBar _seekBar = (SeekBar) playHubActivity.findViewById(jwtc.android.chess.R.id.SeekBarMain);
        if (_seekBar != null) {
            _seekBar.setOnSeekBarChangeListener(new OnSeekBarChangeListener() {

                @Override
                public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                    if (fromUser) {
                        int originalProgress = progress;
                        if (_jni.getNumBoard() - 1 > progress)
                            progress++;

                        PlayHubChessView.this.jumptoMove(progress);
                        if (_arrPGN.size() > originalProgress) {
                            disableControl();
                        } else {
                            if (canViewerPlay) {
                                enableControl();
                            }
                        }
                        PlayHubChessView.this.updateState();
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
            _seekBar.setMax(1);
        }

        ImageButton butNext = (ImageButton) playHubActivity.findViewById(R.id.ButtonNext);
        if (butNext != null) {
            //butNext.setFocusable(false);
            butNext.setOnClickListener(new View.OnClickListener() {
                public void onClick(View arg0) {
                    next();
                    updateEnablity();
                }
            });
            final RelativeLayout _layoutHistory = (RelativeLayout) playHubActivity.findViewById(R.id.LayoutHistory);

            butNext.setOnLongClickListener(new View.OnLongClickListener() {    // Long press takes you to
                @Override                                               // end of game
                public boolean onLongClick(View view) {
                    jumptoMove(_layoutHistory.getChildCount());
                    updateEnablity();
                    return true;
                }
            });

            ImageButton butPrevious = (ImageButton) playHubActivity.findViewById(R.id.ButtonPrevious);
            if (butPrevious != null) {
                //butPrevious.setFocusable(false);
                butPrevious.setOnClickListener(new View.OnClickListener() {
                    public void onClick(View arg0) {
                        previous();
                        updateEnablity();
                    }
                });
                butPrevious.setOnLongClickListener(new View.OnLongClickListener() {
                    @Override
                    public boolean onLongClick(View view) {
                        jumptoMove(1);
                        updateEnablity();
                        return true;
                    }
                });
            }
        }

        TextView _tvClockMe = (TextView) playHubActivity.findViewById(R.id.TextViewClockTimeMe);
        TextView _tvClockOpp = (TextView) playHubActivity.findViewById(R.id.TextViewClockTimeOpp);

        // no clocks in online play
        _tvClockMe.setVisibility(View.GONE);
        _tvClockOpp.setVisibility(View.GONE);
    }

    protected boolean requestMove(int from, int to)
    {
        if (super.requestMove(from, to)) {
            returnToPlayHub();
            return true;
        }
        return false;
    }

    public boolean handleClick(int index)
    {
        //m_textStatus.setText("");
        if (!m_bActive) {
            return false;
        } else {
            return super.handleClick(index);
        }
    }

    void returnToPlayHub() {
        GameInfoFromPlayHub gameInfoFromPlayHub = playHubActivity.getGameInfoFromPlayHub();
        if (gameInfoFromPlayHub.isGameAlreadyFinished || gameInfoFromPlayHub.viewingUserIndex != gameInfoFromPlayHub.currentTurnUserIndex){
            return;
        }

        GameInfoToReturnToPlayHub gameInfoToReturnToPlayHub = new GameInfoToReturnToPlayHub();

        try {
            gameInfoToReturnToPlayHub.innerGameState = exportFullPGN().getBytes("UTF-8");
        } catch (UnsupportedEncodingException e) {
            playHubActivity.finish();
            return;
        }

        gameInfoToReturnToPlayHub.perUserMessages = new HashMap<Integer, String>();

        int state = _jni.getState();

        switch(state)
        {
            case ChessBoard.MATE:
                gameInfoToReturnToPlayHub.gameState = GameInfoToReturnToPlayHub.GameStateToReturnToPlayhub.FINISHED;
                gameInfoToReturnToPlayHub.winnerUserIndex = gameInfoFromPlayHub.viewingUserIndex;
                break;
            case ChessBoard.DRAW_MATERIAL:
                gameInfoToReturnToPlayHub.gameState = GameInfoToReturnToPlayHub.GameStateToReturnToPlayhub.CANCELLED_BY_GAME;
                gameInfoToReturnToPlayHub.perUserMessages.put(0, "Draw over lack of material");
                gameInfoToReturnToPlayHub.perUserMessages.put(1, "Draw over lack of material");
                break;
            case ChessBoard.STALEMATE:
                gameInfoToReturnToPlayHub.gameState = GameInfoToReturnToPlayHub.GameStateToReturnToPlayhub.CANCELLED_BY_GAME;
                gameInfoToReturnToPlayHub.perUserMessages.put(0, "Draw over stalemate");
                gameInfoToReturnToPlayHub.perUserMessages.put(1, "Draw over stalemate");
                break;
            case ChessBoard.DRAW_50:
                gameInfoToReturnToPlayHub.gameState = GameInfoToReturnToPlayHub.GameStateToReturnToPlayhub.CANCELLED_BY_GAME;
                gameInfoToReturnToPlayHub.perUserMessages.put(0, "Draw over 50-move rule");
                gameInfoToReturnToPlayHub.perUserMessages.put(1, "Draw over 50-move rule");
                break;
            case ChessBoard.DRAW_REPEAT:
                gameInfoToReturnToPlayHub.gameState = GameInfoToReturnToPlayHub.GameStateToReturnToPlayhub.CANCELLED_BY_GAME;
                gameInfoToReturnToPlayHub.perUserMessages.put(0, "Draw over 3-fold repetition");
                gameInfoToReturnToPlayHub.perUserMessages.put(1, "Draw over 3-fold repetition");
                break;
            default:
                gameInfoToReturnToPlayHub.gameState = GameInfoToReturnToPlayHub.GameStateToReturnToPlayhub.RUNNING;
        }

        gameInfoToReturnToPlayHub.currentTurnPlayerUserIndex = 1 - gameInfoFromPlayHub.currentTurnUserIndex;

        Intent output = new Intent();
        output.putExtra(Consts.GAME_INFO_TO_RETURN_TO_PLAYHUB_KEY, gameInfoToReturnToPlayHub);
        playHubActivity.setResult(-1, output);
        playHubActivity.finish();
    }

    public void setCanViewerPlay(boolean canViewerPlay) {
        this.canViewerPlay = canViewerPlay;
        if (!canViewerPlay) {
            disableControl();
        }
    }

    public void updateEnablity() {
        if (_arrPGN.size() > _jni.getNumBoard() - 1) {
            disableControl();
        } else {
            if (canViewerPlay) {
                enableControl();
            }
        }
        PlayHubChessView.this.updateState();
    }
}
