package com.connect.service.webos.lgcast.common.connection;

import android.os.Handler;

import com.connect.device.ConnectableDevice;
import com.connect.service.WebOSTVService;
import com.connect.service.capability.listeners.ResponseListener;
import com.connect.service.command.ServiceCommand;
import com.connect.service.command.ServiceCommandError;
import com.connect.service.command.URLServiceSubscription;
import com.connect.service.webos.lgcast.common.utils.JSONObjectEx;
import com.connect.service.webos.lgcast.common.utils.Logger;
import com.connect.service.webos.lgcast.common.utils.ThreadUtil;
import com.connect.service.webos.lgcast.common.utils.ThreadWait;

import org.json.JSONException;
import org.json.JSONObject;

public class LGCastCommand {
    private static final String KEY_SUBSCRIBE = "subscribe";
    private static final String KEY_CMD = "cmd";
    private static final String KEY_SERVICE = "service";
    private static final String KEY_DEVICEINFO = "deviceInfo";
    private static final String KEY_CLIENY_KEY = "clientKey";

    private static final String VAL_CONNECT = "CONNECT";
    private static final String VAL_GET_PARAMETER = "GET_PARAMETER";
    private static final String VAL_SET_PARAMETER = "SET_PARAMETER";
    private static final String VAL_GET_PARAMETER_RESPONSE = "GET_PARAMETER_RESPONSE"; // Remote Camera
    private static final String VAL_SET_PARAMETER_RESPONSE = "SET_PARAMETER_RESPONSE"; // Remote Camera
    private static final String VAL_PLAY = "PLAY";
    private static final String VAL_PAUSE = "PAUSE"; // Remote Camera
    private static final String VAL_KEEPALIVE = "KEEPALIVE";
    private static final String VAL_TEARDOWN = "TEARDOWN";

    private static final String SSAP_SEND_COMMAND = "ssap://com.webos.service.appcasting/sendCommand";
    private static final String SSAP_GET_COMMAND = "ssap://com.webos.service.appcasting/getCommand";
    private static final String SSAP_RECEIVE_UIBC = "ssap://com.webos.service.appcasting/receiveUIBCEvent"; // Screen Mirroring
    private static final String SSAP_GET_POWERSTATE = "ssap://com.webos.service.tvpower/power/getPowerState"; // Screen Mirroring

    private WebOSTVService mWebOSTVService;

    private LGCastCommand() {
    }

    public static LGCastCommand newInstance(ConnectableDevice connectableDevice) {
        WebOSTVService webOsService = (connectableDevice != null) ? (WebOSTVService) connectableDevice.getServiceByName(WebOSTVService.ID) : null;
        if (webOsService == null) return null;

        LGCastCommand wsc = new LGCastCommand();
        wsc.mWebOSTVService = webOsService;
        return wsc;
    }

    public String getClientKey() {
        return mWebOSTVService.getClientKey();
    }

    public void subscribeForServiceCommand(LGCastCommandListener commandListener, Handler responseHandler) {
        JSONObjectEx payload = new JSONObjectEx().put(KEY_SUBSCRIBE, true);
        subscribeToService(SSAP_GET_COMMAND, payload.toJSONObject(), commandListener, responseHandler);
    }

    public void subscribeForUserInput(LGCastCommandListener commandListener, Handler responseHandler) {
        JSONObjectEx payload = new JSONObjectEx().put(KEY_SUBSCRIBE, true);
        subscribeToService(SSAP_RECEIVE_UIBC, payload.toJSONObject(), commandListener, responseHandler);
    }

    public void subscribeForPowerState(LGCastCommandListener commandListener, Handler responseHandler) {
        JSONObjectEx payload = new JSONObjectEx().put(KEY_SUBSCRIBE, true);
        subscribeToService(SSAP_GET_POWERSTATE, payload.toJSONObject(), commandListener, responseHandler);
    }

    public boolean sendConnect(String featureName) {
        JSONObjectEx payload = new JSONObjectEx().put(KEY_CMD, VAL_CONNECT).put(KEY_SERVICE, featureName);
        ;
        return sendServiceCommand(payload.toJSONObject()) != null;
    }

