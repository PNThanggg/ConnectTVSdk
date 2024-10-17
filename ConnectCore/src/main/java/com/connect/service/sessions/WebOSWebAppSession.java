package com.connect.service.sessions;

import android.util.Log;

import androidx.annotation.NonNull;

import com.connect.core.ImageInfo;
import com.connect.core.MediaInfo;
import com.connect.core.SubtitleInfo;
import com.connect.core.Util;
import com.connect.service.DeviceService;
import com.connect.service.WebOSTVService;
import com.connect.service.capability.MediaControl;
import com.connect.service.capability.MediaPlayer;
import com.connect.service.capability.PlaylistControl;
import com.connect.service.capability.listeners.ResponseListener;
import com.connect.service.command.ServiceCommand;
import com.connect.service.command.ServiceCommandError;
import com.connect.service.command.ServiceSubscription;
import com.connect.service.command.URLServiceSubscription;
import com.connect.service.webos.WebOSTVServiceSocketClient;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.util.Enumeration;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.ConcurrentHashMap;

public class WebOSWebAppSession extends WebAppSession {
    private static final String namespaceKey = "connectsdk.";
    private static final String ENABLED_SUBTITLE_ID = "1";

    protected WebOSTVService service;

    ResponseListener<ServiceCommand<ResponseListener<Object>>> mConnectionListener;

    public WebOSTVServiceSocketClient socket;
    public URLServiceSubscription<ResponseListener<Object>> appToAppSubscription;

    private ServiceSubscription<PlayStateListener> mPlayStateSubscription;
    private ServiceSubscription<MessageListener> mMessageSubscription;
    private final ConcurrentHashMap<String, ServiceCommand<?>> mActiveCommands;

    private ServiceSubscription<WebAppPinStatusListener> mWebAppPinnedSubscription;

    String mFullAppId;

    private int UID;
    private boolean connected;

    public WebOSWebAppSession(LaunchSession launchSession, DeviceService service) {
        super(launchSession, service);

        UID = 0;
        mActiveCommands = new ConcurrentHashMap<>(0, 0.75f, 10);
        connected = false;

        this.service = (WebOSTVService) service;
    }

    private int getNextId() {
        return ++UID;
    }

    public Boolean isConnected() {
        return connected && socket != null && socket.isConnected();
    }

    public void setConnected(Boolean connected) {
        this.connected = connected;
    }

    public void handleMediaEvent(JSONObject payload) {
        String type = payload.optString("type");
        if (type.isEmpty()) {
            String errorMsg = payload.optString("error");

            if (errorMsg.isEmpty()) {
                return;
            } else {
                Log.w(Util.T, "Play State Error: " + errorMsg);
                if (mPlayStateSubscription != null) {
                    for (PlayStateListener listener : mPlayStateSubscription.getListeners()) {
                        Util.postError(listener, new ServiceCommandError(errorMsg));
                    }
                }
            }
        }

        if (type.equals("playState")) {
            if (mPlayStateSubscription == null) return;

            String playStateString = payload.optString(type);
            if (playStateString.isEmpty()) return;

            final MediaControl.PlayStateStatus playState = parsePlayState(playStateString);

            for (PlayStateListener listener : mPlayStateSubscription.getListeners()) {
                Util.postSuccess(listener, playState);
            }
        }
    }

    public String getFullAppId() {
        if (mFullAppId == null) {
            if (launchSession.getSessionType() != LaunchSession.LaunchSessionType.WebApp) mFullAppId = launchSession.getAppId();
            else {
                Enumeration<String> enumeration = service.getWebAppIdMappings().keys();

                while (enumeration.hasMoreElements()) {
                    String mappedFullAppId = enumeration.nextElement();
                    String mappedAppId = service.getWebAppIdMappings().get(mappedFullAppId);

                    if (mappedAppId.equalsIgnoreCase(launchSession.getAppId())) {
                        mFullAppId = mappedAppId;
                        break;
                    }
                }
            }
        }

        if (mFullAppId == null) return launchSession.getAppId();
        else return mFullAppId;
    }

    public void setFullAppId(String fullAppId) {
        mFullAppId = fullAppId;
    }

