package com.yearzero.renebeats.download;

import android.annotation.SuppressLint;
import android.app.Dialog;
import android.content.ActivityNotFoundException;
import android.content.ComponentName;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.ServiceConnection;
import android.net.Uri;
import android.os.AsyncTask;
import android.os.Bundle;
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

import androidx.annotation.NonNull;
import androidx.appcompat.app.ActionBar;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.localbroadcastmanager.content.LocalBroadcastManager;

import com.google.android.gms.common.util.ArrayUtils;
import com.google.android.material.chip.Chip;
import com.google.android.material.chip.ChipGroup;
import com.google.android.material.snackbar.Snackbar;
import com.google.android.material.textfield.TextInputEditText;
import com.squareup.picasso.Picasso;
import com.yearzero.renebeats.Directories;
import com.yearzero.renebeats.InternalArgs;
import com.yearzero.renebeats.R;
import com.yearzero.renebeats.download.ui.DurationPicker;
import com.yearzero.renebeats.notification.DownloadReceiver;
import com.yearzero.renebeats.preferences.Preferences;
import com.yearzero.renebeats.preferences.enums.GuesserMode;
import com.yearzero.renebeats.preferences.enums.OverwriteMode;

import org.apache.commons.text.StringEscapeUtils;

import java.io.File;

import jp.wasabeef.picasso.transformations.RoundedCornersTransformation;

public class DownloadActivity extends AppCompatActivity implements ServiceConnection {
    private static final String TAG = "DownloadActivity";

    //TODO: File name appending suggestion (such as ' (1)') should also check running downloads to avoid request.getId() conflict (Accomplished by setting download concurrency to 1)
    //TODO: Proper thumbnail placement when landscape/large screen

    private ImageButton Home;
    private Button Download;

    private ImageView Image;
    private TextView Display;
    private Button Start, End, Swap, Youtube;
    private ChipGroup FormatGroup, BitrateGroup;
    private Chip[] Bitrates, Formats;
    private TextInputEditText Title, Artist, Album, Track, Year, Genres;
//    private NachoTextView Genres;
    private CheckBox Normalize;
    private ImageButton NormalizeHelp;
    private Dialog retrieveDialog;

    private static String loc_sparse = "renebeats/download_activity sparseArray";
    private static String loc_meta = "renebeats/download_activity videoMeta";
    private static String loc_length = "renebeats/download_activity length";

    private YouTubeExtractor.YtFile[] sparseArray;
    private YouTubeExtractor.VideoMeta videoMeta;
    private int length = -1;

    private Query query;
    private Integer start, end;

    private DownloadService service;

