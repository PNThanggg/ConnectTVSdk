package com.connect.module.firetv.service;


import com.amazon.whisperplay.fling.media.controller.RemoteMediaPlayer;
import com.amazon.whisperplay.fling.media.service.CustomMediaPlayer;
import com.amazon.whisperplay.fling.media.service.MediaPlayerInfo;
import com.amazon.whisperplay.fling.media.service.MediaPlayerStatus;
import com.connect.core.ImageInfo;
import com.connect.core.MediaInfo;
import com.connect.core.Util;
import com.connect.discovery.DiscoveryFilter;
import com.connect.module.firetv.service.command.FireTVServiceError;
import com.connect.service.DeviceService;
import com.connect.service.capability.CapabilityMethods;
import com.connect.service.capability.MediaControl;
import com.connect.service.capability.MediaPlayer;
import com.connect.service.capability.listeners.ResponseListener;
import com.connect.service.command.ServiceCommandError;
import com.connect.service.command.ServiceSubscription;
import com.connect.service.config.ServiceConfig;
import com.connect.service.config.ServiceDescription;
import com.connect.service.sessions.LaunchSession;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;

/**
 * FireTVService provides capabilities for FireTV devices. FireTVService acts as a layer on top of
 * Fling SDK, and requires the Fling SDK library to function. FireTVService provides the following
 * functionality:
 * - Media playback
 * - Media control
 * <p>
 * Using Connect SDK for discovery/control of FireTV devices will result in your app complying with
 * the Fling SDK terms of service.
 */
public class FireTVService extends DeviceService implements MediaPlayer, MediaControl {

    public static final String ID = "FireTV";

    private static final String META_TITLE = "title";
    private static final String META_DESCRIPTION = "description";
    private static final String META_MIME_TYPE = "type";
    private static final String META_ICON_IMAGE = "poster";
    private static final String META_NOREPLAY = "noreplay";
    private static final String META_TRACKS = "tracks";
    private static final String META_SRC = "src";
    private static final String META_KIND = "kind";
    private static final String META_SRCLANG = "srclang";
    private static final String META_LABEL = "label";

    private final RemoteMediaPlayer remoteMediaPlayer;
    private PlayStateSubscription playStateSubscription;

    public FireTVService(ServiceDescription serviceDescription, ServiceConfig serviceConfig) {
        super(serviceDescription, serviceConfig);
        if (serviceDescription != null && serviceDescription.getDevice() instanceof RemoteMediaPlayer) {
            this.remoteMediaPlayer = (RemoteMediaPlayer) serviceDescription.getDevice();
        } else {
            this.remoteMediaPlayer = null;
        }
    }

    /**
     * Get filter instance for this service which contains a name of service and id. It is used in
     * discovery process
     */
    public static DiscoveryFilter discoveryFilter() {
        return new DiscoveryFilter(ID, "FireTV");
    }

    /**
     * Prepare a service for usage
     */
    @Override
    public void connect() {
        super.connect();
        if (remoteMediaPlayer != null) {
            connected = true;
            reportConnected(connected);
        }
    }

    /**
     * Check if service is ready
     */
    @Override
    public boolean isConnected() {
        return connected;
    }

    /**
     * Check if service implements connect/disconnect methods
     */
    @Override
    public boolean isConnectable() {
        return true;
    }

    /**
     * Disconnect a service and close all subscriptions
     */
    @Override
    public void disconnect() {
        super.disconnect();
        if (playStateSubscription != null) {
            playStateSubscription.unsubscribe();
            playStateSubscription = null;
        }
        connected = false;
    }

