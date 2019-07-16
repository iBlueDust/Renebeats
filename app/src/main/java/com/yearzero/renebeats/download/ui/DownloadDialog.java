package com.yearzero.renebeats.download.ui;

import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.ImageButton;
import android.widget.TableLayout;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AlertDialog;
import androidx.fragment.app.DialogFragment;

import com.yearzero.renebeats.Commons;
import com.yearzero.renebeats.Directories;
import com.yearzero.renebeats.R;
import com.yearzero.renebeats.download.Download;
import com.yearzero.renebeats.download.DownloadService;
import com.yearzero.renebeats.download.Status;
import com.yearzero.renebeats.preferences.Preferences;

import java.io.File;
import java.text.SimpleDateFormat;
import java.util.Locale;

import de.hdodenhof.circleimageview.CircleImageView;

public class DownloadDialog extends DialogFragment {
    private static final String TAG = "DownloadDialog";

    private TextView Title, Artist, Album, Genres, Year, Track;
    private TextView Format, Bitrate, Normalize, Start, End, Overwrite;
    private TextView DLText, Conversion, Metadata;
    private TextView Assigned, Completed;
    private TextView YouTubeID, ID, URL, AvailFormat, Exception, Progress;
    private TextView PathDownload, PathConversion, PathMetadata;
    private CircleImageView StatusDownload, StatusConversion, StatusMetadata;

    private TableLayout SecretMain, SecretSecond;
    private TextView SecretLabel, SecretPaths;
    private View SecretView;

    private Button Close;
    private ImageButton Dismiss;

    private boolean secret;
    private Download download;

    @Override
    @NonNull
    public View onCreateView(@NonNull LayoutInflater inflater, ViewGroup parent, Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        View dialog = inflater.inflate(R.layout.dialog_download, parent, false);

        Title = dialog.findViewById(R.id.title);
        Artist = dialog.findViewById(R.id.author);
        Album = dialog.findViewById(R.id.album);
        Genres = dialog.findViewById(R.id.genres);
        Year = dialog.findViewById(R.id.year);
        Track = dialog.findViewById(R.id.track);
        Format = dialog.findViewById(R.id.format);
        Bitrate = dialog.findViewById(R.id.bitrate);
        Normalize = dialog.findViewById(R.id.normalize);
        Start = dialog.findViewById(R.id.start);
        End = dialog.findViewById(R.id.end);
        Overwrite = dialog.findViewById(R.id.overwrite);
        DLText = dialog.findViewById(R.id.download);
        Conversion = dialog.findViewById(R.id.conversion);
        Metadata = dialog.findViewById(R.id.metadata);
        Assigned = dialog.findViewById(R.id.assigned);
        Completed = dialog.findViewById(R.id.completed);

        YouTubeID = dialog.findViewById(R.id.ytid);
        ID = dialog.findViewById(R.id.downloadid);
        URL = dialog.findViewById(R.id.url);
        AvailFormat = dialog.findViewById(R.id.availformat);
        Exception = dialog.findViewById(R.id.exception);
        Progress = dialog.findViewById(R.id.progress);
        PathDownload = dialog.findViewById(R.id.path_download);
        PathConversion = dialog.findViewById(R.id.path_conversion);
        PathMetadata = dialog.findViewById(R.id.path_metadata);
        StatusDownload = dialog.findViewById(R.id.status_download);
        StatusConversion = dialog.findViewById(R.id.status_conversion);
        StatusMetadata = dialog.findViewById(R.id.status_metadata);

        SecretLabel = dialog.findViewById(R.id.secret_lbl);
        SecretView = dialog.findViewById(R.id.secret_view);
        SecretMain = dialog.findViewById(R.id.secret_main);
        SecretPaths = dialog.findViewById(R.id.secret_paths);
        SecretSecond = dialog.findViewById(R.id.secret_second);

        Close = dialog.findViewById(R.id.save);
        Dismiss = dialog.findViewById(R.id.dismiss);
        Close.setOnClickListener(v -> dismiss());
        Dismiss.setOnClickListener(v -> dismiss());

        int visibility = secret ? View.VISIBLE : View.GONE;

        SecretLabel.setVisibility(visibility);
        SecretView.setVisibility(visibility);
        SecretMain.setVisibility(visibility);
        SecretPaths.setVisibility(visibility);
        SecretSecond.setVisibility(visibility);

        Update();
        return dialog;
    }

