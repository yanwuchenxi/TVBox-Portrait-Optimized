package com.github.tvbox.osc.util;

import android.os.Handler;
import android.os.Looper;
import android.os.Message;

import androidx.annotation.NonNull;

import java.lang.ref.WeakReference;

/**
 * 弱引用回调 Handler，降低 Activity/Fragment 泄漏风险。
 * 用法：
 * <pre>
 * SafeHandler&lt;MyActivity&gt; handler = new SafeHandler&lt;&gt;(this, (host, msg) -> { ... });
 * </pre>
 */
public class SafeHandler<T> extends Handler {

    public interface Callback<T> {
        void handleMessage(@NonNull T host, @NonNull Message msg);
    }

    private final WeakReference<T> hostRef;
    private final Callback<T> callback;

    public SafeHandler(@NonNull T host, @NonNull Callback<T> callback) {
        super(Looper.getMainLooper());
        this.hostRef = new WeakReference<>(host);
        this.callback = callback;
    }

    public SafeHandler(@NonNull Looper looper, @NonNull T host, @NonNull Callback<T> callback) {
        super(looper);
        this.hostRef = new WeakReference<>(host);
        this.callback = callback;
    }

    @Override
    public void handleMessage(@NonNull Message msg) {
        T host = hostRef.get();
        if (host == null) {
            return;
        }
        callback.handleMessage(host, msg);
    }

    /** 销毁时调用，移除全部消息 */
    public void clear() {
        removeCallbacksAndMessages(null);
        hostRef.clear();
    }
}
