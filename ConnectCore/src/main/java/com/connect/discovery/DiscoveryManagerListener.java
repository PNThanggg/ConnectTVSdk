package com.connect.discovery;

import com.connect.device.ConnectableDevice;
import com.connect.service.command.ServiceCommandError;

/**
 * ###Overview
 * The DiscoveryManagerListener will receive events on the addition/removal/update of ConnectableDevice objects.
 * <p>
 * ###In Depth
 * It is important to note that, unless you are implementing your own device picker, this listener is not needed in your code. Connect SDK's DevicePicker internally acts a separate listener to the DiscoveryManager and handles all of the same method calls.
 */
public interface DiscoveryManagerListener {

    /**
     * This method will be fired upon the first discovery of one of a ConnectableDevice's DeviceServices.
     *
     * @param manager DiscoveryManager that found device
     * @param device ConnectableDevice that was found
     */
    void onDeviceAdded(DiscoveryManager manager, ConnectableDevice device);

    /**
     * This method is called when a ConnectableDevice gains or loses a DeviceService in discovery.
     *
     * @param manager DiscoveryManager that updated device
     * @param device ConnectableDevice that was updated
     */
    void onDeviceUpdated(DiscoveryManager manager, ConnectableDevice device);

    /**
     * This method is called when connections to all of a ConnectableDevice's DeviceServices are lost. This will usually happen when a device is powered off or loses internet connectivity.
     *
     * @param manager DiscoveryManager that lost device
     * @param device ConnectableDevice that was lost
     */
    void onDeviceRemoved(DiscoveryManager manager, ConnectableDevice device);

    /**
     * In the event of an error in the discovery phase, this method will be called.
     *
     * @param manager DiscoveryManager that experienced the error
     * @param error NSError with a description of the failure
     */
    void onDiscoveryFailed(DiscoveryManager manager, ServiceCommandError error);
}
