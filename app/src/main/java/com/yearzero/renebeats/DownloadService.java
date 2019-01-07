package com.yearzero.renebeats;

import android.app.Service;
import android.content.Intent;
import android.os.Binder;
import android.os.IBinder;
import android.util.Log;
import android.util.SparseArray;

import com.tonyodev.fetch2.EnqueueAction;
import com.tonyodev.fetch2.Error;
import com.tonyodev.fetch2.FetchListener;
import com.tonyodev.fetch2.NetworkType;
import com.tonyodev.fetch2.Priority;
import com.tonyodev.fetch2.Request;
import com.tonyodev.fetch2core.DownloadBlock;

import org.cmc.music.common.ID3WriteException;
import org.cmc.music.metadata.MusicMetadata;
import org.cmc.music.metadata.MusicMetadataSet;
import org.cmc.music.myid3.MyID3;
import org.jetbrains.annotations.NotNull;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.Date;
import java.util.LinkedList;
import java.util.List;
import java.util.Locale;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.localbroadcastmanager.content.LocalBroadcastManager;
import cafe.adriel.androidaudioconverter.model.AudioFormat;

public class DownloadService extends Service {

    public static final String TAG = "DownloadService";
    public static final String MISSING_IN_MAP = "Request Queue: A Download in Fetch is not in HashMap (IGNORED)";
    //    public static final String DONT_LOAD = "dont_load";

    // TODO: Check Runtime post-disaster

    private FetchListener fetchListener = new FetchListener() {
        @Override
        public void onAdded(@NotNull com.tonyodev.fetch2.Download download) {
            UpdateProgress(download, "onAdded", null);
        }

        @Override
        public void onQueued(@NotNull com.tonyodev.fetch2.Download download, boolean b) {
            UpdateProgress(download, "onQueued", Status.Download.QUEUED);
        }

        @Override
        public void onWaitingNetwork(@NotNull com.tonyodev.fetch2.Download download) {
            UpdateProgress(download, "onWaitingNetwork", Status.Download.NETWORK_PENDING);
        }

        @Override
        public void onCompleted(@NotNull com.tonyodev.fetch2.Download download) {
            Download args = downloadMap.get(download.getRequest().getId());
            if (args == null) {
                Log.w(TAG, "onCompleted" + MISSING_IN_MAP);
                return;
            }

            args.status.download = Status.Download.COMPLETE;
            convertQueue.add(args);
            downloadMap.remove(download.getId());
            DownloadService.this.onProgress(1L, 1L, false, args);
            Convert();
        }

        @Override
        public void onError(@NotNull com.tonyodev.fetch2.Download download, @NotNull Error error, @org.jetbrains.annotations.Nullable Throwable throwable) {
            String msg = "Download Error" + (throwable == null ? "" : throwable.getMessage());
            Log.e(TAG, msg);

            Download args = downloadMap.get(download.getRequest().getId());
            if (args == null) {
                Log.w(TAG, "onError: " + MISSING_IN_MAP);
                return;
            }

            args.status.download = Status.Download.FAILED;
            completed.add(args);
            downloadMap.remove(download.getId());
            onFinish(args, new ServiceException(msg).setStatus(args.status).setPayload(new RuntimeException(msg)));
        }

        @Override
        public void onDownloadBlockUpdated(@NotNull com.tonyodev.fetch2.Download download, @NotNull DownloadBlock downloadBlock, int i) { }


        @Override
        public void onStarted(@NotNull com.tonyodev.fetch2.Download download, @NotNull List<? extends DownloadBlock> list, int i) {
            UpdateProgress(download, "onStarted", Status.Download.RUNNING);
        }

        @Override
        public void onProgress(@NotNull com.tonyodev.fetch2.Download download, long etaMilli, long Bps) {
            UpdateProgress(download, "onProgress", Status.Download.RUNNING);
        }

        @Override
        public void onPaused(@NotNull com.tonyodev.fetch2.Download download) {
            Download args = downloadMap.get(download.getRequest().getId());
            if (args == null) {
                Log.w(TAG, "onPaused: " + MISSING_IN_MAP);
                return;
            }

            UpdateMaps(download, args);
            DownloadService.this.onProgress(download.getDownloaded(), download.getTotal(), false, args);
        }

        @Override
        public void onResumed(@NotNull com.tonyodev.fetch2.Download download) {
            UpdateProgress(download, "onResumed", null);
        }

        @Override
        public void onCancelled(@NotNull com.tonyodev.fetch2.Download download) {
            UpdateCancelled(download, "onCancelled");
        }

        @Override
        public void onRemoved(@NotNull com.tonyodev.fetch2.Download download) {
            downloadMap.remove(download.getId());
            UpdateCancelled(download, "onRemoved");
        }

        @Override
        public void onDeleted(@NotNull com.tonyodev.fetch2.Download download) {
            UpdateCancelled(download, "onDeleted");
        }
    };

