package com.connect.core;

import org.json.JSONException;
import org.json.JSONObject;

import android.util.Log;

/**
 * Normalized reference object for information about a TVs channels. This object is required to set the channel on a TV.
 */
public class ChannelInfo implements JSONSerializable {
    // @cond INTERNAL
    String channelName;
    String channelId;
    String channelNumber;
    int minorNumber;
    int majorNumber;

    JSONObject rawData;
    // @endcond

    /**
     * Default constructor method.
     */
    public ChannelInfo() {
    }

    /** Gets the raw data from the first screen device about the channel. In most cases, this is an NSDictionary. */
    public JSONObject getRawData() {
        return rawData;
    }

    /** Sets the raw data from the first screen device about the channel. In most cases, this is an NSDictionary. */
    public void setRawData(JSONObject rawData) {
        this.rawData = rawData;
    }

    /** Gets the user-friendly name of the channel */
    public String getName() {
        return channelName;
    }

    /** Sets the user-friendly name of the channel */
    public void setName(String channelName) {
        this.channelName = channelName;
    }

    /** Gets the TV's unique ID for the channel */
    public String getId() {
        return channelId;
    }

    /** Sets the TV's unique ID for the channel */
    public void setId(String channelId) {
        this.channelId = channelId;
    }

    /** Gets the TV channel's number (likely to be a combination of the major & minor numbers) */
    public String getNumber() {
        return channelNumber;
    }

    /** Sets the TV channel's number (likely to be a combination of the major & minor numbers) */
    public void setNumber(String channelNumber) {
        this.channelNumber = channelNumber;
    }

    /** Gets the TV channel's minor number */
    public int getMinorNumber() {
        return minorNumber;
    }

    /** Sets the TV channel's minor number */
    public void setMinorNumber(int minorNumber) {
        this.minorNumber = minorNumber;
    }

    /** Gets the TV channel's major number */
    public int getMajorNumber() {
        return majorNumber;
    }

    /** Sets the TV channel's major number */
    public void setMajorNumber(int majorNumber) {
        this.majorNumber = majorNumber;
    }

    @Override
    public boolean equals(Object o) {
        if (o instanceof ChannelInfo) {
            ChannelInfo other = (ChannelInfo) o;

            if (this.channelId != null) {
                if (this.channelId.equals(other.channelId))
                    return true;
            } else if (this.channelName != null && this.channelNumber != null) {
                return this.channelName.equals(other.channelName)
                        && this.channelNumber.equals(other.channelNumber)
                        && this.majorNumber == other.majorNumber
                        && this.minorNumber == other.minorNumber;
            }

            Log.d(Util.T, "Could not compare channel values, no data to compare against");
            Log.d(Util.T, "This channel info: \n" + this.rawData.toString());
            Log.d(Util.T, "Other channel info: \n" + other.rawData.toString());

            return false;
        }

        return super.equals(o);
    }

    // @cond INTERNAL
    @Override
    public JSONObject toJSONObject() throws JSONException {
        JSONObject obj = new JSONObject();

        obj.put("name", channelName);
        obj.put("id", channelId);
        obj.put("number", channelNumber);
        obj.put("majorNumber", majorNumber);
        obj.put("minorNumber", minorNumber);
        obj.put("rawData", rawData);

        return obj;
    }
    // @endcond
}