    private final WebOSTVServiceSocketClient.WebOSTVServiceSocketClientListener mSocketListener = new WebOSTVServiceSocketClient.WebOSTVServiceSocketClientListener() {

        @Override
        public void onRegistrationFailed(ServiceCommandError error) {
        }

        @Override
        public Boolean onReceiveMessage(JSONObject payload) {
            String type = payload.optString("type");

            if ("p2p".equals(type)) {
                String fromAppId = payload.optString("from");

                if (!fromAppId.equalsIgnoreCase(getFullAppId())) return false;

                Object message = payload.opt("payload");

                if (message instanceof JSONObject) {
                    JSONObject messageJSON = (JSONObject) message;

                    String contentType = messageJSON.optString("contentType");
                    int contentTypeIndex = contentType.indexOf("connectsdk.");

                    if (contentTypeIndex >= 0) {
                        String payloadKey = contentType.split("connectsdk.")[1];

                        if (payloadKey == null || payloadKey.isEmpty()) return false;

                        JSONObject messagePayload = messageJSON.optJSONObject(payloadKey);

                        if (payloadKey.equalsIgnoreCase("media-error")) {
                            handleMediaEvent(messageJSON);
                            return false;
                        }

                        if (messagePayload == null) return false;

                        if (payloadKey.equalsIgnoreCase("mediaEvent")) handleMediaEvent(messagePayload);
                        else if (payloadKey.equalsIgnoreCase("mediaCommandResponse")) handleMediaCommandResponse(messagePayload);
                    } else {
                        handleMessage(messageJSON);
                    }
                } else if (message instanceof String) {
                    handleMessage(message);
                }

                return false;
            }

            return true;
        }

        @Override
        public void updateClientKey(String ClientKey) {

        }

        @Override
        public void updateUUID(String UUID) {

        }

        @Override
        public void updateIPAddress(String IPAddress) {

        }

        @Override
        public void onFailWithError(ServiceCommandError error) {
            connected = false;
            appToAppSubscription = null;

            if (mConnectionListener != null) {
                if (error == null) {
                    error = new ServiceCommandError(0, "Unknown error connecting to web socket", null);
                }

                mConnectionListener.onError(error);
            }

            mConnectionListener = null;
        }

        @Override
        public void onConnect() {
            if (mConnectionListener != null) mConnectionListener.onSuccess(null);

            mConnectionListener = null;
        }

        @Override
        public void onCloseWithError(ServiceCommandError error) {
            connected = false;
            appToAppSubscription = null;

            if (mConnectionListener != null) {
                if (error != null) mConnectionListener.onError(error);
                else {
                    if (getWebAppSessionListener() != null) {
                        getWebAppSessionListener().onWebAppSessionDisconnect(WebOSWebAppSession.this);
                    }
                }
            }

            mConnectionListener = null;
        }

        @Override
        public void onBeforeRegister(DeviceService.PairingType pairingType) {
        }
    };

    @SuppressWarnings("unchecked")
    public void handleMediaCommandResponse(final JSONObject payload) {
        String requestID = payload.optString("requestId");
        if (requestID.isEmpty()) return;

        final ServiceCommand<ResponseListener<Object>> command = (ServiceCommand<ResponseListener<Object>>) mActiveCommands.get(requestID);

        if (command == null) return;

        String mError = payload.optString("error");

        if (mError.length() != 0) {
            Util.postError(command.getResponseListener(), new ServiceCommandError(0, mError, null));
        } else {
            Util.postSuccess(command.getResponseListener(), payload);
        }

        mActiveCommands.remove(requestID);
    }

    public void handleMessage(final Object message) {
        Util.runOnUI(new Runnable() {

            @Override
            public void run() {
                if (getWebAppSessionListener() != null)
                    getWebAppSessionListener().onReceiveMessage(WebOSWebAppSession.this, message);
            }
        });

    }

    public PlayStateStatus parsePlayState(String playStateString) {
        if (playStateString.equals("playing")) return PlayStateStatus.Playing;
        else if (playStateString.equals("paused")) return PlayStateStatus.Paused;
        else if (playStateString.equals("idle")) return PlayStateStatus.Idle;
        else if (playStateString.equals("buffering")) return PlayStateStatus.Buffering;
        else if (playStateString.equals("finished")) return PlayStateStatus.Finished;

        return PlayStateStatus.Unknown;
    }

