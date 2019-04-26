package com.yearzero.renebeats.adapters.viewholder

import android.view.View
import android.widget.TextView

import com.yearzero.renebeats.R

open class QueueViewHolder(itemView: View) : BasicViewHolder(itemView) {

    protected var Status: TextView = itemView.findViewById(R.id.status)

    fun setStatus(status: String) {
        Status.text = status
    }

    companion object {
        const val LocalID = 1
    }
}
