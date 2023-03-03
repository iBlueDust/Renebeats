package com.yearzero.renebeats.download;

import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.os.Binder;
import android.os.IBinder;
import android.util.Log;
import android.util.LongSparseArray;
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
import com.tonyodev.fetch2core.Extras;
import com.yearzero.renebeats.AndroidAudioConverter;
import com.yearzero.renebeats.AudioFormat;
import com.yearzero.renebeats.Commons;
import com.yearzero.renebeats.Directories;
import com.yearzero.renebeats.InternalArgs;
import com.yearzero.renebeats.preferences.Preferences;

import org.cmc.music.metadata.MusicMetadata;
import org.cmc.music.metadata.MusicMetadataSet;
import org.cmc.music.myid3.MyID3;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.channels.FileChannel;
import java.security.InvalidParameterException;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import java.util.stream.Collectors;

public class DownloadService extends Service {

	public static final String TAG = "DownloadService";

	public interface ClientCallbacks {
		void onProgress(Download args, long progress, long max, long size, boolean indeterminate);

		void onDone(Download args, boolean successful, Exception e);

		void onWarn(Download args, String type);
	}

	private final LocalBinder binder = new LocalBinder();

	private final ArrayList<ClientCallbacks> callbacks = new ArrayList<>();
	private final ArrayList<Download> completed = new ArrayList<>();

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
	public void onCreate() {
		downloadEngine = new DownloadEngine();
		conversionEngine = new ConversionEngine(this);
		metadataEngine = new MetadataEngine();
	}

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

		Exception v = assertValidDownloadRequest(current);
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

		processDownload(current);
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

	private Exception assertValidDownloadRequest(Download args) {
		if (args == null)
			return new IllegalArgumentException("Argument object is null");
		else if (args.getUrl() == null)
			return new IllegalArgumentException("No URL found");
		else if (args.getTitle().isEmpty())
			return new IllegalArgumentException("No Title found");
		return null;
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
		return downloadEngine.isBusy() || conversionEngine.isBusy() || metadataEngine.isBusy();
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
		if (e != null)
			intent.putExtra(InternalArgs.EXCEPTION, e);
		intent.putExtra(InternalArgs.RESULT,
				successful ? InternalArgs.SUCCESS : InternalArgs.FAILED);
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
			ClientCallbacks callbacks = this.callbacks.get(i);
			callbacks.onProgress(download, current, total, size, indeterminate);
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

		downloadEngine.destroy();
		conversionEngine.destroy();
		metadataEngine.destroy();

		Intent intent = new Intent(TAG);
		intent.putExtra(InternalArgs.RESULT, InternalArgs.DESTROY);
		intent.putExtra(InternalArgs.DATA, getAll().toArray(new Download[0]));
		LocalBroadcastManager.getInstance(this).sendBroadcast(intent);

		DownloadService.super.onDestroy();
	}

	public void cancel(@NonNull Download request) {
		Download download = downloadEngine.cancel(request);

		if (download == null)
			download = conversionEngine.cancel(request);

		if (download == null)
			download = metadataEngine.cancel(request);

		Intent intent = new Intent(TAG);
		intent.putExtra(InternalArgs.DATA, download);
		intent.putExtra(InternalArgs.RESULT, InternalArgs.CANCELLED);
		LocalBroadcastManager.getInstance(this).sendBroadcast(intent);
	}

	public void sanitizeAllQueues() {
		downloadEngine.sanitizeQueue();
		conversionEngine.sanitizeQueue();
		metadataEngine.sanitizeQueue();
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
			if (!file.delete())
				bad = true;
		}

