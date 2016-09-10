package jwtc.android.chess;

import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.lang.ref.WeakReference;
import java.util.ArrayList;
import java.util.Timer;
import java.util.TimerTask;

import org.json.JSONArray;
import org.json.JSONObject;

import jwtc.chess.*;
import jwtc.chess.board.BoardConstants;
import jwtc.chess.board.BoardMembers;
import jwtc.chess.board.ChessBoard;

import android.app.Activity;
import android.app.AlertDialog;
import android.os.Handler;
import android.os.Message;
import android.os.Vibrator;
import android.util.Log;

import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.View;
import android.view.View.*;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.SharedPreferences;
import android.view.ViewTreeObserver;
import android.widget.*;
import android.widget.SeekBar.OnSeekBarChangeListener;

/**
 *
 */
public class ChessView extends UI {

    public static final String TAG = "ChessView";

    private ChessViewBase _view;

    private ChessActivity _parent;
    private ImageButton _butPlay, butQuickSoundOn, butQuickSoundOff;
    private ViewAnimator _viewAnimator;
    private ProgressBar _progressPlay;
    private TextView _tvClockMe, _tvClockOpp, _tvTitleMe, _tvTitleOpp, _tvAnnotate, _tvEngine, _tvAnnotateGuess;
    private int _dpadPos;
    private int _playMode;
    private String _sPrevECO;
    private HorizontalScrollView _hScrollHistory;
    private ScrollView _vScrollHistory;
    private RelativeLayout _layoutHistory;
    private ArrayList<PGNView> _arrPGNView;
    private LayoutInflater _inflater;
    private boolean _bAutoFlip, _bShowMoves, _bShowLastMove, _bPlayAsBlack, _bDidResume, _bPlayVolume;
    private Timer _timer;
    private ViewSwitcher _switchTurnMe, _switchTurnOpp;
    private SeekBar _seekBar;
    private Vibrator _vibrator;
    private ImageView _imgStatusGuess;
    private JSONArray _jArrayECO;

    // keep track of captured pieces
    private CapturedImageView[][] _arrImageCaptured;
    private TextView[][] _arrTextCaptured;
    ///////////////////////////////

    public static int SUBVIEW_CPU = 0;
    public static int SUBVIEW_CAPTURED = 1;
    public static int SUBVIEW_SEEK = 2;
    public static int SUBVIEW_HISTORY = 3;
    public static int SUBVIEW_ANNOTATE = 4;
    public static int SUBVIEW_GUESS = 5;
    public static int SUBVIEW_BLINDFOLD = 6;
    public static int SUBVIEW_ECO = 7;

    static class InnerHandler extends Handler {
        WeakReference<ChessView> _chessView;

        InnerHandler(ChessView view) {
            _chessView = new WeakReference<ChessView>(view);
        }

        @Override
        public void handleMessage(Message msg) {

            ChessView chessView = _chessView.get();
            if (chessView != null) {
                long lTmp;
                if (chessView._view._flippedBoard) {
                    lTmp = chessView.getBlackRemainClock();
                } else {
                    lTmp = chessView.getWhiteRemainClock();
                }
                if (lTmp < 0) {
                    lTmp = -lTmp;
                    chessView._tvClockMe.setTextColor(0xffff0000); // red
                } else {
                    chessView._tvClockMe.setTextColor(0xffffffff); // white
                }
                chessView._tvClockMe.setText(chessView.formatTime(lTmp));

                if (chessView._view._flippedBoard) {
                    lTmp = chessView.getWhiteRemainClock();
                } else {
                    lTmp = chessView.getBlackRemainClock();
                }
                if (lTmp < 0) {
                    lTmp = -lTmp;
                    chessView._tvClockOpp.setTextColor(0xffff0000);
                } else {
                    chessView._tvClockOpp.setTextColor(0xffffffff);
                }
                chessView._tvClockOpp.setText(chessView.formatTime(lTmp));
            }
        }
    }

    protected InnerHandler m_timerHandler = new InnerHandler(this);


