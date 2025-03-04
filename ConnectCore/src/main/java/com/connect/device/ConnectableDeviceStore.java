package com.connect.device;

import com.connect.service.config.ServiceConfig;
import com.connect.service.config.ServiceDescription;

import org.json.JSONObject;

/**
 * ConnectableDeviceStore is a interface which can be implemented to save key information about ConnectableDevices that have been connected to.  Any class which implements this interface can be used as DiscoveryManager's deviceStore.
 * <p>
 * A default implementation, DefaultConnectableDeviceStore, will be used by DiscoveryManager if no other ConnectableDeviceStore is provided to DiscoveryManager when startDiscovery is called.
 * <p>
 * ###Privacy Considerations
 * If you chose to implement ConnectableDeviceStore, it is important to keep your users' privacy in mind.
 * - There should be UI elements in your app to
 * + completely disable ConnectableDeviceStore
 * + purge all data from ConnectableDeviceStore (removeAll)
 * - Your ConnectableDeviceStore implementation should
 * + avoid tracking too much data (indefinitely storing all discovered devices)
 * + periodically remove ConnectableDevices from the ConnectableDeviceStore if they haven't been used/connected in X amount of time
 */
public interface ConnectableDeviceStore {

    /**
     * Add a ConnectableDevice to the ConnectableDeviceStore. If the ConnectableDevice is already stored, it's record will be updated.
     *
     * @param device ConnectableDevice to add to the ConnectableDeviceStore
     */
    void addDevice(ConnectableDevice device);

    /**
     * Removes a ConnectableDevice's record from the ConnectableDeviceStore.
     *
     * @param device ConnectableDevice to remove from the ConnectableDeviceStore
     */
    void removeDevice(ConnectableDevice device);

    /**
     * Updates a ConnectableDevice's record in the ConnectableDeviceStore.
     *
     * @param device ConnectableDevice to update in the ConnectableDeviceStore
     */
    void updateDevice(ConnectableDevice device);

    /**
     * A JSONObject of all ConnectableDevices in the ConnectableDeviceStore. To gt a strongly-typed ConnectableDevice object, use the `getDevice(String);` method.
     */
    JSONObject getStoredDevices();

    /**
     * Gets a ConnectableDevice object for a provided id.  The id may be for the ConnectableDevice object or any of the DeviceServices.
     *
     * @param uuid Unique ID for a ConnectableDevice or any of its DeviceService objects
     * @return ConnectableDevice object if a matching uuit was found, otherwise will return null
     */
    ConnectableDevice getDevice(String uuid);

    /**
     * Gets a ServcieConfig object for a provided UUID.  This is used by DiscoveryManager to retain crucial service information between sessions (pairing code, etc).
     *
     * @param serviceDescription Description for the service
     * @return ServiceConfig object if matching description was found, otherwise will return null
     */
    ServiceConfig getServiceConfig(ServiceDescription serviceDescription);

    /**
     * Clears out the ConnectableDeviceStore, removing all records.
     */
    void removeAll();
}
