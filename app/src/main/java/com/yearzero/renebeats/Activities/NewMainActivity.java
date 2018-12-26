package com.yearzero.renebeats.Activities;

import android.content.ComponentName;
import android.content.Intent;
import android.content.ServiceConnection;
import android.content.pm.PackageManager;
import android.os.Bundle;
import android.os.IBinder;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.TextView;

import com.google.android.material.snackbar.Snackbar;
import com.google.android.material.textfield.TextInputEditText;
import com.yearzero.renebeats.Adapters.DownloadAdapter;
import com.yearzero.renebeats.Commons;
import com.yearzero.renebeats.Download;
import com.yearzero.renebeats.DownloadService;
import com.yearzero.renebeats.R;

import java.net.MalformedURLException;
import java.net.URL;
import java.util.regex.Pattern;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.RecyclerView;
import pub.devrel.easypermissions.EasyPermissions;

public class NewMainActivity extends AppCompatActivity implements ServiceConnection, DownloadService.ClientCallbacks, View.OnClickListener {
    private static final String TAG = "NewMainActivity";

    private TextInputEditText Search;
    private ImageButton QueryBtn;

    private View ErrorCard;
    private ImageView ErrorImg;
    private TextView ErrorTitle, ErrorMsg;
    private Button ErrorAction;

    private TextView InfoTitle;
    private Button InfoAction;

    private RecyclerView List;
    private DownloadAdapter adapter;
    private DownloadService service;

    @Override
    protected void onStart() {
        super.onStart();

        bindService(new Intent(this, DownloadService.class), this, 0);
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main_new);

        Search = findViewById(R.id.search);
        QueryBtn = findViewById(R.id.query);

        ErrorCard = findViewById(R.id.error_card);
        ErrorImg = findViewById(R.id.error_img);
        ErrorTitle = findViewById(R.id.error_title);
        ErrorMsg = findViewById(R.id.error_msg);
        ErrorAction = findViewById(R.id.error_action);

        InfoTitle = findViewById(R.id.info_title);
        InfoAction = findViewById(R.id.info_action);

        List = findViewById(R.id.list);

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

        QueryBtn.setOnClickListener(this);

    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        EasyPermissions.onRequestPermissionsResult(requestCode, permissions, grantResults);
    }

    @Override
    protected void onStop() {
        if (service != null) {
            if (service.removeCallbacks(this)) Log.i(TAG, "Service callback has been removed");
            else Log.e(TAG, "Failed to remove service callback");
        }
        super.onStop();
    }

    @Override
    public void onServiceConnected(ComponentName componentName, IBinder iBinder) {
        service = ((DownloadService.LocalBinder) iBinder).getService();
        service.addCallbacks(this);
        adapter = new DownloadAdapter(this, service, List);
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

        Intent intent = new Intent(this, DownloadActivity.class);

        String url = "";
        String query = Search.getText().toString();

        if(!(query.startsWith("http://") || query.startsWith("https://") || query.contains(" ")) && query.contains(".")) url = "http://";
        if(Pattern.compile("^(https?://)?[\\w\\d]+\\.[\\w\\d]+", Pattern.CASE_INSENSITIVE).matcher(url).matches()) url += "www.";
        url += query;

        try {
            new URL(url);
            intent.putExtra(Commons.ARGS.URL, Search.getText().toString().isEmpty());
        } catch (MalformedURLException ignored) {
            intent = new Intent(this, QueryActivity.class);
            intent.putExtra(Commons.ARGS.DATA, query);
        } finally {
            startActivity(intent);
        }
    }
}
