package com.yearzero.renebeats.Fragments.Main;


import android.content.ComponentName;
import android.content.Intent;
import android.content.ServiceConnection;
import android.os.Bundle;
import android.os.IBinder;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.TextView;

import com.yearzero.renebeats.Adapters.DownloadAdapter;
import com.yearzero.renebeats.Download;
import com.yearzero.renebeats.DownloadService;
import com.yearzero.renebeats.R;

import androidx.annotation.NonNull;
import androidx.cardview.widget.CardView;
import androidx.fragment.app.Fragment;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

public class DownloadFragment extends Fragment implements ServiceConnection, DownloadService.ClientCallbacks {
    private static final String TAG = "DownloadFragment";

    private DownloadService Service;

    private RecyclerView List;
    private CardView Error, Downloads;
    private ImageView ErrorIcon;
    private TextView ErrorTitle, ErrorMsg, DownloadsHeader;
    private Button ErrorBtn, DownloadsBtn;

    private DownloadAdapter adapter;

    public DownloadFragment() { }

    @Override
    public void onStart() {
        super.onStart();

        getActivity().bindService(new Intent(getActivity(), DownloadService.class), this, 0);

    }

    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.fragment_download, container, false);

        List = view.findViewById(R.id.list);
        Error = view.findViewById(R.id.error);
        Downloads = view.findViewById(R.id.downloads);
        ErrorIcon = view.findViewById(R.id.error_icon);
        ErrorTitle = view.findViewById(R.id.error_title);
        ErrorMsg = view.findViewById(R.id.error_msg);
        ErrorBtn = view.findViewById(R.id.error_button);
        DownloadsHeader = view.findViewById(R.id.downloads_header);
        DownloadsBtn = view.findViewById(R.id.download_action);

        List.setLayoutManager(new LinearLayoutManager(getActivity()));

        return view;
//        return LayoutInflater.from(new ContextThemeWrapper(getActivity(), R.style.AppTheme_Light_NoActionBar)).inflate(R.layout.fragment_download, container, false);
    }

    @Override
    public void onStop() {
        super.onStop();
        if (Service != null) Service.removeCallbacks(this);
        getActivity().unbindService(this);
    }

    @Override
    public void onServiceConnected(ComponentName name, IBinder service) {
        Service = ((DownloadService.LocalBinder) service).getService();
        Service.addCallbacks(this);
        adapter = new DownloadAdapter(getActivity(), Service, List);
        List.setAdapter(adapter);
    }

    @Override
    public void onServiceDisconnected(ComponentName name) {
        Service = null;
    }

    @Override
    public void onProgress(Download args, long progress, long max, boolean indeterminate) {
        Download[] array = adapter.getServiceDownloads();

        int index = -1;

        for (int i = 0; i < array.length; i++) {
            if (array[i].hashCode() == args.hashCode()) {
                index = i;
                break;
            }
        }

        if (index < 0)
            adapter.notifyDataSetChanged();
//        else if (adapter.getViewHolderType(array[index]) != adapter.getViewHolderType(args))
//            adapter.notifyItemChanged(index);
        else adapter.UpdateAtPosition(index, args);

    }

    @Override
    public void onDone(Download args, boolean successful, Exception e) {
//        Download[] array = adapter.getServiceDownloads();
//
//        int index = -1;
//
//        for (int i = 0; i < array.length; i++) {
//            if (array[i].hashCode() == args.hashCode()) {
//                index = i;
//                break;
//            }
//        }
//
//        if (index < 0)
            adapter.notifyDataSetChanged();
//        else adapter.notifyItemChanged(index);
    }

    @Override
    public void onWarn(Download args, String type) {
        Log.w(TAG, (args.title == null ? "SERVICE" : '\'' + args.title + '\'') + ": " + type);
    }

    public void setService(DownloadService service) {
        Service = service;

        if (Service == null)
            Log.e(TAG, "Service is null at onCreateView");
        else {
            adapter = new DownloadAdapter(getActivity(), Service, List);
            List.setLayoutManager(new LinearLayoutManager(getActivity()));
            List.setAdapter(adapter);
        }
    }
}
