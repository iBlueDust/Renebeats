package com.yearzero.renebeats.preferences;

import android.os.Bundle;

import androidx.preference.CheckBoxPreference;
import androidx.preference.ListPreference;

import com.yearzero.renebeats.R;

import de.mrapp.android.preference.activity.PreferenceFragment;

public class PrefGeneralFragment extends PreferenceFragment {

    private ListPreference QueryTimeout, QueryAmount, ConcurrentDownloads, GuesserModes;
    private ListPreference Format, Speed, Bitrate, Overwrite, FileFormat;
    private CheckBoxPreference Normalize;

    public PrefGeneralFragment() { }

    @Override
    public void onCreatePreferences(Bundle savedInstanceState, String rootKey) {
        addPreferencesFromResource(R.xml.pref_general);

        QueryTimeout = (ListPreference) findPreference(getString(R.string.pref_defaults_timeout));
        QueryAmount = (ListPreference) findPreference(getString(R.string.pref_defaults_queryamount));
        ConcurrentDownloads = (ListPreference) findPreference(getString(R.string.pref_defaults_concurrent));
        GuesserModes = (ListPreference) findPreference(getString(R.string.pref_guesser_modes));

        Format = (ListPreference) findPreference(getString(R.string.pref_defaults_format));
        Speed = (ListPreference) findPreference(getString(R.string.pref_defaults_processspeed));
        Bitrate = (ListPreference) findPreference(getString(R.string.pref_defaults_bitrate));
        Overwrite = (ListPreference) findPreference(getString(R.string.pref_defaults_overwrite));
        FileFormat = (ListPreference) findPreference(getString(R.string.pref_defaults_filename));

        Normalize = (CheckBoxPreference) findPreference(getString(R.string.pref_defaults_normalize));
    }

}
