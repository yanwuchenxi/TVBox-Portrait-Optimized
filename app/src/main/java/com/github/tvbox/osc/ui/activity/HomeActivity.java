package com.github.tvbox.osc.ui.activity;

import android.Manifest;
import android.animation.Animator;
import android.animation.AnimatorSet;
import android.animation.IntEvaluator;
import android.animation.ObjectAnimator;
import android.annotation.SuppressLint;
import android.content.DialogInterface;
import android.content.Intent;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.util.TypedValue;
import android.view.KeyEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.animation.AccelerateInterpolator;
import android.view.animation.AlphaAnimation;
import android.view.animation.Animation;
import android.view.animation.BounceInterpolator;
import android.view.inputmethod.EditorInfo;
import android.view.inputmethod.InputMethodManager;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.constraintlayout.widget.ConstraintLayout;
import androidx.lifecycle.Observer;
import androidx.lifecycle.ViewModelProvider;
import androidx.recyclerview.widget.DiffUtil;
import androidx.recyclerview.widget.RecyclerView;
import androidx.viewpager.widget.ViewPager;

import com.github.tvbox.osc.R;
import com.github.tvbox.osc.api.ApiConfig;
import com.github.tvbox.osc.base.BaseActivity;
import com.github.tvbox.osc.base.BaseLazyFragment;
import com.github.tvbox.osc.bean.AbsSortXml;
import com.github.tvbox.osc.bean.Movie;
import com.github.tvbox.osc.bean.MovieSort;
import com.github.tvbox.osc.bean.SourceBean;
import com.github.tvbox.osc.event.RefreshEvent;
import com.github.tvbox.osc.server.ControlManager;
import com.github.tvbox.osc.ui.adapter.HomePageAdapter;
import com.github.tvbox.osc.ui.adapter.SelectDialogAdapter;
import com.github.tvbox.osc.ui.adapter.SortAdapter;
import com.github.tvbox.osc.ui.dialog.SelectDialog;
import com.github.tvbox.osc.ui.dialog.ApiDialog;
import com.github.tvbox.osc.ui.dialog.TipDialog;
import com.github.tvbox.osc.ui.fragment.GridFragment;
import com.github.tvbox.osc.ui.fragment.UserFragment;
import com.github.tvbox.osc.ui.tv.widget.DefaultTransformer;
import com.github.tvbox.osc.ui.tv.widget.FixedSpeedScroller;
import com.github.tvbox.osc.ui.tv.widget.NoScrollViewPager;
import com.github.tvbox.osc.ui.tv.widget.ViewObj;
import com.github.tvbox.osc.util.WeakRunnable;
import com.github.tvbox.osc.util.WeakViewRunnable;
import com.github.tvbox.osc.util.AppManager;
import com.github.tvbox.osc.util.BottomNavHelper;
import com.github.tvbox.osc.util.DefaultConfig;
import com.github.tvbox.osc.util.FastClickCheckUtil;
import com.github.tvbox.osc.util.FileUtils;
import com.github.tvbox.osc.util.HawkConfig;
import com.github.tvbox.osc.util.HistoryHelper;
import com.github.tvbox.osc.util.LOG;
import com.github.tvbox.osc.util.MD5;
import com.github.tvbox.osc.viewmodel.SourceViewModel;
import com.orhanobut.hawk.Hawk;
import com.owen.tvrecyclerview.widget.TvRecyclerView;
import com.owen.tvrecyclerview.widget.V7GridLayoutManager;
import com.owen.tvrecyclerview.widget.V7LinearLayoutManager;

import org.greenrobot.eventbus.EventBus;
import org.greenrobot.eventbus.Subscribe;
import org.greenrobot.eventbus.ThreadMode;
import org.jetbrains.annotations.NotNull;

import java.io.File;
import java.lang.reflect.Field;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.Objects;

import me.jessyan.autosize.utils.AutoSizeUtils;

public class HomeActivity extends BaseActivity {
    private LinearLayout topLayout;
    private LinearLayout contentLayout;
    private TextView tvName;
    private EditText etHomeSearch;
    private ImageView btnHomeApiSwitch;
    private TvRecyclerView mGridView;
    private NoScrollViewPager mViewPager;
    private SourceViewModel sourceViewModel;
    private SortAdapter sortAdapter;
    private HomePageAdapter pageAdapter;
    private View currentView;
    private final List<BaseLazyFragment> fragments = new ArrayList<>();
    private boolean isDownOrUp = false;
    private boolean sortChange = false;
    private int currentSelected = 0;
    private int sortFocused = 0;
    public View sortFocusView = null;
    private String loadingSourceKey;
    private String previousHomeName;
    private SourceBean previousHomeSource;
    private boolean homeSortLoading = false;
    private boolean refreshHomeRec = false;
    private final Handler mHandler = new Handler(Looper.getMainLooper());
    private long mExitTime = 0;
    private boolean eventBusRegistered = false;
    private final Runnable refreshTopInfoTextSizeRunnable = new Runnable() {
        @Override
        public void run() {
            refreshTopInfoTextSize();
        }
    };
    private final Runnable refreshTopLayoutRunnable = new Runnable() {
        @Override
        public void run() {
            if (topLayout == null || isActivityUnavailable() || currentSelected != 0 || topHide != 0) {
                return;
            }
            // OnePlus devices may finish applying immersive mode after the first measure.
            // Re-apply the visible top state once the final display metrics are available.
            hideSysBar();
            ViewGroup.MarginLayoutParams params = (ViewGroup.MarginLayoutParams) topLayout.getLayoutParams();
            params.topMargin = AutoSizeUtils.mm2px(HomeActivity.this, 10.0f);
            params.height = AutoSizeUtils.mm2px(HomeActivity.this, 60.0f);
            topLayout.setLayoutParams(params);
            topLayout.setAlpha(1.0f);
            refreshTopInfoTextSize();
            topLayout.requestLayout();
        }
    };

    @Override
    protected int getLayoutResID() {
        return R.layout.activity_home;
    }

    @Override
    protected boolean shouldRefreshAutoSize() {
        return true;
    }

    @Override
    protected boolean useImmersive() {
        return false;
    }