    @Override
    protected void updateCapabilities() {
        List<String> capabilities = new ArrayList<String>();
        capabilities.add(MediaPlayer.MediaInfo_Get);
        capabilities.add(MediaPlayer.Display_Image);
        capabilities.add(MediaPlayer.Play_Audio);
        capabilities.add(MediaPlayer.Play_Video);
        capabilities.add(MediaPlayer.Close);
        capabilities.add(MediaPlayer.MetaData_MimeType);
        capabilities.add(MediaPlayer.MetaData_Thumbnail);
        capabilities.add(MediaPlayer.MetaData_Title);
        capabilities.add(MediaPlayer.Subtitle_WebVTT);

        capabilities.add(MediaControl.Play);
        capabilities.add(MediaControl.Pause);
        capabilities.add(MediaControl.Stop);
        capabilities.add(MediaControl.Seek);
        capabilities.add(MediaControl.Duration);
        capabilities.add(MediaControl.Position);
        capabilities.add(MediaControl.PlayState);
        capabilities.add(MediaControl.PlayState_Subscribe);

        setCapabilities(capabilities);
    }

    /**
     * Get a priority level for a particular capability
     */
    @Override
    public CapabilityPriorityLevel getPriorityLevel(Class<? extends CapabilityMethods> clazz) {
        if (clazz != null) {
            if (clazz.equals(MediaPlayer.class)) {
                return getMediaPlayerCapabilityLevel();
            } else if (clazz.equals(MediaControl.class)) {
                return getMediaControlCapabilityLevel();
            }
        }
        return CapabilityPriorityLevel.NOT_SUPPORTED;
    }


    /**
     * Get MediaPlayer implementation
     */
    @Override
    public MediaPlayer getMediaPlayer() {
        return this;
    }

    /**
     * Get MediaPlayer priority level
     */
    @Override
    public CapabilityPriorityLevel getMediaPlayerCapabilityLevel() {
        return CapabilityPriorityLevel.HIGH;
    }

    /**
     * Get MediaInfo available only during playback otherwise returns an error
     *
     * @param listener
     */
    @Override
    public void getMediaInfo(final MediaInfoListener listener) {
        final String error = "Error getting media info";
        RemoteMediaPlayer.AsyncFuture<MediaPlayerInfo> asyncFuture;
        try {
            asyncFuture = remoteMediaPlayer.getMediaInfo();
            handleAsyncFutureWithConversion(listener, asyncFuture, data -> {
                JSONObject metaJson;
                metaJson = new JSONObject(data.getMetadata());
                List<ImageInfo> images = null;
                if (metaJson.has(META_ICON_IMAGE)) {
                    images = new ArrayList<>();
                    images.add(new ImageInfo(metaJson.getString(META_ICON_IMAGE)));
                }
                MediaInfo mediaInfo = new MediaInfo(data.getSource(), metaJson.getString(META_MIME_TYPE), metaJson.getString(META_TITLE), metaJson.getString(META_DESCRIPTION), images);
                return mediaInfo;
            }, error);
        } catch (Exception e) {
            Util.postError(listener, new FireTVServiceError(error));
        }
    }

    /**
     * Not supported
     */
    @Override
    public ServiceSubscription<MediaInfoListener> subscribeMediaInfo(MediaInfoListener listener) {
        Util.postError(listener, ServiceCommandError.notSupported());
        return null;
    }

    @Override
    public void displayImage(String url, String mimeType, String title, String description, String iconSrc, final LaunchListener listener) {
        setMediaSource(new MediaInfo.Builder(url, mimeType).setTitle(title).setDescription(description).setIcon(iconSrc).build(), listener);
    }

    @Override
    public void playMedia(String url, String mimeType, String title, String description, String iconSrc, boolean shouldLoop, LaunchListener listener) {
        setMediaSource(new MediaInfo.Builder(url, mimeType).setTitle(title).setDescription(description).setIcon(iconSrc).build(), listener);
    }

    @Override
    public void closeMedia(LaunchSession launchSession, final ResponseListener<Object> listener) {
        stop(listener);
    }

    @Override
    public void displayImage(MediaInfo mediaInfo, LaunchListener listener) {
        setMediaSource(mediaInfo, listener);
    }

    @Override
    public void playMedia(MediaInfo mediaInfo, boolean shouldLoop, LaunchListener listener) {
        setMediaSource(mediaInfo, listener);
    }

