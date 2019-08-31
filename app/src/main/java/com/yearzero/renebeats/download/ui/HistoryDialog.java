package com.yearzero.renebeats.download.ui;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.NonNull;

import com.yearzero.renebeats.R;
import com.yearzero.renebeats.download.Download;

public class HistoryDialog extends DownloadDialog {
    protected static final String TAG = "DownloadDialog";

    @Override
    @NonNull
    public View onCreateView(@NonNull LayoutInflater inflater, ViewGroup parent, Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        View view = super.onCreateView(inflater, parent, savedInstanceState);
        view.findViewById(R.id.progress_row).setVisibility(View.GONE);
        return view;
    }

    @Override
    public DownloadDialog setDownload(@NonNull Download download) {
        return super.setDownload(download);
    }

//    public DownloadDialog setDownload(@NonNull HistoryLog log) {
//        return super.setDownload(log.uncast());
//    }

//    public void UpdatePartial(@NonNull HistoryLog log) {
//        setDownload(log);
//        super.UpdatePartial();
//    }

    @Override
    public void UpdatePartial(@NonNull Download download) {
        super.setDownload(download);
        super.UpdatePartial();
    }
}
