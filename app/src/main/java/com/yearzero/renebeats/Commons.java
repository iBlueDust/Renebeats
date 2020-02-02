package com.yearzero.renebeats;

import android.annotation.SuppressLint;
import android.app.Application;
import android.content.Context;
import android.net.wifi.WifiManager;
import android.os.AsyncTask;
import android.os.Build;
import android.os.Looper;
import android.os.Process;
import android.util.Log;
import android.widget.Toast;

import androidx.annotation.Keep;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.google.gson.ExclusionStrategy;
import com.google.gson.FieldAttributes;
import com.google.gson.GsonBuilder;
import com.squareup.picasso.OkHttp3Downloader;
import com.squareup.picasso.Picasso;
import com.tonyodev.fetch2.Fetch;
import com.tonyodev.fetch2.FetchConfiguration;
import com.tonyodev.fetch2.NetworkType;
import com.yausername.youtubedl_android.YoutubeDL;
import com.yausername.youtubedl_android.YoutubeDLException;
import com.yearzero.renebeats.download.Download;
import com.yearzero.renebeats.download.DownloadService;
import com.yearzero.renebeats.download.HistoryLog;
import com.yearzero.renebeats.notification.Notifications;
import com.yearzero.renebeats.preferences.Preferences;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.PrintStream;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;
import java.util.TimeZone;

import cafe.adriel.androidaudioconverter.callback.ILoadCallback;

@Keep
public class Commons extends Application {

    public static final int PERM_REQUEST = 0x24D1;

    private static final String TAG = "Commons";
    private static final String INTL_DATE_FORMAT = "yyyy-MM-dd hh:mm:ss";

    // SERIALIZABLE UID CLASS CODES
    // Query                  - 0E10_llE1
    // Download               - D010_AD00
    // HistoryLog             - 415C_1066
    // GuesserMode            - 6E54_0DE0

    // REMOVED UIDs
    // OverwriteMode          - 0E1E_0DE0
    // DateFormat             - DA1E_F0A1
    // ProcessSpeed           - FF0C_5F3D
    // ArtistTitleArrangement - AF11_AFAE

//    public static DownloadReceiver downloadReceiver;
    public static Fetch fetch;
    private static Locale locale = Locale.ENGLISH;
    private static NetworkType globalNetworkType;
    public static final float displayThreshold = 480f;

    public static boolean LogException(Throwable ex) {
        return LogExceptionReturn(ex) != null;
    }

    public static String LogExceptionReturn(Throwable ex) {
        StringWriter writer = new StringWriter();
        writer.append("============ ");
        SimpleDateFormat sdf = new SimpleDateFormat("yyyy/MM/dd HH:mm:ss", Locale.ENGLISH);
        sdf.setTimeZone(TimeZone.getTimeZone("UTC"));
        writer.append(sdf.format(new Date()));
        writer.append(" ============\n");
        ex.printStackTrace(new PrintWriter(writer));
        String text = writer.toString();
        return LogExceptionWrite(text);
    }

    public static String LogExceptionReturn(@NonNull Download download, @NonNull Exception ex) {
        return LogExceptionReturn(HistoryLog.generate(download), ex);
    }

    public static String LogExceptionReturn(@NonNull HistoryLog data, @NonNull Exception ex) {
        StringWriter writer = new StringWriter();
        writer.append("============ ");
        writer.append(new SimpleDateFormat(INTL_DATE_FORMAT, Locale.ENGLISH).format(new Date()));
        writer.append(" ============\n");

        if (ex instanceof DownloadService.ServiceException && ((DownloadService.ServiceException) ex).getPayload() != null) {
            writer.append(ex.getMessage());
            ((DownloadService.ServiceException) ex).getPayload().printStackTrace(new PrintWriter(writer));
        } else ex.printStackTrace(new PrintWriter(writer));

        writer.append("\nDOWNLOAD DATA\n===============================\n");
        writer.append(new GsonBuilder().setPrettyPrinting().setExclusionStrategies(new ExclusionStrategy() {
            @Override
            public boolean shouldSkipField(FieldAttributes f) {
                return f.getDeclaringClass() == HistoryLog.class && f.getName().equals("exception");
            }

            @Override
            public boolean shouldSkipClass(Class<?> clazz) {
                return false;
            }
        }).create().toJson(data));
        writer.append('\n');
        return LogExceptionWrite(writer.toString());
    }

    public static boolean LogException(@NonNull Download download, @NonNull Exception ex) {
        return LogExceptionReturn(download, ex) != null;
    }