    @SuppressLint({"StaticFieldLeak", "WrongViewCast"})
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_download);
        ActionBar actionBar = getSupportActionBar();
        if (actionBar != null) actionBar.setDisplayShowTitleEnabled(false);
        getWindow().setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_STATE_HIDDEN);

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

        if (query.getThumbnail(Query.ThumbnailQuality.MaxRes) != null) LoadThumbnail();
        if (query.getTitle() != null) {
            Display.setText(query.getTitle());
            if (Preferences.getGuesser_mode() == GuesserMode.TITLE_UPLOADER) {
                Title.setText(query.getTitle());
                Artist.setText(query.getArtist());
            } else if (Preferences.getGuesser_mode() == GuesserMode.PREDICT) {
                String[] result = extractTitleAndArtist(query.getTitle(), query.getArtist());
                Title.setText(result[0]);
                Artist.setText(result[1]);
            }
        } else if (query.getArtist() != null) Artist.setText(query.getArtist());
        if (query.getAlbum() != null) Album.setText(query.getAlbum());
        if (query.getYear() > 0) Year.setText(String.valueOf(query.getYear()));
        if (query instanceof Download) {
            Download d = (Download) query;
            this.start = d.getStart();
            this.end = d.getEnd();
        }

        Object a, b;
        if (savedInstanceState == null) {
            a = null;
            b = null;
        } else {
            a = savedInstanceState.get(loc_sparse);
            b = savedInstanceState.get(loc_meta);
            length = savedInstanceState.getInt(loc_length, length);
        }

        if (length >= 0 && a instanceof YouTubeExtractor.YtFile[] && b instanceof YouTubeExtractor.VideoMeta) {
            sparseArray = (YouTubeExtractor.YtFile[]) a;
            videoMeta = (YouTubeExtractor.VideoMeta) b;
            onExtractionComplete(sparseArray, videoMeta);
        } else {
            YouTubeExtractor yt = new YouTubeExtractor(this) {
                @Override
                protected void onExtractionComplete(SparseArray<YtFile> data, VideoMeta videoMeta) {
                    if (data == null) {
                        Log.e(TAG, "Retrieved SparseArray is null");
                        return;
                    }
                    YouTubeExtractor.YtFile[] array = new YouTubeExtractor.YtFile[data.size()];
                    for (int i = 0; i < array.length; i++) array[i] = data.valueAt(i);
                    DownloadActivity.this.onExtractionComplete(array, videoMeta);
                }

                @Override
                protected void onTimeout() {
                    if (retrieveDialog != null) retrieveDialog.dismiss();

                    new AlertDialog.Builder(DownloadActivity.this)
                            .setTitle("Timeout")
                            .setMessage("It has taken longer than expected to retrieve the data. Try again later.")
                            .setPositiveButton("OK", (dialogInterface, i) -> {
                                dialogInterface.dismiss();
                                onBackPressed();
                            }).show();
                }
            };
            yt.setTimeout(Preferences.getTimeout());
            yt.extractOnExecutor(AsyncTask.THREAD_POOL_EXECUTOR, "https://www.youtube.com/watch?v=" + query.getYoutubeID(), true, false);

            retrieveDialog = new Dialog(this);
            retrieveDialog.setTitle("Retrieving...");
            retrieveDialog.setCanceledOnTouchOutside(false);
            retrieveDialog.setContentView(R.layout.dialog_retrieving);
            retrieveDialog.findViewById(R.id.negative).setOnClickListener(v -> {
                retrieveDialog.dismiss();
                onBackPressed();
            });
            retrieveDialog.show();
        }

