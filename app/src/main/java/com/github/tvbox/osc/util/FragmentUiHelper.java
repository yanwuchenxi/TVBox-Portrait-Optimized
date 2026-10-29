package com.github.tvbox.osc.util;

import android.app.Activity;

import androidx.fragment.app.Fragment;

/**
 * Fragment 安全切回主线程执行，避免 isAdded()==false 时崩溃。
 */
public final class FragmentUiHelper {

    private FragmentUiHelper() {
    }

    public static void runOnUiThread(Fragment fragment, Runnable action) {
        if (fragment == null || action == null) {
            return;
        }
        if (!fragment.isAdded()) {
            return;
        }
        Activity activity = fragment.getActivity();
        if (activity == null) {
            return;
        }
        activity.runOnUiThread(() -> {
            if (!fragment.isAdded()) {
                return;
            }
            action.run();
        });
    }
}
