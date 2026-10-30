package com.github.tvbox.osc.ui.activity;

import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import android.view.animation.BounceInterpolator;
import android.widget.ImageView;
import android.widget.TextView;

import com.chad.library.adapter.base.BaseQuickAdapter;
import com.github.tvbox.osc.R;
import com.github.tvbox.osc.api.ApiConfig;
import com.github.tvbox.osc.base.BaseActivity;
import com.github.tvbox.osc.bean.SourceBean;
import com.github.tvbox.osc.bean.VodInfo;
import com.github.tvbox.osc.cache.RoomDataManger;
import com.github.tvbox.osc.cache.VodCollect;
import com.github.tvbox.osc.event.RefreshEvent;
import com.github.tvbox.osc.ui.adapter.CollectAdapter;
import com.github.tvbox.osc.ui.adapter.HistoryAdapter;
import com.github.tvbox.osc.ui.dialog.ConfirmClearDialog;
import com.github.tvbox.osc.util.BottomNavHelper;
import com.github.tvbox.osc.util.FastClickCheckUtil;
import com.github.tvbox.osc.util.HawkConfig;
import com.orhanobut.hawk.Hawk;
import com.owen.tvrecyclerview.widget.TvRecyclerView;
import com.owen.tvrecyclerview.widget.V7GridLayoutManager;

import org.greenrobot.eventbus.EventBus;
import org.greenrobot.eventbus.Subscribe;
import org.greenrobot.eventbus.ThreadMode;

import java.util.ArrayList;
import java.util.List;

/**
 * 历史记录与收藏合并页面（双Tab切换）
 */
public class HistoryActivity extends BaseActivity {
    private ImageView tvDelete;
    private ImageView tvClear;
    private TextView tvDelTip;
    private TextView tvTabHistory;
    private TextView tvTabCollect;
    private TvRecyclerView mGridView;
    public static HistoryAdapter historyAdapter;
    private boolean delMode = false;
    private boolean isCollectTab = false;

    @Override
    protected int getLayoutResID() {
        return R.layout.activity_history;
    }

    @Override
    protected boolean shouldRefreshAutoSize() {
        return true;
    }

    @Override
    protected boolean useImmersive() {
        return false;
    }

    @Override
    protected void init() {
        // 已迁移到 MainActivity + Fragment，旧入口导流
        android.content.Intent main = new android.content.Intent(this, MainActivity.class);
        main.putExtra(MainActivity.EXTRA_TAB, 2);
        main.addFlags(android.content.Intent.FLAG_ACTIVITY_CLEAR_TOP | android.content.Intent.FLAG_ACTIVITY_SINGLE_TOP);
        startActivity(main);
        finish();
        return;
        // dead code below kept for compile reference

        initView();
        Intent intent = getIntent();
        if (intent != null && intent.getBooleanExtra("collect", false)) {
            isCollectTab = true;
            tvTabHistory.setTextColor(getResources().getColor(R.color.color_BBFFFFFF));
            tvTabCollect.setTextColor(getResources().getColor(R.color.color_FFFFFF));
            tvDelTip.setText("确定键删除当前选中收藏");
            mGridView.setAdapter(CollectActivity.collectAdapter);
        }
        initData();
    }

    private void toggleDelMode() {
        HawkConfig.hotVodDelete = !HawkConfig.hotVodDelete;
        if (isCollectTab) {
            if (CollectActivity.collectAdapter != null) CollectActivity.collectAdapter.notifyDataSetChanged();
        } else {
            historyAdapter.notifyDataSetChanged();
        }
        delMode = !delMode;
        tvDelTip.setVisibility(delMode ? View.VISIBLE : View.GONE);
    }

