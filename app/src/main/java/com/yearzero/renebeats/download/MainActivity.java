package com.yearzero.renebeats.download;

import android.app.Activity;
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
import android.view.WindowManager;
import android.view.inputmethod.InputMethodManager;
import android.widget.Button;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.LinearSmoothScroller;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.material.button.MaterialButton;
import com.google.android.material.snackbar.Snackbar;
import com.google.android.material.textfield.TextInputEditText;
import com.google.api.services.youtube.model.SearchResult;
import com.sothree.slidinguppanel.SlidingUpPanelLayout;
import com.yearzero.renebeats.Commons;
import com.yearzero.renebeats.preferences.PreferenceActivity;
import com.yearzero.renebeats.R;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import pub.devrel.easypermissions.EasyPermissions;

//TODO: Implement Offline/Error Card

public class MainActivity extends AppCompatActivity implements ServiceConnection, DownloadService.ClientCallbacks, View.OnClickListener, View.OnKeyListener, YoutubeQueryTask.Callbacks {
    private static final String TAG = "MainActivity";

    private SlidingUpPanelLayout SlideUp;

    private TextInputEditText Search;
    private ImageButton QueryBtn, SettingsBtn, HistoryBtn;

    private View ErrorCard;
    private ImageView ErrorImg;
    private TextView ErrorTitle, ErrorMsg;
    private MaterialButton ErrorAction;

    private ImageView ScrollImg;
    private TextView InfoTitle;
    private Button InfoAction;

    private RecyclerView List;
    private DownloadAdapter adapter;
    private DownloadService service;

    private ImageButton Refresh, Dismiss;
    private RecyclerView QueryList;
//    private ProgressBar QueryLoading;
//    private ShimmerFrameLayout Shimmer;
    private ImageView OfflineImg;
    private TextView OfflineMsg, Title;
    private Button OfflineAction;

    private QueryAdapter queryAdapter;
    private LinearLayoutManager manager;

    private static List<SearchResult> queries;
    private static String query;

//    private boolean querying;

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

        ScrollImg = findViewById(R.id.scroll_img);
        InfoTitle = findViewById(R.id.info_title);
        InfoAction = findViewById(R.id.info_action);

        List = findViewById(R.id.list);

        Refresh = findViewById(R.id.refresh);
        Dismiss = findViewById(R.id.dismiss);
        QueryList = findViewById(R.id.query_list);
//        QueryLoading = findViewById(R.id.loading);
//        Shimmer = findViewById(R.id.shimmer);
        Title = findViewById(R.id.title);

        OfflineImg = findViewById(R.id.offline_img);
        OfflineMsg = findViewById(R.id.offline_msg);
        OfflineAction = findViewById(R.id.offline_action);

        queryAdapter = new QueryAdapter(this, new ArrayList<>());

        manager = new LinearLayoutManager(this);

        QueryList.setLayoutManager(new LinearLayoutManager(this));
        QueryList.setAdapter(queryAdapter);

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

//        SlideUp.setScrollableViewHelper(new NestedScrollableViewHelper(NestedList));
//        SlideUp.setPanelState(SlidingUpPanelLayout.PanelState.HIDDEN);
//        SlideUp.setScrollableView(NestedList);

        Dismiss.setOnClickListener(view -> {
            SlideUp.setPanelState(SlidingUpPanelLayout.PanelState.HIDDEN);
            queries = null;
            query = null;
        });

        Search.setOnClickListener(view -> {
            if (SlideUp.getPanelState() == SlidingUpPanelLayout.PanelState.ANCHORED || SlideUp.getPanelState() == SlidingUpPanelLayout.PanelState.EXPANDED)
                SlideUp.setPanelState(SlidingUpPanelLayout.PanelState.COLLAPSED);
        });
        Search.setOnKeyListener(this);
        QueryBtn.setOnClickListener(this);

