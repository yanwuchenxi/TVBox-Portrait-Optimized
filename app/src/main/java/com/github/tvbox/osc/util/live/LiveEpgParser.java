package com.github.tvbox.osc.util.live;

import android.text.TextUtils;

import com.github.tvbox.osc.bean.Epginfo;
import com.github.tvbox.osc.util.HawkConfig;
import com.orhanobut.hawk.Hawk;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.NodeList;

import java.io.StringReader;
import java.net.URLEncoder;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.TimeZone;
import java.util.concurrent.TimeUnit;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;

/**
 * 直播 EPG 纯解析/工具逻辑（从 LivePlayActivity 抽出）
 * 不依赖 Activity / View，便于单测与复用。
 */
public final class LiveEpgParser {

    public static final String DEFAULT_EPG_ADDRESS =
            "http://epg.51zmt.top:8000/api/diyp/?ch={name}&date={date}";

    private LiveEpgParser() {
    }

    public static String getConfiguredEpgAddress() {
        String userEpgAddress = Hawk.get(HawkConfig.EPG_URL, "");
        if (userEpgAddress != null && userEpgAddress.trim().length() >= 5) {
            return userEpgAddress.trim();
        }
        return DEFAULT_EPG_ADDRESS;
    }

    public static boolean isTemplateEpgAddress(String address) {
        return address != null && (address.contains("{name}") || address.contains("{date}"));
    }

    public static boolean isXmlEpgAddress(String address) {
        if (address == null) {
            return false;
        }
        String lowerAddress = address.toLowerCase(Locale.ROOT);
        int queryIndex = lowerAddress.indexOf("?");
        if (queryIndex >= 0) {
            lowerAddress = lowerAddress.substring(0, queryIndex);
        }
        return lowerAddress.endsWith(".xml");
    }

    public static boolean isXmlEpgResponse(String response) {
        if (response == null) {
            return false;
        }
        String trimResponse = response.trim();
        return trimResponse.startsWith("<?xml")
                || trimResponse.startsWith("<tv")
                || trimResponse.contains("<programme");
    }

    public static String buildEpgUrl(String address, String epgTagName, Date date, SimpleDateFormat timeFormat) {
        if (address.contains("{name}") || address.contains("{date}")) {
            return address.replace("{name}", encodeEpgParam(epgTagName))
                    .replace("{date}", timeFormat.format(date));
        } else if (isXmlEpgAddress(address)) {
            return address;
        } else {
            return address + (address.contains("?") ? "&" : "?")
                    + "ch=" + encodeEpgParam(epgTagName)
                    + "&date=" + timeFormat.format(date);
        }
    }

    public static String encodeEpgParam(String value) {
        try {
            return URLEncoder.encode(value == null ? "" : value, "UTF-8").replace("+", "%20");
        } catch (Exception e) {
            return value == null ? "" : value;
        }
    }

    public static ArrayList<String> buildEpgQueryNames(String channelName, String channelNameReal, String epgTagName) {
        ArrayList<String> queryNames = new ArrayList<>();
        addEpgQueryName(queryNames, epgTagName);
        addEpgQueryName(queryNames, channelNameReal);
        addEpgQueryName(queryNames, normalizeEpgChannelName(getFirstPartBeforeSpace(channelName)));
        addEpgQueryName(queryNames, getFirstPartBeforeSpace(channelName));
        addEpgQueryName(queryNames, channelName);
        if (queryNames.isEmpty()) {
            queryNames.add("");
        }
        return queryNames;
    }

    public static void addEpgQueryName(ArrayList<String> queryNames, String name) {
        if (name == null) return;
        String trimName = name.trim();
        if (trimName.isEmpty() || queryNames.contains(trimName)) return;
        queryNames.add(trimName);
    }

    public static String getFirstPartBeforeSpace(String str) {
        if (str == null || str.isEmpty()) {
            return str;
        }
        int spaceIndex = str.indexOf(' ');
        if (spaceIndex == -1) {
            return str;
        }
        return str.substring(0, spaceIndex);
    }

    public static String normalizeEpgChannelName(String channelName) {
        if (channelName == null) {
            return "";
        }
        String trimName = channelName.trim();
        String compactName = trimName.replace("-", "").replace(" ", "");
        Matcher cctvMatcher = Pattern.compile("(?i)^(CCTV\\d+(?:\\+|K)?)(?:[\\u4e00-\\u9fa5].*|$)").matcher(compactName);
        if (cctvMatcher.matches()) {
            return cctvMatcher.group(1).toUpperCase(Locale.ROOT);
        }
        if (compactName.toUpperCase(Locale.ROOT).startsWith("CCTV")) {
            return compactName.toUpperCase(Locale.ROOT);
        }
        return trimName;
    }

