package com.connect.service.capability;

import com.connect.core.AppInfo;
import com.connect.service.capability.listeners.ResponseListener;
import com.connect.service.command.ServiceSubscription;
import com.connect.service.sessions.LaunchSession;

import java.util.List;

public interface Launcher extends CapabilityMethods {
    String Any = "Launcher.Any";

    String Application = "Launcher.App";
    String Application_Params = "Launcher.App.Params";
    String Application_Close = "Launcher.App.Close";
    String Application_List = "Launcher.App.List";
    String Browser = "Launcher.Browser";
    String Browser_Params = "Launcher.Browser.Params";
    String Hulu = "Launcher.Hulu";
    String Hulu_Params = "Launcher.Hulu.Params";
    String Netflix = "Launcher.Netflix";
    String Netflix_Params = "Launcher.Netflix.Params";
    String YouTube = "Launcher.YouTube";
    String YouTube_Params = "Launcher.YouTube.Params";
    String AppStore = "Launcher.AppStore";
    String AppStore_Params = "Launcher.AppStore.Params";
    String AppState = "Launcher.AppState";
    String AppState_Subscribe = "Launcher.AppState.Subscribe";
    String RunningApp = "Launcher.RunningApp";
    String RunningApp_Subscribe = "Launcher.RunningApp.Subscribe";

    String[] Capabilities = {Application, Application_Params, Application_Close, Application_List, Browser, Browser_Params, Hulu, Hulu_Params, Netflix, Netflix_Params, YouTube, YouTube_Params, AppStore, AppStore_Params, AppState, AppState_Subscribe, RunningApp, RunningApp_Subscribe};

    Launcher getLauncher();

    public CapabilityPriorityLevel getLauncherCapabilityLevel();

    public void launchAppWithInfo(AppInfo appInfo, AppLaunchListener listener);

    public void launchAppWithInfo(AppInfo appInfo, Object params, AppLaunchListener listener);

    public void launchApp(String appId, AppLaunchListener listener);

    public void closeApp(LaunchSession launchSession, ResponseListener<Object> listener);

    public void getAppList(AppListListener listener);

    public void getRunningApp(AppInfoListener listener);

    public ServiceSubscription<AppInfoListener> subscribeRunningApp(AppInfoListener listener);

    public void getAppState(LaunchSession launchSession, AppStateListener listener);

    public ServiceSubscription<AppStateListener> subscribeAppState(LaunchSession launchSession, AppStateListener listener);

    public void launchBrowser(String url, AppLaunchListener listener);

    public void launchYouTube(String contentId, AppLaunchListener listener);

    public void launchYouTube(String contentId, float startTime, AppLaunchListener listener);

    public void launchNetflix(String contentId, AppLaunchListener listener);

    public void launchHulu(String contentId, AppLaunchListener listener);

    public void launchAppStore(String appId, AppLaunchListener listener);

    /**
     * Success listener that is called upon successfully launching an app.
     * <p>
     * Passes a LaunchSession Object containing important information about the app's launch session
     */
    public static interface AppLaunchListener extends ResponseListener<LaunchSession> {
    }

    /**
     * Success listener that is called upon requesting info about the current running app.
     * <p>
     * Passes an AppInfo object containing info about the running app
     */
    public static interface AppInfoListener extends ResponseListener<AppInfo> {
    }

    /**
     * Success block that is called upon successfully getting the app list.
     * <p>
     * Passes a List containing an AppInfo for each available app on the device
     */
    public static interface AppListListener extends ResponseListener<List<AppInfo>> {
    }

    // @cond INTERNAL
    public static interface AppCountListener extends ResponseListener<Integer> {
    }
    // @endcond

    /**
     * Success block that is called upon successfully getting an app's state.
     * <p>
     * Passes an AppState object which contains information about the running app.
     */
    interface AppStateListener extends ResponseListener<AppState> {
    }

    /**
     * Helper class used with the AppStateListener to return the current state of an app.
     */
    public static class AppState {
        /**
         * Whether the app is currently running.
         */
        public boolean running;
        /**
         * Whether the app is currently visible.
         */
        public boolean visible;

        public AppState(boolean running, boolean visible) {
            this.running = running;
            this.visible = visible;
        }
    }
}
