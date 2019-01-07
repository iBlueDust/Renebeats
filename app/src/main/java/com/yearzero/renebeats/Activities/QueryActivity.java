package com.yearzero.renebeats.Activities;

import android.os.AsyncTask;
import android.os.Bundle;
import android.os.CountDownTimer;
import android.view.MenuItem;
import android.view.View;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.TextView;

import com.google.api.client.http.javanet.NetHttpTransport;
import com.google.api.client.json.jackson2.JacksonFactory;
import com.google.api.services.youtube.YouTube;
import com.google.api.services.youtube.model.SearchListResponse;
import com.google.api.services.youtube.model.SearchResult;
import com.yearzero.renebeats.Adapters.QueryAdapter;
import com.yearzero.renebeats.Commons;
import com.yearzero.renebeats.Query;
import com.yearzero.renebeats.R;

import java.util.ArrayList;
import java.util.List;

import androidx.annotation.NonNull;
import androidx.appcompat.app.ActionBar;
import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout;

public class QueryActivity extends AppCompatActivity implements SwipeRefreshLayout.OnRefreshListener {

    private static final String TAG = "QueryActivity";

    private SwipeRefreshLayout Swipe;
    private RecyclerView List;
    private ImageView OfflineImg;
    private TextView OfflineMsg;
    private Button OfflineAction;

    private QueryAdapter adapter;

    private String query;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_query);

        Bundle bundle = getIntent().getExtras();
        if (bundle == null || bundle.getString(Commons.ARGS.DATA) == null) {
            onBackPressed();
            return;
        }

        query = bundle.getString(Commons.ARGS.DATA);

        ActionBar actionBar = getSupportActionBar();
        if (actionBar != null) actionBar.setDisplayShowTitleEnabled(false);

        Swipe = findViewById(R.id.swipe);
        List = findViewById(R.id.list);

        OfflineImg = findViewById(R.id.offline_img);
        OfflineMsg = findViewById(R.id.offline_msg);
        OfflineAction = findViewById(R.id.offline_action);

        adapter = new QueryAdapter(this, new ArrayList<>());

        List.setLayoutManager(new LinearLayoutManager(this));
        List.setAdapter(adapter);

        OfflineAction.setOnClickListener(v -> {
            Swipe.setRefreshing(true);
            onRefresh();
        });

        Swipe.setOnRefreshListener(this);
        Swipe.setRefreshing(true);
        onRefresh();

    }

    @Override
    public void onRefresh() {
        new YoutubeQueryTask(new YoutubeQueryTask.Callbacks() {
            @Override
            public void onComplete(List<SearchResult> results) {
                int visi = View.GONE;
                if (results == null) {
                    visi = View.VISIBLE;

                    OfflineImg.setImageResource(R.drawable.ic_cloud_off_secondarydark_96dp);
                    OfflineMsg.setText("It seems that we can't connect to YouTube. Please check your connection and try again later.");
                    OfflineAction.setText("Retry");
                    OfflineAction.setOnClickListener(v -> {
                        Swipe.setRefreshing(true);
                        onRefresh();
                    });
                } else if (results.size() <= 0) {
                    visi = View.VISIBLE;

                    OfflineImg.setImageResource(R.drawable.ic_search_lightgray_96dp);
                    OfflineMsg.setText("Your search didn't come out with any results");
                    OfflineAction.setText("Back");
                    OfflineAction.setOnClickListener(v -> onBackPressed());
                } else adapter.resetList(Query.CastList(results));

                OfflineImg.setVisibility(visi);
                OfflineMsg.setVisibility(visi);
                OfflineAction.setVisibility(visi);

                Swipe.setRefreshing(false);
            }

            @Override
            public void onTimeout() {
                OfflineImg.setImageResource(R.drawable.ic_timer_lightgray_96dp);
                OfflineImg.setVisibility(View.VISIBLE);
                OfflineMsg.setText("Request timed out");
                OfflineMsg.setVisibility(View.VISIBLE);
                OfflineAction.setVisibility(View.VISIBLE);
                OfflineAction.setText("Retry");
                OfflineAction.setOnClickListener(v -> {
                    Swipe.setRefreshing(true);
                    onRefresh();
                });
            }
        }, getPackageName())
            .setTimeout(Commons.Pref.timeout)
            .executeOnExecutor(AsyncTask.THREAD_POOL_EXECUTOR, query);
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        if (item.getItemId() == android.R.id.home) onBackPressed();
        else return super.onOptionsItemSelected(item);
        return true;
    }

    public static class YoutubeQueryTask extends AsyncTask<String, Void, List<SearchResult>> {

        public interface Callbacks {
            void onComplete(List<SearchResult> results);
            void onTimeout();
        }

        private Callbacks listener;
        private String pkgName;
        private int timeout;

        public YoutubeQueryTask(Callbacks listener, @NonNull String pkgName) {
            this.listener = listener;
            this.pkgName = pkgName;
        }

        public YoutubeQueryTask setTimeout(int timeout){
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
                search.setKey(Commons.YT_API_KEY);
                search.setQ(query[0]);

                search.setType("video");
                search.setMaxResults((long) Commons.Pref.query_amount);

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

}
