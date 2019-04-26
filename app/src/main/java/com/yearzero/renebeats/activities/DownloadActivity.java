package com.yearzero.renebeats.activities;

import android.annotation.SuppressLint;
import android.app.Dialog;
import android.content.ActivityNotFoundException;
import android.content.Intent;
import android.content.IntentFilter;
import android.net.Uri;
import android.os.AsyncTask;
import android.os.Bundle;
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

import androidx.appcompat.app.ActionBar;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.localbroadcastmanager.content.LocalBroadcastManager;

import com.google.android.material.chip.Chip;
import com.google.android.material.chip.ChipGroup;
import com.google.android.material.snackbar.Snackbar;
import com.google.android.material.textfield.TextInputEditText;
import com.hootsuite.nachos.NachoTextView;
import com.hootsuite.nachos.terminator.ChipTerminatorHandler;
import com.squareup.picasso.Picasso;
import com.yearzero.renebeats.Commons;
import com.yearzero.renebeats.DownloadReceiver;
import com.yearzero.renebeats.DownloadService;
import com.yearzero.renebeats.DurationPicker;
import com.yearzero.renebeats.R;
import com.yearzero.renebeats.YouTubeExtractor;
import com.yearzero.renebeats.classes.Download;
import com.yearzero.renebeats.classes.Query;

import org.apache.commons.text.StringEscapeUtils;

import java.io.File;

import jp.wasabeef.picasso.transformations.RoundedCornersTransformation;

public class DownloadActivity extends AppCompatActivity implements YouTubeExtractor.Callbacks {
    private static final String TAG = "DownloadActivity";

    private ImageButton Home;
    private Button Download;

    private ImageView Image;
    private TextView Display;
    private Button Start, End, Swap, Youtube;
    private ChipGroup FormatGroup, BitrateGroup;
    private Chip[] Bitrates, Formats;
    private TextInputEditText Title, Artist, Album, Track, Year;
    private NachoTextView Genres;
    private CheckBox Normalize;
    private ImageButton NormalizeHelp;

    private static Dialog retrieveDialog;
    private static SparseArray<YouTubeExtractor.YtFile> sparseArray;
    private static YouTubeExtractor.VideoMeta videoMeta;
    private static int length = -1;

    private Query query;
    private Integer start, end;

    @SuppressLint({"StaticFieldLeak", "WrongViewCast"})
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_download);
        ActionBar actionBar = getSupportActionBar();
        if (actionBar != null) actionBar.setDisplayShowTitleEnabled(false);
        getWindow().setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_STATE_HIDDEN);

        Bundle bundle = getIntent().getExtras();
        if (bundle == null || bundle.getSerializable(Commons.ARGS.DATA) == null || !(bundle.getSerializable(Commons.ARGS.DATA) instanceof Query)) {
            onBackPressed();
            return;
        }

        query = (Query) bundle.getSerializable(Commons.ARGS.DATA);

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

        if (query.getThumbnail(Query.ThumbnailQuality.MaxRes) != null)
            LoadThumbnail();

        if (query.thumbmap != null) Image.setImageURI(query.thumbmap);

        if (query.title != null) {
            Display.setText(query.title);

            String[] result = extractTitleandArtist(query.title, query.artist);
            Title.setText(result[0]);
            Artist.setText(result[1]);
            query.artist = result[1];
        }

        if (length < 0 || sparseArray == null) {
            new YouTubeExtractor(this)
                    .setCallbacks(this)
                    .setTimeout(Commons.Pref.timeout)
                    .extractOnExecutor(AsyncTask.THREAD_POOL_EXECUTOR, "https://www.youtube.com/watch?v=" + query.youtubeID, true, false);

            retrieveDialog = new Dialog(this);
            retrieveDialog.setTitle("Retrieving...");
            retrieveDialog.setCanceledOnTouchOutside(false);
            retrieveDialog.setContentView(R.layout.dialog_retrieving);
            retrieveDialog.findViewById(R.id.negative).setOnClickListener(v -> {
                retrieveDialog.dismiss();
                onBackPressed();
            });
            retrieveDialog.show();
        } else onExtractionComplete(sparseArray, videoMeta);

