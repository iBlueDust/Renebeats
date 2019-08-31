package com.yearzero.renebeats.download

import android.content.Context
import android.content.Intent
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.fragment.app.FragmentManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.gms.common.util.ArrayUtils
import com.yearzero.renebeats.Commons
import com.yearzero.renebeats.InternalArgs
import com.yearzero.renebeats.R
import com.yearzero.renebeats.download.ui.HistoryDialog
import com.yearzero.renebeats.download.ui.viewholder.BasicViewHolder
import com.yearzero.renebeats.download.ui.viewholder.FailedViewHolder
import com.yearzero.renebeats.download.ui.viewholder.SuccessViewHolder
import com.yearzero.renebeats.errorlog.ErrorLogDialog
import com.yearzero.renebeats.preferences.Preferences

class HistoryAdapter(private val context: Context, private val manager: FragmentManager, private var array: Array<HistoryLog>) : RecyclerView.Adapter<BasicViewHolder>() {
    constructor(context: Context, manager: FragmentManager) : this(context, manager, Array(0){ HistoryLog() })

    companion object {
        @JvmStatic private val TAG = "HistoryAdapter"
    }

    private val blacklist = ArrayList<Int>()

    //TODO: Add dividers
    
    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): BasicViewHolder {
        return DownloadViewHolderController.getViewHolderByType(context, parent, viewType)
    }

    override fun getItemCount(): Int = array.size

    override fun onBindViewHolder(holder: BasicViewHolder, position: Int) {
        val uwu = array[position]
        val cast = uwu.uncast()

        //TODO Finish this
        holder.setTitle(cast.filename ?: context.getString(R.string.sym_empty))
        holder.setOnClickListener(View.OnClickListener {
            HistoryDialog()
                    .setDownload(cast)
                    .show(manager, TAG)
        })
        holder.setOnLongClickListener(View.OnLongClickListener {
            HistoryDialog()
                    .setDownload(cast)
                    .setSecret(true)
                    .show(manager, TAG)
            return@OnLongClickListener true
        })
        when (holder) {
            is FailedViewHolder -> {
                holder.setRetryListener(View.OnClickListener {
                    val intent = Intent(context, DownloadService::class.java)
                    intent.putExtra(InternalArgs.DATA, cast)
                    context.startActivity(intent)
                })

                holder.setInfoListener(View.OnClickListener {
                    lateinit var dialog: ErrorLogDialog
                    if (Preferences.always_log_failed && uwu.exception != null) {
                        val name = Commons.LogExceptionReturn(uwu, uwu.exception!!)

                        if (name == null) {
                            Toast.makeText(context, context.getString(R.string.adapter_history_autolog_failed), Toast.LENGTH_LONG).show()
                        } else dialog = ErrorLogDialog(name, null)
                    } else dialog = ErrorLogDialog(null, uwu.exception)

                    dialog.show(manager, TAG)
                })
            }
            is SuccessViewHolder -> {
                holder.setStatus(context.getString(R.string.success))
                holder.setDate("${if (uwu.assigned == null) context.getString(R.string.sym_empty) else Preferences.formatTime(context, uwu.assigned!!)} ${context.getString(R.string.sym_separator)} ${if (uwu.completed == null) context.getString(R.string.sym_empty) else Preferences.formatTime(context, uwu.completed!!)}")
                holder.setRetryListener(View.OnClickListener {
                    val intent = Intent(context, DownloadActivity::class.java)
                    intent.putExtra(InternalArgs.DATA, cast)
                    context.startActivity(intent)
                })
            }
        }

        if (uwu.artist != null && uwu.title != null) holder.setTitle(if (Preferences.artist_first) "${uwu.artist} ${context.getString(R.string.sym_separator)} ${uwu.title}" else "${uwu.title} ${context.getString(R.string.sym_separator)} ${uwu.artist}")
        else if (uwu.artist != null) holder.setTitle(uwu.title ?: context.getString(R.string.sym_empty))
        else if (uwu.title != null) holder.setTitle(uwu.artist ?: context.getString(R.string.sym_empty))
        else holder.setTitle(context.getString(R.string.sym_empty))
    }

    internal fun injectData(array: Array<HistoryLog>) {
        this.array = array
        for (i in blacklist) {
            if (!ArrayUtils.contains(array, i)) {
                blacklist.remove(i)
                continue
            }
        }
        notifyDataSetChanged()
    }

    override fun getItemViewType(position: Int): Int = array[position].let{
                DownloadViewHolderController.getViewHolderType(
                        Status(Status.Download.fromValue(it.status_download),
                                Status.Convert.fromValue(it.status_convert),
                                it.status_meta))
            }

    internal fun blacklistAt(index: Int) {
        blacklist.add(array[index].getId())
        notifyItemRemoved(index)
    }

    internal fun unBlacklistAt(index: Int) {
        blacklist.removeAt(index)
        notifyItemInserted(index)
    }

    internal fun deleteAt(index: Int): Boolean {
        val e = History.deleteRecord(array[index])
        return if (e == null)
            true
        else {
            e.printStackTrace()
            false
        }
    }
}