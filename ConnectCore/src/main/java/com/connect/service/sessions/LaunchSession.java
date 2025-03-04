package com.connect.service.sessions;

import com.connect.core.JSONDeserializable;
import com.connect.core.JSONSerializable;
import com.connect.service.DeviceService;
import com.connect.service.capability.listeners.ResponseListener;

import java.util.List;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

/**
 * Any time anything is launched onto a first screen device, there will be important session information that needs to be tracked. LaunchSession will track this data, and must be retained to perform certain actions within the session.
 */
public class LaunchSession implements JSONSerializable, JSONDeserializable {
    // @cond INTERNAL
    protected String appId;
    protected String appName;
    protected String sessionId;
    protected Object rawData;

    protected DeviceService service;
    protected LaunchSessionType sessionType;
    // @endcond

    /**
     * LaunchSession type is used to help DeviceService's know how to close a LunchSession.
     */
    public enum LaunchSessionType {
        /** Unknown LaunchSession type, may be unable to close this launch session */
        Unknown, 
        /** LaunchSession represents a launched app */
        App, 
        /** LaunchSession represents an external input picker that was launched */
        ExternalInputPicker, 
        /** LaunchSession represents a media app */
        Media, 
        /** LaunchSession represents a web app */
        WebApp
    }

    public LaunchSession() {
    }

    /**
     * Instantiates a LaunchSession object for a given app ID.
     *
     * @param appId System-specific, unique ID of the app
     */
    public static LaunchSession launchSessionForAppId(String appId) {
        LaunchSession launchSession = new LaunchSession();
        launchSession.appId = appId;

        return launchSession;
    }

    // @cond INTERNAL
    public static LaunchSession launchSessionFromJSONObject(JSONObject json) {
        LaunchSession launchSession = new LaunchSession();
        try {
            launchSession.fromJSONObject(json);
        } catch (JSONException e) {
            e.printStackTrace();
        }

        return launchSession;
    }
    // @endcond

    /** System-specific, unique ID of the app (ex. youtube.leanback.v4, 0000134, hulu) */
    public String getAppId() {
        return appId;
    }

    /**
     * Sets the system-specific, unique ID of the app (ex. youtube.leanback.v4, 0000134, hulu)
     * 
     * @param appId Id of the app
     */
    public void setAppId(String appId) {
        this.appId = appId;
    }

    /** User-friendly name of the app (ex. YouTube, Browser, Hulu) */
    public String getAppName() {
        return appName;
    }

    /**
     * Sets the user-friendly name of the app (ex. YouTube, Browser, Hulu)
     * 
     * @param appName Name of the app
     */
    public void setAppName(String appName) {
        this.appName = appName;
    }

    /** Unique ID for the session (only provided by certain protocols) */
    public String getSessionId() {
        return sessionId;
    }

    /**
     * Sets the session id (only provided by certain protocols)
     * 
     * @param sessionId Id of the current session
     */
    public void setSessionId(String sessionId) {
        this.sessionId = sessionId;
    }

    /** DeviceService responsible for launching the session. */
    public DeviceService getService() {
        return service;
    }

    /**
     * DeviceService responsible for launching the session.
     * 
     * @param service Sets the DeviceService
     */
    public void setService(DeviceService service) {
        this.service = service;
    }

    /** Raw data from the first screen device about the session. In most cases, this is a JSONObject. */
    public Object getRawData() {
        return rawData;
    }

    /**
     * Sets the raw data from the first screen device about the session. In most cases, this is a JSONObject.
     * 
     * @param rawData Sets the raw data
     */
    public void setRawData(Object rawData) {
        this.rawData = rawData;
    }

    /**
     * When closing a LaunchSession, the DeviceService relies on the sessionType to determine the method of closing the session.
     */
    public LaunchSessionType getSessionType() {
        return sessionType;
    }

    /**
     * Sets the LaunchSessionType of this LaunchSession.
     * 
     * @param sessionType The type of LaunchSession
     */
    public void setSessionType(LaunchSessionType sessionType) {
        this.sessionType = sessionType;
    }

    /**
     * Close the app/media associated with the session.
     * @param listener
     */
    public void close (ResponseListener<Object> listener) {
        service.closeLaunchSession(this, listener);
    }

    // @cond INTERNAl
    @Override
    public JSONObject toJSONObject() throws JSONException {
        JSONObject obj = new JSONObject();

        obj.putOpt("appId", appId);
        obj.putOpt("sessionId", sessionId);
        obj.putOpt("name", appName);
        obj.putOpt("sessionType", sessionType.name());
        if (service != null) obj.putOpt("serviceName", service.getServiceName());

        if (rawData != null) {
            if (rawData instanceof JSONObject) obj.putOpt("rawData", rawData);
            if (rawData instanceof List<?>) {
                JSONArray arr = new JSONArray();
                for (Object item : (List<?>) rawData)
                    arr.put(item);
                obj.putOpt("rawData", arr);
            }
            if (rawData instanceof Object[]) {
                JSONArray arr = new JSONArray();
                for (Object item : (Object[]) rawData)
                    arr.put(item);
                obj.putOpt("rawData", arr);
            }
            if (rawData instanceof String) obj.putOpt("rawData", rawData);
        }

        return obj;
    }

    @Override
    public void fromJSONObject(JSONObject obj) throws JSONException {
        this.appId = obj.optString("appId");
        this.sessionId = obj.optString("sessionId");
        this.appName = obj.optString("name");
        this.sessionType = LaunchSessionType.valueOf(obj.optString("sessionType"));
        this.rawData = obj.opt("rawData");
    }

    // @endcond

    /**
     * Compares two LaunchSession objects.
     *
     * @param launchSession LaunchSession object to compare.
     *
     * @return true if both LaunchSession id and sessionId values are equal
     */
    @Override
    public boolean equals(Object launchSession) {
        // TODO Auto-generated method stub
        return super.equals(launchSession);
    }
}
