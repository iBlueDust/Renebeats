package com.yearzero.renebeats.download;

import android.app.Service;
import android.content.Intent;
import android.os.Binder;
import android.os.IBinder;
import android.util.Log;
import android.util.LongSparseArray;
import android.util.SparseArray;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.localbroadcastmanager.content.LocalBroadcastManager;

import com.google.android.gms.common.util.ArrayUtils;
import com.tonyodev.fetch2.EnqueueAction;
import com.tonyodev.fetch2.Error;
import com.tonyodev.fetch2.FetchListener;
import com.tonyodev.fetch2.NetworkType;
import com.tonyodev.fetch2.Priority;
import com.tonyodev.fetch2.Request;
import com.tonyodev.fetch2core.DownloadBlock;
import com.yearzero.renebeats.AndroidAudioConverter;
import com.yearzero.renebeats.Commons;
import com.yearzero.renebeats.Directories;
import com.yearzero.renebeats.InternalArgs;
import com.yearzero.renebeats.preferences.Preferences;

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
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;
import java.util.Locale;

import cafe.adriel.androidaudioconverter.model.AudioFormat;

public class DownloadService extends Service {

    //TODO: Dedicated queue file

    public static final String TAG = "DownloadService";
    public static final String MISSING_IN_MAP = "Request Queue: A Download in Fetch is not in HashMap (IGNORED)";
//    public static final long ID_HEADER = 0x80860000L;
    private static final int GROUP_ID = 0xC560_6A11;
    //    public static final String DONT_LOAD = "dont_load";

    private final FetchListener fetchListener = new FetchListener() {
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

            convertQueue.add(args);
            downloadMap.remove(download.getId());
            args.getStatus().setDownload(Status.Download.COMPLETE);
            args.getStatus().setConvert(Status.Convert.QUEUED);
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

            args.getStatus().setDownload(Status.Download.FAILED);
            completed.add(args);
            downloadMap.remove(download.getId());
            onFinish(args, new ServiceException(msg).setPayload(new RuntimeException(msg)));
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
        void onProgress(Download args, long progress, long max, long size, boolean indeterminate);
        void onDone(Download args, boolean successful, Exception e);
        void onWarn(Download args, String type);
    }

    private final LocalBinder binder = new LocalBinder();

    private boolean loaded;//, cpause, pause;
    private @Nullable AndroidAudioConverter converter;
    private final ArrayList<ClientCallbacks> callbacks = new ArrayList<>();
    private final LinkedList<Download> convertQueue = new LinkedList<>();
//    private final ArrayList<Download> convertPause = new ArrayList<>();
    private @Nullable Download convertProgress;
    private final ArrayList<Download> completed = new ArrayList<>();
    private final SparseArray<Download> downloadMap = new SparseArray<>();

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

