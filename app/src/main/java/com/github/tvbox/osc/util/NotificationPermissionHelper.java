package com.github.tvbox.osc.util;

import android.Manifest;
import android.app.Activity;
import android.content.Context;
import android.content.pm.PackageManager;
import android.os.Build;

import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;

/**
 * 通知权限（Android 13+ / API 33+ POST_NOTIFICATIONS）
 * 在 targetSdk 33+ 时，前台服务/下载进度通知等可能需要运行时授权。
 */
public final class NotificationPermissionHelper {

    public static final int REQUEST_NOTIFICATION = 0x5011;

    private static boolean sRequestedInProcess;

    private NotificationPermissionHelper() {
    }

    public static boolean hasPermission(Context context) {
        if (Build.VERSION.SDK_INT < 33) {
            return true;
        }
        return ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS)
                == PackageManager.PERMISSION_GRANTED;
    }

    /**
     * @return true 表示已有权限；false 表示已发起请求
     */
    public static boolean requestIfNeeded(Activity activity) {
        if (hasPermission(activity)) {
            return true;
        }
        if (sRequestedInProcess) {
            return false;
        }
        if (Build.VERSION.SDK_INT >= 33) {
            sRequestedInProcess = true;
            ActivityCompat.requestPermissions(activity,
                    new String[]{Manifest.permission.POST_NOTIFICATIONS},
                    REQUEST_NOTIFICATION);
        }
        return false;
    }

    /**
     * 启动媒体前台服务前调用（Android 13+ 建议先有通知权限，否则通知可能不显示）。
     * 不阻塞服务启动，仅尝试请求权限。
     */
    public static void ensureForMediaService(Activity activity) {
        if (activity == null) return;
        requestIfNeeded(activity);
    }
}

