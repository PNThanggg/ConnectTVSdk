package com.connect.service;

import android.util.SparseArray;

import com.connect.core.Util;
import com.connect.device.ConnectableDevice;
import com.connect.etc.helper.DeviceServiceReachability;
import com.connect.service.capability.CapabilityMethods;
import com.connect.service.capability.ExternalInputControl;
import com.connect.service.capability.Launcher;
import com.connect.service.capability.MediaPlayer;
import com.connect.service.capability.WebAppLauncher;
import com.connect.service.capability.listeners.ResponseListener;
import com.connect.service.command.ServiceCommand;
import com.connect.service.command.ServiceCommandError;
import com.connect.service.command.ServiceSubscription;
import com.connect.service.command.URLServiceSubscription;
import com.connect.service.config.ServiceConfig;
import com.connect.service.config.ServiceDescription;
import com.connect.service.sessions.LaunchSession;

import org.json.JSONException;
import org.json.JSONObject;

import java.lang.reflect.Constructor;
import java.lang.reflect.InvocationTargetException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.regex.Matcher;

public class DeviceService implements DeviceServiceReachability.DeviceServiceReachabilityListener, ServiceCommand.ServiceCommandProcessor {

    /**
     * Enumerates available pairing types. It is used by a DeviceService for implementing pairing
     * strategy.
     */
    public enum PairingType {
        /**
         * DeviceService doesn't require pairing
         */
        NONE,

        /**
         * In this mode user must confirm pairing on the first screen device (e.g. an alert on a TV)
         */
        FIRST_SCREEN,

        /**
         * In this mode user must enter a pin code from a mobile device and send it to the first
         * screen device
         */
        PIN_CODE,

        /**
         * In this mode user can either enter a pin code from a mobile device or confirm
         * pairing on the TV
         */
        MIXED,
    }

    // @cond INTERNAL
    public static final String KEY_CLASS = "class";
    public static final String KEY_CONFIG = "config";
    public static final String KEY_DESC = "description";

    protected PairingType pairingType = PairingType.NONE;

    protected ServiceDescription serviceDescription;

    protected ServiceConfig serviceConfig;

    protected DeviceServiceReachability mServiceReachability;

    protected boolean connected = false;

    private ServiceCommand.ServiceCommandProcessor commandProcessor;
    // @endcond

    /**
     * An array of capabilities supported by the DeviceService. This array may change based off a number of factors.
     * - DiscoveryManager's pairingLevel value
     * - Connect SDK framework version
     * - First screen device OS version
     * - First screen device configuration (apps installed, settings, etc)
     * - Physical region
     */
    List<String> mCapabilities;

    // @cond INTERNAL
    protected DeviceServiceListener listener;

    public SparseArray<ServiceCommand<?>> requests = new SparseArray<>();

    public DeviceService(ServiceDescription serviceDescription, ServiceConfig serviceConfig) {
        this.serviceDescription = serviceDescription;
        this.serviceConfig = serviceConfig;

        mCapabilities = new ArrayList<>();

        updateCapabilities();
    }

    public DeviceService(ServiceConfig serviceConfig) {
        this.serviceConfig = serviceConfig;

        mCapabilities = new ArrayList<>();

        updateCapabilities();
    }

    ServiceCommand.ServiceCommandProcessor getCommandProcessor() {
        return commandProcessor == null ? this : commandProcessor;
    }

    void setCommandProcessor(ServiceCommand.ServiceCommandProcessor commandProcessor) {
        this.commandProcessor = commandProcessor;
    }

    @SuppressWarnings("unchecked")
    public static DeviceService getService(JSONObject json) {
        Class<DeviceService> newServiceClass;

        try {
            String className = json.optString(KEY_CLASS);

            if (className.equalsIgnoreCase("DLNAService")) return null;

            if (className.equalsIgnoreCase("Chromecast")) return null;

            newServiceClass = (Class<DeviceService>) Class.forName(DeviceService.class.getPackage().getName() + "." + className);
            Constructor<DeviceService> constructor = newServiceClass.getConstructor(ServiceDescription.class, ServiceConfig.class);

            JSONObject jsonConfig = json.optJSONObject(KEY_CONFIG);
            ServiceConfig serviceConfig = null;
            if (jsonConfig != null) serviceConfig = ServiceConfig.getConfig(jsonConfig);

            JSONObject jsonDescription = json.optJSONObject(KEY_DESC);
            ServiceDescription serviceDescription = null;
            if (jsonDescription != null) serviceDescription = ServiceDescription.getDescription(jsonDescription);

            if (serviceConfig == null || serviceDescription == null) return null;

            return constructor.newInstance(serviceDescription, serviceConfig);
        } catch (ClassNotFoundException | NoSuchMethodException | IllegalArgumentException | InstantiationException |
                 IllegalAccessException | InvocationTargetException e) {
            e.printStackTrace();
        }

        return null;
    }

