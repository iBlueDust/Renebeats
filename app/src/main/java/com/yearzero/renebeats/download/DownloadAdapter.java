package com.yearzero.renebeats.download;

import android.content.Context;
import android.content.Intent;
import android.util.Log;
import android.view.ViewGroup;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.fragment.app.FragmentManager;
import androidx.recyclerview.widget.RecyclerView;

import com.yearzero.renebeats.Commons;
import com.yearzero.renebeats.InternalArgs;
import com.yearzero.renebeats.R;
import com.yearzero.renebeats.download.ui.DownloadDialog;
import com.yearzero.renebeats.download.ui.viewholder.BasicViewHolder;
import com.yearzero.renebeats.download.ui.viewholder.FailedViewHolder;
import com.yearzero.renebeats.download.ui.viewholder.QueueViewHolder;
import com.yearzero.renebeats.download.ui.viewholder.RunningViewHolder;
import com.yearzero.renebeats.download.ui.viewholder.SuccessViewHolder;
import com.yearzero.renebeats.errorlog.ErrorLogDialog;
import com.yearzero.renebeats.preferences.Preferences;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Date;
import java.util.List;
import java.util.Locale;

public class DownloadAdapter extends RecyclerView.Adapter<BasicViewHolder> implements DownloadService.ClientCallbacks {
	private static final String TAG = "DownloadAdapter";

	private Context context;
	private DownloadService service;
	private RecyclerView recycler;
	private DownloadDialog dialog;
	private FragmentManager manager;
	private ArrayList<Integer> blacklist = new ArrayList<>();

	// TODO: Slide ViewHolders for more options

	DownloadAdapter(Context context, DownloadService service, RecyclerView recycler, FragmentManager manager) {
		this.context = context;
		this.service = service;
		this.recycler = recycler;
		this.manager = manager;
		service.sanitizeAllQueues();
	}

