package jwtc.android.chess.ui;

import android.app.Activity;
import android.content.ClipData;
import android.content.SharedPreferences;
import android.content.res.AssetManager;
import android.graphics.BitmapFactory;
import android.graphics.Rect;
import android.os.Bundle;
import android.util.Log;
import android.view.DragEvent;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewTreeObserver;
import android.view.Window;
import android.widget.RelativeLayout;
import android.widget.TableRow;

import java.util.ArrayList;

import jwtc.android.chess.ChessBoardLayout;
import jwtc.android.chess.ChessImageView;
import jwtc.android.chess.ImageCacheObject;
import jwtc.android.chess.MyBaseActivity;
import jwtc.android.chess.R;
import jwtc.chess.JNI;
import jwtc.chess.Pos;
import jwtc.chess.board.BoardConstants;
import jwtc.chess.board.ChessBoard;

abstract public class ChessBoardActivity extends MyBaseActivity {
    public static final int MODE_BLINDFOLD_SHOWPIECES = 0;
    public static final int MODE_BLINDFOLD_HIDEPIECES = 1;
    public static final int MODE_BLINDFOLD_SHOWPIECELOCATION = 2;

    private ChessImageView[] _arrImages;
    private ImageCacheObject[] _arrImgCache;
    private int _modeBlindfold = ChessBoardActivity.MODE_BLINDFOLD_SHOWPIECES;
    private boolean _flippedBoard = false;
    private boolean _showCoords = false;
    private int[] arrSelPositions = null;
    private ArrayList<Integer> arrPos = null;

    abstract public void handleClick(int index);

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        setContentView(R.layout.main);

        View.OnClickListener ocl = new View.OnClickListener() {
            public void onClick(View arg0) {
                handleClick(getIndexOfView(arg0));
            }
        };

        View.OnLongClickListener olcl = new View.OnLongClickListener() {
            @Override
            public boolean onLongClick(View view) {
                handleClick(getIndexOfView(view));
                return true;
            }
        };

        _arrImages = new ChessImageView[64];
        _arrImages[0] = (ChessImageView) findViewById(R.id.a8);
        _arrImages[1] = (ChessImageView) findViewById(R.id.b8);
        _arrImages[2] = (ChessImageView) findViewById(R.id.c8);
        _arrImages[3] = (ChessImageView) findViewById(R.id.d8);
        _arrImages[4] = (ChessImageView) findViewById(R.id.e8);
        _arrImages[5] = (ChessImageView) findViewById(R.id.f8);
        _arrImages[6] = (ChessImageView) findViewById(R.id.g8);
        _arrImages[7] = (ChessImageView) findViewById(R.id.h8);

        _arrImages[8] = (ChessImageView) findViewById(R.id.a7);
        _arrImages[9] = (ChessImageView) findViewById(R.id.b7);
        _arrImages[10] = (ChessImageView) findViewById(R.id.c7);
        _arrImages[11] = (ChessImageView) findViewById(R.id.d7);
        _arrImages[12] = (ChessImageView) findViewById(R.id.e7);
        _arrImages[13] = (ChessImageView) findViewById(R.id.f7);
        _arrImages[14] = (ChessImageView) findViewById(R.id.g7);
        _arrImages[15] = (ChessImageView) findViewById(R.id.h7);

        _arrImages[16] = (ChessImageView) findViewById(R.id.a6);
        _arrImages[17] = (ChessImageView) findViewById(R.id.b6);
        _arrImages[18] = (ChessImageView) findViewById(R.id.c6);
        _arrImages[19] = (ChessImageView) findViewById(R.id.d6);
        _arrImages[20] = (ChessImageView) findViewById(R.id.e6);
        _arrImages[21] = (ChessImageView) findViewById(R.id.f6);
        _arrImages[22] = (ChessImageView) findViewById(R.id.g6);
        _arrImages[23] = (ChessImageView) findViewById(R.id.h6);