    public DownloadDialog setSecret(boolean secret) {
        this.secret = secret;
        return this;
    }

    public DownloadDialog setDownload(@NonNull Download download) {
        this.download = download;
        return this;
    }

    public Download getDownload() {
        return download;
    }

    private void Update() {
        if (isDownloadNull()) return;

        Title.setText(download.getTitle() == null ? "-" : download.getTitle());
        Artist.setText(download.getArtist() == null ? "-" : download.getArtist());
        Album.setText(download.getAlbum() == null ? "-" : download.getAlbum());
        Genres.setText(download.getGenres() == null ? "-" : download.getGenres());
        Year.setText(String.valueOf(download.getYear()));
        Track.setText(String.valueOf(download.getTrack()));
        download.getFormat();
        Format.setText(download.getFormat().toUpperCase());
        Bitrate.setText(String.format("%s kbps", download.getBitrate()));
        Normalize.setText(download.isNormalize() ? "ON" : "OFF");
        Start.setText(download.getStart() == null ? "-" : IntToHMS(download.getStart()));
        End.setText(download.getEnd() == null ? "-" : IntToHMS(download.getEnd()));
        Overwrite.setText(download.getOverwrite() ? "True" : "False");

        DLText.setText("-");
        Conversion.setText("-");
        Metadata.setText("-");

        Assigned.setText(download == null || download.getAssigned() == null || getContext() == null ? "-" : Preferences.formatDateLong(getContext(), download.getAssigned()));
        Completed.setText(download == null || download.getCompleteDate() == null ? "-" : new SimpleDateFormat("EEE, d MMM yyyy, HH:mm:ss", Locale.ENGLISH).format(download.getCompleteDate()));

        YouTubeID.setText(download == null ? "-" : download.getYoutubeID());
        ID.setText(download == null ? "-" : Long.toHexString(download.getId()));
        //        URL.setText(download == null || download.url == null ? "-" : download.url);
        AvailFormat.setText(download == null || download.getAvailableFormat() == null ? "-" : download.getAvailableFormat().toUpperCase());
        Exception.setText(download == null || download.getException() == null ? "-" : download.getException().getMessage());

        if (download.getUrl() == null || download.getUrl().isEmpty())
            URL.setText('-');
        else {
            URL.setText("Tap to Reveal");
            URL.setOnClickListener(view -> {
                    if (getContext() != null)
                        new AlertDialog.Builder(getContext())
                            .setTitle("URL")
                            .setMessage(download.getUrl())
                            .setPositiveButton("Go", (dialog, which) -> {
                                Intent intent = new Intent(Intent.ACTION_VIEW);
                                intent.setData(Uri.parse(download.getUrl()));
                                startActivity(intent);
                            })
                            .show();
            });
        }
        UpdatePartial();
    }

    public void UpdatePartial(@NonNull Download download) {
        this.download = download;
        UpdatePartial();
    }

