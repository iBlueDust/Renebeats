package com.yearzero.renebeats.download;

import android.content.ActivityNotFoundException;
import android.content.ComponentName;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.ServiceConnection;
import android.net.Uri;
import android.os.Bundle;
import android.os.IBinder;
import android.util.Log;
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
import com.squareup.picasso.Picasso;
import com.yearzero.renebeats.Directories;
import com.yearzero.renebeats.InternalArgs;
import com.yearzero.renebeats.R;
import com.yearzero.renebeats.notification.DownloadReceiver;
import com.yearzero.renebeats.preferences.Preferences;
import com.yearzero.renebeats.preferences.enums.OverwriteMode;

import java.io.File;
import java.util.ArrayList;
import java.util.Locale;

import jp.wasabeef.picasso.transformations.RoundedCornersTransformation;

public class DownloadActivity extends AppCompatActivity implements ServiceConnection {
    private static final String TAG = "DownloadActivity";

    //TODO: File name appending suggestion (such as ' (1)') should also check running downloads to avoid request.getDownloadId() conflict (Accomplished by setting download concurrency to 1)
    //TODO: Proper thumbnail placement when landscape/large screen (TEST PENDING)

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

    private Query query;

    private DownloadService service;

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
        Normalize = findViewById(R.id.exception);
        NormalizeHelp = findViewById(R.id.normalize_help);

        FormatGroup = findViewById(R.id.format_group);
        BitrateGroup = findViewById(R.id.bitrate_group);

        Home.setOnClickListener(v -> onBackPressed());
        Download.setOnClickListener(v -> Download());

        if (query.getThumbnail(Query.ThumbnailQuality.MaxRes) != null) LoadThumbnail();
        if (!query.getTitle().isEmpty()) {
            Display.setText(query.getTitle());
            UseGuesserMode();
        } else Artist.setText(query.getArtist());
        if (!query.getAlbum().isEmpty()) Album.setText(query.getAlbum());
        if (query.getYear() > 0) Year.setText(String.valueOf(query.getYear()));

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

        Genres.setText(query.getGenres());

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

        String[] barr = getResources().getStringArray(R.array.bitrates);
        int lastChipId = -1;
        for (int i = 0; i < barr.length; i++) {
            Chip chip = new Chip(DownloadActivity.this, null, R.style.Widget_MaterialComponents_Chip_Choice);
            chip.setText(String.format("%s %s", barr[i], getString(R.string.kbps)));
            chip.setCheckable(true);
            chip.setChipStrokeColorResource(R.color.Accent);
            chip.setChipStrokeWidth(1f);
            chip.setOnClickListener(v -> ((Chip) v).setChecked(true));

            lastChipId = chip.getId();
            BitrateGroup.addView(chip, i, new ChipGroup.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        }
        if (lastChipId >= 0) BitrateGroup.check(lastChipId);
    }

//    public void onExtractionComplete(YouTubeExtractor.YtFile[] data, YouTubeExtractor.VideoMeta videoMeta) {
//        sparseArray = data;
//        this.videoMeta = videoMeta;
//
//        if (videoMeta.getVideoLength() <= 0) {
//            onBackPressed();
//            Toast.makeText(DownloadActivity.this, R.string.download_invalid_youtube_id, Toast.LENGTH_LONG).show();
//            return;
//        }
//
//        if (query.getTitle().isEmpty()) {
//            query.setTitle(StringEscapeUtils.unescapeXml(videoMeta.getTitle()));
//            Display.setText(query.getTitle());
//
//            UseGuesserMode();
//        } else if (query.getArtist().isEmpty()) {
//            query.setArtist(videoMeta.getAuthor());
//            Artist.setText(query.getArtist());
//        }
//
//        boolean thumbnail = false;
//        if (query.getThumbMax() == null) {
//            query.setThumbMax(videoMeta.getMaxResImageUrl());
//            thumbnail = true;
//        }
//        if (query.getThumbHigh() == null) {
//            query.setThumbHigh(videoMeta.getHqImageUrl());
//            thumbnail = true;
//        }
//        if (query.getThumbMedium() == null) {
//            query.setThumbMedium(videoMeta.getMqImageUrl());
//            thumbnail = true;
//        }
//        if (query.getThumbDefault() == null) {
//            query.setThumbDefault(videoMeta.getThumbUrl());
//            thumbnail = true;
//        }
//        if (query.getThumbStandard() == null) {
//            query.setThumbStandard(videoMeta.getSdImageUrl());
//            thumbnail = true;
//        }
//
//        if (thumbnail) LoadThumbnail();
//
//        int maxbit = Integer.MIN_VALUE;
//        length = (int) videoMeta.getVideoLength();
//
//        if (retrieveDialog != null && retrieveDialog.isShowing()) retrieveDialog.dismiss();
//
//        RefreshTimeRange();
//
//        if (data == null) return;
//        for (YouTubeExtractor.YtFile datum : data)
//            maxbit = Math.max(datum.getFormat().getAudioBitrate(), maxbit);
//
//    }