        _arrImages[24] = (ChessImageView) findViewById(R.id.a5);
        _arrImages[25] = (ChessImageView) findViewById(R.id.b5);
        _arrImages[26] = (ChessImageView) findViewById(R.id.c5);
        _arrImages[27] = (ChessImageView) findViewById(R.id.d5);
        _arrImages[28] = (ChessImageView) findViewById(R.id.e5);
        _arrImages[29] = (ChessImageView) findViewById(R.id.f5);
        _arrImages[30] = (ChessImageView) findViewById(R.id.g5);
        _arrImages[31] = (ChessImageView) findViewById(R.id.h5);

        _arrImages[32] = (ChessImageView) findViewById(R.id.a4);
        _arrImages[33] = (ChessImageView) findViewById(R.id.b4);
        _arrImages[34] = (ChessImageView) findViewById(R.id.c4);
        _arrImages[35] = (ChessImageView) findViewById(R.id.d4);
        _arrImages[36] = (ChessImageView) findViewById(R.id.e4);
        _arrImages[37] = (ChessImageView) findViewById(R.id.f4);
        _arrImages[38] = (ChessImageView) findViewById(R.id.g4);
        _arrImages[39] = (ChessImageView) findViewById(R.id.h4);

        _arrImages[40] = (ChessImageView) findViewById(R.id.a3);
        _arrImages[41] = (ChessImageView) findViewById(R.id.b3);
        _arrImages[42] = (ChessImageView) findViewById(R.id.c3);
        _arrImages[43] = (ChessImageView) findViewById(R.id.d3);
        _arrImages[44] = (ChessImageView) findViewById(R.id.e3);
        _arrImages[45] = (ChessImageView) findViewById(R.id.f3);
        _arrImages[46] = (ChessImageView) findViewById(R.id.g3);
        _arrImages[47] = (ChessImageView) findViewById(R.id.h3);

        _arrImages[48] = (ChessImageView) findViewById(R.id.a2);
        _arrImages[49] = (ChessImageView) findViewById(R.id.b2);
        _arrImages[50] = (ChessImageView) findViewById(R.id.c2);
        _arrImages[51] = (ChessImageView) findViewById(R.id.d2);
        _arrImages[52] = (ChessImageView) findViewById(R.id.e2);
        _arrImages[53] = (ChessImageView) findViewById(R.id.f2);
        _arrImages[54] = (ChessImageView) findViewById(R.id.g2);
        _arrImages[55] = (ChessImageView) findViewById(R.id.h2);

        _arrImages[56] = (ChessImageView) findViewById(R.id.a1);
        _arrImages[57] = (ChessImageView) findViewById(R.id.b1);
        _arrImages[58] = (ChessImageView) findViewById(R.id.c1);
        _arrImages[59] = (ChessImageView) findViewById(R.id.d1);
        _arrImages[60] = (ChessImageView) findViewById(R.id.e1);
        _arrImages[61] = (ChessImageView) findViewById(R.id.f1);
        _arrImages[62] = (ChessImageView) findViewById(R.id.g1);
        _arrImages[63] = (ChessImageView) findViewById(R.id.h1);


        //_imgOverlay = (ImageView)findViewById(R.id.ImageBoardOverlay);

        AssetManager am = getAssets();
        SharedPreferences prefs = getSharedPreferences("ChessPlayer", Activity.MODE_PRIVATE);

        //String sFolder = prefs.getString("pieceSet", "highres") + "/";
        String sFolder = "highres/";
        String sPat = prefs.getString("tileSet", "");

