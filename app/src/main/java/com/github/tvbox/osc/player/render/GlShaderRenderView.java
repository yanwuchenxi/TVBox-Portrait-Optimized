package com.github.tvbox.osc.player.render;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.SurfaceTexture;
import android.opengl.GLES11Ext;
import android.opengl.GLES20;
import android.opengl.GLSurfaceView;
import android.opengl.Matrix;
import android.util.Log;
import android.view.Surface;
import android.view.View;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.github.tvbox.osc.player.effect.PlayerZhenCaiHelper;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.FloatBuffer;

import javax.microedition.khronos.egl.EGLConfig;
import javax.microedition.khronos.opengles.GL10;

import xyz.doikki.videoplayer.player.AbstractPlayer;
import xyz.doikki.videoplayer.render.IRenderView;
import xyz.doikki.videoplayer.render.MeasureHelper;

/**
 * Exo 专用：OES 外部纹理 + 真 fragment shader 臻彩管线。
 */
public class GlShaderRenderView extends GLSurfaceView implements IRenderView, GLSurfaceView.Renderer, SurfaceTexture.OnFrameAvailableListener {
    private static final String TAG = "GlShaderRender";

    private final MeasureHelper measureHelper = new MeasureHelper();
    @Nullable private AbstractPlayer mediaPlayer;
    private SurfaceTexture surfaceTexture;
    private Surface surface;
    private int oesTexId;
    private int program;
    private int aPosition;
    private int aTexCoord;
    private int uSTMatrix;
    private int uSaturation;
    private int uContrast;
    private int uBrightness;
    private int uWarmth;
    private int uGamma;
    private final float[] stMatrix = new float[16];
    private FloatBuffer vertBuffer;
    private FloatBuffer texBuffer;
    private volatile boolean frameAvailable;
    private int videoWidth, videoHeight;

    // 当前臻彩参数（主线程写入，GL 线程读取）
    private volatile float sat = 1f, contrast = 1f, brightness = 0f, warmth = 0f, gamma = 1f;

    public GlShaderRenderView(Context context) {
        super(context);
        setEGLContextClientVersion(2);
        setRenderer(this);
        setRenderMode(RENDERMODE_WHEN_DIRTY);
        float[] verts = {-1f, -1f, 1f, -1f, -1f, 1f, 1f, 1f};
        float[] texs = {0f, 0f, 1f, 0f, 0f, 1f, 1f, 1f};
        vertBuffer = ByteBuffer.allocateDirect(verts.length * 4).order(ByteOrder.nativeOrder()).asFloatBuffer();
        vertBuffer.put(verts).position(0);
        texBuffer = ByteBuffer.allocateDirect(texs.length * 4).order(ByteOrder.nativeOrder()).asFloatBuffer();
        texBuffer.put(texs).position(0);
        Matrix.setIdentityM(stMatrix, 0);
        applyZhenCaiMode(PlayerZhenCaiHelper.getMode());
    }

    /** 影视仓同款：仅开关。0=直通，>0=开启臻彩增强 */
    public void applyZhenCaiMode(int mode) {
        if (mode > 0) {
            // 鲜明提饱和+对比，接近影视仓开启观感
            sat = 1.35f;
            contrast = 1.12f;
            brightness = 0.02f;
            warmth = 0.04f;
            gamma = 1.04f;
        } else {
            sat = 1f;
            contrast = 1f;
            brightness = 0f;
            warmth = 0f;
            gamma = 1f;
        }
        requestRender();
    }

    @Override
    public void attachToPlayer(@NonNull AbstractPlayer player) {
        this.mediaPlayer = player;
        if (surface != null) {
            player.setSurface(surface);
        }
    }

    @Override
    public void setVideoSize(int videoWidth, int videoHeight) {
        if (videoWidth > 0 && videoHeight > 0) {
            this.videoWidth = videoWidth;
            this.videoHeight = videoHeight;
            measureHelper.setVideoSize(videoWidth, videoHeight);
            requestLayout();
        }
    }

    @Override
    public void setVideoRotation(int degree) {
        measureHelper.setVideoRotation(degree);
        setRotation(degree);
    }

    @Override
    public void setScaleType(int scaleType) {
        measureHelper.setScreenScale(scaleType);
        requestLayout();
    }

    @Override
    public View getView() {
        return this;
    }

    @Override
    public Bitmap doScreenShot() {
        return null;
    }

    @Override
    public void release() {
        queueEvent(() -> {
            if (surface != null) {
                surface.release();
                surface = null;
            }
            if (surfaceTexture != null) {
                surfaceTexture.release();
                surfaceTexture = null;
            }
            if (oesTexId != 0) {
                int[] t = {oesTexId};
                GLES20.glDeleteTextures(1, t, 0);
                oesTexId = 0;
            }
            if (program != 0) {
                GLES20.glDeleteProgram(program);
                program = 0;
            }
        });
        mediaPlayer = null;
    }

