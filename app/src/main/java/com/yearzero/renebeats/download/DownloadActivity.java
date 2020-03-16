package com.yearzero.renebeats.download;

import android.app.Dialog;
import android.content.ActivityNotFoundException;
import android.content.ComponentName;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.ServiceConnection;
import android.net.Uri;
import android.os.AsyncTask;
import android.os.Bundle;
import android.os.CountDownTimer;
import android.os.IBinder;
import android.util.Log;
import android.util.SparseArray;
import android.view.ViewGroup;
import android.view.WindowManager;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.Keep;
import androidx.annotation.NonNull;
import androidx.appcompat.app.ActionBar;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.localbroadcastmanager.content.LocalBroadcastManager;

import com.google.android.material.chip.Chip;
import com.google.android.material.chip.ChipGroup;
import com.google.android.material.snackbar.Snackbar;
import com.google.android.material.textfield.TextInputEditText;
import com.google.gson.Gson;
import com.squareup.picasso.Picasso;
import com.yausername.youtubedl_android.YoutubeDL;
import com.yausername.youtubedl_android.YoutubeDLException;
import com.yausername.youtubedl_android.mapper.VideoFormat;
import com.yausername.youtubedl_android.mapper.VideoInfo;
import com.yearzero.renebeats.Commons;
import com.yearzero.renebeats.Directories;
import com.yearzero.renebeats.InternalArgs;
import com.yearzero.renebeats.R;
import com.yearzero.renebeats.download.ui.DurationPicker;
import com.yearzero.renebeats.notification.DownloadReceiver;
import com.yearzero.renebeats.preferences.Preferences;
import com.yearzero.renebeats.preferences.enums.OverwriteMode;

import java.io.File;
import java.util.ArrayList;
import java.util.Locale;

import jp.wasabeef.picasso.transformations.RoundedCornersTransformation;

public class DownloadActivity extends AppCompatActivity implements ServiceConnection {
	private static final String TAG = "DownloadActivity";

	private ImageButton Home;
	private Button Download;

	private ImageView Image;
	private TextView Display;
	private Button Start, End, Swap, Youtube;
	private ChipGroup FormatGroup, BitrateGroup;
	private Chip[] Formats;
	private TextInputEditText Title, Artist, Album, Track, Year, Genres;
	//    private NachoTextView Genres;
	private CheckBox Normalize;
	private ImageButton NormalizeHelp;
	private Dialog retrieveDialog;

	private static String loc_info = "renebeats/download_activity thumbnail";

	private SparseArray<Short> bitrateViewIds = new SparseArray<>();

	private Query query;
	private Integer start, end; // Start and end times for trimming in seconds. Null means don't trim the start/end.

	private DownloadService service;
	private VideoInfo videoInfo; // Video metadata

