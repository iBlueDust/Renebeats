package com.yearzero.renebeats.Activities;

import android.annotation.SuppressLint;
import android.app.Dialog;
import android.content.Intent;
import android.content.IntentFilter;
import android.os.AsyncTask;
import android.os.Bundle;
import android.util.Log;
import android.util.SparseArray;
import android.view.Menu;
import android.view.MenuItem;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.Toast;

import com.google.android.material.snackbar.Snackbar;
import com.google.android.material.textfield.TextInputEditText;
import com.makeramen.roundedimageview.RoundedTransformationBuilder;
import com.squareup.picasso.Picasso;
import com.yearzero.renebeats.Commons;
import com.yearzero.renebeats.Download;
import com.yearzero.renebeats.DownloadReceiver;
import com.yearzero.renebeats.DownloadService;
import com.yearzero.renebeats.DurationPicker;
import com.yearzero.renebeats.Query;
import com.yearzero.renebeats.R;
import com.yearzero.renebeats.YouTubeExtractor;

import java.util.Arrays;
import java.util.List;

import androidx.appcompat.app.ActionBar;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.localbroadcastmanager.content.LocalBroadcastManager;
import mabbas007.tagsedittext.TagsEditText;

public class DownloadActivity extends AppCompatActivity {
    private static final String TAG = "DownloadActivity";

    private ImageView Image;
    private TextView Display;
    private Button Start, End; //, ImageEdit, Location, Autofill;
    private Spinner Format, Bitrate;
    private TextInputEditText Title, Artist, Album, Track, Year;
    private TagsEditText Genres;
    private CheckBox Normalize;
    private ImageButton NormalizeHelp;

    private Dialog retrieveDialog;

    private Query query;
    private SparseArray<YouTubeExtractor.YtFile> sparseArray;

    private Integer start, end;
    private int length = -1;

