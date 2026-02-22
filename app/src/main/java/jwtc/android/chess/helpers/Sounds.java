package jwtc.android.chess.helpers;

import android.content.Context;
import android.media.AudioAttributes;
import android.media.SoundPool;

import jwtc.android.chess.R;

public class Sounds {
    private final static String TAG = "Sounds";
    private SoundPool soundPool = null;
    private final Context context;
    protected int soundTickTock, soundCheck, soundMove, soundCapture, soundNewGame, soundIllegalMove, soundSelect, soundTick, soundError, soundCorrect;
    protected float fVolume = 1.0f;
    protected boolean enabled = false;

    public Sounds(Context context) {
        this.context = context;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
        if (enabled) {
            initSoundPool();
        }
    }

    public boolean getEnabled() {
        return enabled;
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

    public void playTick() {
        play(soundTick);
    }

    public void playError() {
        play(soundError);
    }

    public void playCorrect() {
        play(soundCorrect);
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
            soundError = loadSound(R.raw.error, 1);
            soundCorrect = loadSound(R.raw.correct, 1);
        }
    }

    private void play(int soundId) {
        if (enabled && soundPool != null) {
            soundPool.play(soundId, fVolume, fVolume, 1, 0, 1);
        }
    }

    private int loadSound(int resId, int priority) {
        return soundPool.load(context, resId, priority);
    }
}
