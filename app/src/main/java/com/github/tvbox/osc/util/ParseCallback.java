package com.github.tvbox.osc.util;

import java.util.HashMap;

/**
 * 解析结果回调（供 PlayFragment / 未来独立 Parser 使用）
 */
public interface ParseCallback {

    /** 直接播放 */
    void onPlay(String url, HashMap<String, String> headers);

    /** 需要 WebView 二次嗅探 */
    void onWebView(String url, HashMap<String, String> headers);

    /** 解析失败 */
    void onError(String message);

    /** 可选：解析来源提示 */
    default void onJxFrom(String from) {
    }
}