    public interface ClientCallbacks {
        void onProgress(Download args, long progress, long max, boolean indeterminate);

        void onDone(Download args, boolean successful, Exception e);

        void onWarn(Download args, String type);
    }

    private LocalBinder binder = new LocalBinder();

    private boolean loaded;
    private AndroidAudioConverter converter;
    private ArrayList<ClientCallbacks> callbacks = new ArrayList<>();
    private LinkedList<Download> convertQueue = new LinkedList<>();
    private Download convertProgress;
    private ArrayList<Download> completed = new ArrayList<>();
    private SparseArray<Download> downloadMap = new SparseArray<>();

    @Nullable
    @Override
    public IBinder onBind(Intent intent) {
        return binder;
    }

    public void addCallbacks(ClientCallbacks callbacks) {
        if (callbacks == null) return;
        this.callbacks.add(callbacks);
    }

    public boolean removeCallbacks(ClientCallbacks callbacks) {
        return this.callbacks.remove(callbacks);
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        Download current = null;
        if (intent != null) {
            int reqflag = intent.getIntExtra(Commons.ARGS.REQUEST, 0);
            if (reqflag != 0) {
                Intent req = new Intent(TAG);

                req.putExtras(intent);

                Sanitize();
                int reqid = intent.getIntExtra(Commons.ARGS.REQ_ID, Integer.MIN_VALUE);
                if (reqid != Integer.MIN_VALUE) req.putExtra(Commons.ARGS.REQ_ID, reqid);

                if ((reqflag & Commons.ARGS.FLAG_QUEUE) == Commons.ARGS.FLAG_QUEUE)
                    req.putExtra(Commons.ARGS.REQ_QUEUE, getQueue().toArray(new Download[0]));

                if ((reqflag & Commons.ARGS.FLAG_RUNNING) == Commons.ARGS.FLAG_RUNNING)
                    req.putExtra(Commons.ARGS.REQ_RUNNING, getRunning().toArray(new Download[0]));

                if ((reqflag & Commons.ARGS.FLAG_COMPLETED) == Commons.ARGS.FLAG_COMPLETED)
                    req.putExtra(Commons.ARGS.REQ_COMPLETED, getCompleted().toArray(new Download[0]));

                LocalBroadcastManager.getInstance(DownloadService.this).sendBroadcast(req);

                if (CheckQueues()) stop();
                return START_STICKY;
            }

            if ((intent.getBooleanExtra(Commons.ARGS.LOAD, true) && !loaded)) {
                Download[] pkg = Commons.LoadQueue();
                if (pkg == null)
                    Log.e(TAG, "Failed to load queue");
                else {
                    loaded = true;
                    LoadPackage(pkg);
                }
            }

            try {
                current = (Download) intent.getSerializableExtra(Commons.ARGS.DATA);
            } catch (ClassCastException e) {
                e.printStackTrace();
                if (CheckQueues()) {
                    stop();
                    return START_NOT_STICKY;
                }
            }

            if (current == null) {
                if (CheckQueues()) {
                    stop();
                    return START_NOT_STICKY;
                }
                return START_STICKY;
            }

            Exception v = Validate(current);
            if (v != null) {
                onFinish(current, false, v);
                Log.e(TAG, "Invalid Argument: " + v.getMessage());
                return START_STICKY;
            }

            current.extractFromSparse();

            if (current.url != null) {
                current.status.download = Status.Download.QUEUED;
                current.status.convert = null;
                current.status.metadata = null;
                onProgress(0, 0, true, current);
                Download(current);
            }
        }
        return START_STICKY;
    }

