package com.connect.service.capability;

import com.connect.core.ChannelInfo;
import com.connect.core.ProgramInfo;
import com.connect.core.ProgramList;
import com.connect.service.capability.listeners.ResponseListener;
import com.connect.service.command.ServiceSubscription;

import java.util.List;

public interface TVControl extends CapabilityMethods {
    String Any = "TVControl.Any";

    String Channel_Get = "TVControl.Channel.Get";
    String Channel_Set = "TVControl.Channel.Set";
    String Channel_Up = "TVControl.Channel.Up";
    String Channel_Down = "TVControl.Channel.Down";
    String Channel_List = "TVControl.Channel.List";
    String Channel_Subscribe = "TVControl.Channel.Subscribe";
    String Program_Get = "TVControl.Program.Get";
    String Program_List = "TVControl.Program.List";
    String Program_Subscribe = "TVControl.Program.Subscribe";
    String Program_List_Subscribe = "TVControl.Program.List.Subscribe";
    String Get_3D = "TVControl.3D.Get";
    String Set_3D = "TVControl.3D.Set";
    String Subscribe_3D = "TVControl.3D.Subscribe";

    String[] Capabilities = {
        Channel_Get,
        Channel_Set,
        Channel_Up,
        Channel_Down,
        Channel_List,
        Channel_Subscribe,
        Program_Get,
        Program_List,
        Program_Subscribe,
        Program_List_Subscribe,
        Get_3D,
        Set_3D,
        Subscribe_3D
    };

    TVControl getTVControl();
    public CapabilityPriorityLevel getTVControlCapabilityLevel();

    public void channelUp(ResponseListener<Object> listener);
    public void channelDown(ResponseListener<Object> listener);

    public void setChannel(ChannelInfo channelNumber, ResponseListener<Object> listener);

    public void getCurrentChannel(ChannelListener listener);
    public ServiceSubscription<ChannelListener> subscribeCurrentChannel(ChannelListener listener);

    public void getChannelList(ChannelListListener listener);

    public void getProgramInfo(ProgramInfoListener listener);
    public ServiceSubscription<ProgramInfoListener> subscribeProgramInfo(ProgramInfoListener listener);

    public void getProgramList(ProgramListListener listener);
    public ServiceSubscription<ProgramListListener> subscribeProgramList(ProgramListListener listener);

    public void get3DEnabled(State3DModeListener listener);
    public void set3DEnabled(boolean enabled, ResponseListener<Object> listener);
    public ServiceSubscription<State3DModeListener> subscribe3DEnabled(State3DModeListener listener);

    /**
     * Success block that is called upon successfully getting the TV's 3D mode
     *
     * Passes a Boolean to see Whether 3D mode is currently enabled on the TV
     */
    public static interface State3DModeListener extends ResponseListener<Boolean> { }

    /**
     * Success block that is called upon successfully getting the current channel's information.
     *
     * Passes a ChannelInfo object containing information about the current channel
     */
    public static interface ChannelListener extends ResponseListener<ChannelInfo>{ }

    /**
     * Success block that is called upon successfully getting the channel list.
     *
     * Passes a List of ChannelList objects for each available channel on the TV
     */
    public static interface ChannelListListener extends ResponseListener<List<ChannelInfo>>{ }

    /**
     * Success block that is called upon successfully getting the current program's information.
     *
     * Passes a ProgramInfo object containing information about the current program
     */
    public static interface ProgramInfoListener extends ResponseListener<ProgramInfo> { }

    /**
     * Success block that is called upon successfully getting the program list for the current channel.
     *
     * Passes a ProgramList containing a ProgramInfo object for each available program on the TV's current channel
     */
    public static interface ProgramListListener extends ResponseListener<ProgramList> { }
}
