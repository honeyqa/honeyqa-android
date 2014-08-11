package com.urqa.common;

import java.io.DataOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.Charset;

import org.apache.http.HttpEntity;
import org.apache.http.HttpResponse;
import org.apache.http.ParseException;
import org.apache.http.client.HttpClient;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.entity.FileEntity;
import org.apache.http.entity.StringEntity;
import org.apache.http.impl.client.DefaultHttpClient;
import org.apache.http.params.HttpParams;
import org.apache.http.util.EntityUtils;
import org.json.JSONException;
import org.json.JSONObject;

import android.util.Log;

import com.urqa.Collector.ErrorReport;
import com.urqa.common.JsonObj.IDInstance;

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
		/*
		 * try { HttpClient client = new DefaultHttpClient();
		 * setHttpParams(client.getParams());
		 * 
		 * 
		 * HttpPost post = new HttpPost(StateData.ServerAddress + url);
		 * Log.i("UrQA", String.format(StateData.ServerAddress + url));
		 * post.setHeader("Content-Type", "application/json; charset=utf-8");
		 * post.setEntity(toEntity(report));
		 * 
		 * HttpResponse response = client.execute(post); int code =
		 * response.getStatusLine().getStatusCode(); Log.i("UrQA",
		 * String.format("UrQA Response Code[Native] :: %d", code));
		 * 
		 * 
		 * } catch (Exception e) { e.printStackTrace(); }
		 */

		// dump 보내기
		try {

			String idInstance = Long.toString(report.mId);

			HttpClient dumpclient = new DefaultHttpClient();

			HttpPost dumppost = new HttpPost(StateData.ServerAddress + url
					+ "/dump/?dumpId=" + idInstance);
			Log.i("UrQA",
					String.format(StateData.ServerAddress + url
							+ "/dump/?dumpId=" + idInstance));
			// HttpPost dumppost = new
			// HttpPost("www.ur-qa.com:49999/urqa/client/send/exception/native/dump/?dumpID="+idInstance);

			dumpclient.getParams().setParameter(
					"http.protocol.expect-continue", false);
			dumpclient.getParams()
					.setParameter("http.connection.timeout", 5000);
			dumpclient.getParams().setParameter("http.socket.timeout", 5000);

			// 1. 파일의 내용을 body 로 설정함
			String mergefile_name = filename + ".merge";
			String DATA = setData(report);
			// byte[] array = DATA.getBytes(Charset.forName("utf-8"));
			byte[] json_ary = DATA.getBytes("utf-8");
			int read_buf;
			FileInputStream in = new FileInputStream(filename);
			FileOutputStream out = new FileOutputStream(mergefile_name);

			out.write((json_ary.length & 0xFF) >> 24);
			out.write((json_ary.length & 0xFF) >> 18);
			out.write((json_ary.length & 0xFF) >> 8);
			out.write((json_ary.length & 0xFF));
			out.write(json_ary);
			while ((read_buf = in.read()) != -1) {
				out.write(read_buf);
			}
			out.close();
			in.close();
			Log.i("UrQA",
					String.format("UrQA NativeDump Path :: %s", mergefile_name));
			File file = new File(mergefile_name);
			FileEntity entity = new FileEntity(file, "binary/octet-stream");
			dumppost.setEntity(entity);

			if (file.exists()) {
				Log.d("URQATest", "File True");
			}
			HttpResponse response = dumpclient.execute(dumppost);
			int code = response.getStatusLine().getStatusCode();
			HttpEntity entity2 = response.getEntity();
			String responseString = EntityUtils.toString(entity2, "UTF-8");
			System.out.println(responseString);
			Log.i("UrQA",
					String.format("UrQA Response Code[NativeDump] :: %d", code));
			// file.delete();
		} catch (Exception e) {
			e.printStackTrace();
		}
	}

	private void setHttpParams(HttpParams params) {
		params.setParameter("http.protocol.expect-continue", false);
		params.setParameter("http.connection.timeout", 5000);
		params.setParameter("http.socket.timeout", 5000);
	}

	private StringEntity toEntity(ErrorReport data) throws JSONException,
			IOException {
		final String DATA = setData(data);
		return new StringEntity(DATA, "UTF-8");
	}

	private String setData(ErrorReport data) throws JSONException {
		JSONObject object = new JSONObject();
		object.put("console_log", getLog(data));
		object.put("exception", data.ErrorData.toJSONObject());
		object.put("instance", getId(data));
		object.put("version", data.mUrQAVersion);
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