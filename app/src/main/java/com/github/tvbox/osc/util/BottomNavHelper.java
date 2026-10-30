package com.github.tvbox.osc.util;

import android.content.Intent;
import android.view.View;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import com.github.tvbox.osc.R;
import com.github.tvbox.osc.base.BaseActivity;
import com.github.tvbox.osc.ui.activity.HistoryActivity;
import com.github.tvbox.osc.ui.activity.HomeActivity;
import com.github.tvbox.osc.ui.activity.LivePlayActivity;
import com.github.tvbox.osc.ui.activity.SettingActivity;

public class BottomNavHelper {

    private static Intent tabIntent(BaseActivity activity, Class<? extends BaseActivity> target) {
        Intent intent = new Intent(activity, target);
        intent.addFlags(Intent.FLAG_ACTIVITY_REORDER_TO_FRONT
                | Intent.FLAG_ACTIVITY_NO_ANIMATION
                | Intent.FLAG_ACTIVITY_SINGLE_TOP);
        return intent;
    }

    public static void goHome(BaseActivity activity) {
        if (activity instanceof HomeActivity) return;
        activity.startActivity(tabIntent(activity, HomeActivity.class));
        activity.overridePendingTransition(0, 0);
    }

    public static void setup(final BaseActivity activity, int selectedId) {
        LinearLayout navHome = activity.findViewById(R.id.nav_home);
        LinearLayout navLive = activity.findViewById(R.id.nav_live);
        LinearLayout navHistory = activity.findViewById(R.id.nav_history);
        LinearLayout navSetting = activity.findViewById(R.id.nav_setting);
        if (navHome == null) return;

        updateNavItem(activity, navHome, selectedId == R.id.nav_home);
        updateNavItem(activity, navLive, selectedId == R.id.nav_live);
        updateNavItem(activity, navHistory, selectedId == R.id.nav_history);
        updateNavItem(activity, navSetting, selectedId == R.id.nav_setting);

        View.OnClickListener listener = v -> {
            FastClickCheckUtil.check(v);
            int id = v.getId();
            Class<? extends BaseActivity> target = null;
            if (id == R.id.nav_home) {
                if (!(activity instanceof HomeActivity)) target = HomeActivity.class;
            } else if (id == R.id.nav_live) {
                if (!(activity instanceof LivePlayActivity)) target = LivePlayActivity.class;
            } else if (id == R.id.nav_history) {
                if (!(activity instanceof HistoryActivity)) target = HistoryActivity.class;
            } else if (id == R.id.nav_setting) {
                if (!(activity instanceof SettingActivity)) target = SettingActivity.class;
            }
            if (target == null) return;
            activity.startActivity(tabIntent(activity, target));
            activity.overridePendingTransition(0, 0);
        };
        navHome.setOnClickListener(listener);
        navLive.setOnClickListener(listener);
        navHistory.setOnClickListener(listener);
        navSetting.setOnClickListener(listener);
    }

    private static void updateNavItem(BaseActivity activity, LinearLayout item, boolean selected) {
        if (item == null || item.getChildCount() < 2) return;
        ImageView iv = (ImageView) item.getChildAt(0);
        TextView tv = (TextView) item.getChildAt(1);
        int color = selected ? R.color.color_FFFFFF : R.color.color_BBFFFFFF;
        iv.setColorFilter(activity.getResources().getColor(color));
        tv.setTextColor(activity.getResources().getColor(color));
        item.setBackgroundResource(selected ? R.drawable.bg_nav_item_selected : android.R.color.transparent);
    }
}