    public void connect(ResponseListener<Object> connectionListener) {
        connect(false, connectionListener);
    }

    @Override
    public void join(ResponseListener<Object> connectionListener) {
        connect(true, connectionListener);
    }

    private void connect(final Boolean joinOnly, final ResponseListener<Object> connectionListener) {
        if (socket != null && socket.getState() == WebOSTVServiceSocketClient.State.CONNECTING) {
            if (connectionListener != null) {
                connectionListener.onError(new ServiceCommandError(0, "You have a connection request pending,  please wait until it has finished", null));
            }

            return;
        }

        if (isConnected()) {
            if (connectionListener != null) connectionListener.onSuccess(null);

            return;
        }

        mConnectionListener = new ResponseListener<ServiceCommand<ResponseListener<Object>>>() {

            @Override
            public void onError(ServiceCommandError error) {
                if (socket != null) disconnectFromWebApp();

                if (connectionListener != null) {
                    if (error == null) {
                        error = new ServiceCommandError(0, "Unknown error connecting to web app", null);
                    }

                    connectionListener.onError(error);
                }
            }

            @Override
            public void onSuccess(ServiceCommand<ResponseListener<Object>> object) {
                ResponseListener<Object> finalConnectionListener = new ResponseListener<Object>() {

                    @Override
                    public void onError(ServiceCommandError error) {
                        disconnectFromWebApp();

                        if (connectionListener != null) connectionListener.onError(error);
                    }

                    @Override
                    public void onSuccess(Object object) {
                        connected = true;

                        if (connectionListener != null) connectionListener.onSuccess(object);
                    }
                };

                service.connectToWebApp(WebOSWebAppSession.this, joinOnly, finalConnectionListener);
            }
        };

        if (socket != null) {
            if (socket.isConnected()) {
                mConnectionListener.onSuccess(null);
            } else {
                socket.connect();
            }
        } else {
            socket = new WebOSTVServiceSocketClient(service, WebOSTVServiceSocketClient.getURI(service));
            socket.setListener(mSocketListener);
            socket.connect();
        }
    }

    public void disconnectFromWebApp() {
        connected = false;
        mConnectionListener = null;

        if (appToAppSubscription != null) {
            appToAppSubscription.removeListeners();
            appToAppSubscription = null;
        }

        try {
            if (socket != null) {
                socket.setListener(null);
                socket.clearRequests();
                socket.disconnect();
                socket = null;
            }
        } catch (NullPointerException e) {
            e.printStackTrace();
        }
    }

    @Override
    public void sendMessage(final String message, final ResponseListener<Object> listener) {
        if (message == null || message.length() == 0) {
            Util.postError(listener, new ServiceCommandError(0, "Cannot send an Empty Message", null));
            return;
        }

        sendP2PMessage(message, listener);
    }

    @Override
    public void sendMessage(final JSONObject message, final ResponseListener<Object> listener) {
        if (message == null || message.length() == 0) {
            Util.postError(listener, new ServiceCommandError(0, "Cannot send an Empty Message", null));
            return;
        }

        sendP2PMessage(message, listener);
    }

    private void sendP2PMessage(final Object message, final ResponseListener<Object> listener) {
        JSONObject _payload = new JSONObject();

        try {
            _payload.put("type", "p2p");
            _payload.put("to", getFullAppId());
            _payload.put("payload", message);
        } catch (JSONException ex) {
            // do nothing
        }

        if (isConnected()) {
            socket.sendMessage(_payload, null);

            Util.postSuccess(listener, null);
        } else {
            ResponseListener<Object> connectListener = new ResponseListener<Object>() {

                @Override
                public void onError(ServiceCommandError error) {
                    Util.postError(listener, error);
                }

                @Override
                public void onSuccess(Object object) {
                    sendP2PMessage(message, listener);
                }
            };

            connect(connectListener);
        }
    }