    //region Main

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        Download current = null;
        if (intent != null) {
            int requestFlag = intent.getIntExtra(InternalArgs.REQUEST, 0);
            if (requestFlag != 0) {
                Intent req = new Intent(TAG);

                req.putExtras(intent);

                Sanitize();
                int request = intent.getIntExtra(InternalArgs.REQ_ID, Integer.MIN_VALUE);
                if (request != Integer.MIN_VALUE) req.putExtra(InternalArgs.REQ_ID, request);

                if ((requestFlag & InternalArgs.FLAG_QUEUE) == InternalArgs.FLAG_QUEUE)
                    req.putExtra(InternalArgs.REQ_QUEUE, getQueue());

                if ((requestFlag & InternalArgs.FLAG_RUNNING) == InternalArgs.FLAG_RUNNING)
                    req.putExtra(InternalArgs.REQ_RUNNING, getRunning());

                if ((requestFlag & InternalArgs.FLAG_COMPLETED) == InternalArgs.FLAG_COMPLETED)
                    req.putExtra(InternalArgs.REQ_COMPLETED, getCompleted());

                LocalBroadcastManager.getInstance(DownloadService.this).sendBroadcast(req);

                if (CheckQueues()) stop();
                return START_STICKY;
            }

            if ((intent.getBooleanExtra(InternalArgs.LOAD, true) && !loaded)) {
                Download[] pkg = Directories.loadQueue();
                if (pkg == null)
                    Log.e(TAG, "Failed to load queue");
                else {
                    loaded = true;
                    LoadPackage(pkg);
                }
            }

            try {
                current = (Download) intent.getSerializableExtra(InternalArgs.DATA);
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
                current.getStatus().setInvalid(true);
                onFinish(current, false, v);
                Log.e(TAG, "Invalid Argument: " + v.getMessage());
                return START_STICKY;
            }

            current.extractFromSparse();

            if (current.getUrl() != null) {
                current.getStatus().setDownload(Status.Download.QUEUED);
                current.getStatus().setConvert(null);
                current.getStatus().setMetadata(null);
                new History.AppendNowTask().execute(current);
                onProgress(0, 0, true, current);
                Download(current);
            }
        }
        return START_STICKY;
    }

    private void LoadPackage(Download[] pkg) {
        LongSparseArray<Download> paused = new LongSparseArray<>();

        for (Download d : pkg) {
            if (d.getStatus().getDownload() == null) continue;
            switch (d.getStatus().getDownload()) {
                case QUEUED:
                    Download(d);
                    break;
                case RUNNING:
                case PAUSED:
                    paused.append(d.getId(), d);
                    break;

                case CANCELLED:
                    this.completed.add(d);
                    // TODO: Replace "this.completed.add(d);" with "Commons.History.add(d);"
                    break;
                default:
                    if (d.getStatus().getConvert() == null) continue;
                    switch (d.getStatus().getConvert()) {
                        case RUNNING:
//                        case PAUSED:
//                            d.status.setConvert(Status.Convert.QUEUED);
//                            if (d.conv != null) {
//                                File file = new File(Directories.getBIN(), d.conv);
//                                if (file.exists() && !file.delete())
//                                    Log.w(TAG, "LoadPackage // Found Paused/Running in conversion Download and its conv file, yet failed to delete\nChanged status to QUEUED");
//                            }
                        case QUEUED:
                            if (d.getDown() == null || !new File(d.getDown()).exists()) {
                                Exception v = Validate(d);
                                if (v == null) {
                                    convertQueue.add(d);
                                } else {
//                                    d.exception = v;
                                    onFinish(d, false, v);
                                    Log.e(TAG, "Invalid Argument: " + v.getMessage());
                                }
                            } else convertQueue.add(d);
                            break;
                        default:
                            if (d.getStatus().getMetadata() == null) {
                                if (d.getConv() == null || !new File(d.getConv()).exists()) {
                                    Exception v = Validate(d);
                                    if (v == null) {
                                        if (d.getDown() == null || !new File(d.getDown()).exists())
                                            //                                            downloadQueue.add(d);
                                            Download(d);
                                        else convertQueue.add(d);
                                    } else {
                                        d.getStatus().setMetadata(false);
//                                        d.exception = v;
                                        onFinish(d, false, v);
                                        Log.e(TAG, "Invalid Argument :" + v.getMessage());
                                    }
                                } else Metadata(d);
                            } else {
                                Exception v = Validate(d);
                                if (v == null) {
                                    //                                    downloadQueue.add(d);
                                    Download(d);
                                    continue;
                                }
                                d.getStatus().setMetadata(false);
//                                d.exception = v;
                                onFinish(d, false, v);
                                Log.e(TAG, "Invalid Argument :" + v.getMessage());
                            }
                    }
            }
        }

        List<Integer> ids = new ArrayList<>();
        for (int i = 0; i < paused.size(); i++) ids.add(paused.get(paused.keyAt(i)).getId());

        for (long id : ids) {
            Commons.fetch.getDownloadsByRequestIdentifier(id, result -> {
                for (com.tonyodev.fetch2.Download d : result) {
                    switch (d.getStatus()) {
                        case ADDED:
                        case QUEUED:
                            paused.get(d.getId()).getStatus().setDownload(Status.Download.QUEUED);
                            break;
                        case DOWNLOADING:
                            paused.get(d.getId()).getStatus().setDownload(Status.Download.RUNNING);
                            break;
                        case PAUSED:
                            paused.get(d.getId()).getStatus().setDownload(Status.Download.PAUSED);
                            break;
                        case CANCELLED:
                        case DELETED:
                        case REMOVED:
                            paused.get(d.getId()).getStatus().setDownload(Status.Download.CANCELLED);
                            break;
                        case FAILED:
                            paused.get(d.getId()).getStatus().setDownload(Status.Download.FAILED);
                            break;
                        case COMPLETED:
                            Download p = paused.get(d.getId());
                            p.getStatus().setDownload(Status.Download.COMPLETE);
                            if (p.isConvert()) convertQueue.add(p);
                            else Metadata(p);
                            break;
                        default:
                            Download(paused.get(d.getId()));
                    }
                }
            });
        }
    }

    private Exception Validate(Download args) {
        if (args == null)
            return new IllegalArgumentException("Argument object is null");
        else if (args.getSparseArray() == null)
            return new IllegalArgumentException("No SparseArray found");
        else if (args.getTitle().isEmpty())
            return new IllegalArgumentException("No Title found");
        return null;
    }

    private void Download(@NonNull Download current) {
        short i = 0;
        if (!(Directories.getBIN().exists() || Directories.getBIN().mkdirs()))
            Log.w(TAG, "Failed to create BIN folder");
        else {
            ArrayList<Short> indexes = new ArrayList<>();
            for (String f : Directories.getBIN().list()) {
                String number = f.replaceFirst("^(\\d+).*?$", "$1");

                try {
                    indexes.add(Short.parseShort(number));
                } catch (NumberFormatException ignored) { }
            }
            while (indexes.contains(i)) i++;
        }

        if (current.getAvailableFormat() == null || current.getAvailableFormat().isEmpty()) {
            current.extractFromSparse();
            if (current.getUrl() == null) {
                onFinish(current, false, new IllegalArgumentException("Failed to generate URL from extractFromSparse"));
            }
        }

        current.getStatus().setConvert(null);
        current.getStatus().setMetadata(null);
        current.setDown(String.format(Locale.ENGLISH, "%d.%s", i, current.getAvailableFormat()));

        if (!Commons.fetch.getListenerSet().contains(fetchListener))
            Commons.fetch.addListener(fetchListener);

        if (current.getUrl() == null) current.extractFromSparse();
        Request request = new Request(current.getUrl(), Directories.getBIN().getAbsolutePath() + '/' + current.getDown());
        request.setNetworkType(Preferences.getMobiledata() ? NetworkType.ALL : NetworkType.WIFI_ONLY);
        request.setPriority(Priority.HIGH);
        request.setEnqueueAction(EnqueueAction.REPLACE_EXISTING);
        current.setId(request.getId());
        request.setGroupId(GROUP_ID);
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

        if (current.isConvert()) {
            if (converter == null) converter = AndroidAudioConverter.with(getApplicationContext());
            File conv = converter.setFile(new File(Directories.getBIN(), current.getDown()))
                    .setTrim(current.getStart(), current.getEnd())
                    .setNormalize(current.isNormalize())
                    .setFormat(AudioFormat.valueOf(current.getFormat().toUpperCase()))
                    .setBitrate(current.getBitrate())
                    .setCallback(new AndroidAudioConverter.IConvertCallback() {
                        @Override
                        public void onSuccess(File conv) {
                            convertProgress = null;
                            current.getStatus().setConvert(Status.Convert.COMPLETE);
                            current.setConv(conv.getName());
                            converter.killProcess();
                            if (convertQueue.size() > 0) Convert();
                            Metadata(current);
                        }

                        @Override
                        public void onProgress(long size, int c, int length) {
                            DownloadService.this.onProgress(c, length, size, length == 0, current);
                        }

                        @Override
                        public void onFailure(Exception e) {
                            converter.killProcess();
                            convertProgress = null;
                            current.getStatus().setConvert(Status.Convert.FAILED);

                            e.printStackTrace();
                            onFinish(current, new ServiceException("Failed to process download").setPayload(e));
                        }
                    })
                    .convert();
            current.getStatus().setConvert(Status.Convert.RUNNING);
            current.getStatus().setMetadata(null);
            if (conv != null) current.setConv(conv.getAbsolutePath());
            convertProgress = current;
        } else {
            current.setConv(current.getDown());
            current.getStatus().setConvert(Status.Convert.SKIPPED);
            current.getStatus().setMetadata(null);
            Metadata(current);
            if (convertProgress == null && convertQueue.size() > 0) Convert();
        }

    }

    private void Metadata(Download current) {
        if (current.getConv() == null) {
            current.getStatus().setInvalid(true);
            onFinish(current, false, new IllegalArgumentException("conv is null"));
            return;
        } else if (current.getConv().isEmpty()) {
            current.getStatus().setInvalid(true);
            onFinish(current, false, new IllegalArgumentException("conv is empty"));
            return;
        }

        onProgress(0L, 0L, true, current);

        MusicMetadataSet src_set = null;
        try {
            src_set = new MyID3().read(new File(current.getConv()));
        } catch (IOException e) {
            e.printStackTrace();
        }

        if (current.getOverwrite()) current.setMtdt(current.getFilenameWithExt());
        else {
            short w = 1;
            String mtdt = current.getFilename();
            String offset = mtdt;

            while (new File(Directories.getBIN(), offset + '.' + current.getFormat()).exists()) offset = mtdt + " (" + w++ + ')';

            current.setMtdt(offset + '.' + current.getFormat());
        }

        if (src_set == null) {
            Log.i(TAG, "No metadata in down");
            try {
                try (InputStream in = new FileInputStream(new File(Directories.getBIN(), current.getConv()))) {
                    try (OutputStream out = new FileOutputStream(new File(Directories.getMUSIC(), current.getMtdt()))) {
                        byte[] buf = new byte[1024];
                        int len;
                        while ((len = in.read(buf)) > 0) out.write(buf, 0, len);
                    }
                }
            } catch (IOException e) {
                e.printStackTrace();
                onFinish(current, new ServiceException("Error copying file from conv to mtdt since there is no metadata support").setPayload(e));
            }
        } else {
            MusicMetadata meta = new MusicMetadata("name");
            if (!current.getTitle().isEmpty())
                meta.setSongTitle(current.getTitle());
            if (!current.getArtist().isEmpty())
                meta.setArtist(current.getArtist());
            if (!current.getAlbum().isEmpty()) meta.setAlbum(current.getAlbum());
            if (current.getTrack() > 0) meta.setTrackNumber(current.getTrack());
            if (current.getYear() > 0) meta.setYear(String.valueOf(current.getYear()));

//            if (current.genres != null && current.genres.length > 0) {
//                StringBuilder str = new StringBuilder();
//
//                for (int i = 0; i < current.genres.length - 1; i++)
//                    str.append(current.genres[i]).append(", ");
//
//                str.append(current.genres[current.genres.length - 1]);
//                meta.setGenre(str.toString());
//            }
            if (!current.getGenres().isEmpty()) meta.setGenre(current.getGenres());

            try {
                new MyID3().write(
                        new File(current.getConv()),
                        new File(Directories.getMUSIC(), current.getMtdt()),
                        src_set,
                        meta
                );
            } catch (IOException | ID3WriteException e) {
                e.printStackTrace();
                current.setCompleteDate(new Date());
                current.getStatus().setMetadata(true);
                onFinish(current, new ServiceException("Metadata Failure").setPayload(e));
                return;
            }
        }

        current.setCompleteDate(new Date());
        current.getStatus().setMetadata(true);
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
        //        for (File f : Directories.getBIN().listFiles()) {
        //            if (!f.delete()) failed = true;
        //        }
        if (!(new File(Directories.getBIN(), args.getDown()).delete() &&
                new File(Directories.getBIN(), args.getConv()).delete()))
            Log.w(TAG, "Failed to delete cache from BIN");

        if (args.getCompleteDate() == null) args.setCompleteDate(new Date());
        args.setException(successful ? null : e);
        if (!completed.contains(args)) completed.add(args);

        for (int i = 0; i < callbacks.size(); i++) {
            ClientCallbacks c = callbacks.get(i);
            c.onDone(args, successful, e);
        }

        Intent intent = new Intent(TAG);
        if (e != null) intent.putExtra(InternalArgs.EXCEPTION, e);
        intent.putExtra(InternalArgs.RESULT, successful ? InternalArgs.SUCCESS : InternalArgs.FAILED);
        intent.putExtra(InternalArgs.DATA, args);
        intent.putExtra(InternalArgs.REMAINING, !CheckQueues());
        LocalBroadcastManager.getInstance(this).sendBroadcast(intent);
    }

    private void onFinish(@NonNull Download args, @NonNull ServiceException e) {
        onFinish(args, false, e);//.setStatus(args.status));
    }

    private void onProgress(long current, long total, boolean indeterminate, Download download) {
        onProgress(current, total, 0L, indeterminate, download);
    }

    private void onProgress(long current, long total, long size, boolean indeterminate, Download download) {
        download.setCurrent(current);
        download.setTotal(total);
        download.setSize(size);
        download.setIndeterminate(indeterminate);

        for (int i = 0; i < callbacks.size(); i++) {
            ClientCallbacks c = callbacks.get(i);
            c.onProgress(download, current, total, size, indeterminate);
        }

        Intent intent = new Intent(TAG);
        intent.putExtra(InternalArgs.CURRENT, current);
        intent.putExtra(InternalArgs.TOTAL, total);
        intent.putExtra(InternalArgs.SIZE, size);
        intent.putExtra(InternalArgs.INDETERMINATE, indeterminate);
        intent.putExtra(InternalArgs.DATA, download);
        intent.putExtra(InternalArgs.RESULT, InternalArgs.PROGRESS);
        LocalBroadcastManager.getInstance(this).sendBroadcast(intent);
    }

    @Override
    public void onDestroy() {
        Log.w(TAG, "Service Destroyed");

        Intent intent = new Intent(TAG);
        intent.putExtra(InternalArgs.RESULT, InternalArgs.DESTROY);
        intent.putExtra(InternalArgs.DATA, getAll());
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
        //        if (!Commons.saveQueue(queue.toArray(new Download[0]))) {
        //            Log.e(TAG, "Failed to save queue");
        //            Intent error = new Intent(TAG);
        //            error.putExtra(InternalArgs.RESULT, InternalArgs.ERR_LOAD);
        //            LocalBroadcastManager.getInstance(DownloadService.this).sendBroadcast(error);
        //
        //            if (callbacks != null) callbacks.onWarn(InternalArgs.ERR_LOAD);
        //        }

        DownloadService.super.onDestroy();
        //                    }
        //                });
        //            }
        //        });

    }

