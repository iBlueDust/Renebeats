package com.yearzero.renebeats;

import android.app.Activity;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.os.Build;
import android.util.Log;
import android.util.SparseArray;

import com.google.android.material.snackbar.Snackbar;
import com.yearzero.renebeats.Activities.NewMainActivity;

import java.io.Serializable;
import java.util.Locale;

import androidx.annotation.NonNull;
import androidx.core.app.NotificationCompat;

public class DownloadReceiver extends BroadcastReceiver implements Serializable {
    private static final String TAG = "DownloadReceiver";
    private static final long serialVersionUID = -1000L;

    private static final int SERVICE_PRENOTIF_ID = 5456782;
    private static final int NOTIF_ID = -1;
    private static final int COMPLETE_ID = -2;
    private static final String CHANNEL_ID = "DownloadChannel";
    private static final String COMPLETE_DISMISS = "NotificationCompleteDismiss";

//    private final BroadcastReceiver dismissReceiver = new BroadcastReceiver() {
//        @Override
//        public void onReceive(Context context, Intent intent) {
//            boolean destroyed = true;
//            ActivityManager manager = (ActivityManager) context.getSystemService(Context.ACTIVITY_SERVICE);
//            for (ActivityManager.RunningServiceInfo service : manager.getRunningServices(Integer.MAX_VALUE)) {
//                if (DownloadService.class.getName().equals(service.service.getClassName())) {
//                    destroyed = false;
//                    break;
//                }
//            }
//            if (destroyed) {
//                //TODO: Clear current history
//            }
//            context.unregisterReceiver(this);
//        }
//    };

    private Activity activity;

    private boolean notifications;
    private NotificationManager manager;
    private SparseArray<NotificationCompat.Builder> builders = new SparseArray<>();

    //TODO: Implement parallel/multiple downloads support

    public DownloadReceiver() { }

    public DownloadReceiver(@NonNull Activity activity, boolean notifications) {
        this.activity = activity;
        this.notifications = notifications;
    }

    @Override
    public void onReceive(Context context, Intent intent) {
        Download data = null;
        try {
            data = (Download) intent.getSerializableExtra(Commons.ARGS.DATA);
        } catch (ClassCastException | NullPointerException e) {
            e.printStackTrace();
        }

        switch (intent.getIntExtra(Commons.ARGS.RESULT, Commons.ARGS.FAILED)) {
            case Commons.ARGS.DESTROY:
                Log.w(TAG, "Service was destroyed");
                manager.cancelAll();
                break;
            case Commons.ARGS.ERR_LOAD:
                Log.w(TAG, "Failed to load queue");
                break;
        }

        if (data == null) return;

        if (notifications)
            if (builders.get(data.id) == null) {
                builders.append(data.id, new NotificationCompat.Builder(activity, Commons.Notif.DOWNLOAD_PROGRESS)
                        .setContentIntent(PendingIntent.getActivity(activity, 0, new Intent(context, NewMainActivity.class), 0))
                        .setPriority(NotificationCompat.PRIORITY_HIGH)
                        .setSmallIcon(R.mipmap.ic_launcher_round)
                );
            }

            if (manager == null) {
                manager = (NotificationManager) activity.getSystemService(Context.NOTIFICATION_SERVICE);

                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    NotificationChannel channel = new NotificationChannel(CHANNEL_ID, "Download Channel", NotificationManager.IMPORTANCE_DEFAULT);
                    channel.setDescription("All downloads are displayed here");
                    manager = context.getSystemService(NotificationManager.class);
                    manager.createNotificationChannel(channel);
                }
            }

        NotificationCompat.Builder builder = builders.get(data.id);

