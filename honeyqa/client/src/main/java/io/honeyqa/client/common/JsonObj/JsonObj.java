package io.honeyqa.client.common.JsonObj;

@Deprecated
public abstract class JsonObj
{

    public JsonObj()
    {
    }

    public abstract String toJson();

    public abstract void fromJson(String s);
}
