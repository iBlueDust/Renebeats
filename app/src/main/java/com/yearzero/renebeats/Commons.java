package com.yearzero.renebeats;

import android.annotation.SuppressLint;
import android.app.Application;
import android.content.SharedPreferences;
import android.os.Build;
import android.os.Environment;
import android.util.Log;

import com.downloader.PRDownloader;
import com.downloader.PRDownloaderConfig;
import com.google.gson.ExclusionStrategy;
import com.google.gson.FieldAttributes;
import com.google.gson.GsonBuilder;
import com.tonyodev.fetch2.Fetch;
import com.tonyodev.fetch2.FetchConfiguration;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.io.PrintStream;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.io.Writer;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import cafe.adriel.androidaudioconverter.callback.ILoadCallback;

public class Commons extends Application {

    static final String SHARED_PREF_KEY = "com.yearzero.renebeats";
    public static final String YT_API_KEY = "Still ain't sharing this with ya! :)";
    public static final String[] suffix = {"B", "KB", "MB", "GB"};
    private static final String TAG = "Commons";
    static final String INTL_DATE_FORMAT = "yyyy-mm-ddThh:mm:sszzz";

    public static final int PERM_REQUEST = 0x24D1;

    public static DownloadReceiver downloadReceiver;
    public static Fetch fetch;

    public static boolean LogException(@Nullable Download download, @NonNull Exception ex) {
        String date = new SimpleDateFormat("yyyy-MM-dd-HH-mm", Locale.ENGLISH).format(new Date());

        int i = 0;
        while (new File(Directories.LOGS, date + " (" + i + ").txt").exists()) i++;
        File file = new File(Directories.LOGS, date + " (" + i + ").txt");

        try {
            Writer writer = new StringWriter();
            ex.printStackTrace(new PrintWriter(writer));

            if (download != null) {
                writer.append("\n\nDOWNLOAD DATA\n===============================");
                writer.append(new GsonBuilder().setPrettyPrinting().setExclusionStrategies(new ExclusionStrategy() {
                    @Override
                    public boolean shouldSkipField(FieldAttributes f) {
                        return f.getDeclaringClass() == Download.class && f.getName().equals("exception");
                    }

                    @Override
                    public boolean shouldSkipClass(Class<?> clazz) {
                        return false;
                    }
                }).create().toJson(download));
            }

            file.createNewFile();
            PrintStream stream = new PrintStream(new FileOutputStream(file));
            stream.print(writer.toString());
        } catch (IOException e) {
            e.printStackTrace();
            return false;
        }
        return true;
    }

    public static class Pref {
        static SharedPreferences SharedPref;
        static SharedPreferences.Editor Editor;

        public static final String location_sdcard = "sdcard";
        public static final String location_normalize = "normalize";
        private static final String location_bitrate = "bitrate";
        private static final String location_format = "format";
        private static final String location_concurrency = "concurrency";
        private static final String location_location = "location";

        public static short notif_done_limit = 3;

        public static short[] BITRATES = {64, 96, 128, 192, 256, 320};
        public static short bitrate = 128;
        public static short concurrency = 1;
        public static boolean sdcard = Directories.isExternalStorageAvailable();
        public static boolean normalize = true;
        public static String format = "mp3";
        public static File location = new File(Environment.getExternalStorageDirectory() + "/Music");

        public static Query.ThumbnailQuality queryImage = Query.ThumbnailQuality.Default;
        public static Query.ThumbnailQuality downloadImage = Query.ThumbnailQuality.Medium;

        public static void Save() {
            Editor.putInt(location_bitrate, bitrate);
            Editor.putString(location_format, format);
            Editor.putBoolean(location_normalize, normalize);
            Editor.putBoolean(location_sdcard, sdcard);
            Editor.putInt(location_concurrency, concurrency);
            Editor.putString(location_location, location.getAbsolutePath());
            Editor.apply();
        }

        public static void Load() {
            bitrate = (short) SharedPref.getInt(location_bitrate, bitrate);
            format = SharedPref.getString(location_format, format);
            sdcard = Directories.isExternalStorageAvailable() && SharedPref.getBoolean(location_sdcard, true);
            normalize = SharedPref.getBoolean(location_normalize, true);
            location = new File(SharedPref.getString(location_location, location.getAbsolutePath()));
            concurrency = (short) SharedPref.getInt(location_concurrency, 1);
        }

    }

    public static class ARGS {
        public static final int SUCCESS = 10;
        public static final int FAILED = 0;
        public static final int PROGRESS = 1;
        public static final int CANCELLED = 9;
        public static final int MISC = -10;

        public static final String REMAINING = "remaining";
        public static final String REQUEST = "request";
        public static final String URL = "url";
        public static final String DATA = "data";
        public static final String RESULT = "result";
        public static final String TOTAL = "total";
        public static final String CURRENT = "current";
        public static final String INDETERMINATE = "indeterminate";
//        public static final String STATUS = "status";
        public static final String EXCEPTION = "exception";
        public static final String INDEX = "index";
        public static final String LOAD = "load";
        public static final String PAUSED = "paused";

        public static final int DESTROY = 0xDDDFF;
        public static final int ERR_LOAD = 0xEE0001;

