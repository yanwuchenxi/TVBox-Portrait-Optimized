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
 * 臻彩：与影视仓一致，仅开关。
 * 优先走 Exo + GLES 真 shader；否则回退 TextureView ColorMatrix。
 */
public final class PlayerZhenCaiHelper {
    public static final String HAWK_KEY = "play_zhencai_mode";

    /** 兼容旧按钮文案：0 关，1 开 */
    public static final String[] MODE_NAMES = {"臻彩关", "臻彩开"};

    private PlayerZhenCaiHelper() {}

    public static int getMode() {
        return Hawk.get(HAWK_KEY, 0) > 0 ? 1 : 0;
    }

    public static boolean isOn() {
        return getMode() > 0;
    }

    public static String getModeName() {
        return MODE_NAMES[getMode()];
    }

    public static String getButtonLabel() {
        return isOn() ? "臻彩·开" : "臻彩";
    }

    public static int cycleMode() {
        int next = isOn() ? 0 : 1;
        Hawk.put(HAWK_KEY, next);
        return next;
    }

    public static void setMode(int mode) {
        Hawk.put(HAWK_KEY, mode > 0 ? 1 : 0);
    }

    public static void applyToVideoView(View videoRoot) {
        if (videoRoot == null) return;
        GlShaderRenderView gl = findGlView(videoRoot);
        if (gl != null) {
            gl.applyZhenCaiMode(getMode());
            return;
        }
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
            ColorMatrix cm = new ColorMatrix();
            cm.setSaturation(1.35f);
            ColorMatrix contrast = new ColorMatrix(new float[]{
                    1.12f, 0, 0, 0, 6,
                    0, 1.12f, 0, 0, 6,
                    0, 0, 1.12f, 0, 6,
                    0, 0, 0, 1, 0
            });
            cm.postConcat(contrast);
            Paint paint = new Paint();
            paint.setColorFilter(new ColorMatrixColorFilter(cm));
            textureView.setLayerType(View.LAYER_TYPE_HARDWARE, paint);
        } catch (Throwable ignored) {
        }
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
