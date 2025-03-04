package com.connect.service.webos.lgcast.remotecamera.service;

import android.app.Service;
import android.content.Intent;
import android.content.res.Configuration;
import android.os.IBinder;
import android.util.Size;
import android.view.Surface;

import androidx.annotation.NonNull;

import com.connect.core.R;
import com.connect.device.ConnectableDevice;
import com.connect.discovery.DiscoveryManager;
import com.connect.service.capability.RemoteCameraControl;
import com.connect.service.webos.lgcast.common.connection.ConnectionManager;
import com.connect.service.webos.lgcast.common.connection.ConnectionManagerError;
import com.connect.service.webos.lgcast.common.connection.ConnectionManagerListener;
import com.connect.service.webos.lgcast.common.connection.MobileDescription;
import com.connect.service.webos.lgcast.common.streaming.RTPStreaming;
import com.connect.service.webos.lgcast.common.transfer.RTPStreamerConfig;
import com.connect.service.webos.lgcast.common.utils.AppUtil;
import com.connect.service.webos.lgcast.common.utils.HandlerThreadEx;
import com.connect.service.webos.lgcast.common.utils.IOUtil;
import com.connect.service.webos.lgcast.common.utils.JSONObjectEx;
import com.connect.service.webos.lgcast.common.utils.Logger;
import com.connect.service.webos.lgcast.common.utils.StringUtil;
import com.connect.service.webos.lgcast.common.utils.ThreadUtil;
import com.connect.service.webos.lgcast.common.utils.TimerUtil;
import com.connect.service.webos.lgcast.remotecamera.RemoteCameraConfig;
import com.connect.service.webos.lgcast.remotecamera.capability.CameraSinkCapability;
import com.connect.service.webos.lgcast.remotecamera.capability.CameraSourceCapability;
import com.connect.service.webos.lgcast.remotecamera.capture.CameraCapture;
import com.connect.service.webos.lgcast.remotecamera.capture.MicCapture;

import org.json.JSONObject;

import java.util.concurrent.atomic.AtomicBoolean;

public class CameraService extends Service {
    private static final String CAMERA = "camera";
    private static final String VIDEO_PORT = "videoPort";
    private static final String AUDIO_PORT = "audioPort";

    public static final String RESULT = "result";
    public static final String ERROR_CAUSE = "errorCause";

    private HandlerThreadEx mServiceHandler;

    private ConnectionManager mConnectionManager;
    private CameraSinkCapability mCameraSinkCapability;
    private CameraSourceCapability mCameraSourceCapability;

    private RTPStreaming mRTPStreaming;
    private CameraCapture mCameraCapture;
    private MicCapture mMicCapture;

    private CameraProperty mCameraProperty;
    private final AtomicBoolean mIsPlaying = new AtomicBoolean();

    @Override
    public void onCreate() {
        super.onCreate();
        Logger.showDebug(true);
        mServiceHandler = new HandlerThreadEx("CameraService Handler");
        mServiceHandler.start();
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        mServiceHandler.quit();
        mServiceHandler = null;
    }

    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        Logger.print("onStartCommand: " + StringUtil.toString(intent));
        String action = (intent != null) ? intent.getAction() : null;

        mServiceHandler.post(() -> {
            if (CameraServiceIF.ACTION_START_REQUEST.equals(action)) executeStart(intent);
            else if (CameraServiceIF.ACTION_STOP_REQUEST.equals(action)) executeStop();
            else if (CameraServiceIF.ACTION_STOP_BY_NOTIFICATION.equals(action)) executeStopByNotification();
            else if (CameraServiceIF.ACTION_SET_MIC_MUTE.equals(action)) executeSetMicMute(intent);
            else if (CameraServiceIF.ACTION_SET_LENS_FACING.equals(action)) executeSetLensFacing(intent);
        });