    private void initView() {
        EventBus.getDefault().register(this);
        BottomNavHelper.setup(this, R.id.nav_history);
        tvDelete = findViewById(R.id.tvDelete);
        tvClear = findViewById(R.id.tvClear);
        tvDelTip = findViewById(R.id.tvDelTip);
        tvTabHistory = findViewById(R.id.tvTabHistory);
        tvTabCollect = findViewById(R.id.tvTabCollect);
        mGridView = findViewById(R.id.mGridView);
        mGridView.setHasFixedSize(true);
        mGridView.setLayoutManager(new V7GridLayoutManager(this.mContext, isPortrait() ? 3 : (isBaseOnWidth() ? 5 : 6)));
        historyAdapter = new HistoryAdapter();
        if (CollectActivity.collectAdapter == null) {
            CollectActivity.collectAdapter = new CollectAdapter();
        }
        mGridView.setAdapter(historyAdapter);

        // 历史记录点击
        historyAdapter.setOnItemClickListener(new BaseQuickAdapter.OnItemClickListener() {
            @Override
            public void onItemClick(BaseQuickAdapter adapter, View view, int position) {
                FastClickCheckUtil.check(view);
                if (position == -1) return;
                VodInfo vodInfo = historyAdapter.getData().get(position);
                if (vodInfo != null) {
                    if (delMode) {
                        historyAdapter.remove(position);
                        RoomDataManger.deleteVodRecord(vodInfo.sourceKey, vodInfo);
                    } else {
                        Bundle bundle = new Bundle();
                        bundle.putString("id", vodInfo.id);
                        bundle.putString("sourceKey", vodInfo.sourceKey);
                        SourceBean sourceBean = ApiConfig.get().getSource(vodInfo.sourceKey);
                        if (sourceBean != null) {
                            bundle.putString("title", vodInfo.name);
                            bundle.putString("picture", vodInfo.pic);
                            jumpActivity(DetailActivity.class, bundle);
                        } else {
                            bundle.putString("title", vodInfo.name);
                            if (Hawk.get(HawkConfig.FAST_SEARCH_MODE, true)) {
                                jumpActivity(FastSearchActivity.class, bundle);
                            } else {
                                jumpActivity(SearchActivity.class, bundle);
                            }
                        }
                    }
                }
            }
        });
        historyAdapter.setOnItemLongClickListener(new BaseQuickAdapter.OnItemLongClickListener() {
            @Override
            public boolean onItemLongClick(BaseQuickAdapter adapter, View view, int position) {
                VodInfo vodInfo = historyAdapter.getData().get(position);
                if (vodInfo != null) {
                    Bundle bundle = new Bundle();
                    bundle.putString("title", vodInfo.name);
                    if (Hawk.get(HawkConfig.FAST_SEARCH_MODE, true)) {
                        jumpActivity(FastSearchActivity.class, bundle);
                    } else {
                        jumpActivity(SearchActivity.class, bundle);
                    }
                }
                return true;
            }
        });

        // 收藏点击
        CollectActivity.collectAdapter.setOnItemClickListener(new BaseQuickAdapter.OnItemClickListener() {
            @Override
            public void onItemClick(BaseQuickAdapter adapter, View view, int position) {
                FastClickCheckUtil.check(view);
                VodCollect vodInfo = CollectActivity.collectAdapter.getData().get(position);
                if (vodInfo != null) {
                    if (delMode) {
                        CollectActivity.collectAdapter.remove(position);
                        RoomDataManger.deleteVodCollect(vodInfo.getId());
                    } else {
                        if (ApiConfig.get().getSource(vodInfo.sourceKey) != null) {
                            Bundle bundle = new Bundle();
                            bundle.putString("id", vodInfo.vodId);
                            bundle.putString("sourceKey", vodInfo.sourceKey);
                            bundle.putString("title", vodInfo.name);
                            bundle.putString("picture", vodInfo.pic);
                            bundle.putBoolean("collect", true);
                            jumpActivity(DetailActivity.class, bundle);
                        } else {
                            Intent newIntent = new Intent(mContext, SearchActivity.class);
                            newIntent.putExtra("title", vodInfo.name);
                            newIntent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP);
                            startActivity(newIntent);
                        }
                    }
                }
            }
        });
        CollectActivity.collectAdapter.setOnItemLongClickListener(new BaseQuickAdapter.OnItemLongClickListener() {
            @Override
            public boolean onItemLongClick(BaseQuickAdapter adapter, View view, int position) {
                VodCollect vodInfo = CollectActivity.collectAdapter.getData().get(position);
                if (vodInfo != null) {
                    Bundle bundle = new Bundle();
                    bundle.putString("title", vodInfo.name);
                    if (Hawk.get(HawkConfig.FAST_SEARCH_MODE, true)) {
                        jumpActivity(FastSearchActivity.class, bundle);
                    } else {
                        jumpActivity(SearchActivity.class, bundle);
                    }
                }
                return true;
            }
        });

