package com.yearzero.renebeats.download;

import android.app.Service;
import android.content.Context;
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
import com.tonyodev.fetch2.Fetch;
import com.tonyodev.fetch2.FetchConfiguration;
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

import org.cmc.music.metadata.MusicMetadata;
import org.cmc.music.metadata.MusicMetadataSet;
import org.cmc.music.myid3.MyID3;
import org.jetbrains.annotations.NotNull;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.channels.FileChannel;
import java.security.InvalidParameterException;
import java.util.ArrayList;
import java.util.Date;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;

public class DownloadService extends Service {

	public static final String TAG = "DownloadService";
	public static final String MISSING_IN_MAP = "Request Queue: A Download in Fetch is not in HashMap (IGNORED)";
	//    public static final long ID_HEADER = 0x80860000L;
	private static final int GROUP_ID = 0xC560_6A11;

	private final FetchListener fetchListener = new FetchListener() {
		@Override
		public void onAdded(@NotNull com.tonyodev.fetch2.Download download) {
			updateProgress(download, "onAdded", Status.Download.QUEUED);
		}

		@Override
		public void onQueued(@NotNull com.tonyodev.fetch2.Download download, boolean b) {
			updateProgress(download, "onQueued", Status.Download.QUEUED);
		}

		@Override
		public void onWaitingNetwork(@NotNull com.tonyodev.fetch2.Download download) {
			updateProgress(download, "onWaitingNetwork", Status.Download.NETWORK_PENDING);
		}

		@Override
		public void onCompleted(@NotNull com.tonyodev.fetch2.Download download) {
			Download args = downloadQueue.get(download.getRequest().getId());
			if (args == null) {
				Log.w(TAG, "onCompleted" + MISSING_IN_MAP);
				return;
			}

			convertQueue.add(args);
			downloadQueue.remove(download.getId());
			args.getStatus().setDownload(Status.Download.COMPLETE);
			args.getStatus().setConvert(Status.Convert.QUEUED);
			DownloadService.this.onProgress(1L, 1L, false, args);
			Convert();
		}

		@Override
		public void onError(@NotNull com.tonyodev.fetch2.Download download, @NotNull Error error, @org.jetbrains.annotations.Nullable Throwable throwable) {
			String msg = "Download Error" + (throwable == null ? "" : throwable.getMessage());
			Log.e(TAG, msg);

			Download args = downloadQueue.get(download.getRequest().getId());
			if (args == null) {
				Log.w(TAG, "onError: " + MISSING_IN_MAP);
				return;
			}

			args.getStatus().setDownload(Status.Download.FAILED);
			completed.add(args);
			downloadQueue.remove(download.getId());
			onFinish(args, new ServiceException(msg));
		}

		@Override
		public void onDownloadBlockUpdated(@NotNull com.tonyodev.fetch2.Download download, @NotNull DownloadBlock downloadBlock, int i) {
		}


		@Override
		public void onStarted(@NotNull com.tonyodev.fetch2.Download download, @NotNull List<? extends DownloadBlock> list, int i) {
			updateProgress(download, "onStarted", Status.Download.RUNNING);
		}

		@Override
		public void onProgress(@NotNull com.tonyodev.fetch2.Download download, long etaMilli, long Bps) {
			updateProgress(download, "onProgress", Status.Download.RUNNING);
		}

		@Override
		public void onPaused(@NotNull com.tonyodev.fetch2.Download download) {
			Download args = downloadQueue.get(download.getRequest().getId());
			if (args == null) {
				Log.w(TAG, "onPaused: " + MISSING_IN_MAP);
				return;
			}

			args.getStatus().setDownload(Status.Download.PAUSED);
			DownloadService.this.onProgress(download.getDownloaded(), download.getTotal(), false, args);
		}

		@Override
		public void onResumed(@NotNull com.tonyodev.fetch2.Download download) {
			updateProgress(download, "onResumed", Status.Download.RUNNING);
		}

		@Override
		public void onCancelled(@NotNull com.tonyodev.fetch2.Download download) {
			updateCancelled(download, "onCancelled");
		}

		@Override
		public void onRemoved(@NotNull com.tonyodev.fetch2.Download download) {
			downloadQueue.remove(download.getId());
			updateCancelled(download, "onRemoved");
		}

		@Override
		public void onDeleted(@NotNull com.tonyodev.fetch2.Download download) {
			updateCancelled(download, "onDeleted");
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
	private @Nullable Download runningConversion;
	private final ArrayList<Download> completed = new ArrayList<>();
	private final SparseArray<Download> downloadQueue = new SparseArray<>();

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

		if (intent.hasExtra(InternalArgs.REQUEST)) {
			int requestFlag = intent.getIntExtra(InternalArgs.REQUEST, 0);
			processServiceStatusRequest(intent, requestFlag);
			stopIfIdling();
			return START_STICKY;
		}

		Download current = null;
		try {
			current = (Download) intent.getSerializableExtra(InternalArgs.DATA);
		} catch (ClassCastException e) {
			e.printStackTrace();
			stopIfIdling();
		}

		if (current == null) {
			stopIfIdling();
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

	private void processServiceStatusRequest(Intent intent, int requestFlag) {
		Intent req = new Intent(TAG);
		req.putExtras(intent);

		sanitizeAllQueues();

		int request = intent.getIntExtra(InternalArgs.REQ_ID, 0);

		if (intent.hasExtra(InternalArgs.REQ_ID))
			req.putExtra(InternalArgs.REQ_ID, request);

		if ((requestFlag & InternalArgs.FLAG_QUEUE) == InternalArgs.FLAG_QUEUE)
			req.putExtra(InternalArgs.REQ_QUEUE, getQueue().toArray(new Download[0]));

		if ((requestFlag & InternalArgs.FLAG_RUNNING) == InternalArgs.FLAG_RUNNING)
			req.putExtra(InternalArgs.REQ_RUNNING, getRunning().toArray(new Download[0]));

		if ((requestFlag & InternalArgs.FLAG_COMPLETED) == InternalArgs.FLAG_COMPLETED)
			req.putExtra(InternalArgs.REQ_COMPLETED, getCompleted().toArray(new Download[0]));

		LocalBroadcastManager.getInstance(DownloadService.this).sendBroadcast(req);
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
		else
			current.setDown(String.format(Locale.ENGLISH, "%s.%s", HistoryRepo.getFilename(current), current.getAvailableFormat()));

		current.getStatus().setConvert(null);
		current.getStatus().setMetadata(null);

		if (!Commons.fetch.getListenerSet().contains(fetchListener))
			Commons.fetch.addListener(fetchListener);

		Objects.requireNonNull(current.getUrl());
		Objects.requireNonNull(current.getDown());
		Request request = new Request(
				current.getUrl(),
				new File(Directories.getBIN(), current.getDown()).getAbsolutePath()
		);
		request.setNetworkType(Preferences.getMobiledata() ? NetworkType.ALL : NetworkType.WIFI_ONLY);
		request.setPriority(Priority.HIGH);
		request.setEnqueueAction(EnqueueAction.REPLACE_EXISTING);
		current.setDownloadId(request.getId());
		request.setGroupId(GROUP_ID);
		downloadQueue.put(request.getId(), current);
		Commons.fetch.enqueue(request, null, null);
	}

	private void Convert() {
		if (runningConversion != null) return;
		final Download current = convertQueue.poll();
		if (current == null) {
			stopIfIdling();
			return;
		}

		if (!current.getConvert()) {
			current.setConv(current.getDown());
			current.getStatus().setConvert(Status.Convert.SKIPPED);
			current.getStatus().setMetadata(null);
			Metadata(current);
			if (runningConversion == null && convertQueue.size() > 0) Convert();

			return;
		}

		if (converter == null)
			converter = new AndroidAudioConverter(getApplicationContext());

		if (current.getDown() == null) {
			ServiceException exception = new ServiceException("Cannot convert audio file. Filename lost.");
			Log.e(TAG, exception.getMessage());
			onFinish(current, false, exception);
			return;
		}

		File conv = converter.setFile(new File(Directories.getBIN(), current.getDown()))
				.setTrim(current.getStart(), current.getEnd())
				.setNormalize(current.getNormalize())
				.setFormat(AudioFormat.valueOf(current.getFormat().toUpperCase()))
				.setBitrate(current.getBitrate())
				.setCallback(new AndroidAudioConverter.IConvertCallback() {
					@Override
					public void onSuccess(File conv) {
						runningConversion = null;
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
						runningConversion = null;
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
		runningConversion = current;
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

		Objects.requireNonNull(current.getMtdt());
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
		Objects.requireNonNull(download.getConv());
		Objects.requireNonNull(download.getMtdt());
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

	private String avoidFilenameConflict(Download current, String filename) {
		String nonConflictFilename = filename;
		String extension = current.getFormat();
		int i = 1;
		while (new File(Directories.getBIN(), nonConflictFilename + '.' + extension).exists())
			nonConflictFilename = filename + " (" + i++ + ')';

		return nonConflictFilename + '.' + extension;
	}

	//endregion

	private boolean isServiceBusy() {
		return downloadQueue.size() != 0 ||
				convertQueue.size() != 0 ||
				runningConversion != null;
	}

	private void onFinish(Download args, boolean successful, @Nullable Exception e) {
		deleteCachedDownload(args);

		args.setCompleteDate(new Date());
		args.setException(successful ? null : e);
		if (!completed.contains(args))
			completed.add(args);

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
		intent.putExtra(InternalArgs.REMAINING, isServiceBusy());
		LocalBroadcastManager.getInstance(this).sendBroadcast(intent);
	}

	private void deleteCachedDownload(Download args) {
		if (args.getDown() == null) {
			Log.w(TAG, "Download received does not have a down");
			return;
		}
		if (args.getConv() == null) {
			Log.w(TAG, "Download received does not have a conv");
			return;
		}

		if (!new File(Directories.getBIN(), args.getDown()).delete()
				|| !new File(Directories.getBIN(), args.getConv()).delete())
			Log.w(TAG, "Failed to delete cache from BIN");
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

	private void onFinish(@NonNull Download args, @NonNull ServiceException e) {
		onFinish(args, false, e);//.setStatus(args.status));
	}

	private void onProgress(long current, long total, boolean indeterminate, Download download) {
		onProgress(current, total, 0L, indeterminate, download);
	}

	private void onProgress(long current,
							long total,
							long size,
							boolean indeterminate,
							Download download) {
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
		int index = downloadQueue.indexOfKey(id);
		if (runningConversion != null && runningConversion.getDownloadId() == id) {
			if (converter != null) converter.killProcess();
			runningConversion.setCompleteDate(new Date());
			runningConversion.setCurrent(0);
			runningConversion.setTotal(0);
			runningConversion.setSize(0);
			runningConversion.setIndeterminate(true);
			runningConversion.getStatus().setConvert(Status.Convert.CANCELLED);
			current = runningConversion;
			onFinish(runningConversion, false, new ServiceException("Cancelled"));
			runningConversion = null;
			Convert();
		} else if (index >= 0) {
			Commons.fetch.cancel(id);
			current = downloadQueue.valueAt(index);
		}

		Intent intent = new Intent(TAG);
		intent.putExtra(InternalArgs.DATA, current);
		intent.putExtra(InternalArgs.RESULT, InternalArgs.CANCELLED);
		LocalBroadcastManager.getInstance(this).sendBroadcast(intent);
	}

	private void updateProgress(
			@NonNull com.tonyodev.fetch2.Download download,
			@NonNull String tagPrefix,
			@NonNull Status.Download status
	) {
		Download args = downloadQueue.get(download.getRequest().getId());
		if (args == null) {
			Log.w(TAG, tagPrefix + ": " + MISSING_IN_MAP);
			return;
		}

		args.getStatus().setDownload(status);

		DownloadService.this.onProgress(
				download.getDownloaded(),
				download.getTotal(),
				false,
				args
		);
	}

	private void updateCancelled(@NonNull com.tonyodev.fetch2.Download download, String tagPrefix) {
		Download args = downloadQueue.get(download.getRequest().getId());
		if (args == null) {
			Log.w(TAG, tagPrefix + ": " + MISSING_IN_MAP);
			return;
		}
		args.getStatus().setDownload(Status.Download.CANCELLED);

		downloadQueue.remove(download.getId());
		completed.add(args);
		DownloadService.this.onProgress(
				download.getDownloaded(),
				download.getTotal(),
				false,
				args
		);
	}

	public void sanitizeAllQueues() {
		sanitizeDownloadQueue();
		sanitizeConvertQueue();
		// No need to sanitize Metadata since it does not have a queue
	}

	private void sanitizeDownloadQueue() {
		for (int i = 0; i < downloadQueue.size(); i++) {
			Download download = downloadQueue.valueAt(i);
			boolean isValid = sanitizeDownloadTask(download);
			if (!isValid)
				downloadQueue.remove(download.getDownloadId());
		}
	}

	/**
	 * Checks if download is valid. If not, fail it and report it for deletion.
	 * @return If true, this conversion task is valid. Otherwise, remove it from the queue.
	 */
	private boolean sanitizeDownloadTask(Download download) {
		if (download == null)
			return false;

		// Check if Download has not been started at all
		if (download.getStatus().getDownload() != null)
			return true;

		Exception v = AssertValidDownloadRequest(download);

		if (v == null) {
			download.getStatus().setDownload(Status.Download.QUEUED);
			return true;
		}

		onFinish(download, false, v);
		Log.e(TAG, "Invalid Argument :" + v.getMessage());
		download.getStatus().setDownload(Status.Download.FAILED);
		download.setException(v);
		return false;
	}

	private void sanitizeConvertQueue() {
		for (Download download : convertQueue) {
			boolean isValid = sanitizeConvertTask(download);
			if (!isValid)
				convertQueue.remove(download);
		}

		if (runningConversion != null) {
			boolean isValid = sanitizeConvertTask(runningConversion);
			if (!isValid)
				runningConversion = null;
		}
	}

	/**
	 * Checks if download is valid. If not, fail it and report it for deletion.
	 * @return If true, this conversion task is valid. Otherwise, remove it from the queue.
	 */
	private boolean sanitizeConvertTask(Download download) {
		if (download == null)
			return false;

		if (download.getStatus().getConvert() != null) {
			if (download.getDown() != null
					&& new File(Directories.getBIN(), download.getDown()).exists()) {

				download.getStatus().setConvert(Status.Convert.QUEUED);
				return true;
			}

			Download(download);
			return false;
		}


		Exception v = AssertValidDownloadRequest(download);
		if (v == null) {
			download.getStatus().setConvert(Status.Convert.QUEUED);
			return true;
		}

		convertQueue.remove(download);
		download.getStatus().setConvert(Status.Convert.FAILED);

		onFinish(download, false, v);
		Log.e(TAG, "Invalid Argument :" + v.getMessage());
		download.setException(v);
		return false;
	}

	private void stopIfIdling() {
		if (isServiceBusy())
			return;

		emptyCache();
		super.stopSelf();
	}

	private void emptyCache() {
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

	/**
	 * @return The members of the list returned may mutate.
	 */
	public List<Download> getAll() {
		ArrayList<Download> downloads = new ArrayList<>();
		for (int i = 0; i < downloadQueue.size(); i++) downloads.add(downloadQueue.valueAt(i));
		downloads.addAll(convertQueue);
		downloads.addAll(completed);
		if (runningConversion != null) downloads.add(runningConversion);
		return downloads;
	}

	/**
	 * @return The members of the list returned may mutate.
	 */
	public List<Download> getQueue() {
		ArrayList<Download> list = new ArrayList<>();
		for (int i = 0; i < downloadQueue.size(); i++) {
			Download d = downloadQueue.valueAt(i);
			Status.Download sd = d.getStatus().getDownload();
			if (sd == Status.Download.NETWORK_PENDING || sd == Status.Download.QUEUED) list.add(d);
		}
		return list;
	}

	/**
	 * @return The members of the list returned may mutate.
	 */
	public List<Download> getRunning() {
		ArrayList<Download> downloads = new ArrayList<>();
		for (int i = 0; i < downloadQueue.size(); i++) {
			Download d = downloadQueue.valueAt(i);
			if (d.getStatus().getDownload() == Status.Download.RUNNING || d.getStatus().getDownload() == Status.Download.PAUSED)
				downloads.add(downloadQueue.valueAt(i));
		}
		downloads.addAll(convertQueue);
		if (runningConversion != null) downloads.add(runningConversion);
		return downloads;
	}

	/**
	 * @return The members of the list returned may mutate.
	 */
	public List<Download> getCompleted() {
		return new ArrayList<>(completed);
	}

	private void CallbackWarn(Download download, String msg) {
		for (Iterator<ClientCallbacks> iterator = callbacks.iterator(); iterator.hasNext(); ) {
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
			payload = exp;
		}

		public Exception getPayload() {
			return payload;
		}
	}

	// TODO: Separate business logic into three engines that communicate through DownloadService

	private final DownloadEngine downloadEngine = new DownloadEngine(this);
	private final ConversionEngine conversionEngine = new ConversionEngine(this);
	private final MetadataEngine metadataEngine = new MetadataEngine();

	private void deploy(@NonNull Download request) {
		request.getStatus().reset();
		downloadEngine.download(request)
				.thenCompose(conversionEngine::convert)
				.thenCompose(metadataEngine::process)
				.thenApply(download -> {
					onFinish(download, true, null);
					return download;
				});
	}

	private static class Task {
		@NonNull Download download;
		@NonNull CompletableFuture<Download> future; // to call once download finishes

		Task(@NonNull Download download, @NonNull CompletableFuture<Download> future) {
			this.download = download;
			this.future = future;
		}
	}

	protected class DownloadEngine implements FetchListener {

		private static final int GROUP_ID = 0xC560_6A11;

		private final Fetch fetch;

		// Though the type is called a SparseArray, it is, in fact, a Map<int, ... >
		private final SparseArray<Task> tasks = new SparseArray<>();

		protected DownloadEngine(Context context) {
			fetch = Fetch.Impl.getInstance(new FetchConfiguration.Builder(context)
					.setDownloadConcurrentLimit(Preferences.getConcurrency())
					.build());

			fetch.addListener(this);
		}

		protected CompletableFuture<Download> download(@NonNull Download download) {
			CompletableFuture<Download> future = new CompletableFuture<>();
			// Send to fetch2
			if (!Directories.getBIN().exists() && !Directories.getBIN().mkdirs()) {
				future.completeExceptionally(new RuntimeException("Failed to create BIN folder"));
				return future;
			}

			if (download.getUrl() == null) {
				future.completeExceptionally(new InvalidParameterException("Cannot find download URL"));
				return future;
			}

			String filename = makeTemporaryFilename(download);
			download.setDown(filename);

			Request request = new Request(
					download.getUrl(),
					new File(Directories.getBIN(), filename).getAbsolutePath()
			);
			boolean useMobileData = Preferences.getMobiledata();
			request.setNetworkType(useMobileData ? NetworkType.ALL : NetworkType.WIFI_ONLY);
			request.setPriority(Priority.NORMAL);
			request.setEnqueueAction(EnqueueAction.REPLACE_EXISTING);
			request.setGroupId(GROUP_ID);
			download.setDownloadId(request.getId());

			Task task = new Task(download, future); // bundle with future
			tasks.put(request.getId(), task);
			fetch.enqueue(request, null, null);
			return future;
		}

		private String makeTemporaryFilename(@NonNull Download download) {
			return Long.toHexString(download.getId()) + '.' + download.getAvailableFormat();
		}

		private void updateDownloadStatus(@NonNull com.tonyodev.fetch2.Download fetchDownload,
										  Status.Download status) {
			int id = fetchDownload.getRequest().getId();
			Task task = tasks.get(id);
			if (task == null) {
				logUnknownDownloadId(id, status);
				return;
			}

			Download download = task.download;
			download.getStatus().setDownload(status);
			reportProgress(download, fetchDownload);
		}

		private void resolveDownload(@NonNull com.tonyodev.fetch2.Download fetchDownload,
									 @NonNull Status.Download status) {
			int id = fetchDownload.getRequest().getId();
			Task task = tasks.get(id);
			if (task == null) {
				logUnknownDownloadId(id, status);
				return;
			}

			Download download = task.download;
			download.getStatus().setDownload(status);
			DownloadService.this.onProgress(1L, 1L, false, download);
			task.future.complete(download);
			tasks.remove(id);
		}

		private void rejectDownload(@NonNull com.tonyodev.fetch2.Download fetchDownload,
									@NonNull Status.Download status,
									@NonNull Exception error) {
			int id = fetchDownload.getRequest().getId();
			Task task = tasks.get(id);
			if (task == null) {
				logUnknownDownloadId(id, status);
				return;
			}

			Download download = task.download;
			download.getStatus().setDownload(status);
			DownloadService.this.onFinish(download, false, error);
			task.future.completeExceptionally(error);
			tasks.remove(id);
		}

		private void reportProgress(@NonNull Download download,
									@NonNull com.tonyodev.fetch2.Download fetchDownload) {
			DownloadService.this.onProgress(
					fetchDownload.getDownloaded(),
					fetchDownload.getTotal(),
					fetchDownload.getTotal(),
					false,
					download
			);
		}

		private void logUnknownDownloadId(int id, @NonNull Status.Download status) {
			Log.w(
					TAG,
					"A mysterious download appeared that is "
							+ status.getValue()
							+ " that Fetch processed (id: "
							+ id
							+ ")"
			);
		}

		@Override
		public void onAdded(@NonNull com.tonyodev.fetch2.Download fetchDownload) {
			updateDownloadStatus(fetchDownload, Status.Download.QUEUED);
		}

		@Override
		public void onCancelled(@NonNull com.tonyodev.fetch2.Download fetchDownload) {
			rejectDownload(fetchDownload,
					Status.Download.CANCELLED,
					new InterruptedException("Download cancelled"));
		}

		@Override
		public void onCompleted(@NonNull com.tonyodev.fetch2.Download fetchDownload) {
			resolveDownload(fetchDownload, Status.Download.COMPLETE);
		}

		@Override
		public void onDeleted(@NonNull com.tonyodev.fetch2.Download fetchDownload) {
			rejectDownload(fetchDownload,
					Status.Download.CANCELLED,
					new InterruptedException("Download deleted"));
		}

		@Override
		public void onDownloadBlockUpdated(
				@NonNull com.tonyodev.fetch2.Download download,
				@NonNull DownloadBlock downloadBlock,
				int i
		) {}

		@Override
		public void onError(
				@NonNull com.tonyodev.fetch2.Download fetchDownload,
				@NonNull Error error,
				@Nullable Throwable throwable
		) {
			rejectDownload(fetchDownload,
					Status.Download.FAILED,
					new ServiceException(error.toString()));
		}

		@Override
		public void onPaused(@NonNull com.tonyodev.fetch2.Download fetchDownload) {
			updateDownloadStatus(fetchDownload, Status.Download.PAUSED);
		}

		@Override
		public void onProgress(
				@NonNull com.tonyodev.fetch2.Download fetchDownload,
				long etaMilliseconds,
				long bitsPerSecond
		) {
			// TODO: Use etaMilliseconds and bitsPerSecond
			updateDownloadStatus(fetchDownload, Status.Download.RUNNING);
		}

		@Override
		public void onQueued(@NonNull com.tonyodev.fetch2.Download fetchDownload, boolean b) {
			updateDownloadStatus(fetchDownload, Status.Download.QUEUED);
		}

		@Override
		public void onRemoved(@NonNull com.tonyodev.fetch2.Download fetchDownload) {
			rejectDownload(fetchDownload,
					Status.Download.CANCELLED,
					new InterruptedException("Download removed from queue"));

		}

		@Override
		public void onResumed(@NonNull com.tonyodev.fetch2.Download fetchDownload) {
			updateDownloadStatus(fetchDownload, Status.Download.RUNNING);
		}

		@Override
		public void onStarted(@NonNull com.tonyodev.fetch2.Download fetchDownload,
							  @NonNull List<? extends DownloadBlock> blocks,
							  int i) {
			updateDownloadStatus(fetchDownload, Status.Download.RUNNING);
		}

		@Override
		public void onWaitingNetwork(@NonNull com.tonyodev.fetch2.Download fetchDownload) {
			updateDownloadStatus(fetchDownload, Status.Download.NETWORK_PENDING);
		}
	}
	protected class ConversionEngine {
		private final AndroidAudioConverter converter;
		private final LinkedList<Task> queue = new LinkedList<>();
		private Task currentTask = null;

		public ConversionEngine(@NonNull Context context) {
			converter = new AndroidAudioConverter(context);
		}

		public CompletableFuture<Download> convert(@NonNull Download download) {
			CompletableFuture<Download> future = new CompletableFuture<>();

			queue.push(new Task(download, future));
			if (currentTask == null) // if no tasks are running, start one
				processNextTask();
			return future;
		}

		private void processNextTask() {
			if (queue.isEmpty())
				return;

			currentTask = queue.pop();
			processTask(currentTask).thenApply(download -> {
				currentTask = null;
				processNextTask();
				return download;
			});
		}

		private CompletableFuture<Download> processTask(Task task) {
			if (!task.download.getConvert()) {
				// currentTask does not need converting, resolve future
				skipConversion(task);
				return task.future;
			}

			if (task.download.getDown() == null) {
				ServiceException exception = new ServiceException("Cannot convert audio file. Filename lost.");
				Log.e(TAG, exception.getMessage());
				task.future.complete(task.download);
				return task.future;
			}

			File downloadedFile = new File(Directories.getBIN(), task.download.getDown());
			File convertedFile = converter.setFile(downloadedFile)
					.setTrim(task.download.getStart(), task.download.getEnd())
					.setNormalize(task.download.getNormalize())
					.setFormat(AudioFormat.valueOf(task.download.getFormat().toUpperCase()))
					.setBitrate(task.download.getBitrate())
					.setCallback(new AndroidAudioConverter.IConvertCallback() {
						@Override
						public void onSuccess(File convertedFile) {
							task.download.getStatus().setConvert(Status.Convert.COMPLETE);
							task.download.setConv(convertedFile.getName());
							converter.killProcess();

							task.future.complete(task.download);
						}

						@Override
						public void onProgress(long size, int c, int length) {
							DownloadService.this.onProgress(
									c, length, size, length == 0, task.download
							);
						}

						@Override
						public void onFailure(Exception error) {
							converter.killProcess();
							task.download.getStatus().setConvert(Status.Convert.FAILED);

							error.printStackTrace();
							onFinish(task.download, new ServiceException("Failed to process download", error));

							task.future.completeExceptionally(error);
						}
					})
					.convert();

			if (convertedFile != null)
				task.download.setConv(convertedFile.getAbsolutePath());
			return task.future;
		}

		private void skipConversion(Task task) {
			task.download.setConv(task.download.getDown());
			task.download.getStatus().setConvert(Status.Convert.SKIPPED);
			task.download.getStatus().setMetadata(null);
			task.future.complete(task.download);
		}
	}
	protected class MetadataEngine {
		public CompletableFuture<Download> process(@NonNull Download download) {
			CompletableFuture<Download> future = new CompletableFuture<>();

			if (download.getConv() == null) {
				download.getStatus().setInvalid(true);

				Exception exception = new IllegalArgumentException("No converted file found.");
				future.completeExceptionally(exception);
				return future;
			}

			DownloadService.this.onProgress(0L, 0L, true, download);

			if (download.getOverwrite())
				download.setMtdt(download.getFilenameWithExt());
			else {
				String filename = download.getFilename();
				String metadataFilename = avoidFilenameConflict(download, filename);
				download.setMtdt(metadataFilename);
			}

			if (writeMetadata(download)) {
				download.setCompleteDate(new Date());
				download.getStatus().setMetadata(true);

				future.complete(download);
				return future;
			}

			Log.w(TAG, "Failed to write metadata. Copying files instead...");

			Objects.requireNonNull(download.getMtdt());
			File convertedFile = new File(Directories.getBIN(), download.getConv());
			File metadataFile = new File(Directories.getMUSIC(), download.getMtdt());
			if (copyFile(convertedFile, metadataFile)) {
				download.setCompleteDate(new Date());
				download.getStatus().setMetadata(false);
				future.complete(download);
				return future;
			}

			download.setCompleteDate(new Date());
			download.getStatus().setMetadata(true);
			future.completeExceptionally(new ServiceException("Failed to copy file to output folder."));
			return future;
		}
	}
}
