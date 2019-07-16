package com.yearzero.renebeats.notification;

import android.app.Activity;
import android.app.PendingIntent;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.os.Build;
import android.provider.Settings;
import android.util.Log;

import androidx.annotation.NonNull;
import androidx.core.app.NotificationCompat;

import com.google.android.material.snackbar.Snackbar;
import com.yearzero.renebeats.Commons;
import com.yearzero.renebeats.R;
import com.yearzero.renebeats.download.MainActivity;
import com.yearzero.renebeats.download.Download;
import com.yearzero.renebeats.download.DownloadService;

import java.util.Locale;

//TODO: Check icons on older Android versions for incorrect scaling and/or trimming

public class DownloadReceiver extends BroadcastReceiver {
    private static final String TAG = "DownloadReceiver";

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

    public DownloadReceiver() { }

    public DownloadReceiver(@NonNull Activity activity, boolean notifications) {
        this.activity = activity;
        this.notifications = notifications;
    }

    @Override
    public void onReceive(Context context, Intent intent) {
        if (intent == null) return;

        switch (intent.getIntExtra(Commons.ARGS.RESULT, Commons.ARGS.FAILED)) {
            case Commons.ARGS.DESTROY:
                Log.w(TAG, "Service was destroyed");

                if (notifications && Commons.Notif.manager != null) {
                    Download[] remaining = (Download[]) intent.getSerializableExtra(Commons.ARGS.DATA);

                    if (remaining == null) break;
                    for (Download d : remaining)
                        if (d.status.isSuccessful())
                            Commons.Notif.manager.notify(getNotifID(d.id), new NotificationCompat.Builder(activity, Commons.Notif.DOWNLOAD_PROGRESS)
                                    .setContentIntent(PendingIntent.getActivity(activity, 0, new Intent(context, MainActivity.class), 0))
                                    .setPriority(NotificationCompat.PRIORITY_HIGH)
                                    .setSmallIcon(R.drawable.ic_notif)
                                    .setContentTitle(d.title)
                                    .setColor(context.getResources().getColor(R.color.Accent))
                                    .setGroupAlertBehavior(NotificationCompat.GROUP_ALERT_SUMMARY)
                                    .setGroup(Commons.Notif.CHANNEL_ID)
                                    .setContentText("Cancelled")
                                    .setProgress(0, 0, false)
                                    .setAutoCancel(true)
                                    .setOngoing(false)
                                    .build()
                            );
                }
                return;
            case Commons.ARGS.ERR_LOAD:
                Log.w(TAG, "Failed to load queue");
                break;
        }

        Download data = (Download) intent.getSerializableExtra(Commons.ARGS.DATA);
        if (data == null) return;

        if (notifications) {
            Intent i = new Intent(context, MainActivity.class);
//            i.putExtra(Commons.ARGS.NOTIF_CANCEL, data.id);

            NotificationCompat.Builder builder = new NotificationCompat.Builder(activity, Commons.Notif.DOWNLOAD_PROGRESS)
                    .setContentIntent(PendingIntent.getActivity(activity, 0, i, 0))
                    .setPriority(NotificationCompat.PRIORITY_HIGH)
                    .setSmallIcon(R.drawable.ic_notif)
                    .setContentTitle(data.title)
                    .setColor(context.getResources().getColor(R.color.Accent))
                    .setGroupAlertBehavior(NotificationCompat.GROUP_ALERT_SUMMARY)
                    .setGroup(Commons.Notif.CHANNEL_ID);

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                builder.setChannelId(Commons.Notif.CHANNEL_ID);
            }
            boolean success = false;
            switch (intent.getIntExtra(Commons.ARGS.RESULT, Commons.ARGS.FAILED)) {
                case Commons.ARGS.SUCCESS:
                    success = true;
                    builder.setSound(Settings.System.DEFAULT_NOTIFICATION_URI);
                    builder.setProgress(0, 0, false);
//                    builder.setGroup(DONE_ID);
                    Snackbar.make(activity.findViewById(R.id.main), "Success!", Snackbar.LENGTH_LONG).show();
                    if (!notifications) break;

                    if (data.assigned == null || data.completed == null)
                        builder.setContentText("");
                    else {
                        String text = "Succeeded after ";

                        long elapsed = data.completed.getTime() - data.assigned.getTime();
                        short hour = (short) (elapsed / 3600_000);
                        short minute = (short) ((elapsed / 60_000) % 60);
                        short second = (short) ((elapsed / 1000) % 60);

                        if (hour > 0) {
                            text += hour + "h ";
                            if (minute < 10) text += "0";
                            text += minute + "m ";
                            if (second < 10) text += "0";
                        } else if (minute > 0) {
                            text += minute + "m ";
                            if (second < 10) text += "0";
                        }
                        text += second + "s";

                        builder.setContentText(text);
                    }

                    builder.setAutoCancel(true);
                    builder.setOngoing(false);
                    break;
                case Commons.ARGS.FAILED:
                    builder.setSound(Settings.System.DEFAULT_NOTIFICATION_URI);
                    builder.setProgress(0, 0, false);
//                    builder.setGroup(DONE_ID);
                    Snackbar.make(activity.findViewById(R.id.main), "Failed to download", Snackbar.LENGTH_LONG).show();
                    Exception e = (Exception) intent.getSerializableExtra(Commons.ARGS.EXCEPTION);
                    Log.e(TAG, e == null ? "Unknown error" : e.getMessage());

                    builder.setAutoCancel(true);
                    builder.setOngoing(false);

                    if (intent.getBooleanExtra(Commons.ARGS.REMAINING, true)) {
                        if (notifications) {
                            if (data.exception instanceof IllegalArgumentException)
                                builder.setContentText("Download request is invalid");
                            else if (data.exception instanceof DownloadService.ServiceException) {
                                DownloadService.ServiceException ex = (DownloadService.ServiceException) data.exception;
//                                //region ServiceException switch
//                                switch (ex.getDownload()) {
//                                    case QUEUED:
//                                        builder.setContentText("Failed while queueing for download");
//                                        break;
//                                    case RUNNING:
//                                        if (data.total > 0L) {
//                                            double c = data.current;
//                                            double t = data.total;
//
//                                            short ci = 0;
//                                            short ti = 0;
//
//                                            while (c >= 1000d && ci < Commons.suffix.size) {
//                                                c /= 1000d;
//                                                ci++;
//                                            }
//
//                                            while (t >= 1000d && ti < Commons.suffix.size) {
//                                                t /= 1000d;
//                                                ti++;
//                                            }
//
//                                            builder.setContentText(String.format(Locale.ENGLISH, "Failed while downloading. %.2f%s of %.2f%s completed.", c, Commons.suffix[ci], t, Commons.suffix[ti]));
//                                        } else builder.setContentText("Failed while downloading");
//                                        break;
//                                    case PAUSED:
//                                        if (data.current > 0L) {
//                                            double pc = data.current;
//                                            double pt = data.total;
//
//                                            short pci = 0;
//                                            short pti = 0;
//
//                                            while (pc >= 1000d && pci < Commons.suffix.size) {
//                                                pc /= 1000d;
//                                                pci++;
//                                            }
//
//                                            while (pt >= 1000d && pti < Commons.suffix.size) {
//                                                pt /= 1000d;
//                                                pti++;
//                                            }
//
//                                            builder.setContentText(String.format(Locale.ENGLISH, "An exception occurred while download was paused. %.2f%s of %.2f%s completed.", pc, Commons.suffix[pci], pt, Commons.suffix[pti]));
//                                        } else
//                                            builder.setContentText("An exception occurred while download was paused");
//                                        break;
//                                    default:
//                                        switch (((DownloadService.ServiceException) data.exception).getConvert()) {
//                                            case QUEUED:
//                                                builder.setContentText("An exception occurred while queueing for conversion");
//                                                break;
//                                            case PAUSED:
//                                                builder.setContentText("An exception occurred while paused before conversion");
//                                                break;
//                                            case SKIPPED:
//                                                builder.setContentText("An unknown exception occurred (skipped)");
//                                                break;
//                                            case RUNNING:
//                                                double v = data.current;
//                                                short vi = 0;
//
//                                                while (v >= 1000d && vi < Commons.suffix.size) {
//                                                    v /= 1000d;
//                                                    vi++;
//                                                }
//
//                                                builder.setContentText("Failed to convert file");
//                                                break;
//                                            default:
//                                                builder.setContentText("An exception occurred and was unhandled");
//                                        }
//                                }
//                                //endregion
                                builder.setContentText(ex.getMessage());
                                builder.setProgress(0, 0, false);
                                builder.setAutoCancel(true);
                                builder.setOngoing(false);
                            }
                        }
                    }
                    break;
                case Commons.ARGS.CANCELLED:
                    builder.setContentText("Cancelled");
                    builder.setProgress(0, 0, false);
                    builder.setAutoCancel(true);
//                    builder.setGroup(DONE_ID);
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
                                builder.setProgress(0, 0, false);
                                break;
                            case RUNNING:
                                long t = intent.getLongExtra(Commons.ARGS.TOTAL, 0L);
                                if (intent.getBooleanExtra(Commons.ARGS.PAUSED, false)) builder.setContentText("Paused");
                                else if (t <= 0L) builder.setContentText("Processing");
                                else builder.setContentText(String.format(Locale.ENGLISH, "Downloading %s of %s", Commons.FormatBytes(intent.getLongExtra(Commons.ARGS.CURRENT, 0L)), Commons.FormatBytes(t)));

                                builder.setAutoCancel(false);
                                builder.setOngoing(true);
                                break;
                            case PAUSED:
                                builder.setContentText("Download Paused");
                                builder.setProgress((int) data.current, (int) data.total, data.indeterminate);
                                builder.setAutoCancel(false);
                                builder.setOngoing(false);
                                break;
                            case COMPLETE:
                                if (data.status.convert == null)
                                    builder.setContentText("Processing");
                                else {
                                    switch (data.status.convert) {
                                        case QUEUED:
                                            builder.setContentText("Download Completed, waiting for conversion");
                                            builder.setAutoCancel(false);
                                            builder.setOngoing(true);
                                            break;
                                        case RUNNING:
                                            builder.setContentText(Commons.FormatBytes(intent.getLongExtra(Commons.ARGS.SIZE, 0L)) + " processed so far");
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
                                            builder.setContentText("Failed, " + (x == null ? "Unknown Error" : x.getMessage()));
                                            builder.setAutoCancel(true);
                                            builder.setOngoing(false);
                                        case SKIPPED:
                                        case COMPLETE:
                                            if (data.status.metadata == null) {
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
                    builder.setSound(Settings.System.DEFAULT_NOTIFICATION_URI);
                    builder.setProgress(0, 0, false);
                    Log.w(TAG, "Unknown misc parameter: " + intent.getIntExtra(Commons.ARGS.RESULT, Commons.ARGS.FAILED));
            }

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O)
                Commons.Notif.manager.notify(0, new NotificationCompat.Builder(activity, Commons.Notif.DOWNLOAD_PROGRESS)
                    .setChannelId(Commons.Notif.CHANNEL_ID)
                    .setSmallIcon(R.drawable.ic_notif)
                    .setGroup(Commons.Notif.CHANNEL_ID)
                    .setColor(activity.getResources().getColor(R.color.Accent))
                    .setGroupAlertBehavior(NotificationCompat.GROUP_ALERT_SUMMARY)
                    .setGroupSummary(true)
                    .setContentText("All downloads have finished.")
                    .setContentTitle("Done")
                    .build());

            int id = getNotifID(data.id);
            if (success)
                Commons.Notif.manager.cancel(id);
            Commons.Notif.manager.notify(id, builder.build());
        }
    }

    private int getNotifID(long id) {
        return Commons.Notif.DOWNLOAD_BASE_ID | (int) (id & 0x0000_0000_00FF_FFFF);
    }

//    public void onTerminate() {
//        if (manager != null) manager.cancelAll();
//    }
}
