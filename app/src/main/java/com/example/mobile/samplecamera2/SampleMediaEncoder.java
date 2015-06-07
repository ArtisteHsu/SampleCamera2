package com.example.mobile.samplecamera2;

import android.media.MediaCodec;
import android.media.MediaCodecInfo.CodecCapabilities;
import android.media.MediaFormat;
import android.media.MediaMuxer;
import android.util.Log;
import android.view.Surface;

import java.io.IOException;
import java.nio.ByteBuffer;

//
// How to use
// 1. Caller call init() to initial MediaCodec and create/return input surface for encode.
// 2. Caller call start() to start encoding. Default duration is 300 frames.
// 3, Caller wait for encode complete.
//

public class SampleMediaEncoder {
    private final String TAG = this.getClass().getName();
    private MediaCodec encoder = null;
    private Surface inputSurface = null;
    private boolean eos = true;  // End-Of-Stream
    private MediaMuxer mediaMuxer = null;
    private int videoTrackIndex;
    private int encodeDuration = 300; // Default encode duration is 300 frames

    // Define encode format
    final String MINE_TYPE = "video/avc";
    final int WIDTH = 1280;
    final int HEIGHT = 720;
    final int BIT_RATE = 1250000;
    final int FRAME_RATE = 30;
    final int COLOR_FORMAT = CodecCapabilities.COLOR_FormatSurface;
    final int I_FRAME_INTERVAL = 5;
    final int CAPTURE_RATE = 30;
    final int REPEAT_PREVIOUS_FRAME_AFTER = (1000/FRAME_RATE);

    // Define muxer format
    final String MUXER_OUTPUT_FILE = "/sdcard/Movies/sampleCameraRecord.mp4";
    final int MUXER_OUTPUT_FORMAT = MediaMuxer.OutputFormat.MUXER_OUTPUT_MPEG_4;

    public Surface init() {
        //  Set up encode format
        MediaFormat encodeFormat = MediaFormat.createVideoFormat(MINE_TYPE, WIDTH, HEIGHT);
        encodeFormat.setInteger(MediaFormat.KEY_BIT_RATE, BIT_RATE);
        encodeFormat.setInteger(MediaFormat.KEY_FRAME_RATE, FRAME_RATE);
        encodeFormat.setInteger(MediaFormat.KEY_COLOR_FORMAT, COLOR_FORMAT);
        encodeFormat.setInteger(MediaFormat.KEY_I_FRAME_INTERVAL, I_FRAME_INTERVAL);
        encodeFormat.setInteger(MediaFormat.KEY_CAPTURE_RATE, CAPTURE_RATE);
        // KEY_REPEAT_PREVIOUS_FRAME_AFTER is for Surface-Input mode. See createInputSurface().
        encodeFormat.setInteger(MediaFormat.KEY_REPEAT_PREVIOUS_FRAME_AFTER,
                REPEAT_PREVIOUS_FRAME_AFTER);

        // Create encoder and input surface
        try {
            encoder = MediaCodec.createEncoderByType(MINE_TYPE);
        } catch (IOException e) {
            e.printStackTrace();
        }
        encoder.configure(encodeFormat, null, null, MediaCodec.CONFIGURE_FLAG_ENCODE);
        inputSurface = encoder.createInputSurface();

        // Create MdieaMuxer for write encoded data to file
        try {
            mediaMuxer = new MediaMuxer(MUXER_OUTPUT_FILE, MUXER_OUTPUT_FORMAT);
        } catch (IOException e) {
            e.printStackTrace();
        }
        return inputSurface;
    }
    public void start(int duration) {
        if(encoder == null || inputSurface == null) {
            return;
        }
        if (duration > 0) {
            encodeDuration = duration;
        }
        new Thread(new Runnable() {
            public void run() {
                try {
                    encodeTask();
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }
        }).start();
    }

    private void encodeTask() throws IOException {
        Log.v(TAG, "Start encoder");
        boolean muxerStarted = false;
        encoder.start();
        MediaCodec.BufferInfo bufferInfo = new MediaCodec.BufferInfo();
        eos = false;
        int frameLimit = encodeDuration;
        while (!eos) {
            int outputBufferIndex = encoder.dequeueOutputBuffer(bufferInfo, -1);
            if (outputBufferIndex >= 0) {
                // Encode pre-defined amount of frame
                if(frameLimit > 0) {
                    ByteBuffer outputBuffer = encoder.getOutputBuffer(outputBufferIndex);
                    if (!muxerStarted) {
                        MediaFormat format = encoder.getOutputFormat(outputBufferIndex);
                        Log.v(TAG, "Adding video track " + format);
                        videoTrackIndex = mediaMuxer.addTrack(format);
                        Log.v(TAG, "MediaMuxer start");
                        mediaMuxer.start();
                        muxerStarted = true;
                    }
                    mediaMuxer.writeSampleData(videoTrackIndex, outputBuffer, bufferInfo);
                    if (--frameLimit == 0) {
                        Log.v(TAG, "MediaMuxer stop");
                        mediaMuxer.stop();
                        muxerStarted = false;
                    }
                }
                encoder.releaseOutputBuffer(outputBufferIndex, false);
            } else if (outputBufferIndex == MediaCodec.INFO_OUTPUT_BUFFERS_CHANGED) {
                Log.v(TAG, "Output buffers changed. API Level > 21 can ignore this.");
            } else if (outputBufferIndex == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED) {
                Log.v(TAG, "Output format change. API Level > 21 can ignore this.");
            } else {
                Log.e(TAG, "Un-defined dequeueOutputBuffer() error");
            }
        }

        if (muxerStarted) {
            Log.v(TAG, "MediaMuxer stop");
            mediaMuxer.stop();
        }
        Log.v(TAG, "Stop encoder");
        encoder.stop();
    }
}