        try {

            //ChessImageView._svgTest =  SVGParser.getSVGFromAsset(activity.getAssets(), "svg/kb.svg");
            //ChessImageView._svgTest =  SVGParser.getSVGFromInputStream(am.open("svg/kb.svg"));
            if (prefs.getBoolean("extrahighlight", false)) {
                ChessImageView._bmpBorder = BitmapFactory.decodeStream(am.open(sFolder + "border.png"));
            } else {
                ChessImageView._bmpBorder = null;
            }

            ChessImageView._bmpSelect = BitmapFactory.decodeStream(am.open(sFolder + "select.png"));
            ChessImageView._bmpSelectLight = BitmapFactory.decodeStream(am.open(sFolder + "select_light.png"));

            if (sPat.length() > 0) {
                ChessImageView._bmpTile = BitmapFactory.decodeStream(am.open("tiles/" + sPat + ".png"));
            } else {
                ChessImageView._bmpTile = null;
            }
            // pawn
            ChessImageView._arrPieceBitmaps[ChessBoard.BLACK][BoardConstants.PAWN] = BitmapFactory.decodeStream(am.open(sFolder + "pb.png"));
            ChessImageView._arrPieceBitmaps[ChessBoard.WHITE][BoardConstants.PAWN] = BitmapFactory.decodeStream(am.open(sFolder + "pw.png"));

            // kNight
            ChessImageView._arrPieceBitmaps[ChessBoard.BLACK][BoardConstants.KNIGHT] = BitmapFactory.decodeStream(am.open(sFolder + "nb.png"));
            ChessImageView._arrPieceBitmaps[ChessBoard.WHITE][BoardConstants.KNIGHT] = BitmapFactory.decodeStream(am.open(sFolder + "nw.png"));

            // bishop
            ChessImageView._arrPieceBitmaps[ChessBoard.BLACK][BoardConstants.BISHOP] = BitmapFactory.decodeStream(am.open(sFolder + "bb.png"));
            ChessImageView._arrPieceBitmaps[ChessBoard.WHITE][BoardConstants.BISHOP] = BitmapFactory.decodeStream(am.open(sFolder + "bw.png"));

            // rook
            ChessImageView._arrPieceBitmaps[ChessBoard.BLACK][BoardConstants.ROOK] = BitmapFactory.decodeStream(am.open(sFolder + "rb.png"));
            ChessImageView._arrPieceBitmaps[ChessBoard.WHITE][BoardConstants.ROOK] = BitmapFactory.decodeStream(am.open(sFolder + "rw.png"));

            // queen
            ChessImageView._arrPieceBitmaps[ChessBoard.BLACK][BoardConstants.QUEEN] = BitmapFactory.decodeStream(am.open(sFolder + "qb.png"));
            ChessImageView._arrPieceBitmaps[ChessBoard.WHITE][BoardConstants.QUEEN] = BitmapFactory.decodeStream(am.open(sFolder + "qw.png"));

            // king
            ChessImageView._arrPieceBitmaps[ChessBoard.BLACK][BoardConstants.KING] = BitmapFactory.decodeStream(am.open(sFolder + "kb.png"));
            ChessImageView._arrPieceBitmaps[ChessBoard.WHITE][BoardConstants.KING] = BitmapFactory.decodeStream(am.open(sFolder + "kw.png"));

        } catch (Exception ex) {

        }


        _arrImgCache = new ImageCacheObject[64];
        for (int i = 0; i < 64; i++) {
            _arrImages[i].setOnTouchListener(new MyTouchListener());
            _arrImages[i].setOnDragListener(new MyDragListener());
            //_arrImages[i].setOnClickListener(ocl);
            //_arrImages[i].setFocusable(false);
            //_arrImages[i].setOnLongClickListener(olcl);

            _arrImgCache[i] = new ImageCacheObject();
        }

        final View layout = (View) getWindow().getDecorView().findViewById(android.R.id.content);
        ViewTreeObserver vto = layout.getViewTreeObserver();
        vto.addOnGlobalLayoutListener(new ViewTreeObserver.OnGlobalLayoutListener() {
            @Override
            public void onGlobalLayout() {
                layout.getViewTreeObserver().removeGlobalOnLayoutListener(this);
                adjustWidth();
            }
        });

