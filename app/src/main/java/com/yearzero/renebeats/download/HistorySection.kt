package com.yearzero.renebeats.download

import android.content.Context
import android.content.Intent
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.yearzero.renebeats.Commons
import com.yearzero.renebeats.InternalArgs
import com.yearzero.renebeats.R
import com.yearzero.renebeats.download.ui.HistoryDialog
import com.yearzero.renebeats.download.ui.viewholder.BasicViewHolder
import com.yearzero.renebeats.download.ui.viewholder.FailedViewHolder
import com.yearzero.renebeats.download.ui.viewholder.SuccessViewHolder
import com.yearzero.renebeats.errorlog.ErrorLogDialog
import com.yearzero.renebeats.preferences.Preferences
import java.util.*

class HistorySection(
	private val context: Context,
	private val array: ArrayList<HistoryLog>,
	year: Int = 0,
	month: Int = 0,
	date: Int = 0
) : HistoryActivity.Node(array, year, month, date) {
	constructor(context: Context, node: HistoryActivity.Node)
			: this(context, node.logs, node.year, node.month, node.date)

	private val TAG = "HistorySection - $name"

	val name: String
		get() {
			val calendar = Calendar.getInstance()
			if (
				calendar.get(Calendar.YEAR) == year
				&& calendar.get(Calendar.MONTH) == month
				&& calendar.get(Calendar.DATE) == date
			) {
				return context.getString(R.string.today)
			}

			calendar.add(Calendar.DAY_OF_MONTH, -1)
			if (
				calendar.get(Calendar.YEAR) == year
				&& calendar.get(Calendar.MONTH) == month
				&& calendar.get(Calendar.DATE) == date
			) {
				return context.getString(R.string.yesterday)
			}
			
			calendar.set(Calendar.YEAR, year)
			calendar.set(Calendar.MONTH, month)
			calendar.set(Calendar.DATE, date)

			return Preferences.formatDateMedium(context, calendar.time)
		}

	fun onCreateViewHolder(parent: ViewGroup, viewType: Int): BasicViewHolder = DownloadViewHolderController.getViewHolderByType(context, parent, viewType)

	fun getItemCount(): Int = array.size

	fun onBindViewHolder(holder: BasicViewHolder, position: Int) {
		val historyLog = array[position]
		val cast = historyLog.uncast()

		holder.setTitle(cast.filename ?: context.getString(R.string.sym_empty))

		if (context is AppCompatActivity) {
			holder.setOnClickListener(View.OnClickListener {
				HistoryDialog()
						.setDownload(cast)
						.show(context.supportFragmentManager, TAG)
			})
			holder.setOnLongClickListener(View.OnLongClickListener {
				HistoryDialog()
						.setDownload(cast)
						.setSecret(true)
						.show(context.supportFragmentManager, TAG)
				return@OnLongClickListener true
			})
		}

		when (holder) {
			is FailedViewHolder -> setupFailedViewHolder(holder, historyLog)
			is SuccessViewHolder -> setupSuccessViewHolder(holder, historyLog)
		}

		holder.setTitle(historyLog.filename ?: context.getString(R.string.sym_empty))
	}

	private fun setupSuccessViewHolder(holder: SuccessViewHolder, historyLog: HistoryLog) {
		holder.setStatus(context.getString(R.string.success))
		holder.setDate(formatTimeRange(historyLog.assigned, historyLog.completed))

		holder.setRetryListener(View.OnClickListener {
			val intent = Intent(context, DownloadActivity::class.java)
			intent.putExtra(InternalArgs.DATA, historyLog.uncast())
			context.startActivity(intent)
		})
	}

	private fun setupFailedViewHolder(holder: FailedViewHolder, historyLog: HistoryLog) {
		holder.setRetryListener(View.OnClickListener {
			val intent = Intent(context, DownloadActivity::class.java)
			intent.putExtra(InternalArgs.DATA, historyLog.uncast())
			context.startActivity(intent)
		})

		if (context !is AppCompatActivity)
			return

		holder.setInfoListener(View.OnClickListener {
			lateinit var dialog: ErrorLogDialog

			// Declare as separate variable so Kotlin doesn't complain that by the time
			// LogReturnException is called, historyLog.exception could have mutated.
			val exception = historyLog.exception

			if (exception == null) {
				dialog = ErrorLogDialog(context.getString(R.string.unknown_exception), null)
				dialog.show(context.supportFragmentManager, TAG)
				return@OnClickListener
			}

			if (!Preferences.always_log_failed) {
				dialog = ErrorLogDialog(null, exception)
				dialog.show(context.supportFragmentManager, TAG)
				return@OnClickListener
			}

			val exceptionLog = Commons.LogExceptionReturn(historyLog, exception)

			if (exceptionLog == null) {
				Toast.makeText(
					context,
					context.getString(R.string.adapter_history_autolog_failed),
					Toast.LENGTH_LONG
				).show()
				return@OnClickListener
			}

			dialog = ErrorLogDialog(exceptionLog, null)
			dialog.show(context.supportFragmentManager, TAG)
		})
	}

	private fun formatTimeRange(start: Date?, end: Date?): String {
		val na = context.getString(R.string.sym_not_available)

		val startStr: String =
			if (start == null)
				na
			else
				Preferences.formatTime(context, start)

		val endStr: String =
			if (end == null)
				na
			else
				Preferences.formatTime(context, end)

		return "$startStr — $endStr"
	}

	fun getItemViewType(position: Int): Int = array[position].let{
		if (
			Status(
				Status.Download.fromValue(it.statusDownload),
				Status.Convert.fromValue(it.statusConvert),
				it.statusMeta
			).isSuccessful
		)
			SuccessViewHolder.LocalID
		else
			FailedViewHolder.LocalID
	}

	fun getItemAt(position: Int): HistoryLog {
		return array[position]
	}

}