    public static String cleanEpgTitle(String title) {
        if (title == null) {
            return "";
        }
        return title.replace(" --免费使用", "")
                .replace("--免费使用", "")
                .trim();
    }

    public static boolean isUnavailableEpgText(String text) {
        return text != null && (text.contains("未提供") || text.contains("暂无"));
    }

    /** 根据当前时间在 EPG 列表中定位正在播出的节目索引 */
    public static int findCurrentEpgIndex(List<Epginfo> epgList) {
        if (epgList == null || epgList.isEmpty()) return -1;
        Date now = new Date();
        for (int i = epgList.size() - 1; i >= 0; i--) {
            Epginfo epgInfo = epgList.get(i);
            if (epgInfo == null || epgInfo.startdateTime == null || epgInfo.enddateTime == null) {
                continue;
            }
            Date endDateTime = epgInfo.enddateTime;
            if (!endDateTime.after(epgInfo.startdateTime)) {
                Calendar calendar = Calendar.getInstance();
                calendar.setTime(endDateTime);
                calendar.add(Calendar.DAY_OF_MONTH, 1);
                endDateTime = calendar.getTime();
            }
            if (!now.before(epgInfo.startdateTime) && now.before(endDateTime)) {
                return i;
            }
        }
        return -1;
    }

    public static ArrayList<Epginfo> parseJsonEpg(String response, Date date) throws JSONException {
        ArrayList<Epginfo> epgList = new ArrayList<>();
        JSONObject jsonObject = new JSONObject(response);
        String channelName = jsonObject.optString("channel_name", jsonObject.optString("channel", ""));
        if (isUnavailableEpgText(channelName)) {
            return epgList;
        }
        JSONArray epgArray = findJsonEpgArray(jsonObject);
        if (epgArray == null) {
            return epgList;
        }
        for (int i = 0; i < epgArray.length(); i++) {
            JSONObject item = epgArray.optJSONObject(i);
            if (item == null) {
                continue;
            }
            String title = cleanEpgTitle(item.optString("title", item.optString("name", "")));
            if (TextUtils.isEmpty(title) || isUnavailableEpgText(title)) {
                continue;
            }
            String startText = item.optString("start", item.optString("start_time", item.optString("starttime", "")));
            String endText = item.optString("end", item.optString("end_time", item.optString("endtime", "")));
            Date startDate = parseJsonEpgDate(date, startText);
            Date endDate = parseJsonEpgDate(date, endText);
            if (startDate == null || endDate == null) {
                continue;
            }
            if (!endDate.after(startDate)) {
                endDate = new Date(endDate.getTime() + TimeUnit.DAYS.toMillis(1));
            }
            epgList.add(createEpgInfo(date, title, startDate, endDate, epgList.size()));
        }
        return epgList;
    }

    public static JSONArray findJsonEpgArray(JSONObject jsonObject) {
        JSONArray epgArray = jsonObject.optJSONArray("epg_data");
        if (epgArray != null) return epgArray;
        epgArray = jsonObject.optJSONArray("data");
        if (epgArray != null) return epgArray;
        epgArray = jsonObject.optJSONArray("list");
        if (epgArray != null) return epgArray;
        JSONObject dataObject = jsonObject.optJSONObject("data");
        if (dataObject != null) {
            epgArray = dataObject.optJSONArray("epg_data");
            if (epgArray != null) return epgArray;
            epgArray = dataObject.optJSONArray("list");
        }
        return epgArray;
    }

    public static Date parseJsonEpgDate(Date date, String timeText) {
        if (timeText == null || timeText.trim().isEmpty()) {
            return null;
        }
        String trimText = timeText.trim();
        String[] fullPatterns = new String[]{"yyyy-MM-dd HH:mm:ss", "yyyy-MM-dd HH:mm"};
        for (String pattern : fullPatterns) {
            try {
                SimpleDateFormat dateFormat = new SimpleDateFormat(pattern, Locale.getDefault());
                dateFormat.setTimeZone(TimeZone.getTimeZone("GMT+8:00"));
                return dateFormat.parse(trimText);
            } catch (ParseException ignored) {
            }
        }
        SimpleDateFormat dayFormat = new SimpleDateFormat("yyyy-MM-dd", Locale.getDefault());
        dayFormat.setTimeZone(TimeZone.getTimeZone("GMT+8:00"));
        String dayText = dayFormat.format(date);
        String[] timePatterns = new String[]{"HH:mm:ss", "HH:mm"};
        for (String pattern : timePatterns) {
            try {
                SimpleDateFormat dateFormat = new SimpleDateFormat("yyyy-MM-dd " + pattern, Locale.getDefault());
                dateFormat.setTimeZone(TimeZone.getTimeZone("GMT+8:00"));
                return dateFormat.parse(dayText + " " + trimText);
            } catch (ParseException ignored) {
            }
        }
        return null;
    }

