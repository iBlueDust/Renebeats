package com.yearzero.renebeats.download;

import android.os.AsyncTask;
import android.util.Log;
import android.util.SparseArray;

import androidx.annotation.Nullable;

import com.esotericsoftware.kryo.Kryo;
import com.esotericsoftware.kryo.KryoException;
import com.esotericsoftware.kryo.io.Input;
import com.esotericsoftware.kryo.io.Output;
import com.yearzero.renebeats.Directories;

import org.apache.commons.lang3.ArrayUtils;
import org.apache.commons.lang3.ObjectUtils;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Calendar;
import java.util.Date;
import java.util.List;
import java.util.Locale;

import javax.annotation.Nonnegative;

public class History {
    private static final String EXTENSION = "hist";
    private static final Kryo kryo = new Kryo();

    static {
        kryo.register(HistoryLog.class, 100);
        kryo.register(HistoryLog[].class, 101);
        kryo.register(Date.class, 200);
        kryo.register(String[].class, 301);
    }

    public interface Callback<T, U> {
        void onComplete(U data);
        void onError(@Nullable T current, Exception e);
    }

    static int dateCodeNow(Date date) {
        Calendar cal = Calendar.getInstance();
        cal.setTime(date);
        return cal.get(Calendar.YEAR) * 12 + cal.get(Calendar.MONTH);
    }

    static int dateCodeFromName(String filename) throws IllegalArgumentException {
        short year = (short) Integer.parseInt(filename.substring(0, filename.indexOf('-')));
        short month = Short.parseShort(filename.lastIndexOf('.') < 0 ? filename.substring(filename.indexOf('-')) : filename.substring(filename.indexOf('-') + 1, filename.lastIndexOf('.')));
        if (month < 0 || month > 12) throw new IllegalArgumentException("month value is negative or more than 11");
        return year * 12 + month;
    }

    public static int[] listHist() {
        File[] raw = Directories.getHISTORY().listFiles((dir, name) -> name.endsWith('.' + EXTENSION));
        int[] cooked = new int[raw.length];
        for (int i = 0; i < raw.length; i++) {
            try {
                cooked[i] = dateCodeFromName(raw[i].getName());
            } catch (IllegalArgumentException e) {
                cooked[i] = -1;
            }
        }
        return cooked;
    }

    static File getHistFile(int compressed) {
        return getHistFile((short) (compressed >> 4), (byte) (compressed & 0xF));
    }

    static File getHistFile(@Nonnegative short year, byte month) {
        return new File(Directories.getHISTORY(), String.format(Locale.ENGLISH, "%d-%d.%s", year, month + 1, EXTENSION));
    }

    public static class RetrieveRangeTask extends AsyncTask<Integer, Void, List<HistoryLog[]>> {
        protected Exception exception;
        protected Callback<Integer, List<HistoryLog[]>> callback;
        protected ArrayList<SparseArray<Exception>> runtimeException = new ArrayList<>();
        protected ArrayList<HistoryLog[]> result = new ArrayList<>();

        // ENCODING:
        // MS 16b unsigned -> Start of Range
        // LS 16b unsigned -> End of Range
        //
        // 16b unsigned = Year * 12 + Month
        // (Month starts from 0-11)
        // (Precision = MONTH)
        // Using   signed 16b will allow 2730y7m
        // Using unsigned 16b will allow 5461y4m <----

        @Override
        protected List<HistoryLog[]> doInBackground(Integer... ranges) {
            // 0x0000_FFFF is the maximum range
            for (Integer range : ranges == null ? new Integer[]{0x0000_FFFF} : ranges) {
                if (range == null) continue;
                int start = (range & 0xFFFF_0000) >>> 16;
                int end = range & 0x0000_FFFF;

                byte startm = (byte) (start % 12);
                byte endm = (byte) (end % 12);

                short starty = (short) (start / 12);
                short endy = (short) (end / 12);

                SparseArray<Exception> ex = new SparseArray<>();
                runtimeException.add(ex);

                for (; starty <= endy; starty++) {
                    for (; starty < endy || startm <= endm; startm++) {
                        File file = getHistFile(starty, startm);
                        if (file.exists())
                            try {
                                Input input = new Input(new FileInputStream(file));
                                result.add(kryo.readObject(input, HistoryLog[].class));
                                input.close();
                            } catch (IOException | KryoException e) {
                                ex.append(starty * 12 + startm, e);
                                if (callback != null) callback.onError(startm * 12 + starty, e);
                            }
                    }
                }
            }
            if (callback != null) callback.onComplete(result);
            return result;
        }

        public RetrieveRangeTask setCallback(Callback<Integer, List<HistoryLog[]>> callback) {
            this.callback = callback;
            return this;
        }

        public Exception getException() {
            return exception;
        }

        public List<SparseArray<Exception>> getRuntimeException() {
            return runtimeException;
        }

        public ArrayList<HistoryLog[]> getResult() {
            return result;
        }
    }

    public static class RetrieveTask extends AsyncTask<Integer, Void, SparseArray<HistoryLog[]>> {
        private static final String TAG = "History.RetrieveTask";

        Callback<Integer, SparseArray<HistoryLog[]>> callback;
        ArrayList<SparseArray<Exception>> runtimeException = new ArrayList<>();
        SparseArray<HistoryLog[]> result = new SparseArray<>();

        // ENCODING:
        // High Short -> Start of Range
        // Low  Short -> End of Range
        //
        // Short = Year * 12 + Month
        // (Month starts from 0-11)
        // (Precision = MONTH)