    private static String LogExceptionWrite(String contents) {
        String date = new SimpleDateFormat(INTL_DATE_FORMAT, Locale.ENGLISH).format(new Date());

        int i = 0;
        while (new File(Directories.getLOGS(), date + " (" + i + ").log").exists()) i++;
        String name = date + (i <= 0 ? "" : " (" + i + ')') + ".log";
        File file = new File(Directories.getLOGS(), name);

        try {
            if (file.exists() || ((Directories.getLOGS().exists() || Directories.getLOGS().mkdirs()) && file.createNewFile())) {
                PrintStream stream = new PrintStream(new FileOutputStream(file, true));
                stream.print(contents);
            } else Log.e(TAG, "Failed to create log file: " + file.getAbsolutePath());
        } catch (IOException e) {
            e.printStackTrace();
            return null;
        }
        return name;
    }

    public static void setDownloadNetworkType(boolean mobiledata) {
        globalNetworkType = mobiledata ? NetworkType.ALL : NetworkType.WIFI_ONLY;
        Commons.fetch.setGlobalNetworkType(globalNetworkType);
        // This ^^^^ will override all following downloads
    }

    public static boolean isWifiConnected(Context context) {
        WifiManager wifiManager = (WifiManager) context.getApplicationContext().getSystemService(Context.WIFI_SERVICE);

        return wifiManager.isWifiEnabled() && wifiManager.getConnectionInfo().getNetworkId() != -1;
    }

    @Nullable
    public static NetworkType getDownloadNetworkType() {
        return globalNetworkType;
    }

    @SuppressLint("CommitPrefEdits")
    @Override
    public void onCreate() {
        super.onCreate();
        Thread.setDefaultUncaughtExceptionHandler((thread, throwable) -> {
            Log.e(TAG, "Uncaught Exception. Handing over to logger");
            new Thread(){
                @Override
                public void run() {
                    Looper.prepare();
                    Toast.makeText(Commons.this, getString(R.string.master_error), Toast.LENGTH_LONG).show();
                    Looper.loop();
                }
            }.start();
            if (LogException(throwable))
                Log.i(TAG, "Successfully logged unhandled exception");
            else Log.e(TAG, "Failed to log unhandled exception. Exiting...");

            try {
                Thread.sleep(3500L);
            } catch (InterruptedException ignored) {
                Log.w(TAG, "Interrupted while showing unhandled exception Toast");
            }
            Process.killProcess(Process.myPid());
            System.exit(2);
        });

        locale = Build.VERSION.SDK_INT >= Build.VERSION_CODES.N ? getResources().getConfiguration().getLocales().get(0) : getResources().getConfiguration().locale;
        Preferences.initialize(this);
        Directories.reinitialize(this);

        try {
            YoutubeDL instance = YoutubeDL.getInstance();
            instance.init(this);

            // Auto update YoutubeDL
            new UpdateYoutubeDLTask().execute(this);
        } catch (YoutubeDLException e) {
            Log.e(TAG, "Failed to initialize YouTubeDL", e);
        }

        try {
            fetch = Fetch.Impl.getInstance(new FetchConfiguration.Builder(this)
                    .setDownloadConcurrentLimit(Preferences.getConcurrency())
                    .build());
        } catch (Exception e) {
            Log.e(TAG, "Failed to initialize Fetch", e);
        }

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
        Notifications.Initialize(this);

        Picasso.Builder builder = new Picasso.Builder(this);
        builder.downloader(new OkHttp3Downloader(this, Integer.MAX_VALUE));
        Picasso built = builder.build();
        built.setLoggingEnabled(true);
        Picasso.setSingletonInstance(built);

    }

    public static String FormatBytes(long length) {
        final String[] suffix = {"B", "KB", "MB", "GB"};
        float len = (float) length;
        int i = -1;
        while (i++ < suffix.length && len >= 1000F) len /= 1000F;
        return String.format(Locale.ENGLISH, "%.2f %s", len, suffix[i]);
    }

    public static Locale getLocale() {
        return locale;
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

    private static class UpdateYoutubeDLTask extends AsyncTask<Application, Void, Void> {
            @Override
            protected Void doInBackground(Application... applications) {
                if (applications != null && applications.length > 0 && applications[0] != null) {
                    try {
                        YoutubeDL.getInstance().updateYoutubeDL(applications[0]);
                    } catch (YoutubeDLException e) {
                        e.printStackTrace();
                    }
                }
                return null;
            }
    }
}