    /**
     * Get MediaControl capability. It should be used only during media playback.
     */
    @Override
    public MediaControl getMediaControl() {
        return this;
    }

    /**
     * Get MediaControl priority level
     */
    @Override
    public CapabilityPriorityLevel getMediaControlCapabilityLevel() {
        return CapabilityPriorityLevel.HIGH;
    }

    /**
     * Play current media.
     */
    @Override
    public void play(ResponseListener<Object> listener) {
        final String error = "Error playing";
        RemoteMediaPlayer.AsyncFuture<Void> asyncFuture = null;
        try {
            asyncFuture = remoteMediaPlayer.play();
            handleVoidAsyncFuture(listener, asyncFuture, error);
        } catch (Exception e) {
            Util.postError(listener, new FireTVServiceError(error, e));
        }
    }

    /**
     * Pause current media.
     */
    @Override
    public void pause(ResponseListener<Object> listener) {
        final String error = "Error pausing";
        RemoteMediaPlayer.AsyncFuture<Void> asyncFuture = null;
        try {
            asyncFuture = remoteMediaPlayer.pause();
            handleVoidAsyncFuture(listener, asyncFuture, error);
        } catch (Exception e) {
            Util.postError(listener, new FireTVServiceError(error, e));
        }
    }

    /**
     * Stop current media and close FireTV application.
     */
    @Override
    public void stop(ResponseListener<Object> listener) {
        final String error = "Error stopping";
        RemoteMediaPlayer.AsyncFuture<Void> asyncFuture = null;
        try {
            asyncFuture = remoteMediaPlayer.stop();
            handleVoidAsyncFuture(listener, asyncFuture, error);
        } catch (Exception e) {
            Util.postError(listener, new FireTVServiceError(error, e));
        }
    }

    /**
     * Not supported
     */
    @Override
    public void rewind(ResponseListener<Object> listener) {
        Util.postError(listener, ServiceCommandError.notSupported());
    }

    /**
     * Not supported
     */
    @Override
    public void fastForward(ResponseListener<Object> listener) {
        Util.postError(listener, ServiceCommandError.notSupported());
    }

    /**
     * Not supported
     */
    @Override
    public void previous(ResponseListener<Object> listener) {
        Util.postError(listener, ServiceCommandError.notSupported());
    }

    /**
     * Not supported
     */
    @Override
    public void next(ResponseListener<Object> listener) {
        Util.postError(listener, ServiceCommandError.notSupported());
    }

    /**
     * Seek current media.
     *
     * @param position time in milliseconds
     * @param listener
     */
    @Override
    public void seek(long position, ResponseListener<Object> listener) {
        final String error = "Error seeking";
        RemoteMediaPlayer.AsyncFuture<Void> asyncFuture = null;
        try {
            asyncFuture = remoteMediaPlayer.seek(CustomMediaPlayer.PlayerSeekMode.Absolute, position);
            handleVoidAsyncFuture(listener, asyncFuture, error);
        } catch (Exception e) {
            Util.postError(listener, new FireTVServiceError(error, e));
        }
    }

    /**
     * Get current media duration.
     */
    @Override
    public void getDuration(final DurationListener listener) {
        final String error = "Error getting duration";
        RemoteMediaPlayer.AsyncFuture<Long> asyncFuture;
        try {
            asyncFuture = remoteMediaPlayer.getDuration();
            handleAsyncFuture(listener, asyncFuture, error);
        } catch (Exception e) {
            Util.postError(listener, new FireTVServiceError(error, e));
            return;
        }
    }

    /**
     * Get playback position
     */
    @Override
    public void getPosition(final PositionListener listener) {
        final String error = "Error getting position";
        RemoteMediaPlayer.AsyncFuture<Long> asyncFuture;
        try {
            asyncFuture = remoteMediaPlayer.getPosition();
            handleAsyncFuture(listener, asyncFuture, error);
        } catch (Exception e) {
            Util.postError(listener, new FireTVServiceError(error, e));
            return;
        }
    }