        switch (intent.getIntExtra(Commons.ARGS.RESULT, Commons.ARGS.FAILED)) {
            case Commons.ARGS.SUCCESS:
                Snackbar.make(activity.findViewById(R.id.main), "Success!", Snackbar.LENGTH_LONG).show();
                if (!notifications) break;

                if (data.assigned == null || data.completed == null)
                    builder.setContentText("");
                else {
                    String text = "Took ";

                    long elapsed = data.completed.getTime() - data.assigned.getTime();
                    short hour = (short) (elapsed / 3600_000);
                    short minute = (short) ((elapsed / 60_000) % 60);
                    short second = (short) ((elapsed / 1000) % 60);

                    if (hour > 0) {
                        text += hour + "h ";

                        if (minute < 10) text += "0";
                        text += minute + "m ";
                        if (second < 10) text += "0";
                        text += second + "s";
                    } else if (minute > 0) {
                        text += minute + "m ";
                        if (second < 10) text += "0";
                        text += second + "s";
                    } else text += second + "s";

                    builder.setContentText(text);
                }

                builder.setAutoCancel(true);
                builder.setOngoing(false);
                builder.setContentTitle(data.title + " has succeeded");
                break;
            case Commons.ARGS.FAILED:
                Snackbar.make(activity.findViewById(R.id.main), "Failed to download", Snackbar.LENGTH_LONG).show();
                Exception e = (Exception) intent.getSerializableExtra(Commons.ARGS.EXCEPTION);
                Log.e(TAG, e == null ? "Unknown error" : e.getMessage());

                builder.setAutoCancel(true);
                builder.setOngoing(false);

                if (!intent.getBooleanExtra(Commons.ARGS.REMAINING, false))
                    manager.cancel(NOTIF_ID);
                else if (notifications) {
                    builder.setProgress(0, 0, false)
                            .setContentTitle("Failed");

                    if (data.exception instanceof IllegalArgumentException)
                        builder.setContentText("Download request is invalid");
                    else if (data.exception instanceof DownloadService.ServiceException) {
                        DownloadService.ServiceException ex = (DownloadService.ServiceException) data.exception;
                        //region ServiceException switch
                        switch (ex.getDownload()) {
                            case QUEUED:
                                builder.setContentText("Failed while queueing for download");
                                break;
                            case RUNNING:
                                if (data.total > 0L) {
                                    double c = data.current;
                                    double t = data.total;

                                    short ci = 0;
                                    short ti = 0;

                                    while (c >= 1000d && ci < Commons.suffix.length) {
                                        c /= 1000d;
                                        ci++;
                                    }

                                    while (t >= 1000d && ti < Commons.suffix.length) {
                                        t /= 1000d;
                                        ti++;
                                    }

                                    builder.setContentText(String.format(Locale.ENGLISH, "Failed while downloading. %.2f%s of %.2f%s completed.", c, Commons.suffix[ci], t, Commons.suffix[ti]));
                                } else builder.setContentText("Failed while downloading");
                                break;
                            case PAUSED:
                                if (data.current > 0L) {
                                    double pc = data.current;
                                    double pt = data.total;

                                    short pci = 0;
                                    short pti = 0;

                                    while (pc >= 1000d && pci < Commons.suffix.length) {
                                        pc /= 1000d;
                                        pci++;
                                    }

                                    while (pt >= 1000d && pti < Commons.suffix.length) {
                                        pt /= 1000d;
                                        pti++;
                                    }

                                    builder.setContentText(String.format(Locale.ENGLISH, "An exception occurred while download was paused. %.2f%s of %.2f%s completed.", pc, Commons.suffix[pci], pt, Commons.suffix[pti]));
                                } else
                                    builder.setContentText("An exception occurred while download was paused");
                                break;
                            default:
                                switch (((DownloadService.ServiceException) data.exception).getConvert()) {
                                    case QUEUED:
                                        builder.setContentText("An exception occurred while queueing for conversion");
                                        break;
                                    case PAUSED:
                                        builder.setContentText("An exception occurred while paused before conversion");
                                        break;
                                    case SKIPPED:
                                        builder.setContentText("An unknown exception occurred (skipped)");
                                        break;
                                    case RUNNING:
                                        double v = data.current;
                                        short vi = 0;

                                        while (v >= 1000d && vi < Commons.suffix.length) {
                                            v /= 1000d;
                                            vi++;
                                        }

                                        builder.setContentText("Failed to convert file");
                                        break;
                                    default:
                                        builder.setContentText("An exception occurred and was unhandled");
                                }
                        }
                        //endregion
                    }
                }
                break;
            case Commons.ARGS.CANCELLED:
                builder.setContentText("Cancelled");
                builder.setAutoCancel(true);
                builder.setOngoing(false);
                break;
            case Commons.ARGS.PROGRESS:
                if (!notifications) break;

                if (intent.getBooleanExtra(Commons.ARGS.INDETERMINATE, true))
                    builder.setProgress(0, 0, true);
                else {
                    long total = intent.getLongExtra(Commons.ARGS.TOTAL, 0);
                    builder.setProgress((int) total, (int) intent.getLongExtra(Commons.ARGS.CURRENT, 0), false);
                }

                if (data.status.download == null)
                    builder.setContentText("Processing");
                else {
                    //region Status Main
                    switch (data.status.download) {
                        case QUEUED:
                            builder.setContentText("Waiting for download");
                            builder.setAutoCancel(false);
                            builder.setOngoing(true);
                            break;
                        case RUNNING:
                            builder.setContentTitle("Downloading");

                            double c = intent.getLongExtra(Commons.ARGS.CURRENT, 0L);
                            double t = intent.getLongExtra(Commons.ARGS.TOTAL, 0L);
                            if (intent.getBooleanExtra(Commons.ARGS.PAUSED, false))
                                builder.setContentText("Paused");
                            else {
                                if (t <= 0L)
                                    builder.setContentText("Processing");
                                else {
                                    int a = 0;
                                    while (a < Commons.suffix.length && c >= 1000) {
                                        c /= 1000;
                                        a++;
                                    }
                                    int b = 0;
                                    while (b < Commons.suffix.length && t >= 1000) {
                                        t /= 1000;
                                        b++;
                                    }
                                    builder.setContentText(String.format(Locale.ENGLISH, "%.2f%s of %.2f%s", c, Commons.suffix[a], t, Commons.suffix[b]));
                                }
                            }
                            builder.setAutoCancel(false);
                            builder.setOngoing(true);
                            break;
                        case PAUSED:
                            builder.setContentText("Download Paused");
                            builder.setProgress(data.current, data.total, data.indeterminate);
                            builder.setAutoCancel(false);
                            builder.setOngoing(false);
                            break;
                        case COMPLETE:
                            if (data.status.convert == null) builder.setContentText("Processing");
                            else {
                                switch (data.status.convert) {
                                    case QUEUED:
                                        builder.setContentText("Download Completed, waiting for conversion");
                                        builder.setAutoCancel(false);
                                        builder.setOngoing(true);
                                        break;
                                    case RUNNING:
                                        builder.setContentTitle("Converting");

                                        double size = intent.getLongExtra(Commons.ARGS.CURRENT, -1L);
                                        int d = 0;
                                        while (d < Commons.suffix.length && size >= 1000) {
                                            size /= 1000;
                                            d++;
                                        }
                                        builder.setContentText(String.format(Locale.ENGLISH, "%.2f %s processed so far", size, Commons.suffix[d]));
                                        builder.setAutoCancel(false);
                                        builder.setOngoing(true);
                                        break;
                                    case PAUSED:
                                        builder.setContentText("Paused");
                                        builder.setProgress(0, 1, false);
                                        builder.setAutoCancel(false);
                                        builder.setOngoing(true);
                                        break;
                                    case FAILED:
                                        Exception x = (Exception) intent.getSerializableExtra(Commons.ARGS.EXCEPTION);
                                        builder.setContentTitle("Failed");
                                        builder.setContentText(x == null ? "Unknown Error" : x.getMessage());
                                        builder.setAutoCancel(true);
                                        builder.setOngoing(false);
                                    case SKIPPED:
                                    case COMPLETE:
                                        if (data.status.metadata == null) {
                                            builder.setContentTitle("Finishing");
                                            builder.setContentText("Applying Metadata");
                                            break;
                                        }
                                        builder.setAutoCancel(false);
                                        builder.setOngoing(true);
                                }
                            }

                    }
                    //endregion
                }
                break;
            default:
                Log.w(TAG, "Unknown misc parameter: " + intent.getIntExtra(Commons.ARGS.RESULT, Commons.ARGS.FAILED));
        }

