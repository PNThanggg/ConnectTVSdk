package com.connect.service.capability;

import android.app.ActivityManager;
import android.app.Presentation;
import android.content.Context;
import android.content.Intent;
import android.os.Build;
import android.provider.Settings;

import com.connect.core.R;
import com.connect.device.ConnectableDevice;
import com.connect.discovery.DiscoveryManager;
import com.connect.service.webos.lgcast.common.utils.AppUtil;
import com.connect.service.webos.lgcast.common.utils.IOUtil;
import com.connect.service.webos.lgcast.common.utils.StringUtil;
import com.connect.service.webos.lgcast.screenmirroring.service.MirroringService;
import com.connect.service.webos.lgcast.screenmirroring.uibc.UibcAccessibilityService;

import java.util.ArrayList;
import java.util.List;

public interface ScreenMirroringControl extends CapabilityMethods {
    String Any = "ScreenMirroringControl.Any";
    String ScreenMirroring = "ScreenMirroringControl.ScreenMirroring";
    String[] Capabilities = {ScreenMirroring};


    ///////////////////////////////////////////////////////////////////////////////////////////////
    // Static functions
    ///////////////////////////////////////////////////////////////////////////////////////////////
    static int getSdkVersion(Context context) {
        String version = IOUtil.readRawResourceText(context, R.raw.lgcast_version);
        return StringUtil.toInteger(version, -1);
    }

    static boolean isCompatibleOsVersion() {
        return Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q;
    }

    static boolean isRunning(Context context) {
        ActivityManager.RunningServiceInfo serviceInfo = AppUtil.getServiceInfo(context, MirroringService.class.getName());
        return serviceInfo != null && serviceInfo.foreground;
    }

    static boolean isSupportScreenMirroring(String deviceId) {
        ConnectableDevice dvc = DiscoveryManager.getInstance().getDeviceById(deviceId);
        List<String> capabilities = (dvc != null) ? dvc.getCapabilities() : new ArrayList<>();
        return capabilities.contains(ScreenMirroringControl.ScreenMirroring);
    }

    static boolean isUibcEnabled(Context context) {
        String prefString = Settings.Secure.getString(context.getContentResolver(), Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES);
        return prefString != null && prefString.contains(context.getPackageName() + "/" + UibcAccessibilityService.class.getName());
    }


    ///////////////////////////////////////////////////////////////////////////////////////////////
    // Interfaces
    ///////////////////////////////////////////////////////////////////////////////////////////////
    ScreenMirroringControl getScreenMirroringControl();

    void startScreenMirroring(Context context, Intent projectionData, ScreenMirroringStartListener onStartListener);

    void startScreenMirroring(Context context, Intent projectionData, Class secondScreenClass, ScreenMirroringStartListener onStartListener);

    void stopScreenMirroring(Context context, ScreenMirroringStopListener stopListener);

    void setErrorListener(Context context, ScreenMirroringErrorListener errorListener);


    ///////////////////////////////////////////////////////////////////////////////////////////////
    // Listeners
    ///////////////////////////////////////////////////////////////////////////////////////////////
    enum ScreenMirroringError {
        ERROR_GENERIC, ERROR_CONNECTION_CLOSED, ERROR_DEVICE_SHUTDOWN, ERROR_RENDERER_TERMINATED, ERROR_STOPPED_BY_NOTIFICATION;
    }

    interface ScreenMirroringStartListener {
        void onPairing();

        void onStart(boolean result, Presentation secondScreen);
    }

    interface ScreenMirroringStopListener {
        void onStop(boolean result);
    }

    interface ScreenMirroringErrorListener {
        void onError(ScreenMirroringError screenMirroringError);
    }
}