        int index = getIntent().getIntExtra(Commons.ARGS.INDEX, -1);
        if (index > 0 && List != null && manager.findFirstCompletelyVisibleItemPosition() <= index && manager.findLastCompletelyVisibleItemPosition() >= index) {
            RecyclerView.SmoothScroller smoothScroller = new LinearSmoothScroller(this) {
                @Override
                protected int getVerticalSnapPreference() {
                    return LinearSmoothScroller.SNAP_TO_START;
                }
            };
            smoothScroller.setTargetPosition(index);
            manager.startSmoothScroll(smoothScroller);
        }

//        HistoryBtn.setOnClickListener(v -> new Commons.History.RetrieveRangeTask().setCallback(new Commons.History.Callback<Integer, List<HistoryLog[]>>() {
//            @Override
//            public void onComplete(java.util.List<HistoryLog[]> data) {
//                Log.d(TAG, data.size() < 1 ? "RETRIEVE INVALID LENGTH" : "RETRIEVE > " + new GsonBuilder().setPrettyPrinting().create().toJson(data.get(0)));
//            }
//
//            @Override
//            public void onError(Integer location, Exception e) {
//                Log.e(TAG, String.format(Locale.ENGLISH, "RETRIEVE ERROR at %d-%d.%s", location >> 4, location & 0xF, Commons.History.EXTENSION));
//            }
//        }).execute());

        HistoryBtn.setOnClickListener(v -> {
            startActivity(new Intent(MainActivity.this, HistoryActivity.class));
        });

        SettingsBtn.setOnClickListener(v -> startActivity(new Intent(this, PreferenceActivity.class)));

//        int id = getIntent().getIntExtra(Commons.ARGS.NOTIF_CANCEL, -1);
//        if (id > 0) {
//            Intent intent = new Intent(TAG);
//            intent.putExtra(Commons.ARGS.NOTIF_CANCEL, id);
//            LocalBroadcastManager.getInstance(this).sendBroadcast(intent);
//        }

        getWindow().setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_STATE_HIDDEN);
        Search.findFocus();

        if (queries != null && query != null) {
            Title.setText(query);
            Refresh.setOnClickListener(view -> Query());
            onComplete(queries);
        }

        UpdateInfo();
    }

    private void Query() {
        Query(query);
    }

    private void Query(String query) {
        MainActivity.query = query;
        queries = null;
        queryAdapter.resetList(Collections.nCopies(Commons.Pref.query_amount, null));

        new YoutubeQueryTask(this, getPackageName())
                .setTimeout(Commons.Pref.timeout)
                .executeOnExecutor(AsyncTask.THREAD_POOL_EXECUTOR, query);

        Title.setText(query);
        Refresh.setOnClickListener(view -> Query());
        if (QueryList.getLayoutManager() != null) QueryList.getLayoutManager().scrollToPosition(0);
    }

    private void UpdateInfo() {
        if (service != null) {
            int running = service.getRunning().size();
            if (running > 0) {
                InfoTitle.setText(String.format(Locale.ENGLISH, "%d download%s running", running, running == 1 ? " is" : "s are"));
                return;
            }

            int completed = service.getCompleted().size();
            if (completed > 0) {
                InfoTitle.setText(String.format(Locale.ENGLISH, "%d download%s completed", completed, completed == 1 ? " has" : "s have"));
                return;
            }

            int queued = service.getQueue().size();
            if (queued > 0) {
                InfoTitle.setText(String.format(Locale.ENGLISH, "%d download%s queued", queued, queued == 1 ? " is" : "s are"));
                return;
            }
        }

        InfoTitle.setText("No downloads are running currently");
    }

    public void onComplete(java.util.List<SearchResult> results) {
        queries = results;
        int visi = View.GONE;
        if (results == null) {
            OfflineImg.setImageResource(R.drawable.ic_cloud_off_secondarydark_96dp);
            OfflineMsg.setText("It seems that we can't connect to YouTube. Please check your connection and try again later.");
            OfflineAction.setText("Retry");
            OfflineAction.setOnClickListener(v -> Query());
            queryAdapter.resetList();
        } else if (results.size() <= 0) {
            OfflineImg.setImageResource(R.drawable.ic_search_lightgray_96dp);
            OfflineMsg.setText("Your search didn't come out with any results");
            OfflineAction.setText("Back");
            OfflineAction.setOnClickListener(v -> onBackPressed());
            queryAdapter.resetList();
        } else queryAdapter.resetList(Query.CastListXML(results));

        OfflineImg.setVisibility(visi);
        OfflineMsg.setVisibility(visi);
        OfflineAction.setVisibility(visi);
    }

    public void onTimeout() {
        queryAdapter.resetList();
        OfflineImg.setImageResource(R.drawable.ic_timer_lightgray_96dp);
        OfflineImg.setVisibility(View.VISIBLE);
        OfflineMsg.setText("Request timed out");
        OfflineMsg.setVisibility(View.VISIBLE);
        OfflineAction.setVisibility(View.VISIBLE);
        OfflineAction.setText("Retry");
        OfflineAction.setOnClickListener(v -> Query(query));
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

            getApplication().onTerminate();
        }
