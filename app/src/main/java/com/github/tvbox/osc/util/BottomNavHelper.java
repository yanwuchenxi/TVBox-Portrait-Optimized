package com.github.tvbox.osc.util;

import android.app.Activity;
import android.content.Intent;

import com.github.tvbox.osc.base.BaseActivity;
import com.github.tvbox.osc.ui.activity.MainActivity;

/**
 * 兼容旧调用：全部导流到 MainActivity 对应 Tab。
 */
public class BottomNavHelper {

    public static void goHome(BaseActivity activity) {
        openTab(activity, MainActivity.TAB_HOME);
    }

    public static void setup(final BaseActivity activity, int selectedId) {
        // 旧 Activity 内嵌导航已废弃；若仍被调用则导流到 MainActivity
        int tab = MainActivity.TAB_HOME;
        if (selectedId == com.github.tvbox.osc.R.id.nav_live) tab = MainActivity.TAB_LIVE;
        else if (selectedId == com.github.tvbox.osc.R.id.nav_history) tab = MainActivity.TAB_HISTORY;
        else if (selectedId == com.github.tvbox.osc.R.id.nav_setting) tab = MainActivity.TAB_SETTING;
        if (!(activity instanceof MainActivity)) {
            openTab(activity, tab);
            activity.finish();
        }
    }

    public static void openTab(Activity activity, int tab) {
        if (activity == null) return;
        if (activity instanceof MainActivity) {
            ((MainActivity) activity).switchTab(tab);
            return;
        }
        Intent intent = new Intent(activity, MainActivity.class);
        intent.putExtra(MainActivity.EXTRA_TAB, tab);
        intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_SINGLE_TOP | Intent.FLAG_ACTIVITY_NO_ANIMATION);
        activity.startActivity(intent);
        activity.overridePendingTransition(0, 0);
    }
}
