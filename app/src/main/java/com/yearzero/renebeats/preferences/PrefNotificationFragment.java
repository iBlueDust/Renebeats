package com.yearzero.renebeats.preferences;


import android.app.Fragment;
import android.os.Bundle;

import androidx.preference.CheckBoxPreference;
import androidx.preference.SwitchPreference;

import com.yearzero.renebeats.R;

import de.mrapp.android.preference.activity.PreferenceFragment;

/**
 * A simple {@link Fragment} subclass.
 */
public class PrefNotificationFragment extends PreferenceFragment {

    private SwitchPreference Notifications;
    private CheckBoxPreference Queue, Running, Completed;

    public PrefNotificationFragment() { }

    @Override
    public void onCreatePreferences(Bundle savedInstanceState, String rootKey) {
        addPreferencesFromResource(R.xml.pref_notification);

        Notifications = (SwitchPreference) findPreference(getString(R.string.pref_notification_master));

        Queue = (CheckBoxPreference) findPreference(getString(R.string.pref_notification_master));
        Running = (CheckBoxPreference) findPreference(getString(R.string.pref_notification_running));
        Completed = (CheckBoxPreference) findPreference(getString(R.string.pref_notification_completed));
    }

}
