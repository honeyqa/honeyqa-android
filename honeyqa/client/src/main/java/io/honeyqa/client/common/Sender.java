package io.honeyqa.client.common;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;

import org.json.JSONException;
import org.json.JSONObject;

import android.os.Handler;
import android.os.Message;
import android.util.Base64;
import android.util.Log;

import io.honeyqa.client.collector.ErrorReport;
import io.honeyqa.client.library.model.Authentication;


public class Sender {

    public static final String EXCEPTION_URL = HoneyQAData.ServerAddress + "client/send/exception";
    public static final String NATIVE_EXCEPTION_URL = HoneyQAData.ServerAddress
            + "client/send/exception/native";
    public static final String SESSION_URL = HoneyQAData.ServerAddress + "client/connect";

    public static void sendSession(Authentication auth, String url) {

        Network network = new Network();
        network.setNetworkOption(url, auth.toJSONObject().toString(), Network.Method.POST,
                HoneyQAData.isEncrypt);
        network.start();
    }

    public static void sendException(ErrorReport report, String url)
            throws JSONException {
        Handler handler = new Handler() {
            public void handleMessage(Message msg) {
                String response = (String) msg.obj;
                Log.e(HoneyQAData.HONEYQA_SDK_LOG, response);
            }
        };

        Network network = new Network();
        network.setNetworkOption(url, makeJsonStr(report), Network.Method.POST, HoneyQAData.isEncrypt);
        Log.e(HoneyQAData.HONEYQA_SDK_LOG, makeJsonStr(report));
        network.setHandler(handler);
        network.start();
    }

    public static void sendExceptionWithNative(ErrorReport report, String url,
                                               String fileName) {
        try {
            Network network = new Network();
            // # step 1 : init for reading
            FileInputStream fis = new FileInputStream(fileName);
            File dmp_file = new File(fileName);
            byte[] byteArr = new byte[(int) dmp_file.length()];

            // # step 2 : read file from image
            fis.read(byteArr);
            fis.close();

            // # step 3 : image to String using Base64
            report.NativeData = Base64.encodeToString(byteArr, Base64.NO_WRAP);

            // # step 4 : send data
            dmp_file.delete();
            network.setNetworkOption(url, makeJsonStrForNative(report),
                    Network.Method.POST, HoneyQAData.isEncrypt);
            network.start();

        } catch (IOException e) {
            // TODO Auto-generated catch block
            e.printStackTrace();
        } catch (JSONException e) {
            // TODO Auto-generated catch block
            e.printStackTrace();
        }
    }

    /**
     * create json string for non-native application
     *
     * @return String includes console_log, exception, instance, version
     */
    private static String makeJsonStr(ErrorReport data) throws JSONException {
        JSONObject object = new JSONObject();
        object.put("console_log", getLog(data));
        object.put("exception", data.ErrorData.toJSONObject());
        object.put("instance", getId(data));
        object.put("version", data.mUrQAVersion);
        return object.toString();
    }

    /**
     * create json string for native application
     *
     * @return String includes console_log, exception, instance, version, dump_data
     */
    private static String makeJsonStrForNative(ErrorReport data)
            throws JSONException {
        JSONObject object = new JSONObject();
        object.put("console_log", getLog(data));
        object.put("exception", data.ErrorData.toJSONObject());
        object.put("instance", getId(data));
        object.put("version", data.mUrQAVersion);
        object.put("dump_data", data.NativeData);
        return object.toString();
    }

    /**
     * extract logdata from ErrorReport
     *
     * @return JSONObject LogData
     */
    private static JSONObject getLog(ErrorReport data) throws JSONException {
        JSONObject map = new JSONObject();
        map.put("data", data.LogData);
        return map;
    }

    /**
     * extract id from ErrorReport
     *
     * @return JSONObject id
     */
    private static JSONObject getId(ErrorReport data) throws JSONException {
        JSONObject map = new JSONObject();
        map.put("id", data.mId);
        return map;
    }

}
