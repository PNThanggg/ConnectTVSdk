package com.connect.service.capability;

import com.connect.core.TextInputStatusInfo;
import com.connect.service.capability.listeners.ResponseListener;
import com.connect.service.command.ServiceSubscription;

public interface TextInputControl extends CapabilityMethods {
    String Any = "TextInputControl.Any";

    String Send = "TextInputControl.Send";
    public final static String Send_Enter = "TextInputControl.Enter";
    public final static String Send_Delete = "TextInputControl.Delete";
    public final static String Subscribe = "TextInputControl.Subscribe";

    public final static String[] Capabilities = {Send, Send_Enter, Send_Delete, Subscribe};

    public TextInputControl getTextInputControl();

    public CapabilityPriorityLevel getTextInputControlCapabilityLevel();

    public ServiceSubscription<TextInputStatusListener> subscribeTextInputStatus(TextInputStatusListener listener);

    public void sendText(String input);

    public void sendEnter();

    public void sendDelete();

    /**
     * Response block that is fired on any change of keyboard visibility.
     * <p>
     * Passes TextInputStatusInfo object that provides keyboard type & visibility information
     */
    public static interface TextInputStatusListener extends ResponseListener<TextInputStatusInfo> {
    }
}
