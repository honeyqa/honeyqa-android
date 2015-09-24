package io.honeyqa.client.network;

import java.util.concurrent.TimeUnit;


import android.os.Handler;
import android.os.Message;
import android.util.Log;

import io.honeyqa.client.auth.Encryptor;
import io.honeyqa.client.data.HoneyQAData;
import io.honeyqa.client.network.okhttp.MediaType;
import io.honeyqa.client.network.okhttp.OkHttpClient;
import io.honeyqa.client.network.okhttp.Request;
import io.honeyqa.client.network.okhttp.RequestBody;
import io.honeyqa.client.network.okhttp.Response;

public class Network extends Thread {

    /**
     * HTTP Method
     * GET / POST
     */
    public enum Method {
        GET, POST
    }

    /**
     * Media type for HTTP header
     */
    public static final MediaType JSON
            = MediaType.parse("application/json; charset=utf-8");

    // Variables for communicate with server
    private boolean isEncrypt;
    private Method method;
    private String data, url;
    private Handler handler;

    /**
     * Check options (url / data / method)
     *
     * @throws IllegalStateException when url / data / method not set
     */
    private void checkAssert() {
        if (url == null || data == null || method == null)
            throw new IllegalStateException("you might miss setNetworkOption method");
    }

    /**
     * Set network options
     *
     * @param url       API Server URL
     * @param data
     * @param method
     * @param isEncrypt
     */
    public void setNetworkOption(String url, String data, Method method, boolean isEncrypt) {
        this.url = url;
        this.data = data;
        this.method = method;
        this.isEncrypt = isEncrypt;
    }

    public void setHandler(Handler handler) {
        this.handler = handler;
    }

    @Override
    public void run() {
        switch (method) {
            case GET:
                requestGet();
                break;
            case POST:
                requestPost();
                break;
        }
    }

    private void requestGet() {
        try {
            checkAssert();
            OkHttpClient client = new OkHttpClient();
            setTimeout(client);
            Request request = new Request.Builder()
                    .url(url)
                    .build();
            Response response = client.newCall(request).execute();
            if (handler != null) {
                Message msg = new Message();
                msg.obj = response.body().string();
                handler.sendMessage(msg);
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private void requestPost() {
        try {
            checkAssert();
            OkHttpClient client = new OkHttpClient();
            setTimeout(client);
            RequestBody body = RequestBody.create(JSON, data);
            Request.Builder r = new Request.Builder()
                    .header("Content-Type", "application/json; charset=utf-8")
                    .addHeader("version", "1.0.0")
                    .url(url)
                    .post(body);
            if (isEncrypt && Encryptor.baseKey != null && Encryptor.token != null) {
                r.addHeader("Urqa-Encrypt-Opt", "aes-256-cbc-pkcs5padding+base64");
                data = Encryptor.encrypt(data);
                Log.e(HoneyQAData.HONEYQA_SDK_LOG, data);
            }
            Response response = client.newCall(r.build()).execute();
            if (handler != null) {
                Message msg = new Message();
                msg.obj = response.body().string();
                handler.sendMessage(msg);
            }
            int statusCode = response.code();
            Log.e(HoneyQAData.HONEYQA_SDK_LOG, String.format("HONEYQA Response Code : %d", statusCode));
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private void setTimeout(OkHttpClient client) {
        client.setConnectTimeout(5, TimeUnit.SECONDS);
        client.setReadTimeout(5, TimeUnit.SECONDS);
    }

}