		if (bad) Log.w(TAG, "Failed to empty BIN");
	}

	/**
	 * @return The members of the list returned may mutate.
	 */
	public List<Download> getAll() {
		List<Download> all = downloadEngine.getAll();
		all.addAll(conversionEngine.getAll());
		all.addAll(metadataEngine.getAll());
		all.addAll(completed);
		return all;
	}

	/**
	 * @return The members of the list returned may mutate.
	 */
	public List<Download> getQueue() {
		return downloadEngine.getAll()
				.stream()
				.filter(download -> {
					Status.Download status = download.getStatus().getDownload();
					return status == Status.Download.NETWORK_PENDING
							|| status == Status.Download.QUEUED;
				})
				.collect(Collectors.toList());
	}

	/**
	 * @return The members of the list returned may mutate.
	 */
	public List<Download> getRunning() {
		List<Download> all = downloadEngine.getAll()
				.stream()
				.filter(download -> {
					Status.Download status = download.getStatus().getDownload();
					return status == Status.Download.RUNNING || status == Status.Download.PAUSED;
				})
				.collect(Collectors.toList());

		all.addAll(conversionEngine.getAll());
		all.addAll(metadataEngine.getAll());
		return all;
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

	private DownloadEngine downloadEngine;
	private ConversionEngine conversionEngine;
	private MetadataEngine metadataEngine;

	private CompletableFuture<Download> processDownload(@NonNull Download request) {
		request.getStatus().reset();
		return downloadEngine.download(request)
				.thenCompose(conversionEngine::convert)
				.thenCompose(metadataEngine::process)
				.thenApply(download -> {
					onFinish(download, true, null);
					return download;
				})
				.exceptionally(throwable -> {
					throwable.printStackTrace();
					if (throwable instanceof Exception) {
						onFinish(request, false, (Exception) throwable);
					} else {
						Exception exception = new ServiceException(throwable.getMessage());
						onFinish(request, false, exception);
					}

					return request;
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

		// Though the type is called a SparseArray, it is, in fact, a Map<int, ... >
		private final SparseArray<Task> tasks = new SparseArray<>();

		public DownloadEngine() {
			Commons.fetch.addListener(this);
		}

		public CompletableFuture<Download> download(@NonNull Download download) {
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
			Commons.fetch.enqueue(request, null, null);
			return future;
		}

		public boolean isBusy() {
			return !Commons.fetch.isClosed() && tasks.size() > 0;
		}

		public List<Download> getAll() {
			ArrayList<Download> downloads = new ArrayList<>();
			downloads.ensureCapacity(tasks.size());
			for (int i = 0; i < tasks.size(); i++)
				downloads.add(tasks.valueAt(i).download);
			return downloads;
		}

		@Nullable
		public Download cancel(Download download) {
			Task task = tasks.get(download.getDownloadId());
			if (task == null)
				return null;

			task.download.setCompleteDate(new Date());
			task.download.setCurrent(0);
			task.download.setTotal(0);
			task.download.setSize(0);
			task.download.setIndeterminate(true);
			task.download.getStatus().setDownload(Status.Download.CANCELLED);

			task.future.completeExceptionally(new InterruptedException("Download cancelled"));
			Commons.fetch.cancel(download.getDownloadId());
			tasks.remove(download.getDownloadId());

			return task.download;
		}

		public void destroy() {
			Commons.fetch.removeListener(this);
		}

		public void sanitizeQueue() {
			// Iterate backwards since we are also removing elements simultaneously
			for (int i = tasks.size() - 1; i >= 0; i--) {
				Task task = tasks.valueAt(i);
				boolean isValid = sanitizeTask(task);
				if (!isValid)
					tasks.remove(task.download.getDownloadId());
			}
		}

		/**
		 * Checks if download is valid. If not, fail it and report it for deletion.
		 * @return If true, this conversion task is valid. Otherwise, remove it from the queue.
		 */
		private boolean sanitizeTask(@NonNull Task task) {
			// Check if Download has not been started at all
			if (task.download.getStatus().getDownload() != null)
				return true;

			Exception exception = assertValidDownloadRequest(task.download);

			if (exception == null) {
				task.download.getStatus().setDownload(Status.Download.QUEUED);
				return true;
			}

			task.download.getStatus().setDownload(Status.Download.FAILED);
			task.download.setException(exception);
			task.future.completeExceptionally(exception);
			return false;
		}

		private String makeTemporaryFilename(@NonNull Download download) {
			return Long.toHexString(download.getId()) + '.' + download.getAvailableFormat();
		}

		private void updateDownloadStatus(@NonNull com.tonyodev.fetch2.Download fetchDownload,
										  Status.Download status) {
			int id = fetchDownload.getId();
			Task task = tasks.get(id);
			if (task == null) {
				logUnknownDownloadId(id, status);
				return;
			}

			task.download.getStatus().setDownload(status);
			reportProgress(task.download, fetchDownload);
		}

		private void resolveDownload(@NonNull com.tonyodev.fetch2.Download fetchDownload) {
			int id = fetchDownload.getId();
			Task task = tasks.get(id);
			if (task == null) {
				logUnknownDownloadId(id, Status.Download.COMPLETE);
				return;
			}

			Download download = task.download;
			download.getStatus().setDownload(Status.Download.COMPLETE);
			DownloadService.this.onProgress(1L, 1L, false, download);
			task.future.complete(download);
			tasks.remove(id);
		}

		private void rejectDownload(@NonNull com.tonyodev.fetch2.Download fetchDownload,
									@NonNull Status.Download status,
									@NonNull Exception error) {
			int id = fetchDownload.getId();
			Task task = tasks.get(id);
			if (task == null) {
				logUnknownDownloadId(id, status);
				return;
			}

			Download download = task.download;
			download.getStatus().setDownload(status);
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
			resolveDownload(fetchDownload);
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
		private final LongSparseArray<Task> tasks = new LongSparseArray<>();
		private Task currentTask = null;

		public ConversionEngine(@NonNull Context context) {
			converter = new AndroidAudioConverter(context);
		}

		public CompletableFuture<Download> convert(@NonNull Download download) {
			CompletableFuture<Download> future = new CompletableFuture<>();

			tasks.put(download.getId(), new Task(download, future));
			if (currentTask == null) // if no tasks are running, start one
				processNextTask();
			return future;
		}

		public boolean isBusy() {
			return currentTask != null || tasks.size() > 0;
		}

		public List<Download> getAll() {
			ArrayList<Download> all = new ArrayList<>();
			all.ensureCapacity(tasks.size());

			if (currentTask != null)
				all.add(currentTask.download);

			for (int i = 0; i <	tasks.size(); i++)
				all.add(tasks.valueAt(i).download);

			return all;
		}

		@Nullable
		public Download cancel(Download download) {
			long id = download.getId();
			Task task = tasks.get(id);
			if (task == null)
				return null;

			task.download.setCompleteDate(new Date());
			task.download.setCurrent(0);
			task.download.setTotal(0);
			task.download.setSize(0);
			task.download.setIndeterminate(true);
			task.download.getStatus().setConvert(Status.Convert.CANCELLED);

			task.future.completeExceptionally(new InterruptedException("Download cancelled"));
			tasks.remove(id);

			return task.download;
		}

		public void destroy() {
			converter.killProcess();
		}

		private void sanitizeQueue() {
			for (int i = 0; i < tasks.size(); i++) {
				long id = tasks.keyAt(i);
				boolean isValid = sanitizeTask(tasks.get(id));
				if (!isValid)
					tasks.remove(id);
			}

			if (currentTask == null)
				return;

			boolean isValid = sanitizeTask(currentTask);
			if (isValid)
				return;

			currentTask = null;
			processNextTask();
		}

		/**
		 * Checks if download is valid. If not, fail it and report it for deletion.
		 * @return If true, this conversion task is valid. Otherwise, remove it from the queue.
		 */
		private boolean sanitizeTask(@NonNull Task task) {
			if (task.download.getStatus().getConvert() != null) {
				if (task.download.getDown() == null
						|| !new File(Directories.getBIN(), task.download.getDown()).exists()) {
					return false;
				}

				task.download.getStatus().setConvert(Status.Convert.QUEUED);
				return true;
			}


			Exception exception = assertValidDownloadRequest(task.download);
			if (exception == null) {
				task.download.getStatus().setConvert(Status.Convert.QUEUED);
				return true;
			}

			tasks.remove(task.download.getId());
			task.download.getStatus().setConvert(Status.Convert.FAILED);

			task.download.setException(exception);
			task.future.completeExceptionally(exception);
			return false;
		}

		private void processNextTask() {
			if (tasks.size() <= 0)
				return;

			int lastIndex = tasks.size() - 1;
			currentTask = tasks.valueAt(lastIndex);
			tasks.removeAt(lastIndex);

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

		public boolean isBusy() {
			return false; // this engine can perform tasks synchronously
		}

		public List<Download> getAll() { return new ArrayList<>(); }

		@Nullable
		public Download cancel(@NonNull Download download) { return null; }

		public void destroy() {}

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

		public void sanitizeQueue() {
			// Do nothing
			// No need to sanitize Metadata since it does not have a queue
		}
	}
}
