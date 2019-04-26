package com.yearzero.renebeats.activities;

import androidx.annotation.NonNull;
import androidx.appcompat.app.ActionBar;
import androidx.preference.PreferenceFragmentCompat;

import com.yearzero.renebeats.R;

public class PreferenceActivity extends de.mrapp.android.preference.activity.PreferenceActivity {

    @Override
    protected void onCreateNavigation(@NonNull PreferenceFragmentCompat fragment) {
//        super.onCreateNavigation(fragment);
        fragment.addPreferencesFromResource(R.xml.pref_main);
        ActionBar actionBar = getSupportActionBar();
        if (actionBar != null) actionBar.setDisplayHomeAsUpEnabled(true);
        setToolbarElevation(0);
    }
}