    public static DeviceService getService(Class<? extends DeviceService> clazz, ServiceConfig serviceConfig) {
        try {
            Constructor<? extends DeviceService> constructor = clazz.getConstructor(ServiceConfig.class);

            return constructor.newInstance(serviceConfig);
        } catch (NoSuchMethodException | IllegalArgumentException | InstantiationException | IllegalAccessException |
                 InvocationTargetException e) {
            e.printStackTrace();
        }

        return null;
    }

    public static DeviceService getService(Class<? extends DeviceService> clazz, ServiceDescription serviceDescription, ServiceConfig serviceConfig) {
        try {
            Constructor<? extends DeviceService> constructor = clazz.getConstructor(ServiceDescription.class, ServiceConfig.class);

            return constructor.newInstance(serviceDescription, serviceConfig);
        } catch (NoSuchMethodException | IllegalArgumentException | InstantiationException | IllegalAccessException |
                 InvocationTargetException e) {
            e.printStackTrace();
        }

        return null;
    }

    public PairingType getPairingType() {
        return pairingType;
    }

    public void setPairingType(PairingType pairingType) {
    }

    @SuppressWarnings("unchecked")
    public <T extends CapabilityMethods> T getAPI(Class<?> clazz) {
        if (clazz.isAssignableFrom(this.getClass())) {
            return (T) this;
        } else return null;
    }

    public CapabilityMethods.CapabilityPriorityLevel getPriorityLevel(Class<? extends CapabilityMethods> clazz) {
        return CapabilityMethods.CapabilityPriorityLevel.NOT_SUPPORTED;
    }

    public static <DiscoveryFilter> DiscoveryFilter discoveryFilter() {
        return null;
    }
    // @endcond

    /**
     * Will attempt to connect to the DeviceService. The failure/success will be reported back to the DeviceServiceListener. If the connection attempt reveals that pairing is required, the DeviceServiceListener will also be notified in that event.
     */
    public void connect() {

    }

    /**
     * Will attempt to disconnect from the DeviceService. The failure/success will be reported back to the DeviceServiceListener.
     */
    public void disconnect() {

    }

    /**
     * Whether the DeviceService is currently connected
     */
    public boolean isConnected() {
        return true;
    }

    public boolean isConnectable() {
        return false;
    }

    /**
     * Explicitly cancels pairing in services that require pairing. In some services, this will hide a prompt that is displaying on the device.
     */
    public void cancelPairing() {

    }

    protected void reportConnected(boolean ready) {
        if (listener == null) return;

        // only run callback on main thread if the callback is leaving the SDK
        if (listener instanceof ConnectableDevice) listener.onConnectionSuccess(this);
        else {
            Util.runOnUI(new Runnable() {
                @Override
                public void run() {
                    if (listener != null) listener.onConnectionSuccess(DeviceService.this);
                }
            });
        }
    }

    /**
     * Will attempt to pair with the DeviceService with the provided pairingData. The failure/success will be reported back to the DeviceServiceListener.
     *
     * @param pairingKey Data to be used for pairing. The type of this parameter will vary depending on what type of pairing is required, but is likely to be a string (pin code, pairing key, etc).
     */
    public void sendPairingKey(String pairingKey) {

    }

    // @cond INTERNAL

    public void unsubscribe(URLServiceSubscription<?> subscription) {

    }

    public void unsubscribe(ServiceSubscription<?> subscription) {

    }

    public void sendCommand(ServiceCommand<?> command) {

    }

    // @endcond

    public List<String> getCapabilities() {
        return mCapabilities;
    }

    protected void updateCapabilities() {
    }

    protected void setCapabilities(List<String> newCapabilities) {
        List<String> oldCapabilities = mCapabilities;

        mCapabilities = newCapabilities;

        List<String> _lostCapabilities = new ArrayList<String>();

        for (String capability : oldCapabilities) {
            if (!newCapabilities.contains(capability)) _lostCapabilities.add(capability);
        }

        List<String> _addedCapabilities = new ArrayList<String>();

        for (String capability : newCapabilities) {
            if (!oldCapabilities.contains(capability)) _addedCapabilities.add(capability);
        }

        final List<String> lostCapabilities = _lostCapabilities;
        final List<String> addedCapabilities = _addedCapabilities;

        if (this.listener != null) {
            Util.runOnUI(new Runnable() {

                @Override
                public void run() {
                    listener.onCapabilitiesUpdated(DeviceService.this, addedCapabilities, lostCapabilities);
                }
            });
        }
    }