        @Override
        @Nullable
        protected SparseArray<HistoryLog[]> doInBackground(Integer... times) {
            if (times == null) {
                Log.e(TAG, "No data input");
                return null;
            }

            for (Integer t : times) {
                if (t == null) continue;
                short year = (short) (t >> 4);
                byte month = (byte) (t & 0xF);

                SparseArray<Exception> ex = new SparseArray<>();
                runtimeException.add(ex);

                File file = getHistFile(year, month);

                try {
                    Input input = new Input(new FileInputStream(file));
                    result.append(t, kryo.readObject(input, HistoryLog[].class));
                    input.close();
                } catch (IOException | KryoException e) {
                    ex.append(t, e);
                    if (callback != null) callback.onError(t, e);
                }
            }
            return result;
        }

        @Override
        protected void onPostExecute(SparseArray<HistoryLog[]> sparseArray) {
            if (callback != null) callback.onComplete(result);
            super.onPostExecute(sparseArray);
        }

        public RetrieveTask setCallback(Callback<Integer, SparseArray<HistoryLog[]>> callback) {
            this.callback = callback;
            return this;
        }

        public List<SparseArray<Exception>> getRuntimeException() {
            return runtimeException;
        }

        public SparseArray<HistoryLog[]> getResult() {
            return result;
        }
    }

    public static class StoreTask extends AsyncTask<HistoryLog, Void, SparseArray<Exception>> {
        private static final String TAG = "History.StoreTask";

        protected Callback<HistoryLog[], SparseArray<Exception>> callback;

        // ENCODING:
        // High Short -> Start of Range
        // Low  Short -> End of Range
        //
        // Short = Year * 12 + Month
        // (Month starts from 0-11)
        // (Precision = MONTH)

        @Override
        protected SparseArray<Exception> doInBackground(HistoryLog... data) {
            if (data == null) {
                Log.w(TAG, "No data input");
                return null;
            }

            SparseArray<Exception> exceptions = new SparseArray<>();
            SparseArray<ArrayList<HistoryLog>> categorized = new SparseArray<>();
            for (int i = 0; i < data.length; i++) {
                HistoryLog d = data[i];

                // If date of assignment is missing, insert IllegalArgumentException into
                // exception SparseArray but set the key as 0x8000_0000 + index.
                // Therefore, the indexes will never reach 0 as MIN_VALUE + MAX_VALUE = -1 (0xFFFF_FFFF)
                if (d.getAssigned() == null) {
                    String report = "Data index " + i + " has no date of assignment";
                    Log.w(TAG, report);
                    exceptions.append(Integer.MIN_VALUE + i, new IllegalArgumentException(report));
                    continue;
                }

                int extract = dateCodeNow(d.getAssigned());
                ArrayList<HistoryLog> list = categorized.get(extract);
                if (list == null) {
                    list = new ArrayList<>();
                    categorized.put(extract, list);
                }
                list.add(d);
            }

            for (int i = 0; i < categorized.size(); i++) {
                HistoryLog[] array = categorized.valueAt(i).toArray(new HistoryLog[0]);
                Arrays.sort(array, (a, b) -> {
                    assert b.getAssigned() != null;
                    return b.getAssigned().compareTo(a.getAssigned());
                });
                int key = categorized.keyAt(i);
                File file = getHistFile(key);
                if (!(file.getParentFile().exists() || file.getParentFile().mkdirs())) Log.e(TAG, "Failed to create parent directory");

                try {
                    if (!(file.exists() || file.createNewFile())) Log.e(TAG, "Failed to create file");
                    Output output = new Output(new FileOutputStream(file));
                    kryo.writeObject(output, array);
                    output.close();
                } catch (IOException | KryoException e) {
                    e.printStackTrace();
                    exceptions.append(key, e);
                    if (callback != null) callback.onError(array, e);
                }
            }
            return exceptions;
        }

        @Override
        protected void onPostExecute(SparseArray<Exception> exceptions) {
            if (callback != null) callback.onComplete(exceptions);
            super.onPostExecute(exceptions);
        }

        public StoreTask setCallback(Callback<HistoryLog[], SparseArray<Exception>> callback) {
            this.callback = callback;
            return this;
        }
    }

    public static class AppendNowTask extends AsyncTask<Download, Void, Exception> {

        protected Callback<ObjectUtils.Null, Exception> callback;

        @Override
        protected Exception doInBackground(Download... downloads) {
            int tag = dateCodeNow(new Date());

            SparseArray<HistoryLog[]> retrieve = new RetrieveTask().doInBackground(tag);
            HistoryLog[] data = new HistoryLog[downloads.length];

            for (int i = 0; i < downloads.length; i++)
                data[i] = HistoryLog.generate(downloads[i]);

            if (retrieve != null && retrieve.size() > 0 && retrieve.get(0) != null) data = ArrayUtils.addAll(data, retrieve.get(0));

            SparseArray<Exception> exs = new StoreTask().doInBackground(data);
            if (exs == null || exs.get(tag) == null) {
                if (callback != null) callback.onComplete(null);
                return null;
            } else {
                Exception e = exs.get(tag);
                if (callback != null) callback.onError(null, e);
                return e;
            }
        }

        public AppendNowTask setCallback(Callback<ObjectUtils.Null, Exception> callback) {
            this.callback = callback;
            return this;
        }
    }
}
