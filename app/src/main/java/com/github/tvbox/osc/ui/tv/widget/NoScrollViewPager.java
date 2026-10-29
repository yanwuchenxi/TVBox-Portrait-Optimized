package com.github.tvbox.osc.ui.tv.widget;

import android.content.Context;
import android.util.AttributeSet;
import android.view.KeyEvent;
import android.view.MotionEvent;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.viewpager.widget.ViewPager;

/**
 * @author acer
 * @date 2018/7/24
 */

public class NoScrollViewPager extends ViewPager {

    public NoScrollViewPager(@NonNull Context context) {
        this(context, null);
    }

    public NoScrollViewPager(@NonNull Context context, @Nullable AttributeSet attrs) {
        super(context, attrs);
    }

    private boolean scrollable = false;

    /**
     * 手机竖屏模式下开启触摸滑动切换页面
     */
    public void setScrollable(boolean scrollable) {
        this.scrollable = scrollable;
    }

    /**
     * 禁止viewpager里面内容导致页面切换
     *
     * @param event
     * @return
     */
    @Override
    public boolean executeKeyEvent(KeyEvent event) {
        return false;
    }

    @Override
    public boolean onTouchEvent(MotionEvent ev) {
        return scrollable && super.onTouchEvent(ev);
    }

    @Override
    public boolean onInterceptTouchEvent(MotionEvent ev) {
        return scrollable && super.onInterceptTouchEvent(ev);
    }
}
