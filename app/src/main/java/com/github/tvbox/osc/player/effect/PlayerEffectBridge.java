package com.github.tvbox.osc.player.effect;

import android.view.View;

import java.lang.reflect.Field;
import java.lang.reflect.Method;

import xyz.doikki.videoplayer.player.AbstractPlayer;
import xyz.doikki.videoplayer.player.VideoView;

/**
 * 从 VideoView / 底层播放器反射取 audioSessionId，并应用音效+臻彩。
 */
public final class PlayerEffectBridge {
    private PlayerEffectBridge() {}

    public static void applyAll(VideoView<?> videoView) {
        if (videoView == null) return;
        int session = resolveAudioSessionId(videoView);
        if (session > 0) {
            PlayerAudioFxHelper.attach(session);
        }
        PlayerZhenCaiHelper.applyToVideoView(videoView);
    }

    public static void release() {
        PlayerAudioFxHelper.release();
    }

    public static int resolveAudioSessionId(VideoView<?> videoView) {
        try {
            AbstractPlayer player = getMediaPlayer(videoView);
            if (player == null) return 0;
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
            // generic
            try {
                Method m = player.getClass().getMethod("getAudioSessionId");
                Object r = m.invoke(player);
                if (r instanceof Integer && (Integer) r > 0) return (Integer) r;
            } catch (Throwable ignored) {}
        } catch (Throwable ignored) {}
        return 0;
    }

    @SuppressWarnings("unchecked")
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
