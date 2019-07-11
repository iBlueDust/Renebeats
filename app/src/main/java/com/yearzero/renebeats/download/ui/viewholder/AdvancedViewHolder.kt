package com.yearzero.renebeats.download.ui.viewholder


import android.view.View
import android.widget.ImageView

import com.yearzero.renebeats.R

open class AdvancedViewHolder(Main: View) : IntermediateViewHolder(Main) {
    private var Action1: ImageView = Main.findViewById(R.id.action1)

    protected open fun setAction1(listener: View.OnClickListener) = Action1.setOnClickListener(listener)

    companion object {
        const val LocalID = 0x0200
    }
}
