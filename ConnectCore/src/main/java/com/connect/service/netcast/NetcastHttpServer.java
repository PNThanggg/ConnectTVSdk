package com.connect.service.netcast;

import android.util.Log;

import com.connect.core.ChannelInfo;
import com.connect.core.TextInputStatusInfo;
import com.connect.core.Util;
import com.connect.service.NetcastTVService;
import com.connect.service.capability.listeners.ResponseListener;
import com.connect.service.command.URLServiceSubscription;

import org.json.JSONException;
import org.json.JSONObject;
import org.xml.sax.SAXException;

import java.io.BufferedReader;
import java.io.ByteArrayInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.net.ServerSocket;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.List;
import java.util.Locale;
import java.util.TimeZone;

import javax.xml.parsers.ParserConfigurationException;
import javax.xml.parsers.SAXParser;
import javax.xml.parsers.SAXParserFactory;

public class NetcastHttpServer {
    static final String UDAP_PATH_EVENT = "/udap/api/event";

    NetcastTVService service;
    ServerSocket welcomeSocket;
    ResponseListener<String> textChangedListener;

    int port = -1;

    List<URLServiceSubscription<?>> subscriptions;

    boolean running = false;

    public NetcastHttpServer(NetcastTVService service, int port, ResponseListener<String> textChangedListener) {
        this.service = service;
        this.port = port;
        this.textChangedListener = textChangedListener;
    }

    public void start() {
        if (running) return;

        running = true;

        try {
            welcomeSocket = new ServerSocket(this.port);
        } catch (IOException ex) {
            ex.printStackTrace();
        }

        while (running) {
            if (welcomeSocket == null || welcomeSocket.isClosed()) {
                stop();
                break;
            }

            Socket connectionSocket;
            BufferedReader inFromClient = null;
            DataOutputStream outToClient = null;

            try {
                connectionSocket = welcomeSocket.accept();
            } catch (IOException ex) {
                ex.printStackTrace();
                // this socket may have been closed, so we'll stop
                stop();
                return;
            }

            String str = null;
            int c;
            StringBuilder sb = new StringBuilder();

            try {
                inFromClient = new BufferedReader(new InputStreamReader(connectionSocket.getInputStream()));

                while ((str = inFromClient.readLine()) != null) {
                    if (str.isEmpty()) {
                        break;
                    }
                }

                while ((c = inFromClient.read()) != -1) {
                    sb.append((char) c);
                    String temp = sb.toString();

                    if (temp.endsWith("</envelope>")) break;
                }
            } catch (IOException ex) {
                ex.printStackTrace();
            }

            String body = sb.toString();

            Log.d(Util.T, "got message body: " + body);

            Calendar calendar = Calendar.getInstance();
            SimpleDateFormat dateFormat = new SimpleDateFormat("EEE, dd MMM yyyy HH:mm:ss z", Locale.US);
            dateFormat.setTimeZone(TimeZone.getTimeZone("GMT"));
            String date = dateFormat.format(calendar.getTime());
            String androidOSVersion = android.os.Build.VERSION.RELEASE;

            PrintWriter out = null;

            try {
                outToClient = new DataOutputStream(connectionSocket.getOutputStream());
                out = new PrintWriter(outToClient);
                out.println("HTTP/1.1 200 OK");
                out.println("Server: Android/" + androidOSVersion + " UDAP/2.0 ConnectSDK/1.2.1");
                out.println("Cache-Control: no-store, no-cache, must-revalidate");
                out.println("Date: " + date);
                out.println("Connection: Close");
                out.println("Content-Length: 0");
                out.println();
                out.flush();
            } catch (IOException ex) {
                ex.printStackTrace();
            } finally {
                try {
                    inFromClient.close();
                    out.close();
                    outToClient.close();
                    connectionSocket.close();
                } catch (IOException ex) {
                    ex.printStackTrace();
                }
            }

            SAXParserFactory saxParserFactory = SAXParserFactory.newInstance();
            InputStream stream = null;

            stream = new ByteArrayInputStream(body.getBytes(StandardCharsets.UTF_8));

            NetcastPOSTRequestParser handler = new NetcastPOSTRequestParser();

            SAXParser saxParser;
            try {
                saxParser = saxParserFactory.newSAXParser();
                saxParser.parse(stream, handler);
            } catch (IOException | SAXException | ParserConfigurationException ex) {
                ex.printStackTrace();
            }

            if (body.contains("ChannelChanged")) {
                ChannelInfo channel = NetcastChannelParser.parseRawChannelData(handler.getJSONObject());

                Log.d(Util.T, "Channel Changed: " + channel.getNumber());

                for (URLServiceSubscription<?> sub : subscriptions) {
                    if (sub.getTarget().equalsIgnoreCase("ChannelChanged")) {
                        for (int i = 0; i < sub.getListeners().size(); i++) {
                            @SuppressWarnings("unchecked") ResponseListener<Object> listener = (ResponseListener<Object>) sub.getListeners().get(i);
                            Util.postSuccess(listener, channel);
                        }
                    }
                }
            } else if (body.contains("KeyboardVisible")) {
                boolean focused = false;

                TextInputStatusInfo keyboard = new TextInputStatusInfo();
                keyboard.setRawData(handler.getJSONObject());

                try {
                    JSONObject currentWidget = (JSONObject) handler.getJSONObject().get("currentWidget");
                    focused = (Boolean) currentWidget.get("focus");
                    keyboard.setFocused(focused);
                } catch (JSONException e) {
                    e.printStackTrace();
                }

                Log.d(Util.T, "KeyboardFocused?: " + focused);

                for (URLServiceSubscription<?> sub : subscriptions) {
                    if (sub.getTarget().equalsIgnoreCase("KeyboardVisible")) {
                        for (int i = 0; i < sub.getListeners().size(); i++) {
                            @SuppressWarnings("unchecked") ResponseListener<Object> listener = (ResponseListener<Object>) sub.getListeners().get(i);
                            Util.postSuccess(listener, keyboard);
                        }
                    }
                }
            } else if (body.contains("TextEdited")) {
                System.out.println("TextEdited");

                String newValue = "";

                try {
                    newValue = handler.getJSONObject().getString("value");
                } catch (JSONException ex) {
                    ex.printStackTrace();
                }

                Util.postSuccess(textChangedListener, newValue);
            } else if (body.contains("3DMode")) {
                try {
                    String enabled = (String) handler.getJSONObject().get("value");
                    boolean bEnabled;

                    bEnabled = enabled.equalsIgnoreCase("true");

                    for (URLServiceSubscription<?> sub : subscriptions) {
                        if (sub.getTarget().equalsIgnoreCase("3DMode")) {
                            for (int i = 0; i < sub.getListeners().size(); i++) {
                                @SuppressWarnings("unchecked") ResponseListener<Object> listener = (ResponseListener<Object>) sub.getListeners().get(i);
                                Util.postSuccess(listener, bEnabled);
                            }
                        }
                    }
                } catch (JSONException e) {
                    e.printStackTrace();
                }
            }
        }
    }

    public void stop() {
        if (!running) return;

        if (welcomeSocket != null && !welcomeSocket.isClosed()) {
            try {
                welcomeSocket.close();
            } catch (IOException ex) {
                ex.printStackTrace();
            }
        }

        welcomeSocket = null;
        running = false;
    }

    public void setSubscriptions(List<URLServiceSubscription<?>> subscriptions) {
        this.subscriptions = subscriptions;
    }

}