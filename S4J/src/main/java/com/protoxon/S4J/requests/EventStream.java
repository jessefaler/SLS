package com.protoxon.S4J.requests;

import org.json.JSONObject;

public class EventStream {

    public final String event;
    public final String data;

    private EventStream(String event, String data) {
        this.event = event;
        this.data = data;
    }

    public static EventStream parse(String raw) {
        String event = "message";
        StringBuilder dataBuilder = new StringBuilder();

        for (String line : raw.split("\n")) {
            if (line.startsWith("event:")) {
                event = line.substring(6).trim();
            } else if (line.startsWith("data:")) {
                dataBuilder.append(line.substring(5).trim()).append("\n");
            }
        }

        String data = dataBuilder.toString().trim();
        return new EventStream(event, data);
    }

    public JSONObject dataAsJson() {
        return new JSONObject(data);
    }
}
