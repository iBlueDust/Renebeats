package com.yearzero.renebeats.preferences;


import android.app.Fragment;
import android.os.Bundle;
import android.util.Log;

import androidx.preference.CheckBoxPreference;
import androidx.preference.SwitchPreference;

import com.yearzero.renebeats.R;

import de.mrapp.android.preference.activity.PreferenceFragment;

/**
 * A simple {@link Fragment} subclass.
 */
public class PrefNotificationFragment extends PreferenceFragment {
	private static final String TAG = "Pref.Notification";

	private SwitchPreference Notifications;
	private CheckBoxPreference Queue, Running, Completed;

	public PrefNotificationFragment() { }

	@Override
	public void onCreatePreferences(Bundle savedInstanceState, String rootKey) {
		addPreferencesFromResource(R.xml.pref_notification);

		Notifications = (SwitchPreference) findPreference(getString(R.string.pref_notification_master));

		Queue = (CheckBoxPreference) findPreference(getString(R.string.pref_notification_queue));
		Running = (CheckBoxPreference) findPreference(getString(R.string.pref_notification_running));
		Completed = (CheckBoxPreference) findPreference(getString(R.string.pref_notification_completed));

		Notifications.setChecked(Preferences.getNotifications());

		Queue.setChecked(Preferences.getNotifications_queue());
		Running.setChecked(Preferences.getNotifications_running());
		Completed.setChecked(Preferences.getNotifications_completed());
		Queue.setEnabled(Preferences.getNotifications());
		Running.setEnabled(Preferences.getNotifications());
		Completed.setEnabled(Preferences.getNotifications());

		Notifications.setOnPreferenceChangeListener((preference, newValue) -> {
			if (newValue instanceof Boolean) {
				boolean checked = (Boolean) newValue;
				Queue.setEnabled(checked);
				Running.setEnabled(checked);
				Completed.setEnabled(checked);
				Preferences.setNotifications(checked);
				Preferences.save();
				return true;
			} else {
				Log.w(TAG, "Notifications Checkbox newValue is not a Boolean");
				return false;
			}
		});

		Queue.setOnPreferenceChangeListener((preference, newValue) -> {
			if (newValue instanceof Boolean) {
				Preferences.setNotifications_queue((Boolean) newValue);
				Preferences.save();
				return true;
			} else {
				Log.w(TAG, "Queue Notifications newValue is not a Boolean");
				return false;
			}
		});

		Running.setOnPreferenceChangeListener((preference, newValue) -> {
			if (newValue instanceof Boolean) {
				Preferences.setNotifications_running((Boolean) newValue);
				Preferences.save();
				return true;
			} else {
				Log.w(TAG, "Running Notifications newValue is not a Boolean");
				return false;
			}
		});

		Completed.setOnPreferenceChangeListener((preference, newValue) -> {
			if (newValue instanceof Boolean) {
				Preferences.setNotifications_completed((Boolean) newValue);
				Preferences.save();
				return true;
			} else {
				Log.w(TAG, "Completed Notifications newValue is not a Boolean");
				return false;
			}
		});
	}

}
