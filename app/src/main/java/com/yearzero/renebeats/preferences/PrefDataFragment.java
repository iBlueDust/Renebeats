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

    private Preference OutputDirectory, ClearCache, /*ClearPaused, */ClearLogs, DeleteHistory, RestoreSettings;

    public PrefDataFragment() {
    }

    @Override
    public void onCreatePreferences(Bundle savedInstanceState, String rootKey) {
        addPreferencesFromResource(R.xml.pref_data);
        OutputDirectory = findPreference(getString(R.string.pref_data_output));
        ClearCache = findPreference(getString(R.string.pref_data_clearcache));
//        ClearPaused = findPreference(getString(R.string.pref_data_clearpaused));
        ClearLogs = findPreference(getString(R.string.pref_data_clearlogs));
        DeleteHistory = findPreference(getString(R.string.pref_data_clearhistory));
        RestoreSettings = findPreference(getString(R.string.pref_data_restore));

        OutputDirectory.setSummary(Directories.getMusicPath());
        OutputDirectory.setOnPreferenceClickListener(p -> {
            if (PrefDataFragment.this.getActivity() == null) return false;
            StorageChooser dialog = new StorageChooser.Builder()
                    .withActivity(PrefDataFragment.this.getActivity())
                    .withFragmentManager(PrefDataFragment.this.getActivity().getFragmentManager())
                    .withMemoryBar(true)
                    .allowAddFolder(true)
                    .allowCustomPath(true)
                    .setDialogTitle(getString(R.string.pref_data_output_dialog))
                    .setType(StorageChooser.DIRECTORY_CHOOSER)
                    .build();

            dialog.setOnSelectListener(path -> {
                Directories.setOutputDir(path);
                if (PrefDataFragment.this.getView() != null)
                    Snackbar.make(PrefDataFragment.this.getView(), getString(R.string.pref_data_output_success), Snackbar.LENGTH_LONG).show();
                OutputDirectory.setSummary(Directories.getMusicPath());
            });
            dialog.show();
            return true;
        });

        ClearCache.setOnPreferenceClickListener(p -> {
            if (getContext() == null) return false;
            new TimeoutDialog(getContext())
                    .setTitle(getString(R.string.pref_data_clearcache_dialog))
                    .setMessage(String.format(getString(R.string.pref_data_clearcache_dialog_msg), Commons.FormatBytes(Directories.getCacheSize())))
                    .setPositive(getString(R.string.delete), () -> {
                        errorLog(Directories.clearCache(), getString(R.string.pref_data_clearcache_success), getString(R.string.pref_data_clearcache_failed), "Error has been logged", "Failed to log error");
                        return true;
                    })
                    .setNegative(getString(R.string.cancel), null)
                    .setTimeout(5)
                    .show();
            return true;
        });

//        ClearPaused.setSummary("Has not been implemented yet");

        ClearLogs.setOnPreferenceClickListener(p -> {
            if (getContext() == null) return false;
            else new TimeoutDialog(getContext())
                        .setTitle(getString(R.string.pref_data_clearlogs_dialog))
                        .setMessage(String.format(getString(R.string.pref_data_clearlogs_dialog_msg), Commons.FormatBytes(Directories.getLogsSize())))
                        .setPositive(getString(R.string.clear_all), () -> {
                            errorLog(Directories.clearLogs(), getString(R.string.pref_data_clearlogs_success), getString(R.string.pref_data_clearlogs_failed), "Error logged. Quite ironic eh?", "Failed to log error. Huh.");
                            return true;
                        })
                        .setNegative(getString(R.string.cancel), null)
                        .setTimeout(5)
                        .show();
            return true;
        });

        DeleteHistory.setOnPreferenceClickListener(p -> {
            if (getContext() == null) return false;
            new TimeoutDialog(getContext())
                    .setTitle(getString(R.string.pref_data_clearhistory_dialog))
                    .setMessage(String.format(getString(R.string.pref_data_clearhistory_dialog_msg), Commons.FormatBytes(Directories.getHistorySize())))
                    .setPositive(getString(R.string.delete), () -> {
                        errorLog(Directories.deleteHistory(), getString(R.string.pref_data_clearhistory_success), getString(R.string.pref_data_clearhistory_failed), "Error has been logged", "Failed to log error");
                        return true;
                    })
                    .setNegative(getString(R.string.cancel), null)
                    .setTimeout(5)
                    .show();
            return true;
        });

        RestoreSettings.setOnPreferenceClickListener(p -> {
            if (Preferences.getRestore()) {
                if (getContext() != null) Toast.makeText(getContext(), getString(R.string.restore_cancelled), Toast.LENGTH_LONG).show();
                p.setSummary(R.string.pref_data_restore_inactive);
                Preferences.setRestore(false);
            } else {
                p.setSummary(R.string.pref_data_restore_active);
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
            snackbar.setAction(getString(R.string.save_log), v ->
                    Snackbar.make(getView(), Commons.LogException(exception) ? logSuccess : logFailed, Snackbar.LENGTH_LONG)
                            .show());
        }
        snackbar.show();
    }
}
