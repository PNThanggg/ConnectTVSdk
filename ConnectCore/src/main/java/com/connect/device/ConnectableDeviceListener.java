package com.connect.device;

import com.connect.service.DeviceService;
import com.connect.service.command.ServiceCommandError;

import java.util.List;

/**
 * ConnectableDeviceListener allows for a class to receive messages about ConnectableDevice connection, disconnect, and update events.
 *
 * It also serves as a proxy for message handling when connecting and pairing with each of a ConnectableDevice's DeviceServices. Each of the DeviceService proxy methods are optional and would only be useful in a few use cases.
 * - providing your own UI for the pairing process.
 * - interacting directly and exclusively with a single type of DeviceService
 */
public interface ConnectableDeviceListener {

    /**
     * A ConnectableDevice sends out a ready message when all of its connectable DeviceServices have been connected and are ready to receive commands.
     *
     * @param device ConnectableDevice that is ready for commands.
     */
    public void onDeviceReady(ConnectableDevice device);

    /**
     * When all of a ConnectableDevice's DeviceServices have become disconnected, the disconnected message is sent.
     *
     * @param device ConnectableDevice that has been disconnected.
     */
    public void onDeviceDisconnected(ConnectableDevice device);

    /**
     * DeviceService listener proxy method.
     *
     * This method is called when a DeviceService tries to connect and finds out that it requires pairing information from the user.
     *
     * @param device ConnectableDevice containing the DeviceService
     * @param service DeviceService that requires pairing
     * @param pairingType DeviceServicePairingType that the DeviceService requires
     */
    public void onPairingRequired(ConnectableDevice device, DeviceService service, DeviceService.PairingType pairingType);

    /**
     * When a ConnectableDevice finds & loses DeviceServices, that ConnectableDevice will experience a change in its collective capabilities list. When such a change occurs, this message will be sent with arrays of capabilities that were added & removed.
     *
     * This message will allow you to decide when to stop/start interacting with a ConnectableDevice, based off of its supported capabilities.
     *
     * @param device ConnectableDevice that has experienced a change in capabilities
     * @param added List<String> of capabilities that are new to the ConnectableDevice
     * @param removed List<String> of capabilities that the ConnectableDevice has lost
     */
    public void onCapabilityUpdated(ConnectableDevice device, List<String> added, List<String> removed);

    /**
     * This method is called when the connection to the ConnectableDevice has failed.
     *
     * @param device ConnectableDevice that has failed to connect
     * @param error ServiceCommandError with a description of the failure
     */
    public void onConnectionFailed(ConnectableDevice device, ServiceCommandError error);
}