        // @TODO
        JNI.getInstance().newGame();
        paintBoard();
    }

    private void adjustWidth() {

        ChessImageView._matrix = null;

        final Window window = getWindow();
        final View v = window.getDecorView();

        v.post(new Runnable() {
            @Override
            public void run() {
                Rect rectangle = new Rect();
                v.getWindowVisibleDisplayFrame(rectangle);
                int contentViewTop = window.findViewById(Window.ID_ANDROID_CONTENT).getTop();
                //int titleBarHeight= contentViewTop - statusBarHeight;
                int availableHeight = (rectangle.bottom - rectangle.top) - contentViewTop;
                int availableWidth = rectangle.right - rectangle.left;
                int length, margin = 0;

                // portrait
                if (availableHeight > availableWidth) {
                    length = availableWidth / 8;
                    margin = (availableWidth - 8 * length) / 2;
                } else {
                    length = availableHeight / 8;
                }

                if (margin > 0) {
                    ChessBoardLayout tableLayout = (ChessBoardLayout)findViewById(R.id.includeboard);
                    //View viewBoard = findViewById(R.id.includeboard);
                    ViewGroup.LayoutParams params = tableLayout.getLayoutParams();
                    if (params instanceof RelativeLayout.LayoutParams) {
                        ((RelativeLayout.LayoutParams)params).setMargins(margin, 0, 0, 0); //substitute parameters for left, top, right, bottom
                        tableLayout.setLayoutParams(params);
                    }
                    tableLayout.invalidate();
                }
                Log.i("ChessViewBase", "availableHeight 2 " + availableHeight);

                TableRow.LayoutParams params = new TableRow.LayoutParams(length, length);
                for (int i = 0; i < 64; i++) {
                    _arrImages[i].setLayoutParams(params);
                }
            }
        });
    }

    public void paintBoard() {
        JNI jni = JNI.getInstance();
        boolean bPiece, bSelected, bSelectedPosition;

        int i, iPiece = BoardConstants.PAWN, iColor = ChessBoard.BLACK, iFieldColor;


        // Before and after this method
        System.gc();

        ImageCacheObject._flippedBoard = _flippedBoard;

        for (i = 0; i < 64; i++) {
            _arrImages[i].setPressed(false);
            _arrImages[i].setSelected(false);
        }

        ImageCacheObject tmpCache;

        for (i = 0; i < 64; i++) {
            bPiece = true;
            bSelected = false;
            bSelectedPosition = false;

            iFieldColor = (i & 1) == 0 ? (((i >> 3) & 1) == 0 ? ChessBoard.WHITE : ChessBoard.BLACK) : (((i >> 3) & 1) == 0 ? ChessBoard.BLACK : ChessBoard.WHITE);

            iColor = ChessBoard.BLACK;
            iPiece = jni.pieceAt(iColor, i);

            if (iPiece == BoardConstants.FIELD) {
                iColor = ChessBoard.WHITE;
                iPiece = jni.pieceAt(iColor, i);

                if (iPiece == BoardConstants.FIELD)
                    bPiece = false;
            }

            if (arrSelPositions != null) {
                for (int j = 0; j < arrSelPositions.length; j++) {
                    if (arrSelPositions[j] == i)
                        bSelected = true;
                }
            }
            if (arrPos != null) {
                bSelectedPosition = arrPos.contains(i);
            }

            String coord = null;
            if (_showCoords) {
                if (_flippedBoard) {
                    if (i < 8) {
                        coord = Pos.colToString(i).toUpperCase();
                    } else {
                        if (i % 8 == 7) {
                            coord = Pos.rowToString(i);
                        }
                    }
                } else {
                    if (i > 55) {
                        coord = Pos.colToString(i).toUpperCase();
                    } else {
                        if (i % 8 == 0) {
                            coord = Pos.rowToString(i);
                        }
                    }
                }
            }
            tmpCache = _arrImgCache[i];
            if (tmpCache._bPiece == bPiece &&
                    tmpCache._piece == iPiece &&
                    tmpCache._color == iColor &&
                    tmpCache._fieldColor == iFieldColor &&
                    tmpCache._selectedPos == bSelectedPosition &&
                    tmpCache._selected == bSelected &&
                    tmpCache._coord == coord) {

                continue;
            } else {

                tmpCache._coord = coord;

                if (_modeBlindfold == MODE_BLINDFOLD_HIDEPIECES) {
                    tmpCache._bPiece = false;
                    tmpCache._piece = -1;
                    tmpCache._color = iColor;
                    tmpCache._fieldColor = iFieldColor;
                    tmpCache._selectedPos = bSelectedPosition;
                    tmpCache._selected = bSelected;

                } else if (_modeBlindfold == MODE_BLINDFOLD_SHOWPIECELOCATION) {
                    tmpCache._bPiece = false;
                    tmpCache._piece = -1;
                    tmpCache._color = iColor;
                    tmpCache._fieldColor = iFieldColor;
                    tmpCache._selectedPos = iPiece >= 0;
                    tmpCache._selected = bSelected;
                } else {
                    tmpCache._bPiece = bPiece;
                    tmpCache._piece = iPiece;
                    tmpCache._color = iColor;
                    tmpCache._fieldColor = iFieldColor;
                    tmpCache._selectedPos = bSelectedPosition;
                    tmpCache._selected = bSelected;
                }

                _arrImages[getFieldIndex(i)].setICO(tmpCache);
                _arrImages[getFieldIndex(i)].invalidate();

            } // cache check
        }
        System.gc();
    }

    public void setSelectedPosition(int selectedIndex) {
        arrSelPositions = new int[1];
        arrSelPositions[0] = selectedIndex;
    }

    public void setFlippedBoard(boolean flipped) {
        resetImageCache();
        _flippedBoard = flipped;
    }

    public boolean getFlippedBoard() {
        return _flippedBoard;
    }

    public void flipBoard() {
        resetImageCache();
        _flippedBoard = !_flippedBoard;
        setFlippedBoard(_flippedBoard);
    }

    public int getIndexOfView(View view) {
        for (int i = 0; i < 64; i++) {
            if (_arrImages[i] == ((ChessImageView) view)) {

                _arrImages[i].setPressed(false);

                return i;
            }
        }
        return -1;
    }


    private int getFieldIndex(int i) {
        if (_flippedBoard) {
            return 63 - i;
        }
        return i;
    }

    private void resetImageCache() {
        for (int i = 0; i < 64; i++) {
            _arrImgCache[i]._bPiece = false;
            _arrImgCache[i]._fieldColor = (i & 1) == 0 ? (((i >> 3) & 1) == 0 ? ChessBoard.WHITE : ChessBoard.BLACK) : (((i >> 3) & 1) == 0 ? ChessBoard.BLACK : ChessBoard.WHITE);
            _arrImgCache[i]._selectedPos = false;
            _arrImgCache[i]._selected = false;
            _arrImgCache[i]._color = -1;
            _arrImgCache[i]._piece = -1;
        }
    }

    private final class MyDragListener implements View.OnDragListener {


        @Override
        public boolean onDrag(View v, DragEvent event) {
            int action = event.getAction();
            Log.i(TAG, "onDrag" +  getIndexOfView(v));
            switch (event.getAction()) {
                case DragEvent.ACTION_DRAG_STARTED:
                    // do nothing
                    break;
                case DragEvent.ACTION_DRAG_ENTERED:
//                    v.setBackgroundDrawable(enterShape);
                    break;
                case DragEvent.ACTION_DRAG_EXITED:
//                    v.setBackgroundDrawable(normalShape);
                    break;
                case DragEvent.ACTION_DROP:
                    // Dropped, reassign View to ViewGroup
//                    View view = (View) event.getLocalState();
//                    ViewGroup owner = (ViewGroup) view.getParent();
//                    owner.removeView(view);
//
//                    view.setVisibility(View.VISIBLE);
                    break;
                case DragEvent.ACTION_DRAG_ENDED:
                    //v.setBackgroundDrawable(normalShape);
                default:
                    break;
            }
            return true;
        }
    }

    private final class MyTouchListener implements View.OnTouchListener {
        public boolean onTouch(View view, MotionEvent motionEvent) {
            Log.i(TAG, "onTouch" +  getIndexOfView(view));
            if (motionEvent.getAction() == MotionEvent.ACTION_DOWN) {
                ClipData data = ClipData.newPlainText("", "");
                View.DragShadowBuilder shadowBuilder = new View.DragShadowBuilder(view);
                view.startDrag(data, shadowBuilder, view, 0);
                view.setVisibility(View.INVISIBLE);
                return true;
            } else if (motionEvent.getAction() == MotionEvent.ACTION_UP) {
                view.setVisibility(View.VISIBLE);
                view.invalidate();
                return true;
            }
            return false;
        }
    }
}
