package com.yearzero.renebeats.preferences;

import android.os.Bundle;
import android.widget.Toast;

import androidx.preference.Preference;

import com.codekidlabs.storagechooser.StorageChooser;
import com.google.android.material.snackbar.Snackbar;
import com.yearzero.renebeats.Commons;
import com.yearzero.renebeats.Directories;
import com.yearzero.renebeats.R;

import java.io.IOException;

import de.mrapp.android.preference.activity.PreferenceFragment;

public class PrefDataFragment extends PreferenceFragment {

    private static final String TAG = "PrefDataFragment";

    private Preference OutputDirectory, ClearCache, ClearPaused, ClearLogs, DeleteHistory, RestoreSettings;

    public PrefDataFragment() {
    }

    @Override
    public void onCreatePreferences(Bundle savedInstanceState, String rootKey) {
        addPreferencesFromResource(R.xml.pref_data);
        //TODO: Move download using to general
        //TODO: Rename Guess Mode to Autofill Options
        OutputDirectory = findPreference(getString(R.string.pref_data_outputdirectory));
        ClearCache = findPreference(getString(R.string.pref_data_clearcache));
        ClearPaused = findPreference(getString(R.string.pref_data_clearpaused));
        ClearLogs = findPreference(getString(R.string.pref_data_clearlogs));
        DeleteHistory = findPreference(getString(R.string.pref_data_deletehistory));
        RestoreSettings = findPreference(getString(R.string.pref_data_restoresettings));

        OutputDirectory.setSummary(Directories.getMusicPath());
        OutputDirectory.setOnPreferenceClickListener(p -> {
            if (PrefDataFragment.this.getActivity() == null) return false;
            StorageChooser dialog = new StorageChooser.Builder()
                    .withActivity(PrefDataFragment.this.getActivity())
                    .withFragmentManager(PrefDataFragment.this.getActivity().getFragmentManager())
                    .withMemoryBar(true)
                    .allowAddFolder(true)
                    .allowCustomPath(true)
                    .setDialogTitle("All downloads will be saved in...")
                    .setType(StorageChooser.DIRECTORY_CHOOSER)
                    .build();

            dialog.setOnSelectListener(path -> {
                Directories.setOutputDir(path);
                if (PrefDataFragment.this.getView() != null)
                    Snackbar.make(PrefDataFragment.this.getView(), "Output Folder has been changed", Snackbar.LENGTH_LONG).show();
                OutputDirectory.setSummary(Directories.getMusicPath());
            });
            dialog.show();
            return true;
        });

        ClearCache.setOnPreferenceClickListener(p -> {
            if (getContext() == null) return false;
            new TimeoutDialog(getContext())
                    .setTitle("Clear Cache")
                    .setMessage(String.format("Cache can only be cleared if there are no downloads running. Doing so while a download is running will most likely result in failure.\nCache is %s", Commons.FormatBytes(Directories.getCacheSize())))
                    .setPositive("Delete", () -> {
                        errorLog(Directories.clearCache(), "Cache has been cleared", "Failed to clear cache", "Error has been logged", "Failed to log error");
                        return true;
                    })
                    .setNegative("Cancel", null)
                    .setTimeout(5)
                    .show();
            return true;
        });

        ClearPaused.setSummary("Has not been implemented yet");

        ClearLogs.setOnPreferenceClickListener(p -> {
            if (getContext() == null) return false;
            else new TimeoutDialog(getContext())
                        .setTitle("Clear All Error Logs?")
                        .setMessage(String.format("Logs are currently taking up %s", Commons.FormatBytes(Directories.getLogsSize())))
                        .setPositive("Clear All", () -> {
                            errorLog(Directories.clearLogs(), "Cleared all logs", "Failed to clear logs", "Error logged. Quite ironic eh?", "Failed to log error. Huh.");
                            return true;
                        })
                        .setNegative("Cancel", null)
                        .setTimeout(5)
                        .show();
            return true;
        });

        DeleteHistory.setOnPreferenceClickListener(p -> {
            if (getContext() == null) return false;
            new TimeoutDialog(getContext())
                    .setTitle("Delete Download History")
                    .setMessage(String.format("Download History will be permanently deleted. There is no way to recover the data.\nHistory is %s", Commons.FormatBytes(Directories.getHistorySize())))
                    .setPositive("Delete", () -> {
                        errorLog(Directories.deleteHistory(), "History has been cleared", "Failed to clear history", "Error has been logged", "Failed to log error");
                        return true;
                    })
                    .setNegative("Cancel", null)
                    .setTimeout(5)
                    .show();
            return true;
        });

        RestoreSettings.setOnPreferenceClickListener(p -> {
            if (Preferences.getRestore()) {
                if (getContext() != null) Toast.makeText(getContext(), "Restore Cancelled", Toast.LENGTH_LONG).show();
                p.setSummary("All preferences will be reverted to their initial state");
                Preferences.setRestore(false);
            } else {
                p.setSummary("Settings will be restored after the app is restarted. Tap to cancel.");
                Preferences.setRestore(true);
            }
            Preferences.save();
            return true;
        });
    }

    private void errorLog(IOException exception, String success, String failed, String logSuccess, String logFailed) {
        if (getView() == null) return;
        Snackbar snackbar;
        if (exception == null)
            snackbar = Snackbar.make(getView(), success, Snackbar.LENGTH_LONG);
        else {
            snackbar = Snackbar.make(getView(), failed, Snackbar.LENGTH_LONG);
            snackbar.setAction("save Log", v ->
                    Snackbar.make(getView(), Commons.LogException(exception) ? logSuccess : logFailed, Snackbar.LENGTH_LONG)
                            .show());
        }

        snackbar.show();
    }
}
