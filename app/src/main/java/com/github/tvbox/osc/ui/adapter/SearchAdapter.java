package com.github.tvbox.osc.ui.adapter;

import android.text.TextUtils;
import android.widget.ImageView;

import com.chad.library.adapter.base.BaseQuickAdapter;
import com.chad.library.adapter.base.BaseViewHolder;
import com.github.tvbox.osc.R;
import com.github.tvbox.osc.api.ApiConfig;
import com.github.tvbox.osc.bean.Movie;
import com.github.tvbox.osc.util.ImgUtil;

import java.util.ArrayList;

import me.jessyan.autosize.utils.AutoSizeUtils;

/**
 * 搜索结果：统一使用海报缩略图布局（左侧站点标签 + 右侧 2 列海报）
 */
public class SearchAdapter extends BaseQuickAdapter<Movie.Video, BaseViewHolder> {
    private static final int SEARCH_POSTER_WIDTH = 240;
    private static final int SEARCH_POSTER_HEIGHT = 300;

    public SearchAdapter() {
        super(R.layout.item_search_normal, new ArrayList<>());
    }

    @Override
    protected void convert(BaseViewHolder helper, Movie.Video item) {
        helper.setText(R.id.tvName, item.name);
        String siteName = "";
        try {
            if (ApiConfig.get() != null && !TextUtils.isEmpty(item.sourceKey)) {
                siteName = ApiConfig.get().getSource(item.sourceKey).getName();
            }
        } catch (Throwable ignored) {
        }
        helper.setText(R.id.tvSite, siteName == null ? "" : siteName);
        helper.setVisible(R.id.tvNote, item.note != null && !item.note.isEmpty());
        if (item.note != null && !item.note.isEmpty()) helper.setText(R.id.tvNote, item.note);
        ImageView ivThumb = helper.getView(R.id.ivThumb);
        if (!TextUtils.isEmpty(item.pic)) {
            ImgUtil.load(item.pic, ivThumb, AutoSizeUtils.mm2px(mContext, 10), AutoSizeUtils.mm2px(mContext, SEARCH_POSTER_WIDTH), AutoSizeUtils.mm2px(mContext, SEARCH_POSTER_HEIGHT), item.name);
        } else {
            ivThumb.setImageDrawable(ImgUtil.createTextDrawable(item.name));
        }
    }
}
