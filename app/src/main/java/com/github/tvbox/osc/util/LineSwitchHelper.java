package com.github.tvbox.osc.util;

import android.text.TextUtils;

import com.github.tvbox.osc.bean.VodInfo;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 播放线路切换辅助（从 PlayFragment 抽出纯逻辑）
 */
public final class LineSwitchHelper {

    private LineSwitchHelper() {
    }

    /** 按展示顺序收集线路 flag 列表 */
    public static List<String> getLineFlagsInDisplayOrder(VodInfo vodInfo) {
        List<String> lineFlags = new ArrayList<>();
        if (vodInfo == null || vodInfo.seriesMap == null) {
            return lineFlags;
        }
        if (vodInfo.seriesFlags != null) {
            for (VodInfo.VodSeriesFlag flag : vodInfo.seriesFlags) {
                if (flag != null && !TextUtils.isEmpty(flag.name)
                        && vodInfo.seriesMap.containsKey(flag.name)
                        && !lineFlags.contains(flag.name)) {
                    lineFlags.add(flag.name);
                }
            }
        }
        for (String flag : vodInfo.seriesMap.keySet()) {
            if (!TextUtils.isEmpty(flag) && !lineFlags.contains(flag)) {
                lineFlags.add(flag);
            }
        }
        return lineFlags;
    }

    public static int findLineFlagIndex(List<String> lineFlags, String currentFlag) {
        if (lineFlags == null || TextUtils.isEmpty(currentFlag)) {
            return -1;
        }
        for (int i = 0; i < lineFlags.size(); i++) {
            if (currentFlag.equals(lineFlags.get(i))) {
                return i;
            }
        }
        return -1;
    }

    public static VodInfo.VodSeries getSeries(VodInfo vodInfo, String flag, int index) {
        if (flag == null || vodInfo == null || vodInfo.seriesMap == null) {
            return null;
        }
        List<VodInfo.VodSeries> currentList = vodInfo.seriesMap.get(flag);
        if (currentList == null || currentList.isEmpty()) {
            return null;
        }
        int safeIndex = Math.max(0, Math.min(index, currentList.size() - 1));
        return currentList.get(safeIndex);
    }

    /**
     * 从当前线路之后查找下一条未尝试且有剧集的线路。
     * @return [nextFlag, nextIndex] 字符串数组；找不到返回 null
     */
    public static String[] findNextLine(VodInfo vodInfo, String currentFlag, int currentIndex,
                                       Set<String> triedLineFlags) {
        if (vodInfo == null || vodInfo.seriesMap == null || vodInfo.seriesMap.isEmpty()) {
            return null;
        }
        VodInfo.VodSeries currentSeries = getSeries(vodInfo, currentFlag, currentIndex);
        List<String> lineFlags = getLineFlagsInDisplayOrder(vodInfo);
        int currentLineIndex = findLineFlagIndex(lineFlags, currentFlag);
        int startLineIndex = currentLineIndex >= 0 ? currentLineIndex + 1 : 0;
        for (int i = startLineIndex; i < lineFlags.size(); i++) {
            String flag = lineFlags.get(i);
            List<VodInfo.VodSeries> seriesList = vodInfo.seriesMap.get(flag);
            if (triedLineFlags != null && triedLineFlags.contains(flag)) {
                continue;
            }
            if (seriesList != null && !seriesList.isEmpty()) {
                int nextIndex = EpisodeMatchHelper.findSameEpisodeIndex(currentSeries, seriesList, currentIndex);
                return new String[]{flag, String.valueOf(nextIndex)};
            }
        }
        return null;
    }
}
