package com.github.tvbox.osc.util;

import android.text.TextUtils;

import org.json.JSONObject;

import java.util.HashMap;

/**
 * JSON 解析结果处理（从 PlayFragment.rsJsonJX / json 扩展结果分支抽出）
 */
public final class ParseResultHelper {

    private ParseResultHelper() {
    }

    public static final class PlayDecision {
        /** 是否需要 WebView 二次嗅探 */
        public boolean needWebView;
        /** 最终播放或嗅探地址 */
        public String url;
        /** 请求头 */
        public HashMap<String, String> headers;
        /** 解析来源提示（可空） */
        public String jxFrom;
        /** 可选 UA（超级解析等） */
        public String userAgent;
        /** 结果是否有效 */
        public boolean valid;
    }

    /**
     * 解析 JSON 扩展/聚合返回的 JSONObject，得到播放决策。
     */
    public static PlayDecision fromJsonResult(JSONObject rs) {
        PlayDecision d = new PlayDecision();
        if (rs == null || !rs.has("url") || TextUtils.isEmpty(rs.optString("url"))) {
            d.valid = false;
            return d;
        }
        d.valid = true;
        d.url = rs.optString("url", "");
        d.headers = ParseHeaderHelper.getHeaders(rs);
        d.jxFrom = rs.has("jxFrom") ? rs.optString("jxFrom") : null;
        d.needWebView = rs.optInt("parse", 0) == 1;
        if (rs.has("ua")) {
            d.userAgent = rs.optString("ua", "").trim();
        }
        return d;
    }

    public static boolean isValidPlayUrl(String url) {
        return !TextUtils.isEmpty(url);
    }
}
