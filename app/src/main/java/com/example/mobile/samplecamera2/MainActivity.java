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

//
// How SampleCamera2 works?
//
// Step 1: Create GLSurfaceView for camera2 required EGL environment
// Step 2: Create Surface from SurfaceTexture
// Step 3: Request CameraManager service, find camera ID (1st only) and open camera device
// Step 4: Create capture request builder for preview. Add Surface to target.
// Step 5: Create capture session for preview
// Step 6: Send preview request to start preview
// Step 7: onFrameAvailable() inform texture update of preview
// Step 8: SurfaceTexture update texture image in context of GLSurfaceView.Renderer.onDrawFrame()
// Step 9: Fragment shader draw preview texture (samplerExternalOES)
//
// How to test?
//
// Step 1: Select "Preview" in top-right menu
//

public class MainActivity extends Activity implements SurfaceTexture.OnFrameAvailableListener {
    private final String TAG = this.getClass().getName();

    private SampleGLRenderer glRenderer;
    private SurfaceTexture surfaceTexture;
    private Surface surface;
    private CaptureRequest.Builder previewRequestBuilder = null;
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        // Step 1: Create GLSurfaceView for camera2 required EGL environment
        GLSurfaceView glSurfaceView;
        glSurfaceView = new GLSurfaceView(this);
        glSurfaceView.setEGLContextClientVersion(2);
        glRenderer = new SampleGLRenderer();
        glSurfaceView.setRenderer(glRenderer);

        // SurfaceTexture is the target surface of preview
        surfaceTexture = null;

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
                surface = new Surface(surfaceTexture);
            }
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
                    // Step 4: Create capture request builder for preview. Add Surface to target.
                    Log.v(TAG, "onOpened(" + cameraDevice.getId() + ")");
                    try {
                        previewRequestBuilder = cameraDevice.createCaptureRequest(CameraDevice.TEMPLATE_PREVIEW);
                    } catch (CameraAccessException e) {
                        e.printStackTrace();
                    }
                    previewRequestBuilder.addTarget(surface);

                    // Step 5: Create capture session for preview
                    try {
                        cameraDevice.createCaptureSession(Arrays.asList(surface), cameraCaptureSessionStateCallback, null);
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
}