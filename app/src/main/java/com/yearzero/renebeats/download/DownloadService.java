package com.yearzero.renebeats.download;

import android.app.Service;
import android.content.Intent;
import android.os.Binder;
import android.os.IBinder;
import android.util.Log;
import android.util.SparseArray;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.localbroadcastmanager.content.LocalBroadcastManager;

import com.tonyodev.fetch2.EnqueueAction;
import com.tonyodev.fetch2.Error;
import com.tonyodev.fetch2.FetchListener;
import com.tonyodev.fetch2.NetworkType;
import com.tonyodev.fetch2.Priority;
import com.tonyodev.fetch2.Request;
import com.tonyodev.fetch2core.DownloadBlock;
import com.yearzero.renebeats.AndroidAudioConverter;
import com.yearzero.renebeats.AudioFormat;
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
import java.nio.channels.FileChannel;
import java.util.ArrayList;
import java.util.Date;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;
import java.util.Locale;

public class DownloadService extends Service {

	public static final String TAG = "DownloadService";
	public static final String MISSING_IN_MAP = "Request Queue: A Download in Fetch is not in HashMap (IGNORED)";
	//    public static final long ID_HEADER = 0x80860000L;
	private static final int GROUP_ID = 0xC560_6A11;

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
			onFinish(args, new ServiceException(msg));
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

	private @Nullable AndroidAudioConverter converter;
	private final ArrayList<ClientCallbacks> callbacks = new ArrayList<>();
	private final LinkedList<Download> convertQueue = new LinkedList<>();
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
		if (intent == null)
			return START_STICKY;

		int requestFlag = intent.getIntExtra(InternalArgs.REQUEST, 0);
		if (requestFlag != 0) {
			Intent req = new Intent(TAG);

			req.putExtras(intent);

			Sanitize();
			int request = intent.getIntExtra(InternalArgs.REQ_ID, Integer.MIN_VALUE);
			if (request != Integer.MIN_VALUE) req.putExtra(InternalArgs.REQ_ID, request);

			if ((requestFlag & InternalArgs.FLAG_QUEUE) == InternalArgs.FLAG_QUEUE)
				req.putExtra(InternalArgs.REQ_QUEUE, getQueue().toArray(new Download[0]));

			if ((requestFlag & InternalArgs.FLAG_RUNNING) == InternalArgs.FLAG_RUNNING)
				req.putExtra(InternalArgs.REQ_RUNNING, getRunning().toArray(new Download[0]));

			if ((requestFlag & InternalArgs.FLAG_COMPLETED) == InternalArgs.FLAG_COMPLETED)
				req.putExtra(InternalArgs.REQ_COMPLETED, getCompleted().toArray(new Download[0]));

			LocalBroadcastManager.getInstance(DownloadService.this).sendBroadcast(req);

			if (isServiceIdling()) stop();
			return START_STICKY;
		}


		Download current = null;
		try {
			current = (Download) intent.getSerializableExtra(InternalArgs.DATA);
		} catch (ClassCastException e) {
			e.printStackTrace();
			if (isServiceIdling()) {
				stop();
				return START_NOT_STICKY;
			}
		}

		if (current == null) {
			if (isServiceIdling()) stop();
			return START_STICKY;
		}

		Exception v = AssertValidDownloadRequest(current);
		if (v != null) {
			Log.e(TAG, "Invalid Argument: " + v.getMessage());
			current.getStatus().setInvalid(true);
			RecordInHistory(current);
			onFinish(current, false, v);
			return START_STICKY;
		}

		current.getStatus().setDownload(Status.Download.QUEUED);
		current.getStatus().setConvert(null);
		current.getStatus().setMetadata(null);

		RecordInHistory(current);

