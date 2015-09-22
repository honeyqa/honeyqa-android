package io.honeyqa.client.common;

import android.content.Context;

public class HoneyQAData {
    public static final String HONEYQA_SDK_LOG = "honeyqa";
    public static String SDKVersion = "0.98";
    public static String ServerAddress = "http://ur-qa.com/urqa/";
    public static Context AppContext = null;
    public static String APIKEY = "";
    public static String SessionID = "";
    public static boolean FirstConnect = true;
    public static boolean ToggleLogCat = true;
    public static int LogLine = 20;
    public static boolean TransferLog = true;
    public static String LogFilter = "";
    public static boolean isEncrypt;
}
