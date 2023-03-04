package com.yearzero.renebeats.download.ui.viewholder

import android.content.Intent
import android.view.View
import android.widget.TextView
import com.yearzero.renebeats.InternalArgs
import com.yearzero.renebeats.R
import com.yearzero.renebeats.download.Download
import com.yearzero.renebeats.download.DownloadActivity
import com.yearzero.renebeats.download.DownloadService
import java.util.*

class SuccessViewHolder(itemView: View) : IntermediateViewHolder(itemView) {

	private var dateView: TextView = itemView.findViewById(R.id.date)

	fun setDate(date: String) { dateView.text = date }
	fun setRetryListener(listener: View.OnClickListener) = setAction0(listener)

	override fun update(download: Download, service: DownloadService?) {
		super.update(download, service)

		setStatus(itemView.context.getString(R.string.success))
		setRetryListener(View.OnClickListener {
			val intent = Intent(itemView.context, DownloadActivity::class.java)
			intent.putExtra(InternalArgs.DATA, download)
			itemView.context.startActivity(intent)
		})

		if (download.assigned == null || download.completeDate == null)
			return

		val start: Date = download.assigned!!
		val end: Date = download.completeDate!!
		val durationString: String = formatDuration(start, end)
		setDate(durationString)
	}

	private fun formatDuration(start: Date, end: Date): String {
		val text = StringBuilder()
		text.append(itemView.context.getString(R.string.adapter_download_elapsed))

		val elapsed = end.time - start.time
		val hour = (elapsed / 3600000).toInt()
		val minute = (elapsed / 60000 % 60).toInt()
		val second = (elapsed / 1000 % 60).toInt()

		if (hour > 0)
			text.append(formatTimeComponent(R.string.sym_hour, hour))

		if (minute > 0)
			text.append(formatTimeComponent(R.string.sym_minute, minute))

		text.append(formatTimeComponent(R.string.sym_second, second))

		return text.toString()
	}

	private fun formatTimeComponent(symbolResourceId: Int, value: Int): String {
		return String.format(
			Locale.ENGLISH,
			"%02d%s ",
			value,
			itemView.context.getString(symbolResourceId)
		)
	}

	companion object {
		const val LocalID = 0x0102
	}
}