//        FormatGroup.setAdapter(new ArrayAdapter<>(DownloadActivity.this, android.R.layout.simple_spinner_dropdown_item, formats));
        String[] farr = getResources().getStringArray(R.array.formats);
        int index = -1;
        for (int i = 0; i < farr.length; i++) {
            if (farr[i].toUpperCase().trim().equals(Preferences.getFormat().toUpperCase())) {
                index = i;
                break;
            }
        }
        index = index < 0 ? 0 : index;
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
        if (query.getGenres() != null) Genres.setText(query.getGenres());

        Start.setOnClickListener(v -> {
            if (length <= 0) {
                Log.e(TAG, "Length is 0");
                return;
            }
            DurationPicker dialog = new DurationPicker(DownloadActivity.this);
            dialog.setTitle("Set a start time");

            if (length > 3600)
                dialog.setEnabled(DurationPicker.Mode.Hour);
            else if (length > 60)
                dialog.setEnabled(DurationPicker.Mode.Minute);
            else dialog.setEnabled(DurationPicker.Mode.Second);

            dialog.setTime(start == null ? 0 : start);
            dialog.setMaxTime(length);
            dialog.setCallbacks(new DurationPicker.Callbacks() {
                @Override
                public String Validate(int time) {
                    if (time >= (end == null ? length : end))
                        return "Start time cannot be equal or exceed the end time";
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
            if (length <= 0) {
                Log.e(TAG, "Length is 0");
                return;
            }
            DurationPicker dialog = new DurationPicker(DownloadActivity.this);
            dialog.setTitle("Set an end time");
            dialog.setTime(end == null ? 0 : end);

            if (length > 3600)
                dialog.setEnabled(DurationPicker.Mode.Hour);
            else if (length > 60)
                dialog.setEnabled(DurationPicker.Mode.Minute);
            else dialog.setEnabled(DurationPicker.Mode.Second);

            dialog.setTime(end == null ? length : end);
            dialog.setMaxTime(length);
            dialog.setCallbacks(new DurationPicker.Callbacks() {
                @Override
                public String Validate(int time) {
                    if (start != null && time <= start)
                        return "End time cannot be equal or be less than the start time";
                    if (end != null && end > length)
                        return "End time cannot exceed the video's size";
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
                .setMessage("Some audio/videos may have a quieter audio than other audios. This is because sometimes an audio/video file does not use the full volume range available and thus resulting in its audio being very quiet. Normalization will increase the audio's volume in such that it will utilize the whole available volume range though it may take more time.")
                .setPositiveButton("OK", (dialog, which) -> dialog.dismiss()).show());
    }

    public void onExtractionComplete(YouTubeExtractor.YtFile[] data, YouTubeExtractor.VideoMeta videoMeta) {
        sparseArray = data;
        this.videoMeta = videoMeta;

        if (videoMeta.getVideoLength() <= 0) {
            onBackPressed();
            Toast.makeText(DownloadActivity.this, "Invalid video ID", Toast.LENGTH_LONG).show();
            return;
        }

        if (query.getTitle() == null) {
            query.setTitle(StringEscapeUtils.unescapeXml(videoMeta.getTitle()));
            Display.setText(query.getTitle());

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
        } else if (query.getArtist() == null) {
            query.setArtist(videoMeta.getAuthor());
            Artist.setText(query.getArtist());
        }

        boolean thumbnail = false;
        if (query.getThumbMax() == null) {
            query.setThumbMax(videoMeta.getMaxResImageUrl());
            thumbnail = true;
        }
        if (query.getThumbHigh() == null) {
            query.setThumbHigh(videoMeta.getHqImageUrl());
            thumbnail = true;
        }
        if (query.getThumbMedium() == null) {
            query.setThumbMedium(videoMeta.getMqImageUrl());
            thumbnail = true;
        }
        if (query.getThumbDefault() == null) {
            query.setThumbDefault(videoMeta.getThumbUrl());
            thumbnail = true;
        }
        if (query.getThumbStandard() == null) {
            query.setThumbStandard(videoMeta.getSdImageUrl());
            thumbnail = true;
        }

        if (thumbnail) LoadThumbnail();

        int maxbit = Integer.MIN_VALUE;
        length = (int) videoMeta.getVideoLength();

        if (retrieveDialog != null && retrieveDialog.isShowing()) retrieveDialog.dismiss();

        RefreshTimeRange();

        if (data == null) return;
        for (YouTubeExtractor.YtFile datum : data)
            maxbit = Math.max(datum.getFormat().getAudioBitrate(), maxbit);

        int cnt = 0;
        for (int i = 0; i < Preferences.getBITRATES().length && Preferences.getBITRATES()[i] <= maxbit; i++) cnt++;

        Bitrates = new Chip[cnt];
        String[] barr = getResources().getStringArray(R.array.bitrates);
        for (int i = 0; i < barr.length; i++) barr[i] += " kbps";

        for (int i = 0; i < cnt; i++) {
            Chip chip = new Chip(DownloadActivity.this, null, R.style.Widget_MaterialComponents_Chip_Choice);
            chip.setText(barr[i]);
            chip.setCheckable(true);
            chip.setChipStrokeColorResource(R.color.Accent);
            chip.setChipStrokeWidth(1f);
            chip.setOnClickListener(v -> ((Chip) v).setChecked(true));
            Bitrates[i] = chip;
            BitrateGroup.addView(chip, i, new ChipGroup.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        }
        BitrateGroup.check(Bitrates[Bitrates.length - 1].getId());

    }

    public Download CreateDownload() {
        if (query == null || query.getYoutubeID() == null) return null;

        boolean invalid = false;

        if (Title.getText() == null || Title.getText().toString().isEmpty()) {
            Title.setError("There must be a title");
            invalid = true;
        } else query.setTitle(Title.getText().toString());

        if (Artist.getText() != null)
            query.setArtist(Artist.getText().toString());
        if (!(Album.getText() == null))
            query.setAlbum(Album.getText().toString());

        short bitrate = Preferences.getBitrate();

        Chip chip = findViewById(BitrateGroup.getCheckedChipId());
        if (chip != null) {
            String s = chip.getText().toString();
            try {
                bitrate = Short.parseShort(s.substring(0, s.length() - 5));
            } catch (NumberFormatException e) {
                e.printStackTrace();
                invalid = true;
            }
        }

        if (!(Track.getText() == null || Track.getText().toString().trim().isEmpty())) {
            try {
                query.setTrack(Integer.parseInt(Track.getText().toString()));
            } catch (NumberFormatException ignore) {
                Track.setError("Invalid Track Number");
                invalid = true;
            }
        }

        if (!(Year.getText() == null || Year.getText().toString().isEmpty())) {
            try {
                query.setYear(Integer.parseInt(Year.getText().toString()));
            } catch (NumberFormatException ignore) {
                Snackbar.make(findViewById(R.id.main), "Invalid Year Number", Snackbar.LENGTH_LONG).show();
                Year.setText("Invalid Year Number");
                invalid = true;
            }
        }

        if (invalid) return null;

        String format = ((Chip) findViewById(FormatGroup.getCheckedChipId())).getText().toString().toLowerCase().trim();

        return new Download(
                query,
                bitrate,
                format,
                sparseArray,
                start,
                end != null && end == Math.floor(length) ? null : end,
                Normalize.isChecked(),
                length
        );
    }

    private void Download() {
        Download args = CreateDownload();
        if (args == null) {
            Snackbar.make(findViewById(R.id.main), "Invalid Download Arguments", Snackbar.LENGTH_LONG)
                    .setAction("Retry", v -> Download())
                    .show();
            return;
        }
        String name = args.getFilenameWithExt();
        args.setGenres(Genres.getText() == null ? null : Genres.getText().toString()); //getChipAndTokenValues().toArray(new String[0]);

        if (Preferences.getOverwrite() == OverwriteMode.PROMPT && new File(Directories.getMUSIC(), name).exists()) {
            new AlertDialog.Builder(this)
                    .setTitle("Conflicting File Names")
                    .setMessage("There is already a file called " + name + " in the Music folder. A suffix such as '(1)' can be appended to the file name to avoid conflicts.")
                    .setPositiveButton("Overwrite", (dialogInterface, i) -> {
                        args.setOverwrite(true);
                        ServiceCheck(args);
                    })
                    .setNeutralButton("Cancel", null)
                    .setNegativeButton("Append Suffix", (dialogInterface, i) -> {
                        args.setOverwrite(false);
                        ServiceCheck(args);
                    })
                    .show();
        } else {
            args.setOverwrite(false);
            ServiceCheck(args);
        }
    }

    private void ServiceCheck(Download args) {
        if (service == null) {
            InitDownload(args);
            bindService(new Intent(this, DownloadService.class), this, 0);
            return;
        }
        String name = args.getFilenameWithExt();

        Download[] array = ArrayUtils.concat(service.getQueue(), service.getRunning());
        boolean match = false;

        for (Download d : array) {
            if (d.getFilenameWithExt().equals(name)) {
                match = true;
                break;
            }
        }

        if (match) {
            new AlertDialog.Builder(this)
                    .setTitle("Already Running")
                    .setMessage("Another instance of this download is already running")
                    .setPositiveButton("Cancel and Run This One", (dialog, which) -> {
                        service.cancel(args.getId());
                        InitDownload(args);
                    })
                    .setNeutralButton("Run Again", (dialog, which) -> InitDownload(args))
                    .setNegativeButton("Cancel", null)
                    .show();
        } else InitDownload(args);
    }

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
        Toast.makeText(getApplicationContext(), "Download started", Toast.LENGTH_LONG).show();
    }

    // String[] format is [title, artist]
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

    private void LoadThumbnail() {
        Picasso.get().load(query.getThumbnail(Preferences.getDownloadImage()))
                .placeholder(R.color.SecondaryDark)
                .transform(new RoundedCornersTransformation((int) getResources().getDimension(R.dimen.thumbnail_radius), 0))
                .centerCrop()
                .fit()
                .into(Image);
    }

    private void RefreshTimeRange() {
        StringBuilder start = new StringBuilder();
        StringBuilder end = new StringBuilder();

        short sh = this.start == null ? 0 : (short) Math.floor(this.start / 3600f);
        short sm = this.start == null ? 0 : (short) (Math.floor(this.start / 60f) % 60);
        short ss = this.start == null ? 0 : (short) (this.start % 60);

        short eh = (short) Math.floor((this.end == null ? length : this.end) / 3600f);
        short em = (short) (Math.floor((this.end == null ? length : this.end) / 60f) % 60);
        short es = (short) ((this.end == null ? length : this.end) % 60);

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

        outState.putSerializable(loc_sparse, sparseArray);
        outState.putSerializable(loc_meta, videoMeta);
        outState.putInt(loc_length, length);
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
    protected void onDestroy() {
        if (retrieveDialog != null) retrieveDialog.dismiss();
        length = -1;
        sparseArray = null;
        videoMeta = null;
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
}
