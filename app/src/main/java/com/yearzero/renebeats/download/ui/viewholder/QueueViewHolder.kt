package com.yearzero.renebeats.download.ui.viewholder

import android.view.View
import com.yearzero.renebeats.R
import com.yearzero.renebeats.download.Download
import com.yearzero.renebeats.download.DownloadService
import com.yearzero.renebeats.download.Status

class QueueViewHolder(itemView: View) : AdvancedViewHolder(itemView) {

	fun setCancelListener(listener: View.OnClickListener) = setAction0(listener)
	fun setSortListener(listener: View.OnClickListener) = setAction1(listener)

	override fun update(download: Download, service: DownloadService?) {
		super.update(download, service)

		setStatus(getStatusMessage(download.status))
		if (service != null)
			setCancelListener(View.OnClickListener { service.cancel(download) })
	}

	private fun getStatusMessage(status: Status): String {
		when (status.download) {
			Status.Download.QUEUED ->
				return itemView.context.getString(R.string.adapter_download_network)
			Status.Download.NETWORK_PENDING ->
				return itemView.context.getString(R.string.adapter_download_waiting)
			Status.Download.RUNNING ->
				return "Downloading..." // should be replaced with a formatted string instead
			Status.Download.PAUSED ->
				TODO()
			Status.Download.CANCELLED ->
				TODO() // Shouldn't be handled by QueueViewHolder
			Status.Download.FAILED ->
				TODO() // Shouldn't be handled by QueueViewHolder
			Status.Download.COMPLETE -> {} // continue
		}

		when(status.convert) {
			Status.Convert.QUEUED ->
				return itemView.context.getString(R.string.adapter_download_downloaded)
			else ->
				TODO() // Shouldn't be handled by QueueViewHolder
		}

	}

	companion object {
		const val LocalID = 0x0202
	}
}
