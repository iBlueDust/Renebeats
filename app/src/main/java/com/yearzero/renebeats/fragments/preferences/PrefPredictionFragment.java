package com.yearzero.renebeats.fragments.preferences;


import android.app.Fragment;
import android.os.Bundle;

import androidx.preference.ListPreference;

import com.yearzero.renebeats.R;

import de.mrapp.android.preference.activity.PreferenceFragment;

/**
 * A simple {@link Fragment} subclass.
 */
public class PrefPredictionFragment extends PreferenceFragment {

    private ListPreference Guesser;

    public PrefPredictionFragment() { }

    @Override
    public void onCreatePreferences(Bundle savedInstanceState, String rootKey) {
        addPreferencesFromResource(R.xml.pref_prediction);

        Guesser = (ListPreference) findPreference(getString(R.string.pref_guesser_modes));
    }

}