    @SuppressLint({"StaticFieldLeak", "WrongViewCast"})
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_download);
        ActionBar actionBar = getSupportActionBar();
        if (actionBar != null) actionBar.setDisplayShowTitleEnabled(false);

        Bundle bundle = getIntent().getExtras();
        if (bundle == null || bundle.getSerializable(Commons.ARGS.DATA) == null || !(bundle.getSerializable(Commons.ARGS.DATA) instanceof Query)) {
            onBackPressed();
            return;
        }

        query = (Query) bundle.getSerializable(Commons.ARGS.DATA);

        Image = findViewById(R.id.image);
        Display = findViewById(R.id.display);
        Format = findViewById(R.id.assigned);
        Bitrate = findViewById(R.id.bitrate);
        Title = findViewById(R.id.title);
        Artist = findViewById(R.id.artist);
        Album = findViewById(R.id.album);
        Year = findViewById(R.id.year);
        Track = findViewById(R.id.track);
        Genres = findViewById(R.id.genres);
        Start = findViewById(R.id.download);
        End = findViewById(R.id.conversion);
        Normalize = findViewById(R.id.exception);
        NormalizeHelp = findViewById(R.id.normalize_help);

        if (query.getThumbnail(Query.ThumbnailQuality.MaxRes) != null)
            Picasso.get()
                    .load(query.getThumbnail(Commons.Pref.downloadImage))
                    .transform(new RoundedTransformationBuilder()
                            .cornerRadiusDp(10)
                            .oval(false)
                            .build())
                    .placeholder(R.color.SecondaryDark)
                    .into(Image);

        if (query.thumbmap != null) Image.setImageURI(query.thumbmap);

        if (query.title != null) Display.setText(query.title);

        if (length < 0 || sparseArray == null) {
            new YouTubeExtractor(this) {
                @Override
                protected void onExtractionComplete(SparseArray<YouTubeExtractor.YtFile> data, YouTubeExtractor.VideoMeta videoMeta) {
                    int maxbit = 64;

                    sparseArray = data;

                    start = null;
                    end = null;
                    length = (int) videoMeta.getVideoLength();

                    retrieveDialog.dismiss();

                    RefreshTimeRange();

                    if (data == null) return;
                    for (int i = 0; i < data.size(); i++)
                        maxbit = Math.max(data.get(data.keyAt(i)).getFormat().getAudioBitrate(), maxbit);

                    int i = 0;
                    while (i < Commons.Pref.BITRATES.length && Commons.Pref.BITRATES[i] <= maxbit)
                        i++;

                    List<String> bitrates = Arrays.asList(getResources().getStringArray(R.array.bitrates));

                    Bitrate.setAdapter(new ArrayAdapter<>(DownloadActivity.this, android.R.layout.simple_spinner_dropdown_item, bitrates.subList(0, i)));
                    if (Commons.Pref.bitrate < Commons.Pref.BITRATES[i])
                        Bitrate.setSelection(bitrates.indexOf(Commons.Pref.bitrate + "kbps"));
                }
            }.extractOnExecutor(AsyncTask.THREAD_POOL_EXECUTOR, "https://www.youtube.com/watch?v=" + query.id, true, false);

            retrieveDialog = new Dialog(this);
            retrieveDialog.setTitle("Retrieving...");
            retrieveDialog.setCanceledOnTouchOutside(false);
            retrieveDialog.setContentView(R.layout.dialog_retrieving);
            retrieveDialog.findViewById(R.id.info).setOnClickListener(v -> {
                retrieveDialog.dismiss();
                onBackPressed();
            });
            retrieveDialog.show();
        } else {
            RefreshTimeRange();

            int maxbit = 64;
            for (int i = 0; i < sparseArray.size(); i++)
                maxbit = Math.max(sparseArray.get(sparseArray.keyAt(i)).getFormat().getAudioBitrate(), maxbit);

            int i = 0;
            while (i < Commons.Pref.BITRATES.length && Commons.Pref.BITRATES[i] <= maxbit)
                i++;

            List<String> bitrates = Arrays.asList(getResources().getStringArray(R.array.bitrates));

            Bitrate.setAdapter(new ArrayAdapter<>(DownloadActivity.this, android.R.layout.simple_spinner_dropdown_item, bitrates.subList(0, i)));
            if (Commons.Pref.bitrate < Commons.Pref.BITRATES[i])
                Bitrate.setSelection(bitrates.indexOf(Commons.Pref.bitrate + "kbps"));
        }

        String[] formats = getResources().getStringArray(R.array.formats);
        Format.setAdapter(new ArrayAdapter<>(DownloadActivity.this, android.R.layout.simple_spinner_dropdown_item, formats));
        int index = Arrays.asList(formats).indexOf(Commons.Pref.format);
        Format.setSelection(index < 0 ? 0 : index);

        if (query.artist != null) Artist.setText(query.artist);

        if (query.year > 0) Year.setText(String.valueOf(query.year));

        Genres.setSeparator(",");

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

            dialog.setMaxTime(length);
            dialog.setTime(start == null ? 0 : start);
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

            dialog.setMaxTime(length);
            dialog.setTime(end == null ? length : end);
            dialog.setCallbacks(new DurationPicker.Callbacks() {
                @Override
                public String Validate(int time) {
                    if (start != null && time <= start)
                        return "End time cannot be equal or be less than the start time";
                    else if (end != null && end > length)
                        return "End time cannot exceed the video's length";
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

        Normalize.setChecked(Commons.Pref.normalize);
        NormalizeHelp.setOnClickListener(v -> new AlertDialog.Builder(DownloadActivity.this)
                .setMessage("Some audio/videos may have a quieter audio than other audios. This is because sometimes an audio/video file does not use the full volume range available and thus resulting in its audio being very quiet. Normalization will increase the audio's volume in such that it will utilize the whole available volume range though it may take more time.")
                .setPositiveButton("OK", (dialog, which) -> dialog.dismiss()).show());
    }

    public void Download() {
        if (query == null || query.id == null) return;

        if (!Title.getText().toString().isEmpty()) query.title = Title.getText().toString();
        if (!Artist.getText().toString().isEmpty()) query.artist = Artist.getText().toString();
        if (!Album.getText().toString().isEmpty()) query.album = Album.getText().toString();

        if (!Track.getText().toString().trim().isEmpty()) {
            try {
                query.track = Integer.parseInt(Track.getText().toString());
            } catch (NumberFormatException ignore) {
                Snackbar.make(findViewById(R.id.main), "Invalid Track Number", Snackbar.LENGTH_LONG);
                Track.setText("");
            }
        }

        if (!Year.getText().toString().isEmpty()) {
            try {
                query.year = Integer.parseInt(Year.getText().toString());
            } catch (NumberFormatException ignore) {
                Snackbar.make(findViewById(R.id.main), "Invalid Year Number", Snackbar.LENGTH_LONG);
                Year.setText("");
            }
        }

        short bitrate = Commons.Pref.bitrate;
        short year = -1;
        int track = 0;

        try {
            bitrate = Short.parseShort(((String) Bitrate.getSelectedItem()).replaceAll("\\D", ""));
        } catch (NumberFormatException e) {
            e.printStackTrace();
        }
        try {
            year = Short.parseShort(Year.getText().toString().replaceAll("\\D", ""));
        } catch (NumberFormatException e) {
            e.printStackTrace();
        }
        try {
            track = Integer.parseInt(Year.getText().toString().replaceAll("\\D", ""));
        } catch (NumberFormatException e) {
            e.printStackTrace();
        }

        if (track > 0) query.track = track;
        if (year > 0) query.year = year;

        int index = Format.getSelectedItemPosition();
        String[] formats = getResources().getStringArray(R.array.formats);
        String format = index < 0 || index >= formats.length ? Commons.Pref.format : formats[index];

        Download args = new Download(
                query,
                bitrate,
                format,
                sparseArray,
                start,
                end != null && end == Math.floor(length) ? null : end,
                Normalize.isChecked()
        );

        if (Commons.downloadReceiver == null) {
            Commons.downloadReceiver = new DownloadReceiver(this, true);
            LocalBroadcastManager.getInstance(this).registerReceiver(Commons.downloadReceiver, new IntentFilter(DownloadService.TAG));
        }

        Intent service = new Intent(this, DownloadService.class);
        service.putExtra(Commons.ARGS.DATA, args);
        startService(service);

        Intent intent = new Intent(this, NewMainActivity.class);
        intent.putExtra("page", 0);
        intent.setFlags(Intent.FLAG_ACTIVITY_CLEAR_TASK | Intent.FLAG_ACTIVITY_NEW_TASK);
        startActivity(intent);
        overridePendingTransition(R.anim.slide_left_in, R.anim.slide_left_out);
        Toast.makeText(getApplicationContext(), "Download started", Toast.LENGTH_LONG).show();
    }

    private void RefreshTimeRange() {
        StringBuilder start = new StringBuilder();
        StringBuilder end = new StringBuilder();

        short sh = this.start == null ? 0 : (short) Math.floor(this.start / 3600);
        short sm = this.start == null ? 0 : (short) (Math.floor(this.start / 60) % 60);
        short ss = this.start == null ? 0 : (short) (this.start % 60);

        short eh = (short) Math.floor((this.end == null ? length : this.end) / 3600);
        short em = (short) (Math.floor((this.end == null ? length : this.end) / 60) % 60);
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

//    @Override
//    public void onClick(View v) {
//        CropImage.activity()
//                .setGuidelines(CropImageView.Guidelines.ON)
//                .setCropShape(CropImageView.CropShape.OVAL)
//                .setAspectRatio(1, 1)
//                .start(this);
//    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        getMenuInflater().inflate(R.menu.menu_download, menu);
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        switch (item.getItemId()) {
            case android.R.id.home:
                onBackPressed();
                return true;
            case R.id.menu_download:
                Download();
                return true;
            default:
                return super.onOptionsItemSelected(item);
        }
    }

//    @Override
//    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
//        if (requestCode == CropImage.CROP_IMAGE_ACTIVITY_REQUEST_CODE) {
//            CropImage.ActivityResult result = CropImage.getActivityResult(data);
//            if (resultCode == RESULT_OK) {
//                Picasso.get()
//                        .load(result.getUri())
//                        .transform(new RoundedTransformationBuilder()
//                                .cornerRadiusDp(10)
//                                .scaleType(ImageView.ScaleType.CENTER_CROP)
//                                .oval(false)
//                                .build())
//                        .into(Image);
//            } else if (resultCode == CropImage.CROP_IMAGE_ACTIVITY_RESULT_ERROR_CODE) {
//                result.getError().printStackTrace();
//            }
//        } else super.onActivityResult(requestCode, resultCode, data);
//    }
}
