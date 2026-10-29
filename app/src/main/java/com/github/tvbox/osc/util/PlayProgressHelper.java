package com.github.tvbox.osc.util;

import com.github.tvbox.osc.cache.CacheManager;

/**
 * 播放进度读写（从 PlayFragment 抽出纯缓存逻辑）
 */
public final class PlayProgressHelper {

    private PlayProgressHelper() {
    }

    /**
     * 读取已保存进度，并与片头跳过时间取较大值。
     *
     * @param url       播放地址
     * @param skipMs    片头跳过毫秒（通常 st * 1000）
     */
    public static long getSavedProgress(String url, long skipMs) {
        Object theCache = CacheManager.getCache(MD5.string2MD5(url));
        if (theCache == null) {
            return skipMs;
        }
        long rec = 0;
        if (theCache instanceof Long) {
            rec = (Long) theCache;
        } else if (theCache instanceof String) {
            try {
                rec = Long.parseLong((String) theCache);
            } catch (NumberFormatException e) {
                LOG.i("echo-String value is not a valid long.");
            }
        } else {
            LOG.i("echo-Value cannot be converted to long.");
        }
        return Math.max(rec, skipMs);
    }

    public static void saveProgress(String url, long progress) {
        CacheManager.save(MD5.string2MD5(url), progress);
    }
}
