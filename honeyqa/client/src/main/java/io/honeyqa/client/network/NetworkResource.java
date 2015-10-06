package io.honeyqa.client.network;

/**
 * Created by devholic on 15. 9. 23..
 */
public class NetworkResource {
    public static final String SERVER_URL = "http://honeyqa.io";
    public static final String EXCEPTION_URL = SERVER_URL + "/api/client/exception";
    public static final String NATIVE_EXCEPTION_URL = SERVER_URL
            + "/api/client/exception/native";
    public static final String SESSION_URL = SERVER_URL + "/api/client/session";
    public static final String REQUEST_KEY_URL = SERVER_URL + "/api/client/key";
}
