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
import com.github.tvbox.osc.ui.activity.SearchActivity;
import com.github.tvbox.osc.ui.activity.SettingActivity;

/**
 * 底部导航栏统一处理
 */
public class BottomNavHelper {

    /**
     * 返回首页（复用首页实例，不销毁当前页面，保证底部导航栏视觉固定）
     */
    public static void goHome(BaseActivity activity) {
        if (activity instanceof HomeActivity) return;
        Intent intent = new Intent(activity, HomeActivity.class);
        intent.addFlags(Intent.FLAG_ACTIVITY_REORDER_TO_FRONT);
        activity.startActivity(intent);
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

        View.OnClickListener listener = new View.OnClickListener() {
            @Override
            public void onClick(View v) {
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
                // 复用已有页面实例并取消切换动画，保持底部导航栏视觉固定
                Intent intent = new Intent(activity, target);
                intent.addFlags(Intent.FLAG_ACTIVITY_REORDER_TO_FRONT);
                activity.startActivity(intent);
                activity.overridePendingTransition(0, 0);
            }
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
        // 选中项使用与首页分类一致的胶囊背景高亮
        item.setBackgroundResource(selected ? R.drawable.bg_nav_item_selected : android.R.color.transparent);
    }
}
