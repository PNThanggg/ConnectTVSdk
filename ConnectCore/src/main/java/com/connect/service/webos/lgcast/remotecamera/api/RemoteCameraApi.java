package com.connect.service.webos.lgcast.remotecamera.api;

import android.Manifest;
import android.content.Context;
import android.content.pm.PackageManager;
import android.view.Surface;
import androidx.core.app.ActivityCompat;

import com.connect.service.capability.RemoteCameraControl;
import com.connect.service.webos.lgcast.common.utils.LocalBroadcastEx;
import com.connect.service.webos.lgcast.common.utils.Logger;
import com.connect.service.webos.lgcast.remotecamera.service.CameraServiceError;
import com.connect.service.webos.lgcast.remotecamera.service.CameraServiceIF;

public class RemoteCameraApi {
    private final LocalBroadcastEx mLocalBroadcastEx = new LocalBroadcastEx();

    private RemoteCameraApi() {
    }

    private static class LazyHolder {
        private static final RemoteCameraApi INSTANCE = new RemoteCameraApi();
    }

    public static RemoteCameraApi getInstance() {
        return LazyHolder.INSTANCE;
    }

    public void startRemoteCamera(Context context, Surface previewSurface, String deviceIpAddress, boolean micMute, int lensFacing, RemoteCameraControl.RemoteCameraStartListener startListener) {
        Logger.print("startRemoteCamera");

        try {
            if (context == null || deviceIpAddress == null) throw new Exception("Invalid arguments");
            if (!RemoteCameraControl.isCompatibleOsVersion()) throw new Exception("Incompatible OS version");
            if (RemoteCameraControl.isRunning(context)) throw new Exception("Remote Camera is ALREADY running");

            boolean hasCameraPermission = ActivityCompat.checkSelfPermission(context, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED;
            if (!hasCameraPermission) throw new Exception("Invalid camera permission");

            boolean hasAudioPermission = ActivityCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED;
            if (!hasAudioPermission) throw new Exception("Invalid audio permission");

            mLocalBroadcastEx.registerOnce(context, CameraServiceIF.ACTION_NOTIFY_PAIRING, intent -> {
                if (startListener != null) startListener.onPairing();
            });

            mLocalBroadcastEx.registerOnce(context, CameraServiceIF.ACTION_START_RESPONSE, intent -> {
                boolean result = intent.getBooleanExtra(CameraServiceIF.EXTRA_RESULT, false);
                if (startListener != null) startListener.onStart(result);
            });

            Logger.debug("Request start");
            CameraServiceIF.requestStart(context, previewSurface, deviceIpAddress, micMute, lensFacing);
        } catch (Exception e) {
            if (startListener != null) startListener.onStart(false);
        }
    }

    public void stopRemoteCamera(Context context, RemoteCameraControl.RemoteCameraStopListener stopListener) {
        Logger.print("stopRemoteCamera");

        try {
            if (context == null) throw new Exception("Invalid arguments");
            if (!RemoteCameraControl.isRunning(context)) throw new Exception("Remote Camera is NOT running");

            mLocalBroadcastEx.registerOnce(context, CameraServiceIF.ACTION_STOP_RESPONSE, intent -> {
                boolean result = intent.getBooleanExtra(CameraServiceIF.EXTRA_RESULT, false);
                if (stopListener != null) stopListener.onStop(result);
                mLocalBroadcastEx.unregisterAll(context);
            });

            Logger.debug("Request stop");
            CameraServiceIF.requestStop(context);
        } catch (Exception e) {
            if (stopListener != null) stopListener.onStop(false);
        }
    }

    public void setMicMute(Context context, boolean micMute) {
        Logger.print("setMicMute (micMute=%s)", micMute);
        if (RemoteCameraControl.isRunning(context)) CameraServiceIF.setMicMute(context, micMute);
        else Logger.error("Remote camera is NOT running");
    }

    public void setLensFacing(Context context, int lensFacing) {
        Logger.print("setLensFacing (lensFacing=%d)", lensFacing);
        if (RemoteCameraControl.isRunning(context)) CameraServiceIF.setLensFacing(context, lensFacing);
        else Logger.error("Remote camera is NOT running");
    }

    public void setCameraPlayingListener(Context context, RemoteCameraControl.RemoteCameraPlayingListener playingListener) {
        mLocalBroadcastEx.registerOnce(context, CameraServiceIF.ACTION_NOTIFY_PLAYING, intent -> {
            if (playingListener != null) playingListener.onPlaying();
        });
    }

    public void setPropertyChangeListener(Context context, RemoteCameraControl.RemoteCameraPropertyChangeListener propertyChangeListener) {
        mLocalBroadcastEx.register(context, CameraServiceIF.ACTION_NOTIFY_PROPERTY_CHANGE, intent -> {
            RemoteCameraControl.RemoteCameraProperty property = (RemoteCameraControl.RemoteCameraProperty) intent.getSerializableExtra(CameraServiceIF.EXTRA_PROPERTY);
            if (propertyChangeListener != null) propertyChangeListener.onChange(property);
        });
    }

    public void setErrorListener(Context context, RemoteCameraControl.RemoteCameraErrorListener errorListener) {
        mLocalBroadcastEx.register(context, CameraServiceIF.ACTION_NOTIFY_ERROR, intent -> {
            CameraServiceError serviceError = (CameraServiceError) intent.getSerializableExtra(CameraServiceIF.EXTRA_ERROR);
            if (errorListener != null) errorListener.onError(toRemoteCameraError(serviceError));
        });
    }

    private RemoteCameraControl.RemoteCameraError toRemoteCameraError(CameraServiceError serviceError) {
        if (serviceError == CameraServiceError.ERROR_CONNECTION_CLOSED) return RemoteCameraControl.RemoteCameraError.ERROR_CONNECTION_CLOSED;
        if (serviceError == CameraServiceError.ERROR_DEVICE_SHUTDOWN) return RemoteCameraControl.RemoteCameraError.ERROR_DEVICE_SHUTDOWN;
        if (serviceError == CameraServiceError.ERROR_RENDERER_TERMINATED) return RemoteCameraControl.RemoteCameraError.ERROR_RENDERER_TERMINATED;
        if (serviceError == CameraServiceError.ERROR_STOPPED_BY_NOTIFICATION) return RemoteCameraControl.RemoteCameraError.ERROR_STOPPED_BY_NOTIFICATION;
        return RemoteCameraControl.RemoteCameraError.ERROR_GENERIC;
    }
}