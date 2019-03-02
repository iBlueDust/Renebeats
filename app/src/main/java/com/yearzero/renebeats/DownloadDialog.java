package com.yearzero.renebeats;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.ImageButton;
import android.widget.TableLayout;
import android.widget.TextView;

import java.io.File;
import java.text.SimpleDateFormat;
import java.util.Locale;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AlertDialog;
import androidx.fragment.app.DialogFragment;
import de.hdodenhof.circleimageview.CircleImageView;

public class DownloadDialog extends DialogFragment {

    private TextView Title, Artist, Album, Genres, Year, Track;
    private TextView Format, Bitrate, Normalize, Start, End, Overwrite;
    private TextView DLText, Conversion, Metadata;
    private TextView Assigned, Completed;
    private TextView YouTubeID, ID, URL, AvailFormat, Exception;
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

        Close = dialog.findViewById(R.id.close);
        Dismiss = dialog.findViewById(R.id.dismiss);
        Close.setOnClickListener(v -> dismiss());
        Dismiss.setOnClickListener(v -> dismiss());

        int visi = secret ? View.VISIBLE : View.GONE;

        SecretLabel.setVisibility(visi);
        SecretView.setVisibility(visi);
        SecretMain.setVisibility(visi);
        SecretPaths.setVisibility(visi);
        SecretSecond.setVisibility(visi);

        Update();

        return dialog;
    }

    public DownloadDialog setSecret(boolean secret) {
        this.secret = secret;
        return this;
    }

    public DownloadDialog setDownload(Download download) {
        this.download = download;
        return this;
    }

    private void Update() {
        Title.setText(download == null || download.title == null ? "-" : download.title);
        Artist.setText(download == null || download.artist == null ? "-" : download.artist);
        Album.setText(download == null || download.album == null ? "-" : download.album);
        Genres.setText(download == null || download.genres == null ? "-" : ArrayToString(download.genres));
        Year.setText(download == null ? "-" : String.valueOf(download.year));
        Track.setText(download == null ? "-" : String.valueOf(download.track));
        Format.setText(download == null || download.format == null ? "-" : download.format.toUpperCase());
        Bitrate.setText(download == null ? "-" : String.valueOf(download.bitrate) + " kbps");
        Normalize.setText(download == null ? "-" : (download.normalize ? "ON" : "OFF"));
        Start.setText(download == null || download.start == null ? "-" : IntegerToMinSec(download.start));
        End.setText(download == null || download.end == null ? "-" : IntegerToMinSec(download.end));
        Overwrite.setText(download == null ? "-" : download.overwrite ? "True" : "False");

        DLText.setText("-");
        Conversion.setText("-");
        Metadata.setText("-");

        Assigned.setText(download == null || download.assigned == null ? "-" : new SimpleDateFormat("EEE, d MMM yyyy, HH:mm:ss", Locale.ENGLISH).format(download.assigned));
        Completed.setText(download == null || download.completed == null ? "-" : new SimpleDateFormat("EEE, d MMM yyyy, HH:mm:ss", Locale.ENGLISH).format(download.completed));

        YouTubeID.setText(download == null ? "-" : download.youtubeID);
        ID.setText(download == null ? "-" : Long.toHexString(download.id));
        //        URL.setText(download == null || download.url == null ? "-" : download.url);
        AvailFormat.setText(download == null || download.availformat == null ? "-" : download.availformat.toUpperCase());
        Exception.setText(download == null || download.exception == null ? "-" : download.exception.getMessage());

        if (download == null || download.url == null || download.url.isEmpty())
            URL.setText('-');
        else {
            URL.setText("Tap to Reveal");
            URL.setOnClickListener(view -> new AlertDialog.Builder(getContext())
                    .setTitle("URL")
                    .setMessage(download.url)
                    .show());
        }
        UpdatePartial();
    }

    public void UpdatePartial(Download download) {
        this.download = download;
        UpdatePartial();
    }

    private void UpdatePartial() {
        if (download != null) {
            if (download.exception instanceof IllegalArgumentException)
                Exception.setText("IllegalArgumentException");
            else if (download.exception instanceof DownloadService.ServiceException) {
                if ((((DownloadService.ServiceException) download.exception).getDownload()) == null)
                    DLText.setText("");
                else UpdateStatus(download.status);
            } else if (download.exception != null)
                Exception.setText(download.exception.getMessage());
            else UpdateStatus(download.status);
        }

        if (download == null || download.down == null) PathDownload.setText("-");
        else {
            PathDownload.setText(download.down);
            if (new File(Commons.Directories.BIN, download.down).exists())
                StatusDownload.setImageResource(download.status.metadata != null && download.status.metadata ? R.color.red : R.color.yellow);
            else if (download.status.download == Status.Download.COMPLETE)
                StatusDownload.setImageResource(R.color.green);
            else StatusDownload.setImageResource(R.color.SecondaryDark);
        }

        if (download == null || download.conv == null) PathConversion.setText("-");
        else {
            PathConversion.setText(download.conv);
            if (new File(Commons.Directories.BIN, download.conv).exists())
                StatusConversion.setImageResource(download.status.metadata != null && download.status.metadata ? R.color.red : R.color.yellow);
            else if (download.status.convert == Status.Convert.COMPLETE || download.status.convert == Status.Convert.SKIPPED)
                StatusConversion.setImageResource(R.color.green);
            else StatusConversion.setImageResource(R.color.SecondaryDark);
        }

        if (download == null || download.mtdt == null) PathMetadata.setText("-");
        else {
            PathMetadata.setText(download.mtdt);
            if (download.status.metadata != null && download.status.metadata)
                StatusMetadata.setImageResource(new File(Commons.Directories.MUSIC, download.mtdt).exists() ? R.color.green : R.color.red);
            else StatusMetadata.setImageResource(R.color.SecondaryDark);
        }
    }

    private void UpdateStatus(@Nullable Status status) {
        if (status == null || status.download == null)
            DLText.setText("-");
        else {
            switch (status.download) {
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

        if (status == null || status.convert == null)
            Conversion.setText("-");
        else {
            switch (status.convert) {
                case SKIPPED:
                    Conversion.setText("SKIPPED");
                    break;
                case PAUSED:
                    Conversion.setText("PAUSED");
                    break;
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

        if (status == null || status.metadata == null) Metadata.setText("-");
        else if (status.metadata) Metadata.setText("Completed");
        else Metadata.setText("FAILED");
    }

    private String ArrayToString(String[] strings) {
        if (strings.length <= 0) return "";
        StringBuilder builder = new StringBuilder(strings[0]);
        for (String string : strings) builder.append(',').append(string);
        return builder.toString();
    }

    private String IntegerToMinSec(int i) {
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