    public ChessView(Activity activity) {
        super();
        _parent = (ChessActivity) activity;
        _view = new ChessViewBase(activity);

        _playMode = HUMAN_PC;
        _bAutoFlip = false;
        _bPlayAsBlack = false;
        _bShowMoves = false;
        _bShowLastMove = true;
        _bDidResume = false;
        _dpadPos = -1;

        _arrPGNView = new ArrayList<PGNView>();

        _inflater = (LayoutInflater) _parent.getSystemService(Activity.LAYOUT_INFLATER_SERVICE);

        OnClickListener ocl = new OnClickListener() {
            public void onClick(View arg0) {
                handleClick(_view.getIndexOfButton(arg0));
            }
        };

        OnLongClickListener olcl = new OnLongClickListener() {
            @Override
            public boolean onLongClick(View view) {
                handleClick(_view.getIndexOfButton(view));
                // Long press is ready for the future.
                // For example:  confirm move or to move pieces around and then go back to original position
                return true;
            }
        };

        butQuickSoundOn = (ImageButton) activity.findViewById(R.id.ButtonICSSoundOn);
        butQuickSoundOff = (ImageButton) activity.findViewById(R.id.ButtonICSSoundOff);
        if (butQuickSoundOn != null && butQuickSoundOff != null) {
            butQuickSoundOn.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View view) {
                    _bPlayVolume = false;
                    _parent.set_fVolume(0.0f);
                    butQuickSoundOn.setVisibility(View.GONE);
                    butQuickSoundOff.setVisibility(View.VISIBLE);
                }
            });

            butQuickSoundOff.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View view) {
                    _bPlayVolume = true;
                    _parent.set_fVolume(1.0f);
                    butQuickSoundOff.setVisibility(View.GONE);
                    butQuickSoundOn.setVisibility(View.VISIBLE);
                }
            });
        }

        _vScrollHistory = null;
        _hScrollHistory = null;
        _layoutHistory = null;

        _view.init(ocl, olcl);

        //_vibrator = (Vibrator)activity.getSystemService(Context.VIBRATOR_SERVICE);
        _vibrator = null;

        _jArrayECO = null;
        // below was previously in init() method
        _hScrollHistory = (HorizontalScrollView) _parent.findViewById(R.id.HScrollViewHistory);
        _layoutHistory = (RelativeLayout) _parent.findViewById(R.id.LayoutHistory);
        _vScrollHistory = (ScrollView) _parent.findViewById(R.id.VScrollViewHistory);

        _butPlay = (ImageButton) _parent.findViewById(R.id.ButtonPlay);
        //_butPlay.setFocusable(false);
        if (_butPlay != null) {
            _butPlay.setOnClickListener(new OnClickListener() {
                public void onClick(View arg0) {
                    if (m_bActive) {
                        //Log.i("butPlay", _jni.getNumBoard() + "::" + _arrPGN.size());

                        if (_jni.getNumBoard() < _arrPGN.size()) {
                            AlertDialog.Builder builder = new AlertDialog.Builder(_parent)
                                    .setTitle(_parent.getString(R.string.title_create_new_line))
                                    .setNegativeButton(R.string.alert_no, new DialogInterface.OnClickListener() {
                                        public void onClick(DialogInterface dialog, int which) {
                                            dialog.dismiss();
                                        }
                                    });
                            builder.setPositiveButton(R.string.alert_yes, new DialogInterface.OnClickListener() {
                                public void onClick(DialogInterface dialog, int which) {
                                    dialog.dismiss();
                                    play();
                                }
                            });
                            AlertDialog alert = builder.create();
                            alert.show();
                        } else {
                            play();
                        }
                    }
                }
            });
        }

        OnClickListener oclUndo = new OnClickListener() {
            public void onClick(View arg0) {
                if (m_bActive) {
                    previous();
                } else {
                    stopThreadAndUndo();
                }
            }
        };

        OnLongClickListener olclUndo = new OnLongClickListener() {    // Long press takes you back to
            @Override                                                 // beginning of game
            public boolean onLongClick(View view) {
                jumptoMove(1);
                updateState();
                return true;
            }
        };
        /*
        ImageButton butUndo = (ImageButton)_parent.findViewById(R.id.ButtonUndo);
		if(butUndo != null){
			//butUndo.setFocusable(false);
			butUndo.setOnClickListener(oclUndo);
		}
		*/
        ImageButton butPrevious = (ImageButton) _parent.findViewById(R.id.ButtonPrevious);
        if (butPrevious != null) {
            //butPrevious.setFocusable(false);
            butPrevious.setOnClickListener(oclUndo);
            butPrevious.setOnLongClickListener(olclUndo);
        }
        /*
        ImageButton butPreviousGuess = (ImageButton)_parent.findViewById(R.id.ButtonPreviousGuess);
		if(butPreviousGuess != null){
			//butPreviousGuess.setFocusable(false);
			butPreviousGuess.setOnClickListener(oclUndo);
		}
		*/
        OnClickListener oclFf = new OnClickListener() {
            public void onClick(View arg0) {
                if (m_bActive) {
                    next();
                }

            }
        };
        OnLongClickListener olclFf = new OnLongClickListener() {    // Long press takes you to
            @Override                                               // end of game
            public boolean onLongClick(View view) {
                jumptoMove(_layoutHistory.getChildCount());
                updateState();
                return true;
            }
        };

        ImageButton butNext = (ImageButton) _parent.findViewById(R.id.ButtonNext);
        if (butNext != null) {
            //butNext.setFocusable(false);
            butNext.setOnClickListener(oclFf);
            butNext.setOnLongClickListener(olclFf);
        }
        /*
		ImageButton butNextGuess = (ImageButton)_parent.findViewById(R.id.ButtonNextGuess);
		if(butNextGuess != null){
			//butNextGuess.setFocusable(false);
			butNextGuess.setOnClickListener(oclFf);
		}
		*/
		/*
		ImageButton butFastForward = (ImageButton)_parent.findViewById(R.id.ButtonFastForward);
		if(butFastForward != null){
			//butFastForward.setFocusable(false);
			butFastForward.setOnClickListener(new OnClickListener() {
	        	public void onClick(View arg0) {
	        		if(m_bActive){
	        			jumptoMove(_arrPGN.size());
	        			updateState();
	        			scrollToEnd();
	        		}
	        	}
	    	});
		}
		ImageButton butRewind = (ImageButton)_parent.findViewById(R.id.ButtonRewind);
		if(butRewind != null){
			//butRewind.setFocusable(false);
			butRewind.setOnClickListener(new OnClickListener() {
	        	public void onClick(View arg0) {
	        		if(m_bActive){
	        			jumptoMove(1);
	        			updateState();
	        			scrollToStart();
	        		}
	        	}
	    	});
		}
		ImageButton butBack = (ImageButton)_parent.findViewById(R.id.ButtonBack);
		if(butBack != null){
			butBack.setOnClickListener(new OnClickListener() {
	        	public void onClick(View arg0) {
	        		_parent.finish();
	        	}
	    	});
		}
		ImageButton butMenu = (ImageButton)_parent.findViewById(R.id.ButtonMenu);
		if(butMenu != null){
			butMenu.setOnClickListener(new OnClickListener() {
	        	public void onClick(View arg0) {
	        		_parent.showMenu();
	        	}
	    	});
		}
		*/
		
		/*
		ImageButton butClockMenu = (ImageButton)_parent.findViewById(R.id.ButtonClockMenu);
		if(butClockMenu != null){
			//butClockMenu.setFocusable(false);
			butClockMenu.setOnClickListener(new OnClickListener() {
	        	public void onClick(View arg0) {
	        		final String[] itemsMenu = new String[]{"no clock", "2 minutes", "5 minutes", "10 minutes", "30 minutes", "60 minutes"};
	    			
	        		AlertDialog.Builder builder = new AlertDialog.Builder(_parent);
	    			builder.setTitle(_parent.getString(R.string.title_menu));
	    			
	    			builder.setItems(itemsMenu, new DialogInterface.OnClickListener() {
	    			    public void onClick(DialogInterface dialog, int item) {
	    			        dialog.dismiss();
	    			        if(item == 0)
	    			        	_lClockTotal = 0;
	    			        else if(item == 1)
	    			        	_lClockTotal = 120000;
	    			        else if(item == 2)
	    			        	_lClockTotal = 300000;
	    			        else if(item == 3)
	    			        	_lClockTotal = 600000;
	    			        else if(item == 4)
	    			        	_lClockTotal = 1800000;
	    			        else if(item == 5)
	    			        	_lClockTotal = 3600000;
	    			        resetTimer();
	    			    }
	    			});
	    			AlertDialog alert = builder.create();
	    			alert.show();
	        	}
			});
		}
		*/
		/*
		ImageButton butClockPause = (ImageButton)_parent.findViewById(R.id.ButtonClockPause);
		if(butClockPause != null){
			//butClockPause.setFocusable(false);
			butClockPause.setOnClickListener(new OnClickListener() {
	        	public void onClick(View arg0) {
	        		View v = (View)_parent.findViewById(R.id.includeboard);
	        		if(pauseOrContinueTimer()){
	        			v.setVisibility(View.INVISIBLE);
	        		} else {
	        			v.setVisibility(View.VISIBLE);
	        		}
	        	}
			});
		}
		*/

        Button butNewGame = (Button) _parent.findViewById(R.id.ButtonNewGame);
        if (butNewGame != null) {
            //butNewGame.setFocusable(false);
            butNewGame.setOnClickListener(new OnClickListener() {
                public void onClick(View arg0) {
                    Intent intent = new Intent();
                    intent.setClass(_parent, options.class);
                    intent.putExtra("requestCode", main.REQUEST_NEWGAME);
                    _parent.startActivityForResult(intent, main.REQUEST_NEWGAME);
                }
            });
        }

        ImageButton butShowMenu = (ImageButton) _parent.findViewById(R.id.ButtonShowMenu);
        if (butShowMenu != null) {
            butShowMenu.setOnClickListener(new OnClickListener() {
                public void onClick(View arg0) {

                    _parent.openOptionsMenu();
                }
            });
        }

        ImageButton butSaveGame = (ImageButton) _parent.findViewById(R.id.ButtonSave);
        if (butSaveGame != null) {
            //butSaveGame.setFocusable(false);
            butSaveGame.setOnClickListener(new OnClickListener() {
                public void onClick(View arg0) {

                    _parent.saveGame();
                }
            });
        }

        ImageButton butOpenGame = (ImageButton) _parent.findViewById(R.id.ButtonOpen);
        if (butOpenGame != null) {
            //butOpenGame.setFocusable(false);
            butOpenGame.setOnClickListener(new OnClickListener() {
                public void onClick(View arg0) {
                    Intent intent = new Intent();
                    intent.setClass(_parent, GamesListView.class);
                    _parent.startActivityForResult(intent, main.REQUEST_OPEN);
                }
            });
        }

        ImageButton butFlipBoard = (ImageButton) _parent.findViewById(R.id.ButtonFlipBoard);
        if (butFlipBoard != null) {
            butFlipBoard.setOnClickListener((new OnClickListener() {
                @Override
                public void onClick(View view) {
                    flipBoard();
                }
            }));
        }

        //
		/*
		_butBlindFold = (ImageButton)_parent.findViewById(R.id.ButtonBlindfold);
		if(_butBlindFold != null){
			//_butBlindFold.setFocusable(false);
			_butBlindFold.setOnClickListener(new OnClickListener() {
	        	public void onClick(View arg0) {
	        		if(_view.getBlindfoldMode() == 0){
	        			_view.setBlindfoldMode(ChessViewBase.MODE_BLINDFOLD_HIDEPIECES);
	        			_butBlindFold.setImageResource(R.drawable.navigation_accept);
	        		} else {
	        			_view.setBlindfoldMode(0);
	        			_butBlindFold.setImageResource(R.drawable.navigation_cancel);
	        		}
	        		_view.resetImageCache();
	        		updateState();
	        	}
			});
		}
		*/
        ImageButton butBlindFoldShow = (ImageButton) _parent.findViewById(R.id.ButtonBlindfoldShow);
        if (butBlindFoldShow != null) {
            //butBlindFoldShow.setFocusable(false);
            butBlindFoldShow.setOnClickListener(new OnClickListener() {
                public void onClick(View arg0) {
                    _view.setBlindfoldMode(0);
                    _view.resetImageCache();
                    updateState();
                }
            });
        }
        ImageButton butBlindFoldHide = (ImageButton) _parent.findViewById(R.id.ButtonBlindfoldHide);
        if (butBlindFoldHide != null) {
            //butBlindFoldHide.setFocusable(false);
            butBlindFoldHide.setOnClickListener(new OnClickListener() {
                public void onClick(View arg0) {
                    _view.setBlindfoldMode(ChessViewBase.MODE_BLINDFOLD_HIDEPIECES);
                    _view.resetImageCache();
                    updateState();
                }
            });
        }
        ImageButton butBlindFoldLocations = (ImageButton) _parent.findViewById(R.id.ButtonBlindfoldLocations);
        if (butBlindFoldLocations != null) {
            //butBlindFoldLocations.setFocusable(false);
            butBlindFoldLocations.setOnClickListener(new OnClickListener() {
                public void onClick(View arg0) {
                    _view.setBlindfoldMode(ChessViewBase.MODE_BLINDFOLD_SHOWPIECELOCATION);
                    _view.resetImageCache();
                    updateState();
                }
            });
        }


        Button bHintGuess = (Button) _parent.findViewById(R.id.ButtonHintGuess);
        if (bHintGuess != null) {
            //bHintGuess.setFocusable(false);
            bHintGuess.setOnClickListener(new OnClickListener() {

                @Override
                public void onClick(View v) {
                    int iFrom = getFromOfNextMove();
                    if (iFrom == -1) {
                        _tvAnnotateGuess.setText("No move available");
                    } else {
                        m_iFrom = iFrom;
                        paintBoard();
                    }
                }

            });
        }

        _tvClockMe = (TextView) _parent.findViewById(R.id.TextViewClockTimeMe);
        _tvClockOpp = (TextView) _parent.findViewById(R.id.TextViewClockTimeOpp);
        _tvTitleMe = (TextView) _parent.findViewById(R.id.TextViewTitle);
        _tvTitleOpp = (TextView) _parent.findViewById(R.id.TextViewTopTitle);
        _tvEngine = (TextView) _parent.findViewById(R.id.TextViewEngine);
        //_tvEngineValue = (TextView)_parent.findViewById(R.id.TextViewEngineValue);

        _imgStatusGuess = (ImageView) _parent.findViewById(R.id.ImageStatusGuess);

        _switchTurnMe = (ViewSwitcher) _parent.findViewById(R.id.ImageTurnMe);
        _switchTurnOpp = (ViewSwitcher) _parent.findViewById(R.id.ImageTurnOpp);

        ImageButton butSwitch = (ImageButton) _parent.findViewById(R.id.ButtonSwitch);
        if (butSwitch != null) {
            //butSwitch.setFocusable(false);
            butSwitch.setOnClickListener(new OnClickListener() {
                public void onClick(View arg0) {
                    if (_viewAnimator.getChildCount() >= 6) {
                        _parent.showSubViewMenu();
                    } else {
                        toggleControls();
                    }
                }
            });
        }

        _tvAnnotate = (TextView) _parent.findViewById(R.id.TextViewAnnotate);
        if (_tvAnnotate != null) {
            _tvAnnotate.setOnClickListener(new OnClickListener() {
                public void onClick(View arg0) {

                    final int i = _jni.getNumBoard() - 2;
                    if (i > 0) {
                        final FrameLayout fl = new FrameLayout(_parent);
                        final EditText input = new EditText(_parent);
                        input.setGravity(Gravity.CENTER);
                        input.setText(_arrPGN.get(i)._sAnnotation);

                        fl.addView(input, new FrameLayout.LayoutParams(FrameLayout.LayoutParams.FILL_PARENT, FrameLayout.LayoutParams.WRAP_CONTENT));

                        AlertDialog.Builder builder = new AlertDialog.Builder(_parent)
                                .setView(fl)
                                .setTitle(_parent.getString(R.string.title_annotate) + " " + _arrPGN.get(i)._sMove)
                                .setPositiveButton(R.string.button_ok, new DialogInterface.OnClickListener() {
                                    public void onClick(DialogInterface dialog, int which) {
                                        String s = input.getText().toString();
                                        s = s.replaceAll("[\\{\\}]+", "");
                                        _arrPGN.get(i)._sAnnotation = s;
                                        _tvAnnotate.setText(s);
                                        _arrPGNView.get(i).setAnnotated(s.length() > 0);

                                        dialog.dismiss();
                                    }

                                });
                        AlertDialog alert = builder.create();
                        alert.show();
                    }
                }
            });
        }
        _tvAnnotateGuess = (TextView) _parent.findViewById(R.id.TextViewGuess);

        _viewAnimator = (ViewAnimator) _parent.findViewById(R.id.ViewAnimatorMain);
        if (_viewAnimator != null) {
            _viewAnimator.setOutAnimation(_parent, R.anim.slide_left);
            _viewAnimator.setInAnimation(_parent, R.anim.slide_right);
        }
        _progressPlay = (ProgressBar) _parent.findViewById(R.id.ProgressBarPlay);

        _seekBar = (SeekBar) _parent.findViewById(R.id.SeekBarMain);
        if (_seekBar != null) {
            _seekBar.setOnSeekBarChangeListener(new OnSeekBarChangeListener() {

                @Override
                public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                    if (fromUser) {

                        if (_jni.getNumBoard() - 1 > progress)
                            progress++;

                        ChessView.this.jumptoMove(progress);
                        ChessView.this.updateState();
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

        _arrImageCaptured = new CapturedImageView[2][5];
        _arrImageCaptured[BoardConstants.BLACK][BoardConstants.PAWN] = (CapturedImageView) _parent.findViewById(R.id.ImageCapturedBlackPawn);
        _arrImageCaptured[BoardConstants.BLACK][BoardConstants.PAWN].initBitmap("pb.png");
        _arrImageCaptured[BoardConstants.BLACK][BoardConstants.KNIGHT] = (CapturedImageView) _parent.findViewById(R.id.ImageCapturedBlackKnight);
        _arrImageCaptured[BoardConstants.BLACK][BoardConstants.KNIGHT].initBitmap("nb.png");
        _arrImageCaptured[BoardConstants.BLACK][BoardConstants.BISHOP] = (CapturedImageView) _parent.findViewById(R.id.ImageCapturedBlackBishop);
        _arrImageCaptured[BoardConstants.BLACK][BoardConstants.BISHOP].initBitmap("bb.png");
        _arrImageCaptured[BoardConstants.BLACK][BoardConstants.ROOK] = (CapturedImageView) _parent.findViewById(R.id.ImageCapturedBlackRook);
        _arrImageCaptured[BoardConstants.BLACK][BoardConstants.ROOK].initBitmap("rb.png");
        _arrImageCaptured[BoardConstants.BLACK][BoardConstants.QUEEN] = (CapturedImageView) _parent.findViewById(R.id.ImageCapturedBlackQueen);
        _arrImageCaptured[BoardConstants.BLACK][BoardConstants.QUEEN].initBitmap("qb.png");
        _arrImageCaptured[BoardConstants.WHITE][BoardConstants.PAWN] = (CapturedImageView) _parent.findViewById(R.id.ImageCapturedWhitePawn);
        _arrImageCaptured[BoardConstants.WHITE][BoardConstants.PAWN].initBitmap("pw.png");
        _arrImageCaptured[BoardConstants.WHITE][BoardConstants.KNIGHT] = (CapturedImageView) _parent.findViewById(R.id.ImageCapturedWhiteKnight);
        _arrImageCaptured[BoardConstants.WHITE][BoardConstants.KNIGHT].initBitmap("nw.png");
        _arrImageCaptured[BoardConstants.WHITE][BoardConstants.BISHOP] = (CapturedImageView) _parent.findViewById(R.id.ImageCapturedWhiteBishop);
        _arrImageCaptured[BoardConstants.WHITE][BoardConstants.BISHOP].initBitmap("bw.png");
        _arrImageCaptured[BoardConstants.WHITE][BoardConstants.ROOK] = (CapturedImageView) _parent.findViewById(R.id.ImageCapturedWhiteRook);
        _arrImageCaptured[BoardConstants.WHITE][BoardConstants.ROOK].initBitmap("rw.png");
        _arrImageCaptured[BoardConstants.WHITE][BoardConstants.QUEEN] = (CapturedImageView) _parent.findViewById(R.id.ImageCapturedWhiteQueen);
        _arrImageCaptured[BoardConstants.WHITE][BoardConstants.QUEEN].initBitmap("qw.png");

        _arrTextCaptured = new TextView[2][5];
        _arrTextCaptured[BoardConstants.BLACK][BoardConstants.PAWN] = (TextView) _parent.findViewById(R.id.TextViewCapturedBlackPawn);
        _arrTextCaptured[BoardConstants.BLACK][BoardConstants.KNIGHT] = (TextView) _parent.findViewById(R.id.TextViewCapturedBlackKnight);
        _arrTextCaptured[BoardConstants.BLACK][BoardConstants.BISHOP] = (TextView) _parent.findViewById(R.id.TextViewCapturedBlackBishop);
        _arrTextCaptured[BoardConstants.BLACK][BoardConstants.ROOK] = (TextView) _parent.findViewById(R.id.TextViewCapturedBlackRook);
        _arrTextCaptured[BoardConstants.BLACK][BoardConstants.QUEEN] = (TextView) _parent.findViewById(R.id.TextViewCapturedBlackQueen);
        _arrTextCaptured[BoardConstants.WHITE][BoardConstants.PAWN] = (TextView) _parent.findViewById(R.id.TextViewCapturedWhitePawn);
        _arrTextCaptured[BoardConstants.WHITE][BoardConstants.KNIGHT] = (TextView) _parent.findViewById(R.id.TextViewCapturedWhiteKnight);
        _arrTextCaptured[BoardConstants.WHITE][BoardConstants.BISHOP] = (TextView) _parent.findViewById(R.id.TextViewCapturedWhiteBishop);
        _arrTextCaptured[BoardConstants.WHITE][BoardConstants.ROOK] = (TextView) _parent.findViewById(R.id.TextViewCapturedWhiteRook);
        _arrTextCaptured[BoardConstants.WHITE][BoardConstants.QUEEN] = (TextView) _parent.findViewById(R.id.TextViewCapturedWhiteQueen);

        _selectedLevel = 3;

        _lClockStartWhite = 0;
        _lClockStartBlack = 0;
        _lClockTotal = 0;


        _timer = new Timer(true);
        _timer.schedule(new TimerTask() {
            @Override
            public void run() {
                Message msg = new Message();
                msg.what = 1;
                m_timerHandler.sendMessage(msg);
            }
        }, 1000, 1000);

        _sPrevECO = null;


    }

    protected void next() {
        jumptoMove(_jni.getNumBoard());
        playNotification();
        updateState();
    }

    protected void previous() {
        undo();
        playNotification();
    }

    private String formatTime(long msec) {
        final String sTmp = String.format("%02d:%02d", (int) (Math.floor(msec / 60000)), ((int) (msec / 1000) % 60));
        return sTmp;
    }

    public void toggleControls() {

        if (_viewAnimator != null) {
            _viewAnimator.showNext();
        }
    }

    public void toggleControl(int i) {
        if (_viewAnimator != null) {
            _viewAnimator.setDisplayedChild(i);
        }
    }

    public void setAutoFlip(boolean b) {
        _bAutoFlip = b;
    }

    public void setShowMoves(boolean b) {
        _bShowMoves = b;
    }

    public boolean getAutoFlip() {
        return _bAutoFlip;
    }

    public boolean getShowMoves() {
        return _bShowMoves;
    }

    public void onClickPGNView(PGNView item) {
        if (m_bActive) {
            int i = _arrPGNView.indexOf(item);
            Log.i("onClickPGNView", "index " + i);
            if (_jni.getNumBoard() - 1 > i)
                jumptoMove(i + 2);
            else
                jumptoMove(i + 1);

            //if(_arrPGN.get(i)._sAnnotation.length() > 0){
            //_parent.doToast(_arrPGN.get(i)._sMove + " :" + _arrPGN.get(i)._sAnnotation);

            //}
            updateState();
        } else {
            //TODO toast pleas wait
        }
    }

    public void onLongClickPGNView(PGNView item) {

    }

    public void clearPGNView() {
        _arrPGNView.clear();
        if (_layoutHistory != null) {
            _layoutHistory.removeAllViews();
        }
        updateState();
    }

    @Override
    public void newGame() {
        super.newGame();
        clearPGNView();
    }

    @Override
    public int newGameRandomFischer(int seed) {

        int ret = super.newGameRandomFischer(seed);
        clearPGNView();

        return ret;
    }

    @Override
    public void addPGNEntry(int ply, String sMove, String sAnnotation, int move, boolean bScroll) {
        super.addPGNEntry(ply, sMove, sAnnotation, move, bScroll);
        //Log.i("ChessView", "sMove =  " + sMove);

        if (_bDidResume) {
            playNotification();
        }

        while (ply >= 0 && _arrPGNView.size() >= ply)
            _arrPGNView.remove(_arrPGN.size() - 1);

        View v = _inflater.inflate(R.layout.pgn_item, null, false);
        v.setId(ply);
        _arrPGNView.add(new PGNView(this, v, ply, sMove, sAnnotation.length() > 0));

        if (_layoutHistory != null) {
            while (ply >= 0 && _layoutHistory.getChildCount() >= ply)
                _layoutHistory.removeViewAt(_layoutHistory.getChildCount() - 1);


            RelativeLayout.LayoutParams lp = new RelativeLayout.LayoutParams(RelativeLayout.LayoutParams.WRAP_CONTENT, RelativeLayout.LayoutParams.WRAP_CONTENT);
            if (_layoutHistory.getChildCount() > 0) {
                if (_vScrollHistory != null) {
                    if (_layoutHistory.getChildCount() % 2 == 0) {
                        lp.addRule(RelativeLayout.ALIGN_PARENT_LEFT);
                        lp.addRule(RelativeLayout.BELOW, _layoutHistory.getChildAt(_layoutHistory.getChildCount() - 1).getId());
                    } else {
                        lp.addRule(RelativeLayout.RIGHT_OF, _layoutHistory.getChildAt(_layoutHistory.getChildCount() - 1).getId());
                        if (_layoutHistory.getChildCount() > 2) {
                            lp.addRule(RelativeLayout.BELOW, _layoutHistory.getChildAt(_layoutHistory.getChildCount() - 2).getId());
                        }
                    }
                } else if (_hScrollHistory != null) {
                    lp.addRule(RelativeLayout.RIGHT_OF, _layoutHistory.getChildAt(_layoutHistory.getChildCount() - 1).getId());
                }
            }
            _layoutHistory.addView(v, lp);

        }

        if (bScroll) {
            scrollToEnd();
        }
    }

    @Override
    public void setAnnotation(int i, String sAnno) {
        super.setAnnotation(i, sAnno);

        _arrPGNView.get(i).setAnnotated(sAnno.length() > 0);
        _arrPGNView.get(i).setSelected(false);
    }

    @Override
    public void paintBoard() {

        int[] arrSelPositions;

        int lastMove = _jni.getMyMove();
        if (lastMove != 0 && _bShowLastMove) {
            arrSelPositions = new int[4];
            arrSelPositions[0] = m_iFrom;
            arrSelPositions[1] = Move.getTo(lastMove);
            arrSelPositions[2] = Move.getFrom(lastMove);
            arrSelPositions[3] = _dpadPos;
        } else {
            arrSelPositions = new int[2];
            arrSelPositions[0] = m_iFrom;
            arrSelPositions[1] = _dpadPos;
        }
        int turn = _jni.getTurn();



        if (_playMode == HUMAN_HUMAN && _bAutoFlip &&
                (turn == BoardConstants.WHITE && _view.getFlippedBoard() ||
                        turn == BoardConstants.BLACK && false == _view.getFlippedBoard())) {
            _view.flipBoard();
        }


        ArrayList<Integer> arrPos = new ArrayList<Integer>();
        // collect legal moves if pref is set
        if (_bShowMoves && m_iFrom != -1) {
            try {
                // via try catch because of empty or mem error results in exception

                if (_jni.isEnded() == 0) {
                    synchronized (this) {
                        int size = _jni.getMoveArraySize();
                        //Log.i("paintBoard", "# " + size);
                        int move;
                        for (int i = 0; i < size; i++) {
                            move = _jni.getMoveArrayAt(i);
                            if (Move.getFrom(move) == m_iFrom) {
                                arrPos.add(Move.getTo(move));
                            }
                        }
                    }
                }
            } catch (Exception e) {
                System.gc();
            }
        }

        _view.paintBoard(_jni, arrSelPositions, arrPos);

        if (_layoutHistory != null) {
            for (int i = 0; i < _layoutHistory.getChildCount(); i++) {
                _arrPGNView.get(i).setSelected(i == _jni.getNumBoard() - 2);
            }
        }
    }

    public int getPlayMode() {
        return _playMode;
    }

    public void flipBoard() {
        _view.flipBoard();
        updateState();
    }


    public void setFlippedBoard(boolean b) {
        _view.setFlippedBoard(b);
    }

    public boolean getFlippedBoard() {
        return _view._flippedBoard;
    }

    @Override
    public void play() {
        if (_jni.isEnded() == 0) {
            if (_progressPlay.getVisibility() == View.VISIBLE) {
                _progressPlay.setVisibility(View.GONE);
                _butPlay.setVisibility(View.VISIBLE);
            } else {
                _butPlay.setVisibility(View.GONE);
                _progressPlay.setVisibility(View.VISIBLE);
            }

        }
        super.play();
    }

    public boolean handleClickFromPositionString(String s) {
        int index = Pos.fromString(s);
        if (_view._flippedBoard) {
            index = 63 - index;
        }
        return handleClick(index);
    }

    @Override
    public boolean handleClick(int index) {
        if (false == m_bActive) {
            setMessage(R.string.msg_wait);
            return false;
        }

        final int iTo = _view.getFieldIndex(index);
        if (m_iFrom != -1) {

            // Guess the move ===============================
            if (_viewAnimator != null) {
                if (_viewAnimator.getDisplayedChild() == SUBVIEW_GUESS) {
                    if (wasMovePlayed(m_iFrom, iTo)) {
                        if (_imgStatusGuess != null) {
                            _imgStatusGuess.setImageResource(R.drawable.indicator_ok);
                        }
                        jumptoMove(_jni.getNumBoard());
                        updateState();
                        m_iFrom = -1;
                        //Log.i("WAS MOVE PLAYED", "TRUE");
                        return true;
                    } else {
                        if (_imgStatusGuess != null) {
                            _imgStatusGuess.setImageResource(R.drawable.indicator_error);
                        }
                        m_iFrom = -1;
                        paintBoard();
                        //Log.i("WAS MOVE PLAYED", "FALSE");
                        return false;
                    }
                }
            }
            // ==============================================

            // ###########################################################################
            // DOES NOT WORK WHEN PROMOTION UNDO AND REDO WITH ANOTHER PIECE IS WANTED!!!
			/*
			if(_jni.getNumBoard() <= _arrPGN.size()){
				//
				if(wasMovePlayed(m_iFrom, iTo)){
					jumptoMove(_jni.getNumBoard());
					updateState();
					m_iFrom = -1;
					//Log.i("WAS MOVE PLAYED", "TRUE");
					return true;
				} else {
					Log.i("ChessView", "=== HISTORY overlap");
				}
			}
			*/

            // check if it is a promotion piece
            if (_jni.pieceAt(BoardConstants.WHITE, m_iFrom) == BoardConstants.PAWN &&
                    BoardMembers.ROW_TURN[BoardConstants.WHITE][m_iFrom] == 6 &&
                    BoardMembers.ROW_TURN[BoardConstants.WHITE][iTo] == 7
                    ||
                    _jni.pieceAt(BoardConstants.BLACK, m_iFrom) == BoardConstants.PAWN &&
                            BoardMembers.ROW_TURN[BoardConstants.BLACK][m_iFrom] == 6 &&
                            BoardMembers.ROW_TURN[BoardConstants.BLACK][iTo] == 7) {

                final String[] items = _parent.getResources().getStringArray(R.array.promotionpieces);

                AlertDialog.Builder builder = new AlertDialog.Builder(_parent);
                builder.setTitle(R.string.title_pick_promo);
                builder.setSingleChoiceItems(items, 0, new DialogInterface.OnClickListener() {
                    public void onClick(DialogInterface dialog, int item) {
                        dialog.dismiss();
                        _jni.setPromo(4 - item);
                        boolean bValid = requestMove(m_iFrom, iTo);
                        m_iFrom = -1;
                        if (false == bValid)
                            paintBoard();
                    }
                });
                AlertDialog alert = builder.create();
                alert.show();

                if (_vibrator != null) {
                    _vibrator.vibrate(40L);
                }

                return true;
            } else if (_jni.isAmbiguousCastle(m_iFrom, iTo) != 0) { // in case of Fischer

                AlertDialog.Builder builder = new AlertDialog.Builder(_parent);
                builder.setTitle(R.string.title_castle);
                builder.setPositiveButton(R.string.alert_yes, new DialogInterface.OnClickListener() {
                    public void onClick(DialogInterface dialog, int item) {
                        dialog.dismiss();
                        requestMoveCastle(m_iFrom, iTo);
                        m_iFrom = -1;
                    }
                });
                builder.setNegativeButton(R.string.alert_no, new DialogInterface.OnClickListener() {
                    public void onClick(DialogInterface dialog, int item) {
                        dialog.dismiss();
                        if (m_iFrom != iTo) {
                            requestMove(m_iFrom, iTo);
                        }
                        m_iFrom = -1;
                        paintBoard();
                    }
                });
                AlertDialog alert = builder.create();
                alert.show();

                if (_vibrator != null) {
                    _vibrator.vibrate(40L);
                }

                return true; // done, return from method!
            }
            //Log.i("ChessView", "====== not a special move");
        }
        // if to is same as from (not in case of Fischer random castle!
        if (m_iFrom == iTo) {
            m_iFrom = -1;
            paintBoard();
            return false;
        }
        if (super.handleClick(iTo)) {
            if (_vibrator != null) {
                _vibrator.vibrate(40L);
            }
            return true;
        }
        return false;
    }

    @Override
    public void setMessage(String sMsg) {
        _parent.doToast(sMsg);
        //_tvMessage.setText(sMsg);
        //m_textMessage.setText(sMsg);
    }

    @Override
    public void setEngineMessage(String sText) {
        if (_tvEngine != null) {
            _tvEngine.setText(sText);
        }
    }

    @Override
    public void setMessage(int res) {
        //_tvMessage.setText(res);
        _parent.doToast(_parent.getString(res));
    }

    public void setPlayMode(int mode) {
        _playMode = mode;
    }
	
	/*
	public void adjustWidth(){
		// after resume of view, all images should force width of includeboard
		
		View v = (View)_parent.findViewById(R.id.LayoutBottomClock);
		if(v != null){
			int w = v.getWidth();
			//Log.i("ChessView", "w = " + w);
			View boardView = (View)_parent.findViewById(R.id.includeboard);
			w = boardView.getWidth();
			//Log.i("ChessView", "w = " + w);
			v.setMinimumWidth(w);
			v = (View)_parent.findViewById(R.id.LayoutTopClock);
			v.setMinimumWidth(w);
		}
		
		//
	}
	*/

    public void OnPause(SharedPreferences.Editor editor) {

        if (_uci.isReady()) {
            _uci.quit();
        } else {
            if (m_bActive == false) {
                _jni.interrupt();
            }
        }

        editor.putBoolean("flippedBoard", _view.getFlippedBoard());
        editor.putInt("levelMode", m_iLevelMode);
        editor.putInt("level", _selectedLevel);
        editor.putInt("levelPly", _selectedLevelPly);
        editor.putInt("playMode", _playMode);
        editor.putBoolean("autoflipBoard", _bAutoFlip);
        editor.putBoolean("showMoves", _bShowMoves);
        editor.putBoolean("playAsBlack", _bPlayAsBlack);
        editor.putBoolean("PlayVolume" , _bPlayVolume);
        editor.putInt("boardNum", _jni.getNumBoard());
        if (_viewAnimator != null) {
            editor.putInt("animatorViewNumber", _viewAnimator.getDisplayedChild());
        }
        pauzeTimer();
        editor.putLong("clockTotalMillies", _lClockTotal);
        editor.putLong("clockWhiteMillies", _lClockWhite);
        editor.putLong("clockBlackMillies", _lClockBlack);

        _bDidResume = false;
    }

    public void OnResume(SharedPreferences prefs) {
        super.OnResume();

        _bDidResume = false;

        _view._showCoords = prefs.getBoolean("showCoords", false);

        String sEngine = prefs.getString("UCIEngine", null);
        if (sEngine != null) {
            Log.i("ChessView", "UCIEngine " + sEngine);
            String sEnginePath = "/data/data/jwtc.android.chess/" + sEngine;
            File f = new File(sEnginePath);
            if (f.exists()) {
                _uci.init(sEnginePath);
                if (_tvEngine != null) {
                    _tvEngine.setText("UCI engine " + sEngine);
                } else {
                    Log.w("ChessView", "Could not init engine");
                }
            } else {
                Log.e("ChessView", "UCI engine path does not exists: " + sEnginePath);
            }
        }
        _view.setFlippedBoard(prefs.getBoolean("flippedBoard", false));
        _bAutoFlip = prefs.getBoolean("autoflipBoard", false);
        _bShowMoves = prefs.getBoolean("showMoves", true);
        _bShowLastMove = prefs.getBoolean("showLastMove", true);

        _bPlayAsBlack = prefs.getBoolean("playAsBlack", false);

        setLevelMode(prefs.getInt("levelMode", LEVEL_TIME));
        _selectedLevel = prefs.getInt("level", 2);
        _selectedLevelPly = prefs.getInt("levelPly", 2);
        _playMode = prefs.getInt("playMode", HUMAN_PC);

        if (prefs.getBoolean("onLoadJumpToLastMove", false)) {

        } else {
            jumptoMove(prefs.getInt("boardNum", 0));
        }

        _lClockTotal = prefs.getLong("clockTotalMillies", 0);
        _lClockWhite = prefs.getLong("clockWhiteMillies", 0);
        _lClockBlack = prefs.getLong("clockBlackMillies", 0);
        continueTimer();

        ChessImageView._colorScheme = prefs.getInt("ColorScheme", 0);
        if (_viewAnimator != null) {
            _viewAnimator.setDisplayedChild(prefs.getInt("animatorViewNumber", 0) % _viewAnimator.getChildCount());
        }

        _bPlayVolume = prefs.getBoolean("PlayVolume", true);
        if (_bPlayVolume){
            butQuickSoundOff.setVisibility(View.GONE);
            butQuickSoundOn.setVisibility(View.VISIBLE);
            _parent.set_fVolume(1.0f);
        } else {
            butQuickSoundOn.setVisibility(View.GONE);
            butQuickSoundOff.setVisibility(View.VISIBLE);
            _parent.set_fVolume(0.0f);
        }

        if (_butPlay != null) {
            if (_playMode == HUMAN_HUMAN) {
                _butPlay.setVisibility(View.GONE);    // turn off play button when human vs human
            } else {
                _butPlay.setVisibility(View.VISIBLE);
            }
        }

        if (_bPlayAsBlack) {
            // player as black
            if (false == _view.getFlippedBoard()) {
                flipBoard();
            }
            if (_playMode == HUMAN_PC && _jni.getTurn() == ChessBoard.WHITE) {
                play();
            }

        } else {
            // player as white
            if (_view.getFlippedBoard()) {
                flipBoard();
            }
            if (_playMode == HUMAN_PC && _jni.getTurn() == ChessBoard.BLACK) {
                play();
            }
        }


        ///////////////////////////////////////////////////////////////////

        if (prefs.getBoolean("showECO", true) && _jArrayECO == null) {
            (new Thread(new Runnable() {
                public void run() {
                    try {
                        Thread.sleep(5000);

                        long start = System.currentTimeMillis();
                        InputStream in = _parent.getAssets().open("ECO.json");
                        BufferedReader br = new BufferedReader(new InputStreamReader(in));

                        StringBuffer sb = new StringBuffer("");
                        String line = "";

                        while ((line = br.readLine()) != null) {
                            sb.append(line + "\n");
                        }

                        in.close();

                        _jArrayECO = new JSONArray(sb.toString());
                        Log.i("ChessView", "ECO jArray - size " + _jArrayECO.length() + " load " + (System.currentTimeMillis() - start));

                    } catch (Exception e) {

                    }
                }
            })).start();


        }

        /////////////////////////////////////////////////////////////////
        _bDidResume = true;
    }


    @Override
    public void updateState() {
        super.updateState();

        if (_progressPlay != null) {
            if (_progressPlay.getVisibility() == View.VISIBLE) {
                if (m_bActive) {
                    _progressPlay.setVisibility(View.GONE);
                    _butPlay.setVisibility(View.VISIBLE);
                }
            } else {
                if (false == m_bActive) {
                    _progressPlay.setVisibility(View.VISIBLE);
                    _butPlay.setVisibility(View.GONE);
                }
            }
        }

        int i = _jni.getNumBoard() - 2;

        if (_tvAnnotate != null) {
            if (i >= 0 && i < _arrPGN.size()) {
                _tvAnnotate.setText(_arrPGN.get(i)._sAnnotation);
                if (_tvAnnotateGuess != null) {
                    _tvAnnotateGuess.setText(_arrPGN.get(i)._sAnnotation);
                }
            } else {
                _tvAnnotate.setText("");
                if (_tvAnnotateGuess != null) {
                    _tvAnnotateGuess.setText("");
                }
            }
        }
        int turn, piece;
        if (_arrImageCaptured[0][0] != null) {

            for (turn = 0; turn < 2; turn++) {
                for (piece = 0; piece < 5; piece++) {
                    int iTmp = _jni.getNumCaptured(turn, piece);
                    _arrImageCaptured[turn][piece].setVisibility(iTmp > 0 ? View.VISIBLE : View.INVISIBLE);
                    _arrTextCaptured[turn][piece].setText(iTmp > 1 ? "" + iTmp : "");
                }
            }
        }
        int state = _jni.getState();
        int res = chessStateToR(state);
        turn = _jni.getTurn();

        if (turn == ChessBoard.WHITE) {
            if (_view.getFlippedBoard()) {
                _switchTurnOpp.setVisibility(View.VISIBLE);
                _switchTurnOpp.setDisplayedChild(1);
                _switchTurnMe.setVisibility(View.INVISIBLE);
            } else {
                _switchTurnMe.setVisibility(View.VISIBLE);
                _switchTurnMe.setDisplayedChild(1);
                _switchTurnOpp.setVisibility(View.INVISIBLE);
            }
        } else {
            if (_view.getFlippedBoard()) {
                _switchTurnMe.setVisibility(View.VISIBLE);
                _switchTurnMe.setDisplayedChild(0);
                _switchTurnOpp.setVisibility(View.INVISIBLE);

            } else {
                _switchTurnOpp.setVisibility(View.VISIBLE);
                _switchTurnOpp.setDisplayedChild(0);
                _switchTurnMe.setVisibility(View.INVISIBLE);
            }
        }

        if (turn == ChessBoard.WHITE && _view.getFlippedBoard() == false ||
                turn == ChessBoard.BLACK && _view.getFlippedBoard() == true) {

            if (state == ChessBoard.PLAY) {
                _tvTitleMe.setText(getMyName());
            } else {
                _tvTitleMe.setText(String.format(_parent.getString(R.string.msg_state_format), _parent.getString(res)));
            }
            _tvTitleOpp.setText(getOppName());
        } else {
            if (state == ChessBoard.PLAY) {
                _tvTitleOpp.setText(getOppName());
            } else {
                _tvTitleOpp.setText(String.format(_parent.getString(R.string.msg_state_format), _parent.getString(res)));
            }
            _tvTitleMe.setText(getMyName());
        }

        if (_seekBar != null) {
            _seekBar.setMax(_arrPGN.size());
            _seekBar.setProgress(_jni.getNumBoard() - 1);
        }

        //_imgTurnOpp.setImageResource(R.drawable.emo_im_surprised);

        //if(_tvEngineValue != null)
        //	_tvEngineValue.setText("BoardValue " + _jni.getBoardValue());

        if(_jArrayECO != null) {
            String sECO = getECOInfo(0, _jArrayECO);
            Log.i("ChessView-ECO", sECO == null ? "No ECO" : sECO);
            if (sECO != null && (_sPrevECO != null && _sPrevECO.equals(sECO) == false) || _sPrevECO == null) {
                if (sECO != null && sECO.trim().length() > 0) {
                    _parent.doToast(sECO);
                }
            }
            _sPrevECO = sECO;
        }
    }


    private String getECOInfo(int level, JSONArray jArray) {
        if (level < _arrPGN.size()) {
            PGNEntry entry = _arrPGN.get(level);
            try {
                for (int i = 0; i < jArray.length(); i++) {
                    JSONObject jObj = (JSONObject) jArray.get(i);
                    if (jObj.get("m").equals(entry._sMove)) {

                        String sCurrent = jObj.getString("e") + ": " + jObj.getString("n") + (jObj.getString("v").length() > 0 ? ", " + jObj.getString("v") : "");
                        String sNext = null;

                        if (jObj.has("a")) {
                            sNext = getECOInfo(level + 1, jObj.getJSONArray("a"));
                        }
                        if (sNext == null) {
                            return sCurrent;
                        }
                        return sNext;
                    }
                }
            } catch (Exception ex) {

            }
        }
        return null;
    }

    public String getMyName() {
        if (_view.getFlippedBoard())
            return getBlack();
        return getWhite();
    }

    public String getOppName() {
        if (_view.getFlippedBoard())
            return getWhite();
        return getBlack();
    }

    public void scrollToEnd() {
        if (_hScrollHistory != null) {
            _hScrollHistory.post(new Runnable() {
                public void run() {
                    _hScrollHistory.fullScroll(HorizontalScrollView.FOCUS_RIGHT);
                }

            });
        } else if (_vScrollHistory != null) {
            _vScrollHistory.post(new Runnable() {
                public void run() {
                    _vScrollHistory.fullScroll(ScrollView.FOCUS_DOWN);
                }

            });
        }
    }

    public void scrollToStart() {
        if (_hScrollHistory != null) {
            _hScrollHistory.post(new Runnable() {
                public void run() {
                    _hScrollHistory.fullScroll(HorizontalScrollView.FOCUS_LEFT);
                }

            });
        } else if (_vScrollHistory != null) {
            _vScrollHistory.post(new Runnable() {
                public void run() {
                    _vScrollHistory.fullScroll(ScrollView.FOCUS_UP);
                }

            });
        }
    }

    public boolean hasVerticalScroll() {
        return (_vScrollHistory != null);
    }

    protected void dpadFirst() {
        if (_dpadPos == -1) {
            _dpadPos = _jni.getTurn() == ChessBoard.BLACK ? ChessBoard.e8 : ChessBoard.e1;
        }
    }

    public void dpadUp() {
        dpadFirst();
        if (_view.getFlippedBoard()) {
            if (_dpadPos < 55) {
                _dpadPos += 8;
                paintBoard();
            }
        } else {
            if (_dpadPos > 8) {
                _dpadPos -= 8;
                paintBoard();
            }
        }
    }

    public void dpadDown() {
        dpadFirst();
        if (_view.getFlippedBoard()) {
            if (_dpadPos > 8) {
                _dpadPos -= 8;
                paintBoard();
            }
        } else {
            if (_dpadPos < 55) {
                _dpadPos += 8;
                paintBoard();
            }
        }
    }

    public void dpadLeft() {
        dpadFirst();
        if (_view.getFlippedBoard()) {
            if (_dpadPos < 63) {
                _dpadPos++;
                paintBoard();
            }
        } else {
            if (_dpadPos > 1) {
                _dpadPos--;
                paintBoard();
            }
        }
    }

    public void dpadRight() {
        dpadFirst();
        if (_view.getFlippedBoard()) {
            if (_dpadPos > 1) {
                _dpadPos--;
                paintBoard();
            }
        } else {
            if (_dpadPos < 63) {
                _dpadPos++;
                paintBoard();
            }
        }
    }

    public void dpadSelect() {
        if (_dpadPos != -1) {
            if (m_iFrom == -1) {
                m_iFrom = _dpadPos;
                paintBoard();
            } else {
                if (_view.getFlippedBoard()) {
                    handleClick(_view.getFieldIndex(_dpadPos));
                } else {
                    handleClick(_dpadPos);
                }
            }

        }
    }


    public void playNotification() {

        int move = _jni.getMyMove();
        String sMove = _jni.getMyMoveToString();

        if (sMove.contains("x")){
            _parent.soundCapture();
        } else {
            _parent.soundMove();
        }

        if (sMove.length() > 3 && !sMove.equals("O-O-O")) {
            // assures space to separate which Rook and which Knight to move
            sMove = sMove.substring(0, 2) + " " + sMove.substring(2, sMove.length());
        }

        if (sMove.length() > 3) {
            if (sMove.charAt(sMove.length() - 4) == ' ')    // assures space from last two chars
            {
                sMove = sMove.substring(0, sMove.length() - 2) + " " + sMove.substring(sMove.length() - 2, sMove.length());
            }
        }

        // Pronunciation - the "long A", @see http://stackoverflow.com/questions/9716851/android-tts-doesnt-pronounce-single-letter
        sMove = sMove.replace("a", "ay ");

        sMove = sMove.replace("b", "bee ");
        ///////////////////////////////////

        sMove = sMove.replace("x", " takes ");

        sMove = sMove.replace("=", " promotes to ");

        sMove = sMove.replace("K", "King ");
        sMove = sMove.replace("Q", "Queen ");
        sMove = sMove.replace("R", "Rook ");
        sMove = sMove.replace("B", "Bishop ");
        sMove = sMove.replace("N", "Knight ");

        sMove = sMove.replace("O-O-O", "Castle Queen Side");
        sMove = sMove.replace("O-O", "Castle King Side");

        sMove = sMove.replace("+", " check");
        sMove = sMove.replace("#", " checkmate");

        if (Move.isEP(move)) {
            sMove = sMove + " On Pesawnt";  // En Passant
        }
        //Log.i("ChessView", " 2nd sMove = " + sMove);
        _parent.soundNotification(sMove);
    }

}
