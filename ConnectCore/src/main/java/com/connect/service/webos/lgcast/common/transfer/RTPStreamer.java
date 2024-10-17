package com.connect.service.webos.lgcast.common.transfer;

import android.content.Context;

import com.connect.service.webos.lgcast.common.utils.Logger;
import com.connect.service.webos.lgcast.common.utils.ThreadWait;

import java.util.concurrent.atomic.AtomicBoolean;
import org.freedesktop.gstreamer.GStreamer;

public class RTPStreamer {
    private native void nativeInit();

    private native void nativeFinalize();

    private native static boolean nativeClassInit();

    private native boolean nativeStart();

    private native boolean nativeStop();

    private native boolean nativeSendRawData(int mediaType, long pts, byte[] data);

    private native void nativeSetStreamInfo(RTPStreamerSetting.StreamSetting info);

    private native void nativeUpdateMasterKey();

    private long native_custom_data;

    private RTPStreamerSetting.StreamSetting mStreamSetting;
    private final AtomicBoolean mIsInitialized;
    private final AtomicBoolean mIsStarted;
    private final ThreadWait<Boolean> mStartWait;

    private static final RTPStreamer instance = new RTPStreamer();

    static {
        System.loadLibrary("gstreamer_android");
        System.loadLibrary("gstreamer-appcast");
        nativeClassInit();
    }

    private RTPStreamer() {
        mIsInitialized = new AtomicBoolean(false);
        mIsStarted = new AtomicBoolean(false);
        mStartWait = new ThreadWait<>();
        AtomicBoolean mIsIFrameSent = new AtomicBoolean(false);
    }

    public static RTPStreamer getInstance() {
        return instance;
    }

    public void initialize(Context context) {
        Logger.print("initialize");

        try {
            if (mIsInitialized.get()) {
                Logger.error("ALREADY initialized...");
                return;
            }

            GStreamer.init(context);
            nativeInit();
            mIsInitialized.set(true);
        } catch (Exception e) {
            Logger.error(e);
            mIsInitialized.set(false);
        }
    }

    public void terminate() {
        Logger.print("terminate");

        if (!mIsInitialized.get()) {
            Logger.error("NOT initialized...");
            return;
        }

        nativeFinalize();
        mIsInitialized.set(false);
    }

    public void setStreamMode(RTPStreamerData.ProtocolType protocolType, RTPStreamerData.MediaType mediaType, long ssrc) {
        mStreamSetting = new RTPStreamerSetting.StreamSetting(protocolType, mediaType, ssrc);
    }

    public void setVideoConfig(RTPStreamerConfig.VideoConfig config) {
        try {
            if (config == null || config.bitrate <= 0 || config.framerate <= 0) throw new Exception("Invalid arguments");
            if (mStreamSetting == null || mStreamSetting.videoStreamInfo == null) throw new Exception("Invalid stream setting");
            mStreamSetting.videoStreamInfo.setVideoConfig(config);
        } catch (Exception e) {
            Logger.error(e);
        }
    }

    public void setAudioConfig(RTPStreamerConfig.AudioConfig config) {
        try {
            if (config == null) throw new Exception("Invalid arguments");
            if (mStreamSetting == null || mStreamSetting.audioStreamInfo == null) throw new Exception("Invalid stream setting");
            mStreamSetting.audioStreamInfo.setAudioConfig(config);
        } catch (Exception e) {
            Logger.error(e);
        }
    }

    public void setSecurityConfig(RTPStreamerConfig.SecurityConfig config) {
        try {
            if (config == null) throw new Exception("Invalid arguments");
            if (mStreamSetting == null || mStreamSetting.securityInfo == null) throw new Exception("Invalid stream setting");
            mStreamSetting.securityInfo.setSecurityConfig(config);
        } catch (Exception e) {
            Logger.error(e);
        }
    }

    public void setVideoDstToUri(String host, int port) {
        try {
            if (host == null) throw new Exception("Invalid arguments");
            if (mStreamSetting == null) throw new Exception("Invalid stream setting");
            mStreamSetting.setDestinationStream(RTPStreamerData.MediaType.VIDEO, host, port);
        } catch (Exception e) {
            Logger.error(e);
        }
    }

    public void setAudioDstToUri(String host, int port) {
        try {
            if (host == null) throw new Exception("Invalid arguments");
            if (mStreamSetting == null) throw new Exception("Invalid stream setting");
            mStreamSetting.setDestinationStream(RTPStreamerData.MediaType.AUDIO, host, port);
        } catch (Exception e) {
            Logger.error(e);
        }
    }

    public boolean start() {
        Logger.print("start");

        try {
            if (mIsStarted.get() == true) {
                Logger.error("ALREADY started...");
                return true;
            }

            if (mStreamSetting.audioStreamInfo != null && mStreamSetting.audioStreamInfo.isCompleted() == false) throw new Exception("Need to init audio info!!");
            if (mStreamSetting.videoStreamInfo != null && mStreamSetting.videoStreamInfo.isCompleted() == false) throw new Exception("Need to init video info!!");
            if (mStreamSetting.securityInfo != null && mStreamSetting.securityInfo.isCompleted() == false) throw new Exception("Need to init security info!!");

            nativeSetStreamInfo(mStreamSetting);
            nativeStart();

            if (mStartWait.waitFor(3000, false) == false) throw new Exception("Failed to start RTP streamer");
            mIsStarted.set(true);
            return true;
        } catch (Exception e) {
            Logger.error(e);
            mIsStarted.set(false);
            return false;
        }
    }

    public void stop() {
        Logger.print("stop");

        if (mIsStarted.get() == false) {
            Logger.error("NOT started...");
            return;
        }

        nativeStop();
        mIsStarted.set(false);
    }

    public void sendData(RTPStreamerData.MediaType mediaType, long nanosec, byte[] data) {
        if (data == null) {
            Logger.error("Invalid data");
            return;
        }

        /*if (mediaType == MediaType.VIDEO) RTPStreamerUtil.parseH264FrameInfo(data, nanosec);*/
        nativeSendRawData(mediaType.getType(), nanosec, data);

        /*if (mIsIFrameSent.get() == false) {
            if (mediaType == MediaType.VIDEO && (data[4] & 0x1F) == 0x05) {
                if (nativeSendRawData(mediaType.getType(), nanosec, data) == true) {
                    Logger.error("I frame sent successfully");
                    mIsIFrameSent.set(true);
                }
            }
        } else {
            nativeSendRawData(mediaType.getType(), nanosec, data);
        }*/
    }

    public void updateMasterKey() {
        Logger.print("updateMasterKey..");
        nativeUpdateMasterKey();
    }

    // JNI callback
    private void setMessage(final String message) {
        Logger.print("setMessage() called: ", message);
    }

    // JNI callback
    private void onGStreamerInitialized() {
        Logger.print("onGStreamerInitialized()");
        mStartWait.wakeUp(true);
    }
}
