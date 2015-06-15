package com.example.mobile.samplecamera2;

import android.graphics.SurfaceTexture;
import android.opengl.GLSurfaceView;
import android.util.Log;

import javax.microedition.khronos.egl.EGL10;
import javax.microedition.khronos.egl.EGLConfig;
import javax.microedition.khronos.egl.EGLContext;
import javax.microedition.khronos.egl.EGLDisplay;
import javax.microedition.khronos.egl.EGLSurface;
import javax.microedition.khronos.opengles.GL10;

public class SampleGLRenderer implements GLSurfaceView.Renderer {
    private final String TAG = this.getClass().getName();
    private SampleGLES20Video sampleGL20Video;
    private SurfaceTexture surfaceTexture;
    private EGL10 egl10;
    private EGLDisplay eglDisplay;
    private EGLContext eglContext = null;
    private EGLSurface eglSurfacePreview = null;
    private EGLSurface eglSurfaceEncode = null;
    @Override
    public void onSurfaceCreated(GL10 gl, EGLConfig config) {
        sampleGL20Video = new SampleGLES20Video();
        surfaceTexture = null;
        egl10 = (EGL10) EGLContext.getEGL();
        eglDisplay = egl10.eglGetDisplay(EGL10.EGL_DEFAULT_DISPLAY);
    }

    @Override
    public void onDrawFrame(GL10 gl) {
        // Step 8: SurfaceTexture update texture image in context of GLSurfaceView.Renderer.onDrawFrame()
        if(surfaceTexture != null) {
            // Here is right context of calling SurfaceTexture.updateTexImage()
            surfaceTexture.updateTexImage();
            surfaceTexture = null;
        }

        // Step 9: Fragment shader draw preview texture (samplerExternalOES)
        // Draw on MediaCodec InputSurface for encode
        egl10.eglMakeCurrent(eglDisplay, eglSurfaceEncode, eglSurfaceEncode, eglContext);
        sampleGL20Video.draw();
        egl10.eglSwapBuffers(eglDisplay, eglSurfaceEncode);
        // Draw on native window (surface) for preview
        egl10.eglMakeCurrent(eglDisplay, eglSurfacePreview, eglSurfacePreview, eglContext);
        sampleGL20Video.draw();
    }

    @Override
    public void onSurfaceChanged(GL10 gl, int width, int height) {
        sampleGL20Video.setResolution(width, height);
    }

    public int getTextureHandle() {
        return sampleGL20Video.getTextureHandle();
    }

    public void updateTexture(SurfaceTexture st) {
        // Update SurfaceText pointer to update texture in next call of onDrawFrame()
        surfaceTexture = st;
    }

    public void screenshot(String fileName) {
        sampleGL20Video.screenshot(fileName);
    }

    public void setEglContext(EGLContext ctx) {
        eglContext = ctx;
    }

    public void setEglSurface(EGLSurface preview, EGLSurface encode) {
        eglSurfacePreview = preview;
        eglSurfaceEncode = encode;
    }
}