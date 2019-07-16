package com.yearzero.renebeats.download

import android.content.Context
import android.view.LayoutInflater
import android.view.ViewGroup
import com.yearzero.renebeats.R
import com.yearzero.renebeats.download.ui.viewholder.*

class DownloadController {
    companion object {
        @JvmStatic
        fun getViewHolderType(pack: Status): Int {
            return if (pack.isFailed || pack.isCancelled || pack.isInvalid)
                FailedViewHolder.LocalID
            else if (pack.download == Status.Download.COMPLETE && (pack.convert == Status.Convert.COMPLETE || pack.convert == Status.Convert.SKIPPED))
                if (pack.metadata == null) RunningViewHolder.LocalID else SuccessViewHolder.LocalID
            else if (pack.isQueued || pack.isPaused)
                QueueViewHolder.LocalID
            else if (pack.download == Status.Download.RUNNING || pack.download == Status.Download.PAUSED || pack.convert == Status.Convert.RUNNING || pack.convert == Status.Convert.PAUSED)
                RunningViewHolder.LocalID
            else
                BasicViewHolder.LocalID
        }

        @JvmStatic
        fun getViewHolderByType(context: Context, parent: ViewGroup, viewID: Int): BasicViewHolder {
            return when (viewID) {
                QueueViewHolder.LocalID -> QueueViewHolder(LayoutInflater.from(context).inflate(R.layout.layout_download_queue, parent, false))
                RunningViewHolder.LocalID -> RunningViewHolder(LayoutInflater.from(context).inflate(R.layout.layout_download_running, parent, false))
                SuccessViewHolder.LocalID -> SuccessViewHolder(LayoutInflater.from(context).inflate(R.layout.layout_download_success, parent, false))
                FailedViewHolder.LocalID -> FailedViewHolder(LayoutInflater.from(context).inflate(R.layout.layout_download_failed, parent, false))
                else -> BasicViewHolder(LayoutInflater.from(context).inflate(R.layout.layout_download_basic, parent, false))
            }
        }
    }
}