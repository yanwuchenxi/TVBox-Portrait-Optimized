package com.github.tvbox.osc.util;

import com.github.tvbox.osc.base.App;

import java.io.DataInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.Random;

/**
 * User-Agent 工具类（优化版）
 * <p>
 * 原实现将 5000+ 条 UA 硬编码在 String[] 中（约 728KB），导致：
 * - 类加载慢、DEX 体积膨胀
 * - 内存常驻大数组
 * <p>
 * 现统一从 assets/ua.db 懒加载读取（二进制偏移表格式），
 * random() / randomOne() 行为保持兼容。
 */
public final class UA {

    private static final String DEFAULT_UA =
            "Mozilla/5.0 (Macintosh; Intel Mac OS X 10_15_7) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/91.0.4472.114 Safari/537.36";

    private UA() {
        // no instance
    }

    /**
     * 从 assets/ua.db 随机返回一条 User-Agent。
     * 格式：int32(count) + count * int32(offset) + UTF 字符串数据。
     */
    public static String random() {
        DataInputStream dis = null;
        try {
            InputStream fis = App.getInstance().getAssets().open("ua.db");
            dis = new DataInputStream(fis);
            int len = dis.readInt();
            if (len <= 0) {
                return DEFAULT_UA;
            }
            int random = new Random().nextInt(len);
            // 跳过前面的 offset 表
            dis.skipBytes(random * 4);
            int offset = dis.readInt();
            // 跳过剩余 offset 表 + 到达目标字符串
            dis.skipBytes((len - 1 - random) * 4 + offset);
            return dis.readUTF();
        } catch (IOException e) {
            e.printStackTrace();
            return DEFAULT_UA;
        } finally {
            if (dis != null) {
                try {
                    dis.close();
                } catch (IOException ignored) {
                }
            }
        }
    }

    /**
     * 兼容旧接口，内部直接委托给 random()。
     * 原 randomOne() 使用内存数组，现已统一走 assets。
     */
    public static String randomOne() {
        return random();
    }
}