    @Override
    public void close(ResponseListener<Object> listener) {
        mActiveCommands.clear();

        if (mPlayStateSubscription != null) {
            mPlayStateSubscription.unsubscribe();
            mPlayStateSubscription = null;
        }

        if (mMessageSubscription != null) {
            mMessageSubscription.unsubscribe();
            mMessageSubscription = null;
        }

        if (mWebAppPinnedSubscription != null) {
            mWebAppPinnedSubscription.unsubscribe();
            mWebAppPinnedSubscription = null;
        }

        service.getWebAppLauncher().closeWebApp(launchSession, listener);
    }

    @Override
    public void pinWebApp(String webAppId, ResponseListener<Object> listener) {
        service.getWebAppLauncher().pinWebApp(webAppId, listener);
    }

    @Override
    public void unPinWebApp(String webAppId, ResponseListener<Object> listener) {
        service.getWebAppLauncher().unPinWebApp(webAppId, listener);
    }

    @Override
    public void isWebAppPinned(String webAppId, WebAppPinStatusListener listener) {
        service.getWebAppLauncher().isWebAppPinned(webAppId, listener);
    }

    @Override
    public ServiceSubscription<WebAppPinStatusListener> subscribeIsWebAppPinned(String webAppId, WebAppPinStatusListener listener) {
        mWebAppPinnedSubscription = service.getWebAppLauncher().subscribeIsWebAppPinned(webAppId, listener);
        return mWebAppPinnedSubscription;
    }

    @Override
    public void seek(final long position, ResponseListener<Object> listener) {
        if (position < 0) {
            Util.postError(listener, new ServiceCommandError(0, "Must pass a valid positive value", null));
            return;
        }

        int requestIdNumber = getNextId();
        final String requestId = String.format(Locale.US, "req%d", requestIdNumber);

        JSONObject message = null;
        try {
            message = new JSONObject() {
                {
                    put("contentType", namespaceKey + "mediaCommand");
                    put("mediaCommand", new JSONObject() {
                        {
                            put("type", "seek");
                            put("position", position / 1000);
                            put("requestId", requestId);
                        }
                    });
                }
            };
        } catch (JSONException e) {
            Util.postError(listener, new ServiceCommandError(0, "JSON Parse error", null));
        }

        ServiceCommand<ResponseListener<Object>> command = new ServiceCommand<ResponseListener<Object>>(null, null, null, listener);

        mActiveCommands.put(requestId, command);

        sendMessage(message, listener);
    }

    @Override
    public void getPosition(final PositionListener listener) {
        int requestIdNumber = getNextId();
        final String requestId = String.format(Locale.US, "req%d", requestIdNumber);

        JSONObject message = null;
        try {
            message = new JSONObject() {
                {
                    put("contentType", namespaceKey + "mediaCommand");
                    put("mediaCommand", new JSONObject() {
                        {
                            put("type", "getPosition");
                            put("requestId", requestId);
                        }
                    });
                }
            };
        } catch (JSONException e) {
            Util.postError(listener, new ServiceCommandError(0, "JSON Parse error", null));
        }

        ServiceCommand<ResponseListener<Object>> command = new ServiceCommand<ResponseListener<Object>>(null, null, null, new ResponseListener<Object>() {

            @Override
            public void onSuccess(Object response) {
                try {
                    long position = ((JSONObject) response).getLong("position");
                    Util.postSuccess(listener, position * 1000);
                } catch (JSONException e) {
                    this.onError(new ServiceCommandError(0, "JSON Parse error", null));
                }
            }

            @Override
            public void onError(ServiceCommandError error) {
                Util.postError(listener, error);
            }
        });

        mActiveCommands.put(requestId, command);

        sendMessage(message, new ResponseListener<Object>() {

            @Override
            public void onSuccess(Object response) {
            }

            @Override
            public void onError(ServiceCommandError error) {
                Util.postError(listener, error);
            }
        });
    }