    public JSONObject sendGetParameter(String featureName) {
        JSONObjectEx payload = new JSONObjectEx().put(KEY_CMD, VAL_GET_PARAMETER).put(KEY_SERVICE, featureName);
        return sendServiceCommand(payload.toJSONObject());
    }

    public void sendSetParameter(String name, JSONObject capability, JSONObject deviceInfo) {
        JSONObjectEx payload = new JSONObjectEx().put(KEY_CMD, VAL_SET_PARAMETER);
        if (name != null && capability != null) payload.put(name, capability);
        if (deviceInfo != null) payload.put(KEY_DEVICEINFO, deviceInfo);
        sendServiceCommand(payload.toJSONObject());
    }

    // Remote Camera
    public void sendGetParameterResponse(String name, JSONObject parameter) {
        JSONObjectEx payload = new JSONObjectEx().put(KEY_CMD, VAL_GET_PARAMETER_RESPONSE);
        if (name != null && parameter != null) payload.put(name, parameter);
        sendServiceCommand(payload.toJSONObject());
    }

    // Remote Camera
    public void sendSetParameterResponse(String name, JSONObject parameter) {
        JSONObjectEx payload = new JSONObjectEx().put(KEY_CMD, VAL_SET_PARAMETER_RESPONSE);
        if (name != null && parameter != null) payload.put(name, parameter);
        sendServiceCommand(payload.toJSONObject());
    }

    public void sendPlay() {
        JSONObjectEx payload = new JSONObjectEx().put(KEY_CMD, VAL_PLAY);
        sendServiceCommand(payload.toJSONObject());
    }

    // Remote Camera
    public void sendStop() {
        JSONObjectEx payload = new JSONObjectEx().put(KEY_CMD, VAL_PAUSE);
        sendServiceCommand(payload.toJSONObject());
    }

    public void sendKeepAlive() {
        JSONObjectEx payload = new JSONObjectEx().put(KEY_CMD, VAL_KEEPALIVE);
        sendServiceCommand(payload.toJSONObject());
    }

    public void sendTeardown(String featureName) {
        JSONObjectEx payload = new JSONObjectEx().put(KEY_CMD, VAL_TEARDOWN).put(KEY_SERVICE, featureName);
        sendServiceCommand(payload.toJSONObject());
    }

    private void subscribeToService(String ssapUrl, JSONObject payload, LGCastCommandListener commandListener, Handler responseHandler) {
        if (ThreadUtil.isMainThread()) throw new IllegalStateException("Invalid state");
        if (ssapUrl == null || payload == null) return;

        ResponseListener<Object> listener = new ResponseListener<Object>() {
            @Override
            public void onSuccess(Object object) {
                if (commandListener == null) return;
                if (responseHandler != null) responseHandler.post(() -> commandListener.onReceive((JSONObject) object));
                else ThreadUtil.runInBackground(() -> commandListener.onReceive((JSONObject) object));
            }

            @Override
            public void onError(ServiceCommandError error) {
                Logger.error("sendURLServiceSubscription error - " + error.getMessage());
                if (commandListener == null) return;
                ThreadUtil.runInBackground(() -> commandListener.onReceive(null));
            }
        };

        new URLServiceSubscription<ResponseListener<Object>>(mWebOSTVService, ssapUrl, payload, true, listener).send();
    }

    private JSONObject sendServiceCommand(JSONObject payload) {
        if (ThreadUtil.isMainThread()) throw new IllegalStateException("Invalid state");
        if (payload == null) return null;
        ThreadWait<JSONObject> threadWait = new ThreadWait<>();

        ResponseListener<Object> listener = new ResponseListener<Object>() {
            @Override
            public void onSuccess(Object object) {
                threadWait.wakeUp((JSONObject) object);
            }

            @Override
            public void onError(ServiceCommandError error) {
                Logger.error("sendServiceCommand error: payload=%s, error=%s", payload.toString(), error.getMessage());
                threadWait.wakeUp(null);
            }
        };

        try {
            payload.put(KEY_CLIENY_KEY, getClientKey());
        } catch (JSONException e) {
            Logger.error(e);
        }

        new ServiceCommand<>(mWebOSTVService, SSAP_SEND_COMMAND, payload, true, listener).send();
        return threadWait.waitFor(3000, null);
    }
}
