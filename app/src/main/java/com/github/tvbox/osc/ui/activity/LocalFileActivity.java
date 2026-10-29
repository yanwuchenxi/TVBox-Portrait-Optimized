package com.github.tvbox.osc.ui.activity;

import android.app.Activity;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.Build;
import android.os.Environment;
import android.view.KeyEvent;
import android.view.View;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.chad.library.adapter.base.BaseQuickAdapter;
import com.github.tvbox.osc.R;
import com.github.tvbox.osc.base.BaseActivity;
import com.github.tvbox.osc.ui.adapter.LocalFileAdapter;
import com.github.tvbox.osc.util.StoragePermissionHelper;
import com.owen.tvrecyclerview.widget.TvRecyclerView;
import com.owen.tvrecyclerview.widget.V7LinearLayoutManager;

import java.io.File;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;

import me.jessyan.autosize.utils.AutoSizeUtils;

/**
 * 本地配置文件选择。
 * Android 11+ 必须「所有文件访问」才能看到 json/txt 等非媒体文件；
 * 仅媒体权限时 listFiles 往往只能列出目录。
 */
public class LocalFileActivity extends BaseActivity {
    public static final String EXTRA_LIVE = "live";

    private TvRecyclerView fileList;
    private TextView tvTitle;
    private TextView tvPath;
    private TextView tvEmpty;
    private LocalFileAdapter adapter;
    private File currentDir;
    private File rootDir;
    private boolean pendingLoadAfterPermission;

    @Override
    protected int getLayoutResID() {
        return R.layout.activity_local_file;
    }

    @Override
    protected void init() {
        tvTitle = findViewById(R.id.tvTitle);
        tvPath = findViewById(R.id.tvPath);
        tvEmpty = findViewById(R.id.tvEmpty);
        fileList = findViewById(R.id.fileList);
        adapter = new LocalFileAdapter();
        fileList.setAdapter(adapter);
        fileList.setLayoutManager(new V7LinearLayoutManager(this, 1, false));
        fileList.setSpacingWithMargins(0, AutoSizeUtils.mm2px(this, 8));
        adapter.setOnItemClickListener(new BaseQuickAdapter.OnItemClickListener() {
            @Override
            public void onItemClick(BaseQuickAdapter adapter, View view, int position) {
                File item = (File) adapter.getItem(position);
                if (item != null) open(item);
            }
        });
        boolean live = getIntent() != null && getIntent().getBooleanExtra(EXTRA_LIVE, false);
        tvTitle.setText(live ? "选择直播配置文件" : "选择点播配置文件");

        ensurePermissionAndBrowse();
    }

    @Override
    protected void onResume() {
        super.onResume();
        // 从「所有文件访问」设置页返回后继续加载
        if (pendingLoadAfterPermission && StoragePermissionHelper.hasAllFilesAccess(this)) {
            pendingLoadAfterPermission = false;
            startBrowse();
        }
    }

