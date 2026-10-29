package com.github.tvbox.osc.util;

import android.content.Context;
import android.database.Cursor;
import android.net.Uri;
import android.os.Environment;
import android.provider.DocumentsContract;
import android.provider.OpenableColumns;
import android.text.TextUtils;

import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;

/**
 * 本地配置 Uri → clan:// API 路径（从 ModelSettingFragment 抽出）
 * 统一 file://、Document Uri、content:// 复制到缓存目录的逻辑。
 */
public final class ConfigPathHelper {

    private ConfigPathHelper() {
    }

    /**
     * 将选中的配置文件 Uri 转为 TVBox 可用的 clan://localhost/... 地址。
     */
    public static String uriToClanApi(Context context, Uri uri) {
        if (context == null || uri == null) {
            return "";
        }
        String path = getPathFromUri(context, uri);
        if (TextUtils.isEmpty(path)) {
            path = copyUriToLocalConfig(context, uri);
        }
        if (TextUtils.isEmpty(path)) {
            return "";
        }
        String storageRoot = Environment.getExternalStorageDirectory().getAbsolutePath();
        if (path.startsWith(storageRoot)) {
            return toClanUrl(path, storageRoot);
        }
        path = copyUriToLocalConfig(context, uri);
        if (!TextUtils.isEmpty(path) && path.startsWith(storageRoot)) {
            return toClanUrl(path, storageRoot);
        }
        // 缓存目录可能不在 external storage root 下，仍返回 file 形式 clan（相对路径失败时给空）
        if (!TextUtils.isEmpty(path)) {
            // 尝试用绝对路径包装：部分源支持 file://
            return "file://" + path;
        }
        return "";
    }

    public static String toClanUrl(String absolutePath, String storageRoot) {
        return "clan://localhost/" + absolutePath.substring(storageRoot.length()).replaceFirst("^/+", "");
    }

    public static String getPathFromUri(Context context, Uri uri) {
        try {
            if ("file".equalsIgnoreCase(uri.getScheme())) {
                return uri.getPath();
            }
            if (DocumentsContract.isDocumentUri(context, uri)) {
                String docId = DocumentsContract.getDocumentId(uri);
                if ("com.android.externalstorage.documents".equals(uri.getAuthority())) {
                    String[] split = docId.split(":");
                    if (split.length > 1 && "primary".equalsIgnoreCase(split[0])) {
                        return Environment.getExternalStorageDirectory().getAbsolutePath() + "/" + split[1];
                    }
                }
                if ("com.android.providers.downloads.documents".equals(uri.getAuthority()) && docId.startsWith("raw:")) {
                    return docId.substring(4);
                }
            }
        } catch (Throwable ignored) {
        }
        return null;
    }

    public static String copyUriToLocalConfig(Context context, Uri uri) {
        InputStream input = null;
        FileOutputStream output = null;
        try {
            input = context.getContentResolver().openInputStream(uri);
            if (input == null) return "";
            File dir = new File(FileUtils.getExternalCachePath(), "config");
            if (!dir.exists()) {
                //noinspection ResultOfMethodCallIgnored
                dir.mkdirs();
            }
            File file = new File(dir, getDisplayName(context, uri));
            output = new FileOutputStream(file);
            byte[] buffer = new byte[8192];
            int length;
            while ((length = input.read(buffer)) != -1) {
                output.write(buffer, 0, length);
            }
            return file.getAbsolutePath();
        } catch (Throwable th) {
            th.printStackTrace();
            return "";
        } finally {
            try {
                if (output != null) output.close();
            } catch (Throwable ignored) {
            }
            try {
                if (input != null) input.close();
            } catch (Throwable ignored) {
            }
        }
    }

    public static String getDisplayName(Context context, Uri uri) {
        String name = "local_config.json";
        Cursor cursor = null;
        try {
            cursor = context.getContentResolver().query(uri, null, null, null, null);
            if (cursor != null && cursor.moveToFirst()) {
                int index = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME);
                if (index >= 0) {
                    String displayName = cursor.getString(index);
                    if (displayName != null && !displayName.isEmpty()) {
                        name = displayName;
                    }
                }
            }
        } catch (Throwable ignored) {
        } finally {
            if (cursor != null) cursor.close();
        }
        return name;
    }

    /**
     * clan://localhost/xxx → http://127.0.0.1:xxxx/file/xxx
     * clan://host/path → http://host/file/path
     * @param proxyBase 本地代理根，例如 ControlManager.get().getAddress(true)
     */
    public static String clanToAddress(String lanLink, String proxyBase) {
        if (lanLink == null || lanLink.isEmpty()) {
            return lanLink;
        }
        if (lanLink.startsWith("clan://localhost/")) {
            String base = proxyBase == null ? "" : proxyBase;
            return lanLink.replace("clan://localhost/", base + "file/");
        }
        if (lanLink.startsWith("clan://")) {
            String link = lanLink.substring(7);
            int end = link.indexOf('/');
            if (end <= 0 || end >= link.length() - 1) {
                return lanLink;
            }
            return "http://" + link.substring(0, end) + "/file/" + link.substring(end + 1);
        }
        return lanLink;
    }

    public static String clanContentFix(String resolvedLanLink, String content) {
        if (content == null || resolvedLanLink == null) {
            return content;
        }
        int idx = resolvedLanLink.indexOf("/file/");
        if (idx < 0) {
            return content;
        }
        String fix = resolvedLanLink.substring(0, idx + 6);
        return content.replace("clan://localhost/", fix).replace("file://", fix);
    }

    /** file:// → clan://localhost/ */
    public static String fileUrlToClan(String url) {
        if (url == null) return null;
        return url.replace("file://", "clan://localhost/");
    }

}
