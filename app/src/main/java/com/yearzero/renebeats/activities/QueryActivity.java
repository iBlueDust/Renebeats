package com.yearzero.renebeats.activities;

import android.os.AsyncTask;
import android.os.Bundle;
import android.view.MenuItem;
import android.view.View;
import android.widget.Button;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.TextView;

import com.google.api.services.youtube.model.SearchResult;
import com.yearzero.renebeats.Commons;
import com.yearzero.renebeats.R;
import com.yearzero.renebeats.YoutubeQueryTask;
import com.yearzero.renebeats.adapters.QueryAdapter;
import com.yearzero.renebeats.classes.Query;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import androidx.appcompat.app.ActionBar;
import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

@Deprecated
public class QueryActivity extends AppCompatActivity implements YoutubeQueryTask.Callbacks {

    private static final String TAG = "QueryActivity";

//    private SwipeRefreshLayout Swipe;
    private ImageButton Home, Refresh;
    private RecyclerView List;
    private ImageView OfflineImg;
    private TextView OfflineMsg, Title;
    private Button OfflineAction;
//    private ProgressBar Loading;

    private QueryAdapter adapter;

    private String query;
    private List<SearchResult> queries;

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

//        Swipe = findViewById(R.id.swipe);
        Title = findViewById(R.id.title);
        List = findViewById(R.id.query_list);
        Home = findViewById(R.id.dismiss);
        Refresh = findViewById(R.id.refresh);
//        Loading = findViewById(R.id.loading);

        OfflineImg = findViewById(R.id.offline_img);
        OfflineMsg = findViewById(R.id.offline_msg);
        OfflineAction = findViewById(R.id.offline_action);

        adapter = new QueryAdapter(this, new ArrayList<>());

        List.setLayoutManager(new LinearLayoutManager(this));
        List.setAdapter(adapter);

        Title.setText(query);

        Home.setOnClickListener(v -> onBackPressed());
        Refresh.setOnClickListener(v -> Query());
//
        if (query != null && queries != null) {
            Title.setText(query);
            Refresh.setOnClickListener(view -> Query());
            Query();
        }
    }

    private void Query() {
        Query(query);
    }

    private void Query(String query) {
        this.query = query;
        queries = null;
        adapter.resetList(Collections.nCopies(Commons.Pref.query_amount, null));

        new YoutubeQueryTask(this, getPackageName())
            .setTimeout(Commons.Pref.timeout)
            .executeOnExecutor(AsyncTask.THREAD_POOL_EXECUTOR, query);
    }

    @Override
    public void onComplete(List<SearchResult> results) {
        int visi = View.GONE;
        if (results == null) {
            visi = View.VISIBLE;

            OfflineImg.setImageResource(R.drawable.ic_cloud_off_secondarydark_96dp);
            OfflineMsg.setText("It seems that we can't connect to YouTube. Please check your connection and try again later.");
            OfflineAction.setText("Retry");
            OfflineAction.setOnClickListener(v -> Query());
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
    }

    @Override
    public void onTimeout() {
        OfflineImg.setImageResource(R.drawable.ic_timer_lightgray_96dp);
        OfflineImg.setVisibility(View.VISIBLE);
        OfflineMsg.setText("Request timed out");
        OfflineMsg.setVisibility(View.VISIBLE);
        OfflineAction.setVisibility(View.VISIBLE);
        OfflineAction.setText("Retry");
        OfflineAction.setOnClickListener(v -> Query());
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        if (item.getItemId() == android.R.id.home) onBackPressed();
        else return super.onOptionsItemSelected(item);
        return true;
    }

}
