package com.connect.device;

public interface DevicePickerListener {
    void onPickDevice(ConnectableDevice device);

    void onPickDeviceFailed(boolean canceled);
}
