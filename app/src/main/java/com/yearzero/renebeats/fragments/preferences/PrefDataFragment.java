package com.yearzero.renebeats.fragments.preferences;


import android.os.Bundle;
import android.widget.Toast;

import com.yearzero.renebeats.Commons;
import com.yearzero.renebeats.R;
import com.yearzero.renebeats.TimeoutDialog;

import androidx.annotation.Nullable;
import androidx.preference.ListPreference;
import androidx.preference.Preference;
import de.mrapp.android.preference.activity.PreferenceFragment;

public class PrefDataFragment extends PreferenceFragment {

    private static final String TAG = "PrefDataFragment";

    private ListPreference DownloadUsing;
    private Preference OutputDirectory, ClearCache, ClearPaused, DeleteHistory, RestoreSettings;

    public PrefDataFragment() {
    }

    @Override
    public void onCreatePreferences(Bundle savedInstanceState, String rootKey) {
        addPreferencesFromResource(R.xml.pref_data);
        
        DownloadUsing = (ListPreference) findPreference(getString(R.string.pref_data_downloadusing));
        OutputDirectory = findPreference(getString(R.string.pref_data_outputdirectory));
        ClearCache = findPreference(getString(R.string.pref_data_clearcache));
        ClearPaused = findPreference(getString(R.string.pref_data_clearpaused));
        DeleteHistory = findPreference(getString(R.string.pref_data_deletehistory));
        RestoreSettings = findPreference(getString(R.string.pref_data_restoresettings));

        String[] downloadusing = getResources().getStringArray(R.array.data);
        CharSequence[] entries = new CharSequence[downloadusing.length];
        for (int i = 0; i < downloadusing.length; i++) entries[i] = String.valueOf(i);
        DownloadUsing.setEntries(downloadusing);
        DownloadUsing.setEntryValues(entries);
        DownloadUsing.setValueIndex(Commons.Pref.mobiledata ? 1 : 0);
        DownloadUsing.setSummary(DownloadUsing.getEntry());
        DownloadUsing.setOnPreferenceChangeListener((preference, newValue) -> {
            ListPreference list = (ListPreference) preference;
            Commons.Pref.mobiledata = "1".equals(newValue);
            Commons.Pref.Save();
            preference.setSummary(list.getEntries()[Commons.Pref.mobiledata ? 1 : 0]);
            return true;
        });

//        setPreference(OutputDirectory, Commons.Directories.MUSIC.getAbsolutePath(), (p, val) -> {
//
//        });
        setPreference(OutputDirectory, "Has not been implemented yet", null);

        setPreference(ClearCache, null, p -> {
            if (getContext() == null) return false;
            double size = Commons.Directories.GetCacheSize();

            int i = 0;
            while (size >= 1000 && i < Commons.suffix.length) {
                size /= 1000f;
                i++;
            }

            new TimeoutDialog(getContext())
                    .setTitle("Clear Cache")
                    .setMessage(String.format("Cache can only be cleared if there are no downloads running. Doing so while a download is running will most likely result in failure.\nCache is %s %s", size, Commons.suffix[i]))
                    .setPositive("Delete", () -> {
                        Toast.makeText(getContext(), Commons.Directories.ClearCache() ? "Cache has been cleared" : "Cache was not cleared", Toast.LENGTH_LONG).show();
                        return true;
                    })
                    .setNegative("Cancel", null)
                    .setTimeout(5)
                    .show();
            return true;
        });

        setPreference(ClearPaused, "Has not been implemented yet", null);

        setPreference(DeleteHistory, null, p -> {
            if (getContext() == null) return false;
            double size = Commons.Directories.GetHistorySize();

            int i = 0;
            while (size >= 1000 && i < Commons.suffix.length) {
                size /= 1000f;
                i++;
            }

            new TimeoutDialog(getContext())
                    .setTitle("Delete Download History")
                    .setMessage(String.format("Download History will be permanently deleted. There is no way to recover the data.\nHistory is %s %s", size, Commons.suffix[i]))
                    .setPositive("Delete", () -> {
                        Toast.makeText(getContext(), Commons.Directories.DeleteHistory() ? "History has been cleared" : "History was not cleared", Toast.LENGTH_LONG).show();
                        return true;
                    })
                    .setNegative("Cancel", null)
                    .setTimeout(5)
                    .show();
            return true;
        });

        setPreference(RestoreSettings, null, p -> {
            if (Commons.Pref.restore) {
                if (getContext() != null) Toast.makeText(getContext(), "Restore Cancelled", Toast.LENGTH_LONG).show();
                p.setSummary("All preferences will be reverted to their initial state");
                Commons.Pref.restore = false;
            } else {
                p.setSummary("Settings will be restored after the app is restarted. Tap to cancel.");
                Commons.Pref.restore = true;
            }
            Commons.Pref.Save();
            return true;
        });
    }

    private void setPreference(Preference p, @Nullable String summary, Preference.OnPreferenceClickListener listener) {
        if (summary != null) p.setSummary(summary);
        p.setOnPreferenceClickListener(listener);
    }

}
