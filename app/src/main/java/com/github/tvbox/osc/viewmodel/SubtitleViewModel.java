package com.github.tvbox.osc.viewmodel;

import android.text.TextUtils;

import androidx.lifecycle.MutableLiveData;
import androidx.lifecycle.ViewModel;

import com.github.tvbox.osc.bean.Subtitle;
import com.github.tvbox.osc.bean.SubtitleData;
import com.github.tvbox.osc.ui.dialog.SearchSubtitleDialog;
import com.lzy.okgo.OkGo;
import com.lzy.okgo.callback.AbsCallback;

import org.json.JSONArray;
import org.json.JSONObject;

import java.net.URLEncoder;
import java.util.ArrayList;
import java.util.List;

import okhttp3.Response;

/**
 * 在线字幕搜索：使用迅雷（迅雷看看）开放的 oracle 字幕接口，
 * 免费、无需账号，直接返回可播放的 srt/ass 直链。
 */
public class SubtitleViewModel extends ViewModel {

    private static final String XUNLEI_SUBTITLE_API = "https://api-shoulei-ssl.xunlei.com/oracle/subtitle";

    public MutableLiveData<SubtitleData> searchResult;

    public SubtitleViewModel() {
        searchResult = new MutableLiveData<>();
    }

    public void searchResult(String title, int page) {
        searchResultFromXunlei(title);
    }

    public void getSearchResultSubtitleUrls(Subtitle subtitle) {
        // 迅雷接口返回的即为单片字幕直链，不存在压缩包，直接下发
        List<Subtitle> data = new ArrayList<>();
        if (subtitle != null && !TextUtils.isEmpty(subtitle.getUrl())) {
            subtitle.setIsZip(false);
            data.add(subtitle);
            setSearchListData(data, true, false);
        } else {
            setSearchListData(null, true, false);
        }
    }

    public void getSubtitleUrl(Subtitle subtitle, SearchSubtitleDialog.SubtitleLoader subtitleLoader) {
        if (subtitle == null || TextUtils.isEmpty(subtitle.getUrl()) || subtitleLoader == null) {
            return;
        }
        subtitle.setIsZip(false);
        subtitleLoader.loadSubtitle(subtitle);
    }

    private void setSearchListData(List<Subtitle> data, boolean isNew, boolean isZip) {
        try {
            SubtitleData subtitleData = new SubtitleData();
            subtitleData.setSubtitleList(data);
            subtitleData.setIsNew(isNew);
            subtitleData.setIsZip(isZip);
            searchResult.postValue(subtitleData);
        } catch (Throwable e) {
            e.printStackTrace();
            searchResult.postValue(null);
        }
    }

    private void searchResultFromXunlei(String title) {
        try {
            if (TextUtils.isEmpty(title)) {
                setSearchListData(new ArrayList<>(), true, false);
                return;
            }
            String keyword = URLEncoder.encode(title.trim(), "UTF-8");
            String searchApiUrl = XUNLEI_SUBTITLE_API + "?name=" + keyword;
            OkGo.<String>get(searchApiUrl)
                    .execute(new AbsCallback<String>() {
                        @Override
                        public void onSuccess(com.lzy.okgo.model.Response<String> response) {
                            try {
                                List<Subtitle> data = parseXunleiResult(response.body());
                                setSearchListData(data, true, false);
                            } catch (Throwable th) {
                                th.printStackTrace();
                                setSearchListData(new ArrayList<>(), true, false);
                            }
                        }

                        @Override
                        public String convertResponse(Response response) throws Throwable {
                            return response.body().string();
                        }

                        @Override
                        public void onError(com.lzy.okgo.model.Response<String> response) {
                            super.onError(response);
                            setSearchListData(null, true, false);
                        }
                    });
        } catch (Exception e) {
            e.printStackTrace();
            setSearchListData(null, true, false);
        }
    }

    private List<Subtitle> parseXunleiResult(String content) throws Exception {
        List<Subtitle> data = new ArrayList<>();
        if (TextUtils.isEmpty(content)) return data;
        JSONObject json = new JSONObject(content);
        if (json.optInt("code", -1) != 0) return data;
        JSONArray array = json.optJSONArray("data");
        if (array == null) return data;
        for (int i = 0; i < array.length(); i++) {
            JSONObject item = array.optJSONObject(i);
            if (item == null) continue;
            String url = item.optString("url", "");
            if (TextUtils.isEmpty(url)) continue;
            String ext = item.optString("ext", "");
            if (!isSupportedSubtitleFile(ext)) continue;
            String displayName = item.optString("simple_name", "");
            if (TextUtils.isEmpty(displayName)) {
                displayName = item.optString("name", "字幕");
            }
            String language = "";
            JSONArray languages = item.optJSONArray("languages");
            if (languages != null && languages.length() > 0) {
                language = languages.optString(0, "");
            }
            StringBuilder name = new StringBuilder();
            if (!TextUtils.isEmpty(language)) {
                name.append("[").append(language).append("] ");
            }
            name.append(displayName);
            String duration = formatDuration(item.optLong("duration", 0));
            if (!TextUtils.isEmpty(duration)) {
                name.append("  ").append(duration);
            }
            Subtitle one = new Subtitle();
            one.setName(name.toString());
            one.setUrl(url);
            one.setIsZip(false);
            data.add(one);
        }
        return data;
    }

    private String formatDuration(long durationMs) {
        if (durationMs <= 0) return "";
        long totalSeconds = durationMs / 1000;
        long hours = totalSeconds / 3600;
        long minutes = (totalSeconds % 3600) / 60;
        if (hours > 0) {
            return String.format(java.util.Locale.ROOT, "%d:%02d:%02d", hours, minutes, totalSeconds % 60);
        }
        return String.format(java.util.Locale.ROOT, "%d:%02d", minutes, totalSeconds % 60);
    }

    private boolean isSupportedSubtitleFile(String fileName) {
        if (TextUtils.isEmpty(fileName)) return false;
        String lower = fileName.toLowerCase(java.util.Locale.ROOT).trim();
        if (lower.startsWith(".")) lower = lower.substring(1);
        return "srt".equals(lower) || "ass".equals(lower) || "ssa".equals(lower)
                || "stl".equals(lower) || "ttml".equals(lower);
    }

}