//        FormatGroup.setAdapter(new ArrayAdapter<>(DownloadActivity.this, android.R.layout.simple_spinner_dropdown_item, formats));
        String[] farr = getResources().getStringArray(R.array.formats);
        int index = -1;
        for (int i = 0; i < farr.length; i++) {
            if (farr[i].toUpperCase().trim().equals(Commons.Pref.format.toUpperCase())) {
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
            chip.setChipStrokeWidth(1f);
//            chip.setChipBackgroundColorResource(R.color.ClearGray);
            chip.setOnClickListener(v -> ((Chip) v).setChecked(true));
            Formats[i] = chip;
            FormatGroup.addView(chip, i, new ChipGroup.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        }
        FormatGroup.check(Formats[index].getId());

        if (query.artist != null) Artist.setText(query.artist);

        if (query.year > 0) Year.setText(String.valueOf(query.year));

        Genres.addChipTerminator(',', ChipTerminatorHandler.BEHAVIOR_CHIPIFY_ALL);
        Genres.addChipTerminator(';', ChipTerminatorHandler.BEHAVIOR_CHIPIFY_ALL);
        Genres.addChipTerminator('\n', ChipTerminatorHandler.BEHAVIOR_CHIPIFY_ALL);

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
            Intent appIntent = new Intent(Intent.ACTION_VIEW, Uri.parse("vnd.youtube:" + query.youtubeID));
            Intent webIntent = new Intent(Intent.ACTION_VIEW,
                    Uri.parse("http://www.youtube.com/watch?v=" + query.youtubeID));
            try {
                startActivity(appIntent);
            } catch (ActivityNotFoundException ex) {
                startActivity(webIntent);
            }
        });

        Normalize.setChecked(Commons.Pref.normalize);
        NormalizeHelp.setOnClickListener(v -> new AlertDialog.Builder(DownloadActivity.this)
                .setMessage("Some audio/videos may have a quieter audio than other audios. This is because sometimes an audio/video file does not use the full volume range available and thus resulting in its audio being very quiet. Normalization will increase the audio's volume in such that it will utilize the whole available volume range though it may take more time.")
                .setPositiveButton("OK", (dialog, which) -> dialog.dismiss()).show());
    }

    @Override
    public void onExtractionComplete(SparseArray<YouTubeExtractor.YtFile> data, YouTubeExtractor.VideoMeta videoMeta) {
        sparseArray = data;
        DownloadActivity.videoMeta = videoMeta;

        if (videoMeta.getVideoLength() <= 0) {
            onBackPressed();
            Toast.makeText(DownloadActivity.this, "Invalid video ID", Toast.LENGTH_LONG).show();
            return;
        }

        if (query.title == null) {
            query.title = StringEscapeUtils.unescapeXml(videoMeta.getTitle());
            Display.setText(query.title);

            String[] result = extractTitleandArtist(query.title, query.artist);
            Title.setText(result[0]);
            Artist.setText(result[1]);

            if (query.artist == null) query.artist = result[1];
        } else if (query.artist == null) {
            query.artist = videoMeta.getAuthor();
            Artist.setText(query.artist);
        }

        boolean thumbnail = false;
        if (query.thumbMax == null) {
            query.thumbMax = videoMeta.getMaxResImageUrl();
            thumbnail = true;
        }
        if (query.thumbHigh == null) {
            query.thumbHigh = videoMeta.getHqImageUrl();
            thumbnail = true;
        }
        if (query.thumbMedium == null) {
            query.thumbMedium = videoMeta.getMqImageUrl();
            thumbnail = true;
        }
        if (query.thumbDefault == null) {
            query.thumbDefault = videoMeta.getThumbUrl();
            thumbnail = true;
        }
        if (query.thumbStandard == null) {
            query.thumbStandard = videoMeta.getSdImageUrl();
            thumbnail = true;
        }

        if (thumbnail) LoadThumbnail();

        sparseArray = data;

        int maxbit = 64;
        start = null;
        end = null;
        length = (int) videoMeta.getVideoLength();

        retrieveDialog.dismiss();

        RefreshTimeRange();

        if (data == null) return;
        for (int i = 0; i < data.size(); i++)
            maxbit = Math.max(data.get(data.keyAt(i)).getFormat().getAudioBitrate(), maxbit);

        int cnt = 0;
        for (int i = 0; i < Commons.Pref.BITRATES.length && Commons.Pref.BITRATES[i] <= maxbit; i++) cnt++;

        Bitrates = new Chip[cnt];
        String[] barr = getResources().getStringArray(R.array.bitrates);

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

    @Override
    public void onTimeout() {
        if (retrieveDialog != null) retrieveDialog.dismiss();

        new AlertDialog.Builder(DownloadActivity.this)
                .setTitle("Timeout")
                .setMessage("It has taken longer than expected to retrieve the data. Try again later.")
                .setPositiveButton("OK", (dialogInterface, i) -> {
                    dialogInterface.dismiss();
                    onBackPressed();
                }).show();
    }

    public void Download() {
        if (query == null || query.youtubeID == null) return;

        boolean invalid = false;

        if (Title.getText() == null || Title.getText().toString().isEmpty()) {
            Title.setError("There must be a title");
            invalid = true;
        } else query.title = Title.getText().toString();

        if (!(Artist.getText() == null || Artist.getText().toString().isEmpty())) query.artist = Artist.getText().toString();
        if (!(Album.getText() == null || Album.getText().toString().isEmpty())) query.album = Album.getText().toString();

        short bitrate = Commons.Pref.bitrate;

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
                query.track = Integer.parseInt(Track.getText().toString());
            } catch (NumberFormatException ignore) {
                Track.setError("Invalid Track Number");
                invalid = true;
            }
        }

        if (!(Year.getText() == null || Year.getText().toString().isEmpty())) {
            try {
                query.year = Integer.parseInt(Year.getText().toString());
            } catch (NumberFormatException ignore) {
                Snackbar.make(findViewById(R.id.main), "Invalid Year Number", Snackbar.LENGTH_LONG);
                Year.setText("Invalid Year Number");
                invalid = true;
            }
        }

        if (invalid) return;

        String format = ((Chip) findViewById(FormatGroup.getCheckedChipId())).getText().toString().toLowerCase().trim();

        Download args = new Download(
                query,
                bitrate,
                format,
                sparseArray,
                start,
                end != null && end == Math.floor(length) ? null : end,
                Normalize.isChecked(),
                length
        );

        args.genres = Genres.getChipAndTokenValues().toArray(new String[0]);
        String name = args.makeFile() + '.' + args.format;

        if (Commons.Pref.overwrite == Commons.Pref.OverwriteMode.PROMPT && new File(Commons.Directories.MUSIC, name).exists()) {
            new AlertDialog.Builder(this)
                    .setTitle("Conflicting File Names")
                    .setMessage("There is already a file called " + name + " in the Music folder. A suffix such as '(1)' can be appended to the file name to avoid conflicts.")
                    .setPositiveButton("Overwrite", (dialogInterface, i) -> {
                        args.overwrite = true;
                        InitDownload(args);
                    })
                    .setNeutralButton("Cancel", null)
                    .setNegativeButton("Append Suffix", (dialogInterface, i) -> {
                        args.overwrite = false;
                        InitDownload(args);
                    })
                    .show();
        } else {
            args.overwrite = false;
            InitDownload(args);
        }
    }

    private void InitDownload(Download args) {
//        if (Commons.downloadReceiver == null) {
//            Commons.downloadReceiver = new DownloadReceiver(this, true);
//            LocalBroadcastManager.getInstance(this).registerReceiver(Commons.downloadReceiver, new IntentFilter(DownloadService.TAG));
//        }
        LocalBroadcastManager.getInstance(this).registerReceiver(new DownloadReceiver(this, true), new IntentFilter(DownloadService.TAG));

        Intent service = new Intent(this, DownloadService.class);
        service.putExtra(Commons.ARGS.DATA, args);
        startService(service);

        Intent intent = new Intent(this, MainActivity.class);
        intent.putExtra(Commons.ARGS.INDEX, 0);
        intent.setFlags(Intent.FLAG_ACTIVITY_CLEAR_TASK | Intent.FLAG_ACTIVITY_NEW_TASK);
        startActivity(intent);
        overridePendingTransition(R.anim.slide_left_in, R.anim.slide_left_out);
        Toast.makeText(getApplicationContext(), "Download started", Toast.LENGTH_LONG).show();
    }

    // String[] format is [title, artist]
    private String[] extractTitleandArtist(String title, String uploader) {
        if (title == null) return null;

        if (title.contains(" - ")) {
            String[] split = title.replaceAll("(?i)[(\\[}](official)?\\s*(?:audio|video|(?:music|lyrics?)\\s+video)[)\\]}]", "").replaceAll("(?i)\\(?lyrics\\)", "").trim().split("-");
            split[0] = split[0].trim();
            split[1] = split[1].trim();
            if (split[0].matches("(?i)(?:.*\\s+|\\s*)(?:ft\\.?|feat\\.?|featuring)\\s++.+") || Commons.Pref.artistfirst)
                return new String[]{split[1], split[0]};
            else //if (split[1].matches("(?i)(?:.*\\s+|\\s*)(?:ft\\.?|feat\\.?|featuring)\\s++.+"))
                return split;
        } else return new String[]{title, uploader.replace("VEVO", "")};
    }

    private void LoadThumbnail() {
        Picasso.get()
                .load(query.getThumbnail(Commons.Pref.downloadImage))
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
    protected void onDestroy() {
        if (retrieveDialog != null) retrieveDialog.dismiss();
        length = -1;
        sparseArray = null;
        videoMeta = null;
        super.onDestroy();
    }
}
