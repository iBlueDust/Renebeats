package com.yearzero.renebeats.Activities;

import android.os.AsyncTask;
import android.os.Bundle;
import android.view.MenuItem;
import android.view.View;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.TextView;

import com.google.api.client.http.HttpRequest;
import com.google.api.client.http.HttpRequestInitializer;
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

        //TODO: Timeout

        query = bundle.getString(Commons.ARGS.DATA);

        ActionBar actionBar = getSupportActionBar();
        if (actionBar != null) actionBar.setDisplayShowTitleEnabled(false);

        Swipe = findViewById(R.id.swipe);
        List = findViewById(R.id.list);

        OfflineImg = findViewById(R.id.offline_img);
        OfflineMsg = findViewById(R.id.offline_msg);
        OfflineAction = findViewById(R.id.offline_action);

        adapter = new QueryAdapter(this, new ArrayList<Query>());

        List.setLayoutManager(new LinearLayoutManager(this));
        List.setAdapter(adapter);

        OfflineAction.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                Swipe.setRefreshing(true);
                onRefresh();
            }
        });

        Swipe.setOnRefreshListener(this);
        Swipe.setRefreshing(true);
        onRefresh();

    }

    @Override
    public void onRefresh() {
        new YoutubeQueryTask(new YoutubeQueryTask.OnCompleteListener() {
            @Override
            public void onComplete(List<SearchResult> results) {
                int visi = View.GONE;
                if (results == null) {
                    visi = View.VISIBLE;

                    OfflineImg.setImageResource(R.drawable.ic_cloud_off_secondarydark_96dp);
                    OfflineMsg.setText("It seems that we can't connect to YouTube. Please check your connection and try again later.");
                    OfflineAction.setText("Retry");
                    OfflineAction.setOnClickListener(new View.OnClickListener() {
                        @Override
                        public void onClick(View v) {
                            Swipe.setRefreshing(true);
                            onRefresh();
                        }
                    });
                } else if (results.size() <= 0) {
                    visi = View.VISIBLE;

                    OfflineImg.setImageResource(R.drawable.ic_search_secondarydark_96dp);
                    OfflineMsg.setText("Your search didn't come out with any results");
                    OfflineAction.setText("Back");
                    OfflineAction.setOnClickListener(new View.OnClickListener() {
                        @Override
                        public void onClick(View v) {
                            onBackPressed();
                        }
                    });
                } else adapter.resetList(Query.CastList(results));

                OfflineImg.setVisibility(visi);
                OfflineMsg.setVisibility(visi);
                OfflineAction.setVisibility(visi);

                Swipe.setRefreshing(false);
            }
        }, getPackageName()).executeOnExecutor(AsyncTask.THREAD_POOL_EXECUTOR, query);
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        if (item.getItemId() == android.R.id.home) onBackPressed();
        else return super.onOptionsItemSelected(item);
        return true;
    }

    public static class YoutubeQueryTask extends AsyncTask<String, Void, List<SearchResult>> {

        public interface OnCompleteListener {
            void onComplete(List<SearchResult> results);
        }

        private OnCompleteListener listener;
        private String pkgName;

        public YoutubeQueryTask(OnCompleteListener listener, @NonNull String pkgName) {
            this.listener = listener;
            this.pkgName = pkgName;
        }

        @Override
        protected List<SearchResult> doInBackground(String... query) {
            try {
                YouTube youtube = new YouTube.Builder(new NetHttpTransport(), new JacksonFactory(), new HttpRequestInitializer() {
                    public void initialize(HttpRequest request) {
                    }
                }).setApplicationName(pkgName).build();

                YouTube.Search.List search = youtube.search().list("id,snippet");
                search.setKey(Commons.YT_API_KEY);
                search.setQ(query[0]);

                search.setType("video");
                search.setMaxResults(25L);

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
