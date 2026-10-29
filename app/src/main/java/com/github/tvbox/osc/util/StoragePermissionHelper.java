package com.github.tvbox.osc.util;

import android.Manifest;
import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.Build;
import android.os.Environment;
import android.provider.Settings;

import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;

/**
 * 存储权限适配（targetSdk 33/34）
 * <p>
 * 媒体权限（READ_MEDIA_*）只能访问音视频图片，不能列出 json/txt 等配置文件。
 * 本地配置选择需 Android 11+ 的「所有文件访问」MANAGE_EXTERNAL_STORAGE。
 */
public final class StoragePermissionHelper {

    public static final int REQUEST_STORAGE = 0x5010;
    public static final int REQUEST_MANAGE_STORAGE = 0x5012;

    private StoragePermissionHelper() {
    }

    /** 是否已具备浏览任意本地文件（含配置）的能力 */
    public static boolean hasAllFilesAccess(Context context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            return Environment.isExternalStorageManager();
        }
        return hasLegacyReadPermission(context);
    }

    /** 传统读权限（Android 10 及以下） */
    public static boolean hasLegacyReadPermission(Context context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            return ContextCompat.checkSelfPermission(context, Manifest.permission.READ_EXTERNAL_STORAGE)
                    == PackageManager.PERMISSION_GRANTED;
        }
        return true;
    }

    /**
     * 媒体读权限（仅音视频图片，不能读配置文件）。
     * 保留供播放本地媒体等场景使用。
     */
    public static boolean hasReadPermission(Context context) {
        if (Build.VERSION.SDK_INT >= 33) {
            return ContextCompat.checkSelfPermission(context, Manifest.permission.READ_MEDIA_VIDEO)
                    == PackageManager.PERMISSION_GRANTED
                    || ContextCompat.checkSelfPermission(context, Manifest.permission.READ_MEDIA_AUDIO)
                    == PackageManager.PERMISSION_GRANTED
                    || ContextCompat.checkSelfPermission(context, Manifest.permission.READ_MEDIA_IMAGES)
                    == PackageManager.PERMISSION_GRANTED
                    || hasLegacyReadPermission(context);
        }
        return hasLegacyReadPermission(context);
    }

    public static boolean hasWritePermission(Context context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            return Environment.isExternalStorageManager()
                    || ContextCompat.checkSelfPermission(context, Manifest.permission.WRITE_EXTERNAL_STORAGE)
                    == PackageManager.PERMISSION_GRANTED;
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            return ContextCompat.checkSelfPermission(context, Manifest.permission.WRITE_EXTERNAL_STORAGE)
                    == PackageManager.PERMISSION_GRANTED;
        }
        return true;
    }

    /**
     * 请求「能列出配置文件」的权限：
     * - API 30+：引导系统「所有文件访问」设置页
     * - 更低：请求 READ/WRITE_EXTERNAL_STORAGE
     * @return true 已有权限；false 已发起请求
     */
    public static boolean requestAllFilesAccessIfNeeded(Activity activity) {
        if (hasAllFilesAccess(activity)) {
            return true;
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            try {
                Intent intent = new Intent(Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION);
                intent.setData(Uri.parse("package:" + activity.getPackageName()));
                activity.startActivityForResult(intent, REQUEST_MANAGE_STORAGE);
            } catch (Throwable t) {
                try {
                    Intent intent = new Intent(Settings.ACTION_MANAGE_ALL_FILES_ACCESS_PERMISSION);
                    activity.startActivityForResult(intent, REQUEST_MANAGE_STORAGE);
                } catch (Throwable t2) {
                    // 最后退回传统权限请求
                    requestReadIfNeeded(activity);
                }
            }
            return false;
        }
        return requestReadIfNeeded(activity);
    }

    /**
     * 请求媒体读权限（不能用于配置文件浏览）
     * @return true 已有权限
     */
    public static boolean requestReadIfNeeded(Activity activity) {
        if (hasReadPermission(activity)) {
            return true;
        }
        if (Build.VERSION.SDK_INT >= 33) {
            ActivityCompat.requestPermissions(activity,
                    new String[]{
                            Manifest.permission.READ_MEDIA_VIDEO,
                            Manifest.permission.READ_MEDIA_AUDIO,
                            Manifest.permission.READ_MEDIA_IMAGES
                    },
                    REQUEST_STORAGE);
        } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            ActivityCompat.requestPermissions(activity,
                    new String[]{
                            Manifest.permission.READ_EXTERNAL_STORAGE,
                            Manifest.permission.WRITE_EXTERNAL_STORAGE
                    },
                    REQUEST_STORAGE);
        }
        return false;
    }
}
