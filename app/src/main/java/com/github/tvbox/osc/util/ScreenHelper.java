package com.github.tvbox.osc.util;

import android.app.Activity;
import android.content.Context;
import android.content.pm.ActivityInfo;
import android.content.res.Configuration;
import android.util.DisplayMetrics;

/**
 * 屏幕方向与手机形态辅助工具（优化方案新增）
 * <p>
 * 与现有 {@link ScreenUtils} 互补：
 * - ScreenUtils 负责 isTv() 判断
 * - 本类负责 竖屏/横屏判断、强制切换、尺寸换算
 * 将散落在 VodController / HomeActivity 等处的方向逻辑集中管理。
 */
public final class ScreenHelper {

    private ScreenHelper() {
    }

    /** 当前是否竖屏 */
    public static boolean isPortrait(Context context) {
        if (context == null) return true;
        return context.getResources().getConfiguration().orientation
                == Configuration.ORIENTATION_PORTRAIT;
    }

    /** 当前是否横屏 */
    public static boolean isLandscape(Context context) {
        return !isPortrait(context);
    }

    /** 是否为手机形态（非 TV） */
    public static boolean isMobile(Context context) {
        if (context == null) return true;
        return !ScreenUtils.isTv(context);
    }

    /** 强制竖屏（传感器） */
    public static void forcePortrait(Activity activity) {
        if (activity == null) return;
        activity.setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_SENSOR_PORTRAIT);
    }

    /** 强制横屏（传感器） */
    public static void forceLandscape(Activity activity) {
        if (activity == null) return;
        activity.setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE);
    }

    /**
     * 切换横竖屏
     * @return true = 切换后进入竖屏
     */
    public static boolean toggleOrientation(Activity activity) {
        if (activity == null) return true;
        if (isPortrait(activity)) {
            forceLandscape(activity);
            return false;
        } else {
            forcePortrait(activity);
            return true;
        }
    }

    public static int getScreenWidth(Context context) {
        return context.getResources().getDisplayMetrics().widthPixels;
    }

    public static int getScreenHeight(Context context) {
        return context.getResources().getDisplayMetrics().heightPixels;
    }

    public static int dp2px(Context context, float dp) {
        return (int) (dp * context.getResources().getDisplayMetrics().density + 0.5f);
    }
}
