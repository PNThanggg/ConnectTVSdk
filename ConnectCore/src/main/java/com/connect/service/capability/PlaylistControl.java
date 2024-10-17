package com.connect.service.capability;

import com.connect.service.capability.listeners.ResponseListener;

/**
 * The PlaylistControl capability interface serves to define the methods required for normalizing
 * the control of playlist (next, previous, jumpToTrack, etc)
 */
public interface PlaylistControl extends CapabilityMethods {
    String Any = "PlaylistControl.Any";
    String JumpToTrack = "PlaylistControl.JumpToTrack";
    String SetPlayMode = "PlaylistControl.SetPlayMode";
    String Previous = "PlaylistControl.Previous";
    String Next = "PlaylistControl.Next";


    String[] Capabilities = {
        Previous,
        Next,
        JumpToTrack,
        SetPlayMode,
        JumpToTrack,
    };

    /**
     * Enumerates available playlist mode
     */
    public static enum PlayMode {
        /**
         * Default mode, play tracks in sequence and stop at the end.
         */
        Normal,

        /**
         * Shuffle the playlist and play in sequeance.
         */
        Shuffle,

        /**
         * Repeat current track
         */
        RepeatOne,

        /**
         * Repeat entire playlist
         */
        RepeatAll,
    }

    public PlaylistControl getPlaylistControl();

    public CapabilityPriorityLevel getPlaylistControlCapabilityLevel();

    /**
     * Play previous track in the playlist
     * @param listener optional response listener
     */
    public void previous(ResponseListener<Object> listener);

    /**
     * Play next track in the playlist
     * @param listener optional response listener
     */
    public void next(ResponseListener<Object> listener);

    /**
     * Play a track specified by index in the playlist
     *
     * @param index index in the playlist, it starts from zero like index of array
     * @param listener optional response listener
     */
    public void jumpToTrack(long index, ResponseListener<Object> listener);

    /**
     * Set order of playing tracks
     *
     * @param playMode
     * @param listener optional response listener
     */
    public void setPlayMode(PlayMode playMode, ResponseListener<Object> listener);

}
