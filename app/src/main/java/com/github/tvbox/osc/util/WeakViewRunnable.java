package com.github.tvbox.osc.util;

import android.view.View;

import java.lang.ref.WeakReference;

/**
 * 持有 View 弱引用的 Runnable，避免 postDelayed 动画回调泄漏 Activity/View。
 */
public class WeakViewRunnable implements Runnable {

    public interface Action {
        void run(View view);
    }

    private final WeakReference<View> viewRef;
    private final Action action;

    public WeakViewRunnable(View view, Action action) {
        this.viewRef = new WeakReference<>(view);
        this.action = action;
    }

    @Override
    public void run() {
        View view = viewRef.get();
        if (view == null || action == null) {
            return;
        }
        // View 已从窗口分离时不再执行动画，减少无意义工作
        if (!view.isAttachedToWindow()) {
            return;
        }
        action.run(view);
    }
}