    private void LoadPackage(Download[] pkg) {
        SparseArray<Download> paused = new SparseArray<>();

        for (Download d : pkg) {
            if (d.status.download == null) continue;
            switch (d.status.download) {
                case QUEUED:
                    Download(d);
                    break;
                case RUNNING:
                case PAUSED:
                    paused.append(d.id, d);
                    break;

                case CANCELLED:
                    this.completed.add(d);
                    // TODO: Replace "this.completed.add(d);" with "Commons.History.add(d);"
                    break;
                default:
                    if (d.status.convert == null) continue;
                    switch (d.status.convert) {
                        case RUNNING:
                        case PAUSED:
                            d.status.convert = Status.Convert.QUEUED;
                            if (d.conv != null) {
                                File file = new File(Commons.Directories.BIN, d.conv);
                                if (file.exists() && !file.delete())
                                    Log.w(TAG, "LoadPackage // Found Paused/Running in conversion Download and its conv file, yet failed to delete\nChanged status to QUEUED");
                            }
                        case QUEUED:
                            if (d.down == null || !new File(d.down).exists()) {
                                Exception v = Validate(d);
                                if (v == null) {
                                    convertQueue.add(d);
                                } else {
                                    onFinish(d, false, v);
                                    Log.e(TAG, "Invalid Argument: " + v.getMessage());
                                    d.exception = v;
                                }
                            } else convertQueue.add(d);
                            break;
                        default:
                            if (d.status.metadata == null) {
                                if (d.conv == null || !new File(d.conv).exists()) {
                                    Exception v = Validate(d);
                                    if (v == null) {
                                        if (d.down == null || !new File(d.down).exists())
                                            //                                            downloadQueue.add(d);
                                            Download(d);
                                        else convertQueue.add(d);
                                    } else {
                                        onFinish(d, false, v);
                                        Log.e(TAG, "Invalid Argument :" + v.getMessage());
                                        d.status.metadata = false;
                                        d.exception = v;
                                    }
                                } else Metadata(d);
                            } else {
                                Exception v = Validate(d);
                                if (v == null) {
                                    //                                    downloadQueue.add(d);
                                    Download(d);
                                    continue;
                                }
                                onFinish(d, false, v);
                                Log.e(TAG, "Invalid Argument :" + v.getMessage());
                                d.status.metadata = false;
                                d.exception = v;
                            }
                    }
            }
        }

        List<Integer> ids = new ArrayList<>();
        for (int i = 0; i < paused.size(); i++) ids.add(paused.get(paused.keyAt(i)).id);

        Commons.fetch.getDownloads(ids, result -> {
            for (com.tonyodev.fetch2.Download d : result) {
                switch (d.getStatus()) {
                    case ADDED:
                    case QUEUED:
                        paused.get(d.getId()).status.download = Status.Download.QUEUED;
                        break;
                    case DOWNLOADING:
                        paused.get(d.getId()).status.download = Status.Download.RUNNING;
                        break;
                    case PAUSED:
                        paused.get(d.getId()).status.download = Status.Download.PAUSED;
                        break;
                    case CANCELLED:
                    case DELETED:
                    case REMOVED:
                        paused.get(d.getId()).status.download = Status.Download.CANCELLED;
                        break;
                    case FAILED:
                        paused.get(d.getId()).status.download = Status.Download.FAILED;
                        break;
                    case COMPLETED:
                        Download p = paused.get(d.getId());
                        p.status.download = Status.Download.COMPLETE;
                        if (p.convert) convertQueue.add(p);
                        else Metadata(p);
                        break;
                    default:
                        Download(paused.get(d.getId()));
                }
            }
        });
    }

    private Exception Validate(Download args) {
        if (args == null)
            return new IllegalArgumentException("Argument object is null");
        else if (args.sparseArray == null)
            return new IllegalArgumentException("Sparse array is null");
        else if (args.title == null || args.title.isEmpty())
            return new IllegalArgumentException("Title field is empty");
        return null;
    }