    /**
     * Test to see if the capabilities array contains a given capability. See the individual Capability classes for acceptable capability values.
     * <p>
     * It is possible to append a wildcard search term `.Any` to the end of the search term. This method will return true for capabilities that match the term up to the wildcard.
     * <p>
     * Example: `Launcher.App.Any`
     *
     * @param capability Capability to test against
     */
    public boolean hasCapability(String capability) {
        Matcher m = CapabilityMethods.ANY_PATTERN.matcher(capability);

        if (m.find()) {
            String match = m.group();
            for (String item : this.mCapabilities) {
                if (item.contains(match)) {
                    return true;
                }
            }

            return false;
        }

        return mCapabilities.contains(capability);
    }

    /**
     * Test to see if the capabilities array contains at least one capability in a given set of capabilities. See the individual Capability classes for acceptable capability values.
     * <p>
     * See hasCapability: for a description of the wildcard feature provided by this method.
     *
     * @param capabilities Set of capabilities to test against
     */
    public boolean hasAnyCapability(String... capabilities) {
        for (String capability : capabilities) {
            if (hasCapability(capability)) return true;
        }

        return false;
    }

    /**
     * Test to see if the capabilities array contains a given set of capabilities. See the individual Capability classes for acceptable capability values.
     * <p>
     * See hasCapability: for a description of the wildcard feature provided by this method.
     *
     * @param capabilities List of capabilities to test against
     */
    public boolean hasCapabilities(List<String> capabilities) {
        String[] arr = new String[capabilities.size()];
        capabilities.toArray(arr);
        return hasCapabilities(arr);
    }

    /**
     * Test to see if the capabilities array contains a given set of capabilities. See the individual Capability classes for acceptable capability values.
     * <p>
     * See hasCapability: for a description of the wildcard feature provided by this method.
     *
     * @param capabilities Set of capabilities to test against
     */
    public boolean hasCapabilities(String... capabilities) {
        boolean hasCaps = true;

        for (String capability : capabilities) {
            if (!hasCapability(capability)) {
                hasCaps = false;
                break;
            }
        }

        return hasCaps;
    }

    // @cond INTERNAL
    public void setServiceDescription(ServiceDescription serviceDescription) {
        this.serviceDescription = serviceDescription;
    }
    // @endcond

    public ServiceDescription getServiceDescription() {
        return serviceDescription;
    }

    // @cond INTERNAL
    public void setServiceConfig(ServiceConfig serviceConfig) {
        this.serviceConfig = serviceConfig;
    }
    // @endcond

    public ServiceConfig getServiceConfig() {
        return serviceConfig;
    }

    public JSONObject toJSONObject() {
        JSONObject jsonObj = new JSONObject();

        try {
            jsonObj.put(KEY_CLASS, getClass().getSimpleName());
            jsonObj.put("description", serviceDescription.toJSONObject());
            jsonObj.put("config", serviceConfig.toJSONObject());
        } catch (JSONException e) {
            e.printStackTrace();
        }

        return jsonObj;
    }

    /**
     * Name of the DeviceService (webOS, Chromecast, etc)
     */
    public String getServiceName() {
        return serviceDescription.getServiceID();
    }

    // @cond INTERNAL

    /**
     * Create a LaunchSession from a serialized JSON object.
     * May return null if the session was not the one that created the session.
     * <p>
     * Intended for internal use.
     */
    public LaunchSession decodeLaunchSession(String type, JSONObject sessionObj) {
        return null;
    }

    public DeviceServiceListener getListener() {
        return listener;
    }

    public void setListener(DeviceServiceListener listener) {
        this.listener = listener;
    }
    // @endcond

    /**
     * Closes the session on the first screen device. Depending on the sessionType, the associated service will have different ways of handling the close functionality.
     *
     * @param launchSession LaunchSession to close
     * @param listener      (optional) listener to be called on success/failure
     */
    public void closeLaunchSession(LaunchSession launchSession, ResponseListener<Object> listener) {
        if (launchSession == null) {
            Util.postError(listener, new ServiceCommandError(0, "You must provide a valid LaunchSession", null));
            return;
        }

        DeviceService service = launchSession.getService();
        if (service == null) {
            Util.postError(listener, new ServiceCommandError(0, "There is no service attached to this launch session", null));
            return;
        }

        switch (launchSession.getSessionType()) {
            case App:
                if (service instanceof Launcher) ((Launcher) service).closeApp(launchSession, listener);
                break;
            case Media:
                if (service instanceof MediaPlayer) ((MediaPlayer) service).closeMedia(launchSession, listener);
                break;
            case ExternalInputPicker:
                if (service instanceof ExternalInputControl)
                    ((ExternalInputControl) service).closeInputPicker(launchSession, listener);
                break;
            case WebApp:
                if (service instanceof WebAppLauncher) ((WebAppLauncher) service).closeWebApp(launchSession, listener);
                break;
            case Unknown:
            default:
                Util.postError(listener, new ServiceCommandError(0, "This DeviceService does not know ho to close this LaunchSession", null));
                break;
        }
    }

