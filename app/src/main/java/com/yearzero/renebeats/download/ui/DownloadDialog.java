package com.yearzero.renebeats.download.ui;

import android.content.Context;
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
		String empty = getString(R.string.sym_empty);

		if (download == null) {
			Assigned.setText(empty);
			Completed.setText(empty);
			YouTubeID.setText(empty);
			ID.setText(empty);
			AvailFormat.setText(empty);
			Exception.setText(empty);

			Log.w(TAG, "download is null");
			return;
		}

		Title.setText(download.getTitle());
		Artist.setText(download.getArtist());
		Album.setText(download.getAlbum());
		Genres.setText(download.getGenres());
		Year.setText(String.valueOf(download.getYear()));
		Track.setText(String.valueOf(download.getTrack()));
		Format.setText(download.getFormat().toUpperCase());
		Bitrate.setText(String.format(Locale.ENGLISH, getString(R.string.kbps), download.getBitrate()));
		Normalize.setText(download.getNormalize() ? R.string.on : R.string.off);
		Start.setText(download.getStart() == null ? empty : IntToHMS(download.getStart()));
		End.setText(download.getEnd() == null ? empty : IntToHMS(download.getEnd()));
		Overwrite.setText(download.getOverwrite() ? R.string.tru : R.string.fals);

		DLText.setText(empty);
		Conversion.setText(empty);
		Metadata.setText(empty);

		Assigned.setText(download.getAssigned() == null || getContext() == null ? empty : Preferences.formatDateLong(getContext(), download.getAssigned()));
		Completed.setText(download.getCompleteDate() == null || getContext() == null ? empty : Preferences.formatDateLong(getContext(), download.getCompleteDate()));

		YouTubeID.setText(download.getYoutubeID());
		ID.setText(Long.toHexString(download.getDownloadId()));
		AvailFormat.setText(download.getAvailableFormat() == null ? empty : download.getAvailableFormat().toUpperCase());
		Exception.setText(download.getException() == null ? empty : download.getException().getMessage());

		if (download.getUrl() == null || download.getUrl().isEmpty())
			URL.setText(empty);
		else {
			URL.setText(R.string.dialog_download_url);
			URL.setOnClickListener(view -> {
				Context context = getContext();
				if (context != null)
					showURLDialog(context, download.getUrl());
			});
		}
		UpdateStatus();
	}

	public void UpdateStatus(@NonNull Download download) {
		this.download = download;
		UpdateStatus();
	}

	void UpdateStatus() {
		if (download == null) {
			Log.w(TAG, "download is null");
			return;
		}

		UpdateException(download.getException());

		if (download.getSize() <= 0)
			Progress.setText(String.format(Commons.getLocale(), "%d/%d", download.getCurrent(), download.getTotal()));
		else Progress.setText(String.format(Commons.getLocale(), "%d/%d (%s)", download.getCurrent(), download.getTotal(), Commons.FormatBytes(download.getSize())));

		UpdateStatus(download.getStatus());

		if (download.getDown() == null) PathDownload.setText(R.string.sym_empty);
		else {
			PathDownload.setText(download.getDown());
			if (Directories.isCacheExists(download.getDown()))
				StatusDownload.setImageResource(download.getStatus().getMetadata() != null && download.getStatus().getMetadata() ? R.color.red : R.color.yellow);
			else if (download.getStatus().getDownload() == Status.Download.COMPLETE)
				StatusDownload.setImageResource(R.color.green);
			else StatusDownload.setImageResource(R.color.SecondaryDark);
		}

		if (download.getConv() == null) PathConversion.setText(R.string.sym_empty);
		else {
			PathConversion.setText(download.getConv());
			if (Directories.isCacheExists(download.getConv()))
				StatusConversion.setImageResource(download.getStatus().getMetadata() != null && download.getStatus().getMetadata() ? R.color.red : R.color.yellow);
			else if (download.getStatus().getConvert() == Status.Convert.COMPLETE || download.getStatus().getConvert() == Status.Convert.SKIPPED)
				StatusConversion.setImageResource(R.color.green);
			else StatusConversion.setImageResource(R.color.SecondaryDark);
		}

		if (download.getMtdt() == null) PathMetadata.setText(R.string.sym_empty);
		else {
			PathMetadata.setText(download.getMtdt());
			if (download.getStatus().getMetadata() != null && download.getStatus().getMetadata())
				StatusMetadata.setImageResource(new File(Directories.getMUSIC(), download.getMtdt()).exists() ? R.color.green : R.color.red);
			else StatusMetadata.setImageResource(R.color.SecondaryDark);
		}
	}

	private void UpdateStatus(@Nullable Status status) {
		if (status == null || status.getDownload() == null)
			DLText.setText(R.string.sym_empty);
		else DLText.setText(status.getDownload().getValue());

		if (status == null ||status.getConvert() == null)
			Conversion.setText(R.string.sym_empty);
		else Conversion.setText(status.getConvert().getValue());

		if (status == null || status.getMetadata() == null) Metadata.setText(R.string.sym_empty);
		else if (status.getMetadata()) Metadata.setText(R.string.completed);
		else Metadata.setText(R.string.failed);
	}

	private void UpdateException(@Nullable Exception exception) {
		if (download.getException() instanceof IllegalArgumentException)
			Exception.setText(R.string.illegal_argument_exception);
		else if (download.getException() != null)
			Exception.setText(download.getException().getMessage());
		else
			Exception.setText(R.string.sym_empty);
	}

	private void showURLDialog(Context context, String url) {
		new AlertDialog.Builder(context)
				.setTitle(R.string.url)
				.setMessage(url)
				.setPositiveButton(getString(R.string.go), (dialog, which) -> {
					Intent intent = new Intent(Intent.ACTION_VIEW);
					intent.setData(Uri.parse(url));
					startActivity(intent);
				})
				.show();
	}

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
