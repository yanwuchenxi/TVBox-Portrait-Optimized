package com.github.tvbox.osc.player.effect;

import android.media.audiofx.BassBoost;
import android.media.audiofx.Equalizer;
import android.media.audiofx.LoudnessEnhancer;
import android.media.audiofx.Virtualizer;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;

import com.orhanobut.hawk.Hawk;

/**
 * 音效模式 — 对齐影视仓：关闭 / 影院 / 重低音 / 3D环绕 / HiFi / 人声
 * 通过系统 AudioEffect 挂到播放器 audioSessionId。
 */
public final class PlayerAudioFxHelper {
    private static final String TAG = "PlayerAudioFx";
    public static final String HAWK_KEY = "play_audio_fx_mode";

    /** 与影视仓 MobileAudioEffect 一致 */
    public static final String[] MODE_NAMES = {
            "关闭", "影院", "重低音", "3D环绕", "HiFi", "人声"
    };

    private static Equalizer equalizer;
    private static BassBoost bassBoost;
    private static Virtualizer virtualizer;
    private static LoudnessEnhancer loudness;
    private static int boundSession = 0;
    private static short[] bandLevels;
    private static short bassStrength;
    private static short virtStrength;
    private static int loudnessMb;
    private static boolean bassSupported;
    private static boolean virtSupported;
    private static final Handler mainHandler = new Handler(Looper.getMainLooper());
    private static int attachToken = 0;

    private PlayerAudioFxHelper() {}

    public static int getMode() {
        int m = Hawk.get(HAWK_KEY, 0);
        if (m < 0 || m >= MODE_NAMES.length) m = 0;
        return m;
    }

    public static String getModeName() {
        return MODE_NAMES[getMode()];
    }

    public static String getButtonLabel() {
        int m = getMode();
        if (m <= 0) return "音效";
        return "音效·" + MODE_NAMES[m];
    }

    public static int cycleMode() {
        int next = (getMode() + 1) % MODE_NAMES.length;
        setMode(next);
        return next;
    }

    public static void setMode(int mode) {
        if (mode < 0 || mode >= MODE_NAMES.length) mode = 0;
        Hawk.put(HAWK_KEY, mode);
        preparePreset(mode);
        applyStrength(mode <= 0 ? 0f : 1f);
        if (mode > 0) {
            enableEffects(true);
        } else {
            enableEffects(false);
        }
    }

    public static synchronized void attach(int audioSessionId) {
        if (audioSessionId <= 0) {
            Log.w(TAG, "attach skip session=" + audioSessionId);
            return;
        }
        if (boundSession == audioSessionId && equalizer != null) {
            setMode(getMode());
            return;
        }
        releaseInternal();
        boundSession = audioSessionId;
        try {
            equalizer = new Equalizer(0, audioSessionId);
            equalizer.setEnabled(false);
        } catch (Throwable t) {
            Log.w(TAG, "Equalizer fail", t);
            equalizer = null;
        }
        try {
            bassBoost = new BassBoost(0, audioSessionId);
            bassSupported = bassBoost.getStrengthSupported();
            bassBoost.setEnabled(false);
        } catch (Throwable t) {
            bassBoost = null;
            bassSupported = false;
        }
        try {
            virtualizer = new Virtualizer(0, audioSessionId);
            virtSupported = virtualizer.getStrengthSupported();
            virtualizer.setEnabled(false);
        } catch (Throwable t) {
            virtualizer = null;
            virtSupported = false;
        }
        try {
            if (android.os.Build.VERSION.SDK_INT >= 19) {
                loudness = new LoudnessEnhancer(audioSessionId);
                loudness.setEnabled(false);
            }
        } catch (Throwable t) {
            loudness = null;
        }
        Log.i(TAG, "attached session=" + audioSessionId
                + " eq=" + (equalizer != null)
                + " bass=" + bassSupported
                + " virt=" + virtSupported);
        setMode(getMode());
    }

    /** 起播后重试挂接，解决 session 尚未就绪 */
    public static void attachWithRetry(final int sessionId, final int attemptsLeft) {
        if (sessionId > 0) {
            attach(sessionId);
            if (boundSession == sessionId && equalizer != null) return;
        }
        if (attemptsLeft <= 0) return;
        final int token = ++attachToken;
        mainHandler.postDelayed(new Runnable() {
            @Override
            public void run() {
                if (token != attachToken) return;
                attachWithRetry(sessionId, attemptsLeft - 1);
            }
        }, 400);
    }

