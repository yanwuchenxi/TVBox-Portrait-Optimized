package com.github.tvbox.osc.ui.adapter;

import android.widget.TextView;

import com.chad.library.adapter.base.BaseQuickAdapter;
import com.chad.library.adapter.base.BaseViewHolder;
import com.github.tvbox.osc.R;
import com.github.tvbox.osc.bean.MovieSort;

import java.util.ArrayList;

public class SortAdapter extends BaseQuickAdapter<MovieSort.SortData, BaseViewHolder> {
    private int selectedPosition = 0;

    public SortAdapter() {
        super(R.layout.item_home_sort, new ArrayList<>());
    }

    public void setSelectedPosition(int position) {
        int old = selectedPosition;
        selectedPosition = position;
        if (old >= 0 && old < getItemCount()) notifyItemChanged(old);
        if (selectedPosition >= 0 && selectedPosition < getItemCount()) notifyItemChanged(selectedPosition);
    }

    public int getSelectedPosition() {
        return selectedPosition;
    }

    @Override
    protected void convert(BaseViewHolder helper, MovieSort.SortData item) {
        helper.setText(R.id.tvTitle, item.name);
        boolean selected = helper.getAdapterPosition() == selectedPosition;
        helper.itemView.setSelected(selected);
        TextView tv = helper.getView(R.id.tvTitle);
        if (tv != null) {
            tv.setTextColor(selected
                    ? helper.itemView.getResources().getColor(R.color.color_FFFFFF)
                    : helper.itemView.getResources().getColor(R.color.color_BBFFFFFF));
        }
    }
}
