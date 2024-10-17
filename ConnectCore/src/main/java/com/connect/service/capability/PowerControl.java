package com.connect.service.capability;

import com.connect.service.capability.listeners.ResponseListener;

public interface PowerControl extends CapabilityMethods {
    String Any = "PowerControl.Any";

    String Off = "PowerControl.Off";
    String On = "PowerControl.On";

    String[] Capabilities = {
        Off,
        On
    };

    public PowerControl getPowerControl();
    public CapabilityPriorityLevel getPowerControlCapabilityLevel();

    public void powerOff(ResponseListener<Object> listener);

    public void powerOn(ResponseListener<Object> listener);
}
