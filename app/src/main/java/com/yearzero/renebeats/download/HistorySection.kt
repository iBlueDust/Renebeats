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

class HistorySection(private val context: Context, private val array: ArrayList<HistoryLog>, /*type: HistoryActivity.SectionType,*/ year: Int = 0, month: Int = 0, date: Int = 0) : HistoryActivity.Node(array, /*type, */year, month, date) {
    constructor(context: Context, node: HistoryActivity.Node) : this(context, node.logs, /*node.type,*/ node.year, node.month, node.date)

    private val TAG = "HistorySection - $name"

    val name: String
        get() {
//            when (type) {
//                HistoryActivity.SectionType.Date -> {
                    val cal = Calendar.getInstance()
                    if (cal.get(Calendar.YEAR) == year && cal.get(Calendar.MONTH) == month && cal.get(Calendar.DATE) == date) {
                        return context.getString(R.string.today)
                    }
                    cal.add(Calendar.DAY_OF_MONTH, -1)
                    if (cal.get(Calendar.YEAR) == year && cal.get(Calendar.MONTH) == month && cal.get(Calendar.DATE) == date) {
                        return context.getString(R.string.yesterday)
                    }
                    cal.set(Calendar.YEAR, year)
                    cal.set(Calendar.MONTH, month)
                    cal.set(Calendar.DATE, date)

                    return Preferences.formatDateMedium(context, cal.time)
//                }
//                HistoryActivity.SectionType.Month -> {
//                    val cal = Calendar.getInstance()
//                    if (cal.get(Calendar.YEAR) == year && cal.get(Calendar.MONTH) == month) {
//                        return context.getString(R.string.this_month)
//                    }
//                    cal.add(Calendar.MONTH, -1)
//                    if (cal.get(Calendar.YEAR) == year && cal.get(Calendar.MONTH) == month) {
//                        return context.getString(R.string.last_month)
//                    }
//                    cal.set(Calendar.YEAR, year)
//                    cal.set(Calendar.MONTH, month)
//                    cal.set(Calendar.DATE, date)
//
//                    return SimpleDateFormat(if (cal.get(Calendar.YEAR) == year) "MMMM" else "MMMM yyyy", Preferences.getMainLocale(context))
//                            .format(cal.time)
//                }
//                HistoryActivity.SectionType.Year -> {
//                    val year = Calendar.getInstance().get(Calendar.YEAR)
//
//                    if (this.year == year) return context.getString(R.string.this_year)
//                    if (this.year == year - 1) return context.getString(R.string.last_year)
//                    return year.toString()
//                }
//            }
        }
        

    //TODO: Add dividers
    
    fun onCreateViewHolder(parent: ViewGroup, viewType: Int): BasicViewHolder = DownloadViewHolderController.getViewHolderByType(context, parent, viewType)

    fun getItemCount(): Int = array.size

    fun onBindViewHolder(holder: BasicViewHolder, position: Int) {
        val uwu = array[position]
        val cast = uwu.uncast()

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
        //TODO Add support for sectionType
        when (holder) {
            is FailedViewHolder -> {
                holder.setRetryListener(View.OnClickListener {
                    val intent = Intent(context, DownloadService::class.java)
                    intent.putExtra(InternalArgs.DATA, cast)
                    context.startActivity(intent)
                })

                if (context is AppCompatActivity)
                    holder.setInfoListener(View.OnClickListener {
                        lateinit var dialog: ErrorLogDialog
                        if (Preferences.always_log_failed && uwu.exception != null) {
                            val name = Commons.LogExceptionReturn(uwu, uwu.exception)

                            if (name == null) Toast.makeText(context, context.getString(R.string.adapter_history_autolog_failed), Toast.LENGTH_LONG).show()
                            else dialog = ErrorLogDialog(name, null)
                        } else if (uwu.exception == null) dialog = ErrorLogDialog(context.getString(R.string.unknown_exception), null)
                        else dialog = ErrorLogDialog(null, uwu.exception)

                        dialog.show(context.supportFragmentManager, TAG)
                    })
            }
            is SuccessViewHolder -> {
                val empty = context.getString(R.string.sym_empty)
                holder.setStatus(context.getString(R.string.success))
                holder.setDate("${if (uwu.assigned == null) empty else Preferences.formatTime(context, uwu.assigned!!)} $empty ${if (uwu.completed == null) empty else Preferences.formatTime(context, uwu.completed!!)}")
                holder.setRetryListener(View.OnClickListener {
                    val intent = Intent(context, DownloadActivity::class.java)
                    intent.putExtra(InternalArgs.DATA, cast)
                    context.startActivity(intent)
                })
            }
        }

        holder.setTitle(uwu.getFilename(context.getString(R.string.sym_separator)) ?: context.getString(R.string.sym_empty))
    }

//    internal fun injectData(array: Array<History>) {
//        this.array.clear()
//        Collections.addAll(this.array, *array)
//    }

    fun getItemViewType(position: Int): Int = array[position].let{
//        DownloadViewHolderController.getViewHolderType(
//                        Status(Status.Download.fromValue(it.status_download),
//                                Status.Convert.fromValue(it.status_convert),
//                                it.status_meta))
        if (Status(Status.Download.fromValue(it.status_download),
                                Status.Convert.fromValue(it.status_convert),
                                it.status_meta).isSuccessful)
            SuccessViewHolder.LocalID
        else FailedViewHolder.LocalID
    }

    fun getItemAt(position: Int): HistoryLog {
        return array[position]
    }

//    internal fun deleteAt(index: Int): Boolean {
//        val e = History.deleteRecord(array[index])
//        return if (e == null)
//            true
//        else {
//            e.printStackTrace()
//            false
//        }
//    }
}