    // @cond INTERNAL
    public void addCapability(final String capability) {
        if (capability == null || capability.isEmpty() || this.mCapabilities.contains(capability)) return;

        this.mCapabilities.add(capability);

        Util.runOnUI(() -> {
            List<String> added = new ArrayList<>();
            added.add(capability);

            if (listener != null) listener.onCapabilitiesUpdated(DeviceService.this, added, new ArrayList<>());
        });
    }

    public void addCapabilities(final List<String> capabilities) {
        if (capabilities == null) return;

        for (String capability : capabilities) {
            if (capability == null || capability.isEmpty() || mCapabilities.contains(capability)) continue;

            mCapabilities.add(capability);
        }

        Util.runOnUI(() -> {

            if (listener != null) listener.onCapabilitiesUpdated(DeviceService.this, capabilities, new ArrayList<String>());
        });
    }

    public void addCapabilities(String... capabilities) {
        addCapabilities(Arrays.asList(capabilities));
    }

    public void removeCapability(final String capability) {
        if (capability == null) return;

        this.mCapabilities.remove(capability);

        Util.runOnUI(() -> {
            List<String> removed = new ArrayList<>();
            removed.add(capability);

            if (listener != null) listener.onCapabilitiesUpdated(DeviceService.this, new ArrayList<>(), removed);
        });
    }

    public void removeCapabilities(final List<String> capabilities) {
        if (capabilities == null) return;

        for (String capability : capabilities) {
            mCapabilities.remove(capability);
        }

        Util.runOnUI(() -> {

            if (listener != null) listener.onCapabilitiesUpdated(DeviceService.this, new ArrayList<>(), capabilities);
        });
    }

    public void removeCapabilities(String... capabilities) {
        removeCapabilities(Arrays.asList(capabilities));
    }

    //  Unused by default.
    @Override
    public void onLoseReachability(DeviceServiceReachability reachability) {
    }
    // @endcond

    public interface DeviceServiceListener {

        /**
         * !
         * If the DeviceService requires an active connection (websocket, pairing, etc) this method will be called.
         *
         * @param service DeviceService that requires connection
         */
        void onConnectionRequired(DeviceService service);

        /**
         * !
         * After the connection has been successfully established, and after pairing (if applicable), this method will be called.
         *
         * @param service DeviceService that was successfully connected
         */
        void onConnectionSuccess(DeviceService service);

        /**
         * !
         * There are situations in which a DeviceService will update the capabilities it supports and propagate these changes to the DeviceService. Such situations include:
         * - on discovery, DIALService will reach out to detect if certain apps are installed
         * - on discovery, certain DeviceServices need to reach out for version & region information
         * <p>
         * For more information on this particular method, see ConnectableDeviceDelegate's connectableDevice:capabilitiesAdded:removed: method.
         *
         * @param service DeviceService that has experienced a change in capabilities
         * @param added   List<String> of capabilities that are new to the DeviceService
         * @param removed List<String> of capabilities that the DeviceService has lost
         */
        void onCapabilitiesUpdated(DeviceService service, List<String> added, List<String> removed);

        /**
         * !
         * This method will be called on any disconnection. If error is nil, then the connection was clean and likely triggered by the responsible DiscoveryProvider or by the user.
         *
         * @param service DeviceService that disconnected
         * @param error   Error with a description of any errors causing the disconnect. If this value is nil, then the disconnect was clean/expected.
         */
        void onDisconnect(DeviceService service, Error error);

        /**
         * !
         * Will be called if the DeviceService fails to establish a connection.
         *
         * @param service DeviceService which has failed to connect
         * @param error   Error with a description of the failure
         */
        void onConnectionFailure(DeviceService service, Error error);

        /**
         * !
         * If the DeviceService requires pairing, valuable data will be passed to the delegate via this method.
         *
         * @param service     DeviceService that requires pairing
         * @param pairingType PairingType that the DeviceService requires
         * @param pairingData Any data that might be required for the pairing process, will usually be nil
         */
        void onPairingRequired(DeviceService service, PairingType pairingType, Object pairingData);

        /**
         * !
         * This method will be called upon pairing success. On pairing success, a connection to the DeviceService will be attempted.
         *
         * @property service DeviceService that has successfully completed pairing
         */
        void onPairingSuccess(DeviceService service);

        /**
         * !
         * If there is any error in pairing, this method will be called.
         *
         * @param service DeviceService that has failed to complete pairing
         * @param error   Error with a description of the failure
         */
        void onPairingFailed(DeviceService service, Error error);
    }
}
