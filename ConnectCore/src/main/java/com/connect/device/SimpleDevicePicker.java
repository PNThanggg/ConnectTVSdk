package com.connect.device;

import java.util.List;

import android.app.Activity;
import android.app.Dialog;
import android.content.res.Resources;
import android.util.Log;
import android.widget.Toast;

import com.connect.core.Util;
import com.connect.service.DeviceService;
import com.connect.service.command.ServiceCommandError;

public class SimpleDevicePicker implements ConnectableDeviceListener {
    protected Activity activity;
    protected DevicePicker picker;
    protected Dialog pickerDialog;
    protected Dialog pairingDialog;

    // Device that we're in the process of connecting to
    protected ConnectableDevice pendingDevice;

    // Connected, active device
    protected ConnectableDevice activeDevice;

    protected int selectDeviceResId;
    protected int simplePairingTitleResId;
    protected int simplePairingPromptResId;
    protected int pinPairingPromptResId;
    protected int connectionFailedResId;

    SimpleDevicePickerListener listener;
    private DeviceService.PairingType pairingType;

    public SimpleDevicePicker(Activity activity) {
        this.activity = activity;
        this.picker = new DevicePicker(activity);

        loadStringIds();
    }

    public void setPairingType(DeviceService.PairingType pairingType) {
        this.pairingType = pairingType;
    }

    /**
     * Get the currently selected device
     * @return current connected device
     */
    public ConnectableDevice getCurrentDevice() {
        return activeDevice;
    }

    protected void loadStringIds() {
        selectDeviceResId = getStringId("connect_sdk_picker_select_device");
        simplePairingTitleResId = getStringId("connect_sdk_pairing_simple_title_tv");
        simplePairingPromptResId = getStringId("connect_sdk_pairing_simple_prompt_tv");
        pinPairingPromptResId = getStringId("connect_sdk_pairing_pin_prompt_tv");
        connectionFailedResId = getStringId("connect_sdk_connection_failed");
    }

    protected int getStringId(String key) {
        // First try to get resource from application
        int id = this.activity.getResources().getIdentifier(key, "string", activity.getPackageName());

        // Then try to get from Connect SDK library
        if (id == 0) {
            id = this.activity.getResources().getIdentifier(key, "string", "com.connectsdk");
        }

        if (id == 0) {
            Log.w("ConnectSDK", "missing string resource for \"" + key + "\"");

            throw new Resources.NotFoundException(key);
        }

        return id;
    }

    public void setListener(SimpleDevicePickerListener listener) {
        this.listener = listener;
    }

    protected void cleanupPending() {
        if (pendingDevice != null) {
            pendingDevice.removeListener(this);
            pendingDevice = null;
        }
    }

    protected void cleanupActive() {
        if (activeDevice != null) {
            activeDevice.removeListener(this);
            activeDevice = null;
        }
    }

    public void showPicker() {
        cleanupPending(); // remove any currently pending device
        hidePicker();

        pickerDialog = picker.getPickerDialog(activity.getString(selectDeviceResId), (adapter, view, pos, id) -> {
            ConnectableDevice device = (ConnectableDevice) adapter.getItemAtPosition(pos);

            selectDevice(device);
        });

        pickerDialog.show();
    }

    public void hidePicker() {
        if (pickerDialog != null) {
            pickerDialog.dismiss();
            pickerDialog = null;
        }
    }

    /**
     * Connect to a device
     * 
     * @param device
     */
    public void selectDevice(ConnectableDevice device) {
        if (device != null) {
            pendingDevice = device;
            pendingDevice.addListener(this);

            if (listener != null) {
                // Give listener a chance to setup device before connecting
                listener.onPrepareDevice(device);
            }

            if (!device.isConnected()) {
                device.setPairingType(pairingType);
                device.connect();
            } else {
                onDeviceReady(device);
            }
        } else {
            cleanupPending();
        }
    }

    protected Dialog createSimplePairingDialog() {
        PairingDialog dialog = new PairingDialog(activity, pendingDevice);
        return dialog.getSimplePairingDialog(simplePairingTitleResId, simplePairingPromptResId);
    }

    protected Dialog createPinPairingDialog() {
        PairingDialog dialog = new PairingDialog(activity, pendingDevice);
        return dialog.getPairingDialog(pinPairingPromptResId);
    }

    protected void showPairingDialog(DeviceService.PairingType pairingType) {
        switch (pairingType) { 
        case FIRST_SCREEN:
            pairingDialog = createSimplePairingDialog();
            break;

        case PIN_CODE:
        case MIXED:
            pairingDialog = createPinPairingDialog();
            break;

        case NONE:
        default:
            break;
        }

        if (pairingDialog != null) {
            pairingDialog.show();
        }
    }

    /**
     * Hide the current pairing dialog and cancels the pairing attempt.
     */
    public void hidePairingDialog() {
        // cancel pairing
        if (pairingDialog != null) {
            pairingDialog.dismiss();
            pairingDialog = null;
        }
    }


    @Override
    public void onDeviceReady(final ConnectableDevice device) {
        Util.runOnUI(() -> {
            hidePairingDialog();

            if (device == pendingDevice) {
                activeDevice = pendingDevice;

                if (listener != null) {
                    listener.onPickDevice(pendingDevice);
                }
                pendingDevice = null;
            }
        });
    }

    @Override
    public void onDeviceDisconnected(final ConnectableDevice device) {
        if (device == pendingDevice) {
            pickFailed(device);
        }

        if (device == activeDevice) {
            cleanupActive();
        }
    }

    @Override
    public void onCapabilityUpdated(ConnectableDevice device, List<String> added, List<String> removed) {
    }

    @Override
    public void onConnectionFailed(ConnectableDevice device, ServiceCommandError error) {
        if (device == pendingDevice) {
            pickFailed(device);
        }

        if (device == activeDevice) {
            cleanupActive();
        }
    }

    @Override
    public void onPairingRequired(ConnectableDevice device, DeviceService service, final DeviceService.PairingType pairingType) {
        Log.d("SimpleDevicePicker", "pairing required for device " + device.getFriendlyName());

        Util.runOnUI(new Runnable() {
            @Override
            public void run() {
                showPairingDialog(pairingType);
            }
        });
    }

    protected void pickFailed(final ConnectableDevice device) {
        Util.runOnUI(new Runnable() {
            @Override
            public void run() {
                if (pendingDevice == device) {
                    // Device failed before successfully picking device
                    if (listener != null) {
                        listener.onPickDeviceFailed(false);
                    }
                }

                cleanupPending();

                Toast.makeText(activity, connectionFailedResId, Toast.LENGTH_SHORT).show();
            }
        });
    }
}
