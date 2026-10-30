package com.github.tvbox.osc.ui.dialog;

import android.app.Activity;
import android.content.Context;
import android.text.TextUtils;
import android.view.View;
import android.widget.EditText;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.view.inputmethod.InputMethodManager;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.lifecycle.LifecycleOwner;
import androidx.lifecycle.Observer;
import androidx.lifecycle.ViewModelProvider;
import androidx.lifecycle.ViewModelStoreOwner;

import com.chad.library.adapter.base.BaseQuickAdapter;
import com.github.tvbox.osc.R;
import com.github.tvbox.osc.bean.Subtitle;
import com.github.tvbox.osc.bean.SubtitleData;
import com.github.tvbox.osc.ui.adapter.SearchSubtitleAdapter;
import com.github.tvbox.osc.util.FastClickCheckUtil;
import com.github.tvbox.osc.viewmodel.SubtitleViewModel;
import com.orhanobut.hawk.Hawk;
import com.owen.tvrecyclerview.widget.TvRecyclerView;
import com.owen.tvrecyclerview.widget.V7LinearLayoutManager;

import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.List;

public class SearchSubtitleDialog extends BaseDialog {

    private Context mContext;
    private TvRecyclerView mGridView;
    private SearchSubtitleAdapter searchAdapter;

    private TextView subtitleSearchBtn;
    private EditText subtitleSearchEt;
    private SubtitleLoader mSubtitleLoader;
    private ProgressBar loadingBar;
    private SubtitleViewModel subtitleViewModel;
    private int page = 1;
    private int maxPage = 5;
    private String searchWord = "";

    private List<Subtitle> zipSubtitles = new ArrayList<>();
    private boolean isSearchPag = true;

    /** 在线字幕总开关（Hawk 持久化），关闭后不再加载/展示在线字幕 */
    public static final String KEY_ONLINE_SUBTITLE_ENABLED = "online_subtitle_enabled";
    private TextView btnSubtitleToggle;
    private SubtitleToggleListener mSubtitleToggleListener;

    public interface SubtitleToggleListener {
        void onSubtitleToggle(boolean enabled);
    }

    public void setSubtitleToggleListener(SubtitleToggleListener listener) {
        mSubtitleToggleListener = listener;
    }

    private void refreshSubtitleToggleText() {
        boolean enabled = Hawk.get(KEY_ONLINE_SUBTITLE_ENABLED, true);
        btnSubtitleToggle.setText(enabled ? "开启" : "关闭");
    }


    public SearchSubtitleDialog(@NonNull @NotNull Context context) {
        super(context, R.style.SearchSubtitleDialogStyle);
        mContext = context;
        if (context instanceof Activity) {
            setOwnerActivity((Activity) context);
        }
        setContentView(R.layout.dialog_search_subtitle);
        initView(context);
        initViewModel();
    }


    @Override
    public void show() {
        if (getWindow() != null) {
            getWindow().setSoftInputMode(
                    android.view.WindowManager.LayoutParams.SOFT_INPUT_STATE_VISIBLE
                            | android.view.WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE);
        }
        super.show();
        // 不强制沉浸全屏，避免触发系统全屏输入
        if (getWindow() != null && getWindow().getDecorView() != null) {
            int ui = getWindow().getDecorView().getSystemUiVisibility();
            ui &= ~View.SYSTEM_UI_FLAG_FULLSCREEN;
            ui &= ~View.SYSTEM_UI_FLAG_HIDE_NAVIGATION;
            ui &= ~View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY;
            getWindow().getDecorView().setSystemUiVisibility(ui);
        }
    }

