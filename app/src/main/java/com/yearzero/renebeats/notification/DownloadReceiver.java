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
import com.yearzero.renebeats.InternalArgs;
import com.yearzero.renebeats.R;
import com.yearzero.renebeats.download.Download;
import com.yearzero.renebeats.download.DownloadService;
import com.yearzero.renebeats.download.MainActivity;
import com.yearzero.renebeats.preferences.Preferences;

import java.util.Locale;

//TODO: Check icons on older Android versions for incorrect scaling and/or trimming of icons

public class DownloadReceiver extends BroadcastReceiver {
    private static final String TAG = "DownloadReceiver";

    private Activity activity;

    public DownloadReceiver() {}
    public DownloadReceiver(@NonNull Activity activity) {
        this.activity = activity;
    }

    @Override
    public void onReceive(Context context, Intent intent) {
        if (intent == null) return;

        switch (intent.getIntExtra(InternalArgs.RESULT, InternalArgs.FAILED)) {
            case InternalArgs.DESTROY:
                Log.w(TAG, "Service was destroyed");

                if (Preferences.getNotifications() && Notifications.manager != null) {
                    Download[] remaining = (Download[]) intent.getSerializableExtra(InternalArgs.DATA);

                    if (remaining == null || !(Preferences.getNotifications() && Preferences.getNotifications_completed())) break;
                    for (Download d : remaining)
                        if (d.getStatus().isSuccessful())
                            Notifications.manager.notify(getNotificationID(d.getId()), new NotificationCompat.Builder(activity, Notifications.DOWNLOAD_PROGRESS)
                                    .setContentIntent(PendingIntent.getActivity(activity, 0, new Intent(context, MainActivity.class), 0))
                                    .setPriority(NotificationCompat.PRIORITY_HIGH)
                                    .setSmallIcon(R.drawable.ic_notif)
                                    .setContentTitle(d.getTitle())
                                    .setColor(context.getResources().getColor(R.color.Accent))
                                    .setGroupAlertBehavior(NotificationCompat.GROUP_ALERT_SUMMARY)
                                    .setGroup(Notifications.CHANNEL_ID)
                                    .setContentText("Cancelled")
                                    .setProgress(0, 0, false)
                                    .setAutoCancel(true)
                                    .setOngoing(false)
                                    .build()
                            );
                }
                return;
            case InternalArgs.ERR_LOAD:
                Log.w(TAG, "Failed to load queue");
                break;
        }

        Download data = (Download) intent.getSerializableExtra(InternalArgs.DATA);
        if (data == null) return;

        if (Preferences.getNotifications()) {
            Intent i = new Intent(context, MainActivity.class);
//            i.putExtra(InternalArgs.NOTIF_CANCEL, data.id);

            NotificationCompat.Builder builder = new NotificationCompat.Builder(activity, Notifications.DOWNLOAD_PROGRESS)
                    .setContentIntent(PendingIntent.getActivity(activity, 0, i, 0))
                    .setPriority(NotificationCompat.PRIORITY_HIGH)
                    .setSmallIcon(R.drawable.ic_notif)
                    .setContentTitle(data.getTitle())
                    .setColor(context.getResources().getColor(R.color.Accent))
                    .setGroupAlertBehavior(NotificationCompat.GROUP_ALERT_SUMMARY)
                    .setGroup(Notifications.CHANNEL_ID);

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                builder.setChannelId(Notifications.CHANNEL_ID);
            }
            boolean success = false;
            boolean allowed = false;

            switch (intent.getIntExtra(InternalArgs.RESULT, InternalArgs.FAILED)) {
                case InternalArgs.SUCCESS:
                    success = true;
                    builder.setSound(Settings.System.DEFAULT_NOTIFICATION_URI);
                    builder.setProgress(0, 0, false);
                    Snackbar.make(activity.findViewById(R.id.main), "Success!", Snackbar.LENGTH_LONG).show();

                    if (data.getAssigned() == null || data.getCompleteDate() == null)
                        builder.setContentText("");
                    else {
                        String text = "Succeeded after ";

                        long elapsed = data.getCompleteDate().getTime() - data.getAssigned().getTime();
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
                    allowed = Preferences.getNotifications_completed();
                    break;
                case InternalArgs.FAILED:
                    builder.setSound(Settings.System.DEFAULT_NOTIFICATION_URI);
                    builder.setProgress(0, 0, false);
                    Snackbar.make(activity.findViewById(R.id.main), "Failed to download", Snackbar.LENGTH_LONG).show();
                    Exception e = (Exception) intent.getSerializableExtra(InternalArgs.EXCEPTION);
                    Log.e(TAG, e == null ? "Unknown error" : e.getMessage());

                    builder.setAutoCancel(true);
                    builder.setOngoing(false);

                    if (intent.getBooleanExtra(InternalArgs.REMAINING, true)) {
                        if (Preferences.getNotifications()) {
                            if (data.getException() instanceof IllegalArgumentException)
                                builder.setContentText("Download request is invalid");
                            else if (data.getException() instanceof DownloadService.ServiceException) {
                                DownloadService.ServiceException ex = (DownloadService.ServiceException) data.getException();
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
                    allowed = Preferences.getNotifications_completed();
                    break;
                case InternalArgs.CANCELLED:
                    builder.setContentText("Cancelled");
                    builder.setProgress(0, 0, false);
                    builder.setAutoCancel(true);
//                    builder.setGroup(DONE_ID);
                    builder.setOngoing(false);
                    allowed = Preferences.getNotifications_completed();
                    break;
                case InternalArgs.PROGRESS:
                    if (intent.getBooleanExtra(InternalArgs.INDETERMINATE, true))
                        builder.setProgress(0, 0, true);
                    else {
                        long total = intent.getLongExtra(InternalArgs.TOTAL, 0);
                        builder.setProgress((int) total, (int) intent.getLongExtra(InternalArgs.CURRENT, 0), false);
                    }

                    if (data.getStatus().getDownload() == null)
                        builder.setContentText("Processing");
                    else {
                        //region Status Main
                        switch (data.getStatus().getDownload()) {
                            case QUEUED:
                                builder.setContentText("Waiting for download");
                                builder.setAutoCancel(false);
                                builder.setOngoing(true);
                                builder.setProgress(0, 0, false);
                                break;
                            case RUNNING:
                                long t = intent.getLongExtra(InternalArgs.TOTAL, 0L);
                                if (intent.getBooleanExtra(InternalArgs.PAUSED, false)) builder.setContentText("Paused");
                                else if (t <= 0L) builder.setContentText("Processing");
                                else builder.setContentText(String.format(Locale.ENGLISH, "Downloading %s of %s", Commons.FormatBytes(intent.getLongExtra(InternalArgs.CURRENT, 0L)), Commons.FormatBytes(t)));

                                builder.setAutoCancel(false);
                                builder.setOngoing(true);
                                break;
                            case PAUSED:
                                builder.setContentText("Download Paused");
                                builder.setProgress((int) data.getCurrent(), (int) data.getTotal(), data.isIndeterminate());
                                builder.setAutoCancel(false);
                                builder.setOngoing(false);
                                break;
                            case COMPLETE:
                                if (data.getStatus().getConvert() == null)
                                    builder.setContentText("Processing");
                                else {
                                    switch (data.getStatus().getConvert()) {
                                        case QUEUED:
                                            builder.setContentText("Download Completed, waiting for conversion");
                                            builder.setAutoCancel(false);
                                            builder.setOngoing(true);
                                            break;
                                        case RUNNING:
                                            builder.setContentText(Commons.FormatBytes(intent.getLongExtra(InternalArgs.SIZE, 0L)) + " processed so far");
                                            builder.setAutoCancel(false);
                                            builder.setOngoing(true);
                                            break;
//                                        case PAUSED:
//                                            builder.setContentText("Paused");
//                                            builder.setProgress(0, 1, false);
//                                            builder.setAutoCancel(false);
//                                            builder.setOngoing(true);
//                                            break;
                                        case FAILED:
                                            Exception x = (Exception) intent.getSerializableExtra(InternalArgs.EXCEPTION);
                                            builder.setContentText("Failed, " + (x == null ? "Unknown Error" : x.getMessage()));
                                            builder.setAutoCancel(true);
                                            builder.setOngoing(false);
                                        case SKIPPED:
                                        case COMPLETE:
                                            if (data.getStatus().getMetadata() == null) {
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
                    allowed = Preferences.getNotifications_running();
                    break;
                default:
                    builder.setSound(Settings.System.DEFAULT_NOTIFICATION_URI);
                    builder.setProgress(0, 0, false);
                    Log.w(TAG, "Unknown misc parameter: " + intent.getIntExtra(InternalArgs.RESULT, InternalArgs.FAILED));
            }

            if (allowed && Build.VERSION.SDK_INT >= Build.VERSION_CODES.O)
                Notifications.manager.notify(0, new NotificationCompat.Builder(activity, Notifications.DOWNLOAD_PROGRESS)
                    .setChannelId(Notifications.CHANNEL_ID)
                    .setSmallIcon(R.drawable.ic_notif)
                    .setGroup(Notifications.CHANNEL_ID)
                    .setColor(activity.getResources().getColor(R.color.Accent, activity.getTheme()))
                    .setGroupAlertBehavior(NotificationCompat.GROUP_ALERT_SUMMARY)
                    .setGroupSummary(true)
                    .setContentText("I don't think you're supposed to find this")
                    .setContentTitle("Done")
                    .build());

            int id = getNotificationID(data.getId());
            if (success || !allowed) Notifications.manager.cancel(id);
            if (allowed) Notifications.manager.notify(id, builder.build());
        }
    }

    private int getNotificationID(long id) {
        return Notifications.DOWNLOAD_BASE_ID | (int) (id & 0x0000_0000_00FF_FFFF);
    }

//    public void onTerminate() {
//        if (manager != null) manager.cancelAll();
//    }
}