    private void Download(@NonNull Download current) {
        short i = 0;
        if (!(Commons.Directories.BIN.exists() || Commons.Directories.BIN.mkdirs()))
            Log.w(TAG, "Failed to create BIN folder");
        else {
            ArrayList<Short> indexes = new ArrayList<>();
            for (String f : Commons.Directories.BIN.list()) {
                String number = f.replaceFirst("^(\\d+).*?$", "$1");

                try {
                    indexes.add(Short.parseShort(number));
                } catch (NumberFormatException ignored) { }
            }

            while (indexes.contains(i)) i++;
        }

        if (current.availformat == null || current.availformat.isEmpty()) {
            current.extractFromSparse();
            if (current.url == null) {
                onFinish(current, false, new IllegalArgumentException("Failed to generate URL from extractFromSparse"));
            }
        }

        current.status.convert = null;
        current.status.metadata = null;
        current.down = String.format(Locale.ENGLISH, "%d.%s", i, current.availformat);

        if (!Commons.fetch.getListenerSet().contains(fetchListener))
            Commons.fetch.addListener(fetchListener);

        Request request = new Request(current.url, Commons.Directories.BIN.getAbsolutePath() + '/' + current.down);
        request.setNetworkType(NetworkType.ALL);
        request.setPriority(Priority.HIGH);
        request.setEnqueueAction(EnqueueAction.REPLACE_EXISTING);
        current.id = request.getId();
        downloadMap.put(request.getId(), current);
        Commons.fetch.enqueue(request, null, null);
    }

    private void Convert() {
        if (convertProgress != null) return;
        final Download current = convertQueue.poll();
        if (current == null) {
            if (CheckQueues()) stop();
            return;
        }

        if (current.convert) {
            converter = AndroidAudioConverter.with(getApplicationContext())
                    .setFile(new File(Commons.Directories.BIN, current.down))
                    .setTrim(current.start, current.end)
                    .setNormalize(current.normalize)
                    .setFormat(AudioFormat.valueOf(current.format.toUpperCase()))
                    .setBitrate(current.bitrate)
                    .setCallback(new AndroidAudioConverter.IConvertCallback() {
                        @Override
                        public void onSuccess(File conv) {
                            convertProgress = null;
                            current.status.convert = Status.Convert.COMPLETE;
                            current.conv = conv.getName();
                            if (converter != null) {
                                converter.killProcess();
                                converter = null;
                            }
                            if (convertQueue.size() > 0) Convert();
                            Metadata(current);
                        }

                        @Override
                        public void onProgress(long c) {
                            DownloadService.this.onProgress(c, 0, true, current);
                        }

                        @Override
                        public void onFailure(Exception e) {
                            if (converter != null) {
                                converter.killProcess();
                                converter = null;
                            }
                            convertProgress = null;

                            e.printStackTrace();
                            onFinish(current, false, e);
                        }
                    });
            converter.convert();
            current.status.convert = Status.Convert.RUNNING;
            current.status.metadata = null;
            convertProgress = current;
        } else {
            current.conv = current.down;
            current.status.convert = Status.Convert.SKIPPED;
            current.status.metadata = null;
            Metadata(current);
            if (convertProgress == null && convertQueue.size() > 0) Convert();
        }

    }

    private void Metadata(Download current) {
        if (current.conv == null) {
            onFinish(current, false, new IllegalArgumentException("conv is null"));
            return;
        } else if (current.conv.isEmpty()) {
            onFinish(current, false, new IllegalArgumentException("conv is empty"));
            return;
        }

        onProgress(0L, 0L, true, current);

        MusicMetadataSet src_set = null;
        try {
            src_set = new MyID3().read(new File(current.conv));
        } catch (IOException e) {
            e.printStackTrace();
        }

        if (current.overwrite) current.mtdt = ((current.artist == null ? "" : current.artist.trim() + " - ") + current.title.trim() + '.' + current.format).replaceAll("[|\\\\?*<\":>+\\[\\]/']", "_");
        else {
            short w = 1;
            String mtdt = (current.artist == null ? "" : current.artist.trim() + " - ") + current.title.trim().replaceAll("[|\\\\?*<\":>+\\[\\]/']", "_");
            String offset = mtdt;

            while (new File(Commons.Directories.BIN, offset + '.' + current.format).exists()) {
                offset = mtdt + " (" + w + ')';
                w++;
            }

            current.mtdt = offset + '.' + current.format;
        }

        if (src_set == null) {
            Log.i(TAG, "No metadata in down");

            try {
                try (InputStream in = new FileInputStream(new File(Commons.Directories.BIN, current.conv))) {
                    try (OutputStream out = new FileOutputStream(new File(Commons.Directories.MUSIC, current.mtdt))) {
                        byte[] buf = new byte[1024];
                        int len;
                        while ((len = in.read(buf)) > 0) out.write(buf, 0, len);
                    }
                }
            } catch (IOException e) {
                e.printStackTrace();
                onFinish(current, false, new ServiceException("Error copying file from conv to mtdt since there is no metadata support").setStatus(current.status).setPayload(e));
            }
        } else {
            MusicMetadata meta = new MusicMetadata("name");
            if (!(current.title == null || current.title.isEmpty()))
                meta.setSongTitle(current.title);
            if (!(current.artist == null || current.artist.isEmpty()))
                meta.setArtist(current.artist);
            if (!(current.album == null || current.album.isEmpty())) meta.setAlbum(current.album);
            if (current.track > 0) meta.setTrackNumber(current.track);
            if (current.year > 0) meta.setYear(String.valueOf(current.year));

            if (current.genres != null && current.genres.length > 0) {
                StringBuilder str = new StringBuilder();

                for (int i = 0; i < current.genres.length - 1; i++)
                    str.append(current.genres[i]).append(", ");

                str.append(current.genres[current.genres.length - 1]);
                meta.setGenre(str.toString());
            }

            if (current.title == null) throw new IllegalArgumentException("Title field is somehow empty at Metadata. Was Validate() bypassed?");

            try {
                new MyID3().write(
                        new File(current.conv),
                        new File(Commons.Directories.MUSIC, current.mtdt),
                        src_set,
                        meta
                );
            } catch (IOException | ID3WriteException e) {
                e.printStackTrace();
                current.completed = new Date();
                current.status.metadata = true;
                onFinish(current, false, new ServiceException("Metadata Failure").setStatus(current.status).setPayload(e));
                return;
            }
        }

        current.completed = new Date();
        current.status.metadata = true;
        onFinish(current, true, null);
    }

