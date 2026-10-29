package com.github.tvbox.osc.util;

import android.os.Build;
import android.text.Html;
import android.text.TextUtils;

/**
 * HTML / 富文本清理（从 DetailActivity 抽出）
 */
public final class HtmlTextHelper {

    private HtmlTextHelper() {
    }

    /**
     * 去除常见 HTML 与站点自定义标签，返回纯文本。
     */
    public static String removeHtmlTag(String info) {
        if (TextUtils.isEmpty(info)) {
            return "";
        }
        String text = info.replaceAll("\\[a=cr:(?:\\{.*?\\}|\\[.*?\\])\\/](.*?)\\[\\/a]", "$1");
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            text = Html.fromHtml(text, Html.FROM_HTML_MODE_LEGACY).toString();
        } else {
            text = Html.fromHtml(text).toString();
        }
        return text.replaceAll("\\s", "");
    }
}
