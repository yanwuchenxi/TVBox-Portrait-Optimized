package com.github.tvbox.osc.player.controller;

import android.content.Context;
import android.os.Handler;
import android.view.MotionEvent;
import android.view.View;

import com.github.tvbox.osc.util.ScreenHelper;

/**
 * 竖屏播放手势辅助（从 VodController 提取并收敛）
 * <p>
 * 行为与原实现保持一致：
 * - 仅在竖屏生效
 * - 以纵向滑动为主（|dy| > |dx|）
 * - 上滑 → 下一集，下滑 → 上一集
 * - 阈值默认 80dp
 * - 触发后短暂显示顶部标题栏（由外部传入的 showTitleAction 控制）
 */
public class PortraitGestureHelper {

    private static final float SWIPE_THRESHOLD_DP = 80f;
    private static final long TITLE_SHOW_DURATION_MS = 3000L;

    private final Context context;
    private final Handler handler;
    private final float swipeThresholdPx;

    private boolean swipeTriggered;
    private Runnable showTitleAction;   // 显示标题（如 mTopRoot1.setVisibility(VISIBLE)）
    private Runnable hideTitleAction;   // 隐藏标题
    private Runnable hideTitleRunnable;

    public interface EpisodeSwitchListener {
        /** next = true 下一集，false 上一集 */
        void onSwitchEpisode(boolean next);
    }

    private EpisodeSwitchListener listener;
    /** 额外条件：是否允许处理手势（对应原 canHandleGesture） */
    private GestureGate gestureGate;

    public interface GestureGate {
        boolean canHandle(MotionEvent e1);
    }

    public PortraitGestureHelper(Context context, Handler handler) {
        this.context = context;
        this.handler = handler;
        this.swipeThresholdPx = context.getResources().getDisplayMetrics().density * SWIPE_THRESHOLD_DP;
    }

    public void setEpisodeSwitchListener(EpisodeSwitchListener listener) {
        this.listener = listener;
    }

    public void setGestureGate(GestureGate gate) {
        this.gestureGate = gate;
    }

    /** 设置显示/隐藏标题的动作（保持与原 mTopRoot1 逻辑兼容） */
    public void setTitleActions(Runnable show, Runnable hide) {
        this.showTitleAction = show;
        this.hideTitleAction = hide;
    }

    public void onDown() {
        swipeTriggered = false;
    }

    /**
     * @return true 表示已消费本次滑动（竖屏切集）
     */
    public boolean handleScroll(MotionEvent e1, MotionEvent e2) {
        if (e1 == null || e2 == null) return false;
        if (!ScreenHelper.isPortrait(context)) return false;
        if (gestureGate != null && !gestureGate.canHandle(e1)) return false;

        float dy = e2.getY() - e1.getY();
        float dx = e2.getX() - e1.getX();
        // 必须是纵向主导
        if (Math.abs(dy) <= Math.abs(dx)) return false;

        if (Math.abs(dy) < swipeThresholdPx) {
            return true; // 仍消费，避免与亮度/音量手势冲突，但不触发切集
        }
        if (swipeTriggered) return true;

        swipeTriggered = true;
        boolean next = dy < 0; // 上滑下一集，下滑上一集
        if (listener != null) {
            listener.onSwitchEpisode(next);
        }
        showTitleTemporarily();
        return true;
    }

    private void showTitleTemporarily() {
        if (handler == null) return;
        if (showTitleAction != null) {
            showTitleAction.run();
        }
        if (hideTitleRunnable != null) {
            handler.removeCallbacks(hideTitleRunnable);
        }
        hideTitleRunnable = () -> {
            if (hideTitleAction != null) {
                hideTitleAction.run();
            }
        };
        handler.postDelayed(hideTitleRunnable, TITLE_SHOW_DURATION_MS);
    }

    public void release() {
        if (handler != null && hideTitleRunnable != null) {
            handler.removeCallbacks(hideTitleRunnable);
        }
        hideTitleRunnable = null;
        listener = null;
        gestureGate = null;
        showTitleAction = null;
        hideTitleAction = null;
    }
}
