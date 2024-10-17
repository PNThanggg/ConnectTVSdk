package com.connect.service.capability;

import com.connect.core.ExternalInputInfo;
import com.connect.service.capability.listeners.ResponseListener;
import com.connect.service.sessions.LaunchSession;

import java.util.List;

public interface ExternalInputControl extends CapabilityMethods {
    String Any = "ExternalInputControl.Any";

    String Picker_Launch = "ExternalInputControl.Picker.Launch";

    String Picker_Close = "ExternalInputControl.Picker.Close";

    String List = "ExternalInputControl.List";

    String Set = "ExternalInputControl.Set";

    String[] Capabilities = {Picker_Launch, Picker_Close, List, Set};

    ExternalInputControl getExternalInput();

    CapabilityPriorityLevel getExternalInputControlPriorityLevel();

    void launchInputPicker(Launcher.AppLaunchListener listener);

    void closeInputPicker(LaunchSession launchSessionm, ResponseListener<Object> listener);

    void getExternalInputList(ExternalInputListListener listener);

    void setExternalInput(ExternalInputInfo input, ResponseListener<Object> listener);

    /**
     * Success block that is called upon successfully getting the external input list.
     * <p>
     * Passes a list containing an ExternalInputInfo object for each available external input on the device
     */
    interface ExternalInputListListener extends ResponseListener<java.util.List<ExternalInputInfo>> {
    }
}