	@Override
	protected void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		setContentView(R.layout.activity_download);
		ActionBar actionBar = getSupportActionBar();
		if (actionBar != null) actionBar.setDisplayShowTitleEnabled(false);
		getWindow().setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_STATE_HIDDEN);

		// Get query from intent and validate it
		Bundle bundle = getIntent().getExtras();
		if (bundle == null) {
			onBackPressed();
			return;
		}

		String YTShare = bundle.getString(Intent.EXTRA_TEXT, null);
		if (YTShare == null) {
			try {
				query = (Query) bundle.getSerializable(InternalArgs.DATA);
			} catch (ClassCastException e) {
				e.printStackTrace();
				onBackPressed();
			}
		} else {
			query = new Query(YTShare.substring(YTShare.lastIndexOf('/') + 1));
			Log.d(TAG, "YTShare Trimmed " + query.getYoutubeID());
		}

		// Initialize UI view fields
		Home = findViewById(R.id.home);
		Download = findViewById(R.id.download);
		Image = findViewById(R.id.image);
		Display = findViewById(R.id.display);
		Title = findViewById(R.id.title);
		Swap = findViewById(R.id.swap);
		Youtube = findViewById(R.id.youtube);
		Artist = findViewById(R.id.author);
		Album = findViewById(R.id.album);
		Year = findViewById(R.id.year);
		Track = findViewById(R.id.track);
		Genres = findViewById(R.id.genres);
		Start = findViewById(R.id.start);
		End = findViewById(R.id.end);
		Normalize = findViewById(R.id.exception);
		NormalizeHelp = findViewById(R.id.normalize_help);

		FormatGroup = findViewById(R.id.format_group);
		BitrateGroup = findViewById(R.id.bitrate_group);

		Home.setOnClickListener(v -> onBackPressed());
		Download.setOnClickListener(v -> Download());

		// Initialize UI based on query
		if (query.getThumbnail(Query.ThumbnailQuality.MaxRes) != null) LoadThumbnail();
		if (!query.getTitle().isEmpty()) {
			Display.setText(query.getTitle());
			UseGuesserMode();
		} else Artist.setText(query.getArtist());
		if (!query.getAlbum().isEmpty()) Album.setText(query.getAlbum());
		if (query.getYear() > 0) Year.setText(String.valueOf(query.getYear()));
		if (query instanceof Download) {
			Download d = (Download) query;
			this.start = d.getStart();
			this.end = d.getEnd();
		}

		if (savedInstanceState != null) {
			try {
				videoInfo = (VideoInfo) savedInstanceState.get(loc_info);
			} catch (ClassCastException e) {
				Log.w(TAG, "VideoInfo couldn't be retrieved from savedInstanceState");
			}
		}

		if (videoInfo != null) {
			onExtractionComplete(videoInfo);
		} else {
			FetchVideoInfo task = new FetchVideoInfo(query.getYoutubeID()).setCallback(this::onExtractionComplete);
			task.executeOnExecutor(AsyncTask.THREAD_POOL_EXECUTOR);

			retrieveDialog = new Dialog(this);
			retrieveDialog.setTitle(getString(R.string.download_retrieve));
			retrieveDialog.setCanceledOnTouchOutside(false);
			retrieveDialog.setContentView(R.layout.dialog_retrieving);
			retrieveDialog.findViewById(R.id.negative).setOnClickListener(v -> {
				retrieveDialog.dismiss();
				onBackPressed();
			});
			retrieveDialog.show();

			new CountDownTimer(Preferences.getTimeout(), Preferences.getTimeout()) {
				@Override
				public void onTick(long l) {}

				@Override
				public void onFinish() {
					if (task.getStatus() == AsyncTask.Status.RUNNING) {
						if (retrieveDialog != null) retrieveDialog.dismiss();

						try {
							new AlertDialog.Builder(DownloadActivity.this)
									.setTitle("Timeout")
									.setMessage("It has taken longer than expected to retrieve the data. Try again later.")
									.setPositiveButton("OK", (dialogInterface, i) -> {
										dialogInterface.dismiss();
										onBackPressed();
									}).show();
						} catch (WindowManager.BadTokenException e) {
							Log.w(TAG, "Activity has been closed. Attempted to show timeout dialog.");
						}
					}
				}
			}.start();
		}

		String[] farr = getResources().getStringArray(R.array.formats);
		int index = -1;
		for (int i = 0; i < farr.length; i++) {
			if (farr[i].toUpperCase().trim().equals(Preferences.getFormat().toUpperCase())) {
				index = i;
				break;
			}
		}
		index = Math.max(0, index);
		Formats = new Chip[farr.length];

		for (int i = 0; i < farr.length; i++) {
			Chip chip = new Chip(this, null, R.style.Widget_MaterialComponents_Chip_Choice);
			chip.setText(farr[i].toUpperCase());
			chip.setCheckable(true);
			chip.setChipStrokeColorResource(R.color.Accent);
			chip.setChipStrokeWidth(1F);
			chip.setOnClickListener(v -> ((Chip) v).setChecked(true));
			Formats[i] = chip;
			FormatGroup.addView(chip, i, new ChipGroup.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT));
		}
		FormatGroup.check(Formats[index].getId());

		//        Genres.addChipTerminator(',', ChipTerminatorHandler.BEHAVIOR_CHIPIFY_ALL);
		//        Genres.addChipTerminator(';', ChipTerminatorHandler.BEHAVIOR_CHIPIFY_ALL);
		//        Genres.addChipTerminator('\n', ChipTerminatorHandler.BEHAVIOR_CHIPIFY_ALL);
		//        if (query.genres != null) Genres.setText(Arrays.asList(query.genres));
		Genres.setText(query.getGenres());

		Start.setOnClickListener(v -> {
			if (videoInfo == null) {
				Log.e(TAG, "videoInfo is null");
				return;
			}
			if (videoInfo.duration <= 0) {
				Log.e(TAG, "Length is 0");
				return;
			}

			DurationPicker dialog = new DurationPicker(DownloadActivity.this);
			dialog.setTitle(R.string.download_time_start);

			if (videoInfo.duration > 3600)
				dialog.setEnabled(DurationPicker.Mode.Hour);
			else if (videoInfo.duration > 60)
				dialog.setEnabled(DurationPicker.Mode.Minute);
			else dialog.setEnabled(DurationPicker.Mode.Second);

			dialog.setTime(start == null ? 0 : start);
			dialog.setMaxTime(videoInfo.duration);
			dialog.setCallbacks(new DurationPicker.Callbacks() {
				@Override
				public String Validate(int time) {
					if (time >= (end == null ? videoInfo.duration : end))
						return getString(R.string.download_time_start_end);
					return null;
				}

				@Override
				public void onSubmit(int time) {
					start = time;
					RefreshTimeRange();
				}
			});
			dialog.show();
		});

		End.setOnClickListener(v -> {
			if (videoInfo == null) {
				Log.e(TAG, "videoInfo is null");
				return;
			}
			if (videoInfo.duration <= 0) {
				Log.e(TAG, "Length is 0");
				return;
			}
			DurationPicker dialog = new DurationPicker(DownloadActivity.this);
			dialog.setTitle(R.string.download_time_end);
			dialog.setTime(end == null ? 0 : end);

			if (videoInfo.duration > 3600)
				dialog.setEnabled(DurationPicker.Mode.Hour);
			else if (videoInfo.duration > 60)
				dialog.setEnabled(DurationPicker.Mode.Minute);
			else dialog.setEnabled(DurationPicker.Mode.Second);

			dialog.setTime(end == null ? videoInfo.duration : end);
			dialog.setMaxTime(videoInfo.duration);
			dialog.setCallbacks(new DurationPicker.Callbacks() {
				@Override
				public String Validate(int time) {
					if (start != null && time <= start)
						return getString(R.string.download_time_end_start);
					if (end != null && end > videoInfo.duration)
						return getString(R.string.download_time_end_length);
					return null;
				}

				@Override
				public void onSubmit(int time) {
					end = time;
					RefreshTimeRange();
				}
			});
			dialog.show();
		});

		Swap.setOnClickListener(view -> {
			if (Title.getText() == null || Artist.getText() == null) return;
			String temp = Title.getText().toString();
			Title.setText(Artist.getText().toString());
			Artist.setText(temp);
		});

		Youtube.setOnClickListener(v -> {
			Intent appIntent = new Intent(Intent.ACTION_VIEW, Uri.parse("vnd.youtube:" + query.getYoutubeID()));
			Intent webIntent = new Intent(Intent.ACTION_VIEW,
					Uri.parse("http://www.youtube.com/watch?v=" + query.getYoutubeID()));
			try {
				startActivity(appIntent);
			} catch (ActivityNotFoundException ex) {
				startActivity(webIntent);
			}
		});

		Normalize.setChecked(Preferences.getNormalize());
		NormalizeHelp.setOnClickListener(v -> new AlertDialog.Builder(DownloadActivity.this)
				.setMessage(R.string.download_normalize_msg)
				.setPositiveButton(R.string.ok, (dialog, which) -> dialog.dismiss()).show());
	}

	//// Callback from extract video metadata from Youtube
	public void onExtractionComplete(VideoInfo videoInfo) {
		retrieveDialog.dismiss();

		if (videoInfo == null) return;

		this.videoInfo = videoInfo;

		if (videoInfo.duration <= 0) {
			onBackPressed();
			Toast.makeText(DownloadActivity.this, R.string.download_invalid_youtube_id, Toast.LENGTH_LONG).show();
			return;
		}

		if (query.getTitle().isEmpty()) {
			query.setTitle(videoInfo.title);
			Display.setText(query.getTitle());

			UseGuesserMode();
		} else if (query.getArtist().isEmpty()) {
			query.setArtist(videoInfo.uploader);
			Artist.setText(query.getArtist());
		}

		// Fill missing thumbnails (I don't think this is necessary honestly
		if (query.getThumbMax() == null) query.setThumbMax(videoInfo.thumbnail);
		if (query.getThumbHigh() == null) query.setThumbHigh(videoInfo.thumbnail);
		if (query.getThumbMedium() == null) query.setThumbMedium(videoInfo.thumbnail);
		if (query.getThumbDefault() == null) query.setThumbDefault(videoInfo.thumbnail);
		if (query.getThumbStandard() == null) query.setThumbStandard(videoInfo.thumbnail);
		LoadThumbnail();

		int maxbit = Integer.MIN_VALUE;

		if (retrieveDialog != null && retrieveDialog.isShowing()) retrieveDialog.dismiss();

		RefreshTimeRange();

		for (VideoFormat format : videoInfo.formats)
			maxbit = Math.max(format.abr, maxbit);

		int cnt = 0;
		for (int i = 0; i < Preferences.getBITRATES().length && Preferences.getBITRATES()[i] <= maxbit; i++) cnt++;

		int lastChipId = -1;

		for (int i = 0; i < cnt; i++) {
			short bitrate = Preferences.getBITRATES()[i];

			Chip chip = new Chip(DownloadActivity.this, null, R.style.Widget_MaterialComponents_Chip_Choice);
			chip.setText(String.format(Commons.getLocale(), getString(R.string.kbps), bitrate));
			chip.setCheckable(true);
			chip.setChipStrokeColorResource(R.color.Accent);
			chip.setChipStrokeWidth(1f);
			chip.setOnClickListener(v -> ((Chip) v).setChecked(true));
			BitrateGroup.addView(chip, i, new ChipGroup.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT));

			// Cache ids
			bitrateViewIds.append(chip.getId(), bitrate);
			lastChipId = chip.getId();
		}
		if (lastChipId != -1) BitrateGroup.check(lastChipId);

	}

	//// Set fields to the metadata of the video according to GuesserMode
	private void UseGuesserMode() {
		switch (Preferences.getGuesser_mode()) {
			case TITLE_UPLOADER:
				Artist.setText(query.getArtist());
			case TITLE_ONLY:
				Title.setText(query.getTitle());
				break;
			case PREDICT:
				String[] result = extractTitleAndArtist(query.getTitle(), query.getArtist());
				Title.setText(result[0]);
				Artist.setText(result[1]);
				break;
		}
	}

	//// Constructs the download object from the UI
	public Download CreateDownload() {
		if (query == null || query.getYoutubeID() == null || videoInfo == null) return null;

		boolean invalid = false;

		if (Title.getText() == null || Title.getText().toString().isEmpty()) {
			Title.setError(getString(R.string.download_invalid_title));
			invalid = true;
		} else query.setTitle(Title.getText().toString());

		if (Artist.getText() != null)
			query.setArtist(Artist.getText().toString());
		if (Album.getText() != null)
			query.setAlbum(Album.getText().toString());


		// TODO: C'mon fix this
		short bitrate = Preferences.getBitrate();

		Chip chip = findViewById(BitrateGroup.getCheckedChipId());
		if (chip != null)
			bitrate = bitrateViewIds.get(chip.getId(), bitrate);

		if (!(Track.getText() == null || Track.getText().toString().trim().isEmpty())) {
			try {
				query.setTrack(Integer.parseInt(Track.getText().toString()));
			} catch (NumberFormatException ignore) {
				Track.setError(getString(R.string.download_invalid_track));
				invalid = true;
			}
		}

		if (!(Year.getText() == null || Year.getText().toString().isEmpty())) {
			try {
				query.setYear(Integer.parseInt(Year.getText().toString()));
			} catch (NumberFormatException ignore) {
				Snackbar.make(findViewById(R.id.main), "Invalid Year Number", Snackbar.LENGTH_LONG).show();
				Year.setError(getString(R.string.download_invalid_year));
				invalid = true;
			}
		}

		if (invalid) return null;

		String format = ((Chip) findViewById(FormatGroup.getCheckedChipId())).getText().toString().toLowerCase().trim();
		String url = null;
		String availableFormat = null;
		boolean convert = true;
		// region Extract URL
		int high_bit = -1;

		for (VideoFormat videoFormat : videoInfo.formats) {
			if (videoFormat == null || videoFormat.abr <= high_bit || high_bit >= bitrate)
				continue;
			high_bit = videoFormat.abr;
			url = videoFormat.url;
			availableFormat = videoFormat.ext;
			convert = !(format.toLowerCase().equals(videoFormat.ext.toLowerCase()) && high_bit == bitrate);
		}
		// endregion

		Download download = new Download(
				query,
				bitrate,
				format,
				url,
				start,
				end != null && end == Math.floor(videoInfo.duration) ? null : end,
				Normalize.isChecked(),
				videoInfo.duration
		);
		download.setAvailableFormat(availableFormat);
		download.setConvert(convert);
		return download;
	}

	//// Main method to start a download
	private void Download() {
		// Construct the download from CreateDownload() and verify it
		Download args = CreateDownload();
		if (args == null) {
			Snackbar.make(findViewById(R.id.main), R.string.download_invalid, Snackbar.LENGTH_LONG)
					.setAction(R.string.retry, v -> Download())
					.show();
			return;
		}
		String name = args.getFilenameWithExt();
		args.setGenres(Genres.getText() == null ? "" : Genres.getText().toString());

		// Handle conflicting file names according to the user's preferences
		if (Preferences.getOverwrite() == OverwriteMode.PROMPT && new File(Directories.getMUSIC(), name).exists()) {
			new AlertDialog.Builder(this)
					.setTitle(R.string.download_conflict)
					.setMessage(String.format(Locale.ENGLISH, getString(R.string.download_conflict_msg), name))
					.setPositiveButton(R.string.overwrite, (dialogInterface, i) -> {
						args.setOverwrite(true);
						ServiceCheck(args);
					})
					.setNeutralButton(R.string.cancel, null)
					.setNegativeButton(R.string.append_suffix, (dialogInterface, i) -> {
						args.setOverwrite(false);
						ServiceCheck(args);
					})
					.show();
		} else { // Start the download if preference set to APPEND or OVERWRITE
			args.setOverwrite(false);
			ServiceCheck(args);
		}
	}

	//// Check if the download is already running
	private void ServiceCheck(Download args) {
		// Start the download if the service is not running
		if (service == null) {
			InitDownload(args);
			bindService(new Intent(this, DownloadService.class), this, 0);
			return;
		}

		// Find the download in running and queueing downloads from service
		String name = args.getFilenameWithExt();

		ArrayList<Download> array = new ArrayList<>(service.getQueue());
		array.addAll(service.getRunning());
		boolean match = false;

		for (Download d : array) {
			if (d.getFilenameWithExt().equals(name)) {
				match = true;
				break;
			}
		}

		// If it's already running, show a dialog
		if (match) {
			new AlertDialog.Builder(this)
					.setTitle(R.string.download_already)
					.setMessage(R.string.download_already_msg)
					.setPositiveButton(R.string.download_already_positive, (dialog, which) -> {
						service.cancel(args.getDownloadId());
						InitDownload(args);
					})
					.setNeutralButton(R.string.retry, (dialog, which) -> InitDownload(args))
					.setNegativeButton(R.string.cancel, null)
					.show();
		} else InitDownload(args); // Else, start the download
	}

	//// Send the download to the service
	private void InitDownload(Download args) {
		//        if (Commons.downloadReceiver == null) {
		//            Commons.downloadReceiver = new DownloadReceiver(this, true);
		//            LocalBroadcastManager.getInstance(this).registerReceiver(Commons.downloadReceiver, new IntentFilter(DownloadService.TAG));
		//        }
		LocalBroadcastManager.getInstance(this).registerReceiver(new DownloadReceiver(this), new IntentFilter(DownloadService.TAG));

		Intent service = new Intent(this, DownloadService.class);
		service.putExtra(InternalArgs.DATA, args);
		startService(service);

		Intent intent = new Intent(this, MainActivity.class);
		intent.putExtra(InternalArgs.INDEX, 0);
		intent.setFlags(Intent.FLAG_ACTIVITY_CLEAR_TASK | Intent.FLAG_ACTIVITY_NEW_TASK);
		startActivity(intent);
		overridePendingTransition(R.anim.slide_left_in, R.anim.slide_left_out);
		Toast.makeText(getApplicationContext(), getString(R.string.download_start), Toast.LENGTH_LONG).show();
	}

	// String[] format is [title, artist]
	// Get the title and artist of a video from its metadata according to GuesserMode.
	// This is GuesserMode's underlying algorithm
	private String[] extractTitleAndArtist(String title, String uploader) {
		if (title == null) return null;

		if (title.contains(" - ")) {
			String[] split = title.replaceAll("(?i)[(\\[}](official)?\\s*(?:audio|video|(?:music|lyrics?)\\s+video)[)\\]}]", "").replaceAll("(?i)\\(?lyrics\\)", "").trim().split("-");
			split[0] = split[0].trim();
			split[1] = split[1].trim();
			if (split[0].matches("(?i)(?:.*\\s+|\\s*)(?:ft\\.?|feat\\.?|featuring)\\s++.+") || Preferences.getArtist_first())
				return new String[]{split[1], split[0]};
			else //if (split[1].matches("(?i)(?:.*\\s+|\\s*)(?:ft\\.?|feat\\.?|featuring)\\s++.+"))
				return split;
		} else return new String[]{title, uploader == null ? "" : uploader.replace("VEVO", "")};
	}

	// Get that thumbnail ing Picasso
	private void LoadThumbnail() {
		Picasso.get().load(query.getThumbnail(Preferences.getDownloadImage()))
				.placeholder(R.color.SecondaryDark)
				.transform(new RoundedCornersTransformation((int) getResources().getDimension(R.dimen.thumbnail_radius), 0))
				.centerCrop()
				.fit()
				.into(Image);
	}

	private void RefreshTimeRange() {
		if (videoInfo == null) return;
		StringBuilder start = new StringBuilder();
		StringBuilder end = new StringBuilder();

		short sh = this.start == null ? 0 : (short) Math.floor(this.start / 3600f);
		short sm = this.start == null ? 0 : (short) (Math.floor(this.start / 60f) % 60);
		short ss = this.start == null ? 0 : (short) (this.start % 60);

		short eh = (short) Math.floor((this.end == null ? videoInfo.duration : this.end) / 3600f);
		short em = (short) (Math.floor((this.end == null ? videoInfo.duration : this.end) / 60f) % 60);
		short es = (short) ((this.end == null ? videoInfo.duration : this.end) % 60);

		if (eh > 0) {
			start.append(sh).append(':');
			end.append(eh).append(':');
			if (sm < 10) start.append('0');
			if (em < 10) end.append('0');
		}

		start.append(sm).append(':');
		if (ss < 10) start.append('0');
		start.append(ss);
		end.append(em).append(':');
		if (es < 10) end.append('0');
		end.append(es);
		Start.setText(start.toString());
		End.setText(end.toString());
	}

	@Override
	public void onSaveInstanceState(@NonNull Bundle outState) {
		super.onSaveInstanceState(outState);
		outState.putString(loc_info, new Gson().toJson(videoInfo));
	}

	@Override
	protected void onPause() {
		unbindService(this);
		super.onPause();
	}

	@Override
	protected void onResume() {
		bindService(new Intent(this, DownloadService.class), this, 0);
		super.onResume();
	}

	@Override
	protected void onDestroy() {
		if (retrieveDialog != null) retrieveDialog.dismiss();
		videoInfo = null;
		super.onDestroy();
	}

	@Override
	public void onServiceConnected(ComponentName name, IBinder binder) {
		service = ((DownloadService.LocalBinder) binder).getService();
	}

	@Override
	public void onServiceDisconnected(ComponentName name) {
		service = null;
	}

	@Keep
	private static class FetchVideoInfo extends AsyncTask<Void, Void, VideoInfo> {
		interface Callback {
			void onExtractionComplete(VideoInfo videoInfo);
		}

		private Callback callback;
		private String id;

		FetchVideoInfo(String id) {
			this.id = id;
		}

		FetchVideoInfo setCallback(Callback callback) {
			this.callback = callback;
			return this;
		}

		@Override
		protected VideoInfo doInBackground(Void... voids) {
			try {
				return YoutubeDL.getInstance().getInfo("https://www.youtube.com/watch?v=" + id);
			} catch (YoutubeDLException | InterruptedException e) {
				e.printStackTrace();
			}
			return null;
		}

		@Override
		protected void onPostExecute(VideoInfo videoInfo) {
			if (callback != null) callback.onExtractionComplete(videoInfo);
			super.onPostExecute(videoInfo);
		}
	}

}
