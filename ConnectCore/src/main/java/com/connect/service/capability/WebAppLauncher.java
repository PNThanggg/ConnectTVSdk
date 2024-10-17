package com.connect.service.capability;

import com.connect.service.capability.listeners.ResponseListener;
import com.connect.service.command.ServiceSubscription;
import com.connect.service.sessions.LaunchSession;
import com.connect.service.sessions.WebAppSession;

import org.json.JSONObject;

public interface WebAppLauncher extends CapabilityMethods {
    String Any = "WebAppLauncher.Any";

    String Launch = "WebAppLauncher.Launch";

    String Launch_Params = "WebAppLauncher.Launch.Params";

    String Message_Send = "WebAppLauncher.Message.Send";

    String Message_Receive = "WebAppLauncher.Message.Receive";

    String Message_Send_JSON = "WebAppLauncher.Message.Send.JSON";

    String Message_Receive_JSON = "WebAppLauncher.Message.Receive.JSON";

    String Connect = "WebAppLauncher.Connect";

    String Disconnect = "WebAppLauncher.Disconnect";

    String Join = "WebAppLauncher.Join";

    String Close = "WebAppLauncher.Close";

    String Pin = "WebAppLauncher.Pin";

    String[] Capabilities = {Launch, Launch_Params, Message_Send, Message_Receive, Message_Send_JSON, Message_Receive_JSON, Connect, Disconnect, Join, Close, Pin};

    WebAppLauncher getWebAppLauncher();

    CapabilityPriorityLevel getWebAppLauncherCapabilityLevel();

    void launchWebApp(String webAppId, WebAppSession.LaunchListener listener);

    void launchWebApp(String webAppId, boolean relaunchIfRunning, WebAppSession.LaunchListener listener);

    void launchWebApp(String webAppId, JSONObject params, WebAppSession.LaunchListener listener);

    void launchWebApp(String webAppId, JSONObject params, boolean relaunchIfRunning, WebAppSession.LaunchListener listener);

    void joinWebApp(LaunchSession webAppLaunchSession, WebAppSession.LaunchListener listener);

    void joinWebApp(String webAppId, MediaPlayer.LaunchListener listener);

    void closeWebApp(LaunchSession launchSession, ResponseListener<Object> listener);

    void pinWebApp(String webAppId, ResponseListener<Object> listener);

    void unPinWebApp(String webAppId, ResponseListener<Object> listener);

    void isWebAppPinned(String webAppId, WebAppSession.WebAppPinStatusListener listener);

    ServiceSubscription<WebAppSession.WebAppPinStatusListener> subscribeIsWebAppPinned(String webAppId, WebAppSession.WebAppPinStatusListener listener);
}
