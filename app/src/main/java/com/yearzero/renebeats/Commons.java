package com.yearzero.renebeats;

import android.annotation.SuppressLint;
import android.app.Application;
import android.content.SharedPreferences;
import android.os.AsyncTask;
import android.os.Build;
import android.os.Environment;
import android.util.Log;
import android.util.SparseArray;

import com.esotericsoftware.kryo.Kryo;
import com.esotericsoftware.kryo.KryoException;
import com.esotericsoftware.kryo.io.Input;
import com.esotericsoftware.kryo.io.Output;
import com.google.gson.ExclusionStrategy;
import com.google.gson.FieldAttributes;
import com.google.gson.GsonBuilder;
import com.tonyodev.fetch2.Fetch;
import com.tonyodev.fetch2.FetchConfiguration;
import com.yearzero.renebeats.classes.Download;
import com.yearzero.renebeats.classes.HistoryLog;
import com.yearzero.renebeats.classes.Query;
import com.yearzero.renebeats.classes.Status;

import org.apache.commons.io.FileUtils;
import org.apache.commons.io.FilenameUtils;

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
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Calendar;
import java.util.Date;
import java.util.List;
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
    // Query      - 0E10
    // Download   - D01D
    // Status     - 55A5
    // HistoryLog - 415C


//    public static DownloadReceiver downloadReceiver;
    public static Fetch fetch;

    public static boolean LogException(Throwable ex) {
        if (ex == null) return false;
        String date = new SimpleDateFormat("yyyy-MM-dd-HH-mm", Locale.ENGLISH).format(new Date());

        int i = 0;
        while (new File(Directories.LOGS, date + " (" + i + ").txt").exists()) i++;
        File file = new File(Directories.LOGS, date + " (" + i + ").txt");

        try {
            if (file.exists() || file.createNewFile()) {
                PrintStream writer = new PrintStream(file);
                ex.printStackTrace(writer);
                return true;
            }
            Log.e(TAG, "Failed to create exception log file");
        } catch (Exception e) {
            e.printStackTrace();
        }
        return false;
    }

    public static boolean LogException(Exception ex) {
        if (ex == null) return false;
        String date = new SimpleDateFormat("yyyy-MM-dd-HH-mm", Locale.ENGLISH).format(new Date());

        int i = 0;
        while (new File(Directories.LOGS, date + " (" + i + ").txt").exists()) i++;
        File file = new File(Directories.LOGS, date + " (" + i + ").txt");

        try {
            if (file.exists() || file.createNewFile()) {
                PrintStream writer = new PrintStream(file);
                ex.printStackTrace(writer);
                return true;
            }
            Log.e(TAG, "Failed to create exception log file");
        } catch (Exception e) {
            e.printStackTrace();
        }
        return false;
    }

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

            if (file.createNewFile()) {
                PrintStream stream = new PrintStream(new FileOutputStream(file));
                stream.print(writer.toString());
            } else Log.e(TAG, "Failed to create log file: " + file.getAbsolutePath());
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

        //TODO: Add dateformat settings

        static SharedPreferences SharedPref;
        static SharedPreferences.Editor Editor;

        private static final String location_sdcard = "sdcard";
        private static final String location_normalize = "normalize";
        private static final String location_mobiledata = "mobiledata";
        private static final String location_bitrate = "bitrate";
        private static final String location_format = "format";
        private static final String location_concurrency = "concurrency";
        private static final String location_location = "location";
        private static final String location_query_amount = "query amount";
        private static final String location_timeout = "master timeout";
        private static final String location_overwrite = "overwrite mode";
        private static final String location_restore = "restore all settings";

        private static final String location_overwrite_version = "overwrite mode version";

        public static OverwriteMode overwrite = OverwriteMode.PROMPT;
        public static int timeout = 30_000;
        public static short query_amount = 25;
        public static short[] BITRATES = {64, 96, 128, 192, 256, 320};
        public static short bitrate = 128;
        public static short concurrency = 3;
        public static boolean sdcard = Directories.isExternalStorageAvailable();
        public static boolean normalize = true;
        public static boolean mobiledata;
        public static boolean restore;
        public static String format = "mp3";
        public static File location = new File(Environment.getExternalStorageDirectory() + "/Music");

        public static Query.ThumbnailQuality queryImage = Query.ThumbnailQuality.High;
        public static Query.ThumbnailQuality queryImageLarge = Query.ThumbnailQuality.Medium;
        public static Query.ThumbnailQuality downloadImage = Query.ThumbnailQuality.High;

        public static void Save() {
            Editor.putInt(location_bitrate, bitrate);
            Editor.putString(location_format, format);
            Editor.putBoolean(location_normalize, normalize);
            Editor.putBoolean(location_mobiledata, mobiledata);
            Editor.putBoolean(location_sdcard, sdcard);
            Editor.putInt(location_concurrency, concurrency);
            Editor.putString(location_location, location.getAbsolutePath());
            Editor.putInt(location_query_amount, query_amount);
//            Editor.putInt(location_timeout, timeout);
            Editor.putInt(location_overwrite, overwrite.getValue());
            Editor.putInt(location_overwrite_version, OverwriteMode.versionID);
            Editor.putBoolean(location_restore, restore);
            Editor.apply();
        }

        public static void Load() {
            restore = SharedPref.getBoolean(location_restore, false);

            if (restore) Save();
            else {
                bitrate = (short) SharedPref.getInt(location_bitrate, bitrate);
                format = SharedPref.getString(location_format, format);
                sdcard = Directories.isExternalStorageAvailable() && SharedPref.getBoolean(location_sdcard, sdcard);
                normalize = SharedPref.getBoolean(location_normalize, normalize);
                mobiledata = SharedPref.getBoolean(location_mobiledata, mobiledata);
                location = new File(SharedPref.getString(location_location, location.getAbsolutePath()));
                concurrency = (short) SharedPref.getInt(location_concurrency, concurrency);
                query_amount = (short) SharedPref.getInt(location_query_amount, query_amount);
                //            timeout = SharedPref.getInt(location_timeout, timeout);
            }

            if (SharedPref.getInt(location_overwrite_version, -1) == OverwriteMode.versionID)
                overwrite = OverwriteMode.values()[SharedPref.getInt(location_overwrite, overwrite.getValue())];
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
//        public static final String NOTIF_CANCEL = "notifications.cancel";

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

        Directories.CACHE = getCacheDir();
        Directories.PERMANENT = getFilesDir();
        Directories.BIN = new File(Directories.CACHE, "/bin/");
        Directories.HISTORY = new File(Directories.PERMANENT, "/history/");
        Directories.DOWNLOADS = new File(Directories.CACHE, "/queue.dat");

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

//    @Override
//    public void onTerminate() {
//        Log.w(TAG, "onTerminate");
//        if (downloadReceiver != null) downloadReceiver.onTerminate();
//        super.onTerminate();
//    }

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
            if (LogException(ex))
                Log.i(TAG, "Exception has been stored to /logs/");
            else Log.e(TAG, "Failed to store exception into /logs/");
            androidDefaultUEH.uncaughtException(thread, ex);
        }
    };

    public static class Directories {
        static File CACHE = new File(Environment.getExternalStorageDirectory().getAbsolutePath(), "/Year Zero/Renebeats/");
        static File PERMANENT = new File(Environment.getExternalStorageDirectory().getAbsolutePath(), "/Year Zero/Renebeats/");
        static File BIN = new File(CACHE, "/bin/");
        static File LOGS = new File(CACHE, "/logs/");
        static File HISTORY = new File(PERMANENT, "/history/");
        static File DOWNLOADS = new File(CACHE, "/queue.dat");
        public static File MUSIC = new File(Environment.getExternalStorageDirectory(), "/Music/");

        private static boolean isExternalStorageAvailable() {
            String state = Environment.getExternalStorageState();
//            boolean mExternalStorageAvailable = false;
//            boolean mExternalStorageWritable = false;
//            Environment.
//            if (Environment.MEDIA_MOUNTED.equals(state))
//                mExternalStorageAvailable = mExternalStorageWritable = true;
//            else if (Environment.MEDIA_MOUNTED_READ_ONLY.equals(state))
//                mExternalStorageAvailable = true;
//
//            return mExternalStorageAvailable && mExternalStorageWritable;
            return Environment.MEDIA_MOUNTED.equals(state);
        }

        public static long GetCacheSize() {
            return BIN.length();
        }

        public static boolean ClearCache() {
            try {
                FileUtils.deleteDirectory(BIN);
                return true;
            } catch (IOException e) {
                e.printStackTrace();
                return false;
            }
        }

        public static long GetHistorySize() {
            return HISTORY.length();
        }

        public static boolean DeleteHistory() {
            try {
                FileUtils.deleteDirectory(HISTORY);
                return true;
            } catch (IOException e) {
                e.printStackTrace();
                return false;
            }
        }
    }

    static class Notif {
        static final String DOWNLOAD_PROGRESS = "com.yearzero.renebeats/download/progress";
        static final String DOWNLOAD_COMPLETE = "com.yearzero.renebeats/download/complete";
        static final int DOWNLOAD_BASE_ID = 0x7B00_0000;
    }

    public static class History {
        public static final String EXTENSION = "hist";
        private static final Kryo kryo = new Kryo();

        static {
            kryo.register(HistoryLog.class, 100);
            kryo.register(HistoryLog[].class, 101);
            kryo.register(Date.class, 200);
            kryo.register(String[].class, 301);
            kryo.register(Status.class, 400);
            kryo.register(Status.Convert.class, 500);
            kryo.register(Status.Download.class, 600);
        }

        public interface Callback<T, U> {
            void onComplete(U data);
            void onError(@Nullable T current, Exception e);
        }

        static int Compress(Date date) {
            Calendar cal = Calendar.getInstance();
            cal.setTime(date);
            return Compress((short) cal.get(Calendar.YEAR), (byte) cal.get(Calendar.MONTH));
        }

        static int Compress(short year, byte month) {
            return (year << 4) | month;
        }

        static File GetHistFile(int compressed) {
            return GetHistFile((short) (compressed >> 4), (byte) (compressed & 0xF));
        }

        static File GetHistFile(short year, byte month) {
            return new File(Directories.HISTORY, String.format(Locale.ENGLISH, "%d-%d.%s", year, month + 1, EXTENSION));
        }

//        static long CompressRange(short start, short end) {
//            return (start << 16) | end;
//        }
//
//        static long CompressRange(short starty, byte startm, short endy, byte endm) {
//            return CompressRange(Compress(starty, startm), Compress(endy, endm));
//        }
//
//        static long CompressRange(Date start, Date end) {
//            return CompressRange(Compress(start), Compress(end));
//        }

        public static class RetrieveRangeTask extends AsyncTask<Long, Void, List<HistoryLog[]>> {
            protected Exception exception;
            protected Callback<Integer, List<HistoryLog[]>> callback;
            protected ArrayList<SparseArray<Exception>> runtimeException = new ArrayList<>();
            protected ArrayList<HistoryLog[]> result = new ArrayList<>();

            // ENCODING:
            // High Short -> Start of Range
            // Low  Short -> End of Range
            //
            // Short = Year * Month
            // (Month starts from 0-11)
            // (Precision = MONTH)

            @Override
            protected List<HistoryLog[]> doInBackground(Long... ranges) {
                if (ranges == null || ranges.length <= 0) {
                    SparseArray<Exception> ex = new SparseArray<>();
                    runtimeException.add(ex);
                    ArrayList<HistoryLog> array = new ArrayList<>();
                    for (@NonNull File file : Directories.HISTORY.listFiles()) {
                        short year;
                        byte month;

                        try {
                            if (!FilenameUtils.getExtension(file.getName()).toLowerCase().equals(EXTENSION)) continue;

                            String name = file.getName();
                            year = Short.parseShort(name.substring(0, name.indexOf('-')));
                            if (year < 0) continue;

                            month = (byte) Short.parseShort(name.substring(name.indexOf('-') + 1, name.lastIndexOf('.')));
                            if (month < 0 || month > 11) continue;
                        } catch (NumberFormatException ignored) {
                            continue;
                        }

                        try {
                            Input input = new Input(new FileInputStream(file));
                            array.addAll(Arrays.asList(kryo.readObject(input, HistoryLog[].class)));
                            input.close();
                        } catch (IOException | KryoException e) {
                            if (callback != null) callback.onError(Compress(year, month), e);
                            ex.append(Compress(year, month), e);
                        }
                    }
                    result.add(array.toArray(new HistoryLog[0]));
                    if (callback != null) callback.onComplete(result);
                    return result;
                }

                for (Long range : ranges) {
                    if (range == null) continue;
                    short start = (short) (range & 0xFFFF_0000);
                    short end = (short) (range & 0x0000_FFFF);

                    byte startm = (byte) (start % 12);
                    byte endm = (byte) (end & 12);

                    short starty = (short) (start / 12);
                    short endy = (short) (end / 12);

                    SparseArray<Exception> ex = new SparseArray<>();
                    runtimeException.add(ex);

                    for (; starty <= endy; starty++) {
                        for (; starty < endy || startm <= endm; startm++) {
                            File file = GetHistFile(starty, startm);

                            try {
                                Input input = new Input(new FileInputStream(file));
                                result.add(kryo.readObject(input, HistoryLog[].class));
                                input.close();
                            } catch (IOException | KryoException e) {
                                ex.append(starty * 12 + startm, e);
                                if (callback != null) callback.onError(Compress(starty, startm), e);
                            }
                        }
                    }
                }
                if (callback != null) callback.onComplete(result);
                return result;
            }

            public RetrieveRangeTask setCallback(Callback<Integer, List<HistoryLog[]>> callback) {
                this.callback = callback;
                return this;
            }

            public Exception getException() {
                return exception;
            }

            public List<SparseArray<Exception>> getRuntimeException() {
                return runtimeException;
            }

            public ArrayList<HistoryLog[]> getResult() {
                return result;
            }
        }

        public static class RetrieveTask extends AsyncTask<Integer, Void, List<HistoryLog[]>> {
            protected Exception exception;
            protected Callback<Integer, List<HistoryLog[]>> callback;
            protected ArrayList<SparseArray<Exception>> runtimeException = new ArrayList<>();
            protected ArrayList<HistoryLog[]> result = new ArrayList<>();

            // ENCODING:
            // High Short -> Start of Range
            // Low  Short -> End of Range
            //
            // Short = Year * Month
            // (Month starts from 0-11)
            // (Precision = MONTH)

            @Override
            protected List<HistoryLog[]> doInBackground(Integer... times) {
                if (times == null) {
                    exception = new IllegalArgumentException("Parameters are null");
                    return null;
                }

                for (Integer t : times) {
                    if (t == null) continue;
                    short year = (short) (t >> 4);
                    byte month = (byte) (t & 0xF);

                    SparseArray<Exception> ex = new SparseArray<>();
                    runtimeException.add(ex);

                    File file = GetHistFile(year, month);

                    try {
                        Input input = new Input(new FileInputStream(file));
                        result.add(kryo.readObject(input, HistoryLog[].class));
                        input.close();
                    } catch (IOException | KryoException e) {
                        ex.append(t, e);
                        if (callback != null) callback.onError(t, e);
                    }
                }
                if (callback != null) callback.onComplete(result);
                return result;
            }

            public RetrieveTask setCallback(Callback<Integer, List<HistoryLog[]>> callback) {
                this.callback = callback;
                return this;
            }

            public Exception getException() {
                return exception;
            }

            public List<SparseArray<Exception>> getRuntimeException() {
                return runtimeException;
            }

            public ArrayList<HistoryLog[]> getResult() {
                return result;
            }
        }

        public static class StoreTask extends AsyncTask<HistoryLog, Void, SparseArray<Exception>> {

            protected Callback<HistoryLog[], SparseArray<Exception>> callback;

            // ENCODING:
            // High Short -> Start of Range
            // Low  Short -> End of Range
            //
            // Short = Year * Month
            // (Month starts from 0-11)
            // (Precision = MONTH)

            @Override
            protected SparseArray<Exception> doInBackground(HistoryLog... data) {
                SparseArray<Exception> exceptions = new SparseArray<>();

                if (data == null) {
                    Exception e = new IllegalArgumentException("Download array is null");
                    exceptions.put(-1, e);
                    if (callback != null) callback.onError(null, e);
                    return exceptions;
                }

                SparseArray<ArrayList<HistoryLog>> categorized = new SparseArray<>();

                for (HistoryLog d : data) {
                    int extract = Compress(d.assigned);
                    ArrayList<HistoryLog> list = categorized.get(extract);
                    if (list == null) {
                        list = new ArrayList<>();
                        categorized.put(extract, list);
                    }
                    list.add(d);
                }

                for (int i = 0; i < categorized.size(); i++) {
                    HistoryLog[] array = categorized.valueAt(i).toArray(new HistoryLog[0]);
                    Arrays.sort(array, (a, b) -> b.assigned.compareTo(a.assigned));
                    int key = categorized.keyAt(i);
                    File file = GetHistFile(key);
                    if (!(file.getParentFile().exists() || file.getParentFile().mkdirs())) Log.e(TAG, "Failed to create parent directory");
                    
                    try {
                        if (!(file.exists() || file.createNewFile())) Log.e(TAG, "Failed to create file");
                        Output output = new Output(new FileOutputStream(file));
                        kryo.writeObject(output, array);
                        output.close();
                    } catch (IOException | KryoException e) {
                        e.printStackTrace();
                        exceptions.put(key, e);
                        if (callback != null) callback.onError(array, e);
                    }
                }
                if (callback != null) callback.onComplete(exceptions);
                return exceptions;
            }

            public StoreTask setCallback(Callback<HistoryLog[], SparseArray<Exception>> callback) {
                this.callback = callback;
                return this;
            }
        }

        public static class AppendNowTask extends AsyncTask<Download, Void, Exception> {

            protected Callback<Exception, Exception> callback;

            @Override
            protected Exception doInBackground(Download... downloads) {
                int tag = Compress(new Date());

                List<HistoryLog[]> retrieve = new RetrieveTask().doInBackground(tag);
                ArrayList<HistoryLog> data = retrieve == null || retrieve.size() < 1 || retrieve.get(0) == null ? new ArrayList<>() : new ArrayList<>(Arrays.asList(retrieve.get(0)));

                for (Download d : downloads)
                    data.add(HistoryLog.Cast(d));

                SparseArray<Exception> exs = new StoreTask().doInBackground(data.toArray(new HistoryLog[0]));
                if (exs == null || exs.get(tag) == null) {
                    if (callback != null) callback.onComplete(null);
                    return null;
                } else {
                    Exception e = exs.get(tag);
                    if (callback != null) callback.onError(null, e);
                    return e;
                }
            }

            public AppendNowTask setCallback(Callback<Exception, Exception> callback) {
                this.callback = callback;
                return this;
            }
        }
    }
}
