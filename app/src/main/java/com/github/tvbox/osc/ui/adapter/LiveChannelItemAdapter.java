package com.github.tvbox.osc.ui.adapter;

import android.graphics.Color;
import android.view.View;
import android.widget.TextView;

import com.chad.library.adapter.base.BaseQuickAdapter;
import com.chad.library.adapter.base.BaseViewHolder;
import com.github.tvbox.osc.R;
import com.github.tvbox.osc.bean.LiveChannelItem;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.Set;

/**
 * @author pj567
 * @date :2021/1/12
 * @description:
 */
public class LiveChannelItemAdapter extends BaseQuickAdapter<LiveChannelItem, BaseViewHolder> {
    private int selectedChannelIndex = -1;
    private int focusedChannelIndex = -1;
    private Set<String> favoriteNames = new HashSet<>();

    public LiveChannelItemAdapter() {
        super(R.layout.item_live_channel, new ArrayList<>());
    }

    @Override
    protected void convert(BaseViewHolder holder, LiveChannelItem item) {
        TextView tvChannelNum = holder.getView(R.id.tvChannelNum);
        TextView tvChannel = holder.getView(R.id.tvChannelName);
        tvChannelNum.setText(String.format("%s", item.getChannelNum()));
        tvChannel.setText(item.getChannelName());
        tvChannelNum.setSelected(true);
        tvChannel.setSelected(true);
        int channelIndex = item.getChannelIndex();
        holder.itemView.setSelected(channelIndex == selectedChannelIndex);
        View star = holder.getView(R.id.tvChannelStar);
        if (star != null) {
            star.setVisibility(favoriteNames.contains(item.getChannelName()) ? View.VISIBLE : View.GONE);
        }
        if (channelIndex == selectedChannelIndex) {
            tvChannelNum.setTextColor(0xFFE3A64C);
            tvChannel.setTextColor(0xFFE3A64C);
        }
        else{
            tvChannelNum.setTextColor(0xFFEFF4FB);
            tvChannel.setTextColor(0xFFEFF4FB);
        }
    }

    public void setSelectedChannelIndex(int selectedChannelIndex) {
        if (selectedChannelIndex == this.selectedChannelIndex) return;
        int preSelectedChannelIndex = this.selectedChannelIndex;
        this.selectedChannelIndex = selectedChannelIndex;
        if (preSelectedChannelIndex != -1)
            notifyItemChanged(preSelectedChannelIndex);
        if (this.selectedChannelIndex != -1)
            notifyItemChanged(this.selectedChannelIndex);
    }

    public void setFocusedChannelIndex(int focusedChannelIndex) {
        int preFocusedChannelIndex = this.focusedChannelIndex;
        this.focusedChannelIndex = focusedChannelIndex;
        if (preFocusedChannelIndex != -1)
            notifyItemChanged(preFocusedChannelIndex);
        if (this.focusedChannelIndex != -1)
            notifyItemChanged(this.focusedChannelIndex);
        else if (this.selectedChannelIndex != -1)
            notifyItemChanged(this.selectedChannelIndex);
    }

    public void setFavoriteNames(Set<String> names) {
        Set<String> newNames = names == null ? new HashSet<String>() : new HashSet<>(names);
        if (newNames.equals(favoriteNames)) return;
        favoriteNames = newNames;
        notifyDataSetChanged();
    }
}
