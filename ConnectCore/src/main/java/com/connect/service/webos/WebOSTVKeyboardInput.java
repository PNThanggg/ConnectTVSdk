package com.connect.service.webos;

import com.connect.core.TextInputStatusInfo;
import com.connect.core.Util;
import com.connect.service.WebOSTVService;
import com.connect.service.capability.TextInputControl;
import com.connect.service.capability.listeners.ResponseListener;
import com.connect.service.command.ServiceCommand;
import com.connect.service.command.ServiceCommandError;
import com.connect.service.command.URLServiceSubscription;

import java.util.ArrayList;
import java.util.List;

import org.json.JSONException;
import org.json.JSONObject;


public class WebOSTVKeyboardInput {

    WebOSTVService service;
    boolean waiting;
    List<String> toSend;

    static String KEYBOARD_INPUT = "ssap://com.webos.service.ime/registerRemoteKeyboard";
    static String ENTER = "ENTER";
    static String DELETE = "DELETE";

    boolean canReplaceText = false;

    public WebOSTVKeyboardInput(WebOSTVService service) {
        this.service = service;
        waiting = false;

        toSend = new ArrayList<>();
    }

    public void addToQueue(String input) {
        toSend.add(input);
        if (!waiting) {
            sendData();
        }
    }

    public void sendEnter() {
        toSend.add(ENTER);
        if (!waiting) {
            sendData();
        }
    }

    public void sendDel() {
        if (toSend.isEmpty()) {
            toSend.add(DELETE);
            if (!waiting) {
                sendData();
            }
        }
        else {
            toSend.remove(toSend.size()-1);
        }
    }

    private void sendData() {
        waiting = true;

        String uri;
        String typeTest = toSend.get(0);

        JSONObject payload = new JSONObject();

        if (typeTest.equals(ENTER)) {
            toSend.remove(0);
            uri = "ssap://com.webos.service.ime/sendEnterKey";
        }
        else if (typeTest.equals(DELETE)) {
            uri = "ssap://com.webos.service.ime/deleteCharacters";

            int count = 0;
            while (!toSend.isEmpty() && toSend.get(0).equals(DELETE)) {
                toSend.remove(0);
                count++;
            }

            try {
                payload.put("count", count);
            } catch (JSONException e) {
                e.printStackTrace();
            }
        }
        else {
            uri = "ssap://com.webos.service.ime/insertText";
            StringBuilder sb = new StringBuilder();

            while (!toSend.isEmpty() && !(toSend.get(0).equals(DELETE) || toSend.get(0).equals(ENTER))) {
                String text = toSend.get(0);
                sb.append(text);
                toSend.remove(0);
            }

            try {
                payload.put("text", sb.toString());
                payload.put("replace", 0);
            } catch (JSONException e) {
                e.printStackTrace();
            }
        }

        ResponseListener<Object> responseListener = new ResponseListener<Object>() {

            @Override
            public void onSuccess(Object response) {
                waiting = false;
                if (!toSend.isEmpty())
                    sendData();
            }

            @Override
            public void onError(ServiceCommandError error) {
                waiting = false;
                if (!toSend.isEmpty())
                    sendData();
            }
        };

        ServiceCommand<ResponseListener<Object>> request = new ServiceCommand<>(service, uri, payload, true, responseListener);
        request.send();
    }

    public URLServiceSubscription<TextInputControl.TextInputStatusListener> connect(final TextInputControl.TextInputStatusListener listener) {
        ResponseListener<Object> responseListener = new ResponseListener<Object>() {

            @Override
            public void onSuccess(Object response) {
                JSONObject jsonObj = (JSONObject)response;

                TextInputStatusInfo keyboard = parseRawKeyboardData(jsonObj);

                Util.postSuccess(listener, keyboard);
            }

            @Override
            public void onError(ServiceCommandError error) {
                Util.postError(listener, error);
            }
        };

        URLServiceSubscription<TextInputControl.TextInputStatusListener> subscription = new URLServiceSubscription<>(service, KEYBOARD_INPUT, null, true, responseListener);
        subscription.send();

        return subscription;
    }

    private TextInputStatusInfo parseRawKeyboardData(JSONObject rawData) {
        boolean focused = false;
        String contentType = null;
        boolean predictionEnabled = false;
        boolean correctionEnabled = false;
        boolean autoCapitalization = false;
        boolean hiddenText = false;
        boolean focusChanged = false;

        TextInputStatusInfo keyboard = new TextInputStatusInfo();
        keyboard.setRawData(rawData);

        try {
            if (rawData.has("currentWidget")) {
                JSONObject currentWidget = (JSONObject) rawData.get("currentWidget");
                focused = (Boolean) currentWidget.get("focus");

                if (currentWidget.has("contentType")) {
                    contentType = (String) currentWidget.get("contentType");
                }
                if (currentWidget.has("predictionEnabled")) {
                    predictionEnabled = (Boolean) currentWidget.get("predictionEnabled");
                }
                if (currentWidget.has("correctionEnabled")) {
                    correctionEnabled = (Boolean) currentWidget.get("correctionEnabled");
                }
                if (currentWidget.has("autoCapitalization")) {
                    autoCapitalization = (Boolean) currentWidget.get("autoCapitalization");
                }
                if (currentWidget.has("hiddenText")) {
                    hiddenText = (Boolean) currentWidget.get("hiddenText");
                }
            }
            if (rawData.has("focusChanged")) 
                focusChanged = (Boolean) rawData.get("focusChanged");

        } catch (JSONException e) {
            e.printStackTrace();
        }

        keyboard.setFocused(focused);
        keyboard.setContentType(contentType);
        keyboard.setPredictionEnabled(predictionEnabled);
        keyboard.setCorrectionEnabled(correctionEnabled);
        keyboard.setAutoCapitalization(autoCapitalization);
        keyboard.setHiddenText(hiddenText);
        keyboard.setFocusChanged(focusChanged);

        return keyboard;
    }
}