    @Override
    protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        int[] measuredSize = measureHelper.doMeasure(widthMeasureSpec, heightMeasureSpec);
        setMeasuredDimension(measuredSize[0], measuredSize[1]);
    }

    @Override
    public void onSurfaceCreated(GL10 gl, EGLConfig config) {
        oesTexId = createOesTexture();
        surfaceTexture = new SurfaceTexture(oesTexId);
        surfaceTexture.setOnFrameAvailableListener(this);
        surface = new Surface(surfaceTexture);
        program = buildProgram(loadAsset("shaders/zhencai_vertex.glsl"), loadAsset("shaders/zhencai_fragment.glsl"));
        aPosition = GLES20.glGetAttribLocation(program, "aPosition");
        aTexCoord = GLES20.glGetAttribLocation(program, "aTexCoord");
        uSTMatrix = GLES20.glGetUniformLocation(program, "uSTMatrix");
        uSaturation = GLES20.glGetUniformLocation(program, "uSaturation");
        uContrast = GLES20.glGetUniformLocation(program, "uContrast");
        uBrightness = GLES20.glGetUniformLocation(program, "uBrightness");
        uWarmth = GLES20.glGetUniformLocation(program, "uWarmth");
        uGamma = GLES20.glGetUniformLocation(program, "uGamma");
        applyZhenCaiMode(PlayerZhenCaiHelper.getMode());
        post(() -> {
            if (mediaPlayer != null && surface != null) {
                mediaPlayer.setSurface(surface);
            }
        });
    }

    @Override
    public void onSurfaceChanged(GL10 gl, int width, int height) {
        GLES20.glViewport(0, 0, width, height);
    }

    @Override
    public void onDrawFrame(GL10 gl) {
        if (surfaceTexture == null || program == 0) return;
        if (frameAvailable) {
            frameAvailable = false;
            try {
                surfaceTexture.updateTexImage();
                surfaceTexture.getTransformMatrix(stMatrix);
            } catch (Throwable t) {
                return;
            }
        }
        GLES20.glClearColor(0f, 0f, 0f, 1f);
        GLES20.glClear(GLES20.GL_COLOR_BUFFER_BIT);
        GLES20.glUseProgram(program);
        GLES20.glActiveTexture(GLES20.GL_TEXTURE0);
        GLES20.glBindTexture(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, oesTexId);
        GLES20.glUniformMatrix4fv(uSTMatrix, 1, false, stMatrix, 0);
        GLES20.glUniform1f(uSaturation, sat);
        GLES20.glUniform1f(uContrast, contrast);
        GLES20.glUniform1f(uBrightness, brightness);
        GLES20.glUniform1f(uWarmth, warmth);
        GLES20.glUniform1f(uGamma, gamma);
        GLES20.glEnableVertexAttribArray(aPosition);
        GLES20.glVertexAttribPointer(aPosition, 2, GLES20.GL_FLOAT, false, 0, vertBuffer);
        GLES20.glEnableVertexAttribArray(aTexCoord);
        GLES20.glVertexAttribPointer(aTexCoord, 2, GLES20.GL_FLOAT, false, 0, texBuffer);
        GLES20.glDrawArrays(GLES20.GL_TRIANGLE_STRIP, 0, 4);
        GLES20.glDisableVertexAttribArray(aPosition);
        GLES20.glDisableVertexAttribArray(aTexCoord);
    }

    @Override
    public void onFrameAvailable(SurfaceTexture surfaceTexture) {
        frameAvailable = true;
        requestRender();
    }

    private int createOesTexture() {
        int[] tex = new int[1];
        GLES20.glGenTextures(1, tex, 0);
        GLES20.glBindTexture(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, tex[0]);
        GLES20.glTexParameteri(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, GLES20.GL_TEXTURE_MIN_FILTER, GLES20.GL_LINEAR);
        GLES20.glTexParameteri(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, GLES20.GL_TEXTURE_MAG_FILTER, GLES20.GL_LINEAR);
        GLES20.glTexParameteri(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, GLES20.GL_TEXTURE_WRAP_S, GLES20.GL_CLAMP_TO_EDGE);
        GLES20.glTexParameteri(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, GLES20.GL_TEXTURE_WRAP_T, GLES20.GL_CLAMP_TO_EDGE);
        return tex[0];
    }

    private String loadAsset(String path) {
        try {
            InputStream is = getContext().getAssets().open(path);
            BufferedReader br = new BufferedReader(new InputStreamReader(is));
            StringBuilder sb = new StringBuilder();
            String line;
            while ((line = br.readLine()) != null) sb.append(line).append('\n');
            br.close();
            return sb.toString();
        } catch (Throwable t) {
            Log.e(TAG, "loadAsset " + path, t);
            return "";
        }
    }

    private int buildProgram(String vs, String fs) {
        int v = compile(GLES20.GL_VERTEX_SHADER, vs);
        int f = compile(GLES20.GL_FRAGMENT_SHADER, fs);
        int p = GLES20.glCreateProgram();
        GLES20.glAttachShader(p, v);
        GLES20.glAttachShader(p, f);
        GLES20.glLinkProgram(p);
        int[] ok = new int[1];
        GLES20.glGetProgramiv(p, GLES20.GL_LINK_STATUS, ok, 0);
        if (ok[0] == 0) {
            Log.e(TAG, "link error: " + GLES20.glGetProgramInfoLog(p));
            GLES20.glDeleteProgram(p);
            return 0;
        }
        return p;
    }

    private int compile(int type, String src) {
        int s = GLES20.glCreateShader(type);
        GLES20.glShaderSource(s, src);
        GLES20.glCompileShader(s);
        int[] ok = new int[1];
        GLES20.glGetShaderiv(s, GLES20.GL_COMPILE_STATUS, ok, 0);
        if (ok[0] == 0) {
            Log.e(TAG, "compile error: " + GLES20.glGetShaderInfoLog(s));
        }
        return s;
    }
}
