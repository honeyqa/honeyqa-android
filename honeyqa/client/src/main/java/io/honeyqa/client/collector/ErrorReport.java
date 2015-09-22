package io.honeyqa.client.collector;


import io.honeyqa.client.common.JsonObj.ErrorSendData;

public class ErrorReport {
    public long mId;
    public String mHoneyQAVersion;
    public ErrorSendData ErrorData;
    public String LogData;
    public String NativeData;
}
