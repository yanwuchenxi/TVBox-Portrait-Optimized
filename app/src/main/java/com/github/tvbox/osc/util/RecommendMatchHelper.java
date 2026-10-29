package com.github.tvbox.osc.util;

import android.text.TextUtils;

import com.github.tvbox.osc.bean.Movie;
import com.github.tvbox.osc.bean.MovieSort;

import java.util.ArrayList;
import java.util.List;

/**
 * 详情页「同类推荐」匹配逻辑（从 DetailActivity 抽出）
 */
public final class RecommendMatchHelper {

    private RecommendMatchHelper() {
    }

    /**
     * 根据影片类型名在站点分类列表中匹配分类 id。
     * 顺序：精确相等 → 互相包含 → 第一个非「主页/首页」分类。
     *
     * @return 匹配到的分类 id，失败返回 null
     */
    public static String matchSortId(List<MovieSort.SortData> sortList, String typeName) {
        if (sortList == null || sortList.isEmpty()) {
            return null;
        }
        if (!TextUtils.isEmpty(typeName)) {
            for (MovieSort.SortData data : sortList) {
                if (data == null || TextUtils.isEmpty(data.name)) continue;
                if (data.name.equals(typeName)) {
                    return data.id;
                }
            }
            for (MovieSort.SortData data : sortList) {
                if (data == null || TextUtils.isEmpty(data.name)) continue;
                if (typeName.contains(data.name) || data.name.contains(typeName)) {
                    return data.id;
                }
            }
        }
        for (MovieSort.SortData data : sortList) {
            if (data == null || TextUtils.isEmpty(data.id)) continue;
            if (data.name != null && (data.name.contains("主页") || data.name.contains("首页"))) continue;
            return data.id;
        }
        return null;
    }

    /**
     * 从推荐列表结果中过滤当前影片，最多保留 maxCount 条。
     */
    public static List<Movie.Video> filterRecommendList(List<Movie.Video> videoList, String currentVodId, int maxCount) {
        ArrayList<Movie.Video> recommendList = new ArrayList<>();
        if (videoList == null) {
            return recommendList;
        }
        for (Movie.Video video : videoList) {
            if (video == null || TextUtils.isEmpty(video.id)) continue;
            if (TextUtils.equals(video.id, currentVodId)) continue;
            recommendList.add(video);
            if (recommendList.size() >= maxCount) break;
        }
        return recommendList;
    }
}
