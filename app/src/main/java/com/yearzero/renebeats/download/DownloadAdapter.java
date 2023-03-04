package com.yearzero.renebeats.download;

import android.content.Context;
import android.util.Log;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.fragment.app.FragmentManager;
import androidx.recyclerview.widget.RecyclerView;

import com.yearzero.renebeats.download.ui.DownloadDialog;
import com.yearzero.renebeats.download.ui.viewholder.BasicViewHolder;

import java.util.ArrayList;
import java.util.List;

public class DownloadAdapter extends RecyclerView.Adapter<BasicViewHolder> implements DownloadService.ClientCallbacks {
	private static final String TAG = "DownloadAdapter";

	private final Context context;
	private final DownloadService service;
	private final RecyclerView recycler;
	private DownloadDialog dialog;
	private final FragmentManager manager;
	private final ArrayList<Integer> blacklist = new ArrayList<>();

	// TODO: Slide ViewHolders for more options

	DownloadAdapter(Context context,
					DownloadService service,
					RecyclerView recycler,
					FragmentManager manager) {
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
		initializeViewHolder(holder, list.get(position));
	}

	private void initializeViewHolder(BasicViewHolder holder, final Download download) {
		holder.setOnClickListener(v -> {
			dialog = new DownloadDialog().setDownload(download);
			dialog.show(manager, TAG);
		});

		holder.setOnLongClickListener(v -> {
			dialog = new DownloadDialog().setDownload(download).setSecret(true);
			dialog.show(manager, TAG);
			return true;
		});

		holder.update(download, service);
	}

	@Override
	public void onProgress(Download args,
						   long progress,
						   long max,
						   long size,
						   boolean indeterminate) {
		int index = -1;

		List<Download> downloads = getServiceDownloads();
		for (int i = 0; i < downloads.size(); i++) {
			if (downloads.get(i).getId() != args.getId())
				continue;
			index = i;
			break;
		}

		if (index >= 0)
			notifyItemChanged(index);
//			updateAtPosition(index, args);
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

	private void updateAtPosition(int position, Download download) {
		RecyclerView.ViewHolder holder = recycler.findViewHolderForAdapterPosition(position);
		if (holder instanceof BasicViewHolder)
			initializeViewHolder((BasicViewHolder) holder, download);
	}
}
