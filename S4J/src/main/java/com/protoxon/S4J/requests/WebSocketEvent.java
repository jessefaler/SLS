package com.protoxon.S4J.requests;

import org.json.JSONObject;

public class WebSocketEvent {

    public final String topic;
    public final JSONObject data;

    private WebSocketEvent(String topic, JSONObject data) {
        this.topic = topic;
        this.data = data;
    }

    public static WebSocketEvent parse(String json) {
        JSONObject obj = new JSONObject(json);
        String topic = obj.optString("Topic", "message");
        JSONObject data = obj.optJSONObject("Data");
        if (data == null) {
            // If Data is not a JSONObject, create one with the raw value
            Object dataValue = obj.opt("Data");
            if (dataValue != null) {
                data = new JSONObject().put("value", dataValue);
            } else {
                data = new JSONObject();
            }
        }
        return new WebSocketEvent(topic, data);
    }

    public JSONObject dataAsJson() {
        return data;
    }
}

