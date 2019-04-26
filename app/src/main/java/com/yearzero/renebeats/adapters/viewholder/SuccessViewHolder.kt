package com.yearzero.renebeats.adapters.viewholder

import android.view.View
import android.widget.TextView

import com.yearzero.renebeats.R

class SuccessViewHolder(itemView: View) : QueueViewHolder(itemView) {

    protected var Date: TextView = itemView.findViewById(R.id.date)

    fun setDate(date: String) {
        Date.text = date
    }

    companion object {
        const val LocalID = 3
    }
}