//    public void pause(int id) {
//        if (convertProgress != null && convertProgress.getId() == id) {
//            converter.killProcess();
//            convertPause.add(convertProgress);
//            convertProgress.setPause(true);
//            convertProgress = null;
//            Convert();
//        } else {
//            Commons.fetch.pause(id);
//            Download d = downloadMap.get(id);
//            if (d != null) d.setPause(true);
//        }
//    }
    
//    public void pauseAll(boolean includeConvert) {
//        if (includeConvert) cpause = true;
//        pause = true;
//        Commons.fetch.pauseGroup(GROUP_ID);
//    }
//
//    public void resume(int id) {
//        boolean not = true;
//        for (Download d : convertPause) {
//            if (d.getId() == id) {
//                convertQueue.add(d);
//                d.setPause(false);
//                Convert();
//                not = false;
//                break;
//            }
//        }
//        if (not) {
//            Commons.fetch.resume(id);
//            Download d = downloadMap.get(id);
//            if (d != null) d.setPause(true);
//        }
//    }
//
//    public void resumeAll() {
//        cpause = false;
//        pause = false;
//        Convert();
//        Commons.fetch.resumeGroup(GROUP_ID);
//    }
//
//    public boolean isPaused() {
//        return cpause || pause;
//    }
//
//    public boolean isCpause() {
//        return cpause;
//    }

    public void cancel(int id) {
        if (convertProgress != null && convertProgress.getId() == id) {
            if (converter != null) converter.killProcess();
            convertProgress.setCompleteDate(new Date());
            convertProgress.setCurrent(0);
            convertProgress.setTotal(0);
            convertProgress.setSize(0);
            convertProgress.setIndeterminate(true);
            convertProgress.getStatus().setConvert(Status.Convert.CANCELLED);
            onFinish(convertProgress, false, new ServiceException("Cancelled"));
            convertProgress = null;
            Convert();
        } else if (downloadMap.indexOfKey(id) >= 0) Commons.fetch.cancel(id);
    }

    private void UpdateMaps(com.tonyodev.fetch2.Download download, Download args) {
        switch (download.getStatus()) {
            case QUEUED:
            case ADDED:
                args.getStatus().setDownload(Status.Download.QUEUED);
                break;
            case DOWNLOADING:
                args.getStatus().setDownload(Status.Download.RUNNING);
                break;
            case PAUSED:
                args.getStatus().setDownload(Status.Download.PAUSED);
                break;
            case COMPLETED:
                args.getStatus().setDownload(Status.Download.COMPLETE);
                break;
            case CANCELLED:
            case DELETED:
                args.getStatus().setDownload(Status.Download.CANCELLED);
                break;
            case FAILED:
            case REMOVED:
                args.getStatus().setDownload(Status.Download.FAILED);
                break;
            default:
                args.getStatus().setDownload(null);
        }
    }

    private void UpdateProgress(@NonNull com.tonyodev.fetch2.Download download, @NonNull String tagPrefix, @Nullable Status.Download status) {
        Download args = downloadMap.get(download.getRequest().getId());
        if (args == null) {
            Log.w(TAG, tagPrefix + ": " + MISSING_IN_MAP);
            return;
        }

        if (status == null)
            UpdateMaps(download, args);
        else args.getStatus().setDownload(status);

        DownloadService.this.onProgress(download.getDownloaded(), download.getTotal(), false, args);
    }

    private void UpdateCancelled(@NonNull com.tonyodev.fetch2.Download download, String tagPrefix) {
        Download args = downloadMap.get(download.getRequest().getId());
        if (args == null) {
            Log.w(TAG, tagPrefix + ": " + MISSING_IN_MAP);
            return;
        }
        args.getStatus().setDownload(Status.Download.CANCELLED);

        downloadMap.remove(download.getId());
        completed.add(args);
        DownloadService.this.onProgress(download.getDownloaded(), download.getTotal(), false, args);
    }

    public void Sanitize() {
        for (int i = 0; i < downloadMap.size(); i++) {
            Download d = downloadMap.valueAt(i);
            if (d == null) {
                downloadMap.removeAt(i);
            } else if (d.getStatus().getDownload() == null) {
                Exception v = Validate(d);
                if (v == null) {
                    d.getStatus().setDownload(Status.Download.QUEUED);
                } else {
                    downloadMap.removeAt(i);
                    onFinish(d, false, v);
                    Log.e(TAG, "Invalid Argument :" + v.getMessage());
                    d.getStatus().setDownload(Status.Download.FAILED);
                    d.setException(v);
                }
            }
        }

        for (Download d : convertQueue) {
            if (d.getStatus().getConvert() == null) {
                Exception v = Validate(d);
                if (v == null) {
                    d.getStatus().setConvert(Status.Convert.QUEUED);
                } else {
                    convertQueue.remove(d);
                    d.getStatus().setConvert(Status.Convert.FAILED);
                    onFinish(d, false, v);
                    Log.e(TAG, "Invalid Argument :" + v.getMessage());
//                    d.exception = v;
                }
            } else if (d.getDown() == null || !new File(Directories.getBIN(), d.getDown()).exists()) {
                convertQueue.remove(d);
                Download(d);
            } else d.getStatus().setConvert(Status.Convert.QUEUED);
        }

        if (convertProgress != null) {
            if (convertProgress.getStatus().getConvert() == null) {
                Exception v = Validate(convertProgress);
                if (v == null) {
                    convertProgress.getStatus().setConvert(Status.Convert.QUEUED);
                } else {
                    Log.e(TAG, "Invalid Argument :" + v.getMessage());
                    convertProgress.getStatus().setConvert(Status.Convert.FAILED);
//                    convertProgress.exception = v;
                    onFinish(convertProgress, false, v);
                    convertProgress = null;
                }
            } else convertProgress.getStatus().setConvert(Status.Convert.RUNNING);
        }
    }

    private void stop() {
        boolean bad = false;
        for (File file : Directories.getBIN().listFiles()) {
            if (!file.delete()) bad = true;
        }

        if (bad) Log.w(TAG, "Failed to empty BIN");
    }

    public Download[] getAll() {
        Download[] downloads = new Download[downloadMap.size()];
        for (int i = 0; i < downloadMap.size(); i++) downloads[i] = downloadMap.valueAt(i);
        Download[] list = ArrayUtils.concat(downloads, convertQueue.toArray(new Download[0]), completed.toArray(new Download[0]));
        return convertProgress == null ? list : ArrayUtils.appendToArray(list, convertProgress);
    }

    public Download[] getQueue() {
        Download[] list = new Download[downloadMap.size()];
        for (int i = 0; i < downloadMap.size(); i++) {
            Download d = downloadMap.valueAt(i);
            if (d.getStatus().getDownload() == Status.Download.QUEUED) list[i] = d;
        }
        return list;
    }