    //endregion

    private boolean CheckQueues() {
        return downloadMap.size() == 0 &&
                convertQueue.size() == 0 &&
                convertProgress == null;
    }

    private void onFinish(Download args, boolean successful, @Nullable Exception e) {
        //        boolean failed = false;
        //        for (File f : Commons.Directories.BIN.listFiles()) {
        //            if (!f.delete()) failed = true;
        //        }
        if (!(new File(Commons.Directories.BIN, args.down).delete() &&
                new File(Commons.Directories.BIN, args.conv).delete()))
            Log.w(TAG, "Failed to delete cache from BIN");

        if (args.getCompleteDate() == null) args.completed = new Date();
        if (successful) args.exception = null;
        if (!completed.contains(args)) completed.add(args);

        CallbackDone(args, successful, e);

        Intent intent = new Intent(TAG);
        if (e != null) intent.putExtra(Commons.ARGS.EXCEPTION, e);
        intent.putExtra(Commons.ARGS.RESULT, successful ? Commons.ARGS.SUCCESS : Commons.ARGS.FAILED);
        intent.putExtra(Commons.ARGS.DATA, args);
        intent.putExtra(Commons.ARGS.REMAINING, !CheckQueues());
        LocalBroadcastManager.getInstance(this).sendBroadcast(intent);
    }

    private void onFinish(@NonNull Download args, @NonNull ServiceException e) {
        onFinish(args, false, e.setStatus(args.status));
    }

    private void onProgress(long current, long total, boolean indeterminate, Download download) {
        download.current = (int) current;
        download.total = (int) total;
        download.indeterminate = indeterminate;

        CallbackProgress(download, current, total, indeterminate);

        Intent intent = new Intent(TAG);
        intent.putExtra(Commons.ARGS.CURRENT, current);
        intent.putExtra(Commons.ARGS.TOTAL, total);
        intent.putExtra(Commons.ARGS.INDETERMINATE, indeterminate);
        intent.putExtra(Commons.ARGS.DATA, download);
        intent.putExtra(Commons.ARGS.RESULT, Commons.ARGS.PROGRESS);
        LocalBroadcastManager.getInstance(this).sendBroadcast(intent);
    }

