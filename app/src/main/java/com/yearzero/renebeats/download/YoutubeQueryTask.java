package com.yearzero.renebeats.download;

import android.os.AsyncTask;
import android.os.CountDownTimer;
import android.util.Log;

import androidx.annotation.NonNull;

import com.google.api.client.googleapis.json.GoogleJsonResponseException;
import com.google.api.client.http.javanet.NetHttpTransport;
import com.google.api.client.json.jackson2.JacksonFactory;
import com.google.api.services.youtube.YouTube;
import com.google.api.services.youtube.model.SearchListResponse;
import com.google.api.services.youtube.model.SearchResult;
import com.yearzero.renebeats.BuildConfig;
import com.yearzero.renebeats.preferences.Preferences;

import java.util.List;

public class YoutubeQueryTask extends AsyncTask<String, Void, List<SearchResult>> {

	private static final String TAG = "YoutubeQueryTask";

	public interface Callbacks {
		void onComplete(List<SearchResult> results);
		void onError(Exception e);
		void onTimeout();
	}

	private Callbacks listener;
	private String pkgName;
	private String sha1;
	private int timeout;

	private Exception exception;

	YoutubeQueryTask(Callbacks listener, @NonNull String pkgName) {
		this.listener = listener;
		this.pkgName = pkgName;
	}

	YoutubeQueryTask setSignature(String sha1) {
		this.sha1 = sha1;
		return this;
	}

	YoutubeQueryTask setTimeout(int timeout) {
		this.timeout = timeout;
		return this;
	}

	@Override
	protected void onPreExecute() {
		new CountDownTimer(timeout, timeout) {
			@Override
			public void onTick(long l) {}

			@Override
			public void onFinish() {
				if (getStatus() == Status.RUNNING) {
					cancel();
					if (listener != null) listener.onTimeout();
				}
			}
		}.start();
		super.onPreExecute();
	}

	@Override
	protected List<SearchResult> doInBackground(String... query) {
		try {
			YouTube youtube = new YouTube.Builder(new NetHttpTransport(), new JacksonFactory(), request -> {
				request.getHeaders().set("X-Android-Package", pkgName);
				if (sha1 != null) request.getHeaders().set("X-Android-Cert", sha1);
			})
					.setApplicationName(pkgName)
					.build();

			YouTube.Search.List search = youtube.search().list("id,snippet");
			search.setKey(BuildConfig.YT_API_KEY);
			search.setQ(query[0]);

			search.setType("video");
			search.setMaxResults((long) Preferences.getQuery_amount());

			SearchListResponse searchResponse = search.execute();
			return searchResponse.getItems();
		} catch (GoogleJsonResponseException e) {
			Log.e(TAG, "Error code: " + e.getStatusCode());
			exception = e;
		} catch (Exception e) {
			e.printStackTrace();
			exception = e;
		}
		return null;
	}

	@Override
	protected void onPostExecute(List<SearchResult> results) {
		if (listener != null) {
			listener.onComplete(results);
			if (results == null && exception != null)
				listener.onError(exception);
		}
	}
}