        tvDelete.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                toggleDelMode();
            }
        });
        tvClear.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                ConfirmClearDialog dialog = new ConfirmClearDialog(mContext, isCollectTab ? "Collect" : "History");
                dialog.show();
            }
        });
        mGridView.setOnInBorderKeyEventListener(new TvRecyclerView.OnInBorderKeyEventListener() {
            @Override
            public boolean onInBorderKeyEvent(int direction, View focused) {
                if (direction == View.FOCUS_UP) {
                    tvDelete.setFocusable(true);
                    tvClear.setFocusable(true);
                    tvDelete.requestFocus();
                }
                return false;
            }
        });
        mGridView.setOnItemListener(new TvRecyclerView.OnItemListener() {
            @Override
            public void onItemPreSelected(TvRecyclerView parent, View itemView, int position) {
                itemView.animate().scaleX(1.0f).scaleY(1.0f).setDuration(300).setInterpolator(new BounceInterpolator()).start();
            }

            @Override
            public void onItemSelected(TvRecyclerView parent, View itemView, int position) {
                itemView.animate().scaleX(1.05f).scaleY(1.05f).setDuration(300).setInterpolator(new BounceInterpolator()).start();
            }

            @Override
            public void onItemClick(TvRecyclerView parent, View itemView, int position) {

            }
        });

        // Tab 切换
        tvTabHistory.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                switchTab(false);
            }
        });
        tvTabCollect.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                switchTab(true);
            }
        });
    }

    private void switchTab(boolean collect) {
        if (isCollectTab == collect) return;
        isCollectTab = collect;
        delMode = false;
        HawkConfig.hotVodDelete = false;
        tvDelTip.setVisibility(View.GONE);
        if (collect) {
            tvTabHistory.setTextColor(getResources().getColor(R.color.color_BBFFFFFF));
            tvTabCollect.setTextColor(getResources().getColor(R.color.color_FFFFFF));
            tvDelTip.setText("确定键删除当前选中收藏");
            mGridView.setAdapter(CollectActivity.collectAdapter);
        } else {
            tvTabHistory.setTextColor(getResources().getColor(R.color.color_FFFFFF));
            tvTabCollect.setTextColor(getResources().getColor(R.color.color_BBFFFFFF));
            tvDelTip.setText("确定键删除当前选中记录");
            mGridView.setAdapter(historyAdapter);
        }
        initData();
    }

    private void initData() {
        if (isCollectTab) {
            List<VodCollect> allVodRecord = RoomDataManger.getAllVodCollect();
            List<VodCollect> vodInfoList = new ArrayList<>();
            for (VodCollect vodInfo : allVodRecord) {
                vodInfoList.add(vodInfo);
            }
            CollectActivity.collectAdapter.setNewData(vodInfoList);
        } else {
            List<VodInfo> allVodRecord = RoomDataManger.getAllVodRecord(100);
            List<VodInfo> vodInfoList = new ArrayList<>();
            for (VodInfo vodInfo : allVodRecord) {
                if (vodInfo.playNote != null && !vodInfo.playNote.isEmpty())
                    vodInfo.note = "上次看到" + vodInfo.playNote;
                vodInfoList.add(vodInfo);
            }
            historyAdapter.setNewData(vodInfoList);
        }
    }

    @Subscribe(threadMode = ThreadMode.MAIN)
    public void refresh(RefreshEvent event) {
        if (event.type == RefreshEvent.TYPE_HISTORY_REFRESH) {
            initData();
        }
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        EventBus.getDefault().unregister(this);
    }

    @Override
    public void onBackPressed() {
        if (delMode) {
            toggleDelMode();
            return;
        }
        // 底部导航页面：返回直接回首页，而不是按切换记录逐步返回
        BottomNavHelper.goHome(this);
    }
}
