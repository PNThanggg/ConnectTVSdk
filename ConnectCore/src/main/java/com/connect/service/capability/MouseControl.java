package com.connect.service.capability;

import android.graphics.PointF;


public interface MouseControl extends CapabilityMethods {
    String Any = "MouseControl.Any";

    String Connect = "MouseControl.Connect";
    String Disconnect = "MouseControl.Disconnect";
    String Click = "MouseControl.Click";
    String Move = "MouseControl.Move";
    String Scroll = "MouseControl.Scroll";

    String[] Capabilities = {
        Connect,
        Disconnect,
        Click,
        Move,
        Scroll
    };

    public MouseControl getMouseControl();
    public CapabilityPriorityLevel getMouseControlCapabilityLevel();

    public void connectMouse();
    public void disconnectMouse();

    public void click();
    public void move(double dx, double dy);
    public void move(PointF distance);
    public void scroll(double dx, double dy);
    public void scroll(PointF distance);
}
