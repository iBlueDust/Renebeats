package com.yearzero.renebeats.Activities;

import android.content.ComponentName;
import android.content.Intent;
import android.content.ServiceConnection;
import android.content.pm.PackageManager;
import android.os.AsyncTask;
import android.os.Bundle;
import android.os.IBinder;
import android.util.Log;
import android.view.KeyEvent;
import android.view.View;
import android.widget.Button;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.TextView;

import com.google.android.material.button.MaterialButton;
import com.google.android.material.snackbar.Snackbar;
import com.google.android.material.textfield.TextInputEditText;
import com.google.api.services.youtube.model.SearchResult;
import com.sothree.slidinguppanel.SlidingUpPanelLayout;
import com.yearzero.renebeats.Adapters.DownloadAdapter;
import com.yearzero.renebeats.Adapters.QueryAdapter;
import com.yearzero.renebeats.Commons;
import com.yearzero.renebeats.Download;
import com.yearzero.renebeats.DownloadService;
import com.yearzero.renebeats.Query;
import com.yearzero.renebeats.R;
import com.yearzero.renebeats.YoutubeQueryTask;

import java.util.ArrayList;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import pub.devrel.easypermissions.EasyPermissions;

public class MainActivity extends AppCompatActivity implements ServiceConnection, DownloadService.ClientCallbacks, View.OnClickListener, View.OnKeyListener {
    private static final String TAG = "MainActivity";

    private SlidingUpPanelLayout SlideUp;

    private TextInputEditText Search;
    private ImageButton QueryBtn, SettingsBtn, HistoryBtn;

    private View ErrorCard;
    private ImageView ErrorImg;
    private TextView ErrorTitle, ErrorMsg;
    private MaterialButton ErrorAction;

    private TextView InfoTitle;
    private MaterialButton InfoAction;

    private RecyclerView List;
    private DownloadAdapter adapter;
    private DownloadService service;


    private ImageButton Refresh, Dismiss;
    private RecyclerView QueryList;
    private ImageView OfflineImg;
    private TextView OfflineMsg;
    private Button OfflineAction;

    private QueryAdapter queryAdapter;

    //TODO: Implement Download Header Features
    //TODO: History Activity and support
    //TODO: Preference Activity
    //TODO: Use XD QueryAdapter ViewHolder redesign

    @Override
    protected void onStart() {
        super.onStart();
        bindService(new Intent(this, DownloadService.class), this, 0);
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        SlideUp = findViewById(R.id.slide_up);

        Search = findViewById(R.id.search);
        SettingsBtn = findViewById(R.id.settings);
        HistoryBtn = findViewById(R.id.history);
        QueryBtn = findViewById(R.id.query);

        ErrorCard = findViewById(R.id.error_card);
        ErrorImg = findViewById(R.id.error_img);
        ErrorTitle = findViewById(R.id.error_title);
        ErrorMsg = findViewById(R.id.error_msg);
        ErrorAction = findViewById(R.id.error_action);

        InfoTitle = findViewById(R.id.info_title);
        InfoAction = findViewById(R.id.info_action);

        List = findViewById(R.id.list);

        Refresh = findViewById(R.id.refresh);
        Dismiss = findViewById(R.id.dismiss);
        QueryList = findViewById(R.id.query_list);

        OfflineImg = findViewById(R.id.offline_img);
        OfflineMsg = findViewById(R.id.offline_msg);
        OfflineAction = findViewById(R.id.offline_action);

        queryAdapter = new QueryAdapter(this, new ArrayList<>());

        List.setLayoutManager(new LinearLayoutManager(this));
        List.setAdapter(adapter);

        QueryList.setLayoutManager(new LinearLayoutManager(this));
        QueryList.setAdapter(queryAdapter);

//        OfflineAction.setOnClickListener(v -> {
//            Swipe.setRefreshing(true);
//            onRefresh();
//        });

        Search.requestFocus();

        try {
            String[] perms = getPackageManager().getPackageInfo(getPackageName(), PackageManager.GET_PERMISSIONS).requestedPermissions;
            if (!EasyPermissions.hasPermissions(this, perms))
                EasyPermissions.requestPermissions(this,
                        "This app requires the following permissions. Without them, some functionality will be lost",
                        Commons.PERM_REQUEST,
                        perms
                );

        } catch (PackageManager.NameNotFoundException e) {
            e.printStackTrace();
        }

        SlideUp.setAnchorPoint(0.85f);
        SlideUp.setPanelState(SlidingUpPanelLayout.PanelState.HIDDEN);

        Dismiss.setOnClickListener(view -> SlideUp.setPanelState(SlidingUpPanelLayout.PanelState.HIDDEN));

        Search.setOnKeyListener(this);
        QueryBtn.setOnClickListener(this);
    }

