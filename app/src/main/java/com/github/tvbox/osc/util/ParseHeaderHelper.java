package com.github.tvbox.osc.util;

import android.text.TextUtils;

import org.json.JSONException;
import org.json.JSONObject;

import java.util.HashMap;
import java.util.Iterator;
import java.util.Locale;
import java.util.Map;

/**
 * 解析扩展字段中的 Header（从 PlayFragment 抽出）
 */
public final class ParseHeaderHelper {

    private ParseHeaderHelper() {
    }

    public static HashMap<String, String> getHeaders(JSONObject object) {
        if (object == null) return null;
        HashMap<String, String> headers = new HashMap<>();
        appendHeaders(headers, object.opt("header"));
        appendHeaders(headers, object.opt("headers"));
        return headers.isEmpty() ? null : headers;
    }

    public static void appendHeaders(HashMap<String, String> headers, Object rawHeaders) {
        if (headers == null || rawHeaders == null || rawHeaders == JSONObject.NULL) return;
        try {
            JSONObject json = null;
            if (rawHeaders instanceof JSONObject) {
                json = (JSONObject) rawHeaders;
            } else if (rawHeaders instanceof String) {
                String text = ((String) rawHeaders).trim();
                if (!TextUtils.isEmpty(text)) {
                    json = new JSONObject(text);
                }
            }
            if (json == null) return;
            Iterator<String> keys = json.keys();
            while (keys.hasNext()) {
                String key = keys.next();
                if (!TextUtils.isEmpty(key)) {
                    headers.put(key, json.optString(key, ""));
                }
            }
        } catch (Throwable ignored) {
        }
    }

    public static void putHeaders(JSONObject target, HashMap<String, String> headers) throws JSONException {
        if (target == null || headers == null) return;
        for (Map.Entry<String, String> e : headers.entrySet()) {
            target.put(e.getKey(), e.getValue());
        }
    }

    public static String getHeaderValue(HashMap<String, String> headers, String name) {
        if (headers == null || name == null) return null;
        for (String key : headers.keySet()) {
            if (name.equalsIgnoreCase(key)) {
                return headers.get(key);
            }
        }
        return null;
    }

    /**
     * 嗅探解析 ext：拆出 User-Agent 与其它请求头。
     */
    public static class WebExtResult {
        public String userAgent;
        public HashMap<String, String> headers = new HashMap<>();
    }

    public static WebExtResult parseWebExt(String extJson) {
        WebExtResult result = new WebExtResult();
        if (TextUtils.isEmpty(extJson)) {
            return result;
        }
        try {
            JSONObject jsonObject = new JSONObject(extJson);
            HashMap<String, String> headerMap = getHeaders(jsonObject);
            if (headerMap == null) {
                return result;
            }
            for (Map.Entry<String, String> e : headerMap.entrySet()) {
                if ("user-agent".equalsIgnoreCase(e.getKey())) {
                    if (e.getValue() != null) {
                        result.userAgent = e.getValue().trim();
                    }
                } else {
                    result.headers.put(e.getKey(), e.getValue());
                }
            }
        } catch (Throwable e) {
            e.printStackTrace();
        }
        return result;
    }
}
