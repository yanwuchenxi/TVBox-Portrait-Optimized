package com.github.tvbox.osc.player.render;

import android.content.Context;

import xyz.doikki.videoplayer.render.IRenderView;
import xyz.doikki.videoplayer.render.RenderViewFactory;

public class GlShaderRenderViewFactory extends RenderViewFactory {
    public static GlShaderRenderViewFactory create() {
        return new GlShaderRenderViewFactory();
    }

    @Override
    public IRenderView createRenderView(Context context) {
        return new GlShaderRenderView(context);
    }
}