    void UpdatePartial() {
        if (isDownloadNull()) return;

        if (download.getException() instanceof IllegalArgumentException)
            Exception.setText("IllegalArgumentException");
        else if (download.getException() instanceof DownloadService.ServiceException) {
//                if ((((DownloadService.ServiceException) download.exception).getDownload()) == null)
//                    DLText.setText("");
//                else UpdateStatus(download.status);
            Exception payload = ((DownloadService.ServiceException) download.getException()).getPayload();
            Exception.setText(payload == null ? download.getException().getMessage() : payload.getMessage());
        } else if (download.getException() != null)
            Exception.setText(download.getException().getMessage());

        if (download.getSize() <= 0)
            Progress.setText(String.format(Commons.getLocale(), "%d/%d", download.getCurrent(), download.getTotal()));
        else Progress.setText(String.format(Commons.getLocale(), "%d/%d (%s)", download.getCurrent(), download.getTotal(), Commons.FormatBytes(download.getSize())));

        UpdateStatus(download.getStatus());

        if (download.getDown() == null) PathDownload.setText("-");
        else {
            PathDownload.setText(download.getDown());
            if (Directories.isCacheExists(download.getDown()))
                StatusDownload.setImageResource(download.getStatus().getMetadata() != null && download.getStatus().getMetadata() ? R.color.red : R.color.yellow);
            else if (download.getStatus().getDownload() == Status.Download.COMPLETE)
                StatusDownload.setImageResource(R.color.green);
            else StatusDownload.setImageResource(R.color.SecondaryDark);
        }

        if (download.getConv() == null) PathConversion.setText("-");
        else {
            PathConversion.setText(download.getConv());
            if (Directories.isCacheExists(download.getConv()))
                StatusConversion.setImageResource(download.getStatus().getMetadata() != null && download.getStatus().getMetadata() ? R.color.red : R.color.yellow);
            else if (download.getStatus().getConvert() == Status.Convert.COMPLETE || download.getStatus().getConvert() == Status.Convert.SKIPPED)
                StatusConversion.setImageResource(R.color.green);
            else StatusConversion.setImageResource(R.color.SecondaryDark);
        }

        if (download.getMtdt() == null) PathMetadata.setText("-");
        else {
            PathMetadata.setText(download.getMtdt());
            if (download.getStatus().getMetadata() != null && download.getStatus().getMetadata())
                StatusMetadata.setImageResource(new File(Directories.getMUSIC(), download.getMtdt()).exists() ? R.color.green : R.color.red);
            else StatusMetadata.setImageResource(R.color.SecondaryDark);
        }
    }

    private void UpdateStatus(@Nullable Status status) {
        if (status == null || status.getDownload() == null)
            DLText.setText("-");
        else {
            switch (status.getDownload()) {
                case QUEUED:
                    DLText.setText("QUEUED");
                    break;
                case RUNNING:
                    DLText.setText("IN PROGRESS");
                    break;
                case PAUSED:
                    DLText.setText("PAUSED");
                    break;
                case COMPLETE:
                    DLText.setText("Completed");
                    break;
                case FAILED:
                    DLText.setText("FAILED");
                    break;
                default:
                    DLText.setText("-");
            }
        }

        if (status == null || status.getConvert() == null)
            Conversion.setText("-");
        else {
            switch (status.getConvert()) {
                case SKIPPED:
                    Conversion.setText("SKIPPED");
                    break;
//                case PAUSED:
//                    Conversion.setText("PAUSED");
//                    break;
                case QUEUED:
                    Conversion.setText("QUEUED");
                    break;
                case RUNNING:
                    Conversion.setText("IN PROGRESS");
                    break;
                case CANCELLED:
                    Conversion.setText("CANCELLED");
                    break;
                case COMPLETE:
                    Conversion.setText("Completed");
                    break;
                case FAILED:
                    Conversion.setText("FAILED");
                    break;
                default:
                    Conversion.setText("-");
            }
        }

        if (status == null || status.getMetadata() == null) Metadata.setText("-");
        else if (status.getMetadata()) Metadata.setText("Completed");
        else Metadata.setText("FAILED");
    }

    private boolean isDownloadNull() {
        if (download == null) {
            Log.w(TAG, "download == null");
            return true;
        } else return false;
    }

//    private String ArrayToString(String[] strings) {
//        if (strings.length <= 0) return "";
//        StringBuilder builder = new StringBuilder(strings[0]);
//        for (String string : strings) builder.append(',').append(string);
//        return builder.toString();
//    }

    private String IntToHMS(int i) {
        StringBuilder str = new StringBuilder();

        short h = (short) Math.floor(i / 3600f);
        short m = (short) (Math.floor(i / 60f) % 60);
        short s = (short) (i % 60);

        if (h > 0) {
            str.append(h).append(':');
            if (m < 10) str.append('0');
        }

        str.append(m).append(':');
        if (s < 10) str.append('0');
        str.append(s);
        return str.toString();
    }
}