		onProgress(0, 0, true, current);
		Download(current);
		return START_STICKY;
	}

	private void RecordInHistory(Download current) {
		Exception e = HistoryRepo.record(HistoryLog.generate(current));
		if (e != null) {
			Log.e(TAG, "Failed to record download history");
			e.printStackTrace();
		}
	}

	private Exception AssertValidDownloadRequest(Download args) {
		if (args == null)
			return new IllegalArgumentException("Argument object is null");
		else if (args.getUrl() == null)
			return new IllegalArgumentException("No URL found");
		else if (args.getTitle().isEmpty())
			return new IllegalArgumentException("No Title found");
		return null;
	}

	private void Download(@NonNull Download current) {
		if (!Directories.getBIN().exists() && !Directories.getBIN().mkdirs())
			Log.w(TAG, "Failed to create BIN folder");
		else current.setDown(String.format(Locale.ENGLISH, "%s.%s", HistoryRepo.getFilename(current), current.getAvailableFormat()));

		current.getStatus().setConvert(null);
		current.getStatus().setMetadata(null);

		if (!Commons.fetch.getListenerSet().contains(fetchListener))
			Commons.fetch.addListener(fetchListener);

		Request request = new Request(
				current.getUrl(),
				new File(Directories.getBIN(), current.getDown()).getAbsolutePath()
		);
		request.setNetworkType(Preferences.getMobiledata() ? NetworkType.ALL : NetworkType.WIFI_ONLY);
		request.setPriority(Priority.HIGH);
		request.setEnqueueAction(EnqueueAction.REPLACE_EXISTING);
		current.setDownloadId(request.getId());
		request.setGroupId(GROUP_ID);
		downloadMap.put(request.getId(), current);
		Commons.fetch.enqueue(request, null, null);
	}

	private void Convert() {
		if (convertProgress != null) return;
		final Download current = convertQueue.poll();
		if (current == null) {
			if (isServiceIdling()) stop();
			return;
		}

		if (!current.isConvert()) {
			current.setConv(current.getDown());
			current.getStatus().setConvert(Status.Convert.SKIPPED);
			current.getStatus().setMetadata(null);
			Metadata(current);
			if (convertProgress == null && convertQueue.size() > 0) Convert();

			return;
		}

		if (converter == null)
			converter = new AndroidAudioConverter(getApplicationContext());

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
						DownloadService.this.onProgress(
								c, length, size, length == 0, current
						);
					}

					@Override
					public void onFailure(Exception e) {
						converter.killProcess();
						convertProgress = null;
						current.getStatus().setConvert(Status.Convert.FAILED);

						e.printStackTrace();
						onFinish(current, new ServiceException("Failed to process download", e));
					}
				})
				.convert();

		current.getStatus().setConvert(Status.Convert.RUNNING);
		current.getStatus().setMetadata(null);
		if (conv != null)
			current.setConv(conv.getAbsolutePath());
		convertProgress = current;
	}

	private void Metadata(Download current) {
		if (current.getConv() == null || current.getConv().isEmpty()) {
			current.getStatus().setInvalid(true);
			Exception exception = new IllegalArgumentException("No converted file found.");
			onFinish(current, false, exception);
			return;
		}

		onProgress(0L, 0L, true, current);

		if (current.getOverwrite()) 
			current.setMtdt(current.getFilenameWithExt());
		else {
			String filename = current.getFilename();
			String metadataFilename = avoidFilenameConflict(current, filename);
			current.setMtdt(metadataFilename);
		}

		if (writeMetadata(current)) {
			current.setCompleteDate(new Date());
			current.getStatus().setMetadata(true);
			onFinish(current, true, null);
			return;
		}

		Log.w(TAG, "Failed to write metadata. Copying files instead...");

		File convertedFile = new File(Directories.getBIN(), current.getConv());
		File metadataFile = new File(Directories.getMUSIC(), current.getMtdt());
		if (copyFile(convertedFile, metadataFile)) {
			current.setCompleteDate(new Date());
			current.getStatus().setMetadata(false);
			onFinish(current, true, null);
			return;
		}

		current.setCompleteDate(new Date());
		current.getStatus().setMetadata(true);
		onFinish(current, new ServiceException("Failed to copy file to output folder."));
	}

	private boolean writeMetadata(Download download) {
		File convertedFile = new File(Directories.getBIN(), download.getConv());
		File metadataFile = new File(Directories.getMUSIC(), download.getMtdt());

		MusicMetadata metadata = new MusicMetadata("name");
		metadata.setSongTitle(download.getTitle());
		metadata.setArtist(download.getArtist());
		metadata.setAlbum(download.getAlbum());
		metadata.setTrackNumber(download.getTrack());
		metadata.setYear(String.valueOf(download.getYear()));
		metadata.setGenre(download.getGenres());

		MyID3 metadataEngine = new MyID3();
		try {
			MusicMetadataSet metadataSet = metadataEngine.read(convertedFile);
			metadataEngine.write(convertedFile, metadataFile, metadataSet, metadata);
			return true;
		} catch (Exception e) {
			e.printStackTrace();
			return false;
		}
	}

	private boolean copyFile(File source, File destination) {
		try (FileInputStream in = new FileInputStream(source);
			 FileOutputStream out = new FileOutputStream(destination)) {
			FileChannel inChannel = in.getChannel();
			FileChannel outChannel = out.getChannel();
			inChannel.transferTo(0, inChannel.size(), outChannel);
			return true;
		} catch (IOException e) {
			e.printStackTrace();
			return false;
		}
	}

	private String avoidFilenameConflict(Download current, String filename) {
		String nonConflictFilename = filename;
		String extension = current.getFormat();
		int i = 1;
		while (new File(Directories.getBIN(), nonConflictFilename + '.' + extension).exists())
			nonConflictFilename = filename + " (" + i++ + ')';
		
		return nonConflictFilename + '.' + extension;
	}

	//endregion

	private boolean isServiceIdling() {
		return downloadMap.size() == 0 &&
				convertQueue.size() == 0 &&
				convertProgress == null;
	}

	private void onFinish(Download args, boolean successful, @Nullable Exception e) {
		if (args.getDown() == null)
			Log.w(TAG, "Download received does not have a down");
		else if (args.getConv() == null)
			Log.w(TAG, "Download received does not have a conv");
		else if (!(new File(Directories.getBIN(), args.getDown()).delete() && new File(Directories.getBIN(), args.getConv()).delete()))
			Log.w(TAG, "Failed to delete cache from BIN");

		if (args.getCompleteDate() == null) args.setCompleteDate(new Date());
		args.setException(successful ? null : e);
		if (!completed.contains(args)) completed.add(args);

		Exception f = HistoryRepo.completeRecord(HistoryLog.generate(args));
		if (f != null) {
			Log.e(TAG, "Failed to record download history");
			f.printStackTrace();
		}

		for (int i = 0; i < callbacks.size(); i++) {
			ClientCallbacks c = callbacks.get(i);
			c.onDone(args, successful, e);
		}

		Intent intent = new Intent(TAG);
		if (e != null) intent.putExtra(InternalArgs.EXCEPTION, e);
		intent.putExtra(InternalArgs.RESULT, successful ? InternalArgs.SUCCESS : InternalArgs.FAILED);
		intent.putExtra(InternalArgs.DATA, args);
		intent.putExtra(InternalArgs.REMAINING, !isServiceIdling());
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
		intent.putExtra(InternalArgs.DATA, getAll().toArray(new Download[0]));
		LocalBroadcastManager.getInstance(this).sendBroadcast(intent);

		Commons.fetch.removeListener(fetchListener);

		DownloadService.super.onDestroy();
	}

	public void cancel(int id) {
		Download current = null;
		int index = downloadMap.indexOfKey(id);
		if (convertProgress != null && convertProgress.getDownloadId() == id) {
			if (converter != null) converter.killProcess();
			convertProgress.setCompleteDate(new Date());
			convertProgress.setCurrent(0);
			convertProgress.setTotal(0);
			convertProgress.setSize(0);
			convertProgress.setIndeterminate(true);
			convertProgress.getStatus().setConvert(Status.Convert.CANCELLED);
			current = convertProgress;
			onFinish(convertProgress, false, new ServiceException("Cancelled"));
			convertProgress = null;
			Convert();
		} else if (index >= 0) {
			Commons.fetch.cancel(id);
			current = downloadMap.valueAt(index);
		}

		Intent intent = new Intent(TAG);
		intent.putExtra(InternalArgs.DATA, current);
		intent.putExtra(InternalArgs.RESULT, InternalArgs.CANCELLED);
		LocalBroadcastManager.getInstance(this).sendBroadcast(intent);
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
				Exception v = AssertValidDownloadRequest(d);
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
				Exception v = AssertValidDownloadRequest(d);
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
				Exception v = AssertValidDownloadRequest(convertProgress);
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
		emptyBin();
	}

	private void emptyBin() {
		File[] files = Directories.getBIN().listFiles();
		if (files == null) {
			Log.v(TAG, "Failed to list all files in BIN");
			Log.w(TAG, "Failed to empty BIN");
			return;
		}

		boolean bad = false;
		for (File file : files) {
			if (!file.delete()) bad = true;
		}

		if (bad) Log.w(TAG, "Failed to empty BIN");
	}

	public List<Download> getAll() {
		ArrayList<Download> downloads = new ArrayList<>();
		for (int i = 0; i < downloadMap.size(); i++) downloads.add(downloadMap.valueAt(i));
		downloads.addAll(convertQueue);
		downloads.addAll(completed);
		if (convertProgress != null) downloads.add(convertProgress);
		return downloads;
	}

	public List<Download> getQueue() {
		ArrayList<Download> list = new ArrayList<>();
		for (int i = 0; i < downloadMap.size(); i++) {
			Download d = downloadMap.valueAt(i);
			Status.Download sd = d.getStatus().getDownload();
			if (sd == Status.Download.NETWORK_PENDING || sd == Status.Download.QUEUED) list.add(d);
		}
		return list;
	}

	public List<Download> getRunning() {
		ArrayList<Download> downloads = new ArrayList<>();
		for (int i = 0; i < downloadMap.size(); i++) {
			Download d = downloadMap.valueAt(i);
			if (d.getStatus().getDownload() == Status.Download.RUNNING || d.getStatus().getDownload() == Status.Download.PAUSED)
				downloads.add(downloadMap.valueAt(i));
		}
		downloads.addAll(convertQueue);
		if (convertProgress != null) downloads.add(convertProgress);
		return downloads;
	}

	public List<Download> getCompleted() {
		return new ArrayList<>(completed);
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

		private ServiceException(String msg, @NonNull Exception exp) {
			super(msg + " | " + exp.getMessage());
		}

		public Exception getPayload() {
			return payload;
		}
	}
}