        if (notifications) manager.notify(Commons.Notif.DOWNLOAD_BASE_ID | data.id, builder.build());
    }

//    private void PrepareNotification(Context context, Intent i) {
//        Intent intent = new Intent(context, DownloadService.class);
//        intent.putExtra(Commons.ARGS.REQ_ID, SERVICE_PRENOTIF_ID);
//        intent.putExtra(Commons.ARGS.REQUEST, Commons.ARGS.FLAG_COMPLETED);
//        intent.putExtra(Commons.ARGS.DATA, i.getSerializableExtra(Commons.ARGS.DATA));
//        context.startService(intent);
//    }
//
//    private void CompleteNotification(Download[] completed) {
//        ArrayList<Download> successful = new ArrayList<>();
//        ArrayList<Download> failed = new ArrayList<>();
//        short uncomplete = 0;
//        for (Download args : completed) {
//            if (args != null) {
//                if (args.getCompleteDate() == null) uncomplete++;
//                else if (args.isSuccessful()) successful.add(args);
//                else failed.add(args);
//            }
//        }
//        if (uncomplete > 0) Log.w(TAG, uncomplete + " is still labeled as uncomplete");
//
//        StringBuilder text = new StringBuilder();
//
//        if (failed.size() > 0) {
//            complete.setSmallIcon(R.drawable.ic_error_secondarydark_48dp);
//            if (failed.size() <= Commons.Pref.notif_done_limit) {
//                text.append(failed.get(0).title);
//                for (int i = 1; i < failed.size() - 1; i++)
//                    text.append('\n').append(failed.get(i).title);
//                if (failed.size() > 1)
//                    text.append("\nand ").append(failed.get(failed.size() - 1).title);
//                else if (failed.size() > Commons.Pref.notif_done_limit)
//                    text.append("\nand ").append(failed.size() - Commons.Pref.notif_done_limit).append(" more");
//                text.append(" FAILED");
//            }
//        } else if (successful.size() > 0) {
//            complete.setSmallIcon(R.drawable.ic_offline_pin_secondarydark_48dp);
//            if (successful.size() <= Commons.Pref.notif_done_limit) {
//                text.append(successful.get(0).title);
//                for (int i = 1; i < successful.size() - 1; i++)
//                    text.append(", ").append(successful.get(i).title);
//                if (successful.size() > 1)
//                    text.append("\nand ").append(successful.get(successful.size() - 1).title);
//                else if (successful.size() > Commons.Pref.notif_done_limit)
//                    text.append("\nand ").append(successful.size() - Commons.Pref.notif_done_limit).append(" more");
//                text.append(" SUCCEEDED");
//            }
//        }
//
//        complete.setContentText(text.toString());
//        complete.setContentTitle(String.format("Success: %s Failed: %s", successful.size(), failed.size()));
//
//        activity.registerReceiver(dismissReceiver, new IntentFilter(COMPLETE_DISMISS));
//        complete.setDeleteIntent(PendingIntent.getBroadcast(activity, 0, new Intent(COMPLETE_DISMISS), 0));
//
//        manager.notify(COMPLETE_ID, complete.build());
//    }
}