//    public Download[] getConvertPaused() {
//        return convertPause.toArray(new Download[0]);
//    }

    public Download[] getRunning() {
        Download[] downloads = new Download[downloadMap.size()];
        for (int i = 0; i < downloadMap.size(); i++) {
            Download d = downloadMap.valueAt(i);
            if (d.getStatus().getDownload() == Status.Download.RUNNING || d.getStatus().getDownload() == Status.Download.PAUSED)
                downloads[i] = downloadMap.valueAt(i);
        }
        Download[] list = ArrayUtils.concat(downloads, convertQueue.toArray(new Download[0]));
        return convertProgress == null ? list : ArrayUtils.appendToArray(list, convertProgress);
    }

    public Download[] getCompleted() {
        return completed.toArray(new Download[0]);
    }

    private void CallbackWarn(Download download, String msg) {
        for (Iterator<ClientCallbacks> iterator = callbacks.iterator(); iterator.hasNext();) {
            ClientCallbacks c = iterator.next();
            if (c == null) iterator.remove();
            else c.onWarn(download, msg);
        }
    }

    class LocalBinder extends Binder {
        DownloadService getService() {
            return DownloadService.this;
        }
    }

    public static class ServiceException extends RuntimeException {
        private Exception payload;

        private ServiceException(String msg) {
            super(msg);
        }

        private ServiceException setPayload(Exception payload) {
            this.payload = payload;
            return this;
        }

        public Exception getPayload() {
            return payload;
        }
    }
}
