package com.github.tvbox.osc.ui.activity;

import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.os.Build;
import android.os.Bundle;
import android.text.TextUtils;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.KeyEvent;
import android.view.View;
import android.view.inputmethod.EditorInfo;
import android.view.inputmethod.InputMethodManager;
import android.widget.EditText;
import android.widget.GridLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.lifecycle.ViewModelProvider;

import com.chad.library.adapter.base.BaseQuickAdapter;
import com.github.catvod.crawler.JsLoader;
import com.github.tvbox.osc.R;
import com.github.tvbox.osc.api.ApiConfig;
import com.github.tvbox.osc.base.BaseActivity;
import com.github.tvbox.osc.bean.AbsXml;
import com.github.tvbox.osc.bean.Movie;
import com.github.tvbox.osc.bean.SourceBean;
import com.github.tvbox.osc.event.RefreshEvent;
import com.github.tvbox.osc.event.ServerEvent;
import com.github.tvbox.osc.ui.adapter.SearchAdapter;
import com.github.tvbox.osc.ui.dialog.SearchCheckboxDialog;
import com.github.tvbox.osc.util.BottomNavHelper;
import com.github.tvbox.osc.util.FastClickCheckUtil;
import com.github.tvbox.osc.util.HawkConfig;
import com.github.tvbox.osc.util.HistoryHelper;
import com.github.tvbox.osc.util.SearchHelper;
import com.github.tvbox.osc.viewmodel.SourceViewModel;
import com.orhanobut.hawk.Hawk;
import com.owen.tvrecyclerview.widget.TvRecyclerView;
import com.owen.tvrecyclerview.widget.V7GridLayoutManager;
import com.owen.tvrecyclerview.widget.V7LinearLayoutManager;

import org.greenrobot.eventbus.EventBus;
import org.greenrobot.eventbus.Subscribe;
import org.greenrobot.eventbus.ThreadMode;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.SynchronousQueue;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * 搜索页（手机版）：顶部搜索框+搜索源+搜索按钮，下方搜索历史，结果原地展示
 */
public class SearchActivity extends BaseActivity {
    private static final int SEARCH_THREAD_COUNT = 12;
    private static final int SEARCH_MAX_THREAD_COUNT = 24;
    private static final int SEARCH_NEXT_BATCH_SECONDS = 0;
    private static final int SEARCH_SITE_TIMEOUT_SECONDS = 4;
    private LinearLayout llLayout;
    private LinearLayout llHistoryWord;
    private LinearLayout llSiteLabels;
    private android.widget.ScrollView svSiteLabels;
    private TvRecyclerView mGridView;
    private GridLayout historyWordGrid;
    SourceViewModel sourceViewModel;
    private EditText etSearch;
    private TextView tvSearch;
    private ImageView tvHistoryClear;
    private SearchAdapter searchAdapter;
    private String searchTitle = "";
    private final List<Movie.Video> highMatchVods = new ArrayList<>();
    private boolean showHighMatchResults = false;
    private TextView tvSearchCheckboxBtn;
    // 全部搜索结果（未按站点分组）
    private final List<Movie.Video> allSearchResults = new ArrayList<>();
    // 按站点分组的搜索结果，按返回顺序保留
    private final LinkedHashMap<String, List<Movie.Video>> siteSearchResults = new LinkedHashMap<>();
    // 当前选中的站点标签，null 表示"全部"
    private String selectedSiteKey = null;

    private static HashMap<String, String> mCheckSources = null;
    private SearchCheckboxDialog mSearchCheckboxDialog = null;

    @Override
    protected int getLayoutResID() {
        return R.layout.activity_search;
    }

    @Override
    protected boolean useImmersive() {
        return false;
    }

    @Override
    protected void init() {
        initView();
        initViewModel();
        initData();
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (searchPaused) {
            resumePausedSearches();
        }
        refreshSearchHistoryWords();
    }