    @Override
    public void onDestroy() {
        Log.w(TAG, "Service Destroyed");

        Intent intent = new Intent(TAG);
        intent.putExtra(Commons.ARGS.RESULT, Commons.ARGS.DESTROY);
        LocalBroadcastManager.getInstance(this).sendBroadcast(intent);

        Commons.fetch.removeListener(fetchListener);

        // TODO: Implement saving, completed array must not be saved nor loaded, rather saved into another "history array" in Commons

        //        Commons.fetch.getDownloadsWithStatus(Status.COMPLETE, new Func<List<Download>>() {
        //            @Override
        //            public void call(@NotNull final List<Download> completed) {
        //                Commons.fetch.getDownloadsWithStatus(Status.DOWN_QUEUE, new Func<List<Download>>() {
        //                    @Override
        //                    public void call(@NotNull List<Download> queue) {

        //        Sanitize();
        //        if (!Commons.SaveQueue(queue.toArray(new Download[0]))) {
        //            Log.e(TAG, "Failed to save queue");
        //            Intent error = new Intent(TAG);
        //            error.putExtra(Commons.ARGS.RESULT, Commons.ARGS.ERR_LOAD);
        //            LocalBroadcastManager.getInstance(DownloadService.this).sendBroadcast(error);
        //
        //            if (callbacks != null) callbacks.onWarn(Commons.ARGS.ERR_LOAD);
        //        }

        DownloadService.super.onDestroy();
        //                    }
        //                });
        //            }
        //        });

    }

    private void UpdateMaps(com.tonyodev.fetch2.Download download, Download args) {
        switch (download.getStatus()) {
            case QUEUED:
            case ADDED:
                args.status.download = Status.Download.QUEUED;
                break;
            case DOWNLOADING:
                args.status.download = Status.Download.RUNNING;
                break;
            case PAUSED:
                args.status.download = Status.Download.PAUSED;
                break;
            case COMPLETED:
                args.status.download = Status.Download.COMPLETE;
                break;
            case CANCELLED:
            case DELETED:
                args.status.download = Status.Download.CANCELLED;
                break;
            case FAILED:
            case REMOVED:
                args.status.download = Status.Download.FAILED;
                break;
            default:
                args.status.download = null;
        }
    }

    //    private void UpdateProgress(@NonNull com.tonyodev.fetch2.Download download, @Nullable Download.DownloadStatus status) {
    //        UpdateProgress(download, "", status);
    //    }

    private void UpdateProgress(@NonNull com.tonyodev.fetch2.Download download, @NonNull String tagPrefix, @Nullable Status.Download status) {
        Download args = downloadMap.get(download.getRequest().getId());
        if (args == null) {
            Log.w(TAG, tagPrefix + ": " + MISSING_IN_MAP);
            return;
        }

        if (status == null)
            UpdateMaps(download, args);
        else args.status.download = status;

        DownloadService.this.onProgress(download.getDownloaded(), download.getTotal(), false, args);
    }

    private void UpdateCancelled(@NonNull com.tonyodev.fetch2.Download download, String tagPrefix) {
        Download args = downloadMap.get(download.getRequest().getId());
        if (args == null) {
            Log.w(TAG, tagPrefix + ": " + MISSING_IN_MAP);
            return;
        }
        args.status.download = Status.Download.CANCELLED;

        downloadMap.remove(download.getId());
        completed.add(args);
        DownloadService.this.onProgress(download.getDownloaded(), download.getTotal(), false, args);
    }

    public void Sanitize() {
        for (int i = 0; i < downloadMap.size(); i++) {
            Download d = downloadMap.valueAt(i);
            if (d == null) {
                downloadMap.removeAt(i);
            } else if (d.status.download == null) {
                Exception v = Validate(d);
                if (v == null) {
                    d.status.download = Status.Download.QUEUED;
                } else {
                    downloadMap.removeAt(i);
                    onFinish(d, false, v);
                    Log.e(TAG, "Invalid Argument :" + v.getMessage());
                    d.status.download = Status.Download.FAILED;
                    d.exception = v;
                }
            }
        }

        for (Download d : convertQueue) {
            if (d.status.convert == null) {
                Exception v = Validate(d);
                if (v == null) {
                    d.status.convert = Status.Convert.QUEUED;
                } else {
                    convertQueue.remove(d);
                    onFinish(d, false, v);
                    Log.e(TAG, "Invalid Argument :" + v.getMessage());
                    d.status.convert = Status.Convert.FAILED;
                    d.exception = v;
                }
            } else if (d.down == null || !new File(Commons.Directories.BIN, d.down).exists()) {
                convertQueue.remove(d);
                Download(d);
            } else d.status.convert = Status.Convert.QUEUED;
        }

        if (convertProgress != null) {
            if (convertProgress.status.convert == null) {
                Exception v = Validate(convertProgress);
                if (v == null) {
                    convertProgress.status.convert = Status.Convert.QUEUED;
                } else {
                    Log.e(TAG, "Invalid Argument :" + v.getMessage());
                    convertProgress.status.convert = Status.Convert.FAILED;
                    convertProgress.exception = v;
                    onFinish(convertProgress, false, v);
                    convertProgress = null;
                }
            } else convertProgress.status.convert = Status.Convert.RUNNING;
        }
    }