    @Override
    public void getDuration(final DurationListener listener) {
        int requestIdNumber = getNextId();
        final String requestId = String.format(Locale.US, "req%d", requestIdNumber);

        JSONObject message = null;
        try {
            message = new JSONObject() {
                {
                    put("contentType", namespaceKey + "mediaCommand");
                    put("mediaCommand", new JSONObject() {
                        {
                            put("type", "getDuration");
                            put("requestId", requestId);
                        }
                    });
                }
            };
        } catch (JSONException e) {
            Util.postError(listener, new ServiceCommandError(0, "JSON Parse error", null));
        }

        ServiceCommand<ResponseListener<Object>> command = new ServiceCommand<ResponseListener<Object>>(null, null, null, new ResponseListener<Object>() {

            @Override
            public void onSuccess(Object response) {
                try {
                    long position = ((JSONObject) response).getLong("duration");
                    Util.postSuccess(listener, position * 1000);
                } catch (JSONException e) {
                    Util.postError(listener, new ServiceCommandError(0, "JSON Parse error", null));
                }
            }

            @Override
            public void onError(ServiceCommandError error) {
                Util.postError(listener, error);
            }
        });

        mActiveCommands.put(requestId, command);

        sendMessage(message, new ResponseListener<Object>() {

            @Override
            public void onSuccess(Object response) {
            }

            @Override
            public void onError(ServiceCommandError error) {
                Util.postError(listener, error);
            }
        });
    }

    @Override
    public void getPlayState(final PlayStateListener listener) {
        int requestIdNumber = getNextId();
        final String requestId = String.format(Locale.US, "req%d", requestIdNumber);

        JSONObject message = null;
        try {
            message = new JSONObject() {
                {
                    put("contentType", namespaceKey + "mediaCommand");
                    put("mediaCommand", new JSONObject() {
                        {
                            put("type", "getPlayState");
                            put("requestId", requestId);
                        }
                    });
                }
            };
        } catch (JSONException e) {
            Util.postError(listener, new ServiceCommandError(0, "JSON Parse error", null));
        }

        ServiceCommand<ResponseListener<Object>> command = new ServiceCommand<ResponseListener<Object>>(null, null, null, new ResponseListener<Object>() {

            @Override
            public void onSuccess(Object response) {
                try {
                    String playStateString = ((JSONObject) response).getString("playState");
                    PlayStateStatus playState = parsePlayState(playStateString);
                    Util.postSuccess(listener, playState);
                } catch (JSONException e) {
                    this.onError(new ServiceCommandError(0, "JSON Parse error", null));
                }
            }

            @Override
            public void onError(ServiceCommandError error) {
                Util.postError(listener, error);
            }
        });

        mActiveCommands.put(requestId, command);

        sendMessage(message, new ResponseListener<Object>() {

            @Override
            public void onSuccess(Object response) {
            }

            @Override
            public void onError(ServiceCommandError error) {
                Util.postError(listener, error);
            }
        });
    }

    @Override
    public ServiceSubscription<PlayStateListener> subscribePlayState(final PlayStateListener listener) {
        if (mPlayStateSubscription == null)
            mPlayStateSubscription = new URLServiceSubscription<MediaControl.PlayStateListener>(null, null, null, null);

        if (!connected) {
            connect(new ResponseListener<Object>() {

                @Override
                public void onError(ServiceCommandError error) {
                    Util.postError(listener, error);
                }

                @Override
                public void onSuccess(Object response) {
                }
            });
        }

        if (!mPlayStateSubscription.getListeners().contains(listener)) mPlayStateSubscription.addListener(listener);

        return mPlayStateSubscription;
    }

    /*****************
     * Media Control *
     *****************/
    @Override
    public MediaControl getMediaControl() {
        return this;
    }

    @Override
    public CapabilityPriorityLevel getMediaControlCapabilityLevel() {
        return CapabilityPriorityLevel.HIGH;
    }

    /****************
     * Media Player *
     ****************/
    @Override
    public MediaPlayer getMediaPlayer() {
        return this;
    }

    @Override
    public CapabilityPriorityLevel getMediaPlayerCapabilityLevel() {
        return CapabilityPriorityLevel.HIGH;
    }

