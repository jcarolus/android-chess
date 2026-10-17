package jwtc.android.chess.helpers;

import android.content.Context;
import android.media.AudioAttributes;
import android.media.SoundPool;
import android.os.Handler;
import android.os.Looper;

import java.util.EnumMap;

import jwtc.android.chess.R;

public class Sounds {
    // Measured asset durations, rounded to milliseconds. Update when replacing an asset.
    private enum Sound {
        TickTock(R.raw.ticktock, 174L),
        Check(R.raw.impact, 984L),
        Move(R.raw.move, 60L),
        Capture(R.raw.capture, 118L),
        NewGame(R.raw.chesspiecesfall, 1009L),
        IllegalMove(R.raw.illegal, 100L),
        Select(R.raw.select, 151L),
        Tick(R.raw.tick, 43L),
        TickBlack(R.raw.tick_black, 43L),
        Error(R.raw.error, 454L),
        Correct(R.raw.correct, 202L),
        TickPiece(R.raw.tick_piece, 463L),
        TickPieceBlack(R.raw.tick_piece_black, 463L),
        Unselect(R.raw.unselect, 273L),
        Bell(R.raw.bell, 1500L),
        LowTime(R.raw.lowtime, 500L),
        StopWatch(R.raw.stopwatch, 620L),
        Notification(R.raw.notification, 1304L);

        final int resourceId;
        final long durationMs;

        Sound(int resourceId, long durationMs) {
            this.resourceId = resourceId;
            this.durationMs = durationMs;
        }
    }

    private final Context context;
    private final Handler completionHandler = new Handler(Looper.getMainLooper());
    private final EnumMap<Sound, Integer> soundIds = new EnumMap<>(Sound.class);
    private SoundPool soundPool;
    private boolean released;
    protected float fVolume = 1.0f;
    protected boolean enabled = false;

    private static final float RATE_LIGHT_SQUARE = 1.2f;
    private static final float RATE_DARK_SQUARE = 0.84f; // ~minor third lower

    public Sounds(Context context) {
        this.context = context;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
        if (enabled && !released) {
            initSoundPool();
        }
    }

    public boolean isEnabled() {
        return enabled;
    }

    public void playTickTock() {
        playTickTock(null);
    }

    public void playTickTock(Runnable onComplete) {
        play(Sound.TickTock, 1f, onComplete);
    }

    public void playCheck() {
        playCheck(null);
    }

    public void playCheck(Runnable onComplete) {
        play(Sound.Check, 1f, onComplete);
    }

    public void playMove() {
        playMove(null);
    }

    public void playMove(Runnable onComplete) {
        play(Sound.Move, 1f, onComplete);
    }

    public void playCapture() {
        playCapture(null);
    }

    public void playCapture(Runnable onComplete) {
        play(Sound.Capture, 1f, onComplete);
    }

    public void playNewGame() {
        playNewGame(null);
    }

    public void playNewGame(Runnable onComplete) {
        play(Sound.NewGame, 1f, onComplete);
    }

    public void playIllegalMove() {
        playIllegalMove(null);
    }

    public void playIllegalMove(Runnable onComplete) {
        play(Sound.IllegalMove, 1f, onComplete);
    }

    public void playSelect() {
        playSelect(null);
    }

    public void playSelect(Runnable onComplete) {
        play(Sound.Select, 1f, onComplete);
    }

    public void playTick(boolean isDarkSquare) {
        playTick(isDarkSquare, null);
    }

    public void playTick(boolean isDarkSquare, Runnable onComplete) {
        play(isDarkSquare ? Sound.TickBlack : Sound.Tick,
            isDarkSquare ? RATE_DARK_SQUARE : RATE_LIGHT_SQUARE, onComplete);
    }

    public void playError() {
        playError(null);
    }

    public void playError(Runnable onComplete) {
        play(Sound.Error, 1f, onComplete);
    }

    public void playCorrect() {
        playCorrect(null);
    }

    public void playCorrect(Runnable onComplete) {
        play(Sound.Correct, 1f, onComplete);
    }

    public void playTickPiece(boolean isDarkSquare, boolean isBlackPiece) {
        playTickPiece(isDarkSquare, isBlackPiece, null);
    }

    public void playTickPiece(boolean isDarkSquare, boolean isBlackPiece, Runnable onComplete) {
        play(isBlackPiece ? Sound.TickPieceBlack : Sound.TickPiece,
            isDarkSquare ? RATE_DARK_SQUARE : RATE_LIGHT_SQUARE, onComplete);
    }

    public void playUnselect() {
        playUnselect(null);
    }

    public void playUnselect(Runnable onComplete) {
        play(Sound.Unselect, 1f, onComplete);
    }

    public void playBell() {
        playBell(null);
    }

    public void playBell(Runnable onComplete) {
        play(Sound.Bell, 1f, onComplete);
    }

    public void playLowTime() {
        playLowTime(null);
    }

    public void playLowTime(Runnable onComplete) {
        play(Sound.LowTime, 1f, onComplete);
    }

    public void playStopWatch() {
        playStopWatch(null);
    }

    public void playStopWatch(Runnable onComplete) {
        play(Sound.StopWatch, 1f, onComplete);
    }

    public void playNotification() {
        playNotification(null);
    }

    public void playNotification(Runnable onComplete) {
        play(Sound.Notification, 1f, onComplete);
    }

    protected void initSoundPool() {
        if (soundPool != null || released) {
            return;
        }
        AudioAttributes audioAttributes = new AudioAttributes.Builder()
            .setUsage(AudioAttributes.USAGE_GAME)
            .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
            .build();
        soundPool = new SoundPool.Builder()
            .setMaxStreams(8)
            .setAudioAttributes(audioAttributes)
            .build();
        for (Sound sound : Sound.values()) {
            try {
                soundIds.put(sound, soundPool.load(context, sound.resourceId, sound == Sound.Check ? 2 : 1));
            } catch (Exception e) {
                soundIds.put(sound, 0);
            }
        }
    }

    /**
     * Completion is estimated from duration and playback rate, not reported by SoundPool.
     * Disabled, unloaded or failed sounds complete immediately. Callbacks run on the main
     * thread, and release cancels pending callbacks. Other audio is not blocked by this timer.
     */
    private void play(Sound sound, float rate, Runnable onComplete) {
        if (released) {
            return;
        }
        int streamId = 0;
        Integer soundId = soundIds.get(sound);
        if (enabled && soundPool != null && soundId != null && soundId != 0) {
            streamId = soundPool.play(soundId, fVolume, fVolume, 1, 0, rate);
        }
        if (onComplete != null) {
            if (streamId != 0) {
                completionHandler.postDelayed(onComplete, Math.round(sound.durationMs / (double) rate));
            } else if (Looper.myLooper() == Looper.getMainLooper()) {
                onComplete.run();
            } else {
                completionHandler.post(onComplete);
            }
        }
    }

    public void release() {
        released = true;
        enabled = false;
        completionHandler.removeCallbacksAndMessages(null);
        if (soundPool != null) {
            soundPool.release();
            soundPool = null;
        }
        soundIds.clear();
    }
}