    public static synchronized void reapply() {
        if (boundSession > 0) setMode(getMode());
    }

    public static synchronized void release() {
        attachToken++;
        releaseInternal();
    }

    private static void releaseInternal() {
        try { if (equalizer != null) { equalizer.setEnabled(false); equalizer.release(); } } catch (Throwable ignored) {}
        try { if (bassBoost != null) { bassBoost.setEnabled(false); bassBoost.release(); } } catch (Throwable ignored) {}
        try { if (virtualizer != null) { virtualizer.setEnabled(false); virtualizer.release(); } } catch (Throwable ignored) {}
        try { if (loudness != null) { loudness.setEnabled(false); loudness.release(); } } catch (Throwable ignored) {}
        equalizer = null;
        bassBoost = null;
        virtualizer = null;
        loudness = null;
        boundSession = 0;
        bandLevels = null;
        bassStrength = 0;
        virtStrength = 0;
        loudnessMb = 0;
    }

    /** 预设参数（单位 mB / BassBoost strength 0-1000） */
    private static void preparePreset(int mode) {
        // 默认清空
        bandLevels = null;
        bassStrength = 0;
        virtStrength = 0;
        loudnessMb = 0;
        switch (mode) {
            case 1: // 影院：低频+环绕感
                bandLevels = new short[]{600, 300, 0, 200, 400};
                bassStrength = 550;
                virtStrength = 650;
                loudnessMb = 400;
                break;
            case 2: // 重低音
                bandLevels = new short[]{1200, 700, 100, -100, 0};
                bassStrength = 900;
                virtStrength = 200;
                loudnessMb = 300;
                break;
            case 3: // 3D环绕
                bandLevels = new short[]{400, 100, 150, 350, 600};
                bassStrength = 350;
                virtStrength = 950;
                loudnessMb = 250;
                break;
            case 4: // HiFi：均衡清晰
                bandLevels = new short[]{200, 150, 100, 250, 350};
                bassStrength = 200;
                virtStrength = 150;
                loudnessMb = 150;
                break;
            case 5: // 人声
                bandLevels = new short[]{-300, 200, 800, 600, 100};
                bassStrength = 100;
                virtStrength = 100;
                loudnessMb = 350;
                break;
            default:
                break;
        }
    }

    private static void applyStrength(float factor) {
        if (factor < 0f) factor = 0f;
        if (factor > 1f) factor = 1f;
        try {
            if (equalizer != null && bandLevels != null) {
                short bands = equalizer.getNumberOfBands();
                short min = equalizer.getBandLevelRange()[0];
                short max = equalizer.getBandLevelRange()[1];
                int n = Math.min(bands, bandLevels.length);
                for (short i = 0; i < n; i++) {
                    short lv = (short) Math.round(bandLevels[i] * factor);
                    if (lv < min) lv = min;
                    if (lv > max) lv = max;
                    equalizer.setBandLevel(i, lv);
                }
            }
            if (bassBoost != null && bassSupported && bassStrength > 0) {
                bassBoost.setStrength((short) Math.round(bassStrength * factor));
            }
            if (virtualizer != null && virtSupported && virtStrength > 0) {
                virtualizer.setStrength((short) Math.round(virtStrength * factor));
            }
            if (loudness != null && loudnessMb > 0 && android.os.Build.VERSION.SDK_INT >= 19) {
                loudness.setTargetGain(Math.round(loudnessMb * factor));
            }
        } catch (Throwable t) {
            Log.w(TAG, "applyStrength fail", t);
        }
    }

    private static void enableEffects(boolean on) {
        try {
            if (equalizer != null) equalizer.setEnabled(on && bandLevels != null);
            if (bassBoost != null) bassBoost.setEnabled(on && bassSupported && bassStrength > 0);
            if (virtualizer != null) virtualizer.setEnabled(on && virtSupported && virtStrength > 0);
            if (loudness != null) loudness.setEnabled(on && loudnessMb > 0);
        } catch (Throwable t) {
            Log.w(TAG, "enableEffects fail", t);
        }
    }

    public static int getBoundSession() {
        return boundSession;
    }
}