    private void UseGuesserMode() {
        switch (Preferences.getGuesser_mode()) {
            case TITLE_UPLOADER:
                Artist.setText(query.getArtist());
            case TITLE_ONLY:
                Title.setText(query.getTitle());
                break;
            case PREDICT:
                String[] result = extractTitleAndArtist(query.getTitle(), query.getArtist());
                if (result != null) {
                    Title.setText(result[0]);
                    Artist.setText(result[1]);
                }
                break;
        }
    }

    // Create download object
    public Download CreateDownload() {
        if (query == null || query.getYoutubeID() == null) return null;

        boolean invalid = false;

        if (Title.getText() == null || Title.getText().toString().isEmpty()) {
            Title.setError(getString(R.string.download_invalid_title));
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

        return new Download(
                query,
                bitrate,
                format,
                Normalize.isChecked()
        );
    }

    // Validate fields
    private void Download() {
        Download args = CreateDownload();
        if (args == null) {
            Snackbar.make(findViewById(R.id.main), R.string.download_invalid, Snackbar.LENGTH_LONG)
                    .setAction(R.string.retry, v -> Download())
                    .show();
            return;
        }
        String name = args.getFilenameWithExt();
        args.setGenres(Genres.getText() == null ? "" : Genres.getText().toString()); //getChipAndTokenValues().toArray(new String[0]);

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
        } else {
            args.setOverwrite(false);
            ServiceCheck(args);
        }
    }

    // Check if the download is already running
    private void ServiceCheck(Download args) {
        if (service == null) {
            InitDownload(args);
            bindService(new Intent(this, DownloadService.class), this, 0);
            return;
        }
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
        } else InitDownload(args);
    }

    // Send Intent to DownloadService to start the download
    private void InitDownload(Download args) {
        // Register DownloadReceiver to enable notifications
        LocalBroadcastManager.getInstance(this).registerReceiver(new DownloadReceiver(this), new IntentFilter(DownloadService.TAG));

        Intent service = new Intent(this, DownloadService.class);
        service.putExtra(InternalArgs.DATA, args);
        startService(service);

        // Return to MainActivity
        Intent intent = new Intent(this, MainActivity.class);
        intent.putExtra(InternalArgs.INDEX, 0);
        intent.setFlags(Intent.FLAG_ACTIVITY_CLEAR_TASK | Intent.FLAG_ACTIVITY_NEW_TASK);
        startActivity(intent);
        overridePendingTransition(R.anim.slide_left_in, R.anim.slide_left_out);
        Toast.makeText(getApplicationContext(), getString(R.string.download_start), Toast.LENGTH_LONG).show();
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
    public void onServiceConnected(ComponentName name, IBinder binder) {
        service = ((DownloadService.LocalBinder) binder).getService();
    }

    @Override
    public void onServiceDisconnected(ComponentName name) {
        service = null;
    }
}
