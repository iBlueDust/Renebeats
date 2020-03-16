package com.yearzero.renebeats.notification;

import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.content.Context;
import android.os.Build;

import com.yearzero.renebeats.BuildConfig;
import com.yearzero.renebeats.R;

public class Notifications {

	static final String DOWNLOAD_PROGRESS = BuildConfig.Notif_ChannelID;
	static final String CHANNEL_ID = BuildConfig.Notif_ChannelID;
	static final int DOWNLOAD_BASE_ID = BuildConfig.Notif_BaseID;

	static NotificationManager manager;

	public static void Initialize(Context context) {
		manager = (NotificationManager) context.getSystemService(Context.NOTIFICATION_SERVICE);
		if (manager != null) {
			manager.cancelAll();
			if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
				NotificationChannel channel = new NotificationChannel(CHANNEL_ID, "Downloads in Progress", android.app.NotificationManager.IMPORTANCE_LOW);
				channel.setDescription(context.getString(R.string.notif_desc));
				channel.enableLights(false);
				channel.enableVibration(false);
				manager.createNotificationChannel(channel);
			}
		}
	}
}
