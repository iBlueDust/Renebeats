package com.yearzero.renebeats.adapters

import android.content.Context
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import com.yearzero.renebeats.Commons
import com.yearzero.renebeats.adapters.viewholder.BasicViewHolder
import com.yearzero.renebeats.adapters.viewholder.FailedViewHolder
import com.yearzero.renebeats.adapters.viewholder.controller.DownloadController
import com.yearzero.renebeats.classes.HistoryLog
import com.yearzero.renebeats.classes.Status

class HistoryAdapter(private val context: Context, private val array: Array<HistoryLog>) : RecyclerView.Adapter<BasicViewHolder>() {


    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): BasicViewHolder {
        return DownloadController.getViewHolderByType(context, parent, viewType)
    }

    override fun getItemCount(): Int = array.size

    override fun onBindViewHolder(holder: BasicViewHolder, position: Int) {
        val it = array[position]

        when (holder) {
            is FailedViewHolder -> {

            }
        }

        holder.setTitle(if (Commons.Pref.artistfirst) "${it.artist} - ${it.title}" else "${it.title} - ${it.artist}")

    }

    override fun getItemViewType(position: Int): Int = DownloadController.getViewHolderType(Status.Unpack(array[position].status))

}