package com.yearzero.renebeats.download

import android.content.Context
import android.content.Intent
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.fragment.app.FragmentManager
import androidx.recyclerview.widget.RecyclerView
import com.yearzero.renebeats.Commons
import com.yearzero.renebeats.InternalArgs
import com.yearzero.renebeats.download.ui.HistoryDialog
import com.yearzero.renebeats.download.ui.viewholder.BasicViewHolder
import com.yearzero.renebeats.download.ui.viewholder.FailedViewHolder
import com.yearzero.renebeats.download.ui.viewholder.SuccessViewHolder
import com.yearzero.renebeats.errorlog.ErrorLogDialog
import com.yearzero.renebeats.preferences.Preferences

class HistoryAdapter(private val context: Context, private val manager: FragmentManager, private var array: Array<HistoryLog> = Array(0){ HistoryLog() }) : RecyclerView.Adapter<BasicViewHolder>() {

    companion object {
        @JvmStatic private val TAG = "HistoryAdapter"
    }

    //TODO: Add dividers
    
    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): BasicViewHolder {
        return DownloadViewHolderController.getViewHolderByType(context, parent, viewType)
    }

    override fun getItemCount(): Int = array.size

    override fun onBindViewHolder(holder: BasicViewHolder, position: Int) {
        val uwu = array[position]
        val cast = uwu.uncast()

        //TODO Finish this
        holder.setTitle(cast.filename ?: "N/A")
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
                            Toast.makeText(context, "Failed to autolog download error", Toast.LENGTH_LONG).show()
                        } else dialog = ErrorLogDialog(name, null)
                    } else dialog = ErrorLogDialog(null, uwu.exception)

                    dialog.show(manager, TAG)
                })
            }
            is SuccessViewHolder -> {
                holder.setStatus("SUCCESS")
                holder.setDate("${if (uwu.assigned == null) "N/A" else Preferences.formatDate(context, uwu.assigned!!)} - ${if (uwu.completed == null) "N/A" else Preferences.formatDate(context, uwu.completed!!)}")
            }
        }

        if (uwu.artist != null && uwu.title != null) holder.setTitle(if (Preferences.artist_first) "${uwu.artist} - ${uwu.title}" else "${uwu.title} - ${uwu.artist}")
        else if (uwu.artist != null) holder.setTitle(uwu.title ?: "N/A")
        else if (uwu.title != null) holder.setTitle(uwu.artist ?: "N/A")
        else holder.setTitle("-")
    }

    internal fun injectData(array: Array<HistoryLog>) {
        this.array = array
        notifyDataSetChanged()
    }

    override fun getItemViewType(position: Int): Int = array[position].let{
                DownloadViewHolderController.getViewHolderType(
                        Status(Status.Download.fromValue(it.status_download),
                                Status.Convert.fromValue(it.status_convert),
                                it.status_meta))
            }

}