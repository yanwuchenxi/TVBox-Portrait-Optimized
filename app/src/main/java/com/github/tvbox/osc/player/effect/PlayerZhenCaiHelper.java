package com.github.tvbox.osc.player.effect;

import android.graphics.ColorMatrix;
import android.graphics.ColorMatrixColorFilter;
import android.graphics.Paint;
import android.view.TextureView;
import android.view.View;
import android.view.ViewGroup;
import com.github.tvbox.osc.player.render.GlShaderRenderView;

import com.orhanobut.hawk.Hawk;

/**
 * 点播臻彩（色彩增强）：对 TextureView 施加 ColorMatrix。
 * 0关闭 1标准 2鲜明 3柔和 4暖色 5冷色 6黑白电影
 */
public final class PlayerZhenCaiHelper {
    public static final String HAWK_KEY = "play_zhencai_mode";

    public static final String[] MODE_NAMES = {
            "臻彩关", "标准", "鲜明", "柔和", "暖色", "冷色", "电影感"
    };

    private static int appliedOnViewHash = 0;

    private PlayerZhenCaiHelper() {}

    public static int getMode() {
        return Hawk.get(HAWK_KEY, 0);
    }

    public static String getModeName() {
        int m = getMode();
        if (m < 0 || m >= MODE_NAMES.length) m = 0;
        return MODE_NAMES[m];
    }

    public static int cycleMode() {
        int next = (getMode() + 1) % MODE_NAMES.length;
        Hawk.put(HAWK_KEY, next);
        return next;
    }

    public static void setMode(int mode) {
        if (mode < 0 || mode >= MODE_NAMES.length) mode = 0;
        Hawk.put(HAWK_KEY, mode);
    }

    public static void applyToVideoView(View videoRoot) {
        if (videoRoot == null) return;
        GlShaderRenderView gl = findGlView(videoRoot);
        if (gl != null) {
            gl.applyZhenCaiMode(getMode());
            return;
        }
        // 回退：TextureView ColorMatrix（非 Exo/GL 路径）
        TextureView tv = findTextureView(videoRoot);
        if (tv == null) return;
        applyToTextureView(tv, getMode());
    }

    private static GlShaderRenderView findGlView(View root) {
        if (root instanceof GlShaderRenderView) return (GlShaderRenderView) root;
        if (root instanceof ViewGroup) {
            ViewGroup vg = (ViewGroup) root;
            for (int i = 0; i < vg.getChildCount(); i++) {
                GlShaderRenderView g = findGlView(vg.getChildAt(i));
                if (g != null) return g;
            }
        }
        return null;
    }

    public static void applyToTextureView(TextureView textureView, int mode) {
        if (textureView == null) return;
        try {
            if (mode <= 0) {
                textureView.setLayerType(View.LAYER_TYPE_NONE, null);
                return;
            }
            ColorMatrix cm = buildMatrix(mode);
            Paint paint = new Paint();
            paint.setColorFilter(new ColorMatrixColorFilter(cm));
            textureView.setLayerType(View.LAYER_TYPE_HARDWARE, paint);
            appliedOnViewHash = System.identityHashCode(textureView);
        } catch (Throwable ignored) {
        }
    }

    private static ColorMatrix buildMatrix(int mode) {
        ColorMatrix cm = new ColorMatrix();
        switch (mode) {
            case 1: // 标准：轻微提亮对比
                cm.setScale(1.05f, 1.05f, 1.05f, 1f);
                break;
            case 2: // 鲜明：饱和+对比
                cm.setSaturation(1.45f);
                ColorMatrix contrast = new ColorMatrix(new float[]{
                        1.15f, 0, 0, 0, 8,
                        0, 1.15f, 0, 0, 8,
                        0, 0, 1.15f, 0, 8,
                        0, 0, 0, 1, 0
                });
                cm.postConcat(contrast);
                break;
            case 3: // 柔和
                cm.setSaturation(0.85f);
                ColorMatrix soft = new ColorMatrix(new float[]{
                        0.95f, 0, 0, 0, 12,
                        0, 0.95f, 0, 0, 12,
                        0, 0, 0.95f, 0, 12,
                        0, 0, 0, 1, 0
                });
                cm.postConcat(soft);
                break;
            case 4: // 暖色
                cm.set(new float[]{
                        1.15f, 0, 0, 0, 10,
                        0, 1.05f, 0, 0, 5,
                        0, 0, 0.9f, 0, 0,
                        0, 0, 0, 1, 0
                });
                break;
            case 5: // 冷色
                cm.set(new float[]{
                        0.9f, 0, 0, 0, 0,
                        0, 1.05f, 0, 0, 5,
                        0, 0, 1.2f, 0, 12,
                        0, 0, 0, 1, 0
                });
                break;
            case 6: // 电影感：降饱和+抬对比
                cm.setSaturation(0.7f);
                ColorMatrix film = new ColorMatrix(new float[]{
                        1.2f, 0, 0, 0, -10,
                        0, 1.15f, 0, 0, -8,
                        0, 0, 1.1f, 0, -5,
                        0, 0, 0, 1, 0
                });
                cm.postConcat(film);
                break;
            default:
                break;
        }
        return cm;
    }

    private static TextureView findTextureView(View root) {
        if (root instanceof TextureView) return (TextureView) root;
        if (root instanceof ViewGroup) {
            ViewGroup vg = (ViewGroup) root;
            for (int i = 0; i < vg.getChildCount(); i++) {
                TextureView t = findTextureView(vg.getChildAt(i));
                if (t != null) return t;
            }
        }
        return null;
    }
}
