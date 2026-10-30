package com.github.tvbox.osc.ui.dialog;

import android.content.Context;
import android.os.Bundle;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.DiffUtil;

import com.github.tvbox.osc.R;
import com.github.tvbox.osc.ui.adapter.SelectDialogAdapter;
import com.owen.tvrecyclerview.widget.TvRecyclerView;

import org.jetbrains.annotations.NotNull;

import java.util.List;

public class SelectDialog<T> extends BaseDialog {
    public SelectDialog(@NonNull @NotNull Context context) {
        super(context);
        setContentView(R.layout.dialog_select);
    }

    public SelectDialog(@NonNull @NotNull Context context, int resId) {
        super(context);
        setContentView(resId);
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
    }

    public void setTip(String tip) {
        ((TextView) findViewById(R.id.title)).setText(tip);
    }

    public void setAdapter(SelectDialogAdapter.SelectDialogInterface<T> sourceBeanSelectDialogInterface,
                           DiffUtil.ItemCallback<T> sourceBeanItemCallback,
                           List<T> data, int select) {
        final int selectIdx = Math.max(0, select);
        SelectDialogAdapter<T> adapter = new SelectDialogAdapter<>(sourceBeanSelectDialogInterface, sourceBeanItemCallback);
        adapter.setData(data, selectIdx);
        final TvRecyclerView tvRecyclerView = findViewById(R.id.list);
        tvRecyclerView.setAdapter(adapter);
        tvRecyclerView.setSelectedPosition(selectIdx);
        tvRecyclerView.setSelection(selectIdx);
        tvRecyclerView.post(new Runnable() {
            @Override
            public void run() {
                tvRecyclerView.scrollToPosition(selectIdx);
                tvRecyclerView.setSelection(selectIdx);
                try {
                    tvRecyclerView.smoothScrollToPosition(selectIdx);
                } catch (Throwable ignored) {
                }
            }
        });
        // 再次延迟，确保网格布局测量完成后再定位
        tvRecyclerView.postDelayed(new Runnable() {
            @Override
            public void run() {
                tvRecyclerView.scrollToPosition(selectIdx);
                tvRecyclerView.setSelection(selectIdx);
            }
        }, 120);
    }
}
