package com.urqa.common;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;

import org.apache.http.HttpResponse;
import org.apache.http.client.HttpClient;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.entity.StringEntity;
import org.apache.http.impl.client.DefaultHttpClient;
import org.apache.http.params.HttpParams;
import org.json.JSONException;
import org.json.JSONObject;

import android.util.Base64;
import android.util.Log;

import com.urqa.Collector.ErrorReport;

public class SendErrorProcessURLConnection extends Thread {
	private ErrorReport report;
	private String url;
	private String filename;

	public SendErrorProcessURLConnection(ErrorReport report, String url,
			String FileName) {
		// TODO Auto-generated constructor stub
		this.report = report;
		this.url = url;
		this.filename = FileName;
	}

	@Override
	public void run() {		
		
		try{
			// # step 1 : init for reading
			FileInputStream fis = new FileInputStream(filename);
			File dmp_file = new File(filename);
			byte[] byteArr = new byte[(int) dmp_file.length()];
			// # step 2 : read file from image
			fis.read(byteArr);			
			fis.close();
			// # step 3 : image to String using Base64
			report.NativeData = Base64.encodeToString(byteArr,Base64.NO_WRAP);
			dmp_file.delete();
						
			HttpClient client = new DefaultHttpClient();
			setHttpParams(client.getParams());
			
			client.getParams().setParameter("http.protocol.expect-continue", false);
			client.getParams().setParameter("http.connection.timeout", 5000);
			client.getParams().setParameter("http.socket.timeout", 5000);
			
			HttpPost post = new HttpPost(StateData.ServerAddress + url);
			//Log.i("UrQA", String.format(StateData.ServerAddress + url));
			post.setHeader("Content-Type", "application/json; charset=utf-8");			
			post.setEntity(toEntity(report));
			
			HttpResponse response = client.execute(post); 
			int code =	response.getStatusLine().getStatusCode(); Log.i("UrQA",
			String.format("UrQA Response Code[Native] :: %d", code));
			
		} catch (Exception e) {
			e.printStackTrace();
		}
	}

	private void setHttpParams(HttpParams params) {
		params.setParameter("http.protocol.expect-continue", false);
		params.setParameter("http.connection.timeout", 5000);
		params.setParameter("http.socket.timeout", 5000);
	}

	private StringEntity toEntity(ErrorReport data) throws JSONException, IOException {
		final String DATA = setData(data);		
		return new StringEntity(DATA, "UTF-8");
	}

	private String setData(ErrorReport data) throws JSONException {
		JSONObject object = new JSONObject();
		object.put("console_log", getLog(data));
		object.put("exception", data.ErrorData.toJSONObject());
		object.put("instance", getId(data));
		object.put("version", data.mUrQAVersion);
		object.put("dump_data", data.NativeData);
		return object.toString();
	}

	private JSONObject getLog(ErrorReport data) throws JSONException {
		JSONObject map = new JSONObject();
		map.put("data", data.LogData);
		return map;
	}

	private JSONObject getId(ErrorReport data) throws JSONException {
		JSONObject map = new JSONObject();
		map.put("id", data.mId);

		return map;
	}
}