	@NonNull
	@Override
	public BasicViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
		return DownloadViewHolderController.getViewHolderByType(context, parent, viewType);
	}

	@Override
	public int getItemCount() {
		return getServiceDownloads().size();
	}

	@Override
	public void onBindViewHolder(@NonNull BasicViewHolder holder, int position) {
		List<Download> list = getServiceDownloads();
		InitializeViewHolder(holder, list.get(position));
	}

	private void InitializeViewHolder(BasicViewHolder holder, final Download args) {
		holder.setTitle(args.getFilename() == null ? context.getString(R.string.sym_empty) : args.getFilename());
		//        holder.setIsRecyclable(true);

		holder.setOnClickListener(v -> {
			dialog = new DownloadDialog().setDownload(args);
			dialog.show(manager, TAG);
		});

		holder.setOnLongClickListener(v -> {
			dialog = new DownloadDialog().setDownload(args).setSecret(true);
			dialog.show(manager, TAG);
			return true;
		});

		if (args.getStatus().isInvalid()) {
			FailedViewHolder n = (FailedViewHolder) holder;
			n.setStatus(context.getString(R.string.adapter_download_invalid));

			ErrorButtons(n, args);
			return;
		}

		if (args.getStatus().isFailed()) {
			//region FailedViewHolder
			FailedViewHolder n = (FailedViewHolder) holder;
			n.setStatus(context.getString(R.string.failed));

			if (args.getException() instanceof IllegalArgumentException)
				n.setStatus(context.getString(R.string.adapter_download_invalid));
			else if (args.getException() instanceof DownloadService.ServiceException) {
				DownloadService.ServiceException e = (DownloadService.ServiceException) args.getException();
				n.setStatus(e.getMessage());
			}

			//TODO: Instead of switch statements based on status, what if it was based on ViewHolder type?

			ErrorButtons(n, args);
			//endregion
			return;
		}

		if (args.getStatus().isCancelled()) {
			if (holder instanceof FailedViewHolder) {
				FailedViewHolder h = (FailedViewHolder) holder;
				h.setStatus(context.getString(R.string.cancelled));
				h.setInfoVisible(false);
				h.setRetryListener(v -> {
					Intent intent = new Intent(context, DownloadActivity.class);
					intent.putExtra(InternalArgs.DATA, args);
					context.startActivity(intent);
				});
			} else {
				notifyItemChanged(holder.getAdapterPosition());
				Log.w(TAG, "Cancelled case got a non-FailedViewHolder");
			}
			return;
		}

		if (args.getStatus().getDownload() == null)
			return;

		try {
			//region Status Main
			switch (args.getStatus().getDownload()) {
				case QUEUED:
					initializeQueueViewHolder(
							(QueueViewHolder) holder, R.string.adapter_download_network, args);
					break;
				case NETWORK_PENDING:
					initializeQueueViewHolder(
							(QueueViewHolder) holder, R.string.adapter_download_waiting, args);
					break;
				case RUNNING:
					initializeRunningViewHolder((RunningViewHolder) holder, args);
					break;
				case COMPLETE:
					if (args.getStatus().getConvert() == null)
						break;
					switch (args.getStatus().getConvert()) {
						case QUEUED:
							QueueViewHolder h = (QueueViewHolder) holder;
							h.setStatus(context.getString(R.string.adapter_download_downloaded));
							break;
						case RUNNING:
							RunningViewHolder j = (RunningViewHolder) holder;
							j.setCancelListener(v -> service.cancel(args));
							j.setStatus(String.format(Locale.ENGLISH, context.getString(R.string.adapter_download_converting), Commons.FormatBytes(args.getSize())));
							j.setProgress((int) args.getCurrent(), (int) args.getTotal(), false);
							break;
						case SKIPPED:
						case COMPLETE:
							if (args.getStatus().getMetadata() == null) {
								RunningViewHolder n = (RunningViewHolder) holder;
								n.setCancelListener(v -> service.cancel(args));
								n.setStatus(context.getString(R.string.adapter_download_metadata));
								n.setProgress(0, 0, true);
								break;
							}

							SuccessViewHolder o = (SuccessViewHolder) holder;
							o.setStatus(context.getString(R.string.success));

							if (args.getAssigned() != null && args.getCompleteDate() != null) {
								Date start = args.getAssigned();
								Date end = args.getCompleteDate();
								String durationString = formatDuration(start, end);
								o.setDate(durationString);
							}

							o.setRetryListener(v -> {
								Intent intent = new Intent(context, DownloadActivity.class);
								intent.putExtra(InternalArgs.DATA, args);
								context.startActivity(intent);
							});
					}
				case CANCELLED:
				case FAILED:
					break;
			}
			//endregion
		} catch (ClassCastException e) {
			Log.v(TAG, "Class cast exception > " + e.getMessage());
			recycler.post(() -> notifyItemChanged(holder.getAdapterPosition(), null));
		}
	}

	private void initializeRunningViewHolder(RunningViewHolder holder, Download args) {
		String format = context.getString(R.string.adapter_download_downloading);
		String statusString = String.format(Locale.ENGLISH,
				format,
				Commons.FormatBytes(args.getCurrent()),
				Commons.FormatBytes(args.getTotal())
		);
		holder.setStatus(statusString);
		holder.setProgress((int) args.getCurrent(), (int) args.getTotal(), args.getIndeterminate());
		holder.setCancelListener(v -> service.cancel(args));
	}

	private void initializeQueueViewHolder(QueueViewHolder holder, int adapter_download_network, Download args) {
		holder.setStatus(context.getString(adapter_download_network));
		holder.setCancelListener(v -> service.cancel(args));
	}

	@NonNull
	private String formatDuration(@NonNull Date start, @NonNull Date end) {
		StringBuilder text = new StringBuilder();
		text.append(context.getString(R.string.adapter_download_elapsed));

		long elapsed = start.getTime() - end.getTime();
		short hour = (short) (elapsed / 3600_000);
		short minute = (short) ((elapsed / 60_000) % 60);
		short second = (short) ((elapsed / 1000) % 60);

		if (hour > 0) {
			String hourString = String.format(
					Locale.ENGLISH,
					"%02d%s ",
					hour,
					context.getString(R.string.sym_hour)
			);
			text.append(hourString);
		}

		if (minute > 0) {
			String minuteString = String.format(
					Locale.ENGLISH,
					"%02d%s ",
					minute,
					context.getString(R.string.sym_minute)
			);
			text.append(minuteString);
		}

		String secondString = String.format(
				Locale.ENGLISH,
				"%02d%s ",
				second,
				context.getString(R.string.sym_seconds)
		);
		text.append(secondString);

		return text.toString();
	}

	private void ErrorButtons(FailedViewHolder viewHolder, Download args) {
		viewHolder.setInfoListener(v -> {
			ErrorLogDialog dialog;
			if (!Preferences.getAlways_log_failed()) {
				dialog = new ErrorLogDialog(null, args.getException());
				dialog.show(manager, TAG);
				return;
			}

			String name = null;
			if (args.getException() != null)
				name = Commons.LogExceptionReturn(args, args.getException());

			if (name == null) {
				Toast.makeText(context, R.string.adapter_download_autolog_failed, Toast.LENGTH_LONG)
						.show();
				return;
			}

			dialog = new ErrorLogDialog(name, null);
			dialog.show(manager, TAG);
		});

		viewHolder.setRetryListener(v -> {
			Intent intent = new Intent(context, DownloadActivity.class);
			intent.putExtra(InternalArgs.DATA, args);
			context.startActivity(intent);
		});
	}

	@Override
	public void onProgress(Download args, long progress, long max, long size, boolean indeterminate) {
		int index = -1;

		List<Download> downloads = getServiceDownloads();
		for (int i = 0; i < downloads.size(); i++) {
			if (downloads.get(i).getId() != args.getId())
				continue;
			index = i;
			break;
		}

		if (index >= 0)
			UpdateAtPosition(index, args);
		else
			Log.w(TAG, "onProgress indexOf returned -1");

		if (dialog != null)
			dialog.UpdateStatus(args);
	}

	@Override
	public void onDone(Download args, boolean successful, Exception e) {
		int index = getServiceDownloads().indexOf(args);

		if (index < 0)
			notifyDataSetChanged();
		else
			notifyItemChanged(index);

		if (dialog != null)
			dialog.UpdateStatus(args);
	}

	@Override
	public void onWarn(Download args, String type) {
		Log.w(TAG, type);
	}

	@Override
	public int getItemViewType(int position) {
		return getItemViewType(position, getServiceDownloads());
	}

	private int getItemViewType(int position, List<Download> array) {
		if (array.size() <= position || array.get(position) == null) {
			notifyDataSetChanged();
			return -1;
		}
		return DownloadViewHolderController.getViewHolderType(array.get(position).getStatus());
	}

	private List<Download> getServiceDownloads() {
		ArrayList<Download> list = new ArrayList<>(service.getAll());
		for (int i = 0; i < list.size(); /* do not increment */) {
			if (blacklist.contains(list.get(i).getDownloadId()))
				list.remove(i);
			else
				i++;
		}

		list.sort((a, b) -> {
			if (a.getAssigned() == null || b.getAssigned() == null)
				return 0;

			return b.getAssigned().compareTo(a.getAssigned());
		});
		return list;
	}

	void blacklistAt(int index) {
		blacklist.add(getServiceDownloads().get(index).getDownloadId());
		notifyItemRemoved(index);
	}

	void unBlacklistAt(int index) {
		blacklist.remove((Integer) getServiceDownloads().get(index).getDownloadId());
		notifyItemInserted(index);
	}

	private void UpdateAtPosition(int position, Download download) {
		RecyclerView.ViewHolder holder = recycler.findViewHolderForAdapterPosition(position);
		if (holder instanceof BasicViewHolder)
			InitializeViewHolder((BasicViewHolder) holder, download);
	}
}
