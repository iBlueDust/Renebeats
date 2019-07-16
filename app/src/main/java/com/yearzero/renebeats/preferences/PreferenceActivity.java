package com.yearzero.renebeats.preferences;

import android.content.Intent;
import android.view.MenuItem;

import androidx.annotation.NonNull;
import androidx.appcompat.app.ActionBar;
import androidx.preference.Preference;
import androidx.preference.PreferenceFragmentCompat;

import com.yearzero.renebeats.R;
import com.yearzero.renebeats.errorlog.ErrorLogActivity;

public class PreferenceActivity extends de.mrapp.android.preference.activity.PreferenceActivity {

    private Preference ShowLogs;

    @Override
    protected void onCreateNavigation(@NonNull PreferenceFragmentCompat fragment) {
        fragment.addPreferencesFromResource(R.xml.pref_main);
        ActionBar actionBar = getSupportActionBar();
        if (actionBar != null) {
            actionBar.setDisplayHomeAsUpEnabled(true);
//            actionBar.setTitle();
        }
        setToolbarElevation(0);

        ShowLogs = fragment.findPreference(getString(R.string.pref_data_showlogs));
        ShowLogs.setOnPreferenceClickListener(p -> {
            startActivity(new Intent(PreferenceActivity.this, ErrorLogActivity.class));
            return true;
        });
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        if(item.getItemId() == android.R.id.home) {
            onBackPressed();
            return true;
        }
        return super.onOptionsItemSelected(item);
    }
}
