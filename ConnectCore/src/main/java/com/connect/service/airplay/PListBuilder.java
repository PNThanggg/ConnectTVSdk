package com.connect.service.airplay;

import androidx.annotation.NonNull;

import org.w3c.dom.DOMImplementation;
import org.w3c.dom.Document;
import org.w3c.dom.DocumentType;
import org.w3c.dom.Element;

import java.io.StringWriter;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;
import javax.xml.transform.OutputKeys;
import javax.xml.transform.Transformer;
import javax.xml.transform.TransformerException;
import javax.xml.transform.TransformerFactory;
import javax.xml.transform.dom.DOMSource;
import javax.xml.transform.stream.StreamResult;

public class PListBuilder {
    DocumentType dt;
    Document doc;

    Element root;
    Element rootDict;

    public PListBuilder() {
        try {
            DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
            DocumentBuilder builder;
            builder = factory.newDocumentBuilder();
            DOMImplementation di = builder.getDOMImplementation();
            dt = di.createDocumentType("plist", "-//Apple//DTD PLIST 1.0//EN", "http://www.apple.com/DTDs/PropertyList-1.0.dtd");

            doc = di.createDocument("", "plist", dt);
            doc.setXmlStandalone(true);

            root = doc.getDocumentElement();
            root.setAttribute("version", "1.0");

            rootDict = doc.createElement("dict");
            root.appendChild(rootDict);
        } catch (ParserConfigurationException e) {
            e.printStackTrace();
        }
    }

    private void putKey(String key) {
        Element eKey = doc.createElement("key");
        eKey.setTextContent(key);
        rootDict.appendChild(eKey);
    }

    public void putString(String key, String value) {
        putKey(key);

        Element eValue = doc.createElement("string");
        eValue.setTextContent(value);
        rootDict.appendChild(eValue);
    }

    public void putReal(String key, double value) {
        putKey(key);

        Element eValue = doc.createElement("real");
        eValue.setTextContent(String.valueOf(value));
        rootDict.appendChild(eValue);
    }

    public void putInteger(String key, long value) {
        putKey(key);

        Element eValue = doc.createElement("integer");
        eValue.setTextContent(String.valueOf(value));
        rootDict.appendChild(eValue);
    }

    public void putBoolean(String key, boolean value) {
        putKey(key);

        String str = value ? "true" : "false";
        Element eValue = doc.createElement(str);
        rootDict.appendChild(eValue);
    }

    public void putData(String key, String value) {
        putKey(key);

        Element eValue = doc.createElement("data");
        eValue.setTextContent(value);
        rootDict.appendChild(eValue);
    }

    @NonNull
    @Override
    public String toString() {
        DOMSource domSource = new DOMSource(doc);
        TransformerFactory tf = TransformerFactory.newInstance();
        Transformer t;

        StringWriter stringWriter = new StringWriter();

        try {
            t = tf.newTransformer();
            t.setOutputProperty(OutputKeys.ENCODING, "UTF-8");
            t.setOutputProperty(OutputKeys.DOCTYPE_PUBLIC, dt.getPublicId());
            t.setOutputProperty(OutputKeys.DOCTYPE_SYSTEM, dt.getSystemId());
            t.setOutputProperty(OutputKeys.INDENT, "yes");
            t.setOutputProperty("{http://xml.apache.org/xslt}indent-amount", "2");

            StreamResult streamResult = new StreamResult(stringWriter);
            t.transform(domSource, streamResult);
        } catch (TransformerException e) {
            e.printStackTrace();
        }

        return stringWriter.toString();
    }
}
