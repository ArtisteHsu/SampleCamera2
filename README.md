# SampleCamera2
Implementation of android.hardware.camera2 package base on SampleOpenGLPlayer that uses GLSurfaceView, Surface of SurfaceTexture.

How SampleCamera2 works?

    Step 1: Create GLSurfaceView for camera2 required EGL environment
    Step 2: Create Surface from SurfaceTexture
    Step 3: Request CameraManager service, find camera ID (1st only) and open camera device
    Step 4: Create capture request builder for preview. Add Surface to target.
    Step 5: Create capture session for preview
    Step 6: Send preview request to start preview
    Step 7: onFrameAvailable() inform texture update of preview
    Step 8: SurfaceTexture update texture image in context of GLSurfaceView.Renderer.onDrawFrame()
    Step 9: Fragment shader draw preview texture (samplerExternalOES)

How to test?

    Step 1: Select "Preview" in top-right menu

