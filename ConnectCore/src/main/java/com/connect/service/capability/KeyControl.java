package com.connect.service.capability;

import com.connect.service.capability.listeners.ResponseListener;

public interface KeyControl extends CapabilityMethods {
    String Any = "KeyControl.Any";

    String Up = "KeyControl.Up";
    String Down = "KeyControl.Down";
    String Left = "KeyControl.Left";
    String Right = "KeyControl.Right";
    String OK = "KeyControl.OK";
    String Back = "KeyControl.Back";
    String Home = "KeyControl.Home";
    String Send_Key = "KeyControl.SendKey";
    String KeyCode = "KeyControl.KeyCode";

    public enum KeyCode {
        NUM_0 (0),
        NUM_1 (1),
        NUM_2 (2),
        NUM_3 (3),
        NUM_4 (4),
        NUM_5 (5),
        NUM_6 (6),
        NUM_7 (7),
        NUM_8 (8),
        NUM_9 (9),

        DASH (10),
        ENTER (11);

        private final int code; 

        private static final KeyCode[] codes = {
            NUM_0, NUM_1, NUM_2, NUM_3, NUM_4, NUM_5, NUM_6, NUM_7, NUM_8, NUM_9, DASH, ENTER
        };

        KeyCode(int code) {
            this.code = code;
        }

        public int getCode() {
            return code;
        }

        public static KeyCode createFromInteger(int keyCode) {
            if (keyCode >= 0 && keyCode < codes.length) {
                return codes[keyCode];
            }
            return null;
        }
    }

    String[] Capabilities = {
        Up,
        Down,
        Left,
        Right,
        OK,
        Back,
        Home,
        KeyCode,
    };

    KeyControl getKeyControl();
    CapabilityPriorityLevel getKeyControlCapabilityLevel();

    void up(ResponseListener<Object> listener);

    void down(ResponseListener<Object> listener);

    void left(ResponseListener<Object> listener);

    void right(ResponseListener<Object> listener);

    void ok(ResponseListener<Object> listener);

    void back(ResponseListener<Object> listener);

    void home(ResponseListener<Object> listener);

    void sendKeyCode(KeyCode keycode, ResponseListener<Object> listener);
}
