package com.yearzero.renebeats.download.ui.viewholder

import android.view.View

class FailedViewHolder(itemView: View) : AdvancedViewHolder(itemView) {

    fun setInfoListener(listener: View.OnClickListener) = setAction0(listener)
    fun setRetryListener(listener: View.OnClickListener) = setAction1(listener)
    fun setInfoVisible(visible: Boolean) { Action0.visibility = if (visible) View.VISIBLE else View.GONE }

    companion object {
        const val LocalID = 0x0201
    }
}