    protected void initView(Context context) {
        loadingBar = findViewById(R.id.loadingBar);
        mGridView = findViewById(R.id.mGridView);
        subtitleSearchEt = findViewById(R.id.input);
        subtitleSearchBtn = findViewById(R.id.inputSubmit);
        btnSubtitleToggle = findViewById(R.id.btnSubtitleToggle);
        refreshSubtitleToggleText();
        btnSubtitleToggle.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                FastClickCheckUtil.check(v);
                boolean enabled = !Hawk.get(KEY_ONLINE_SUBTITLE_ENABLED, true);
                Hawk.put(KEY_ONLINE_SUBTITLE_ENABLED, enabled);
                refreshSubtitleToggleText();
                if (mSubtitleToggleListener != null) {
                    mSubtitleToggleListener.onSubtitleToggle(enabled);
                }
                Toast.makeText(getContext(), enabled ? "在线字幕已开启" : "在线字幕已关闭", Toast.LENGTH_SHORT).show();
            }
        });
        searchAdapter = new SearchSubtitleAdapter();
        // 列表不接受焦点：防止 ENTER/DPAD 键的 FOCUS_DOWN 焦点搜索命中该列表，
        // 在其 GONE/0尺寸 状态下抛 IllegalStateException 闪退
        mGridView.setFocusable(false);
        mGridView.setFocusableInTouchMode(false);
        mGridView.setHasFixedSize(true);
        mGridView.setLayoutManager(new V7LinearLayoutManager(getContext(), 1, false));
        mGridView.setAdapter(searchAdapter);
        searchAdapter.setOnItemClickListener(new BaseQuickAdapter.OnItemClickListener() {
            @Override
            public void onItemClick(BaseQuickAdapter adapter, View view, int position) {
                FastClickCheckUtil.check(view);
                if (!Hawk.get(KEY_ONLINE_SUBTITLE_ENABLED, true)) {
                    Toast.makeText(getContext(), "在线字幕已关闭", Toast.LENGTH_SHORT).show();
                    return;
                }
                Subtitle subtitle = searchAdapter.getData().get(position);
                //加载字幕
                if (mSubtitleLoader != null) {
                    if (subtitle.getIsZip()) {
                        isSearchPag = false;
                        loadingBar.setVisibility(View.VISIBLE);
                        mGridView.setVisibility(View.GONE);
                        subtitleViewModel.getSearchResultSubtitleUrls(subtitle);
                    } else {
                        loadSubtitle(subtitle);
                        dismiss();
                    }
                }
            }
        });

        searchAdapter.setOnLoadMoreListener(new BaseQuickAdapter.RequestLoadMoreListener() {
            @Override
            public void onLoadMoreRequested() {
                if (!searchAdapter.getData().isEmpty() && searchAdapter.getData().get(0).getIsZip()) {
                    subtitleViewModel.searchResult(searchWord, page);
                }
            }
        }, mGridView);

        subtitleSearchBtn.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                FastClickCheckUtil.check(v);
                String wd = subtitleSearchEt.getText().toString().trim();
                search(wd);
            }
        });
        // 拦截 ENTER 键：TextView.onKeyUp 默认会做 FOCUS_DOWN 焦点搜索，
        // 搜索中会命中处于 GONE/0尺寸状态的 mGridView，直接抛
        // IllegalStateException("focus search returned a view that wasn't able to take focus!") 闪退
        View.OnKeyListener enterKeyListener = new View.OnKeyListener() {
            @Override
            public boolean onKey(View v, int keyCode, android.view.KeyEvent event) {
                if ((keyCode == android.view.KeyEvent.KEYCODE_ENTER
                        || keyCode == android.view.KeyEvent.KEYCODE_DPAD_CENTER
                        || keyCode == android.view.KeyEvent.KEYCODE_NUMPAD_ENTER)
                        && event.getAction() == android.view.KeyEvent.ACTION_UP) {
                    if (v.getId() == R.id.input) {
                        String wd = subtitleSearchEt.getText().toString().trim();
                        search(wd);
                    } else {
                        v.performClick();
                    }
                    return true;
                }
                return false;
            }
        };
        subtitleSearchEt.setOnKeyListener(enterKeyListener);
        subtitleSearchBtn.setOnKeyListener(enterKeyListener);
        // 点击输入框直接弹系统键盘，避免全屏输入法/二次跳转
        subtitleSearchEt.setFocusable(true);
        subtitleSearchEt.setFocusableInTouchMode(true);
        if (android.os.Build.VERSION.SDK_INT >= 21) {
            subtitleSearchEt.setShowSoftInputOnFocus(true);
        }
        subtitleSearchEt.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                subtitleSearchEt.requestFocus();
                InputMethodManager imm = (InputMethodManager) getContext().getSystemService(Context.INPUT_METHOD_SERVICE);
                if (imm != null) {
                    imm.showSoftInput(subtitleSearchEt, InputMethodManager.SHOW_IMPLICIT);
                }
            }
        });
        subtitleSearchEt.setOnFocusChangeListener(new View.OnFocusChangeListener() {
            @Override
            public void onFocusChange(View v, boolean hasFocus) {
                if (hasFocus) {
                    InputMethodManager imm = (InputMethodManager) getContext().getSystemService(Context.INPUT_METHOD_SERVICE);
                    if (imm != null) {
                        imm.showSoftInput(subtitleSearchEt, InputMethodManager.SHOW_IMPLICIT);
                    }
                }
            }
        });
        searchAdapter.setNewData(new ArrayList<>());
        if (getWindow() != null) {
            getWindow().setSoftInputMode(
                    android.view.WindowManager.LayoutParams.SOFT_INPUT_STATE_VISIBLE
                            | android.view.WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE);
        }
    }

    public void setSearchWord(String wd) {
        wd = wd.replaceAll("(?:（|\\(|\\[|【|\\.mp4|\\.mkv|\\.avi|\\.MP4|\\.MKV|\\.AVI)", "");
        wd = wd.replaceAll("(?:：|\\:|）|\\)|\\]|】|\\.)", " ");
        int len = wd.length();
        int finalLen = len >= 36 ? 36 : len;
        wd = wd.substring(0, finalLen).trim();
        subtitleSearchEt.setText(wd);
        subtitleSearchEt.setSelection(wd.length());
        subtitleSearchEt.requestFocus();
        search(wd);
    }

    public void search(String wd) {
        isSearchPag = true;
        searchAdapter.setEnableLoadMore(false);
        searchAdapter.setNewData(new ArrayList<>());
        if (!TextUtils.isEmpty(wd)) {
            loadingBar.setVisibility(View.VISIBLE);
            mGridView.setVisibility(View.GONE);
            searchWord = wd;
            subtitleViewModel.searchResult(wd, page = 1);
        } else {
            Toast.makeText(getContext(), "输入内容不能为空", Toast.LENGTH_SHORT).show();
        }
    }

    private void initViewModel() {
        subtitleViewModel = new ViewModelProvider((ViewModelStoreOwner) mContext).get(SubtitleViewModel.class);
        subtitleViewModel.searchResult.observe((LifecycleOwner) mContext, new Observer<SubtitleData>() {
            @Override
            public void onChanged(SubtitleData subtitleData) {
                loadingBar.setVisibility(View.GONE);
                mGridView.setVisibility(View.VISIBLE);
                if (subtitleData == null) {
                    Toast.makeText(getContext(), "未查询到匹配字幕", Toast.LENGTH_SHORT).show();
                    return;
                }
                List<Subtitle> data = subtitleData.getSubtitleList();
                if (data == null) {
                    mGridView.post(new Runnable() {
                        @Override
                        public void run() {
                            Toast.makeText(getContext(), "未查询到匹配字幕", Toast.LENGTH_SHORT).show();
                        }
                    });
                    return;
                }

                if (data.size() > 0) {
                    mGridView.requestFocus();
                    if (subtitleData.getIsZip()) {
                        if (subtitleData.getIsNew()) {
                            searchAdapter.setNewData(data);
                            zipSubtitles = data;
                        } else {
                            searchAdapter.addData(data);
                            zipSubtitles.addAll(data);
                        }
                        page++;
                        if (page > maxPage) {
                            searchAdapter.loadMoreEnd();
                            searchAdapter.setEnableLoadMore(false);
                        } else {
                            searchAdapter.loadMoreComplete();
                            searchAdapter.setEnableLoadMore(true);
                        }
                    } else {
                        searchAdapter.loadMoreComplete();
                        searchAdapter.setNewData(data);
                        searchAdapter.setEnableLoadMore(false);
                    }
                } else {
                    if (page > maxPage) {
                        searchAdapter.loadMoreEnd();
                    } else {
                        searchAdapter.loadMoreComplete();
                    }
                    searchAdapter.setEnableLoadMore(false);
                }

            }
        });
    }

    private void loadSubtitle(Subtitle subtitle) {
        subtitleViewModel.getSubtitleUrl(subtitle, mSubtitleLoader);
    }

    public void setSubtitleLoader(SubtitleLoader subtitleLoader) {
        mSubtitleLoader = subtitleLoader;
    }

    public interface SubtitleLoader {
        void loadSubtitle(Subtitle subtitle);
    }

    @Override
    public void onBackPressed() {
        if (!isSearchPag) {
            isSearchPag = true;
            loadingBar.setVisibility(View.GONE);
            mGridView.setVisibility(View.VISIBLE);
            searchAdapter.setNewData(zipSubtitles);
            searchAdapter.setEnableLoadMore(page < maxPage);
            return;
        }
        dismiss();
    }


}
