package com.connect.core;

import org.json.JSONException;
import org.json.JSONObject;

public interface JSONSerializable {
    JSONObject toJSONObject() throws JSONException;
}
