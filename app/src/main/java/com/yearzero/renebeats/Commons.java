package com.yearzero.renebeats;

import android.annotation.SuppressLint;
import android.app.Application;
import android.content.SharedPreferences;
import android.os.Build;
import android.os.Environment;
import android.util.Log;

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
import java.io.Serializable;
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
    public static final String YT_API_KEY = "AIzaSyCwdpSqwQDMXfZdLKldtuZr9pe7y08pgok";
    public static final String[] suffix = {"B", "KB", "MB", "GB"};
    private static final String TAG = "Commons";
    static final String INTL_DATE_FORMAT = "yyyy-mm-ddThh:mm:sszzz";

    public static final int PERM_REQUEST = 0x24D1;

    // SERIALIZABLE UID CLASS CODES
    // Query    - 0E10
    // Download - D01D
    // Status   - 55A5


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
        public enum OverwriteMode implements Serializable {
            PROMPT(0),
            APPEND(1),
            OVERWRITE(2);

            // Appcode (EA50) - Class code (OverwriteMode: 0ED9) - Gradle version (3) - Iteration
            private static final long serialVersionUID = 0x0ED9_D01D_0003_0000L;

            public static int versionID = 0;
            private int state;

            OverwriteMode(int state) {
                this.state = state;
            }

            public int getValue() {
                return state;
            }
        }

        static SharedPreferences SharedPref;
        static SharedPreferences.Editor Editor;

        private static final String location_sdcard = "sdcard";
        private static final String location_normalize = "normalize";
        private static final String location_bitrate = "bitrate";
        private static final String location_format = "format";
        private static final String location_concurrency = "concurrency";
        private static final String location_location = "location";
        private static final String location_query_amount = "query amount";
        private static final String location_timeout = "master timeout";
        private static final String location_overwrite = "overwrite mode";

        private static final String location_overwrite_version = "overwrite mode version";

        public static short notif_done_limit = 3;

        public static OverwriteMode overwrite = OverwriteMode.PROMPT;
        public static int timeout = 30_000;
        public static short query_amount = 25;
        public static short[] BITRATES = {64, 96, 128, 192, 256, 320};
        public static short bitrate = 128;
        public static short concurrency = 3;
        public static boolean sdcard = Directories.isExternalStorageAvailable();
        public static boolean normalize = true;
        public static String format = "mp3";
        public static File location = new File(Environment.getExternalStorageDirectory() + "/Music");

        public static Query.ThumbnailQuality queryImage = Query.ThumbnailQuality.High;
        public static Query.ThumbnailQuality downloadImage = Query.ThumbnailQuality.High;

        public static void Save() {
            Editor.putInt(location_bitrate, bitrate);
            Editor.putString(location_format, format);
            Editor.putBoolean(location_normalize, normalize);
            Editor.putBoolean(location_sdcard, sdcard);
            Editor.putInt(location_concurrency, concurrency);
            Editor.putString(location_location, location.getAbsolutePath());
            Editor.putInt(location_query_amount, query_amount);
            Editor.putInt(location_timeout, timeout);
            Editor.putInt(location_overwrite, overwrite.getValue());
            Editor.putInt(location_overwrite_version, OverwriteMode.versionID);
            Editor.apply();
        }

        public static void Load() {
            bitrate = (short) SharedPref.getInt(location_bitrate, bitrate);
            format = SharedPref.getString(location_format, format);
            sdcard = Directories.isExternalStorageAvailable() && SharedPref.getBoolean(location_sdcard, sdcard);
            normalize = SharedPref.getBoolean(location_normalize, normalize);
            location = new File(SharedPref.getString(location_location, location.getAbsolutePath()));
            concurrency = (short) SharedPref.getInt(location_concurrency, concurrency);
            query_amount = (short) SharedPref.getInt(location_query_amount, query_amount);
            timeout = SharedPref.getInt(location_timeout, timeout);

            if (SharedPref.getInt(location_overwrite_version, -1) == OverwriteMode.versionID)
                overwrite = OverwriteMode.values()[SharedPref.getInt(location_timeout, overwrite.getValue())];
            else {
                Editor.remove(location_overwrite);
                Editor.remove(location_overwrite_version);
                Editor.apply();
            }
        }
    }

    public static class ARGS {
        public static final int SUCCESS = 10;
        public static final int FAILED = 0;
        public static final int PROGRESS = 1;
        public static final int CANCELLED = 9;

        public static final String REMAINING = "remaining";
        public static final String REQUEST = "request";
        public static final String URL = "url";
        public static final String DATA = "data";
        public static final String RESULT = "result";
        public static final String TOTAL = "total";
        public static final String CURRENT = "current";
        public static final String INDETERMINATE = "indeterminate";
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
            File[] files = getExternalCacheDirs();
            Directories.MAIN = files[Pref.sdcard && files.length > 1 && files[1] != null ? 1 : 0];
            Directories.BIN = new File(Directories.MAIN, "/bin/");
            Directories.DOWNLOADS = new File(Directories.MAIN, "/queue.dat");
        }

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

    public static class Directories {
        static File MAIN = new File(Environment.getExternalStorageDirectory().getAbsolutePath(), "/Year Zero/Renebeats/");
        static File BIN = new File(MAIN, "/bin/");
        static File LOGS = new File(MAIN, "/logs/");
        static File DOWNLOADS = new File(MAIN, "/queue.dat");
        public static File MUSIC = new File(Environment.getExternalStorageDirectory(), "/Music/");

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

    static class Notif {
        static final String DOWNLOAD_PROGRESS = "com.yearzero.renebeats/download/progress";
        static final String DOWNLOAD_COMPLETE = "com.yearzero.renebeats/download/complete";
        static final int DOWNLOAD_BASE_ID = 0xDB00_0000;
    }

//    public static class DownloadQueuePackage implements Serializable {
//        private static final long serialVersionUID = 200L;
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