    boolean useCacheConfig = false;
    // 当前首页已加载配置所对应的接口地址，用于检测接口切换
    private String loadedApiUrl;
    // 接口切换后的重新加载不触发“默认进入直播页”
    private boolean skipLiveJumpOnce = false;

    @Override
    protected void init() {
        EventBus.getDefault().register(this);
        eventBusRegistered = true;
        ControlManager.get().startServer();
        initView();
        initViewModel();
        useCacheConfig = false;
        Intent intent = getIntent();
        if (intent != null && intent.getExtras() != null) {
            Bundle bundle = intent.getExtras();
            useCacheConfig = bundle.getBoolean("useCache", false);
        }
        initData();
    }

    private void initView() {
        this.topLayout = findViewById(R.id.topLayout);
        this.tvName = findViewById(R.id.tvName);
        this.etHomeSearch = findViewById(R.id.etHomeSearch);
        this.btnHomeApiSwitch = findViewById(R.id.btn_home_api_switch);
        initHomeApiSwitch();
        initHomeSearch();
        this.contentLayout = findViewById(R.id.contentLayout);
        this.mGridView = findViewById(R.id.mGridView);
        this.mViewPager = findViewById(R.id.mViewPager);
        this.sortAdapter = new SortAdapter();
        this.mGridView.setLayoutManager(new V7LinearLayoutManager(this.mContext, 0, false));
        this.mGridView.setSpacingWithMargins(0, AutoSizeUtils.dp2px(this.mContext, 10.0f));
        this.mGridView.setAdapter(this.sortAdapter);
        sortAdapter.registerAdapterDataObserver(new RecyclerView.AdapterDataObserver() {
            @Override
            public void onChanged() {
                mGridView.post(() -> {
                    View firstChild = Objects.requireNonNull(mGridView.getLayoutManager()).findViewByPosition(0);
                    if (firstChild != null) {
                        mGridView.setSelectedPosition(0);
                        firstChild.requestFocus();
                    }
                });
            }
        });
        this.mGridView.setOnItemListener(new TvRecyclerView.OnItemListener() {
            public void onItemPreSelected(TvRecyclerView tvRecyclerView, View view, int position) {
                if (view != null && !HomeActivity.this.isDownOrUp) {
                    mHandler.postDelayed(new WeakViewRunnable(view, v -> {
                            TextView textView = v.findViewById(R.id.tvTitle);
                            if (textView == null) return;
                            final int p = position;
                            textView.getPaint().setFakeBoldText(false);
                            if (sortFocused == p) {
                                v.animate().scaleX(1.1f).scaleY(1.1f).setInterpolator(new BounceInterpolator()).setDuration(300).start();
                                textView.setTextColor(getResources().getColor(R.color.color_FFFFFF));
                            } else {
                                v.animate().scaleX(1.0f).scaleY(1.0f).setDuration(300).start();
                                textView.setTextColor(getResources().getColor(R.color.color_BBFFFFFF));
                                View f1 = v.findViewById(R.id.tvFilter);
                                View f2 = v.findViewById(R.id.tvFilterColor);
                                if (f1 != null) f1.setVisibility(View.GONE);
                                if (f2 != null) f2.setVisibility(View.GONE);
                            }
                            textView.invalidate();
                        }), 10);
                }
            }

            public void onItemSelected(TvRecyclerView tvRecyclerView, View view, int position) {
                if (view != null) {
                    HomeActivity.this.currentView = view;
                    HomeActivity.this.isDownOrUp = false;
                    HomeActivity.this.sortChange = true;
                    view.animate().scaleX(1.1f).scaleY(1.1f).setInterpolator(new BounceInterpolator()).setDuration(300).start();
                    TextView textView = view.findViewById(R.id.tvTitle);
                    textView.getPaint().setFakeBoldText(true);
                    textView.setTextColor(HomeActivity.this.getResources().getColor(R.color.color_FFFFFF));
                    textView.invalidate();
                    MovieSort.SortData sortData = sortAdapter.getItem(position);
                    if (!sortData.filters.isEmpty()) {
                        showFilterIcon(sortData.filterSelectCount());
                    }
                    HomeActivity.this.sortFocusView = view;
                    HomeActivity.this.sortFocused = position;
                    mHandler.removeCallbacks(mDataRunnable);
                    mHandler.postDelayed(mDataRunnable, 200);
                }
            }

            @Override
            public void onItemClick(TvRecyclerView parent, View itemView, int position) {
                if (itemView != null && currentSelected == position) {
                    BaseLazyFragment baseLazyFragment = fragments.get(currentSelected);
                    if ((baseLazyFragment instanceof GridFragment) && !sortAdapter.getItem(position).filters.isEmpty()) {// 弹出筛选
                        ((GridFragment) baseLazyFragment).showFilter();
                    } else if (baseLazyFragment instanceof UserFragment) {
                        showSiteSwitch();
                    }
                }
            }
        });

        this.mGridView.setOnInBorderKeyEventListener(new TvRecyclerView.OnInBorderKeyEventListener() {
            public boolean onInBorderKeyEvent(int direction, View view) {
                if (direction == View.FOCUS_UP) {
                    BaseLazyFragment baseLazyFragment = fragments.get(sortFocused);
                    if (baseLazyFragment instanceof UserFragment) {
                        refreshHomeSort();
                        return true;
                    }
                    if (baseLazyFragment instanceof GridFragment) {
                        ((GridFragment) baseLazyFragment).forceRefresh();
                        return true;
                    }
                }
                if (direction != View.FOCUS_DOWN) {
                    return false;
                }
                BaseLazyFragment baseLazyFragment = fragments.get(sortFocused);
                if (!(baseLazyFragment instanceof GridFragment)) {
                    return false;
                }
                return !((GridFragment) baseLazyFragment).isLoad();
            }
        });
        tvName.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                FastClickCheckUtil.check(v);
                SourceBean homeSource = ApiConfig.get().getHomeSourceBean();
                if (dataInitOk && jarInitOk && homeSource != null) {
                    String jar = homeSource.getJar();
                    String jarUrl = jar != null && !jar.isEmpty() ? jar : ApiConfig.get().getSpider();
                    if (jarUrl == null) jarUrl = "";
                    String jarSource = jarUrl.split(";md5;")[0];
                    File cspCacheDir = new File(FileUtils.getFilePath() + "/csp/" + MD5.string2MD5(jarSource) + ".jar");
                    File jarCacheDir = new File(FileUtils.getCachePath() + "/jar/" + MD5.string2MD5(jarSource) + ".jar");
                    File jarFullCacheDir = new File(FileUtils.getCachePath() + "/jar/" + MD5.string2MD5(jarUrl) + ".jar");
                    Toast.makeText(mContext, "缓存已清除", Toast.LENGTH_LONG).show();
                    new Thread(() -> {
                        try {
                            FileUtils.deleteFile(cspCacheDir);
                            FileUtils.deleteFile(jarCacheDir);
                            FileUtils.deleteFile(jarFullCacheDir);
                            FileUtils.clearSpiderCacheFiles();
                            ApiConfig.get().clearSpiderCache();
                            refreshHome();
                        } catch (Exception e) {
                            e.printStackTrace();
                        }
                    }).start();

                } else {
                    jumpActivity(SettingActivity.class);
                }
            }
        });
        tvName.setOnLongClickListener(new View.OnLongClickListener() {
            @Override
            public boolean onLongClick(View v) {
                jumpActivity(SettingActivity.class);
                return true;
            }
        });
        View.OnClickListener allSortListener = new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                showAllSortDialog();
            }
        };
        findViewById(R.id.btnSortAllRight).setOnClickListener(allSortListener);
        initBottomNav();
        setLoadSir(this.contentLayout);
        //mHandler.postDelayed(mFindFocus, 500);
    }

    /**
     * 顶部站点名前的接口切换图标：弹出接口(数据源地址)切换弹窗，
     * 切换后直接走 reloadForApiChange 重新加载首页
     */
    private void initHomeApiSwitch() {
        if (btnHomeApiSwitch == null) return;
        btnHomeApiSwitch.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                FastClickCheckUtil.check(v);
                final ApiDialog dialog = new ApiDialog(HomeActivity.this);
                EventBus.getDefault().register(dialog);
                dialog.setOnListener(new ApiDialog.OnListener() {
                    @Override
                    public void onchange(String api) {
                        String oldApi = Hawk.get(HawkConfig.API_URL, "");
                        Hawk.put(HawkConfig.API_URL, api);
                        if (!HistoryHelper.isApiLineHistory(api)) {
                            HistoryHelper.clearApiLineList();
                        }
                        if (!oldApi.equals(api)) {
                            reloadForApiChange(api);
                        }
                    }

                    @Override
                    public void onLocalConfig(boolean live) {
                        // 首页不提供本地配置导入，忽略
                    }
                });
                dialog.setOnDismissListener(new DialogInterface.OnDismissListener() {
                    @Override
                    public void onDismiss(DialogInterface d) {
                        EventBus.getDefault().unregister(dialog);
                    }
                });
                dialog.show();
            }
        });
    }

    /**
     * 顶部搜索框：位于站点名称与日期之间，输入后回车直接跳转搜索页并搜索
     */
    private void initHomeSearch() {
        if (etHomeSearch == null) return;
        // 默认不抢焦点，避免进入首页就弹出输入法；点击后正常输入
        etHomeSearch.setFocusableInTouchMode(false);
        etHomeSearch.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                etHomeSearch.setFocusableInTouchMode(true);
                etHomeSearch.requestFocus();
                etHomeSearch.setSelection(etHomeSearch.getText().length());
                InputMethodManager imm = (InputMethodManager) getSystemService(INPUT_METHOD_SERVICE);
                if (imm != null) imm.showSoftInput(etHomeSearch, InputMethodManager.SHOW_IMPLICIT);
            }
        });
        etHomeSearch.setOnEditorActionListener(new TextView.OnEditorActionListener() {
            @Override
            public boolean onEditorAction(TextView v, int actionId, KeyEvent event) {
                boolean search = actionId == EditorInfo.IME_ACTION_SEARCH
                        || actionId == EditorInfo.IME_ACTION_DONE
                        || (event != null && event.getKeyCode() == KeyEvent.KEYCODE_ENTER);
                if (!search) return false;
                String keyword = etHomeSearch.getText() == null ? "" : etHomeSearch.getText().toString().trim();
                if (keyword.isEmpty()) {
                    Toast.makeText(mContext, "请输入搜索内容", Toast.LENGTH_SHORT).show();
                    return true;
                }
                etHomeSearch.clearFocus();
                InputMethodManager imm = (InputMethodManager) getSystemService(INPUT_METHOD_SERVICE);
                if (imm != null) imm.hideSoftInputFromWindow(etHomeSearch.getWindowToken(), 0);
                etHomeSearch.setText("");
                Bundle bundle = new Bundle();
                bundle.putString("title", keyword);
                jumpActivity(SearchActivity.class, bundle);
                return true;
            }
        });
    }

    /**
     * 站点名称最多显示 7 个汉字，超出截断
     */
    private String formatSiteName(String name) {
        if (name == null) return "";
        String trimmed = name.trim();
        if (trimmed.length() > 7) {
            trimmed = trimmed.substring(0, 7);
            // 截断后若末尾残留括号/标点，去掉，避免出现半截符号
            while (trimmed.length() > 0 && "([{（【「|/-—·・:：,，.。 ".indexOf(trimmed.charAt(trimmed.length() - 1)) >= 0) {
                trimmed = trimmed.substring(0, trimmed.length() - 1);
            }
        }
        return trimmed;
    }

    private void initBottomNav() {
        BottomNavHelper.setup(this, R.id.nav_home);
        // 长按首页导航：刷新当前站点数据
        View navHome = findViewById(R.id.nav_home);
        if (navHome != null) {
            navHome.setOnLongClickListener(new View.OnLongClickListener() {
                @Override
                public boolean onLongClick(View v) {
                    Toast.makeText(mContext, "正在刷新站点数据", Toast.LENGTH_SHORT).show();
                    refreshHomeSort();
                    return true;
                }
            });
        }
    }

    private SelectDialog<MovieSort.SortData> mAllSortDialog;

    void showAllSortDialog() {
        if (isActivityUnavailable()) return;
        List<MovieSort.SortData> data = sortAdapter.getData();
        if (data == null || data.isEmpty()) return;
        if (mAllSortDialog == null) {
            mAllSortDialog = new SelectDialog<>(HomeActivity.this);
            TvRecyclerView tvRecyclerView = mAllSortDialog.findViewById(R.id.list);
            tvRecyclerView.setLayoutManager(new V7GridLayoutManager(mAllSortDialog.getContext(), 3));
            ConstraintLayout cl_root = mAllSortDialog.findViewById(R.id.cl_root);
            ViewGroup.LayoutParams clp = cl_root.getLayoutParams();
            clp.width = AutoSizeUtils.mm2px(mAllSortDialog.getContext(), 560);
            mAllSortDialog.setTip("全部分类");
        }
        mAllSortDialog.setAdapter(new SelectDialogAdapter.SelectDialogInterface<MovieSort.SortData>() {
            @Override
            public void click(MovieSort.SortData value, int pos) {
                if (mAllSortDialog != null && mAllSortDialog.isShowing()) mAllSortDialog.dismiss();
                mGridView.setSelection(pos);
            }
            @Override
            public String getDisplay(MovieSort.SortData val) {
                return val.name;
            }
        }, new DiffUtil.ItemCallback<MovieSort.SortData>() {
            @Override
            public boolean areItemsTheSame(@NonNull MovieSort.SortData oldItem, @NonNull MovieSort.SortData newItem) {
                return oldItem == newItem;
            }
            @Override
            public boolean areContentsTheSame(@NonNull MovieSort.SortData oldItem, @NonNull MovieSort.SortData newItem) {
                return oldItem.id != null && oldItem.id.equals(newItem.id);
            }
        }, data, currentSelected);
        if (!mAllSortDialog.isShowing())
            mAllSortDialog.show();
    }


    private boolean skipNextUpdate = false;

    private void initViewModel() {
        sourceViewModel = new ViewModelProvider(this).get(SourceViewModel.class);
        sourceViewModel.sortResult.observe(this, new Observer<AbsSortXml>() {
            @Override
            public void onChanged(AbsSortXml absXml) {
                if (skipNextUpdate) {
                    skipNextUpdate = false;
                    return;
                }
                if (!homeSortLoading && loadingSourceKey == null) {
                    return;
                }
                if (absXml != null && absXml.sourceKey != null && loadingSourceKey != null && !loadingSourceKey.equals(absXml.sourceKey)) {
                    return;
                }
                SourceBean home = ApiConfig.get().getHomeSourceBean();
                boolean hasOldContent = !fragments.isEmpty() || !sortAdapter.getData().isEmpty();
                // 空 JSON(AbsSortJson)/网络失败都会产出空分类，需视为失败而非“空白首页”
                boolean noClass = absXml == null || absXml.classes == null
                        || absXml.classes.sortList == null || absXml.classes.sortList.isEmpty();
                boolean noVideo = absXml == null
                        || ((absXml.videoList == null || absXml.videoList.isEmpty())
                        && (absXml.list == null || absXml.list.videoList == null || absXml.list.videoList.isEmpty()));
                boolean noData = noClass && noVideo;
                if (noData) {
                    // 站点接口失败/无数据：结束 loading、回退站点名，避免卡在“切换中”或空白
                    cancelHomeSortLoading();
                    showSuccess();
                    Toast.makeText(mContext, "站点数据加载失败，请重试", Toast.LENGTH_SHORT).show();
                    if (hasOldContent) {
                        return;
                    }
                }
                showSuccess();
                clearHomePages();
                List<MovieSort.SortData> newSortData;
                if (absXml != null && absXml.classes != null && absXml.classes.sortList != null) {
                    newSortData = DefaultConfig.adjustSort(ApiConfig.get().getHomeSourceBean().getKey(), absXml.classes.sortList, true);
                } else {
                    newSortData = DefaultConfig.adjustSort(ApiConfig.get().getHomeSourceBean().getKey(), new ArrayList<>(), true);
                }
                updateSortData(newSortData);
                initViewPager(absXml);
                updateHomeRec(absXml);
                if (home != null && home.getName() != null && !home.getName().isEmpty()) tvName.setText(formatSiteName(home.getName()));
                tvName.clearAnimation();
                homeSortLoading = false;
                lastLoadedSourceKey = loadingSourceKey;
                loadingSourceKey = null;
                previousHomeName = null;
                previousHomeSource = null;
            }
        });
    }

    private String lastLoadedSourceKey;
    private boolean dataInitOk = false;
    private boolean jarInitOk = false;
    private boolean searchSpiderWarmStarted = false;
    private TipDialog mConfigErrorDialog;

    private void initData() {
        if (dataInitOk && jarInitOk) {
            loadedApiUrl = Hawk.get(HawkConfig.API_URL, "");
            loadHomeSort(false);
            if (hasPermission(Manifest.permission.WRITE_EXTERNAL_STORAGE)) {
                LOG.e("有");
            } else {
                LOG.e("无");
            }
            boolean skipLiveJump = skipLiveJumpOnce;
            skipLiveJumpOnce = false;
            if (!useCacheConfig && !skipLiveJump && Hawk.get(HawkConfig.DEFAULT_LOAD_LIVE, false)) {
                jumpActivity(LivePlayActivity.class);
            }
            //爬虫预热 仅首次加载
            if(!useCacheConfig)warmSearchSpidersOnce();
            return;
        }
        tvNameAnimation();
        showLoading();
        if (dataInitOk && !jarInitOk) {
            if (!ApiConfig.get().getSpider().isEmpty()) {
                ApiConfig.get().loadJar(useCacheConfig, ApiConfig.get().getSpider(), new ApiConfig.LoadConfigCallback() {
                    @Override
                    public void success() {
                        jarInitOk = true;
                        mHandler.postDelayed(new WeakRunnable<>(HomeActivity.this, host -> host.initData()), 50);
                    }

                    @Override
                    public void notice(String msg) {
                        mHandler.post(new WeakRunnable<>(HomeActivity.this, host ->
                                Toast.makeText(host, msg, Toast.LENGTH_SHORT).show()));
                    }

                    @Override
                    public void error(String msg) {
                        jarInitOk = true;
                        dataInitOk = true;
                        mHandler.postDelayed(new WeakRunnable<>(HomeActivity.this, host -> {
                            Toast.makeText(host, msg + " jar load err", Toast.LENGTH_SHORT).show();
                            host.initData();
                        }), 50);
                    }
                });
            }
            return;
        }
        ApiConfig.get().loadConfig(useCacheConfig, new ApiConfig.LoadConfigCallback() {
            @Override
            public void notice(String msg) {
                mHandler.post(new WeakRunnable<>(HomeActivity.this, host ->
                        Toast.makeText(host, msg, Toast.LENGTH_SHORT).show()));
            }

            @Override
            public void success() {
                dataInitOk = true;
                if (ApiConfig.get().getSpider().isEmpty()) {
                    jarInitOk = true;
                }
                mHandler.postDelayed(new WeakRunnable<>(HomeActivity.this, host -> host.initData()), 50);
            }

            @Override
            public void error(String msg) {
                if (msg.equalsIgnoreCase("-1")) {
                    mHandler.post(new WeakRunnable<>(HomeActivity.this, host -> {
                            host.dataInitOk = true;
                            host.jarInitOk = true;
                            host.initData();
                        }));
                    return;
                }
                mHandler.post(new WeakRunnable<>(HomeActivity.this, host -> {
                        if (host.isActivityUnavailable()) {
                            return;
                        }
                        if (host.mConfigErrorDialog == null)
                            host.mConfigErrorDialog = new TipDialog(host, msg, "重试", "取消", new TipDialog.OnListener() {
                                @Override
                                public void left() {
                                    host.mHandler.post(new WeakRunnable<>(host, h -> {
                                            h.dismissConfigErrorDialog();
                                            h.initData();
                                        }));
                                }

                                @Override
                                public void right() {
                                    host.dataInitOk = true;
                                    host.jarInitOk = true;
                                    host.mHandler.post(new WeakRunnable<>(host, h -> {
                                            h.dismissConfigErrorDialog();
                                            h.initData();
                                        }));
                                }

                                @Override
                                public void cancel() {
                                    host.dataInitOk = true;
                                    host.jarInitOk = true;
                                    host.mHandler.post(new WeakRunnable<>(host, h -> {
                                            h.dismissConfigErrorDialog();
                                            h.initData();
                                        }));
                                }
                            });
                        if (!host.mConfigErrorDialog.isShowing())
                            host.mConfigErrorDialog.show();
                    }));
            }
        }, this);
    }

    private void warmSearchSpidersOnce() {
        if (searchSpiderWarmStarted) return;
        searchSpiderWarmStarted = true;
        ApiConfig.get().warmSearchSpiders();
    }

    private void loadHomeSort(boolean keepCurrentContent) {
        SourceBean home = ApiConfig.get().getHomeSourceBean();
        homeSortLoading = keepCurrentContent;
        if (keepCurrentContent && home != null && home.getName() != null && !home.getName().isEmpty()) {
            previousHomeName = tvName.getText() == null ? null : tvName.getText().toString();
            tvName.setText(formatSiteName(home.getName()));
        }
        tvNameAnimation();
        if (home == null) {
            loadingSourceKey = null;
            if (!keepCurrentContent) showLoading();
            sourceViewModel.getSort(null);
            return;
        }
        loadingSourceKey = home.getKey();
        if (!keepCurrentContent) {
            showLoading();
        }
        sourceViewModel.getSort(loadingSourceKey);
    }

    private void initViewPager(AbsSortXml absXml) {
        if (sortAdapter.getData().size() > 0) {
            for (MovieSort.SortData data : sortAdapter.getData()) {
                if (data.id.equals("my0")) {
                    if (Hawk.get(HawkConfig.HOME_REC, HawkConfig.DEFAULT_HOME_REC) == 1 && absXml != null && absXml.videoList != null && absXml.videoList.size() > 0) {
                        fragments.add(UserFragment.newInstance(absXml.videoList));
                    } else {
                        fragments.add(UserFragment.newInstance(null));
                    }
                } else {
                    fragments.add(GridFragment.newInstance(data));
                }
            }
            pageAdapter = new HomePageAdapter(getSupportFragmentManager(), fragments);
            try {
                Field field = ViewPager.class.getDeclaredField("mScroller");
                field.setAccessible(true);
                FixedSpeedScroller scroller = new FixedSpeedScroller(mContext, new AccelerateInterpolator());
                field.set(mViewPager, scroller);
                scroller.setmDuration(300);
            } catch (Exception e) {
            }
            mViewPager.setPageTransformer(true, new DefaultTransformer());
            mViewPager.setAdapter(pageAdapter);
            mViewPager.setCurrentItem(currentSelected, false);
            // 手机竖屏：内容区支持左右滑动切换分类
            mViewPager.setScrollable(true);
            mViewPager.clearOnPageChangeListeners();
            mViewPager.addOnPageChangeListener(new ViewPager.SimpleOnPageChangeListener() {
                @Override
                public void onPageSelected(int position) {
                    if (position != sortFocused && position >= 0 && position < fragments.size()) {
                        mGridView.setSelection(position);
                    }
                }
            });
        }
    }

    private void clearHomePages() {
        mHandler.removeCallbacks(mDataRunnable);
        currentSelected = 0;
        sortFocused = 0;
        sortChange = false;
        sortFocusView = null;
        currentView = null;
        if (pageAdapter != null) {
            mViewPager.setAdapter(null);
            pageAdapter.removeAll();
            pageAdapter = null;
        } else if (!fragments.isEmpty()) {
            fragments.clear();
        }
    }

    private void updateSortData(List<MovieSort.SortData> newSortData) {
        if (newSortData == null) {
            newSortData = new ArrayList<>();
        }
        List<MovieSort.SortData> oldSortData = sortAdapter.getData();
        if (oldSortData.isEmpty()
                || newSortData.isEmpty()
                || oldSortData.get(0) == null
                || newSortData.get(0) == null
                || !"my0".equals(oldSortData.get(0).id)
                || !"my0".equals(newSortData.get(0).id)) {
            sortAdapter.setNewData(newSortData);
            return;
        }
        int oldTailCount = oldSortData.size() - 1;
        if (oldTailCount > 0) {
            oldSortData.subList(1, oldSortData.size()).clear();
            sortAdapter.notifyItemRangeRemoved(1, oldTailCount);
        }
        if (newSortData.size() > 1) {
            oldSortData.addAll(newSortData.subList(1, newSortData.size()));
            sortAdapter.notifyItemRangeInserted(1, newSortData.size() - 1);
        }
    }

    @SuppressLint("NotifyDataSetChanged")
    @Override
    public void onBackPressed() {
        // 打断加载
        if (homeSortLoading) {
            cancelHomeSortLoading();
            return;
        }
        if (isLoading()) {
            refreshEmpty();
            return;
        }
        // 如果处于 VOD 删除模式，则退出该模式并刷新界面
        if (HawkConfig.hotVodDelete) {
            HawkConfig.hotVodDelete = false;
            UserFragment.homeHotVodAdapter.notifyDataSetChanged();
            return;
        }

        // 检查 fragments 状态
        if (this.fragments.size() <= 0 || this.sortFocused >= this.fragments.size() || this.sortFocused < 0) {
            doExit();
            return;
        }

        BaseLazyFragment baseLazyFragment = this.fragments.get(this.sortFocused);
        if (baseLazyFragment instanceof GridFragment) {
            GridFragment grid = (GridFragment) baseLazyFragment;
            // 如果当前 Fragment 能恢复之前保存的 UI 状态，则直接返回
            if (grid.restoreView()) {
                return;
            }
            // 如果 sortFocusView 存在且没有获取焦点，则请求焦点
            if (this.sortFocusView != null && !this.sortFocusView.isFocused()) {
                this.sortFocusView.requestFocus();
            }
            // 如果当前不是第一个界面，则将列表设置到第一项
            else if (this.sortFocused != 0) {
                this.mGridView.setSelection(0);
            } else {
                doExit();
            }
        } else if (baseLazyFragment instanceof UserFragment && UserFragment.tvHotList.canScrollVertically(-1)) {
            // 如果 UserFragment 列表可以向上滚动，则滚动到顶部
            UserFragment.tvHotList.scrollToPosition(0);
            this.mGridView.setSelection(0);
        } else {
            doExit();
        }
    }

    private void doExit() {
        // 如果两次返回间隔小于 2000 毫秒，则退出应用
        if (System.currentTimeMillis() - mExitTime < 2000) {
            AppManager.getInstance().finishAllActivity();
            unregisterEventBus();
            ControlManager.get().stopServer();
            finish();
            android.os.Process.killProcess(android.os.Process.myPid());
            System.exit(0);
        } else {
            // 否则仅提示用户，再按一次退出应用
            mExitTime = System.currentTimeMillis();
            Toast.makeText(mContext, "再按一次返回键退出应用", Toast.LENGTH_SHORT).show();
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
        // 站点在其他页面（如设置）被切换后，回到首页时自动刷新站点数据
        SourceBean curHome = ApiConfig.get().getHomeSourceBean();
        String curKey = curHome == null ? null : curHome.getKey();
        String curApiUrl = Hawk.get(HawkConfig.API_URL, "");
        if (dataInitOk && jarInitOk && loadedApiUrl != null && !loadedApiUrl.equals(curApiUrl)) {
            // 接口(api)在设置页被切换：内存中仍是旧配置，必须重新加载配置并刷新首页
            reloadForApiChange(curApiUrl);
        } else if (dataInitOk && jarInitOk && curKey != null && lastLoadedSourceKey != null
                && !curKey.equals(lastLoadedSourceKey)
                && !(homeSortLoading && curKey.equals(loadingSourceKey))) {
            SourceViewModel.clearSortCache(curKey);
            skipNextUpdate = false;
            refreshHome(false);
        }
        changeWallpaper(false);
        refreshTopInfoTextSize();
        mHandler.removeCallbacks(refreshTopInfoTextSizeRunnable);
        mHandler.postDelayed(refreshTopInfoTextSizeRunnable, 350);
        mHandler.removeCallbacks(refreshTopLayoutRunnable);
        mHandler.postDelayed(refreshTopLayoutRunnable, 450);
    }


    @Override
    protected void onPause() {
        super.onPause();
        mHandler.removeCallbacks(refreshTopInfoTextSizeRunnable);
        mHandler.removeCallbacks(refreshTopLayoutRunnable);
    }

    private void refreshTopInfoTextSize() {
        if (tvName == null) {
            return;
        }
        tvName.setTextSize(TypedValue.COMPLEX_UNIT_PX, getResources().getDimension(R.dimen.ts_26));
    }

    @Subscribe(threadMode = ThreadMode.MAIN)
    public void refresh(RefreshEvent event) {
        if (event.type == RefreshEvent.TYPE_PUSH_URL) {
            if (ApiConfig.get().getSource("push_agent") != null) {
                Intent newIntent = new Intent(mContext, DetailActivity.class);
                newIntent.putExtra("id", (String) event.obj);
                newIntent.putExtra("sourceKey", "push_agent");
                newIntent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP);
                HomeActivity.this.startActivity(newIntent);
            }
        } else if (event.type == RefreshEvent.TYPE_FILTER_CHANGE) {
            if (currentView != null) {
                showFilterIcon((int) event.obj);
            }
        } else if (event.type == RefreshEvent.TYPE_HOME_SOURCE_CHANGE) {
            SourceBean home = ApiConfig.get().getHomeSourceBean();
            if (home != null) SourceViewModel.clearSortCache(home.getKey());
            skipNextUpdate = false;
            refreshHome(false);
        }
    }

    private void showFilterIcon(int count) {
        boolean visible = count > 0;
        currentView.findViewById(R.id.tvFilterColor).setVisibility(visible ? View.VISIBLE : View.GONE);
        currentView.findViewById(R.id.tvFilter).setVisibility(visible ? View.GONE : View.VISIBLE);
    }

    private final Runnable mDataRunnable = new Runnable() {
        @Override
        public void run() {
            if (sortChange) {
                sortChange = false;
                BaseLazyFragment baseLazyFragment = fragments.get(sortFocused);
                if (sortFocused != currentSelected) {
                    currentSelected = sortFocused;
                    mViewPager.setCurrentItem(sortFocused, false);
                    changeTop(sortFocused != 0);
                    if (baseLazyFragment instanceof GridFragment && ((GridFragment) baseLazyFragment).shouldReloadOnSelect()) {
                        ((GridFragment) baseLazyFragment).forceRefresh();
                    }
                } else if (baseLazyFragment instanceof GridFragment && ((GridFragment) baseLazyFragment).shouldReloadOnSelect()) {
                    ((GridFragment) baseLazyFragment).forceRefresh();
                }
            }
        }
    };

    private long menuKeyDownTime = 0;
    private static final long LONG_PRESS_THRESHOLD = 2000; // 设置长按的阈值，单位是毫秒
    @Override
    public boolean dispatchKeyEvent(KeyEvent event) {
        if (topHide < 0)
            return false;
        int keyCode = event.getKeyCode();
        if (keyCode == KeyEvent.KEYCODE_MENU) {
            if (event.getAction() == KeyEvent.ACTION_DOWN) {
                menuKeyDownTime = System.currentTimeMillis();
            } else if (event.getAction() == KeyEvent.ACTION_UP) {
                long pressDuration = System.currentTimeMillis() - menuKeyDownTime;
                if (pressDuration >= LONG_PRESS_THRESHOLD) {
                    jumpActivity(SettingActivity.class);;
                }else {
                    showSiteSwitch();
                }
            }
        }
        return super.dispatchKeyEvent(event);
    }

    byte topHide = 0;

    private void changeTop(boolean hide) {
        ViewObj viewObj = new ViewObj(topLayout, (ViewGroup.MarginLayoutParams) topLayout.getLayoutParams());
        AnimatorSet animatorSet = new AnimatorSet();
        animatorSet.addListener(new Animator.AnimatorListener() {
            @Override
            public void onAnimationStart(Animator animation) {

            }

            @Override
            public void onAnimationEnd(Animator animation) {
                topHide = (byte) (hide ? 1 : 0);
            }

            @Override
            public void onAnimationCancel(Animator animation) {

            }

            @Override
            public void onAnimationRepeat(Animator animation) {

            }
        });
        if (hide && topHide == 0) {
            animatorSet.playTogether(ObjectAnimator.ofObject(viewObj, "marginTop", new IntEvaluator(),
                            AutoSizeUtils.mm2px(this.mContext, 10.0f),
                            AutoSizeUtils.mm2px(this.mContext, 0.0f)),
                    ObjectAnimator.ofObject(viewObj, "height", new IntEvaluator(),
                            AutoSizeUtils.mm2px(this.mContext, 50.0f),
                            AutoSizeUtils.mm2px(this.mContext, 1.0f)),
                    ObjectAnimator.ofFloat(this.topLayout, "alpha", 1.0f, 0.0f));
            animatorSet.setDuration(200);
            animatorSet.start();
            return;
        }
        if (!hide && topHide == 1) {
            animatorSet.playTogether(ObjectAnimator.ofObject(viewObj, "marginTop", new IntEvaluator(),
                            AutoSizeUtils.mm2px(this.mContext, 0.0f),
                            AutoSizeUtils.mm2px(this.mContext, 10.0f)),
                    ObjectAnimator.ofObject(viewObj, "height", new IntEvaluator(),
                            AutoSizeUtils.mm2px(this.mContext, 1.0f),
                            AutoSizeUtils.mm2px(this.mContext, 50.0f)),
                    ObjectAnimator.ofFloat(this.topLayout, "alpha", 0.0f, 1.0f));
            animatorSet.setDuration(200);
            animatorSet.start();
        }
    }

    @Override
    protected void onDestroy() {
        dismissHomeDialogs();
        mHandler.removeCallbacksAndMessages(null);
        super.onDestroy();
        unregisterEventBus();
        if (isFinishing()) {
            ControlManager.get().stopServer();
        }
    }

    private void unregisterEventBus() {
        if (eventBusRegistered) {
            EventBus.getDefault().unregister(this);
            eventBusRegistered = false;
        }
    }

    private SelectDialog<SourceBean> mSiteSwitchDialog;

    void showSiteSwitch() {
        if (isActivityUnavailable()) return;
        List<SourceBean> sites = ApiConfig.get().getSwitchSourceBeanList();
        if (sites.isEmpty()) return;
        int select = sites.indexOf(ApiConfig.get().getHomeSourceBean());
        if (select < 0 || select >= sites.size()) select = 0;
        if (mSiteSwitchDialog == null) {
            mSiteSwitchDialog = new SelectDialog<>(HomeActivity.this);
            TvRecyclerView tvRecyclerView = mSiteSwitchDialog.findViewById(R.id.list);
            // 根据 sites 数量动态计算列数
            int spanCount = (int) Math.floor(sites.size() / 20.0);
            spanCount = Math.min(spanCount, 2);
            tvRecyclerView.setLayoutManager(new V7GridLayoutManager(mSiteSwitchDialog.getContext(), spanCount + 1));
            // 设置对话框宽度
            ConstraintLayout cl_root = mSiteSwitchDialog.findViewById(R.id.cl_root);
            ViewGroup.LayoutParams clp = cl_root.getLayoutParams();
            clp.width = AutoSizeUtils.mm2px(mSiteSwitchDialog.getContext(), 380 + 200 * spanCount);
            int maxWidth = mContext.getResources().getDisplayMetrics().widthPixels - AutoSizeUtils.mm2px(mSiteSwitchDialog.getContext(), 40);
            if (clp.width > maxWidth) clp.width = maxWidth;
            mSiteSwitchDialog.setTip("请选择首页数据源");
        }
        mSiteSwitchDialog.setAdapter(new SelectDialogAdapter.SelectDialogInterface<SourceBean>() {
            @Override
            public void click(SourceBean value, int pos) {
                dismissSiteSwitchDialog();
                if (value == null) return;
                previousHomeSource = ApiConfig.get().getHomeSourceBean();
                // 清理上一次可能残留的加载状态，避免新的站点结果被提前 return 丢弃
                skipNextUpdate = false;
                homeSortLoading = false;
                loadingSourceKey = null;
                ApiConfig.get().setSourceBean(value);
                // 清除新站点缓存，强制重新拉取站点数据
                SourceViewModel.clearSortCache(value.getKey());
                refreshHome(false);
            }
            @Override
            public String getDisplay(SourceBean val) {
                return val.getName();
            }
        }, new DiffUtil.ItemCallback<SourceBean>() {
            @Override
            public boolean areItemsTheSame(@NonNull SourceBean oldItem, @NonNull SourceBean newItem) {
                return oldItem == newItem;
            }
            @Override
            public boolean areContentsTheSame(@NonNull SourceBean oldItem, @NonNull SourceBean newItem) {
                return oldItem.getKey().equals(newItem.getKey());
            }
        }, sites, select);
        if (!mSiteSwitchDialog.isShowing())
            mSiteSwitchDialog.show();
    }

    private void refreshHome()
    {
        refreshHome(true);
    }

    private void refreshHomeSort() {
        refreshHomeRec = true;
        if (UserFragment.homeHotVodAdapter != null) {
            UserFragment.homeHotVodAdapter.setNewData(new ArrayList<Movie.Video>());
        }
        SourceBean home = ApiConfig.get().getHomeSourceBean();
        if (home != null) {
            SourceViewModel.clearSortCache(home.getKey());
        }
        skipNextUpdate = false;
        refreshHome(false);
    }

    private void updateHomeRec(AbsSortXml absXml) {
        if (!refreshHomeRec) return;
        refreshHomeRec = false;
        if (Hawk.get(HawkConfig.HOME_REC, HawkConfig.DEFAULT_HOME_REC) != 1) return;
        if (absXml == null || absXml.videoList == null || UserFragment.homeHotVodAdapter == null) return;
        UserFragment.homeHotVodAdapter.setNewData(absXml.videoList);
    }

    /**
     * 接口(api)切换后：内存里仍是旧配置，需重置初始化状态、清空运行时缓存，
     * 重新 loadConfig 并完整加载一次首页分类与列表。
     */
    private void reloadForApiChange(final String apiUrl) {
        if (isActivityUnavailable()) {
            return;
        }
        loadedApiUrl = apiUrl;
        useCacheConfig = false;
        skipLiveJumpOnce = true;
        SourceViewModel.clearRuntimeCache();
        dataInitOk = false;
        jarInitOk = false;
        homeSortLoading = false;
        loadingSourceKey = null;
        skipNextUpdate = false;
        lastLoadedSourceKey = null;
        refreshHomeRec = false;
        previousHomeName = null;
        previousHomeSource = null;
        dismissHomeDialogs();
        clearHomePages();
        updateSortData(new ArrayList<MovieSort.SortData>());
        showLoading();
        tvNameAnimation();
        initData();
        Toast.makeText(mContext, "接口已切换，正在重新加载", Toast.LENGTH_SHORT).show();
    }

    private void refreshHome(final boolean restart)
    {
        if (Thread.currentThread() != android.os.Looper.getMainLooper().getThread()) {
            mHandler.post(new WeakRunnable<>(HomeActivity.this, host -> host.refreshHome(restart)));
            return;
        }
        if (isActivityUnavailable()) {
            return;
        }
        dismissHomeDialogs();
        if (!restart) {
            loadHomeSort(true);
            return;
        }
        Intent intent = new Intent(getApplicationContext(), HomeActivity.class);
        intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
        Bundle bundle = new Bundle();
        bundle.putBoolean("useCache", true);
        intent.putExtras(bundle);
        HomeActivity.this.startActivity(intent);
    }

    private boolean isActivityUnavailable() {
        return isFinishing() || (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.JELLY_BEAN_MR1 && isDestroyed());
    }

    private void dismissHomeDialogs() {
        dismissConfigErrorDialog();
        dismissSiteSwitchDialog();
        dismissAllSortDialog();
    }

    private void dismissAllSortDialog() {
        if (mAllSortDialog != null) {
            if (mAllSortDialog.isShowing()) {
                mAllSortDialog.dismiss();
            }
            mAllSortDialog = null;
        }
    }

    private void dismissConfigErrorDialog() {
        if (mConfigErrorDialog != null) {
            if (mConfigErrorDialog.isShowing()) {
                mConfigErrorDialog.dismiss();
            }
            mConfigErrorDialog = null;
        }
    }

    private void dismissSiteSwitchDialog() {
        if (mSiteSwitchDialog != null) {
            if (mSiteSwitchDialog.isShowing()) {
                mSiteSwitchDialog.dismiss();
            }
            mSiteSwitchDialog = null;
        }
    }

    private void refreshEmpty()
    {
        skipNextUpdate=true;
        showSuccess();
        cancelHomeSortLoading();
        clearHomePages();
        sortAdapter.setNewData(DefaultConfig.adjustSort(ApiConfig.get().getHomeSourceBean().getKey(), new ArrayList<>(), true));
        initViewPager(null);
        tvName.clearAnimation();
    }

    private void cancelHomeSortLoading() {
        homeSortLoading = false;
        loadingSourceKey = null;
        tvName.clearAnimation();
        if (previousHomeSource != null) {
            ApiConfig.get().setSourceBean(previousHomeSource);
        }
        if (previousHomeName != null && !previousHomeName.isEmpty()) {
            tvName.setText(previousHomeName);
        }
        previousHomeSource = null;
        previousHomeName = null;
    }

    private void tvNameAnimation()
    {
        tvName.clearAnimation();
        AlphaAnimation blinkAnimation = new AlphaAnimation(0.0f, 1.0f);
        blinkAnimation.setDuration(500);
        blinkAnimation.setStartOffset(20);
        blinkAnimation.setRepeatMode(Animation.REVERSE);
        blinkAnimation.setRepeatCount(Animation.INFINITE);
        tvName.startAnimation(blinkAnimation);
    }
}