    private void Query(final String query) {
        new YoutubeQueryTask(new YoutubeQueryTask.Callbacks() {
            @Override
            public void onComplete(java.util.List<SearchResult> results) {
                int visi = View.GONE;
                if (results == null) {
                    visi = View.VISIBLE;

                    OfflineImg.setImageResource(R.drawable.ic_cloud_off_secondarydark_96dp);
                    OfflineMsg.setText("It seems that we can't connect to YouTube. Please check your connection and try again later.");
                    OfflineAction.setText("Retry");
                    OfflineAction.setOnClickListener(v -> {
                        //                        Swipe.setRefreshing(true);
                        Query(query);
                    });
                } else if (results.size() <= 0) {
                    visi = View.VISIBLE;

                    OfflineImg.setImageResource(R.drawable.ic_search_lightgray_96dp);
                    OfflineMsg.setText("Your search didn't come out with any results");
                    OfflineAction.setText("Back");
                    OfflineAction.setOnClickListener(v -> onBackPressed());
                } else queryAdapter.resetList(Query.CastList(results));

                OfflineImg.setVisibility(visi);
                OfflineMsg.setVisibility(visi);
                OfflineAction.setVisibility(visi);

                //                Swipe.setRefreshing(false);
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
                    //                    Swipe.setRefreshing(true);
                    Query(query);
                });
            }
        }, getPackageName())
                .setTimeout(Commons.Pref.timeout)
                .executeOnExecutor(AsyncTask.THREAD_POOL_EXECUTOR, query);

        queryAdapter.resetList();
        Refresh.setOnClickListener(view -> Query(query));
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        EasyPermissions.onRequestPermissionsResult(requestCode, permissions, grantResults);
    }

    @Override
    protected void onDestroy() {
        if (service != null) {
            if (service.removeCallbacks(this)) Log.i(TAG, "Service callback has been removed");
            else Log.e(TAG, "Failed to remove service callback");
        }
        unbindService(this);
        super.onDestroy();
    }

    @Override
    public void onServiceConnected(ComponentName componentName, IBinder iBinder) {
        service = ((DownloadService.LocalBinder) iBinder).getService();
        service.addCallbacks(this);
        adapter = new DownloadAdapter(this, service, List);
        List.setLayoutManager(new LinearLayoutManager(this));
        List.setAdapter(adapter);
    }

    @Override
    public void onServiceDisconnected(ComponentName componentName) {
        service.removeCallbacks(this);
        service = null;
        Log.w(TAG, "Service has been disconnected");
    }

    @Override
    public void onProgress(Download args, long progress, long max, boolean indeterminate) {
        adapter.onProgress(args, progress, max, indeterminate);
    }

    @Override
    public void onDone(Download args, boolean successful, Exception e) {
        adapter.onDone(args, successful, e);
    }

    @Override
    public void onWarn(Download args, String type) {
        adapter.onWarn(args, type);
        Log.w(TAG, (args.title == null ? "SERVICE " : '\'' + args.title + '\'') + ": " + type);
        Snackbar.make(findViewById(R.id.main), type, Snackbar.LENGTH_LONG).show();
    }

    @Override
    public void onClick(View view) {
        if(Search.getText().toString().trim().isEmpty()) return;

        String url = "";
        String query = Search.getText().toString();

        if(!(query.startsWith("http://") || query.startsWith("https://") || query.contains(" ")) && query.contains(".")) url = "https://";
        if(Pattern.compile("[\\w\\d]+\\.[\\w\\d]+", Pattern.CASE_INSENSITIVE).matcher(url).matches()) url += "www.";
        url += query;

        Matcher matcher = Pattern.compile("^https?://.*(?:youtu.be/|v/|u/\\w/|embed/|watch\\?v=)([^#&?]*).*$", Pattern.CASE_INSENSITIVE).matcher(url);
        if (matcher.matches()){
            Intent intent = new Intent(this, DownloadActivity.class);
            intent.putExtra(Commons.ARGS.DATA, new Query(matcher.group(1)));
            startActivity(intent);
        } else {
//            Intent intent = new Intent(this, QueryActivity.class);
//            intent.putExtra(Commons.ARGS.DATA, query);
//            startActivity(intent);
            Query(query);
            SlideUp.setPanelState(SlidingUpPanelLayout.PanelState.EXPANDED);
        }
    }

    @Override
    public void onBackPressed() {
        if (SlideUp != null && (SlideUp.getPanelState() == SlidingUpPanelLayout.PanelState.EXPANDED || SlideUp.getPanelState() == SlidingUpPanelLayout.PanelState.ANCHORED))
            SlideUp.setPanelState(SlidingUpPanelLayout.PanelState.HIDDEN);
    }

    @Override
    public boolean onKey(View view, int keyCode, KeyEvent keyEvent) {
        if (keyEvent.getAction() == KeyEvent.ACTION_UP && keyCode == KeyEvent.KEYCODE_ENTER) {
            onClick(Search);
            return true;
        }
        return false;
    }
}
