package com.yearzero.renebeats;

import android.app.Dialog;
import android.content.Context;
import android.view.View;
import android.view.Window;
import android.widget.Button;
import android.widget.TableLayout;
import android.widget.TextView;

import java.io.File;
import java.text.SimpleDateFormat;
import java.util.Locale;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AlertDialog;
import de.hdodenhof.circleimageview.CircleImageView;

public class DownloadDialog extends Dialog {

    // TODO: Migrate to ConstraintLayout

    private TextView Title, Artist, Album, Genres, Year, Track;
    private TextView Format, Bitrate, Normalize, Start, End, Overwrite;
    private TextView DLText, Conversion, Metadata;
    private TextView Assigned, Completed;
    private TextView YouTubeID, PRDownloaderID, URL, AvailFormat, Exception;
    private TextView PathDownload, PathConversion, PathMetadata;
    private CircleImageView StatusDownload, StatusConversion, StatusMetadata;

    private TableLayout SecretMain, SecretSecond;
    private TextView SecretLabel, SecretPaths;
    private View SecretView;

    private Button Close;

    private boolean secret;

    public DownloadDialog(@NonNull Context context) {
        super(context);

        requestWindowFeature(Window.FEATURE_NO_TITLE);
        setContentView(R.layout.dialog_download);

        Title = findViewById(R.id.title);
        Artist = findViewById(R.id.author);
        Album = findViewById(R.id.album);
        Genres = findViewById(R.id.genres);
        Year = findViewById(R.id.year);
        Track = findViewById(R.id.track);
        Format = findViewById(R.id.format);
        Bitrate = findViewById(R.id.bitrate);
        Normalize = findViewById(R.id.normalize);
        Start = findViewById(R.id.start);
        End = findViewById(R.id.end);
        Overwrite = findViewById(R.id.overwrite);
        DLText = findViewById(R.id.download);
        Conversion = findViewById(R.id.conversion);
        Metadata = findViewById(R.id.metadata);
        Assigned = findViewById(R.id.assigned);
        Completed = findViewById(R.id.completed);

        YouTubeID = findViewById(R.id.ytid);
        PRDownloaderID = findViewById(R.id.downloadid);
        URL = findViewById(R.id.url);
        AvailFormat = findViewById(R.id.availformat);
        Exception = findViewById(R.id.exception);
        PathDownload = findViewById(R.id.path_download);
        PathConversion = findViewById(R.id.path_conversion);
        PathMetadata = findViewById(R.id.path_metadata);
        StatusDownload = findViewById(R.id.status_download);
        StatusConversion = findViewById(R.id.status_conversion);
        StatusMetadata = findViewById(R.id.status_metadata);

        SecretLabel = findViewById(R.id.secret_lbl);
        SecretView = findViewById(R.id.secret_view);
        SecretMain = findViewById(R.id.secret_main);
        SecretPaths = findViewById(R.id.secret_paths);
        SecretSecond = findViewById(R.id.secret_second);

        Close = findViewById(R.id.close);
        Close.setOnClickListener(v -> dismiss());

    }

    public DownloadDialog setSecret(boolean secret) {
        int visi = secret ? View.VISIBLE : View.GONE;

        SecretLabel.setVisibility(visi);
        SecretView.setVisibility(visi);
        SecretMain.setVisibility(visi);
        SecretPaths.setVisibility(visi);
        SecretSecond.setVisibility(visi);

        return this;
    }

    public DownloadDialog setSecret(boolean secret, @Nullable Download download) {
        setSecret(secret);

        boolean redo = secret && !this.secret;

        this.secret = secret;

        if (redo) setDownload(download);
        return this;
    }

    public DownloadDialog setDownload(Download download) {
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

        YouTubeID.setText(download == null ? "-" : String.valueOf(download.id));
        PRDownloaderID.setText(download == null ? "-" : String.valueOf(download.id));
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

        UpdatePartial(download);
        return this;
    }

    public void UpdatePartial(Download download) {
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

    public void UpdateStatus(@Nullable Status status) {
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
        for (int i = 0; i < strings.length; i++) builder.append(',').append(strings[i]);
        return builder.toString();
    }

    private String IntegerToMinSec(int i) {
        StringBuilder str = new StringBuilder();

        short h = (short) Math.floor(i / 3600);
        short m = (short) (Math.floor(i / 60) % 60);
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