//        unbindService(this);
        super.onDestroy();
    }

    @Override
    protected void onPause() {
        unbindService(this);
        super.onPause();
    }

    @Override
    protected void onResume() {
        super.onResume();
        bindService(new Intent(this, DownloadService.class), this, 0);
    }

    @Override
    public void onServiceConnected(ComponentName componentName, IBinder iBinder) {
        service = ((DownloadService.LocalBinder) iBinder).getService();
        service.addCallbacks(this);
        adapter = new DownloadAdapter(this, service, List, getSupportFragmentManager());
        List.setLayoutManager(manager);
        List.setAdapter(adapter);
        ScrollImg.setVisibility(View.VISIBLE);
//        int index = getIntent().getIntExtra(Commons.ARGS.INDEX, -1);
//        if (index > 0 && manager.findFirstCompletelyVisibleItemPosition() <= index && manager.findLastCompletelyVisibleItemPosition() >= index) {
//            RecyclerView.SmoothScroller smoothScroller = new LinearSmoothScroller(this) {
//                @Override
//                protected int getVerticalSnapPreference() {
//                    return LinearSmoothScroller.SNAP_TO_START;
//                }
//            };
//            smoothScroller.setTargetPosition(index);
//            manager.startSmoothScroll(smoothScroller);
//        }


//        if (getIntent() != null) {
//            int scrollIndex = getIntent().getIntExtra(Commons.ARGS.INDEX, -1);
//            if (scrollIndex >= 0 && scrollIndex < adapter.getItemCount()) {
//                float y = List.getY() + List.getChildAt(scrollIndex).getY();
//                ((NestedScrollView) findViewById(R.id.main)).smoothScrollTo(0, (int) y);
////                manager.scrollToPositionWithOffset(scrollIndex, 20);
////                List.scrollToPosition(scrollIndex);
//            }
//        }
    }

    @Override
    public void onServiceDisconnected(ComponentName componentName) {
        service.removeCallbacks(this);
        service = null;
        if (adapter.getItemCount() > 0) ScrollImg.setVisibility(View.VISIBLE);
        Log.w(TAG, "Service has been disconnected");
    }

    @Override
    public void onProgress(Download args, long progress, long max, long size, boolean indeterminate) {
        adapter.onProgress(args, progress, max, size, indeterminate);
        UpdateInfo();
    }

    @Override
    public void onDone(Download args, boolean successful, Exception e) {
        adapter.onDone(args, successful, e);
        UpdateInfo();
    }

    @Override
    public void onWarn(Download args, String type) {
        adapter.onWarn(args, type);
        Log.w(TAG, (args.title == null ? "SERVICE " : '\'' + args.title + '\'') + ": " + type);
        Snackbar.make(findViewById(R.id.main), type, Snackbar.LENGTH_LONG).show();
    }

    @Override
    public void onClick(View view) {
        if(Search.getText() == null || Search.getText().toString().trim().isEmpty()) return;

        InputMethodManager imm = (InputMethodManager) getSystemService(Activity.INPUT_METHOD_SERVICE);
        View v = getCurrentFocus();
        if (v == null) {
            v = new View(this);
        }
        imm.hideSoftInputFromWindow(v.getWindowToken(), 0);

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
            SlideUp.setPanelState(SlidingUpPanelLayout.PanelState.ANCHORED);
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

//    @Override
//    protected void onPause() {
//        super.onPause();
//        Shimmer.stopShimmer();
//    }
//
//    @Override
//    protected void onResume() {
//        super.onResume();
//        if (querying) Shimmer.startShimmer();
//    }
}
