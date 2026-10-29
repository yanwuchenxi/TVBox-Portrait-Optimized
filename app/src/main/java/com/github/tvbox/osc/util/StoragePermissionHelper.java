package com.github.tvbox.osc.util;

import android.Manifest;
import android.app.Activity;
import android.content.Context;
import android.content.pm.PackageManager;
import android.os.Build;
import android.os.Environment;

import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;

/**
 * 存储权限适配（配合 targetSdk 33）
 * <p>
 * Android 13+ 媒体权限拆分；Android 10+ 分区存储。
 * 本助手提供统一判断与请求入口，避免各页面重复 if-else。
 */
public final class StoragePermissionHelper {

    public static final int REQUEST_STORAGE = 0x5010;

    private StoragePermissionHelper() {
    }

    /** 是否已具备当前系统所需的读存储能力 */
    public static boolean hasReadPermission(Context context) {
        if (Build.VERSION.SDK_INT >= 33) {
            // 视频类应用主要读视频；若还要读图片可追加 READ_MEDIA_IMAGES
            return ContextCompat.checkSelfPermission(context, Manifest.permission.READ_MEDIA_VIDEO)
                    == PackageManager.PERMISSION_GRANTED
                    || ContextCompat.checkSelfPermission(context, Manifest.permission.READ_MEDIA_AUDIO)
                    == PackageManager.PERMISSION_GRANTED
                    || ContextCompat.checkSelfPermission(context, Manifest.permission.READ_EXTERNAL_STORAGE)
                    == PackageManager.PERMISSION_GRANTED;
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            return ContextCompat.checkSelfPermission(context, Manifest.permission.READ_EXTERNAL_STORAGE)
                    == PackageManager.PERMISSION_GRANTED;
        }
        return true;
    }

    /** 是否可写外部存储（传统路径；分区存储下多数场景不需要） */
    public static boolean hasWritePermission(Context context) {
        if (Build.VERSION.SDK_INT >= 30) {
            // Android 11+ 写公共目录需 MANAGE_EXTERNAL_STORAGE 或使用 MediaStore/SAF
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
     * 请求读权限（按 API 级别选择权限组）
     * @return true 表示已有权限，无需请求
     */
    public static boolean requestReadIfNeeded(Activity activity) {
        if (hasReadPermission(activity)) {
            return true;
        }
        if (Build.VERSION.SDK_INT >= 33) {
            ActivityCompat.requestPermissions(activity, new String[]{
                    Manifest.permission.READ_MEDIA_VIDEO,
                    Manifest.permission.READ_MEDIA_AUDIO
            }, REQUEST_STORAGE);
        } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            ActivityCompat.requestPermissions(activity, new String[]{
                    Manifest.permission.READ_EXTERNAL_STORAGE,
                    Manifest.permission.WRITE_EXTERNAL_STORAGE
            }, REQUEST_STORAGE);
        }
        return false;
    }

    /** 供 Manifest 参考的权限名（API 33+） */
    public static String[] mediaPermissionsApi33() {
        return new String[]{
                Manifest.permission.READ_MEDIA_VIDEO,
                Manifest.permission.READ_MEDIA_AUDIO
        };
    }
}