    private void ensurePermissionAndBrowse() {
        if (StoragePermissionHelper.hasAllFilesAccess(this)) {
            startBrowse();
            return;
        }
        pendingLoadAfterPermission = true;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            Toast.makeText(this, "请开启「所有文件访问」权限，才能看到本地配置文件", Toast.LENGTH_LONG).show();
        } else {
            Toast.makeText(this, "需要存储权限以浏览本地文件", Toast.LENGTH_SHORT).show();
        }
        StoragePermissionHelper.requestAllFilesAccessIfNeeded(this);
    }

    private void startBrowse() {
        initRoot();
        loadDir(rootDir);
    }

    private void initRoot() {
        File storage = Environment.getExternalStorageDirectory();
        File tvBox = new File(storage, "TVBox");
        if (!tvBox.exists()) {
            //noinspection ResultOfMethodCallIgnored
            tvBox.mkdirs();
        }
        rootDir = tvBox.exists() && tvBox.isDirectory() ? tvBox : storage;
    }

    private void loadDir(File dir) {
        if (dir == null || !dir.exists() || !dir.isDirectory()) {
            Toast.makeText(this, "无法打开目录", Toast.LENGTH_SHORT).show();
            return;
        }
        currentDir = dir;
        tvPath.setText(dir.getAbsolutePath());
        List<File> files = new ArrayList<File>();
        if (!isStorageRoot(dir) && dir.getParentFile() != null) {
            files.add(dir.getParentFile());
        }
        File[] children = dir.listFiles();
        if (children != null) {
            // 显示目录 + 常见配置/文本文件（隐藏点文件）
            for (File child : children) {
                if (child == null) continue;
                String name = child.getName();
                if (name.startsWith(".")) continue;
                if (child.isDirectory() || isLikelyConfigFile(name)) {
                    files.add(child);
                } else if (child.isFile()) {
                    // 其它普通文件也显示，避免用户找不到无后缀配置
                    files.add(child);
                }
            }
        } else if (!StoragePermissionHelper.hasAllFilesAccess(this)) {
            Toast.makeText(this, "无法读取文件列表，请授予「所有文件访问」权限", Toast.LENGTH_LONG).show();
        }
        adapter.setParentDir(isStorageRoot(dir) ? null : dir.getParentFile());
        Collections.sort(files, new Comparator<File>() {
            @Override
            public int compare(File left, File right) {
                if (currentDir != null && currentDir.getParentFile() != null) {
                    if (left.equals(currentDir.getParentFile())) return -1;
                    if (right.equals(currentDir.getParentFile())) return 1;
                }
                if (left.isDirectory() != right.isDirectory()) {
                    return left.isDirectory() ? -1 : 1;
                }
                return left.getName().compareToIgnoreCase(right.getName());
            }
        });
        adapter.setNewData(files);
        // 仅「返回上级」一项时视为空
        boolean onlyParent = files.size() == 1 && currentDir != null
                && currentDir.getParentFile() != null && files.get(0).equals(currentDir.getParentFile());
        tvEmpty.setVisibility(files.isEmpty() || onlyParent ? View.VISIBLE : View.GONE);
        fileList.postDelayed(new Runnable() {
            @Override
            public void run() {
                fileList.requestFocus();
            }
        }, 100);
    }

    private static boolean isLikelyConfigFile(String name) {
        String lower = name.toLowerCase(Locale.US);
        return lower.endsWith(".json")
                || lower.endsWith(".txt")
                || lower.endsWith(".m3u")
                || lower.endsWith(".m3u8")
                || lower.endsWith(".xml")
                || lower.endsWith(".conf")
                || lower.endsWith(".ini")
                || lower.endsWith(".csv");
    }

    private void open(File item) {
        if (item.isDirectory()) {
            loadDir(item);
            return;
        }
        Intent data = new Intent();
        data.setData(Uri.fromFile(item));
        setResult(Activity.RESULT_OK, data);
        finish();
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, @Nullable Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == StoragePermissionHelper.REQUEST_MANAGE_STORAGE) {
            if (StoragePermissionHelper.hasAllFilesAccess(this)) {
                pendingLoadAfterPermission = false;
                startBrowse();
            } else {
                Toast.makeText(this, "未开启所有文件访问，无法浏览配置文件", Toast.LENGTH_LONG).show();
            }
        }
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode != StoragePermissionHelper.REQUEST_STORAGE) {
            return;
        }
        if (StoragePermissionHelper.hasAllFilesAccess(this) || StoragePermissionHelper.hasLegacyReadPermission(this)) {
            if (pendingLoadAfterPermission) {
                pendingLoadAfterPermission = false;
                startBrowse();
            }
        } else {
            Toast.makeText(this, "未获得存储权限，无法浏览本地文件", Toast.LENGTH_LONG).show();
            finish();
        }
    }

    @Override
    public boolean dispatchKeyEvent(KeyEvent event) {
        if (event.getAction() == KeyEvent.ACTION_UP && event.getKeyCode() == KeyEvent.KEYCODE_BACK) {
            if (currentDir != null && !isStorageRoot(currentDir) && currentDir.getParentFile() != null) {
                loadDir(currentDir.getParentFile());
                return true;
            }
        }
        return super.dispatchKeyEvent(event);
    }

    private boolean isStorageRoot(File dir) {
        try {
            return Environment.getExternalStorageDirectory().getCanonicalPath().equals(dir.getCanonicalPath());
        } catch (Throwable ignored) {
            return Environment.getExternalStorageDirectory().getAbsolutePath().equals(dir.getAbsolutePath());
        }
    }
}
