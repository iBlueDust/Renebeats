package com.yearzero.renebeats.download;import android.content.Context;import android.view.LayoutInflater;import android.view.ViewGroup;import com.yearzero.renebeats.R;import com.yearzero.renebeats.download.ui.viewholder.BasicViewHolder;import com.yearzero.renebeats.download.ui.viewholder.FailedViewHolder;import com.yearzero.renebeats.download.ui.viewholder.QueueViewHolder;import com.yearzero.renebeats.download.ui.viewholder.RunningViewHolder;import com.yearzero.renebeats.download.ui.viewholder.SuccessViewHolder;class DownloadViewHolderController {	static int getViewHolderType(Status pack) {		if (pack.isFailed() || pack.isCancelled() || pack.isInvalid())			return FailedViewHolder.LocalID;		if (pack.getDownload() == Status.Download.COMPLETE				&& (pack.getConvert() == Status.Convert.COMPLETE					|| pack.getConvert() == Status.Convert.SKIPPED))			return pack.getMetadata() == null					|| !pack.getMetadata() ? RunningViewHolder.LocalID : SuccessViewHolder.LocalID;		if (pack.isQueued())// || pack.isPaused())			return QueueViewHolder.LocalID;		if (pack.getDownload() == Status.Download.RUNNING				|| pack.getDownload() == Status.Download.PAUSED				|| pack.getConvert() == Status.Convert.RUNNING) //|| pack.convert == Status.Convert.PAUSED)			return RunningViewHolder.LocalID;		return BasicViewHolder.LocalID;	}	static BasicViewHolder getViewHolderByType(Context context, ViewGroup parent, int viewID) {		switch (viewID) {			case QueueViewHolder.LocalID:				return new QueueViewHolder(LayoutInflater.from(context).inflate(R.layout.layout_download_queue, parent, false));			case RunningViewHolder.LocalID:				return new RunningViewHolder(LayoutInflater.from(context).inflate(R.layout.layout_download_running, parent, false));			case SuccessViewHolder.LocalID:				return new SuccessViewHolder(LayoutInflater.from(context).inflate(R.layout.layout_download_success, parent, false));			case FailedViewHolder.LocalID:				return new FailedViewHolder(LayoutInflater.from(context).inflate(R.layout.layout_download_failed, parent, false));			default:				return new BasicViewHolder(LayoutInflater.from(context).inflate(R.layout.layout_download_basic, parent, false));		}	}}