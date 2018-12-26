package com.yearzero.renebeats;

import android.app.Service;
import android.content.Intent;
import android.os.Binder;
import android.os.IBinder;
import android.util.Log;
import android.util.SparseArray;

import com.downloader.PRDownloader;
import com.tonyodev.fetch2.EnqueueAction;
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
import java.io.IOException;
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

    private FetchListener fetchListener = new FetchListener() {
        @Override
        public void onAdded(com.tonyodev.fetch2.@NotNull Download download) {
            UpdateProgress(download, "onAdded", null);
        }

        @Override
        public void onQueued(com.tonyodev.fetch2.@NotNull Download download, boolean b) {
            UpdateProgress(download, "onQueued", Download.DownloadStatus.QUEUED);
        }

        @Override
        public void onWaitingNetwork(com.tonyodev.fetch2.@NotNull Download download) {
            UpdateProgress(download, "onWaitingNetwork", null);
        }

        @Override
        public void onCompleted(com.tonyodev.fetch2.@NotNull Download download) {
            Download args = downloadMap.get(download.getRequest().getId());
            if (args == null) {
                Log.w(TAG, "onCompleted" + MISSING_IN_MAP);
                return;
            }

            args.downloadStatus = Download.DownloadStatus.COMPLETE;
            convertQueue.add(args);
            downloadMap.remove(download.getId());
            Convert();
        }

        @Override
        public void onError(com.tonyodev.fetch2.@NotNull Download download, com.tonyodev.fetch2.@NotNull Error error, @org.jetbrains.annotations.Nullable Throwable throwable) {
            String msg = "Download Error" + (throwable == null ? "" : throwable.getMessage());
            Log.e(TAG, msg);

            Download args = downloadMap.get(download.getRequest().getId());
            if (args == null) {
                Log.w(TAG, "onError: " + MISSING_IN_MAP);
                return;
            }

            args.downloadStatus = Download.DownloadStatus.FAILED;
            completed.add(args);
            downloadMap.remove(download.getId());
            onFinish(args, false, new ServiceException(msg).setStatus(args).setPayload(new RuntimeException(msg)));
        }

        @Override
        public void onDownloadBlockUpdated(com.tonyodev.fetch2.@NotNull Download download, @NotNull DownloadBlock downloadBlock, int i) {
        }


        @Override
        public void onStarted(com.tonyodev.fetch2.@NotNull Download download, @NotNull List<? extends DownloadBlock> list, int i) {
            UpdateProgress(download, "onStarted", Download.DownloadStatus.RUNNING);
        }

        @Override
        public void onProgress(com.tonyodev.fetch2.@NotNull Download download, long etaMilli, long Bps) {
            UpdateProgress(download, "onProgress", Download.DownloadStatus.RUNNING);
        }

        @Override
        public void onPaused(com.tonyodev.fetch2.@NotNull Download download) {
            Download args = downloadMap.get(download.getRequest().getId());
            if (args == null) {
                Log.w(TAG, "onPaused: " + MISSING_IN_MAP);
                return;
            }

            UpdateMaps(download, args);
            DownloadService.this.onProgress(download.getDownloaded(), download.getTotal(), false, args);
        }

        @Override
        public void onResumed(com.tonyodev.fetch2.@NotNull Download download) {
            UpdateProgress(download, "onResumed", null);
        }

        @Override
        public void onCancelled(com.tonyodev.fetch2.@NotNull Download download) {
            UpdateCancelled(download, "onCancelled");
        }

        @Override
        public void onRemoved(com.tonyodev.fetch2.@NotNull Download download) {
            downloadMap.remove(download.getId());
            UpdateCancelled(download, "onRemoved");
        }

        @Override
        public void onDeleted(com.tonyodev.fetch2.@NotNull Download download) {
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
    //    private LinkedList<Download> downloadQueue = new LinkedList<>();
//    private ArrayList<Download> downloadProgress = new ArrayList<>();
    private LinkedList<Download> convertQueue = new LinkedList<>();
    private Download convertProgress;
    private ArrayList<Download> completed = new ArrayList<>();
    //    private HashMap<Request, Download> downloadMap = new HashMap<>();
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
//                if (downloadProgress.size() < Commons.Pref.concurrency && downloadQueue.size() > 0)
//                Download();
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
                Log.e(TAG, "Invalid Argument :" + v.getMessage());
                return START_STICKY;
            }

            current.extractFromSparse();

            if (current.url != null) {
                 current.downloadStatus = Download.DownloadStatus.QUEUED;
                current.convertStatus = null;
                current.metadataSuccess = null;
                onProgress(0, 0, true, current);
//                downloadQueue.add(current);
                Download(current);
            }

        }
        return START_STICKY;
    }

    private void LoadPackage(Download[] pkg) {
        for (Download d : pkg) {
            if (d.downloadStatus() == null) continue;
            switch (d.downloadStatus()) {
                case QUEUED:
//                    downloadQueue.add(d);
                    Download(d);
                    break;
                case RUNNING:
                case PAUSED:
                    boolean completed = false;
                    switch (PRDownloader.getStatus(d.downloadId)) {
                        case QUEUED:
                        case RUNNING:
                            d.downloadStatus = Download.DownloadStatus.RUNNING;
                            break;
                        case PAUSED:
                            d.downloadStatus = Download.DownloadStatus.RUNNING;
                            break;
                        case COMPLETED:
                            d.downloadStatus = Download.DownloadStatus.COMPLETE;
                            completed = true;
                            break;
                        case CANCELLED:
                             d.downloadStatus = Download.DownloadStatus.CANCELLED;
                            completed = true;
                            break;
                    }

                    if (completed && d.convert) convertQueue.add(d);
                    else Metadata(d);
                    break;

                case CANCELLED:
                    this.completed.add(d);
                    // TODO: Replace "this.completed.add(d);" with "Commons.History.add(d);"
                    break;
                default:
                    if (d.convertStatus == null) continue;
                    switch (d.convertStatus) {
                        case RUNNING:
                        case PAUSED:
                            d.convertStatus = Download.ConvertStatus.QUEUED;
                            if (d.conv != null){
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
                            if (d.metadataSuccess == null) {
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
                                        d.metadataSuccess = false;
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
                                d.metadataSuccess = false;
                                d.exception = v;
                            }
                    }
            }
        }
    }

    private Exception Validate(Download args) {
        if (args == null)
            return new IllegalArgumentException("Argument object is null");
        else if (args.sparseArray == null)
            return new IllegalArgumentException("Sparse array is null");
        else if (args.artist == null || args.artist.isEmpty() || args.title == null || args.title.isEmpty())
            return new IllegalArgumentException("Title or Artist fields are empty");
        return null;
    }

    private void Download(@NonNull Download current) {
//        final Download current = downloadQueue.poll();
//        if (current == null) {
//            if (CheckQueues()) stop();
//            return;
//        }

        short i = 0;
        if (!(Commons.Directories.BIN.exists() || Commons.Directories.BIN.mkdirs()))
            Log.w(TAG, "Failed to create BIN folder");
        else {
            ArrayList<Short> indexes = new ArrayList<>();
            for (String f : Commons.Directories.BIN.list()) {
                String number = f.replaceFirst("^(\\d+).*?$", "$1");

                try {
                    indexes.add(Short.parseShort(number));
                } catch (NumberFormatException ignored) {
                }
            }

            while (indexes.contains(i)) i++;
        }


        if (current.availformat == null || current.availformat.isEmpty()) {
            current.extractFromSparse();
            if (current.url == null) {
                onFinish(current, false, new IllegalArgumentException("Failed to generate URL from extractFromSparse"));
            }
        }

        current.convertStatus = null;
        current.metadataSuccess = null;
        current.down = String.format(Locale.ENGLISH, "%d.%s", i, current.availformat);

        if (!Commons.fetch.getListenerSet().contains(fetchListener))
            Commons.fetch.addListener(fetchListener);

        Request request = new Request(current.url, Commons.Directories.BIN.getAbsolutePath() + '/' + current.down);
        request.setNetworkType(NetworkType.ALL);
        request.setPriority(Priority.HIGH);
        request.setEnqueueAction(EnqueueAction.REPLACE_EXISTING);
        downloadMap.put(request.getId(), current);
        Commons.fetch.enqueue(request, null, null);

//        int id = Integer.MIN_VALUE;
//        while (id < Integer.MAX_VALUE) {
//            boolean contains = false;
//            for (int j = 0; j < queue.size(); j++) {
//                if (queue.keyAt(j) == id) {
//                    id++;
//                    contains = true;
//                }
//            }
//            if (!contains) break;
//        }


//        current.downloadId = PRDownloader.download(current.url, Commons.Directories.BIN.getAbsolutePath(), current.down)
//                .build()
//                .setOnProgressListener(new OnProgressListener() {
//                    @Override
//                    public void onProgress(Progress progress) {
//                        DownloadService.this.onProgress(progress.currentBytes, progress.totalBytes, false, current);
//                    }
//                })
//                .setOnPauseListener(new OnPauseListener() {
//                    @Override
//                    public void onPause() {
//                        current.downloadStatus = Download.DownloadStatus.PAUSED;
//                        DownloadService.this.onProgress(0L, 0L, true, current);
//                    }
//                })
//                .start(new OnDownloadListener() {
//                    @Override
//                    public void onDownloadComplete() {
//                        current.downloadStatus = Download.DownloadStatus.COMPLETE;
//                        current.convertStatus = Download.ConvertStatus.QUEUED;
//                        current.metadataSuccess = null;
//                        convertQueue.add(current);
//                        downloadProgress.remove(current);
//                        if (downloadProgress.size() < Commons.Pref.concurrency && downloadQueue.size() > 0)
//                            Download();
//                        Convert();
//                    }
//
//                    @Override
//                    public void onError(Error error) {
//                        current.downloadStatus = Download.DownloadStatus.FAILED;
//                        current.convertStatus = null;
//                        current.metadataSuccess = null;
//                        downloadProgress.remove(current);
//                        onFinish(current, false, new ServiceException("Download Error // Connection: " + (error.isConnectionError() ? "GOOD" : "ERROR") + " Server: " + (error.isServerError() ? "GOOD" : "ERROR")));
//                    }
//                });
//        downloadProgress.add(current);

    }

    private void Convert() {
        final Download current = convertQueue.poll();
        if (current == null) {
            if (CheckQueues()) stop();
            return;
        }

        if (current.convert) {
            if (convertProgress != null) {
                current.convertStatus = Download.ConvertStatus.QUEUED;
                return;
            }
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
                            current.convertStatus = Download.ConvertStatus.COMPLETE;
                            current.conv = conv.getAbsolutePath();
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
            current.convertStatus = Download.ConvertStatus.RUNNING;
            current.metadataSuccess = null;
            convertProgress = current;

        } else {
            current.conv = current.down;
            current.convertStatus = Download.ConvertStatus.SKIPPED;
            current.metadataSuccess = null;
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

        MusicMetadataSet src_set = null;
        try {
            src_set = new MyID3().read(new File(current.conv));
        } catch (IOException e) {
            e.printStackTrace();
        }

        if (src_set == null) {
            Log.i(TAG, "No metadata in down");
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

            current.mtdt = (current.artist == null ? String.format("%s.%s", current.title.trim(), current.format) : String.format("%s - %s.%s", current.artist.trim(), current.title.trim(), current.format)).replaceAll("[|\\\\?*<\":>+\\[\\]/']", "_");

            try {
                new MyID3().write(
                        new File(current.conv),
                        new File(Commons.Directories.MUSIC, current.mtdt),
                        src_set,
                        meta
                );
                current.completed = new Date();
                current.metadataSuccess = true;
                onProgress(0, 0, true, current);
            } catch (IOException | ID3WriteException e) {
                e.printStackTrace();
                current.metadataSuccess = false;
                onFinish(current, false, new ServiceException("Metadata Failure").setStatus(current).setPayload(e));
            }
        }

        Log.i(TAG, "SUCCESS!");
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
                new File(Commons.Directories.BIN, args.conv).delete() &&
                new File(Commons.Directories.BIN, args.mtdt).delete()))
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

    private void onFinish(Download args, @NonNull ServiceException e) {
        onFinish(args, false, e.setStatus(args.downloadStatus(), args.convertStatus, args.metadataSuccess));
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

//    private ArrayList<Download> MapList(List<Download> input) {
//        ArrayList<Download> res = new ArrayList<>();
//        for (Download d : input) {
//            Download a = queue.get(d.getRequest());
//            if (a == null)
//                Log.w(TAG, MISSING_IN_MAP);
//            else res.add(a);
//        }
//        return res;
//    }
//


    private void UpdateMaps(com.tonyodev.fetch2.Download download) {
        Download args = downloadMap.get(download.getRequest().getId());
        if (args == null)
            Log.w(TAG, MISSING_IN_MAP);
        else UpdateMaps(download, args);
    }

    private void UpdateMaps(com.tonyodev.fetch2.Download download, Download args) {
        switch (download.getStatus()) {
            case QUEUED:
            case ADDED:
                args.downloadStatus = Download.DownloadStatus.QUEUED;
                break;
            case DOWNLOADING:
                args.downloadStatus = Download.DownloadStatus.RUNNING;
                break;
            case PAUSED:
                args.downloadStatus = Download.DownloadStatus.PAUSED;
                break;
            case COMPLETED:
                args.downloadStatus = Download.DownloadStatus.COMPLETE;
                break;
            case CANCELLED:
            case DELETED:
                args.downloadStatus = Download.DownloadStatus.CANCELLED;
                break;
            case FAILED:
            case REMOVED:
                args.downloadStatus = Download.DownloadStatus.FAILED;
                break;
            default:
                args.downloadStatus = null;
        }
    }

    private void UpdateProgress(@NonNull com.tonyodev.fetch2.Download download, @Nullable Download.DownloadStatus status) {
        UpdateProgress(download, "", status);
    }

    private void UpdateProgress(@NonNull com.tonyodev.fetch2.Download download, @NonNull String tagPrefix, @Nullable Download.DownloadStatus status) {
        Download args = downloadMap.get(download.getRequest().getId());
        if (args == null) {
            Log.w(TAG, tagPrefix + ": " + MISSING_IN_MAP);
            return;
        }

        if (status == null)
            UpdateMaps(download, args);
        else args.downloadStatus = status;

        DownloadService.this.onProgress(download.getDownloaded(), download.getTotal(), false, args);
    }

    private void UpdateCancelled(@NonNull com.tonyodev.fetch2.Download download, String tagPrefix) {
        Download args = downloadMap.get(download.getRequest().getId());
        if (args == null) {
            Log.w(TAG, tagPrefix + ": " + MISSING_IN_MAP);
            return;
        }
        args.downloadStatus = Download.DownloadStatus.CANCELLED;

        downloadMap.remove(download.getId());
        completed.add(args);
        DownloadService.this.onProgress(download.getDownloaded(), download.getTotal(), false, args);
    }

    public void Sanitize() {
        for (int i = 0; i < downloadMap.size(); i++) {
            Download d = downloadMap.valueAt(i);
            if (d == null) {
                downloadMap.removeAt(i);
            } else if (d.downloadStatus() == null) {
                Exception v = Validate(d);
                if (v == null) {
                     d.downloadStatus = Download.DownloadStatus.QUEUED;
                } else {
                    downloadMap.removeAt(i);
                    onFinish(d, false, v);
                    Log.e(TAG, "Invalid Argument :" + v.getMessage());
                     d.downloadStatus = Download.DownloadStatus.FAILED;
                    d.exception = v;
                }
            }
        }

//        for (Map.Entry<Request, Download> pair : downloadMap.entrySet()) {
//            Download d = pair.getValue();
//            if (d == null) {
//                downloadMap.remove(pair.getKey());
//            } else if (d.downloadStatus == null) {
//                Exception v = Validate(d);
//                if (v == null) {
//                    d.downloadStatus = Download.DownloadStatus.QUEUED;
//                } else {
//                    downloadMap.remove(pair.getKey());
//                    onFinish(d, false, v);
//                    Log.e(TAG, "Invalid Argument :" + v.getMessage());
//                    d.downloadStatus = Download.DownloadStatus.FAILED;
//                    d.exception = v;
//                }
//            } else d.downloadStatus = Download.DownloadStatus.QUEUED;
//        }

//        for (int i = 0; i < downloadQueue.size(); i++) {
//            Download d = downloadQueue.get(i);
//            if (d == null) {
//                downloadQueue.remove(i);
//            } else if (d.downloadStatus == null) {
//                Exception v = Validate(d);
//                if (v == null) {
//                    d.downloadStatus = Download.DownloadStatus.QUEUED;
//                } else {
//                    downloadQueue.remove(d);
//                    onFinish(d, false, v);
//                    Log.e(TAG, "Invalid Argument :" + v.getMessage());
//                    d.downloadStatus = Download.DownloadStatus.FAILED;
//                    d.exception = v;
//                }
//            } else d.downloadStatus = Download.DownloadStatus.QUEUED;
//        }
//
//        for (Download d : downloadProgress) {
//            if (d.downloadStatus == null) {
//                Exception v = Validate(d);
//                if (v == null) {
//                    d.downloadStatus = Download.DownloadStatus.QUEUED;
//                } else {
//                    downloadProgress.remove(d);
//                    onFinish(d, false, v);
//                    Log.e(TAG, "Invalid Argument :" + v.getMessage());
//                    d.downloadStatus = Download.DownloadStatus.FAILED;
//                    d.exception = v;
//                }
//            } else if (PRDownloader.getStatus(d.downloadId) == com.downloader.Status.UNKNOWN) {
//                downloadProgress.remove(d);
//                downloadQueue.add(d);
//            } else d.downloadStatus = Download.DownloadStatus.QUEUED;
//        }

        for (Download d : convertQueue) {
            if (d.convertStatus == null) {
                Exception v = Validate(d);
                if (v == null) {
                    d.convertStatus = Download.ConvertStatus.QUEUED;
                } else {
                    convertQueue.remove(d);
                    onFinish(d, false, v);
                    Log.e(TAG, "Invalid Argument :" + v.getMessage());
                    d.convertStatus = Download.ConvertStatus.FAILED;
                    d.exception = v;
                }
            } else if (d.down == null || !new File(d.down).exists()) {
                convertQueue.remove(d);
                Download(d);
            } else d.convertStatus = Download.ConvertStatus.QUEUED;
        }

        if (convertProgress != null) {
            if (convertProgress.convertStatus == null) {
                Exception v = Validate(convertProgress);
                if (v == null) {
                    convertProgress.convertStatus = Download.ConvertStatus.QUEUED;
                } else {
                    Log.e(TAG, "Invalid Argument :" + v.getMessage());
                    convertProgress.convertStatus = Download.ConvertStatus.FAILED;
                    convertProgress.exception = v;
                    onFinish(convertProgress, false, v);
                    convertProgress = null;
                }
            } else convertProgress.convertStatus = Download.ConvertStatus.RUNNING;
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
            if (d.downloadStatus() == Download.DownloadStatus.QUEUED) list.add(d);
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
            if (d.downloadStatus() == Download.DownloadStatus.RUNNING || d.downloadStatus() == Download.DownloadStatus.PAUSED)
                list.add(downloadMap.valueAt(i));
        }

        list.addAll(convertQueue);
        if (convertProgress != null) list.add(convertProgress);
        return list;
    }

    public List<Download> getCompleted() {
//        ArrayList<Download> list = new ArrayList<>();
//        Iterator<Map.Entry<Request, Download>> it = queue.entrySet().iterator();
//        while (it.hasNext()) {
//            Map.Entry<Request, Download> pair = it.next();
//            if (pair.getValue().getStatus() == Status.COMPLETE ||
//                    pair.getValue().getStatus() == Status.FAILED)
//                list.add(pair.getValue());
//            it.remove(); // avoids a ConcurrentModificationException
//        }
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
        private Download.DownloadStatus download;
        private Download.ConvertStatus conversion;
        private Boolean metadata;
        private Exception payload;

        private ServiceException(String msg) {
            super(msg);
        }

        private ServiceException setStatus(Download.DownloadStatus download, Download.ConvertStatus conversion, Boolean metadata) {
            this.download = download;
            this.conversion = conversion;
            this.metadata = metadata;
            return this;
        }

        private ServiceException setStatus(Download pack) {
            download = pack.downloadStatus();
            conversion = pack.convertStatus;
            metadata = pack.metadataSuccess;
            return this;
        }

        private ServiceException setPayload(Exception payload) {
            this.payload = payload;
            return this;
        }

        public Download.DownloadStatus getDownload() {
            return download;
        }

        public Download.ConvertStatus getConversion() {
            return conversion;
        }

        public Boolean getMetadata() {
            return metadata;
        }

        public Exception getPayload() {
            return payload;
        }
    }
}
