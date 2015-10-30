package io.honeyqa.client.network;

import java.util.concurrent.TimeUnit;


import android.os.Handler;
import android.os.Message;
import android.util.Log;

import io.honeyqa.client.auth.Encryptor;
import io.honeyqa.client.data.HoneyQAData;
import io.honeyqa.client.network.okhttp.HQ_MediaType;
import io.honeyqa.client.network.okhttp.HQ_OkHttpClient;
import io.honeyqa.client.network.okhttp.HQ_Request;
import io.honeyqa.client.network.okhttp.HQ_RequestBody;
import io.honeyqa.client.network.okhttp.HQ_Response;

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
    public static final HQ_MediaType JSON
            = HQ_MediaType.parse("application/json; charset=utf-8");

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
            HQ_OkHttpClient client = new HQ_OkHttpClient();
            setTimeout(client);
            HQ_Request request = new HQ_Request.Builder()
                    .url(url)
                    .build();
            HQ_Response response = client.newCall(request).execute();
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
            HQ_OkHttpClient client = new HQ_OkHttpClient();
            setTimeout(client);
            HQ_RequestBody body = HQ_RequestBody.create(JSON, data);
            HQ_Request.Builder r = new HQ_Request.Builder()
                    .header("Content-Type", "application/json; charset=utf-8")
                    .addHeader("version", "1.0.0")
                    .url(url)
                    .post(body);
            if (isEncrypt && Encryptor.baseKey != null && Encryptor.token != null) {
                r.addHeader("HoneyQA-Encrypt-Opt", "aes-256-cbc-pkcs5padding+base64");
                data = Encryptor.encrypt(data);
                Log.e(HoneyQAData.HONEYQA_SDK_LOG, data);
            }
            HQ_Response response = client.newCall(r.build()).execute();
            if (handler != null) {
                Message msg = new Message();
                msg.obj = response.body().string();
                handler.sendMessage(msg);
            }
            int statusCode = response.code();
            Log.e(HoneyQAData.HONEYQA_SDK_LOG, String.format("HoneyQA HQ_Response Code : %d", statusCode));
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private void setTimeout(HQ_OkHttpClient client) {
        client.setConnectTimeout(5, TimeUnit.SECONDS);
        client.setReadTimeout(5, TimeUnit.SECONDS);
    }

}
