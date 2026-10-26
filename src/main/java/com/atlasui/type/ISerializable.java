package com.atlasui.type;

import org.json.JSONObject;

public interface ISerializable
{
    default JSONObject Serialize()
    {
        return OnSerialize();
    }

    default void Deserialize(JSONObject _JSON)
    {
        OnDeserialize(_JSON);
    }

    JSONObject OnSerialize();
    void OnDeserialize(JSONObject JSON);
}
