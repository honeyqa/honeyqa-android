package io.honeyqa.client.json;

import org.json.JSONException;
import org.json.JSONObject;

import io.honeyqa.client.json.JSONInterface;

public class JSONAppData implements JSONInterface {
    public String apikey;
    public String appversion;

    @Override
    public JSONObject toJSONObject() {
        JSONObject object = new JSONObject();
        try {
            object.put("apikey", apikey);
            object.put("appversion", appversion);
        } catch (JSONException e) {
        }
        return object;
    }
}
