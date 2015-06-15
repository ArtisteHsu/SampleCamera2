package com.example.mobile.samplecamera2;

import android.app.Activity;
import android.content.Context;
import android.content.pm.ActivityInfo;
import android.graphics.SurfaceTexture;
import android.hardware.camera2.CameraAccessException;
import android.hardware.camera2.CameraCaptureSession;
import android.hardware.camera2.CameraDevice;
import android.hardware.camera2.CameraManager;
import android.hardware.camera2.CaptureFailure;
import android.hardware.camera2.CaptureRequest;
import android.hardware.camera2.CaptureResult;
import android.hardware.camera2.TotalCaptureResult;
import android.opengl.GLSurfaceView;
import android.os.Bundle;
import android.util.Log;
import android.view.Menu;
import android.view.MenuItem;
import android.view.Surface;

import java.util.Arrays;

import javax.microedition.khronos.egl.EGL10;
import javax.microedition.khronos.egl.EGLConfig;
import javax.microedition.khronos.egl.EGLContext;
import javax.microedition.khronos.egl.EGLDisplay;
import javax.microedition.khronos.egl.EGLSurface;

//
// How SampleCamera2 works?
//
// Step 1: Create GLSurfaceView for camera2 required EGL environment
// Step 2: Create Surface from SurfaceTexture
// Step 3: Request CameraManager service, find camera ID (1st only) and open camera device
// Step 4: Create capture request builder for preview. Add preview and encode Surface to target.
// Step 5: Create capture session for preview
// Step 6: Send preview request to start preview
// Step 7: onFrameAvailable() inform texture update of preview
// Step 8: SurfaceTexture update texture image in context of GLSurfaceView.Renderer.onDrawFrame()
// Step 9: Fragment shader draw preview texture (samplerExternalOES)
//
// How to test?
//
// Step 1: Select "Preview + Record" in top-right menu
//

public class MainActivity extends Activity implements SurfaceTexture.OnFrameAvailableListener {
    private final String TAG = this.getClass().getName();

    private SampleMediaEncoder mediaEncoder;
    private SampleGLRenderer glRenderer;
    private SurfaceTexture surfaceTexture;
    private Surface previewSurface;
    private Surface encodeSurface;
    private CaptureRequest.Builder previewRequestBuilder = null;
    private SampleContextFactory sampleContextFactory;
    private SampleWindowSurfaceFactory sampleWindowSurfaceFactory;
    private int mEGLContextClientVersion = 2;
    private EGLContext eglContext = null;
    private EGLSurface eglSurfacePreview = null;
    private EGLSurface eglSurfaceEncode = null;
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        // Step 1: Create GLSurfaceView for camera2 required EGL environment
        GLSurfaceView glSurfaceView;
        glSurfaceView = new GLSurfaceView(this);
        glSurfaceView.setEGLContextClientVersion(mEGLContextClientVersion);
        glRenderer = new SampleGLRenderer();
        sampleContextFactory = new SampleContextFactory();
        sampleWindowSurfaceFactory = new SampleWindowSurfaceFactory();
        glSurfaceView.setEGLContextFactory(sampleContextFactory);
        glSurfaceView.setEGLWindowSurfaceFactory(sampleWindowSurfaceFactory);
        glSurfaceView.setRenderer(glRenderer);

        // SurfaceTexture is the target surface of preview
        surfaceTexture = null;

        //  Create encoder and MediaCodec input surface
        mediaEncoder = new SampleMediaEncoder();
        encodeSurface = mediaEncoder.init();

