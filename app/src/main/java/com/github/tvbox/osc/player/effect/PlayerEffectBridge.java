package com.github.tvbox.osc.player.effect;

import android.os.Handler;
import android.os.Looper;
import android.util.Log;
import android.view.View;

import java.lang.reflect.Field;
import java.lang.reflect.Method;

import xyz.doikki.videoplayer.player.AbstractPlayer;
import xyz.doikki.videoplayer.player.VideoView;

/**
 * 从 VideoView / 底层播放器取 audioSessionId，应用音效 + 臻彩。
 */
public final class PlayerEffectBridge {
    private static final String TAG = "PlayerEffectBridge";
    private static final Handler mainHandler = new Handler(Looper.getMainLooper());
    private static int applyToken = 0;

    private PlayerEffectBridge() {}

    public static void applyAll(VideoView<?> videoView) {
        if (videoView == null) return;
        PlayerZhenCaiHelper.applyToVideoView(videoView);
        int session = resolveAudioSessionId(videoView);
        Log.i(TAG, "applyAll session=" + session);
        if (session > 0) {
            PlayerAudioFxHelper.attach(session);
        } else {
            // 起播瞬间 session 可能为 0，延迟重试
            scheduleRetry(videoView, 6);
        }
    }

    private static void scheduleRetry(final VideoView<?> videoView, final int left) {
        if (left <= 0 || videoView == null) return;
        final int token = ++applyToken;
        mainHandler.postDelayed(new Runnable() {
            @Override
            public void run() {
                if (token != applyToken) return;
                int session = resolveAudioSessionId(videoView);
                Log.i(TAG, "retry session=" + session + " left=" + left);
                if (session > 0) {
                    PlayerAudioFxHelper.attach(session);
                } else {
                    scheduleRetry(videoView, left - 1);
                }
            }
        }, 500);
    }

    public static void release() {
        applyToken++;
        PlayerAudioFxHelper.release();
    }

    public static int resolveAudioSessionId(VideoView<?> videoView) {
        try {
            AbstractPlayer player = getMediaPlayer(videoView);
            if (player == null) return 0;

            // ExoMediaPlayer.getAudioSessionId()（我们新增的）
            try {
                Method m = player.getClass().getMethod("getAudioSessionId");
                Object r = m.invoke(player);
                if (r instanceof Integer && (Integer) r > 0) return (Integer) r;
            } catch (Throwable ignored) {}

            // Exo: mInternalPlayer.getAudioSessionId()
            try {
                Field f = player.getClass().getDeclaredField("mInternalPlayer");
                f.setAccessible(true);
                Object exo = f.get(player);
                if (exo != null) {
                    Method m = exo.getClass().getMethod("getAudioSessionId");
                    Object r = m.invoke(exo);
                    if (r instanceof Integer && (Integer) r > 0) return (Integer) r;
                }
            } catch (Throwable ignored) {}

            // Ijk: mMediaPlayer.getAudioSessionId()
            try {
                Field f = player.getClass().getDeclaredField("mMediaPlayer");
                f.setAccessible(true);
                Object ijk = f.get(player);
                if (ijk != null) {
                    Method m = ijk.getClass().getMethod("getAudioSessionId");
                    Object r = m.invoke(ijk);
                    if (r instanceof Integer && (Integer) r > 0) return (Integer) r;
                }
            } catch (Throwable ignored) {}
        } catch (Throwable t) {
            Log.w(TAG, "resolveAudioSessionId", t);
        }
        return 0;
    }

    private static AbstractPlayer getMediaPlayer(VideoView<?> videoView) {
        try {
            Field f = VideoView.class.getDeclaredField("mMediaPlayer");
            f.setAccessible(true);
            return (AbstractPlayer) f.get(videoView);
        } catch (Throwable t) {
            return null;
        }
    }
}