    private void initView() {
        EventBus.getDefault().register(this);
        BottomNavHelper.setup(this, 0);
        llLayout = findViewById(R.id.llLayout);
        llHistoryWord = findViewById(R.id.llHistoryWord);
        llSiteLabels = findViewById(R.id.llSiteLabels);
        svSiteLabels = findViewById(R.id.svSiteLabels);
        etSearch = findViewById(R.id.etSearch);
        tvSearch = findViewById(R.id.tvSearch);
        tvSearchCheckboxBtn = findViewById(R.id.tvSearchCheckboxBtn);
        mGridView = findViewById(R.id.mGridView);
        historyWordGrid = findViewById(R.id.historyWordGrid);
        tvHistoryClear = findViewById(R.id.tvHistoryClear);
        mGridView.setHasFixedSize(true);
        // 搜索结果为 2 列海报缩略图布局（左侧站点标签 + 右侧 2 列）
        mGridView.setLayoutManager(new V7GridLayoutManager(this.mContext, 2));
        searchAdapter = new SearchAdapter();
        mGridView.setAdapter(searchAdapter);
        searchAdapter.setOnItemClickListener(new BaseQuickAdapter.OnItemClickListener() {
            @Override
            public void onItemClick(BaseQuickAdapter adapter, View view, int position) {
                FastClickCheckUtil.check(view);
                Movie.Video video = searchAdapter.getData().get(position);
                if (video != null) {
                    openSearchVideo(video);
                }
            }
        });
        tvHistoryClear.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                FastClickCheckUtil.check(v);
                HistoryHelper.clearSearchHistory();
                refreshSearchHistoryWords();
                Toast.makeText(mContext, "已清空搜索历史", Toast.LENGTH_SHORT).show();
            }
        });
        tvSearch.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                FastClickCheckUtil.check(v);
                String wd = etSearch.getText().toString().trim();
                if (!TextUtils.isEmpty(wd)) {
                    hiddenImm();
                    search(wd);
                } else {
                    Toast.makeText(mContext, "输入内容不能为空", Toast.LENGTH_SHORT).show();
                }
            }
        });

        etSearch.setOnEditorActionListener(new TextView.OnEditorActionListener() {
            @Override
            public boolean onEditorAction(TextView v, int actionId, KeyEvent event) {
                if (actionId == EditorInfo.IME_ACTION_DONE || actionId == EditorInfo.IME_ACTION_SEARCH || (event != null && event.getKeyCode() == KeyEvent.KEYCODE_ENTER)) {
                    String wd = etSearch.getText().toString().trim();
                    if (!TextUtils.isEmpty(wd)) {
                        hiddenImm();
                        search(wd);
                    } else {
                        Toast.makeText(mContext, "输入内容不能为空", Toast.LENGTH_SHORT).show();
                    }
                    return true;
                }
                return false;
            }
        });
        setLoadSir(llLayout);
        tvSearchCheckboxBtn.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                List<SourceBean> searchAbleSource = ApiConfig.get().getSearchSourceBeanList();
                if (mSearchCheckboxDialog == null) {
                    mSearchCheckboxDialog = new SearchCheckboxDialog(SearchActivity.this, searchAbleSource, mCheckSources);
                } else {
                    if (searchAbleSource.size() != mSearchCheckboxDialog.mSourceList.size()) {
                        mSearchCheckboxDialog.setMSourceList(searchAbleSource);
                    }
                }
                mSearchCheckboxDialog.setOnDismissListener(new DialogInterface.OnDismissListener() {
                    @Override
                    public void onDismiss(DialogInterface dialog) {
                        dialog.dismiss();
                    }
                });
                mSearchCheckboxDialog.show();
            }
        });
    }

    private void refreshSearchHistoryWords() {
        historyWordGrid.post(new Runnable() {
            @Override
            public void run() {
                ArrayList<String> history = Hawk.get(HawkConfig.SEARCH_HISTORY, new ArrayList<String>());
                historyWordGrid.removeAllViews();
                int itemHeight = getResources().getDimensionPixelSize(R.dimen.vs_50);
                int itemMargin = getResources().getDimensionPixelSize(R.dimen.vs_5);
                int paddingH = getResources().getDimensionPixelSize(R.dimen.vs_10);
                int minWidth = getResources().getDimensionPixelSize(R.dimen.vs_80);
                int availableWidth = historyWordGrid.getWidth();
                if (availableWidth <= 0) availableWidth = llHistoryWord.getWidth();
                float textSize = getResources().getDimension(R.dimen.ts_22);
                int textColor = getResources().getColor(R.color.color_FFFFFF);
                LinearLayout row = null;
                int rowWidth = 0;
                for (int i = 0; i < history.size(); i++) {
                    final String word = history.get(i);
                    TextView item = new TextView(SearchActivity.this);
                    item.setText(word);
                    item.setSingleLine(true);
                    item.setGravity(Gravity.CENTER);
                    item.setIncludeFontPadding(false);
                    item.setFocusable(true);
                    item.setTextColor(textColor);
                    item.setTextSize(TypedValue.COMPLEX_UNIT_PX, textSize);
                    item.setMinWidth(minWidth);
                    item.setPadding(paddingH, 0, paddingH, 0);
                    item.setBackgroundResource(R.drawable.shape_user_focus);
                    item.measure(
                            View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED),
                            View.MeasureSpec.makeMeasureSpec(itemHeight, View.MeasureSpec.EXACTLY));
                    int itemWidth = Math.max(minWidth, item.getMeasuredWidth());
                    int rowItemWidth = itemWidth + itemMargin * 2;
                    if (row == null || (rowWidth > 0 && rowWidth + rowItemWidth > availableWidth)) {
                        row = new LinearLayout(SearchActivity.this);
                        row.setOrientation(LinearLayout.HORIZONTAL);
                        GridLayout.LayoutParams rowParams = new GridLayout.LayoutParams(
                                GridLayout.spec(GridLayout.UNDEFINED),
                                GridLayout.spec(GridLayout.UNDEFINED));
                        rowParams.width = GridLayout.LayoutParams.MATCH_PARENT;
                        rowParams.height = GridLayout.LayoutParams.WRAP_CONTENT;
                        historyWordGrid.addView(row, rowParams);
                        rowWidth = 0;
                    }
                    item.setOnClickListener(new View.OnClickListener() {
                        @Override
                        public void onClick(View v) {
                            search(word);
                        }
                    });
                    LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(itemWidth, itemHeight);
                    params.setMargins(itemMargin, itemMargin, itemMargin, itemMargin);
                    row.addView(item, params);
                    rowWidth += rowItemWidth;
                }
            }
        });
    }

    private void initViewModel() {
        sourceViewModel = new ViewModelProvider(this).get(SourceViewModel.class);
        sourceViewModel.listResult.observe(this, new androidx.lifecycle.Observer<AbsXml>() {
            @Override
            public void onChanged(AbsXml data) {
                if (!folderLoading) return;
                folderLoading = false;
                if (data == null || data.movie == null || data.movie.videoList == null) {
                    showEmpty();
                    return;
                }
                showSuccess();
                mGridView.setVisibility(View.VISIBLE);
                svSiteLabels.setVisibility(View.GONE);
                searchAdapter.setNewData(data.movie.videoList);
            }
        });
    }

    private void openSearchVideo(Movie.Video video) {
        pauseSearchTasks();
        if (TextUtils.equals("folder", video.tag)) {
            folderHistory.add(new ArrayList<>(searchAdapter.getData()));
            folderLoading = true;
            showLoading();
            sourceViewModel.getList(video.sourceKey, video.id);
            return;
        }
        Bundle bundle = new Bundle();
        bundle.putString("id", video.id);
        bundle.putString("sourceKey", video.sourceKey);
        bundle.putString("title", video.name);
        bundle.putString("picture", video.pic);
        putDetailFallbackCandidates(bundle, video);
        jumpActivity(DetailActivity.class, bundle);
    }

    @Override
    public void onBackPressed() {
        if (!folderHistory.isEmpty()) {
            folderLoading = false;
            List<Movie.Video> previous = folderHistory.remove(folderHistory.size() - 1);
            showSuccess();
            mGridView.setVisibility(View.VISIBLE);
            svSiteLabels.setVisibility(folderHistory.isEmpty() ? View.VISIBLE : View.GONE);
            searchAdapter.setNewData(previous);
            return;
        }
        if (llLayout.getVisibility() == View.VISIBLE) {
            // 返回时从搜索结果回到搜索历史
            pauseSearchTasks();
            llLayout.setVisibility(View.GONE);
            llHistoryWord.setVisibility(View.VISIBLE);
            refreshSearchHistoryWords();
            return;
        }
        // 底部导航页面：返回直接回首页，而不是按切换记录逐步返回
        com.github.tvbox.osc.util.BottomNavHelper.goHome(this);
    }

    private void initData() {
        initCheckedSourcesForSearch();
        Intent intent = getIntent();
        if (intent != null && intent.hasExtra("title")) {
            String title = intent.getStringExtra("title");
            etSearch.setText(title);
            search(title);
        }
        refreshSearchHistoryWords();
    }

    @Subscribe(threadMode = ThreadMode.MAIN)
    public void server(ServerEvent event) {
        if (event.type == ServerEvent.SERVER_SEARCH) {
            String title = (String) event.obj;
            search(title);
        }
    }

    @Subscribe(threadMode = ThreadMode.MAIN)
    public void refresh(RefreshEvent event) {
        if (event.type == RefreshEvent.TYPE_SEARCH_RESULT) {
            try {
                searchData(event.obj == null ? null : (AbsXml) event.obj);
            } catch (Exception e) {
                searchData(null);
            }
        }
    }

    private void initCheckedSourcesForSearch() {
        mCheckSources = SearchHelper.getSourcesForSearch();
    }

    public static void setCheckedSourcesForSearch(HashMap<String, String> checkedSources) {
        mCheckSources = checkedSources;
    }

    private void search(String title) {
        cancel();
        // 结果覆盖搜索历史，原地展示
        llHistoryWord.setVisibility(View.GONE);
        llLayout.setVisibility(View.VISIBLE);
        showLoading();
        etSearch.setText(title);
        etSearch.setSelection(title.length());

        //写入历史记录
        HistoryHelper.setSearchHistory(title);

        this.searchTitle = title;
        mGridView.setVisibility(View.INVISIBLE);
        // 重置站点分组与选中状态
        allSearchResults.clear();
        siteSearchResults.clear();
        selectedSiteKey = null;
        if (llSiteLabels != null) llSiteLabels.removeAllViews();
        searchAdapter.setNewData(new ArrayList<>());
        searchResult();
    }

    private ExecutorService searchExecutorService = null;
    private ScheduledExecutorService searchTimeoutExecutor = null;
    private AtomicInteger allRunCount = new AtomicInteger(0);
    private final Set<String> pendingSearchKeys = Collections.synchronizedSet(new HashSet<String>());
    private final List<SearchTask> waitingSearchTasks = Collections.synchronizedList(new ArrayList<SearchTask>());
    private final Set<String> startedSearchKeys = Collections.synchronizedSet(new HashSet<String>());
    private final Set<String> releasedSearchKeys = Collections.synchronizedSet(new HashSet<String>());
    private final AtomicInteger searchTokenSeq = new AtomicInteger(0);
    private final AtomicInteger totalSearchCount = new AtomicInteger(0);
    private String currentSearchToken = "";
    private boolean searchPaused = false;
    private final List<Movie.Video> detailFallbackSearchResults = new ArrayList<>();
    private final List<List<Movie.Video>> folderHistory = new ArrayList<>();
    private boolean folderLoading;

    private void searchResult() {
        try {
            if (searchExecutorService != null) {
                searchExecutorService.shutdownNow();
                searchExecutorService = null;
                JsLoader.stopAll();
            }
            if (searchTimeoutExecutor != null) {
                searchTimeoutExecutor.shutdownNow();
                searchTimeoutExecutor = null;
            }
        } catch (Throwable th) {
            th.printStackTrace();
        } finally {
            searchAdapter.setNewData(new ArrayList<>());
            allRunCount.set(0);
            pendingSearchKeys.clear();
            waitingSearchTasks.clear();
            startedSearchKeys.clear();
            releasedSearchKeys.clear();
            highMatchVods.clear();
            detailFallbackSearchResults.clear();
            folderHistory.clear();
            showHighMatchResults = false;
            totalSearchCount.set(0);
            currentSearchToken = String.valueOf(searchTokenSeq.incrementAndGet());
            searchPaused = false;
        }
        List<SourceBean> searchRequestList = new ArrayList<>();
        searchRequestList.addAll(ApiConfig.get().getSourceBeanList());
        SourceBean home = ApiConfig.get().getHomeSourceBean();
        searchRequestList.remove(home);
        searchRequestList.add(0, home);

        ArrayList<SearchTask> searchTasks = new ArrayList<>();
        for (SourceBean bean : searchRequestList) {
            if (!bean.isSearchable()) {
                continue;
            }
            if (mCheckSources != null && !mCheckSources.containsKey(bean.getKey())) {
                continue;
            }
            searchTasks.add(new SearchTask(bean.getKey(), searchTitle, currentSearchToken, isBlockingSearchSource(bean)));
        }
        if (searchTasks.size() <= 0) {
            Toast.makeText(mContext, "没有指定搜索源", Toast.LENGTH_SHORT).show();
            showEmpty();
            return;
        }
        for (SearchTask task : searchTasks) {
            pendingSearchKeys.add(task.sourceKey);
        }
        allRunCount.set(searchTasks.size());
        totalSearchCount.set(searchTasks.size());
        searchExecutorService = createSearchExecutor();
        searchTimeoutExecutor = Executors.newSingleThreadScheduledExecutor();
        startFastSearchTasks(searchTasks);
        waitingSearchTasks.addAll(searchTasks);
        startNextSearchBatch(currentSearchToken);
    }

    private boolean matchSearchResult(String name, String searchTitle) {
        if (TextUtils.isEmpty(name) || TextUtils.isEmpty(searchTitle)) return false;
        return TextUtils.equals(name.trim(), searchTitle.trim());
    }

    private boolean isHighMatchSearchResult(Movie.Video video) {
        return video != null && !TextUtils.isEmpty(video.name) && !TextUtils.isEmpty(searchTitle)
                && video.name.replaceAll("\\s+", "").startsWith(searchTitle.replaceAll("\\s+", ""));
    }

    private boolean shouldShowHighMatchResults() {
        // 增量显示：只要有站点返回了匹配内容就立即展示，不再等前N个站点都返回
        return !highMatchVods.isEmpty();
    }

    private void addSearchResults(List<Movie.Video> data) {
        if (data == null || data.isEmpty()) return;
        boolean firstIn = allSearchResults.isEmpty();
        allSearchResults.addAll(data);
        for (Movie.Video video : data) {
            if (video == null) continue;
            String key = video.sourceKey == null ? "" : video.sourceKey;
            List<Movie.Video> list = siteSearchResults.get(key);
            if (list == null) {
                list = new ArrayList<>();
                siteSearchResults.put(key, list);
            }
            list.add(video);
        }
        if (firstIn) {
            showSuccess();
            mGridView.setVisibility(View.VISIBLE);
            llLayout.setVisibility(View.VISIBLE);
        }
        refreshSiteLabels();
        refreshResultGrid();
    }

    /**
     * 左侧站点标签：第一项"全部"，其后每个有搜索结果的站点一项，可点击切换
     */
    private void refreshSiteLabels() {
        if (llSiteLabels == null) return;
        llSiteLabels.removeAllViews();
        svSiteLabels.setVisibility(View.VISIBLE);
        addSiteLabelView("全部(" + allSearchResults.size() + ")", null);
        for (Map.Entry<String, List<Movie.Video>> entry : siteSearchResults.entrySet()) {
            String siteName = getSiteDisplayName(entry.getKey());
            addSiteLabelView(siteName + "(" + entry.getValue().size() + ")", entry.getKey());
        }
    }

    private String getSiteDisplayName(String sourceKey) {
        try {
            if (!TextUtils.isEmpty(sourceKey) && ApiConfig.get() != null) {
                SourceBean sourceBean = ApiConfig.get().getSource(sourceKey);
                if (sourceBean != null && !TextUtils.isEmpty(sourceBean.getName())) {
                    return sourceBean.getName();
                }
            }
        } catch (Throwable ignored) {
        }
        return TextUtils.isEmpty(sourceKey) ? "未知站点" : sourceKey;
    }

    private void addSiteLabelView(String text, final String siteKey) {
        TextView label = new TextView(this);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        lp.bottomMargin = getResources().getDimensionPixelSize(R.dimen.vs_8);
        label.setLayoutParams(lp);
        label.setText(text);
        label.setTextSize(android.util.TypedValue.COMPLEX_UNIT_PX,
                getResources().getDimension(R.dimen.ts_20));
        label.setGravity(android.view.Gravity.CENTER_VERTICAL);
        label.setSingleLine(true);
        label.setEllipsize(TextUtils.TruncateAt.END);
        label.setPadding(getResources().getDimensionPixelSize(R.dimen.vs_8),
                getResources().getDimensionPixelSize(R.dimen.vs_8),
                getResources().getDimensionPixelSize(R.dimen.vs_8),
                getResources().getDimensionPixelSize(R.dimen.vs_8));
        boolean selected = siteKey == null ? selectedSiteKey == null : siteKey.equals(selectedSiteKey);
        label.setTextColor(selected ? 0xFF0CADE2 : 0xFFFFFFFF);
        label.setTypeface(null, selected ? android.graphics.Typeface.BOLD : android.graphics.Typeface.NORMAL);
        label.setBackgroundResource(R.drawable.shape_user_search);
        label.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if (siteKey == null ? selectedSiteKey == null : siteKey.equals(selectedSiteKey)) return;
                selectedSiteKey = siteKey;
                refreshSiteLabels();
                refreshResultGrid();
                mGridView.scrollToPosition(0);
            }
        });
        llSiteLabels.addView(label);
    }

    /**
     * 按当前选中的站点标签刷新右侧海报列表
     */
    private void refreshResultGrid() {
        if (searchAdapter == null) return;
        List<Movie.Video> shown;
        if (selectedSiteKey == null) {
            shown = new ArrayList<>(allSearchResults);
        } else {
            List<Movie.Video> list = siteSearchResults.get(selectedSiteKey);
            shown = list == null ? new ArrayList<Movie.Video>() : new ArrayList<>(list);
        }
        searchAdapter.setNewData(shown);
    }

    private void searchData(AbsXml absXml) {
        if (!isCurrentSearchResult(absXml)) {
            return;
        }
        String sourceKey = absXml == null ? "" : absXml.sourceKey;
        if (!markSearchFinished(sourceKey, absXml.searchToken)) {
            return;
        }
        releaseSearchSlotAndStartNext(sourceKey, absXml.searchToken);
        if (absXml != null && absXml.movie != null && absXml.movie.videoList != null && absXml.movie.videoList.size() > 0) {
            List<Movie.Video> exactData = new ArrayList<>();
            List<Movie.Video> highData = new ArrayList<>();
            for (Movie.Video video : absXml.movie.videoList) {
                if (isHighMatchSearchResult(video)) {
                    highMatchVods.add(video);
                    highData.add(video);
                    detailFallbackSearchResults.add(video);
                }
                if (matchSearchResult(video.name, searchTitle)) {
                    exactData.add(video);
                }
            }

            if (showHighMatchResults) {
                addSearchResults(highData);
            } else if (!exactData.isEmpty()) {
                addSearchResults(exactData);
            }
        }

        if (!showHighMatchResults && shouldShowHighMatchResults()) {
            showHighMatchResults = true;
            // 此前已按精确匹配展示过的结果不再重复添加
            List<Movie.Video> pendingHighMatch = new ArrayList<>();
            for (Movie.Video video : highMatchVods) {
                if (video != null && !allSearchResults.contains(video)) {
                    pendingHighMatch.add(video);
                }
            }
            addSearchResults(pendingHighMatch);
        }

        finishSearchIfDone();
    }

    private void putDetailFallbackCandidates(Bundle bundle, Movie.Video selectedVideo) {
        if (bundle == null || selectedVideo == null || TextUtils.isEmpty(selectedVideo.name)) {
            return;
        }
        String title = selectedVideo.name.trim();
        ArrayList<Movie.Video> candidates = new ArrayList<>();
        Set<String> keys = new HashSet<>();
        for (Movie.Video video : detailFallbackSearchResults) {
            if (video == null || TextUtils.isEmpty(video.id)
                    || !TextUtils.equals(title, video.name == null ? "" : video.name.trim())) {
                continue;
            }
            String key = (video.sourceKey == null ? "" : video.sourceKey) + "|" + video.id;
            if (keys.add(key)) {
                candidates.add(video);
                if (candidates.size() >= 20) {
                    break;
                }
            }
        }
        if (!candidates.isEmpty()) {
            bundle.putSerializable(DetailActivity.EXTRA_DETAIL_FALLBACK_CANDIDATES, candidates);
        }
    }

    private void scheduleSearchAdvance(final String sourceKey, final String searchToken) {
        if (searchTimeoutExecutor == null) return;
        searchTimeoutExecutor.schedule(new Runnable() {
            @Override
            public void run() {
                if (!isCurrentSearchToken(searchToken)) return;
                if (isSearchPending(sourceKey, searchToken) && releaseSearchSlot(sourceKey, searchToken)) {
                    startNextSearchTask(searchToken);
                }
            }
        }, SEARCH_NEXT_BATCH_SECONDS, TimeUnit.SECONDS);
    }

    private void scheduleSearchTimeout(final String sourceKey, final String searchToken) {
        if (searchTimeoutExecutor == null) return;
        searchTimeoutExecutor.schedule(new Runnable() {
            @Override
            public void run() {
                if (!isCurrentSearchToken(searchToken)) return;
                if (markSearchFinished(sourceKey, searchToken)) {
                    releaseSearchSlotAndStartNext(sourceKey, searchToken);
                    runOnUiThread(new Runnable() {
                        @Override
                        public void run() {
                            finishSearchIfDone();
                        }
                    });
                }
            }
        }, SEARCH_SITE_TIMEOUT_SECONDS, TimeUnit.SECONDS);
    }

    private boolean submitSearchTask(SearchTask task) {
        if (!isSearchPending(task.sourceKey, task.searchToken)) return false;
        if (searchExecutorService == null || searchExecutorService.isShutdown()) return false;
        try {
            searchExecutorService.execute(task);
        } catch (RejectedExecutionException e) {
            return false;
        }
        scheduleSearchAdvance(task.sourceKey, task.searchToken);
        scheduleSearchTimeout(task.sourceKey, task.searchToken);
        return true;
    }

    private ExecutorService createSearchExecutor() {
        return new ThreadPoolExecutor(8, SEARCH_MAX_THREAD_COUNT, 30L, TimeUnit.SECONDS, new java.util.concurrent.LinkedBlockingQueue<Runnable>());
    }

    private void startNextSearchBatch(String searchToken) {
        for (int i = 0; i < SEARCH_THREAD_COUNT; i++) {
            if (!startNextSearchTask(searchToken)) {
                return;
            }
        }
    }

    private boolean startNextSearchTask(String searchToken) {
        if (!isCurrentSearchToken(searchToken)) return false;
        SearchTask task = takeNextSearchTask(searchToken);
        if (task == null) {
            return false;
        }
        if (!submitSearchTask(task)) {
            startedSearchKeys.remove(task.sourceKey);
            synchronized (waitingSearchTasks) {
                waitingSearchTasks.add(0, task);
            }
            return false;
        }
        return true;
    }

    private SearchTask takeNextSearchTask(String searchToken) {
        synchronized (waitingSearchTasks) {
            while (!waitingSearchTasks.isEmpty()) {
                SearchTask task = waitingSearchTasks.remove(0);
                if (!isSearchPending(task.sourceKey, searchToken) || !startedSearchKeys.add(task.sourceKey)) {
                    continue;
                }
                return task;
            }
        }
        return null;
    }

    private void resumePausedSearches() {
        if (!searchPaused) {
            return;
        }
        searchPaused = false;
        List<String> sourceKeys = getPendingSearchKeys();
        if (sourceKeys.isEmpty()) {
            finishSearchIfDone();
            return;
        }
        currentSearchToken = String.valueOf(searchTokenSeq.incrementAndGet());
        waitingSearchTasks.clear();
        startedSearchKeys.clear();
        releasedSearchKeys.clear();
        for (String sourceKey : sourceKeys) {
            SourceBean bean = ApiConfig.get().getSource(sourceKey);
            waitingSearchTasks.add(new SearchTask(sourceKey, searchTitle, currentSearchToken, isBlockingSearchSource(bean)));
        }
        if (searchExecutorService == null || searchExecutorService.isShutdown()) {
            searchExecutorService = createSearchExecutor();
        }
        if (searchTimeoutExecutor == null || searchTimeoutExecutor.isShutdown()) {
            searchTimeoutExecutor = Executors.newSingleThreadScheduledExecutor();
        }
        startNextSearchBatch(currentSearchToken);
    }

    private void pauseSearchTasks() {
        try {
            if (searchExecutorService != null) {
                searchExecutorService.shutdownNow();
                searchExecutorService = null;
                JsLoader.stopAll();
            }
            if (searchTimeoutExecutor != null) {
                searchTimeoutExecutor.shutdownNow();
                searchTimeoutExecutor = null;
            }
            searchPaused = allRunCount.get() > 0;
            if (searchPaused) {
                cancel();
                currentSearchToken = "";
            }
        } catch (Throwable th) {
            th.printStackTrace();
        }
    }

    private boolean isCurrentSearchResult(AbsXml absXml) {
        return absXml != null && isCurrentSearchToken(absXml.searchToken);
    }

    private boolean isCurrentSearchToken(String searchToken) {
        return !TextUtils.isEmpty(searchToken) && searchToken.equals(currentSearchToken);
    }

    private boolean markSearchFinished(String sourceKey, String searchToken) {
        if (!isCurrentSearchToken(searchToken)) return false;
        synchronized (pendingSearchKeys) {
            if (TextUtils.isEmpty(sourceKey)) {
                return false;
            }
            if (!pendingSearchKeys.remove(sourceKey)) {
                return false;
            }
            allRunCount.set(pendingSearchKeys.size());
            return true;
        }
    }

    private boolean releaseSearchSlot(String sourceKey, String searchToken) {
        if (!isCurrentSearchToken(searchToken) || TextUtils.isEmpty(sourceKey)) return false;
        return releasedSearchKeys.add(sourceKey);
    }

    private void releaseSearchSlotAndStartNext(String sourceKey, String searchToken) {
        if (releaseSearchSlot(sourceKey, searchToken)) {
            startNextSearchTask(searchToken);
        }
    }

    private boolean isSearchPending(String sourceKey, String searchToken) {
        if (!isCurrentSearchToken(searchToken) || TextUtils.isEmpty(sourceKey)) return false;
        synchronized (pendingSearchKeys) {
            return pendingSearchKeys.contains(sourceKey);
        }
    }

    private boolean isBlockingSearchSource(SourceBean bean) {
        return bean == null || bean.getType() == 3;
    }

    private void startFastSearchTasks(List<SearchTask> tasks) {
        for (SearchTask task : tasks) {
            if (task.blocking) {
                continue;
            }
            if (startedSearchKeys.add(task.sourceKey)) {
                submitDirectSearchTask(task);
            }
        }
    }

    private void submitDirectSearchTask(SearchTask task) {
        if (!isSearchPending(task.sourceKey, task.searchToken)) return;
        scheduleSearchTimeout(task.sourceKey, task.searchToken);
        try {
            sourceViewModel.getSearch(task.sourceKey, task.title, task.searchToken);
        } catch (Throwable th) {
            th.printStackTrace();
            if (markSearchFinished(task.sourceKey, task.searchToken)) {
                finishSearchIfDone();
            }
        }
    }

    private List<String> getPendingSearchKeys() {
        synchronized (pendingSearchKeys) {
            return new ArrayList<>(pendingSearchKeys);
        }
    }

    private void finishSearchIfDone() {
        if (allRunCount.get() > 0) return;
        searchPaused = false;
        if (allSearchResults.isEmpty()) {
            showEmpty();
        }
        cancel();
        if (searchTimeoutExecutor != null) {
            searchTimeoutExecutor.shutdownNow();
            searchTimeoutExecutor = null;
        }
    }

    private class SearchTask implements Runnable {
        private final String sourceKey;
        private final String title;
        private final String searchToken;
        private final boolean blocking;

        private SearchTask(String sourceKey, String title, String searchToken, boolean blocking) {
            this.sourceKey = sourceKey;
            this.title = title;
            this.searchToken = searchToken;
            this.blocking = blocking;
        }

        @Override
        public void run() {
            if (!isSearchPending(sourceKey, searchToken)) return;
            try {
                sourceViewModel.getSearch(sourceKey, title, searchToken);
            } catch (Throwable th) {
                th.printStackTrace();
                if (markSearchFinished(sourceKey, searchToken)) {
                    runOnUiThread(new Runnable() {
                        @Override
                        public void run() {
                            finishSearchIfDone();
                        }
                    });
                }
            }
        }
    }

    private void cancel() {
        com.lzy.okgo.OkGo.getInstance().cancelTag("search");
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        cancel();
        try {
            if (searchExecutorService != null) {
                searchExecutorService.shutdownNow();
                searchExecutorService = null;
                JsLoader.stopAll();
            }
            if (searchTimeoutExecutor != null) {
                searchTimeoutExecutor.shutdownNow();
                searchTimeoutExecutor = null;
            }
        } catch (Throwable th) {
            th.printStackTrace();
        }
        EventBus.getDefault().unregister(this);
    }

    private void hiddenImm() {
        InputMethodManager imm = (InputMethodManager) mContext.getSystemService(Context.INPUT_METHOD_SERVICE);
        if (imm != null) {
            imm.hideSoftInputFromWindow(etSearch.getWindowToken(), 0);
        }
    }
}