        return START_STICKY;
    }

    @Override
    public void onConfigurationChanged(Configuration newConfig) {
        super.onConfigurationChanged(newConfig);
        if (mCameraProperty == null || mConnectionManager == null) return;

        mCameraProperty.rotation = AppUtil.getRotationDegree(getBaseContext());
        JSONObjectEx jsonObj = new JSONObjectEx().put(CameraProperty.ROTATION, mCameraProperty.rotation);
        mConnectionManager.updateSourceDeviceCapability(jsonObj.toJSONObject());
    }

    private void executeStart(@NonNull Intent intent) {
        ConnectionManagerListener connectionListener = new ConnectionManagerListener() {
            @Override
            public void onPairingRequested() {
                Logger.debug("onPairingRequested");
                CameraServiceIF.notifyPairing(getBaseContext());
            }

            @Override
            public void onPairingRejected() {
                Logger.debug("onPairingRejected");
                CameraServiceIF.respondStart(getBaseContext(), false);
                stop();
            }

            @Override
            public void onConnectionFailed(@NonNull String message) {
                Logger.debug("onConnectionFailed (%s)", message);
                CameraServiceIF.respondStart(getBaseContext(), false);
                stop();
            }

            @Override
            public void onConnectionCompleted(@NonNull JSONObject jsonObj) {
                Logger.debug("onConnectionCompleted");
                mCameraSinkCapability = new CameraSinkCapability(jsonObj);
                mCameraSinkCapability.debug();

                mCameraSourceCapability = CameraSourceCapability.create(getBaseContext(), mCameraSinkCapability.publicKey);
                mCameraSourceCapability.debug();

                MobileDescription mobileDescription = new MobileDescription(getBaseContext());
                mobileDescription.debug();

                mConnectionManager.setSourceDeviceCapability(mCameraSourceCapability.toJSONObject(), mobileDescription.toJSONObject());
                CameraServiceIF.respondStart(getBaseContext(), true);
            }

            @Override
            public void onReceivePlayCommand(@NonNull JSONObject jsonObj) {
                Logger.debug("onReceivePlayCommand");
                JSONObject cameraObj = jsonObj.optJSONObject(CAMERA);
                if (cameraObj == null) return;

                int width = cameraObj.optInt(CameraProperty.WIDTH, mCameraProperty.width);
                int height = cameraObj.optInt(CameraProperty.HEIGHT, mCameraProperty.height);
                int facing = cameraObj.optInt(CameraProperty.FACING, mCameraProperty.facing);

                boolean isSupportedResolution = false;
                Logger.debug("Before: width=%d, height=%d", width, height);

                for (Size s : mCameraSourceCapability.mSupportedPreviewSizes) {
                    if (width == s.getWidth() && height == s.getHeight()) isSupportedResolution = true;
                }

                width = (isSupportedResolution) ? width : RemoteCameraConfig.Properties.DEFAULT_WIDTH;
                height = (isSupportedResolution) ? height : RemoteCameraConfig.Properties.DEFAULT_HEIGHT;
                Logger.debug("After: width=%d, height=%d", width, height);

                if (width != mCameraProperty.width || height != mCameraProperty.height || facing != mCameraProperty.facing) {
                    mCameraProperty.width = width;
                    mCameraProperty.height = height;
                    mCameraProperty.facing = facing;

                    Logger.debug("Restarting with width=%d, height=%d, facing=%d", mCameraProperty.width, mCameraProperty.height, mCameraProperty.facing);
                    mCameraCapture.restartPreview(mCameraProperty);
                    ThreadUtil.sleep(10);
                } else {
                    Logger.debug("No changes in current preview");
                }

                if (cameraObj.has(VIDEO_PORT) && cameraObj.has(AUDIO_PORT)) {
                    startStreaming(cameraObj.optInt(VIDEO_PORT), cameraObj.optInt(AUDIO_PORT));
                    CameraServiceIF.notifyPlaying(getBaseContext());
                } else {
                    CameraServiceIF.notifyError(getBaseContext(), CameraServiceError.ERROR_GENERIC);
                }
            }

            @Override
            public void onReceiveStopCommand(@NonNull JSONObject jsonObj) {
                Logger.debug("onReceiveStopCommand");
                stopStreaming();
            }

            @Override
            public void onReceiveGetParameter(@NonNull JSONObject jsonObj) {
                Logger.debug("onReceiveGetParameter");
                mCameraProperty.rotation = AppUtil.getRotationDegree(getBaseContext());
                mConnectionManager.sendGetParameterResponse(mCameraProperty.toJSONObject());
            }

            @Override
            public void onReceiveSetParameter(@NonNull JSONObject jsonObj) {
                Logger.debug("onReceiveSetParameter - " + jsonObj);
                JSONObject cameraObj = jsonObj.optJSONObject(CAMERA);
                if (cameraObj == null) return;

                if (cameraObj.has(CameraProperty.BRIGHTNESS)) onChangeBrightness(cameraObj);
                else if (cameraObj.has(CameraProperty.WHITE_BALANCE)) onChangeWhiteBalance(cameraObj);
                else if (cameraObj.has(CameraProperty.AUTO_WHITE_BALANCE)) onChangeAutoWhiteBalance(cameraObj);
                else if (cameraObj.has(CameraProperty.AUDIO)) onChangeAudio(cameraObj);
                else if (cameraObj.has(CameraProperty.FACING)) onChangeFacing(cameraObj);
                else sendSetParameterResponse(false, cameraObj, RemoteCameraControl.RemoteCameraProperty.UNKNOWN);
            }

            @Override
            public void onError(@NonNull ConnectionManagerError connectionError, @NonNull String errorMessage) {
                Logger.error("onError: connectionError=%s, errorMessage=%s", connectionError, errorMessage);
                TimerUtil.schedule(() -> CameraServiceIF.notifyError(getBaseContext(), CameraServiceIF.toCameraError(connectionError)), 100);
                stop();
            }

            private void onChangeBrightness(@NonNull JSONObject cameraObj) {
                int brightness = cameraObj.optInt(CameraProperty.BRIGHTNESS);
                boolean result = mCameraCapture.changeBrightness(brightness);
                mCameraProperty.brightness = (result) ? brightness : mCameraProperty.brightness;
                sendSetParameterResponse(result, cameraObj, RemoteCameraControl.RemoteCameraProperty.BRIGHTNESS);
            }

            private void onChangeWhiteBalance(@NonNull JSONObject cameraObj) {
                int whiteBalance = cameraObj.optInt(CameraProperty.WHITE_BALANCE);
                boolean result = mCameraCapture.changeWhiteBalance(whiteBalance);

                if (result) {
                    mCameraCapture.changeAutoWhiteBalance(false);
                    mCameraProperty.autoWhiteBalance = false;
                    mCameraProperty.whiteBalance = whiteBalance;
                }

                sendSetParameterResponse(result, cameraObj, RemoteCameraControl.RemoteCameraProperty.WHITE_BALANCE);
            }

            private void onChangeAutoWhiteBalance(@NonNull JSONObject cameraObj) {
                boolean autoWhiteBalance = cameraObj.optBoolean(CameraProperty.AUTO_WHITE_BALANCE);
                boolean result = mCameraCapture.changeAutoWhiteBalance(autoWhiteBalance);
                mCameraProperty.autoWhiteBalance = (result) ? autoWhiteBalance : mCameraProperty.autoWhiteBalance;
                sendSetParameterResponse(result, cameraObj, RemoteCameraControl.RemoteCameraProperty.AUTO_WHITE_BALANCE);
            }

            private void onChangeAudio(@NonNull JSONObject cameraObj) {
                boolean audio = cameraObj.optBoolean(CameraProperty.AUDIO);
                boolean result = mMicCapture.changeMicMute(!audio);
                mCameraProperty.audio = (result) ? audio : mCameraProperty.audio;
                sendSetParameterResponse(result, cameraObj, RemoteCameraControl.RemoteCameraProperty.AUDIO);
            }

            private void onChangeFacing(@NonNull JSONObject cameraObj) {
                mCameraProperty.facing = cameraObj.optInt(CameraProperty.FACING);
                boolean result = mCameraCapture.restartPreview(mCameraProperty);
                sendSetParameterResponse(result, cameraObj, RemoteCameraControl.RemoteCameraProperty.LENS_FACING);
            }

            private void sendSetParameterResponse(boolean result, @NonNull JSONObject cameraObj, @NonNull RemoteCameraControl.RemoteCameraProperty property) {
                JSONObjectEx response = new JSONObjectEx(cameraObj);
                response.put(RESULT, result);

                if (result) { // If whiteBalance is set, response must have autoWhiteBalance with 'false'
                    if (property == RemoteCameraControl.RemoteCameraProperty.WHITE_BALANCE)
                        response.put(CameraProperty.AUTO_WHITE_BALANCE, false);
                    mConnectionManager.sendSetParameterResponse(response);
                    CameraServiceIF.notifyPropertyChange(getBaseContext(), property);
                } else {
                    Logger.error("Failed to change: " + property);
                    response.put(ERROR_CAUSE, "Failed to change: " + property);
                    mConnectionManager.sendSetParameterResponse(response);
                }
            }
        };

        Logger.print("executeStart");
        start(intent, connectionListener);
        AppUtil.showToastLong(this, "########## DEBUG version ##########");
    }

    private void executeStop() {
        Logger.print("executeStop");
        stop();
        CameraServiceIF.respondStop(this, true);
    }

    private void executeStopByNotification() {
        Logger.print("executeStopByNotification");
        stop();
        CameraServiceIF.notifyError(this, CameraServiceError.ERROR_STOPPED_BY_NOTIFICATION);
    }

    private void executeSetMicMute(@NonNull Intent intent) {
        Logger.print("executeSetMicMute");

        try {
            if (mMicCapture == null) throw new Exception("Invalid mic status");
            mCameraProperty.audio = !intent.getBooleanExtra(CameraServiceIF.EXTRA_MIC_MUTE, false);
            mMicCapture.changeMicMute(!mCameraProperty.audio);

            JSONObjectEx jsonObj = new JSONObjectEx().put(CameraProperty.AUDIO, mCameraProperty.audio);
            mConnectionManager.updateSourceDeviceCapability(jsonObj.toJSONObject());
            CameraServiceIF.notifyPropertyChange(getBaseContext(), RemoteCameraControl.RemoteCameraProperty.AUDIO);
        } catch (Exception e) {
            Logger.error(e);
            CameraServiceIF.notifyError(this, CameraServiceError.ERROR_GENERIC);
            stop();
        }
    }

    private void executeSetLensFacing(@NonNull Intent intent) {
        Logger.print("executeSetLensFacing");

        try {
            if (mCameraCapture == null) throw new Exception("Invalid camera status");
            mCameraProperty.facing = intent.getIntExtra(CameraServiceIF.EXTRA_LENS_FACING, CameraServiceIF.LENS_FACING_FRONT);
            mCameraCapture.restartPreview(mCameraProperty);

            JSONObjectEx jsonObj = new JSONObjectEx().put(CameraProperty.FACING, mCameraProperty.facing);
            mConnectionManager.updateSourceDeviceCapability(jsonObj.toJSONObject());
            CameraServiceIF.notifyPropertyChange(getBaseContext(), RemoteCameraControl.RemoteCameraProperty.LENS_FACING);
        } catch (Exception e) {
            Logger.error(e);
            CameraServiceIF.notifyError(this, CameraServiceError.ERROR_GENERIC);
            stop();
        }
    }

    ///////////////////////////////////////////////////////////////////////////////////////////////

    private void start(@NonNull Intent intent, ConnectionManagerListener connectionListener) {
        Logger.print("stop");

        try {
            int lensFacing = intent.getIntExtra(CameraServiceIF.EXTRA_LENS_FACING, CameraServiceIF.LENS_FACING_FRONT);
            boolean micMute = intent.getBooleanExtra(CameraServiceIF.EXTRA_MIC_MUTE, false);
            mCameraProperty = new CameraProperty();
            mCameraProperty.facing = lensFacing;
            mCameraProperty.audio = !micMute;
            mCameraProperty.debug();

            /*mIsPlaying = new AtomicBoolean();*/
            mIsPlaying.set(false);

            initializeService(intent);
            startCameraPreview(intent);
            startMicCapture(intent);
            openTvConnection(intent, connectionListener);
        } catch (Exception e) {
            Logger.error(e);
            CameraServiceIF.respondStart(this, false);
        }
    }

    private void stop() {
        Logger.print("stop");
        stopStreaming();

        closeTvConnection();
        stopMicCapture();
        stopCameraPreview();
        terminateService();
    }

    private void initializeService(@NonNull Intent intent) {
        Logger.print("initializeService (SDK version=%s)", IOUtil.readRawResourceText(this, R.raw.lgcast_version));
        startForeground(RemoteCameraConfig.Notification.ID, CameraServiceFunc.createNotification(this));
    }

    private void terminateService() {
        Logger.print("terminateService");
        stopForeground(true);
        ThreadUtil.runOnMainLooper(this::stopSelf, 150);
    }

    private void startCameraPreview(@NonNull Intent intent) throws Exception {
        Logger.print("startCameraPreview");
        CameraCapture.ErrorCallback errorCallback = error -> {
            CameraServiceIF.notifyError(CameraService.this, CameraServiceError.ERROR_GENERIC);
            stop();
        };

        Surface previewSurface = intent.getParcelableExtra(CameraServiceIF.EXTRA_PREVIEW_SURFACE);
        mCameraCapture = new CameraCapture(this, previewSurface, errorCallback);
        mCameraCapture.start(mCameraProperty);
    }

    private void stopCameraPreview() {
        Logger.print("stopCameraPreview");
        if (mCameraCapture != null) mCameraCapture.stop();
        mCameraCapture = null;
    }

    private void startMicCapture(@NonNull Intent intent) {
        Logger.print("startMicCapture");
        mMicCapture = new MicCapture();
        mMicCapture.startMicCapture(!mCameraProperty.audio);
    }

    private void stopMicCapture() {
        Logger.print("stopMicCapture");
        if (mMicCapture != null) mMicCapture.stopMicCapture();
        mMicCapture = null;
    }

    private void openTvConnection(@NonNull Intent intent, ConnectionManagerListener connectionListener) {
        Logger.print("openTvConnection");
        String deviceIpAddress = CameraServiceFunc.getDeviceIpAddress(intent);
        ConnectableDevice connectableDevice = DiscoveryManager.getInstance().getDeviceByIpAddress(deviceIpAddress);
        mConnectionManager = new ConnectionManager("camera");
        mConnectionManager.openConnection(connectableDevice, connectionListener);
    }

    private void closeTvConnection() {
        Logger.print("closeTvConnection");
        if (mConnectionManager != null) mConnectionManager.closeConnection();
        mConnectionManager = null;
    }

    private void startStreaming(int videoPort, int audioPort) {
        Logger.print("startStreaming");
        mIsPlaying.set(true);

        try {
            RTPStreamerConfig.VideoConfig videoConfig = CameraServiceFunc.createRtpVideoConfig(mCameraProperty.width, mCameraProperty.height);
            RTPStreamerConfig.AudioConfig audioConfig = CameraServiceFunc.createRtpAudioConfig();
            RTPStreamerConfig.SecurityConfig securityConfig = CameraServiceFunc.createRtpSecurityConfig(mCameraSourceCapability.masterKeys);

            mRTPStreaming = new RTPStreaming();
            mRTPStreaming.setStreamingConfig(videoConfig, audioConfig, securityConfig);
            mRTPStreaming.open(this, RemoteCameraConfig.RTP.SSRC, mCameraSinkCapability.ipAddress, videoPort, audioPort);

            mMicCapture.startStreaming(mRTPStreaming.getAudioStreamHandler());
            mCameraCapture.startStreaming(mRTPStreaming.getVideoStreamHandler());
        } catch (Exception e) {
            Logger.error(e);
            mIsPlaying.set(false);
        }
    }

    private void stopStreaming() {
        Logger.print("stopStreaming");
        mIsPlaying.set(false);

        if (mCameraCapture != null) mCameraCapture.stopStreaming();
        if (mMicCapture != null) mMicCapture.stopStreaming();

        if (mRTPStreaming != null) mRTPStreaming.close();
        mRTPStreaming = null;
    }
}