    private void stop() {
        boolean bad = false;
        for (File file : Commons.Directories.BIN.listFiles()) {
            if (!file.delete()) bad = true;
        }

        if (bad) Log.w(TAG, "Failed to empty BIN");
    }

    public List<Download> getAll() {
        //        ArrayList<Download> list = new ArrayList<>();
        //        Iterator<Map.Entry<Request, Download>> it = queue.entrySet().iterator();
        //        while (it.hasNext()) {
        //            Map.Entry<Request, Download> pair = it.next();
        //            list.add(pair.getValue());
        //            it.remove(); // avoids a ConcurrentModificationException
        //        }
        ArrayList<Download> list = new ArrayList<>();

        for (int i = 0; i < downloadMap.size(); i++) list.add(downloadMap.valueAt(i));
        //        list.addAll(downloadProgress);
        list.addAll(convertQueue);
        if (convertProgress != null) list.add(convertProgress);
        list.addAll(completed);
        return list;
    }

    public List<Download> getQueue() {
        ArrayList<Download> list = new ArrayList<>();

        //        for (Map.Entry<Request, Download> pair : downloadMap.entrySet()) {
        //            if (pair.getValue().downloadStatus == Download.DownloadStatus.QUEUED)
        //                list.add(pair.getValue());
        //        }
        for (int i = 0; i < downloadMap.size(); i++) {
            Download d = downloadMap.valueAt(i);
            if (d.status.download == Status.Download.QUEUED) list.add(d);
        }

        return list;
    }

    public List<Download> getRunning() {
        ArrayList<Download> list = new ArrayList<>();

        //        for (Map.Entry<Request, Download> pair : downloadMap.entrySet()) {
        //            Download d = pair.getValue();
        //            if (d.downloadStatus == Download.DownloadStatus.RUNNING ||
        //                    d.downloadStatus == Download.DownloadStatus.PAUSED)
        //                list.add(d);
        //        }
        for (int i = 0; i < downloadMap.size(); i++) {
            Download d = downloadMap.valueAt(i);
            if (d.status.download == Status.Download.RUNNING || d.status.download == Status.Download.PAUSED)
                list.add(downloadMap.valueAt(i));
        }

        list.addAll(convertQueue);
        if (convertProgress != null) list.add(convertProgress);
        return list;
    }

    public List<Download> getCompleted() {
        return new ArrayList<>(completed);
    }

    private void CallbackProgress(Download download, long current, long total, boolean indeterminate) {
        for (int i = 0; i < callbacks.size(); i++) {
            ClientCallbacks c = callbacks.get(i);
            if (c == null)
                callbacks.remove(i);
            else
                c.onProgress(download, current, total, indeterminate);
        }
    }

    private void CallbackDone(Download download, boolean successful, @Nullable Exception e) {
        for (int i = 0; i < callbacks.size(); i++) {
            ClientCallbacks c = callbacks.get(i);
            if (c == null)
                callbacks.remove(i);
            else
                c.onDone(download, successful, e);
        }
    }

    private void CallbackWarn(Download download, String msg) {
        for (int i = 0; i < callbacks.size(); i++) {
            ClientCallbacks c = callbacks.get(i);
            if (c == null)
                callbacks.remove(i);
            else
                c.onWarn(download, msg);
        }
    }

    public class LocalBinder extends Binder {
        public DownloadService getService() {
            return DownloadService.this;
        }
    }

    public static class ServiceException extends RuntimeException {
        private Status status;
        private Exception payload;

        private ServiceException(String msg) {
            super(msg);
            status = new Status();
        }

        private ServiceException setStatus(@NonNull Status status) {
            this.status = status;
            return this;
        }

        private ServiceException setPayload(Exception payload) {
            this.payload = payload;
            return this;
        }

        public Status.Download getDownload() {
            return status.download;
        }

        public Status.Convert getConvert() {
            return status.convert;
        }

        public Boolean getMetadata() {
            return status.metadata;
        }

        public Status getStatus() {
            return status;
        }

        public Exception getPayload() {
            return payload;
        }
    }
}
