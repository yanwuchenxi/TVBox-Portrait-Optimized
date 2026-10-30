package com.github.tvbox.osc.ui.adapter;

import android.text.TextUtils;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;

import com.chad.library.adapter.base.BaseQuickAdapter;
import com.chad.library.adapter.base.BaseViewHolder;
import com.github.tvbox.osc.R;
import com.github.tvbox.osc.api.ApiConfig;
import com.github.tvbox.osc.bean.Movie;
import com.github.tvbox.osc.util.HawkConfig;
import com.github.tvbox.osc.util.ImgUtil;
import com.orhanobut.hawk.Hawk;

import java.util.ArrayList;

import me.jessyan.autosize.utils.AutoSizeUtils;

/**
 * 搜索结果：SEARCH_VIEW=0 文字列表，SEARCH_VIEW=1 海报缩略图
 */
public class SearchAdapter extends BaseQuickAdapter<Movie.Video, BaseViewHolder> {
    private static final int SEARCH_POSTER_WIDTH = 240;
    private static final int SEARCH_POSTER_HEIGHT = 300;
    private final boolean withPoster;

    public SearchAdapter() {
        super(Hawk.get(HawkConfig.SEARCH_VIEW, 0) == 1 ? R.layout.item_search_normal : R.layout.item_search_lite, new ArrayList<>());
        withPoster = Hawk.get(HawkConfig.SEARCH_VIEW, 0) == 1;
    }

    public boolean isWithPoster() {
        return withPoster;
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
        if (helper.getView(R.id.tvSite) != null) {
            helper.setText(R.id.tvSite, siteName == null ? "" : siteName);
        }
        if (helper.getView(R.id.tvNote) != null) {
            helper.setVisible(R.id.tvNote, item.note != null && !item.note.isEmpty());
            if (item.note != null && !item.note.isEmpty()) helper.setText(R.id.tvNote, item.note);
        }
        ImageView ivThumb = null;
        try {
            ivThumb = helper.getView(R.id.ivThumb);
        } catch (Throwable ignored) {
        }
        if (ivThumb != null) {
            if (!TextUtils.isEmpty(item.pic)) {
                ImgUtil.load(item.pic, ivThumb, AutoSizeUtils.mm2px(mContext, 10),
                        AutoSizeUtils.mm2px(mContext, SEARCH_POSTER_WIDTH),
                        AutoSizeUtils.mm2px(mContext, SEARCH_POSTER_HEIGHT), item.name);
            } else {
                ivThumb.setImageDrawable(ImgUtil.createTextDrawable(item.name));
            }
        }
        // 列表模式：把站点信息拼到名称旁
        if (!withPoster) {
            TextView tvName = helper.getView(R.id.tvName);
            if (tvName != null && !TextUtils.isEmpty(siteName)) {
                helper.setText(R.id.tvName, item.name + "  [" + siteName + "]");
            }
        }
    }
}
