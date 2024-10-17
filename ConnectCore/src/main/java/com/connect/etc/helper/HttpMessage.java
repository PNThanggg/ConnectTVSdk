package com.connect.etc.helper;

import org.apache.http.client.methods.HttpDelete;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.client.methods.HttpPost;

import java.io.UnsupportedEncodingException;
import java.net.URLDecoder;
import java.net.URLEncoder;

public class HttpMessage {
    public static String CONTENT_TYPE_HEADER = "Content-Type";
    public static String CONTENT_TYPE_TEXT_XML = "text/xml; charset=utf-8";
    public static String CONTENT_TYPE_APPLICATION_PLIST = "application/x-apple-binary-plist";
    public static String UDAP_USER_AGENT = "UDAP/2.0";
    public static String LG_ELECTRONICS = "LG Electronics";
    public static String USER_AGENT = "User-Agent";
    public static String SOAP_ACTION = "\"urn:schemas-upnp-org:service:AVTransport:1#%s\"";
    public static String SOAP_HEADER = "Soapaction";
    public static String NEW_LINE = "\r\n";

    public static HttpPost getHttpPost(String uri) {
        HttpPost post = null;
        try {
            post = new HttpPost(uri);
            post.setHeader("Content-Type", CONTENT_TYPE_TEXT_XML);
        } catch (IllegalArgumentException e) {
            e.printStackTrace();
        }

        return post;
    }

    public static HttpPost getUDAPHttpPost(String uri) {
        HttpPost post = getHttpPost(uri);
        post.setHeader("User-Agent", UDAP_USER_AGENT);

        return post;
    }

    public static HttpPost getDLNAHttpPost(String uri, String action) {
        String soapAction = "\"urn:schemas-upnp-org:service:AVTransport:1#" + action + "\"";

        HttpPost post = getHttpPost(uri);
        post.setHeader("Soapaction", soapAction);

        return post;
    }

    public static HttpPost getDLNAHttpPostRenderControl(String uri, String action) {
        String soapAction = "\"urn:schemas-upnp-org:service:RenderingControl:1#" + action + "\"";

        HttpPost post = getHttpPost(uri);
        post.setHeader("Soapaction", soapAction);

        return post;
    }

    public static HttpGet getHttpGet(String url) {
        return new HttpGet(url);
    }

    public static HttpGet getUDAPHttpGet(String uri) { 
        HttpGet get = getHttpGet(uri);
        get.setHeader("User-Agent", UDAP_USER_AGENT);

        return get;
    }

    public static HttpDelete getHttpDelete(String url) {
        return new HttpDelete(url);
    }

    public static String encode(String str) {
        try {
            return URLEncoder.encode(str, "UTF-8");
        } catch (UnsupportedEncodingException e) {
            e.printStackTrace();
        }
        return null;
    }

    public static String decode(String str) {
        try {
            return URLDecoder.decode(str, "UTF-8");
        } catch (UnsupportedEncodingException e) {
            e.printStackTrace();
        }
        return null;
    }
}