    @Override
    public void displayImage(final String url, final String mimeType, final String title, final String description, final String iconSrc, final MediaPlayer.LaunchListener listener) {
        int requestIdNumber = getNextId();
        final String requestId = String.format(Locale.US, "req%d", requestIdNumber);

        JSONObject message = null;
        try {
            message = new JSONObject() {
                {
                    putOpt("contentType", namespaceKey + "mediaCommand");
                    putOpt("mediaCommand", new JSONObject() {
                        {
                            putOpt("type", "displayImage");
                            putOpt("mediaURL", url);
                            putOpt("iconURL", iconSrc);
                            putOpt("title", title);
                            putOpt("description", description);
                            putOpt("mimeType", mimeType);
                            putOpt("requestId", requestId);
                        }
                    });
                }
            };
        } catch (JSONException e) {
            Util.postError(listener, new ServiceCommandError(0, "JSON Parse error", null));
            return;
        }

        ResponseListener<Object> response = new ResponseListener<Object>() {

            @Override
            public void onError(ServiceCommandError error) {
                Util.postError(listener, error);
            }

            @Override
            public void onSuccess(Object object) {
                Util.postSuccess(listener, new MediaLaunchObject(launchSession, getMediaControl()));
            }
        };

        ServiceCommand<ResponseListener<Object>> command = new ServiceCommand<ResponseListener<Object>>(socket, null, null, response);

        mActiveCommands.put(requestId, command);

        sendP2PMessage(message, new ResponseListener<Object>() {

            @Override
            public void onError(ServiceCommandError error) {
                Util.postError(listener, error);
            }

            @Override
            public void onSuccess(Object object) {
            }
        });
    }

    @Override
    public void displayImage(MediaInfo mediaInfo, MediaPlayer.LaunchListener listener) {
        String mediaUrl = null;
        String mimeType = null;
        String title = null;
        String desc = null;
        String iconSrc = null;

        if (mediaInfo != null) {
            mediaUrl = mediaInfo.getUrl();
            mimeType = mediaInfo.getMimeType();
            title = mediaInfo.getTitle();
            desc = mediaInfo.getDescription();

            if (mediaInfo.getImages() != null && !mediaInfo.getImages().isEmpty()) {
                ImageInfo imageInfo = mediaInfo.getImages().get(0);
                iconSrc = imageInfo.getUrl();
            }
        }

        displayImage(mediaUrl, mimeType, title, desc, iconSrc, listener);
    }

    @Override
    public void playMedia(String url, String mimeType, String title, String description, String iconSrc, boolean shouldLoop, MediaPlayer.LaunchListener listener) {
        MediaInfo mediaInfo = new MediaInfo.Builder(url, mimeType).setTitle(title).setDescription(description).setIcon(iconSrc).build();
        playMedia(mediaInfo, shouldLoop, listener);
    }

    @Override
    public void playMedia(final MediaInfo mediaInfo, final boolean shouldLoop, final MediaPlayer.LaunchListener listener) {
        int requestIdNumber = getNextId();
        final String requestId = String.format(Locale.US, "req%d", requestIdNumber);
        JSONObject message = null;
        ImageInfo iconImage = null;
        List<ImageInfo> images = mediaInfo.getImages();

        if (images != null && !images.isEmpty()) {
            iconImage = images.get(0);
        }

        final String iconSrc = iconImage == null ? null : iconImage.getUrl();
        final SubtitleInfo subtitleInfo = mediaInfo.getSubtitleInfo();

        try {
            message = createPlayMediaJsonRequest(mediaInfo, shouldLoop, requestId, iconSrc, subtitleInfo);
        } catch (JSONException e) {
            Util.postError(listener, new ServiceCommandError(0, "JSON Parse error", null));
            return;
        }

        ResponseListener<Object> response = new ResponseListener<Object>() {

            @Override
            public void onError(ServiceCommandError error) {
                Util.postError(listener, error);
            }

            @Override
            public void onSuccess(Object object) {
                Util.postSuccess(listener, new MediaLaunchObject(launchSession, getMediaControl(), getPlaylistControl()));
            }
        };

        ServiceCommand<ResponseListener<Object>> command = new ServiceCommand<ResponseListener<Object>>(null, null, null, response);

        mActiveCommands.put(requestId, command);

        sendMessage(message, new ResponseListener<Object>() {

            @Override
            public void onError(ServiceCommandError error) {
                Util.postError(listener, error);
            }

            @Override
            public void onSuccess(Object object) {
            }
        });
    }