    /**
     * Get playback state
     */
    @Override
    public void getPlayState(final PlayStateListener listener) {
        final String error = "Error getting play state";
        RemoteMediaPlayer.AsyncFuture<MediaPlayerStatus> asyncFuture;
        try {
            asyncFuture = remoteMediaPlayer.getStatus();
            handleAsyncFutureWithConversion(listener, asyncFuture, new ConvertResult<PlayStateStatus, MediaPlayerStatus>() {
                @Override
                public PlayStateStatus convert(MediaPlayerStatus data) {
                    return createPlayStateStatusFromFireTVStatus(data);
                }
            }, error);
        } catch (Exception e) {
            Util.postError(listener, new FireTVServiceError(error, e));
            return;
        }
    }

    /**
     * Subscribe to playback state. Only single instance of subscription is available. Each new
     * call returns the same subscription object.
     */
    @Override
    public ServiceSubscription<PlayStateListener> subscribePlayState(final PlayStateListener listener) {

        if (playStateSubscription == null) {
            playStateSubscription = new PlayStateSubscription(listener);
            remoteMediaPlayer.addStatusListener(playStateSubscription);
        } else if (!playStateSubscription.getListeners().contains(listener)) {
            playStateSubscription.addListener(listener);
        }
        getPlayState(listener);
        return playStateSubscription;
    }

    PlayStateStatus createPlayStateStatusFromFireTVStatus(MediaPlayerStatus status) {
        PlayStateStatus playState = PlayStateStatus.Unknown;
        if (status != null) {
            switch (status.getState()) {
                case PreparingMedia:
                    playState = PlayStateStatus.Buffering;
                    break;
                case Playing:
                    playState = PlayStateStatus.Playing;
                    break;
                case Paused:
                    playState = PlayStateStatus.Paused;
                    break;
                case Finished:
                    playState = PlayStateStatus.Finished;
                    break;
                case NoSource:
                    playState = PlayStateStatus.Idle;
            }
        }
        return playState;
    }

    private String getMetadata(MediaInfo mediaInfo) throws JSONException {
        JSONObject json = new JSONObject();
        if (mediaInfo.getTitle() != null && !mediaInfo.getTitle().isEmpty()) {
            json.put(META_TITLE, mediaInfo.getTitle());
        }
        if (mediaInfo.getDescription() != null && !mediaInfo.getDescription().isEmpty()) {
            json.put(META_DESCRIPTION, mediaInfo.getDescription());
        }
        json.put(META_MIME_TYPE, mediaInfo.getMimeType());
        if (mediaInfo.getImages() != null && mediaInfo.getImages().size() > 0) {
            ImageInfo image = mediaInfo.getImages().get(0);
            if (image != null) {
                if (image.getUrl() != null && !image.getUrl().isEmpty()) {
                    json.put(META_ICON_IMAGE, image.getUrl());
                }
            }
        }
        json.put(META_NOREPLAY, true);
        if (mediaInfo.getSubtitleInfo() != null) {
            JSONArray tracksArray = new JSONArray();
            JSONObject trackObj = new JSONObject();
            trackObj.put(META_KIND, "subtitles");
            trackObj.put(META_SRC, mediaInfo.getSubtitleInfo().getUrl());
            String label = mediaInfo.getSubtitleInfo().getLabel();
            trackObj.put(META_LABEL, label == null ? "" : label);
            String language = mediaInfo.getSubtitleInfo().getLanguage();
            trackObj.put(META_SRCLANG, language == null ? "" : language);
            tracksArray.put(trackObj);
            json.put(META_TRACKS, tracksArray);
        }
        return json.toString();
    }

