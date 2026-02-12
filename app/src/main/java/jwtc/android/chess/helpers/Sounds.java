package jwtc.android.chess.helpers;

import android.content.Context;
import android.content.SharedPreferences;
import android.media.AudioAttributes;
import android.media.SoundPool;

import jwtc.android.chess.R;

public class Sounds {
    private final static String TAG = "Sounds";
    private SoundPool soundPool = null;
    private final Context context;
    protected int soundTickTock, soundCheck, soundMove, soundCapture, soundNewGame;
    protected float fVolume = 1.0f;
    protected boolean enabled = false;

    public Sounds(Context context) {
        this.context = context;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public boolean getEnabled() {
        return enabled;
    }

    public void initPrefs(SharedPreferences prefs) {
        enabled = prefs.getBoolean("moveSounds", false);
        if (enabled) {
            initSoundPool();
        }
    }

    public void playCheck() {
        if (enabled && soundPool != null) {
            float factor = 1f;
            soundPool.play(soundCheck, fVolume * factor, fVolume * factor, 1, 0, 1);
        }
    }

    public void playMove() {
        if (enabled && soundPool != null) {
            soundPool.play(soundMove, fVolume, fVolume, 1, 0, 1);
        }
    }

    public void playCapture() {
        if (enabled && soundPool != null) {
            soundPool.play(soundCapture, fVolume, fVolume, 1, 0, 1);
        }
    }

    public void playNewGame() {
        if (enabled && soundPool != null) {
            soundPool.play(soundNewGame, fVolume, fVolume, 1, 0, 1);
        }
    }

    public void playTickTock() {
        if (enabled && soundPool != null) {
            soundPool.play(soundTickTock, fVolume, fVolume, 1, 0, 1);
        }
    }

    protected void initSoundPool() {
        if (soundPool == null) {
            AudioAttributes audioAttributes = new AudioAttributes.Builder()
                .setUsage(AudioAttributes.USAGE_GAME)
                .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                .build();

            soundPool = new SoundPool.Builder()
                .setMaxStreams(7)
                .setAudioAttributes(audioAttributes)
                .build();

            soundTickTock = soundPool.load(context, R.raw.ticktock, 1);
            soundCheck = soundPool.load(context, R.raw.impact, 2);
            soundMove = soundPool.load(context, R.raw.move, 1);
            soundCapture = soundPool.load(context, R.raw.capture, 1);
            soundNewGame = soundPool.load(context, R.raw.chesspiecesfall, 1);
        }
    }
}