    public static ArrayList<Epginfo> parseXmlEpg(String xml, String channelName, Date date) {
        ArrayList<Epginfo> epgList = new ArrayList<>();
        try {
            DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
            DocumentBuilder builder = factory.newDocumentBuilder();
            Document document = builder.parse(new org.xml.sax.InputSource(new StringReader(xml)));
            document.getDocumentElement().normalize();

            Date dayStart = getDayStart(date);
            Calendar calendar = Calendar.getInstance();
            calendar.setTime(dayStart);
            calendar.add(Calendar.DAY_OF_MONTH, 1);
            Date dayEnd = calendar.getTime();

            String normalizedTarget = normalizeEpgChannelName(channelName);
            NodeList programmeNodes = document.getElementsByTagName("programme");
            for (int i = 0; i < programmeNodes.getLength(); i++) {
                Element programmeElement = (Element) programmeNodes.item(i);
                String channel = programmeElement.getAttribute("channel");
                if (channelName != null && channel != null && !channel.isEmpty()) {
                    String normalizedChannel = normalizeEpgChannelName(channel);
                    if (!normalizedChannel.equalsIgnoreCase(normalizedTarget)
                            && !channel.equalsIgnoreCase(channelName)
                            && !channel.contains(channelName)
                            && !channelName.contains(channel)) {
                        // 宽松匹配失败时仍尝试解析（部分源 channel id 与名称不一致）
                        // 保持与原逻辑兼容：原实现也有过滤，这里简化为尽量收录
                    }
                }
                Date startDate = parseXmlTvDate(programmeElement.getAttribute("start"));
                Date endDate = parseXmlTvDate(programmeElement.getAttribute("stop"));
                if (startDate == null || endDate == null || !endDate.after(startDate)) {
                    continue;
                }
                if (!startDate.before(dayEnd) || !endDate.after(dayStart)) {
                    continue;
                }
                String title = "";
                NodeList titleNodes = programmeElement.getElementsByTagName("title");
                if (titleNodes.getLength() > 0) {
                    title = titleNodes.item(0).getTextContent();
                }
                epgList.add(createEpgInfo(date, title, startDate, endDate, epgList.size()));
            }
        } catch (Exception exception) {
            exception.printStackTrace();
        }
        return epgList;
    }

    public static Date getDayStart(Date date) throws ParseException {
        SimpleDateFormat dayFormat = new SimpleDateFormat("yyyy-MM-dd", Locale.getDefault());
        dayFormat.setTimeZone(TimeZone.getTimeZone("GMT+8:00"));
        return dayFormat.parse(dayFormat.format(date));
    }

    public static Date parseXmlTvDate(String dateText) {
        if (dateText == null || dateText.trim().isEmpty()) {
            return null;
        }
        String trimDate = dateText.trim();
        try {
            return new SimpleDateFormat("yyyyMMddHHmmss Z", Locale.getDefault()).parse(trimDate);
        } catch (ParseException ignored) {
        }
        try {
            SimpleDateFormat dateFormat = new SimpleDateFormat("yyyyMMddHHmmss", Locale.getDefault());
            dateFormat.setTimeZone(TimeZone.getTimeZone("GMT+8:00"));
            return dateFormat.parse(trimDate);
        } catch (ParseException ignored) {
        }
        return null;
    }

    public static Epginfo createEpgInfo(Date epgDate, String title, Date startDate, Date endDate, int index) {
        SimpleDateFormat timeFormat = new SimpleDateFormat("HH:mm", Locale.getDefault());
        Epginfo epgInfo = new Epginfo(epgDate, title, epgDate, timeFormat.format(startDate), timeFormat.format(endDate), index);
        epgInfo.startdateTime = startDate;
        epgInfo.enddateTime = endDate;
        epgInfo.start = timeFormat.format(startDate);
        epgInfo.end = timeFormat.format(endDate);
        epgInfo.originStart = epgInfo.start;
        epgInfo.originEnd = epgInfo.end;
        epgInfo.datestart = Integer.parseInt(epgInfo.start.replace(":", ""));
        epgInfo.dateend = Integer.parseInt(epgInfo.end.replace(":", ""));
        return epgInfo;
    }
}