    @NonNull
    private JSONObject createPlayMediaJsonRequest(final MediaInfo mediaInfo, final boolean shouldLoop, final String requestId, final String iconSrc, final SubtitleInfo subtitleInfo) throws JSONException {
        return new JSONObject() {{
            putOpt("contentType", namespaceKey + "mediaCommand");
            putOpt("mediaCommand", new JSONObject() {{
                putOpt("type", "playMedia");
                putOpt("mediaURL", mediaInfo.getUrl());
                putOpt("iconURL", iconSrc);
                putOpt("title", mediaInfo.getTitle());
                putOpt("description", mediaInfo.getDescription());
                putOpt("mimeType", mediaInfo.getMimeType());
                putOpt("shouldLoop", shouldLoop);
                putOpt("requestId", requestId);
                if (subtitleInfo != null) {
                    putOpt("subtitles", new JSONObject() {{
                        putOpt("default", ENABLED_SUBTITLE_ID);
                        putOpt("enabled", ENABLED_SUBTITLE_ID);
                        putOpt("tracks", new JSONArray() {{
                            put(new JSONObject() {{
                                putOpt("id", ENABLED_SUBTITLE_ID);
                                putOpt("language", subtitleInfo.getLanguage());
                                putOpt("source", subtitleInfo.getUrl());
                                putOpt("label", subtitleInfo.getLabel());
                            }});
                        }});
                    }});
                }
            }});
        }};
    }

    /****************
     * Playlist Control *
     ****************/
    public PlaylistControl getPlaylistControl() {
        return this;
    }

    @Override
    public CapabilityPriorityLevel getPlaylistControlCapabilityLevel() {
        return CapabilityPriorityLevel.HIGH;
    }

    @Override
    public void jumpToTrack(final long index, final ResponseListener<Object> listener) {
        int requestIdNumber = getNextId();
        final String requestId = String.format(Locale.US, "req%d", requestIdNumber);

        JSONObject message = null;
        try {
            message = new JSONObject() {
                {
                    put("contentType", namespaceKey + "mediaCommand");
                    put("mediaCommand", new JSONObject() {
                        {
                            put("type", "jumpToTrack");
                            put("requestId", requestId);
                            put("index", (int) index);
                        }
                    });
                }
            };
        } catch (JSONException e) {
            Util.postError(listener, new ServiceCommandError(0, "JSON Parse error", null));
            return;
        }

        ServiceCommand<ResponseListener<Object>> command = new ServiceCommand<ResponseListener<Object>>(null, null, null, listener);
        mActiveCommands.put(requestId, command);
        sendMessage(message, listener);
    }

    @Override
    public void previous(final ResponseListener<Object> listener) {
        int requestIdNumber = getNextId();
        final String requestId = String.format(Locale.US, "req%d", requestIdNumber);

        JSONObject message = null;
        try {
            message = new JSONObject() {
                {
                    put("contentType", namespaceKey + "mediaCommand");
                    put("mediaCommand", new JSONObject() {
                        {
                            put("type", "playPrevious");
                            put("requestId", requestId);
                        }
                    });
                }
            };
        } catch (JSONException e) {
            Util.postError(listener, new ServiceCommandError(0, "JSON Parse error", null));
            return;
        }

        ServiceCommand<ResponseListener<Object>> command = new ServiceCommand<ResponseListener<Object>>(null, null, null, listener);
        mActiveCommands.put(requestId, command);
        sendMessage(message, listener);
    }

    @Override
    public void next(final ResponseListener<Object> listener) {
        int requestIdNumber = getNextId();
        final String requestId = String.format(Locale.US, "req%d", requestIdNumber);

        JSONObject message = null;
        try {
            message = new JSONObject() {
                {
                    put("contentType", namespaceKey + "mediaCommand");
                    put("mediaCommand", new JSONObject() {
                        {
                            put("type", "playNext");
                            put("requestId", requestId);
                        }
                    });
                }
            };
        } catch (JSONException e) {
            Util.postError(listener, new ServiceCommandError(0, "JSON Parse error", null));
            return;
        }

        ServiceCommand<ResponseListener<Object>> command = new ServiceCommand<ResponseListener<Object>>(null, null, null, listener);
        mActiveCommands.put(requestId, command);
        sendMessage(message, listener);
    }
}