        public static final int FLAG_COMPLETED = 0x1;
        public static final int FLAG_RUNNING = 0x10;
        public static final int FLAG_QUEUE = 0x100;

        public static final String REQ_ID = "request/id";
        public static final String REQ_COMPLETED = "request/completed";
        public static final String REQ_RUNNING = "request/running";
        public static final String REQ_QUEUE = "request/queue";
    }

    @SuppressLint("CommitPrefEdits")
    @Override
    public void onCreate() {
        super.onCreate();

        Pref.SharedPref = getSharedPreferences(SHARED_PREF_KEY, MODE_PRIVATE);
        Pref.Editor = Pref.SharedPref.edit();
        Pref.Load();

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.KITKAT) {
            Directories.MAIN = getExternalCacheDirs()[Pref.sdcard && getExternalCacheDirs().length > 1 ? 1 : 0];
            Directories.BIN = new File(Directories.MAIN, "/bin/");
            Directories.DOWNLOADS = new File(Directories.MAIN, "/queue.dat");
        }

        PRDownloader.initialize(this,
                PRDownloaderConfig.newBuilder()
                        .setConnectTimeout(30_000)
                        .setReadTimeout(30_000)
                        .setDatabaseEnabled(true)
                        .build());

        fetch = Fetch.Impl.getInstance(new FetchConfiguration.Builder(this)
            .setDownloadConcurrentLimit(Pref.concurrency)
            .build());

        AndroidAudioConverter.load(this, new ILoadCallback() {
            @Override
            public void onSuccess() {
                Log.i(TAG, "AndroidAudioConverter successfully loaded");
            }

            @Override
            public void onFailure(Exception error) {
                Log.e(TAG, "AndroidAudioConverter failed to load");
            }
        });

        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.LOLLIPOP) {
            androidDefaultUEH = Thread.getDefaultUncaughtExceptionHandler();
            Thread.setDefaultUncaughtExceptionHandler(handler);
        }

    }

    protected static boolean SaveQueue(Download[] array) {
        if (!Directories.DOWNLOADS.getParentFile().exists())
            Directories.DOWNLOADS.getParentFile().mkdirs();
        try {
            if (!Directories.DOWNLOADS.exists()) Directories.DOWNLOADS.createNewFile();
            FileOutputStream file = new FileOutputStream(Directories.DOWNLOADS);
            ObjectOutputStream stream = new ObjectOutputStream(file);
//            stream.writeObject(new DownloadQueuePackage(queue, completed));
            stream.writeObject(array);
            stream.close();
            file.close();
            return true;
        } catch (IOException e) {
            e.printStackTrace();
        }
        return false;
    }

    protected static Download[] LoadQueue() {
        if (!Directories.DOWNLOADS.exists()) return null;
        try {
            FileInputStream file = new FileInputStream(Directories.DOWNLOADS);
            ObjectInputStream stream = new ObjectInputStream(file);
            Download[] data = (Download[]) stream.readObject();
            stream.close();
            file.close();

            return data;
        } catch (IOException | ClassNotFoundException e) {
            e.printStackTrace();
        }
        return null;
    }

    private Thread.UncaughtExceptionHandler androidDefaultUEH;
    private Thread.UncaughtExceptionHandler handler = new Thread.UncaughtExceptionHandler() {
        public void uncaughtException(Thread thread, Throwable ex) {
            Log.e("Commons", "Uncaught exception: ", ex);
            androidDefaultUEH.uncaughtException(thread, ex);
        }
    };

    static class Directories {
        static File MAIN = new File(Environment.getExternalStorageDirectory().getAbsolutePath(), "/Year Zero/Renebeats/");
        static File BIN = new File(MAIN, "/bin/");
        static File LOGS = new File(MAIN, "/logs/");
        static File DOWNLOADS = new File(MAIN, "/queue.dat");
        static File MUSIC = new File(Environment.getExternalStorageDirectory(), "/Music/");

        private static boolean isExternalStorageAvailable() {
            String state = Environment.getExternalStorageState();
            boolean mExternalStorageAvailable = false;
            boolean mExternalStorageWritable = false;

            if (Environment.MEDIA_MOUNTED.equals(state))
                mExternalStorageAvailable = mExternalStorageWritable = true;
            else if (Environment.MEDIA_MOUNTED_READ_ONLY.equals(state))
                mExternalStorageAvailable = true;

            return mExternalStorageAvailable && mExternalStorageWritable;
        }
    }

    public static class Notif {
        public static final String DOWNLOAD_PROGRESS = "com.yearzero.renebeats/download/progress";
        public static final String DOWNLOAD_COMPLETE = "com.yearzero.renebeats/download/complete";
    }

//    public static class DownloadQueuePackage implements Serializable {
//        private static final long serialVersionUID = 200;
//
//        Download[] queue, completed;
//
//        DownloadQueuePackage(Download[] queue, Download[] completed) {
//            this.queue = queue;
//            this.completed = completed;
//        }
//
//        public Download[] getQueue() {
//            return queue;
//        }
//
//        public Download[] getCompleted() {
//            return completed;
//        }
//    }
}
