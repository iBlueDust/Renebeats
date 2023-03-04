package com.yearzero.renebeats.download.ui.viewholder

import android.graphics.drawable.AnimationDrawable
import android.view.View
import android.widget.ProgressBar
import androidx.constraintlayout.widget.ConstraintLayout
import com.yearzero.renebeats.Commons
import com.yearzero.renebeats.R
import com.yearzero.renebeats.download.Download
import com.yearzero.renebeats.download.DownloadService
import com.yearzero.renebeats.download.Status
import java.util.*

class RunningViewHolder(Main: View) : IntermediateViewHolder(Main) {

	private var constraintLayout: ConstraintLayout = Main.findViewById(R.id.constraint)
	private var progressBar: ProgressBar = Main.findViewById(R.id.progress)

	init { updateAnimation() }

	fun setCancelListener(listener: View.OnClickListener) = setAction0(listener)

	fun setProgress(current: Int, total: Int, indeterminate: Boolean) {
		if (indeterminate) {
			progressBar.isIndeterminate = true
		} else {
			progressBar.isIndeterminate = false
			progressBar.max = total
			progressBar.progress = current
		}
	}

	override fun update(download: Download, service: DownloadService?) {
		super.update(download, service)

		setProgress(download.current.toInt(), download.total.toInt(), download.indeterminate)
		if (service != null)
			setCancelListener(View.OnClickListener {
				service.cancel(download)
			})


		if (download.status.download == Status.Download.RUNNING)
			updateStatusView(R.string.adapter_download_downloading, download.current, download.total)

		if (download.status.download != Status.Download.COMPLETE)
			return
		if (download.status.convert == Status.Convert.RUNNING) {
			updateStatusView(R.string.adapter_download_downloading, download.size)
		}

		if (download.status.convert != Status.Convert.COMPLETE)
			return
		if (download.status.metadata == null) {
			updateStatusView(R.string.adapter_download_metadata)
			setProgress(0, 0, true)
		}
	}

	private fun updateStatusView(formatResourceId: Int, vararg parameters: Long) {
		val format: String = itemView.context.getString(formatResourceId)
		val statusString = String.format(
			Locale.ENGLISH,
			format,
			*(parameters.map { p -> Commons.FormatBytes(p) }).toTypedArray()
		)
		setStatus(statusString)
	}

	private fun updateAnimation() {
		val draw = constraintLayout.background
		if (draw !is AnimationDrawable)
			return

		draw.setEnterFadeDuration(500)
		draw.setExitFadeDuration(1000)
		draw.start()
	}

	companion object {
		const val LocalID = 0x0101
	}
}
