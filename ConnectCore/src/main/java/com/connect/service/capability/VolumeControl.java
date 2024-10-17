package com.connect.service.capability;

import com.connect.service.capability.listeners.ResponseListener;
import com.connect.service.command.ServiceSubscription;

public interface VolumeControl extends CapabilityMethods {
    public final static String Any = "VolumeControl.Any";

    public final static String Volume_Get = "VolumeControl.Get";
    public final static String Volume_Set = "VolumeControl.Set";
    public final static String Volume_Up_Down = "VolumeControl.UpDown";
    public final static String Volume_Subscribe = "VolumeControl.Subscribe";
    public final static String Mute_Get = "VolumeControl.Mute.Get";
    public final static String Mute_Set = "VolumeControl.Mute.Set";
    public final static String Mute_Subscribe = "VolumeControl.Mute.Subscribe";

    public final static String[] Capabilities = {
        Volume_Get,
        Volume_Set,
        Volume_Up_Down,
        Volume_Subscribe,
        Mute_Get,
        Mute_Set,
        Mute_Subscribe
    };

    VolumeControl getVolumeControl();
    CapabilityPriorityLevel getVolumeControlCapabilityLevel();

    void volumeUp(ResponseListener<Object> listener);
    void volumeDown(ResponseListener<Object> listener);

    void setVolume(float volume, ResponseListener<Object> listener);
    void getVolume(VolumeListener listener);

    void setMute(boolean isMute, ResponseListener<Object> listener);
    void getMute(MuteListener listener);

    ServiceSubscription<VolumeListener> subscribeVolume(VolumeListener listener);
    ServiceSubscription<MuteListener> subscribeMute(MuteListener listener);

    /**
     * Success block that is called upon successfully getting the device's system volume.
     *
     * Passes the current system volume, value is a float between 0.0 and 1.0
     */
    public static interface VolumeListener extends ResponseListener<Float> { }

    /**
     * Success block that is called upon successfully getting the device's system mute status.
     *
     * Passes current system mute status
     */
    public static interface MuteListener extends ResponseListener<Boolean> { }

    /**
     * Success block that is called upon successfully getting the device's system volume status.
     *
     * Passes current system mute status
     */
    public static interface VolumeStatusListener extends ResponseListener<VolumeStatus> { }

    /**
     * Helper class used with the VolumeControl.VolueStatusListener to return the current volume status.
     */
    public static class VolumeStatus {
        public boolean isMute;
        public float volume;

        public VolumeStatus(boolean isMute, float volume) {
            this.isMute = isMute;
            this.volume = volume;
        }
    }
}
