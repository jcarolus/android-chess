package jwtc.android.chess.helpers;

import android.content.Context;
import android.media.AudioAttributes;
import android.media.SoundPool;

import jwtc.android.chess.R;

public class Sounds {
    private final static String TAG = "Sounds";
    private SoundPool soundPool = null;
    private final Context context;
    protected int soundTickTock,
        soundCheck,
        soundMove,
        soundCapture,
        soundNewGame,
        soundIllegalMove,
        soundSelect,
        soundTick,
        soundTickBlack,
        soundError,
        soundCorrect,
        soundTickPiece,
        soundTickPieceBlack,
        soundUnselect,
        soundBell,
        soundLowTime,
        soundStopWatch;
    protected float fVolume = 1.0f;
    protected boolean enabled = false;

    // Pitch of the crossing tick, keyed off square color: light squares sound higher,
    // dark squares lower. Tunable; SoundPool rate range is 0.5f..2.0f.
    private static final float RATE_LIGHT_SQUARE = 1.2f;
    private static final float RATE_DARK_SQUARE = 0.84f; // ~minor third lower

    public Sounds(Context context) {
        this.context = context;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
        if (enabled) {
            initSoundPool();
        }
    }

    public boolean isEnabled() {
        return enabled;
    }

    public void playCheck() {
        play(soundCheck);
    }

    public void playMove() {
        play(soundMove);
    }

    public void playCapture() {
        play(soundCapture);
    }

    public void playNewGame() {
        play(soundNewGame);
    }

    public void playTickTock() {
        play(soundTickTock);
    }

    public void playIllegalMove() {
        play(soundIllegalMove);
    }

    public void playSelect() {
        play(soundSelect);
    }

    public void playTick(boolean isDarkSquare) {
        play(isDarkSquare ? soundTickBlack : soundTick, isDarkSquare ? RATE_DARK_SQUARE : RATE_LIGHT_SQUARE);
    }

    // Black pieces use the echoing variant; white pieces (and the duck) use the plain
    // tick_piece. Square color still drives the pitch on top of either sample.
    public void playTickPiece(boolean isDarkSquare, boolean isBlackPiece) {
        int soundId = isBlackPiece ? soundTickPieceBlack : soundTickPiece;
        play(soundId, isDarkSquare ? RATE_DARK_SQUARE : RATE_LIGHT_SQUARE);
    }

    public void playError() {
        play(soundError);
    }

    public void playCorrect() {
        play(soundCorrect);
    }

    public void playUnselect() {
        play(soundUnselect);
    }

    public void playBell() {
        play(soundBell);
    }

    public void playLowTime() {
        play(soundLowTime);
    }

    public void playStopWatch() {
        play(soundStopWatch);
    }

    protected void initSoundPool() {
        if (soundPool == null) {
            AudioAttributes audioAttributes = new AudioAttributes.Builder()
                .setUsage(AudioAttributes.USAGE_GAME)
                .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                .build();

            soundPool = new SoundPool.Builder()
                .setMaxStreams(8)
                .setAudioAttributes(audioAttributes)
                .build();

            soundTickTock = loadSound(R.raw.ticktock, 1);
            soundCheck = loadSound(R.raw.impact, 2);
            soundMove = loadSound(R.raw.move, 1);
            soundCapture = loadSound(R.raw.capture, 1);
            soundNewGame = loadSound(R.raw.chesspiecesfall, 1);
            soundIllegalMove = loadSound(R.raw.illegal, 1);
            soundSelect = loadSound(R.raw.select, 1);
            soundTick = loadSound(R.raw.tick, 1);
            soundTickBlack = loadSound(R.raw.tick_black, 1);
            soundTickPiece = loadSound(R.raw.tick_piece, 1);
            soundTickPieceBlack = loadSound(R.raw.tick_piece_black, 1);
            soundError = loadSound(R.raw.error, 1);
            soundCorrect = loadSound(R.raw.correct, 1);
            soundUnselect = loadSound(R.raw.unselect, 1);
            soundBell = loadSound(R.raw.bell, 1);
            soundLowTime = loadSound(R.raw.lowtime, 1);
            soundStopWatch = loadSound(R.raw.stopwatch, 1);
        }
    }

    private void play(int soundId) {
        play(soundId, 1f);
    }

    private void play(int soundId, float rate) {
        if (enabled && soundPool != null) {
            soundPool.play(soundId, fVolume, fVolume, 1, 0, rate);
        }
    }

    private int loadSound(int resId, int priority) {
        try {
            return soundPool.load(context, resId, priority);
        } catch (Exception e) {
            return 0;
        }
    }
}
