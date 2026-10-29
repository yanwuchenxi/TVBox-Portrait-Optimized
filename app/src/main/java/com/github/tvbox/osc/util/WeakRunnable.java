package com.github.tvbox.osc.util;

import java.lang.ref.WeakReference;

/**
 * 弱引用 Runnable，避免 Handler.postDelayed 持有外部类导致泄漏。
 * <pre>
 * mHandler.postDelayed(new WeakRunnable&lt;&gt;(this, host -&gt; host.refresh()), 300);
 * </pre>
 */
public class WeakRunnable<T> implements Runnable {

    public interface Action<T> {
        void run(T host);
    }

    private final WeakReference<T> hostRef;
    private final Action<T> action;

    public WeakRunnable(T host, Action<T> action) {
        this.hostRef = new WeakReference<>(host);
        this.action = action;
    }

    @Override
    public void run() {
        T host = hostRef.get();
        if (host != null && action != null) {
            action.run(host);
        }
    }
}