        // Set landscape mode only, we do not handle screen rotation in this sample
        this.setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE);

        setContentView(glSurfaceView);
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        // Inflate the menu; this adds items to the action bar if it is present.
        getMenuInflater().inflate(R.menu.menu_main, menu);
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        // Handle action bar item clicks here. The action bar will
        // automatically handle clicks on the Home/Up button, so long
        // as you specify a parent activity in AndroidManifest.xml.
        int id = item.getItemId();

        //noinspection SimplifiableIfStatement
        if (id == R.id.action_preview) {
            // Step 2: Create Surface from SurfaceTexture
            if (surfaceTexture == null) {
                surfaceTexture = new SurfaceTexture(glRenderer.getTextureHandle());
                surfaceTexture.setOnFrameAvailableListener(this);
                previewSurface = new Surface(surfaceTexture);
            }

            glRenderer.setEglContext(eglContext);
            glRenderer.setEglSurface(eglSurfacePreview, eglSurfaceEncode);

            // Step 3: Request CameraManager service, find camera ID (1st only) and open camera device
            CameraManager cameraManager = (CameraManager) getSystemService(Context.CAMERA_SERVICE);

            // Find camera ID. This sample uses first camera only
            String cameraID = "";
            try {
                cameraID = cameraManager.getCameraIdList()[0];
            } catch (CameraAccessException e) {
                e.printStackTrace();
            }

            // Setup camera availability callbacks
            cameraManager.registerAvailabilityCallback(cameraAvailabilityCallback, null);

            // Open camera with CameraDevice state callback registered
            try {
                cameraManager.openCamera(cameraID, cameraDeviceStateCallback, null);
            } catch (CameraAccessException e) {
                e.printStackTrace();
            }

            // Start encoder for 300 frames
            mediaEncoder.start(300);

            return true;
        }

        return super.onOptionsItemSelected(item);
    }

    @Override
    public void onFrameAvailable(SurfaceTexture surfaceTexture) {
        // Step 7: onFrameAvailable() inform texture update of preview
        glRenderer.updateTexture(surfaceTexture);
    }

    private final CameraManager.AvailabilityCallback cameraAvailabilityCallback =
            new CameraManager.AvailabilityCallback() {
                @Override
                public void onCameraAvailable(String cameraId) {
                    Log.v(TAG, "onCameraAvailable(" + cameraId + ")");

                }

                @Override
                public void onCameraUnavailable(String cameraId) {
                    Log.v(TAG, "onCameraUnavailable(" + cameraId + ")");

                }
            };

    private final CameraDevice.StateCallback cameraDeviceStateCallback =
            new CameraDevice.StateCallback() {
                @Override
                public void onOpened(CameraDevice cameraDevice) {
                    // Step 4: Create capture request builder for preview.
                    Log.v(TAG, "onOpened(" + cameraDevice.getId() + ")");
                    try {
                        previewRequestBuilder = cameraDevice.createCaptureRequest(CameraDevice.TEMPLATE_PREVIEW);
                    } catch (CameraAccessException e) {
                        e.printStackTrace();
                    }
                    // Add surface of GLSurfaceView to preview target.
                    previewRequestBuilder.addTarget(previewSurface);

                    // Step 5: Create capture session for preview
                    try {
                        cameraDevice.createCaptureSession(Arrays.asList(previewSurface), cameraCaptureSessionStateCallback, null);
                    } catch (CameraAccessException e) {
                        e.printStackTrace();
                    }
                }

                @Override
                public void onDisconnected(CameraDevice cameraDevice) {
                    Log.v(TAG, "onDisconnected(" + cameraDevice.getId() + ")");
                }

                @Override
                public void onError(CameraDevice cameraDevice, int error) {
                    Log.e(TAG, "onError(" + cameraDevice.getId() + ") error " + error );
                }

                @Override
                public void onClosed(CameraDevice cameraDevice) {
                    Log.v(TAG, "onClosed(" + cameraDevice.getId() + ")");
                }
            };

    private final CameraCaptureSession.StateCallback cameraCaptureSessionStateCallback =
            new CameraCaptureSession.StateCallback() {
                @Override
                public void onConfigured(CameraCaptureSession cameraCaptureSession) {
                    // Step 6: Send preview request to start preview
                    Log.v(TAG, "onConfigured(" + cameraCaptureSession + ")");
                    try {
                        CaptureRequest previewRequest = previewRequestBuilder.build();
                        cameraCaptureSession.setRepeatingRequest(previewRequest,
                                cameraCaptureSessionCaptureCallback, null);
                    } catch (CameraAccessException e) {
                        e.printStackTrace();
                    }
                }

                @Override
                public void onConfigureFailed(CameraCaptureSession cameraCaptureSession) {
                    Log.e(TAG, "onConfigureFailed(" + cameraCaptureSession + ")");
                }
            };
    private CameraCaptureSession.CaptureCallback cameraCaptureSessionCaptureCallback
            = new CameraCaptureSession.CaptureCallback() {

        @Override
        public void onCaptureStarted(CameraCaptureSession session, CaptureRequest request, long timestamp,
                                     long frameNumber) {
            //Log.d(TAG, "onCaptureStarted(" + session + ")");
        }

        @Override
        public void onCaptureProgressed(CameraCaptureSession session, CaptureRequest request,
                                        CaptureResult partialResult) {
            //Log.d(TAG, "onCaptureProgressed(" + session + ")");
        }

        @Override
        public void onCaptureCompleted(CameraCaptureSession session, CaptureRequest request,
                                       TotalCaptureResult result) {
            //Log.d(TAG, "onCaptureCompleted(" + session + ")");
        }

        @Override
        public void onCaptureFailed(CameraCaptureSession session, CaptureRequest request,
                                    CaptureFailure failure) {
            Log.e(TAG, "onCaptureFailed(" + session + ")");
        }
    };

    private class SampleContextFactory implements GLSurfaceView.EGLContextFactory {
        private int EGL_CONTEXT_CLIENT_VERSION = 0x3098;

        public EGLContext createContext(EGL10 egl, EGLDisplay display, EGLConfig config) {
            int[] attrib_list = {EGL_CONTEXT_CLIENT_VERSION, mEGLContextClientVersion,
                    EGL10.EGL_NONE };

            eglContext = egl.eglCreateContext(display, config, EGL10.EGL_NO_CONTEXT,
                    mEGLContextClientVersion != 0 ? attrib_list : null);
            return eglContext;
        }

        public void destroyContext(EGL10 egl, EGLDisplay display,
                                   EGLContext context) {
            if (!egl.eglDestroyContext(display, context)) {
                Log.e("DefaultContextFactory", "display:" + display + " context: " + context);
            }
        }
    }

    private class SampleWindowSurfaceFactory implements GLSurfaceView.EGLWindowSurfaceFactory {
        private final String TAG = this.getClass().getName();
        public EGLSurface createWindowSurface(EGL10 egl, EGLDisplay display,
                                              EGLConfig config, Object nativeWindow) {
            EGLSurface result = null;
            try {
                eglSurfacePreview = egl.eglCreateWindowSurface(display, config, nativeWindow, null);
            } catch (IllegalArgumentException e) {
                // This exception indicates that the surface flinger surface
                // is not valid. This can happen if the surface flinger surface has
                // been torn down, but the application has not yet been
                // notified via SurfaceHolder.Callback.surfaceDestroyed.
                // In theory the application should be notified first,
                // but in practice sometimes it is not. See b/4588890
                Log.e(TAG, "eglCreateWindowSurface (native)", e);
            }
            try {
                eglSurfaceEncode = egl.eglCreateWindowSurface(display, config, encodeSurface, null);
            } catch (IllegalArgumentException e) {
                // This exception indicates that the surface flinger surface
                // is not valid. This can happen if the surface flinger surface has
                // been torn down, but the application has not yet been
                // notified via SurfaceHolder.Callback.surfaceDestroyed.
                // In theory the application should be notified first,
                // but in practice sometimes it is not. See b/4588890
                Log.e(TAG, "eglCreateWindowSurface (input surface)", e);
            }
            result = eglSurfacePreview;
            return result;
        }

        public void destroySurface(EGL10 egl, EGLDisplay display,
                                   EGLSurface surface) {
            egl.eglDestroySurface(display, surface);
        }
    }
}