package com.github.tvbox.osc.player.effect;

import android.media.audiofx.BassBoost;
import android.media.audiofx.Equalizer;
import android.media.audiofx.LoudnessEnhancer;
import android.media.audiofx.Virtualizer;
import android.util.Log;

import com.orhanobut.hawk.Hawk;

/**
 * 点播音效模式（参考影视仓：系统 AudioEffect 挂到播放器 audioSession）。
 * 0关闭 1标准 2音乐 3电影 4低音 5人声 6环绕
 */
public final class PlayerAudioFxHelper {
    private static final String TAG = "PlayerAudioFx";
    public static final String HAWK_KEY = "play_audio_fx_mode";

    public static final String[] MODE_NAMES = {
            "关闭", "标准", "音乐", "电影", "低音增强", "人声增强", "环绕"
    };

    /** 控制栏短文案 */
    public static String getButtonLabel() {
        int m = getMode();
        if (m <= 0) return "音效";
        return "音效·" + MODE_NAMES[m];
    }

    private static Equalizer equalizer;
    private static BassBoost bassBoost;
    private static Virtualizer virtualizer;
    private static LoudnessEnhancer loudness;
    private static int boundSession = 0;

    private PlayerAudioFxHelper() {}

    public static int getMode() {
        return Hawk.get(HAWK_KEY, 0);
    }

    public static String getModeName() {
        int m = getMode();
        if (m < 0 || m >= MODE_NAMES.length) m = 0;
        return MODE_NAMES[m];
    }

    public static int cycleMode() {
        int next = (getMode() + 1) % MODE_NAMES.length;
        Hawk.put(HAWK_KEY, next);
        return next;
    }

    public static void setMode(int mode) {
        if (mode < 0 || mode >= MODE_NAMES.length) mode = 0;
        Hawk.put(HAWK_KEY, mode);
        reapply();
    }

    public static synchronized void attach(int audioSessionId) {
        if (audioSessionId <= 0) return;
        if (boundSession == audioSessionId && equalizer != null) {
            applyMode(getMode());
            return;
        }
        release();
        boundSession = audioSessionId;
        try {
            equalizer = new Equalizer(0, audioSessionId);
            equalizer.setEnabled(false);
        } catch (Throwable t) {
            Log.w(TAG, "Equalizer init fail", t);
            equalizer = null;
        }
        try {
            bassBoost = new BassBoost(0, audioSessionId);
            bassBoost.setEnabled(false);
        } catch (Throwable t) {
            bassBoost = null;
        }
        try {
            virtualizer = new Virtualizer(0, audioSessionId);
            virtualizer.setEnabled(false);
        } catch (Throwable t) {
            virtualizer = null;
        }
        try {
            if (android.os.Build.VERSION.SDK_INT >= 19) {
                loudness = new LoudnessEnhancer(audioSessionId);
                loudness.setEnabled(false);
            }
        } catch (Throwable t) {
            loudness = null;
        }
        applyMode(getMode());
    }

    public static synchronized void reapply() {
        if (boundSession > 0) applyMode(getMode());
    }

    public static synchronized void release() {
        try { if (equalizer != null) { equalizer.setEnabled(false); equalizer.release(); } } catch (Throwable ignored) {}
        try { if (bassBoost != null) { bassBoost.setEnabled(false); bassBoost.release(); } } catch (Throwable ignored) {}
        try { if (virtualizer != null) { virtualizer.setEnabled(false); virtualizer.release(); } } catch (Throwable ignored) {}
        try { if (loudness != null) { loudness.setEnabled(false); loudness.release(); } } catch (Throwable ignored) {}
        equalizer = null;
        bassBoost = null;
        virtualizer = null;
        loudness = null;
        boundSession = 0;
    }

    private static void applyMode(int mode) {
        try {
            if (equalizer != null) equalizer.setEnabled(false);
            if (bassBoost != null) bassBoost.setEnabled(false);
            if (virtualizer != null) virtualizer.setEnabled(false);
            if (loudness != null) loudness.setEnabled(false);
            if (mode <= 0) return;

            short[] levels = null;
            short bass = 0;
            short virt = 0;
            int gainMb = 0;
            switch (mode) {
                case 1: // 标准
                    levels = new short[]{300, 200, 0, 200, 300};
                    break;
                case 2: // 音乐
                    levels = new short[]{500, 300, 0, 400, 500};
                    bass = 400;
                    break;
                case 3: // 电影
                    levels = new short[]{600, 200, 0, 300, 400};
                    bass = 550;
                    virt = 600;
                    gainMb = 300;
                    break;
                case 4: // 低音
                    levels = new short[]{900, 500, 0, -100, 0};
                    bass = 800;
                    break;
                case 5: // 人声
                    levels = new short[]{-200, 200, 700, 500, 0};
                    gainMb = 200;
                    break;
                case 6: // 环绕
                    levels = new short[]{400, 0, 200, 300, 500};
                    virt = 900;
                    bass = 300;
                    break;
                default:
                    return;
            }
            if (equalizer != null && levels != null) {
                short bands = equalizer.getNumberOfBands();
                short min = equalizer.getBandLevelRange()[0];
                short max = equalizer.getBandLevelRange()[1];
                for (short i = 0; i < bands && i < levels.length; i++) {
                    short lv = (short) Math.max(min, Math.min(max, levels[i]));
                    equalizer.setBandLevel(i, lv);
                }
                equalizer.setEnabled(true);
            }
            if (bassBoost != null && bass > 0) {
                bassBoost.setStrength(bass);
                bassBoost.setEnabled(true);
            }
            if (virtualizer != null && virt > 0) {
                virtualizer.setStrength(virt);
                virtualizer.setEnabled(true);
            }
            if (loudness != null && gainMb > 0 && android.os.Build.VERSION.SDK_INT >= 19) {
                loudness.setTargetGain(gainMb);
                loudness.setEnabled(true);
            }
        } catch (Throwable t) {
            Log.w(TAG, "applyMode fail", t);
        }
    }
}
