package com.github.tvbox.osc.ui.activity;

import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.fragment.app.Fragment;
import androidx.fragment.app.FragmentTransaction;

import com.github.tvbox.osc.R;
import com.github.tvbox.osc.base.BaseActivity;
import com.github.tvbox.osc.base.BaseLazyFragment;
import com.github.tvbox.osc.ui.fragment.HistoryTabFragment;
import com.github.tvbox.osc.ui.fragment.HomeTabFragment;
import com.github.tvbox.osc.ui.fragment.LiveTabFragment;
import com.github.tvbox.osc.ui.fragment.SettingTabFragment;
import com.github.tvbox.osc.util.FastClickCheckUtil;

/**
 * 单 Activity + 多 Fragment：底部导航栏只存在一份，切换 Tab 不再整页覆盖。
 */
public class MainActivity extends BaseActivity {
    public static final String EXTRA_TAB = "main_tab";
    public static final int TAB_HOME = 0;
    public static final int TAB_LIVE = 1;
    public static final int TAB_HISTORY = 2;
    public static final int TAB_SETTING = 3;

    private BaseLazyFragment homeFragment;
    private BaseLazyFragment liveFragment;
    private BaseLazyFragment historyFragment;
    private BaseLazyFragment settingFragment;
    private BaseLazyFragment currentFragment;
    private int currentTab = TAB_HOME;

    @Override
    protected int getLayoutResID() {
        return R.layout.activity_main;
    }

    @Override
    protected boolean useImmersive() {
        return false;
    }

    @Override
    protected boolean shouldRefreshAutoSize() {
        return true;
    }

    @Override
    protected void init() {
        changeWallpaper(false);
        setupBottomNav();
        int tab = TAB_HOME;
        Intent intent = getIntent();
        if (intent != null) {
            tab = intent.getIntExtra(EXTRA_TAB, TAB_HOME);
        }
        switchTab(tab, false);
    }

    @Override
    protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        setIntent(intent);
        if (intent != null && intent.hasExtra(EXTRA_TAB)) {
            switchTab(intent.getIntExtra(EXTRA_TAB, TAB_HOME), false);
        }
    }

    public void switchTab(int tab) {
        switchTab(tab, true);
    }

    private void switchTab(int tab, boolean fromClick) {
        if (tab < TAB_HOME || tab > TAB_SETTING) tab = TAB_HOME;
        BaseLazyFragment target = fragmentFor(tab);
        if (target == null) return;
        FragmentTransaction ft = getSupportFragmentManager().beginTransaction();
        if (currentFragment == target && currentFragment.isAdded()) {
            updateNavSelected(tab);
            currentTab = tab;
            return;
        }
        if (currentFragment != null && currentFragment.isAdded()) {
            ft.hide(currentFragment);
        }
        if (!target.isAdded()) {
            ft.add(R.id.tab_container, target, "tab_" + tab);
        } else {
            ft.show(target);
        }
        ft.commitAllowingStateLoss();
        currentFragment = target;
        currentTab = tab;
        updateNavSelected(tab);
    }

    private BaseLazyFragment fragmentFor(int tab) {
        switch (tab) {
            case TAB_LIVE:
                if (liveFragment == null) liveFragment = new LiveTabFragment();
                return liveFragment;
            case TAB_HISTORY:
                if (historyFragment == null) historyFragment = new HistoryTabFragment();
                return historyFragment;
            case TAB_SETTING:
                if (settingFragment == null) settingFragment = new SettingTabFragment();
                return settingFragment;
            case TAB_HOME:
            default:
                if (homeFragment == null) homeFragment = new HomeTabFragment();
                return homeFragment;
        }
    }

    private void setupBottomNav() {
        View navHome = findViewById(R.id.nav_home);
        View navLive = findViewById(R.id.nav_live);
        View navHistory = findViewById(R.id.nav_history);
        View navSetting = findViewById(R.id.nav_setting);
        if (navHome == null) return;
        View.OnClickListener listener = v -> {
            FastClickCheckUtil.check(v);
            int id = v.getId();
            if (id == R.id.nav_home) switchTab(TAB_HOME);
            else if (id == R.id.nav_live) switchTab(TAB_LIVE);
            else if (id == R.id.nav_history) switchTab(TAB_HISTORY);
            else if (id == R.id.nav_setting) switchTab(TAB_SETTING);
        };
        navHome.setOnClickListener(listener);
        navLive.setOnClickListener(listener);
        navHistory.setOnClickListener(listener);
        navSetting.setOnClickListener(listener);
    }

    private void updateNavSelected(int tab) {
        updateNavItem(R.id.nav_home, tab == TAB_HOME);
        updateNavItem(R.id.nav_live, tab == TAB_LIVE);
        updateNavItem(R.id.nav_history, tab == TAB_HISTORY);
        updateNavItem(R.id.nav_setting, tab == TAB_SETTING);
    }

    private void updateNavItem(int id, boolean selected) {
        LinearLayout item = findViewById(id);
        if (item == null || item.getChildCount() < 2) return;
        ImageView iv = (ImageView) item.getChildAt(0);
        TextView tv = (TextView) item.getChildAt(1);
        int color = selected ? R.color.color_FFFFFF : R.color.color_BBFFFFFF;
        iv.setColorFilter(getResources().getColor(color));
        tv.setTextColor(getResources().getColor(color));
        item.setBackgroundResource(selected ? R.drawable.bg_nav_item_selected : android.R.color.transparent);
    }

    @Override
    public void onBackPressed() {
        if (currentTab != TAB_HOME) {
            switchTab(TAB_HOME);
            return;
        }
        super.onBackPressed();
    }
}
