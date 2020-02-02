package com.yearzero.renebeats.preferences;

import android.os.Bundle;
import android.util.Log;

import androidx.preference.CheckBoxPreference;
import androidx.preference.ListPreference;

import com.yearzero.renebeats.Commons;
import com.yearzero.renebeats.R;
import com.yearzero.renebeats.preferences.enums.GuesserMode;
import com.yearzero.renebeats.preferences.enums.OverwriteMode;
import com.yearzero.renebeats.preferences.enums.ProcessSpeed;

import org.apache.commons.lang3.ArrayUtils;

import de.mrapp.android.preference.activity.PreferenceFragment;

public class PrefGeneralFragment extends PreferenceFragment {
    private static final String TAG = "Pref.General";

    private ListPreference DownloadUsing, QueryAmount, /*ConcurrentDownloads, */GuesserModes;
    private CheckBoxPreference AlwaysLogFailed;

    private ListPreference Format, Speed, Bitrate, Overwrite, FileFormat;
    private CheckBoxPreference Normalize;

    public PrefGeneralFragment() { }

    @Override
    public void onCreatePreferences(Bundle savedInstanceState, String rootKey) {
        addPreferencesFromResource(R.xml.pref_general);

        DownloadUsing = (ListPreference) findPreference(getString(R.string.pref_data_using));
        QueryAmount = (ListPreference) findPreference(getString(R.string.pref_general_query));
        AlwaysLogFailed = (CheckBoxPreference) findPreference(getString(R.string.pref_general_autolog));
        //        ConcurrentDownloads = (ListPreference) findPreference(getString(R.string.pref_defaults_concurrent));
        GuesserModes = (ListPreference) findPreference(getString(R.string.pref_guesser));

        Format = (ListPreference) findPreference(getString(R.string.pref_general_format));
        Speed = (ListPreference) findPreference(getString(R.string.pref_general_speed));
        Bitrate = (ListPreference) findPreference(getString(R.string.pref_general_bitrate));
        Overwrite = (ListPreference) findPreference(getString(R.string.pref_general_overwrite));
        FileFormat = (ListPreference) findPreference(getString(R.string.pref_general_first));

        Normalize = (CheckBoxPreference) findPreference(getString(R.string.pref_general_normalize));

        String[] downloadUsing = getResources().getStringArray(R.array.data);
        CharSequence[] entries = new CharSequence[downloadUsing.length];
        for (int i = 0; i < downloadUsing.length; i++) entries[i] = String.valueOf(i);
        DownloadUsing.setEntries(downloadUsing);
        DownloadUsing.setEntryValues(entries);
        DownloadUsing.setValueIndex(Preferences.getMobiledata() ? 1 : 0);
        DownloadUsing.setSummary(DownloadUsing.getEntry());
        DownloadUsing.setOnPreferenceChangeListener((preference, newValue) -> {
            ListPreference list = (ListPreference) preference;
            Preferences.setMobiledata("1".equals(newValue));
            Preferences.save();
            Commons.setDownloadNetworkType(Preferences.getMobiledata());

            preference.setSummary(list.getEntries()[Preferences.getMobiledata() ? 1 : 0]);
            return true;
        });

        QueryAmount.setValue(String.valueOf(Preferences.getQuery_amount()));
        UpdateQueryAmtSummary();
        QueryAmount.setOnPreferenceChangeListener((pref, val) -> {
            try {
                Preferences.setQuery_amount(Short.valueOf((String) val));
                Preferences.save();
                UpdateQueryAmtSummary();
                return true;
            } catch (ClassCastException | NumberFormatException e) {
                Log.e(TAG, "QueryAmount received a non-short string newValue");
                return false;
            }
        });

        AlwaysLogFailed.setChecked(Preferences.getAlways_log_failed());
        AlwaysLogFailed.setOnPreferenceChangeListener((pref, newValue) -> {
            if (newValue instanceof Boolean) {
                Preferences.setAlways_log_failed((boolean) newValue);
                Preferences.save();
                return true;
            } else {
                Log.e(TAG, "AlwaysLogFailed received a non-short string newValue");
                return false;
            }
        });

        String val0 = Preferences.getGuesser_mode().getValue();
        DefaultListPrefRoutine(GuesserModes, val0, GuesserModes.getEntries()[ArrayUtils.indexOf(GuesserModes.getEntryValues(), val0)], key -> {
            Preferences.setGuesser_mode(GuesserMode.fromValue(key));
            Preferences.save();

            CharSequence[] ent = GuesserModes.getEntries();
            CharSequence[] entryVal = GuesserModes.getEntryValues();
            int index = ArrayUtils.indexOf(entryVal, key);
            return ent[index == ArrayUtils.INDEX_NOT_FOUND ? ArrayUtils.indexOf(entryVal, GuesserMode.getDefault().getValue()) : index];
        });

        DefaultListPrefRoutine(Format, Preferences.getFormat().toLowerCase(), Preferences.getFormat().toUpperCase(), key -> {
            Preferences.setFormat(key.toLowerCase());
            Preferences.save();

            return key.toUpperCase();
        });

        String val1 = Preferences.getProcess_speed().getValue();
        DefaultListPrefRoutine(Speed, val1, Speed.getEntries()[ArrayUtils.indexOf(Speed.getEntryValues(), val1)], key -> {
            Preferences.setProcess_speed(ProcessSpeed.fromValue(key));
            Preferences.save();

//            CharSequence[] entries = Speed.getEntries();
//            CharSequence[] entryVal = Speed.getEntryValues();
//            int index = ArrayUtils.indexOf(entryVal, key);
            return Speed.getEntries()[ArrayUtils.indexOf(Speed.getEntryValues(), key)];
//            return entries[index == ArrayUtils.INDEX_NOT_FOUND ? ArrayUtils.indexOf(entryVal, ProcessSpeed.getDefault().getValue()) : index];
        });

        String[] en = new String[Preferences.getBITRATES().length];
        String[] keys = new String[Preferences.getBITRATES().length];
        for (int i = 0; i < Preferences.getBITRATES().length; i++) {
            en[i] = Preferences.getBITRATES()[i] + " kbps";
            keys[i] = String.valueOf(Preferences.getBITRATES()[i]);
        }
        Bitrate.setEntries(en);
        Bitrate.setEntryValues(keys);
        DefaultListPrefRoutine(Bitrate, String.valueOf(Preferences.getBitrate()), String.format(Commons.getLocale(), getString(R.string.kbps), Preferences.getBitrate()), key -> {
            try {
                Preferences.setBitrate(Short.valueOf(key));
                Preferences.save();
            } catch(NumberFormatException e) {
                Log.w(TAG, "Invalid bitrate value");
            }

            return String.format(Commons.getLocale(), getString(R.string.kbps), Preferences.getBitrate());
        });

        String val2 = Preferences.getOverwrite().getValue();
        DefaultListPrefRoutine(Overwrite, val2, Overwrite.getEntries()[ArrayUtils.indexOf(Overwrite.getEntryValues(), val2)], key -> {
            Preferences.setOverwrite(OverwriteMode.fromValue(key));
            Preferences.save();

//            CharSequence[] entries = Overwrite.getEntries();
//            CharSequence[] entryVal = Overwrite.getEntryValues();
//            int index = ArrayUtils.indexOf(entryVal, key);
            return Overwrite.getEntries()[ArrayUtils.indexOf(Overwrite.getEntryValues(), key)];
//            return entries[index == ArrayUtils.INDEX_NOT_FOUND ? ArrayUtils.indexOf(entryVal, OverwriteMode.getDefault().getValue()) : index];
        });

//        CharSequence[] seq = FileFormat.getEntries();
        CharSequence[] seqV = FileFormat.getEntryValues();
        DefaultListPrefRoutine(FileFormat, (String) seqV[Preferences.getArtist_first() ? 0 : 1], Preferences.getArtist_first() ? getString(R.string.pref_general_first_artist) : getString(R.string.pref_general_first_titl), key -> {
            Preferences.setArtist_first(key.equals("artist first"));
            Preferences.save();

            return FileFormat.getEntries()[Preferences.getArtist_first() ? 0 : 1];
        });

        Normalize.setChecked(Preferences.getNormalize());
        Normalize.setOnPreferenceChangeListener((pref, newValue) -> {
            if (newValue instanceof Boolean) {
                Preferences.setNormalize((boolean) newValue);
                Preferences.save();
                return true;
            } else return false;
        });
    }

    private interface DefaultPrefInterface {
        CharSequence implement(String key);
    }

    private void DefaultListPrefRoutine(ListPreference pref, String init_value, CharSequence init_summary, DefaultPrefInterface callback) {
        pref.setValue(init_value);
        pref.setSummary(init_summary);
        pref.setOnPreferenceChangeListener((p, newVal) -> {
            if (newVal instanceof String) {
                pref.setSummary(callback.implement((String) newVal));
                return true;
            } else return false;
        });
    }

    private void DefaultListPrefRoutine(ListPreference pref, int init_index, CharSequence init_summary, DefaultPrefInterface callback) {
        pref.setValueIndex(init_index);
        pref.setSummary(init_summary);
        pref.setOnPreferenceChangeListener((p, newVal) -> {
            if (newVal instanceof String) {
                pref.setSummary(callback.implement((String) newVal));
                return true;
            } else return false;
        });
    }

    private void UpdateQueryAmtSummary() {
        QueryAmount.setSummary(String.format(Commons.getLocale(), getString(R.string.pref_general_query_prefix), Preferences.getQuery_amount()));
    }

}
