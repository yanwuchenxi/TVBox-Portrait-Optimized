package com.github.tvbox.osc.util;

/**
 * 播放解析超时与类型常量（集中配置）
 */
public final class ParseTimeoutHelper {

    /** Handler message：嗅探/解析超时 */
    public static final int MSG_PARSE_TIMEOUT = 100;
    /** Handler message：解析播放地址超时 */
    public static final int MSG_RESOLVE_PLAY_URL_TIMEOUT = 101;
    /** Handler message：切换线路播放超时 */
    public static final int MSG_SWITCH_LINE_PLAY_TIMEOUT = 102;

    /** 默认解析/嗅探超时（毫秒） */
    public static final long PARSE_TIMEOUT_MS = 20_000L;
    /** 切换线路后等待出画超时（毫秒） */
    public static final long SWITCH_LINE_PLAY_TIMEOUT_MS = 15_000L;
    /** 解析直链超时（毫秒） */
    public static final long RESOLVE_PLAY_URL_TIMEOUT_MS = 15_000L;

    /** ParseBean.type：嗅探 */
    public static final int TYPE_SNIFF = 0;
    /** ParseBean.type：JSON 解析 */
    public static final int TYPE_JSON = 1;
    /** ParseBean.type：JSON 扩展 */
    public static final int TYPE_JSON_EXT = 2;
    /** ParseBean.type：JSON 聚合 */
    public static final int TYPE_JSON_MIX = 3;
    /** ParseBean.type：超级聚合 */
    public static final int TYPE_SUPER_MIX = 4;

    private ParseTimeoutHelper() {
    }
}
