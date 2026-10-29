package com.github.tvbox.osc.util;

import xyz.doikki.videoplayer.player.VideoView;

/**
 * 播放器状态码映射（从 PlayFragment.getMediaInfo 抽出）
 * 对外协议：3 播放中 / 2 暂停或已缓冲 / 1 缓冲中 / -1 其它
 */
public final class PlayerStateHelper {

    private PlayerStateHelper() {
    }

    public static final int STATE_PLAYING = 3;
    public static final int STATE_PAUSED_OR_READY = 2;
    public static final int STATE_BUFFERING = 1;
    public static final int STATE_OTHER = -1;

    public static int mapPlayState(VideoView videoView) {
        if (videoView == null) {
            return STATE_OTHER;
        }
        int playState = videoView.getCurrentPlayState();
        if (videoView.isPlaying()) {
            return STATE_PLAYING;
        }
        if (playState == VideoView.STATE_BUFFERING) {
            return STATE_BUFFERING;
        }
        if (playState == VideoView.STATE_PAUSED
                || playState == VideoView.STATE_PREPARED
                || playState == VideoView.STATE_BUFFERED) {
            return STATE_PAUSED_OR_READY;
        }
        return STATE_OTHER;
    }
}
