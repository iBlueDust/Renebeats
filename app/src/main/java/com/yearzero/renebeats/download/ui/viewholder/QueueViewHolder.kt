package com.yearzero.renebeats.download.ui.viewholder

import android.view.View

class QueueViewHolder(itemView: View) : AdvancedViewHolder(itemView) {

    fun setCancelListener(listener: View.OnClickListener) = setAction0(listener)
    fun setSortListener(listener: View.OnClickListener) = setAction1(listener)

    companion object {
        const val LocalID = 0x0202
    }
}
