package com.connect.service.netcast;

import org.json.JSONException;
import org.json.JSONObject;
import org.xml.sax.Attributes;
import org.xml.sax.SAXException;
import org.xml.sax.helpers.DefaultHandler;

public class NetcastPOSTRequestParser extends DefaultHandler {
    public JSONObject object;
    public JSONObject subObject;

    boolean textEditMode = false;
    boolean keyboardVisibleMode = false;

    public String value;

    public final String CHANNEL_TYPE = "chtype";
    public final String MAJOR = "major";
    public final String MINOR = "minor";
    public final String DISPLAY_MAJOR = "displayMajor";
    public final String DISPLAY_MINOR = "displayMinor";
    public final String SOURCE_INDEX = "sourceIndex";
    public final String PHYSICAL_NUM = "physicalNum";
    public final String CHANNEL_NAME = "chname";
    public final String PROGRAM_NAME = "progName";
    public final String AUDIO_CHANNEL = "audioCh";
    public final String INPUT_SOURCE_NAME = "inputSourceName";
    public final String INPUT_SOURCE_TYPE = "inputSourceType";
    public final String LABEL_NAME = "labelName";
    public final String INPUT_SOURCE_INDEX = "inputSourceIdx";

    public final String VALUE = "value";
    public final String MODE = "mode";
    public final String STATE = "state";

    public NetcastPOSTRequestParser() {
        object = new JSONObject();
        subObject = new JSONObject();
        value = null;
    }

    @Override
    public void startElement(String uri, String localName, String qName, Attributes attributes) {

    }

    @Override
    public void endElement(String uri, String localName, String qName) {
        try {
            System.out.println("XML key: " + qName + ", value: " + value);
            if (qName.equalsIgnoreCase(CHANNEL_TYPE)) {
                object.put("channelModeName", value);
            }
            else if (qName.equalsIgnoreCase(MAJOR)) {
                object.put("majorNumber", Integer.parseInt(value));
            }
            else if (qName.equalsIgnoreCase(DISPLAY_MAJOR)) {
                object.put("displayMajorNumber", Integer.parseInt(value));
            }
            else if (qName.equalsIgnoreCase(MINOR)) {
                object.put("minorNumber", Integer.parseInt(value));
            }
            else if (qName.equalsIgnoreCase(DISPLAY_MINOR)) {
                object.put("displayMinorNumber", Integer.parseInt(value));
            }
            else if (qName.equalsIgnoreCase(SOURCE_INDEX)) {
                object.put("sourceIndex", value);
            }
            else if (qName.equalsIgnoreCase(PHYSICAL_NUM)) {
                object.put("physicalNumber", Integer.parseInt(value));
            }
            else if (qName.equalsIgnoreCase(CHANNEL_NAME)) {
                object.put("channelName", value);
            }
            else if (qName.equalsIgnoreCase(PROGRAM_NAME)) {
                object.put("programName", value);
            }
            else if (qName.equalsIgnoreCase(AUDIO_CHANNEL)) {
                object.put("audioCh", value);
            }
            else if (qName.equalsIgnoreCase(INPUT_SOURCE_NAME)) {
                object.put("inputSourceName", value);
            }
            else if (qName.equalsIgnoreCase(INPUT_SOURCE_TYPE)) {
                object.put("inputSourceType", value);
            }
            else if (qName.equalsIgnoreCase(LABEL_NAME)) {
                object.put("labelName", value);
            }
            else if (qName.equalsIgnoreCase(INPUT_SOURCE_INDEX)) {
                object.put("inputSourceIndex", value);
            }
            else if (qName.equalsIgnoreCase(VALUE)) {
                if (keyboardVisibleMode) {
                    subObject.put("focus", value.equalsIgnoreCase("true"));
                    object.put("currentWidget", subObject);
                }
                else {
                    object.put("value", value);
                }
            }
            else if (qName.equalsIgnoreCase(MODE)) {
                if (keyboardVisibleMode) {
                    if (value.equalsIgnoreCase("default"))
                        subObject.put("hiddenText", false);
                    else 
                        subObject.put("hiddenText", true);
                    object.put("currentWidget", subObject);
                }
            }
            else if (qName.equalsIgnoreCase(STATE)) { 

            }
            else if (value != null && value.equalsIgnoreCase("KeyboardVisible")) {
                keyboardVisibleMode = true;

                try {
                    subObject.put("contentType", "normal");
                    subObject.put("focus", false);
                    subObject.put("hiddenText", false);
                    subObject.put("predictionEnabled", false);
                    subObject.put("correctionEnabled", false);
                    subObject.put("autoCapitalization", false);
                } catch (JSONException e) {
                    e.printStackTrace();
                }
            }
            else if (value != null && value.equalsIgnoreCase("TextEdited")) {
                textEditMode = true;
            }
            value = null;
        } catch (JSONException e) {
            e.printStackTrace();
        }
    }

    @Override
    public void characters(char[] ch, int start, int length) {
        value = new String(ch, start, length);
    }

    public JSONObject getJSONObject() {
        return object;
    }
}
