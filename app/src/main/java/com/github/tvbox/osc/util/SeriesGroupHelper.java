package com.github.tvbox.osc.util;

import java.util.ArrayList;
import java.util.List;

/**
 * 剧集分组选项生成（从 DetailActivity 抽出）
 * 将「第 1-20 集 / 21-40 集」这类标签计算与 UI 解耦。
 */
public final class SeriesGroupHelper {

    private SeriesGroupHelper() {
    }

    /**
     * 根据列表长度计算推荐分组大小。
     */
    public static int resolveGroupCount(int listSize, int spanCount) {
        int groupCount = (spanCount == 3 || spanCount == 6) ? 30 : 20;
        if (listSize > 100 && listSize <= 400) {
            groupCount = 60;
        }
        if (listSize > 400) {
            groupCount = 120;
        }
        return groupCount;
    }

    /**
     * 生成分组标签列表，例如 "1 - 20", "21 - 40"。
     *
     * @param listSize     总集数
     * @param groupCount   每组数量
     * @param reverseSort  是否倒序
     */
    public static List<String> buildOptions(int listSize, int groupCount, boolean reverseSort) {
        ArrayList<String> options = new ArrayList<>();
        if (listSize <= 1 || groupCount <= 0) {
            return options;
        }
        int remainedOptionSize = listSize % groupCount;
        int optionSize = listSize / groupCount;

        for (int i = 0; i < optionSize; i++) {
            if (reverseSort) {
                options.add(String.format("%d - %d",
                        listSize - (i * groupCount + 1) + 1,
                        listSize - (i * groupCount + groupCount) + 1));
            } else {
                options.add(String.format("%d - %d",
                        i * groupCount + 1,
                        i * groupCount + groupCount));
            }
        }
        if (remainedOptionSize > 0) {
            if (reverseSort) {
                options.add(String.format("%d - %d",
                        listSize - (optionSize * groupCount + 1) + 1,
                        listSize - (optionSize * groupCount + remainedOptionSize) + 1));
            } else {
                options.add(String.format("%d - %d",
                        optionSize * groupCount + 1,
                        optionSize * groupCount + remainedOptionSize));
            }
        }
        return options;
    }

    /**
     * 根据当前播放集索引计算应选中的分组位置。
     */
    public static int resolveSelectedGroupPosition(int playIndex, int groupCount, int optionSize) {
        if (groupCount <= 0 || optionSize <= 0) {
            return 0;
        }
        return Math.max(0, Math.min(playIndex / groupCount, optionSize - 1));
    }
}