    private MediaLaunchObject createMediaLaunchObject() {
        LaunchSession launchSession = new LaunchSession();
        launchSession.setService(this);
        launchSession.setSessionType(LaunchSession.LaunchSessionType.Media);
        launchSession.setAppId(remoteMediaPlayer.getUniqueIdentifier());
        launchSession.setAppName(remoteMediaPlayer.getName());
        MediaLaunchObject mediaLaunchObject = new MediaLaunchObject(launchSession, this);
        return mediaLaunchObject;
    }

    private void setMediaSource(MediaInfo mediaInfo, final LaunchListener listener) {
        final String error = "Error setting media source";
        RemoteMediaPlayer.AsyncFuture<Void> asyncFuture;
        try {
            final String metadata = getMetadata(mediaInfo);
            asyncFuture = remoteMediaPlayer.setMediaSource(mediaInfo.getUrl(), metadata, true, false);
        } catch (Exception e) {
            Util.postError(listener, new FireTVServiceError(error, e));
            return;
        }
        handleAsyncFutureWithConversion(listener, asyncFuture, data -> createMediaLaunchObject(), error);
    }

    private void handleVoidAsyncFuture(final ResponseListener<Object> listener, final RemoteMediaPlayer.AsyncFuture<Void> asyncFuture, final String errorMessage) {
        handleAsyncFutureWithConversion(listener, asyncFuture, data -> data, errorMessage);
    }

    private <T> void handleAsyncFuture(final ResponseListener<T> listener, final RemoteMediaPlayer.AsyncFuture<T> asyncFuture, final String errorMessage) {
        handleAsyncFutureWithConversion(listener, asyncFuture, data -> data, errorMessage);
    }

    private <Response, Result> void handleAsyncFutureWithConversion(final ResponseListener<Response> listener, final RemoteMediaPlayer.AsyncFuture<Result> asyncFuture, final ConvertResult<Response, Result> conversion, final String errorMessage) {
        if (asyncFuture != null) {
            asyncFuture.getAsync(future -> {
                try {
                    Result result = future.get();
                    Util.postSuccess(listener, conversion.convert(result));
                } catch (ExecutionException e) {
                    Util.postError(listener, new FireTVServiceError(errorMessage, e.getCause()));
                } catch (Exception e) {
                    Util.postError(listener, new FireTVServiceError(errorMessage, e));
                }
            });
        } else {
            Util.postError(listener, new FireTVServiceError(errorMessage));
        }
    }

    private interface ConvertResult<Response, Result> {
        Response convert(Result data) throws Exception;
    }

    private abstract static class Subscription<Status, Listener extends ResponseListener<Status>> implements ServiceSubscription<Listener> {

        List<Listener> listeners = new ArrayList<Listener>();

        Status prevStatus;

        public Subscription(Listener listener) {
            if (listener != null) {
                this.listeners.add(listener);
            }
        }

        synchronized void notifyListeners(final Status status) {
            if (!status.equals(prevStatus)) {
                Util.runOnUI(new Runnable() {
                    @Override
                    public void run() {
                        for (Listener listener : listeners) {
                            listener.onSuccess(status);
                        }
                    }
                });
                prevStatus = status;
            }
        }

        @Override
        public Listener addListener(Listener listener) {
            if (listener != null) {
                listeners.add(listener);
            }
            return listener;
        }

        @Override
        public void removeListener(Listener listener) {
            listeners.remove(listener);
        }

        @Override
        public List<Listener> getListeners() {
            return listeners;
        }
    }

    /**
     * Internal play state subscription implementation
     */
    class PlayStateSubscription extends Subscription<PlayStateStatus, PlayStateListener> implements CustomMediaPlayer.StatusListener {

        public PlayStateSubscription(PlayStateListener listener) {
            super(listener);
        }

        @Override
        public void onStatusChange(MediaPlayerStatus mediaPlayerStatus, long position) {
            final PlayStateStatus status = createPlayStateStatusFromFireTVStatus(mediaPlayerStatus);
            notifyListeners(status);
        }

        @Override
        public void unsubscribe() {
            remoteMediaPlayer.removeStatusListener(this);
            playStateSubscription = null;
        }

    }

}
