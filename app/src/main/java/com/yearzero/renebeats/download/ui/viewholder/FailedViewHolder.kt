package com.yearzero.renebeats.download.ui.viewholder

import android.content.Intent
import android.view.View
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.yearzero.renebeats.Commons
import com.yearzero.renebeats.InternalArgs
import com.yearzero.renebeats.R
import com.yearzero.renebeats.download.Download
import com.yearzero.renebeats.download.DownloadActivity
import com.yearzero.renebeats.download.DownloadService
import com.yearzero.renebeats.download.DownloadService.ServiceException
import com.yearzero.renebeats.errorlog.ErrorLogDialog
import com.yearzero.renebeats.preferences.Preferences.always_log_failed

class FailedViewHolder(itemView: View) : AdvancedViewHolder(itemView) {

	fun setInfoListener(listener: View.OnClickListener) = setAction0(listener)
	fun setRetryListener(listener: View.OnClickListener) = setAction1(listener)
	fun setInfoVisible(visible: Boolean) { action0View.visibility = if (visible) View.VISIBLE else View.GONE }

	override fun update(download: Download, service: DownloadService?) {
		super.update(download, service)

		if (download.status.isFailed)
			updateFailedDownload(download)
		else if (download.status.isCancelled)
			updateCancelledDownload(download)
	}

	private fun updateFailedDownload(download: Download) {
		setStatus(itemView.context.getString(R.string.failed))

		if (download.exception is IllegalArgumentException)
			setStatus(itemView.context.getString(R.string.adapter_download_invalid))
		else if (download.exception is ServiceException) {
			setStatus((download.exception as ServiceException).message!!)
		}
		updateErrorButtons(download)
	}

	private fun updateCancelledDownload(download: Download) {
		setStatus(itemView.context.getString(R.string.cancelled))
		setInfoVisible(false)
		setRetryListener(View.OnClickListener {
			val intent = Intent(itemView.context, DownloadActivity::class.java)
			intent.putExtra(InternalArgs.DATA, download)
			itemView.context.startActivity(intent)
		})
	}

	private fun updateErrorButtons(download: Download) {
		setInfoListener(View.OnClickListener {
			val manager = (itemView.context as AppCompatActivity).supportFragmentManager

			val dialog: ErrorLogDialog
			if (!always_log_failed) {
				dialog = ErrorLogDialog(null, download.exception)
				dialog.show(manager, TAG)
				return@OnClickListener
			}

			var name: String? = null
			if (download.exception != null)
				name = Commons.LogExceptionReturn(download, download.exception!!)

			if (name == null) {
				Toast.makeText(
					itemView.context,
					R.string.adapter_download_autolog_failed,
					Toast.LENGTH_LONG
				).show()
				return@OnClickListener
			}
			dialog = ErrorLogDialog(name, null)
			dialog.show(manager, TAG)
		})

		setRetryListener(View.OnClickListener {
			val intent = Intent(itemView.context, DownloadActivity::class.java)
			intent.putExtra(InternalArgs.DATA, download)
			itemView.context.startActivity(intent)
		})
	}

	companion object {
		const val LocalID = 0x0201
		const val TAG = "FailedViewHolder"
	}
}
