package com.yearzero.renebeats.fragments.preferences;


import android.app.Fragment;
import android.os.Bundle;

import com.yearzero.renebeats.R;

import de.mrapp.android.preference.activity.PreferenceFragment;

/**
 * A simple {@link Fragment} subclass.
 */
public class PrefPredictionFragment extends PreferenceFragment {


    public PrefPredictionFragment() {
        // Required empty public constructor
    }

    @Override
    public void onCreatePreferences(Bundle savedInstanceState, String rootKey) {
        addPreferencesFromResource(R.xml.pref_prediction);
    }

}
