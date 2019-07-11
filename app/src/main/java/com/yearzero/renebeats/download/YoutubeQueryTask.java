package com.yearzero.renebeats.download;

import android.os.AsyncTask;
import android.os.CountDownTimer;

import androidx.annotation.NonNull;

import com.google.api.client.http.javanet.NetHttpTransport;
import com.google.api.client.json.jackson2.JacksonFactory;
import com.google.api.services.youtube.YouTube;
import com.google.api.services.youtube.model.SearchListResponse;
import com.google.api.services.youtube.model.SearchResult;
import com.yearzero.renebeats.BuildConfig;
import com.yearzero.renebeats.preferences.Preferences;

import java.util.List;

public class YoutubeQueryTask extends AsyncTask<String, Void, List<SearchResult>> {

    public interface Callbacks {
        void onComplete(List<SearchResult> results);

        void onTimeout();
    }

    private Callbacks listener;
    private String pkgName;
    private int timeout;

    YoutubeQueryTask(Callbacks listener, @NonNull String pkgName) {
        this.listener = listener;
        this.pkgName = pkgName;
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
            YouTube youtube = new YouTube.Builder(new NetHttpTransport(), new JacksonFactory(), request -> {})
                    .setApplicationName(pkgName)
                    .build();

            YouTube.Search.List search = youtube.search().list("id,snippet");
            search.setKey(BuildConfig.YT_API_KEY);
            search.setQ(query[0]);

            search.setType("video");
            search.setMaxResults((long) Preferences.getQuery_amount());

            SearchListResponse searchResponse = search.execute();
            return searchResponse.getItems();
        } catch (Exception e) {
            e.printStackTrace();
        }
        return null;
    }

    @Override
    protected void onPostExecute(List<SearchResult> results) {
        if (listener != null) listener.onComplete(results);
    }
}
