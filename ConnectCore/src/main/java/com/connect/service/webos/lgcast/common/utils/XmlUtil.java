package com.connect.service.webos.lgcast.common.utils;

import org.xmlpull.v1.XmlPullParser;
import org.xmlpull.v1.XmlPullParserFactory;

import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;

public class XmlUtil {
    public static String findElement(String serviceDescription, String elementName) {
        try {
            if (serviceDescription == null) throw new Exception("Invalid serviceDescription");
            if (elementName == null) throw new Exception("Invalid elementName");

            XmlPullParser parser = XmlPullParserFactory.newInstance().newPullParser();
            parser.setInput(new ByteArrayInputStream(serviceDescription.getBytes(StandardCharsets.UTF_8)), "UTF-8");

            int eventType = parser.getEventType();
            String tagName = null;

            while (eventType != XmlPullParser.END_DOCUMENT) {
                if (eventType == XmlPullParser.START_TAG) {
                    tagName = parser.getName();
                } else if (eventType == XmlPullParser.TEXT) {
                    if (tagName != null && tagName.equals(elementName)) return parser.getText();
                } else if (eventType == XmlPullParser.END_TAG) {
                    tagName = null;
                }
                eventType = parser.next();
            }
        } catch (Exception e) {
            Logger.error(e);
        }

        return null;
    }
}
